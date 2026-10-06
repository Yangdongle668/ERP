package com.erp.module.backup.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.common.exception.BizException;
import com.erp.common.result.PageParam;
import com.erp.common.result.PageResult;
import com.erp.framework.maintenance.DataRestoredEvent;
import com.erp.framework.maintenance.MaintenanceMode;
import com.erp.framework.security.SecurityUtils;
import com.erp.module.backup.api.BackupErrorCodes;
import com.erp.module.backup.config.BackupModuleConfig;
import com.erp.module.backup.controller.vo.BackupVOs.BackupInfo;
import com.erp.module.backup.controller.vo.BackupVOs.BackupReq;
import com.erp.module.backup.controller.vo.BackupVOs.RecordRow;
import com.erp.module.backup.controller.vo.BackupVOs.RestoreCheck;
import com.erp.module.backup.controller.vo.BackupVOs.RestoreLogRow;
import com.erp.module.backup.dal.dataobject.BakRecordDO;
import com.erp.module.backup.dal.dataobject.BakRestoreLogDO;
import com.erp.module.backup.dal.mapper.BakRecordMapper;
import com.erp.module.backup.dal.mapper.BakRestoreLogMapper;
import com.erp.module.system.api.job.ErpJob;
import com.erp.module.system.api.param.ParamApi;
import com.erp.module.system.api.user.UserApi;
import com.erp.module.system.api.user.UserDTO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * 系统备份与恢复（需求 14-系统备份）。
 *
 * <p>备份文件为 zip：{@code manifest.json}（格式、数据库产品、各模块数据库版本、表与行数、是否含附件）、
 * {@code tables/<表>.jsonl}（每张表的数据）、{@code files/…}（本地存储的附件，可选）。保存在 {@code erp.backup.path}。
 *
 * <p>恢复：校验文件完整性（SHA-256）、数据库版本与表结构一致 → 进入维护模式（拒绝其他请求、暂停定时任务）→ 自动做一份恢复前备份 →
 * 一个事务内清空并写入全部表（失败整体回滚）→ 替换附件目录 → 清除缓存并发布 {@link DataRestoredEvent} → 退出维护模式。
 * 同一时间只允许一个备份或恢复。
 */
@Service
public class BackupService {

    private static final Logger LOG = LoggerFactory.getLogger(BackupService.class);
    static final String FORMAT = "erp-backup/1";
    public static final String RUNNING = "RUNNING";
    public static final String SUCCESS = "SUCCESS";
    public static final String FAILED = "FAILED";
    public static final String CONFIRM_TEXT = "确认恢复";
    private static final DateTimeFormatter NAME_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final BakRecordMapper recordMapper;
    private final BakRestoreLogMapper restoreMapper;
    private final DatabaseSnapshot snapshot;
    private final ObjectMapper objectMapper;
    private final ParamApi paramApi;
    private final UserApi userApi;
    private final MaintenanceMode maintenance;
    private final CacheManager cacheManager;
    private final ApplicationEventPublisher events;
    private final Path backupDir;
    private final Path filesDir;
    private final String fileStorage;
    private final long settleMillis;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "erp-backup");
        t.setDaemon(true);
        return t;
    });
    /** 正在进行的操作（备份 / 恢复），为空表示空闲 */
    private final AtomicReference<String> busy = new AtomicReference<>();

    public BackupService(BakRecordMapper recordMapper, BakRestoreLogMapper restoreMapper, DatabaseSnapshot snapshot, ObjectMapper objectMapper,
                         ParamApi paramApi, UserApi userApi, MaintenanceMode maintenance, CacheManager cacheManager, ApplicationEventPublisher events,
                         @Value("${erp.backup.path:./data/backups}") String backupPath,
                         @Value("${erp.file.local-path:./data/files}") String filesPath,
                         @Value("${erp.file.storage:local}") String fileStorage,
                         @Value("${erp.backup.settle-millis:2000}") long settleMillis) {
        this.recordMapper = recordMapper;
        this.restoreMapper = restoreMapper;
        this.snapshot = snapshot;
        this.objectMapper = objectMapper;
        this.paramApi = paramApi;
        this.userApi = userApi;
        this.maintenance = maintenance;
        this.cacheManager = cacheManager;
        this.events = events;
        this.backupDir = Path.of(backupPath).toAbsolutePath().normalize();
        this.filesDir = Path.of(filesPath).toAbsolutePath().normalize();
        this.fileStorage = fileStorage;
        this.settleMillis = settleMillis;
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }

    private boolean localFiles() {
        return "local".equalsIgnoreCase(fileStorage);
    }

    private void acquire(String what) {
        if (!busy.compareAndSet(null, what)) throw BizException.of(BackupErrorCodes.BUSY, busy.get());
    }

    // ==================== 查询 ====================

    public BackupInfo info() {
        Map<String, String> versions = currentVersions();
        long usable = 0;
        try {
            Files.createDirectories(backupDir);
            usable = Files.getFileStore(backupDir).getUsableSpace();
        } catch (IOException e) {
            LOG.warn("读取备份目录可用空间失败：{}", e.getMessage());
        }
        return new BackupInfo(backupDir.toString(), fileStorage, localFiles(), versions, usable, busy.get(), MaintenanceMode.active(),
                paramApi.getBool(BackupModuleConfig.P_AUTO_ENABLED), paramApi.getInt(BackupModuleConfig.P_AUTO_KEEP), CONFIRM_TEXT);
    }

    public PageResult<RecordRow> page(PageParam q) {
        IPage<BakRecordDO> p = recordMapper.selectPage(new Page<>(q.getPageNo(), q.getPageSize()),
                new LambdaQueryWrapper<BakRecordDO>().orderByDesc(BakRecordDO::getStartedAt).orderByDesc(BakRecordDO::getId));
        Map<String, String> current = currentVersions();
        Map<Long, UserDTO> users = userApi.list(p.getRecords().stream().map(BakRecordDO::getOperatorId).filter(Objects::nonNull).distinct().toList());
        return new PageResult<>(p.getRecords().stream().map(r -> row(r, current, users)).toList(), p.getTotal());
    }

    public RecordRow get(Long id) {
        BakRecordDO r = getOrThrow(id);
        return row(r, currentVersions(), userApi.list(r.getOperatorId() == null ? List.of() : List.of(r.getOperatorId())));
    }

    private RecordRow row(BakRecordDO r, Map<String, String> current, Map<Long, UserDTO> users) {
        Map<String, String> versions = versions(r.getSchemaVersions());
        String mismatch = SUCCESS.equals(r.getRecordStatus()) ? mismatch(versions, current) : null;
        UserDTO u = r.getOperatorId() == null ? null : users.get(r.getOperatorId());
        return new RecordRow(r.getId(), r.getFileName(), r.getFileSize(), r.getBackupType(), r.getRecordStatus(), Boolean.TRUE.equals(r.getIncludeFiles()),
                r.getTableCount(), r.getRowCount(), r.getAttachmentCount(), versions, r.getDbProduct(), r.getErrorMsg(), r.getRemark(), r.getStartedAt(),
                r.getFinishedAt(), r.getOperatorId(), u == null ? null : u.realName(), SUCCESS.equals(r.getRecordStatus()) && mismatch == null, mismatch);
    }

    public List<RestoreLogRow> restoreLogs(int limit) {
        List<BakRestoreLogDO> list = restoreMapper.selectList(new LambdaQueryWrapper<BakRestoreLogDO>().orderByDesc(BakRestoreLogDO::getStartedAt)
                .orderByDesc(BakRestoreLogDO::getId).last("LIMIT " + Math.max(1, Math.min(limit, 100))));
        Map<Long, UserDTO> users = userApi.list(list.stream().map(BakRestoreLogDO::getOperatorId).filter(Objects::nonNull).distinct().toList());
        return list.stream().map(l -> restoreRow(l, users)).toList();
    }

    /** 最近一次恢复（维护模式中也可查询） */
    public RestoreLogRow restoreStatus() {
        BakRestoreLogDO l = restoreMapper.selectOne(new LambdaQueryWrapper<BakRestoreLogDO>().orderByDesc(BakRestoreLogDO::getStartedAt)
                .orderByDesc(BakRestoreLogDO::getId).last("LIMIT 1"));
        return l == null ? null : restoreRow(l, Map.of());
    }

    private static RestoreLogRow restoreRow(BakRestoreLogDO l, Map<Long, UserDTO> users) {
        UserDTO u = l.getOperatorId() == null ? null : users.get(l.getOperatorId());
        return new RestoreLogRow(l.getId(), l.getRecordId(), l.getFileName(), l.getPreBackupId(), l.getRestoreStatus(), l.getPhase(), l.getTableCount(),
                l.getRowCount(), l.getAttachmentCount(), l.getErrorMsg(), l.getStartedAt(), l.getFinishedAt(), u == null ? null : u.realName());
    }

    BakRecordDO getOrThrow(Long id) {
        BakRecordDO r = id == null ? null : recordMapper.selectById(id);
        if (r == null) throw new BizException(BackupErrorCodes.NOT_EXISTS);
        return r;
    }

    private Map<String, String> currentVersions() {
        try (Connection c = snapshot.dataSource().getConnection()) {
            return snapshot.schemaVersions(c);
        } catch (SQLException e) {
            throw BizException.of(BackupErrorCodes.BACKUP_FAILED, e.getMessage());
        }
    }

    private Map<String, String> versions(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, String>>() {
            });
        } catch (IOException e) {
            return Map.of();
        }
    }

    /** 数据库版本不一致的说明；一致返回 null */
    static String mismatch(Map<String, String> backup, Map<String, String> current) {
        List<String> diff = new ArrayList<>();
        for (Map.Entry<String, String> e : current.entrySet()) {
            String b = backup.get(e.getKey());
            if (!Objects.equals(b == null ? "" : b, e.getValue())) diff.add(e.getKey() + " 备份 V" + (b == null || b.isEmpty() ? "-" : b) + " / 当前 V" + e.getValue());
        }
        for (String k : backup.keySet()) if (!current.containsKey(k)) diff.add(k + " 当前系统没有该模块");
        return diff.isEmpty() ? null : String.join("；", diff);
    }

    // ==================== 备份 ====================

    /** 手工备份：后台执行，返回备份记录 ID */
    public Long startBackup(BackupReq req) {
        acquire("备份");
        try {
            BakRecordDO r = newRecord(BackupModuleConfig.MANUAL, req == null || req.includeFiles() == null || req.includeFiles(),
                    req == null ? null : req.remark(), SecurityUtils.getLoginUserIdOrNull());
            executor.execute(() -> {
                try {
                    runBackup(r);
                } finally {
                    busy.set(null);
                }
            });
            return r.getId();
        } catch (RuntimeException e) {
            busy.set(null);
            throw e;
        }
    }

    /** 定时自动备份（参数 bak.auto.enabled 开启时），按参数保留最近的份数 */
    @ErpJob(code = "BAK_AUTO", name = "系统自动备份", cron = "0 15 3 * * ?")
    public String autoBackup() {
        if (!paramApi.getBool(BackupModuleConfig.P_AUTO_ENABLED)) return "未开启自动备份";
        acquire("备份");
        try {
            BakRecordDO r = runBackup(newRecord(BackupModuleConfig.AUTO, paramApi.getBool(BackupModuleConfig.P_AUTO_INCLUDE_FILES), "定时自动备份", null));
            int removed = purge(BackupModuleConfig.AUTO, paramApi.getInt(BackupModuleConfig.P_AUTO_KEEP));
            return r.getFileName() + " " + r.getRecordStatus() + (removed > 0 ? "，清理旧备份 " + removed + " 份" : "");
        } finally {
            busy.set(null);
        }
    }

    private BakRecordDO newRecord(String type, boolean includeFiles, String remark, Long operator) {
        LocalDateTime now = LocalDateTime.now();
        String suffix = switch (type) {
            case BackupModuleConfig.AUTO -> "-auto";
            case BackupModuleConfig.PRE_RESTORE -> "-pre-restore";
            default -> "";
        };
        BakRecordDO r = new BakRecordDO();
        r.setFileName("erp-backup-" + now.format(NAME_TIME) + suffix + "-" + Long.toString(System.nanoTime() % 100_000, 36) + ".zip");
        r.setFileSize(0L);
        r.setBackupType(type);
        r.setRecordStatus(RUNNING);
        r.setIncludeFiles(includeFiles && localFiles());
        r.setTableCount(0);
        r.setRowCount(0L);
        r.setAttachmentCount(0);
        r.setRemark(remark == null || remark.isBlank() ? null : remark.trim().length() > 256 ? remark.trim().substring(0, 256) : remark.trim());
        r.setStartedAt(now);
        r.setOperatorId(operator);
        recordMapper.insert(r);
        return r;
    }

    /** 同步执行备份（调用方已持有 busy） */
    BakRecordDO runBackup(BakRecordDO r) {
        Path target = backupDir.resolve(r.getFileName());
        Path tmp = backupDir.resolve(r.getFileName() + ".part");
        try {
            Files.createDirectories(backupDir);
            long rows = 0;
            int tableCount;
            int attachments = 0;
            Map<String, String> versions;
            String product;
            try (OutputStream fos = Files.newOutputStream(tmp); ZipOutputStream zip = new ZipOutputStream(fos)) {
                List<Map<String, Object>> tables = new ArrayList<>();
                try (Connection c = snapshot.dataSource().getConnection()) {
                    c.setReadOnly(true);
                    c.setAutoCommit(false);
                    trySerializable(c);
                    product = snapshot.product(c);
                    versions = snapshot.schemaVersions(c);
                    for (String t : snapshot.tables(c)) {
                        zip.putNextEntry(new ZipEntry("tables/" + t + ".jsonl"));
                        long n = snapshot.export(c, t, zip);
                        zip.closeEntry();
                        rows += n;
                        tables.add(Map.of("name", t, "rows", n));
                    }
                    c.rollback();
                }
                tableCount = tables.size();
                if (Boolean.TRUE.equals(r.getIncludeFiles()) && Files.isDirectory(filesDir)) attachments = zipFiles(zip);
                ObjectNode manifest = objectMapper.createObjectNode();
                manifest.put("format", FORMAT);
                manifest.put("createdAt", r.getStartedAt().toString());
                manifest.put("dbProduct", product);
                manifest.set("schemaVersions", objectMapper.valueToTree(versions));
                manifest.set("tables", objectMapper.valueToTree(tables));
                manifest.put("includeFiles", Boolean.TRUE.equals(r.getIncludeFiles()));
                manifest.put("fileStorage", fileStorage);
                manifest.put("attachmentCount", attachments);
                manifest.put("backupType", r.getBackupType());
                if (r.getRemark() != null) manifest.put("remark", r.getRemark());
                zip.putNextEntry(new ZipEntry("manifest.json"));
                zip.write(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(manifest));
                zip.closeEntry();
            }
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            r.setFileSize(Files.size(target));
            r.setSha256(sha256(target));
            r.setTableCount(tableCount);
            r.setRowCount(rows);
            r.setAttachmentCount(attachments);
            r.setSchemaVersions(objectMapper.writeValueAsString(versions));
            r.setDbProduct(product);
            r.setRecordStatus(SUCCESS);
            r.setErrorMsg(null);
        } catch (IOException | SQLException | RuntimeException e) {
            LOG.error("[系统备份] {} 失败", r.getFileName(), e);
            try {
                Files.deleteIfExists(tmp);
            } catch (IOException ignored) {
                // 临时文件删除失败不影响结果
            }
            r.setRecordStatus(FAILED);
            r.setErrorMsg(limit(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(), 2000));
        }
        r.setFinishedAt(LocalDateTime.now());
        BakRecordDO fresh = recordMapper.selectById(r.getId());
        r.setVersion(fresh.getVersion());
        recordMapper.updateByIdOrFail(r);
        return r;
    }

    /** 尽量取一致性快照（MySQL InnoDB 可重复读即可；不支持时忽略） */
    private static void trySerializable(Connection c) {
        try {
            c.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
        } catch (SQLException e) {
            LOG.debug("设置隔离级别失败：{}", e.getMessage());
        }
    }

    private int zipFiles(ZipOutputStream zip) throws IOException {
        int n = 0;
        try (Stream<Path> s = Files.walk(filesDir)) {
            for (Path p : s.filter(Files::isRegularFile).sorted().toList()) {
                String rel = filesDir.relativize(p).toString().replace('\\', '/');
                zip.putNextEntry(new ZipEntry("files/" + rel));
                Files.copy(p, zip);
                zip.closeEntry();
                n++;
            }
        }
        return n;
    }

    /** 每种类型只保留最近 keep 份（删除记录和文件） */
    int purge(String type, int keep) {
        List<BakRecordDO> list = recordMapper.selectList(new LambdaQueryWrapper<BakRecordDO>().eq(BakRecordDO::getBackupType, type)
                .ne(BakRecordDO::getRecordStatus, RUNNING).orderByDesc(BakRecordDO::getStartedAt).orderByDesc(BakRecordDO::getId));
        int removed = 0;
        for (int i = Math.max(1, keep); i < list.size(); i++) {
            remove(list.get(i));
            removed++;
        }
        return removed;
    }

    // ==================== 文件管理 ====================

    public void delete(Long id) {
        BakRecordDO r = getOrThrow(id);
        if (RUNNING.equals(r.getRecordStatus())) throw BizException.of(BackupErrorCodes.BUSY, "备份");
        remove(r);
    }

    private void remove(BakRecordDO r) {
        try {
            Files.deleteIfExists(backupDir.resolve(r.getFileName()));
        } catch (IOException e) {
            throw BizException.of(BackupErrorCodes.BACKUP_FAILED, "删除文件失败：" + e.getMessage());
        }
        recordMapper.deleteById(r.getId());
    }

    /** 备份文件（下载） */
    public Path file(Long id) {
        BakRecordDO r = getOrThrow(id);
        if (!SUCCESS.equals(r.getRecordStatus())) throw new BizException(BackupErrorCodes.NOT_SUCCESS);
        Path p = backupDir.resolve(r.getFileName());
        if (!Files.isRegularFile(p)) throw BizException.of(BackupErrorCodes.FILE_MISSING, r.getFileName());
        return p;
    }

    /** 上传备份文件（如从其他服务器下载的备份）：校验格式后登记为“上传”记录 */
    public Long upload(MultipartFile file) {
        if (file == null || file.isEmpty()) throw BizException.of(BackupErrorCodes.INVALID_FILE, "文件为空");
        String name = "erp-backup-" + LocalDateTime.now().format(NAME_TIME) + "-upload-" + Long.toString(System.nanoTime() % 100_000, 36) + ".zip";
        Path target = backupDir.resolve(name);
        try {
            Files.createDirectories(backupDir);
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
            JsonNode m;
            try (ZipFile zip = new ZipFile(target.toFile())) {
                m = manifest(zip);
            }
            BakRecordDO r = new BakRecordDO();
            r.setFileName(name);
            r.setFileSize(Files.size(target));
            r.setSha256(sha256(target));
            r.setBackupType(BackupModuleConfig.UPLOAD);
            r.setRecordStatus(SUCCESS);
            r.setIncludeFiles(m.path("includeFiles").asBoolean(false));
            long rows = 0;
            for (JsonNode t : m.path("tables")) rows += t.path("rows").asLong();
            r.setTableCount(m.path("tables").size());
            r.setRowCount(rows);
            r.setAttachmentCount(m.path("attachmentCount").asInt(0));
            r.setSchemaVersions(objectMapper.writeValueAsString(m.path("schemaVersions")));
            r.setDbProduct(limit(m.path("dbProduct").asText(null), 32));
            r.setRemark(limit("上传：" + (file.getOriginalFilename() == null ? "" : file.getOriginalFilename()), 256));
            r.setStartedAt(LocalDateTime.now());
            r.setFinishedAt(LocalDateTime.now());
            r.setOperatorId(SecurityUtils.getLoginUserIdOrNull());
            recordMapper.insert(r);
            return r.getId();
        } catch (IOException e) {
            deleteQuietly(target);
            throw BizException.of(BackupErrorCodes.INVALID_FILE, e.getMessage());
        } catch (BizException e) {
            deleteQuietly(target);
            throw e;
        }
    }

    private JsonNode manifest(ZipFile zip) throws IOException {
        ZipEntry e = zip.getEntry("manifest.json");
        if (e == null) throw BizException.of(BackupErrorCodes.INVALID_FILE, "缺少 manifest.json");
        JsonNode m;
        try (InputStream in = zip.getInputStream(e)) {
            m = objectMapper.readTree(in);
        }
        if (!FORMAT.equals(m.path("format").asText())) throw BizException.of(BackupErrorCodes.INVALID_FILE, "格式 " + m.path("format").asText());
        for (JsonNode t : m.path("tables")) {
            if (zip.getEntry("tables/" + t.path("name").asText() + ".jsonl") == null) {
                throw BizException.of(BackupErrorCodes.INVALID_FILE, "缺少表 " + t.path("name").asText() + " 的数据");
            }
        }
        return m;
    }

    // ==================== 恢复 ====================

    /** 恢复前检查（不修改数据）：文件完整、数据库版本与表结构一致 */
    public RestoreCheck check(Long id) {
        BakRecordDO r = getOrThrow(id);
        List<String> problems = new ArrayList<>();
        Map<String, String> current = currentVersions();
        Map<String, String> backup = versions(r.getSchemaVersions());
        try {
            Path p = file(id);
            if (r.getSha256() != null && !r.getSha256().equals(sha256(p))) problems.add(BackupErrorCodes.CHECKSUM_MISMATCH.message());
            try (ZipFile zip = new ZipFile(p.toFile())) {
                JsonNode m = manifest(zip);
                backup = objectMapper.convertValue(m.path("schemaVersions"), new TypeReference<LinkedHashMap<String, String>>() {
                });
                String mm = mismatch(backup, current);
                if (mm != null) problems.add("数据库版本不一致：" + mm);
                else problems.addAll(verifyTables(zip, m));
            }
        } catch (BizException e) {
            problems.add(e.getMessage());
        } catch (IOException | SQLException e) {
            problems.add(e.getMessage());
        }
        return new RestoreCheck(r.getId(), r.getFileName(), problems.isEmpty(), problems, backup, current, Boolean.TRUE.equals(r.getIncludeFiles()),
                localFiles(), r.getRowCount(), r.getAttachmentCount(), CONFIRM_TEXT);
    }

    private List<String> verifyTables(ZipFile zip, JsonNode m) throws IOException, SQLException {
        Map<String, List<String>> cols = new LinkedHashMap<>();
        for (JsonNode t : m.path("tables")) {
            String name = t.path("name").asText();
            try (InputStream in = zip.getInputStream(zip.getEntry("tables/" + name + ".jsonl"))) {
                String head = firstLine(in);
                JsonNode h = objectMapper.readTree(head);
                List<String> list = new ArrayList<>();
                for (JsonNode c : (ArrayNode) h.path("columns")) list.add(c.asText());
                cols.put(name, list);
            }
        }
        try (Connection c = snapshot.dataSource().getConnection()) {
            return snapshot.verify(c, cols);
        }
    }

    private static String firstLine(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        int b;
        while ((b = in.read()) != -1 && b != '\n') sb.append((char) b);
        return new String(sb.toString().getBytes(java.nio.charset.StandardCharsets.ISO_8859_1), java.nio.charset.StandardCharsets.UTF_8);
    }

    /** 一键恢复：校验通过后后台执行，返回恢复日志 ID；进度用 {@link #restoreStatus()} 查询 */
    public Long startRestore(Long id, String confirm) {
        if (!CONFIRM_TEXT.equals(confirm == null ? null : confirm.trim())) throw new BizException(BackupErrorCodes.CONFIRM_REQUIRED);
        acquire("恢复");
        try {
            RestoreCheck check = check(id);
            if (!check.ok()) {
                String msg = String.join("；", check.problems());
                if (msg.contains("数据库版本不一致")) throw BizException.of(BackupErrorCodes.SCHEMA_MISMATCH, msg);
                throw BizException.of(BackupErrorCodes.TABLE_MISMATCH, msg);
            }
            BakRecordDO r = getOrThrow(id);
            BakRestoreLogDO log = new BakRestoreLogDO();
            log.setRecordId(r.getId());
            log.setFileName(r.getFileName());
            log.setRestoreStatus(RUNNING);
            log.setPhase("准备");
            log.setTableCount(0);
            log.setRowCount(0L);
            log.setAttachmentCount(0);
            log.setStartedAt(LocalDateTime.now());
            log.setOperatorId(SecurityUtils.getLoginUserIdOrNull());
            restoreMapper.insert(log);
            executor.execute(() -> {
                try {
                    runRestore(r, log.getId());
                } finally {
                    busy.set(null);
                }
            });
            return log.getId();
        } catch (RuntimeException e) {
            busy.set(null);
            throw e;
        }
    }

    void runRestore(BakRecordDO r, Long logId) {
        if (!maintenance.enter("正在恢复数据（" + r.getFileName() + "）")) {
            finishRestore(logId, FAILED, "系统已在维护中", null);
            return;
        }
        String warning = null;
        try {
            phase(logId, "等待进行中的请求结束");
            if (settleMillis > 0) Thread.sleep(settleMillis);
            phase(logId, "恢复前自动备份");
            BakRecordDO pre = runBackup(newRecord(BackupModuleConfig.PRE_RESTORE, Boolean.TRUE.equals(r.getIncludeFiles()), "恢复 " + r.getFileName() + " 前自动备份",
                    r.getOperatorId()));
            BakRestoreLogDO l = restoreMapper.selectById(logId);
            l.setPreBackupId(pre.getId());
            restoreMapper.updateByIdOrFail(l);
            if (!SUCCESS.equals(pre.getRecordStatus())) throw new IllegalStateException("恢复前自动备份失败：" + pre.getErrorMsg());
            purge(BackupModuleConfig.PRE_RESTORE, paramApi.getInt(BackupModuleConfig.P_AUTO_KEEP));

            Path p = backupDir.resolve(r.getFileName());
            long rows;
            List<String> tables = new ArrayList<>();
            int attachments = 0;
            try (ZipFile zip = new ZipFile(p.toFile())) {
                JsonNode m = manifest(zip);
                for (JsonNode t : m.path("tables")) tables.add(t.path("name").asText());
                phase(logId, "恢复数据库（" + tables.size() + " 张表）");
                rows = snapshot.restore(tables, t -> zip.getInputStream(zip.getEntry("tables/" + t + ".jsonl")));
                BakRestoreLogDO x = restoreMapper.selectById(logId);
                x.setTableCount(tables.size());
                x.setRowCount(rows);
                restoreMapper.updateByIdOrFail(x);
                if (m.path("includeFiles").asBoolean(false) && localFiles()) {
                    phase(logId, "恢复附件");
                    try {
                        attachments = restoreFiles(zip);
                    } catch (IOException | RuntimeException e) {
                        LOG.error("[系统恢复] 附件恢复失败", e);
                        warning = "数据库已恢复，附件恢复失败：" + e.getMessage();
                    }
                }
            }
            phase(logId, "刷新缓存");
            cacheManager.getCacheNames().forEach(n -> Objects.requireNonNull(cacheManager.getCache(n)).clear());
            try {
                events.publishEvent(new DataRestoredEvent(r.getFileName()));
            } catch (RuntimeException e) {
                LOG.error("[系统恢复] 恢复后刷新失败", e);
                warning = (warning == null ? "" : warning + "；") + "恢复后刷新失败（建议重启服务）：" + e.getMessage();
            }
            BakRestoreLogDO x = restoreMapper.selectById(logId);
            x.setAttachmentCount(attachments);
            restoreMapper.updateByIdOrFail(x);
            finishRestore(logId, SUCCESS, warning, "完成");
            LOG.warn("[系统恢复] 已用 {} 恢复全部数据：{} 张表、{} 行、附件 {} 个", r.getFileName(), tables.size(), rows, attachments);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            finishRestore(logId, FAILED, "恢复被中断，数据未修改", null);
        } catch (Exception e) {
            LOG.error("[系统恢复] {} 失败", r.getFileName(), e);
            finishRestore(logId, FAILED, "恢复失败，数据库已回滚到恢复前的状态：" + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()), null);
        } finally {
            maintenance.exit();
        }
    }

    private void phase(Long logId, String phase) {
        BakRestoreLogDO l = restoreMapper.selectById(logId);
        l.setPhase(phase);
        restoreMapper.updateByIdOrFail(l);
    }

    private void finishRestore(Long logId, String status, String msg, String phase) {
        BakRestoreLogDO l = restoreMapper.selectById(logId);
        l.setRestoreStatus(status);
        l.setErrorMsg(limit(msg, 2000));
        if (phase != null) l.setPhase(phase);
        l.setFinishedAt(LocalDateTime.now());
        restoreMapper.updateByIdOrFail(l);
    }

    /** 先解压到临时目录，再替换附件目录（旧目录在替换成功后删除） */
    private int restoreFiles(ZipFile zip) throws IOException {
        String ts = LocalDateTime.now().format(NAME_TIME);
        Path tmp = filesDir.resolveSibling(filesDir.getFileName() + ".restoring-" + ts);
        Path old = filesDir.resolveSibling(filesDir.getFileName() + ".old-" + ts);
        Files.createDirectories(tmp);
        int n = 0;
        try {
            for (ZipEntry e : java.util.Collections.list(zip.entries())) {
                if (e.isDirectory() || !e.getName().startsWith("files/")) continue;
                Path target = tmp.resolve(e.getName().substring("files/".length())).normalize();
                if (!target.startsWith(tmp)) throw new IOException("非法的附件路径：" + e.getName());
                Files.createDirectories(target.getParent());
                try (InputStream in = zip.getInputStream(e)) {
                    Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                }
                n++;
            }
            if (Files.exists(filesDir)) Files.move(filesDir, old);
            Files.move(tmp, filesDir);
        } catch (IOException | RuntimeException e) {
            if (!Files.exists(filesDir) && Files.exists(old)) Files.move(old, filesDir);
            deleteTree(tmp);
            throw e;
        }
        deleteTree(old);
        return n;
    }

    private static void deleteTree(Path p) {
        if (!Files.exists(p)) return;
        try (Stream<Path> s = Files.walk(p)) {
            s.sorted(Comparator.reverseOrder()).forEach(BackupService::deleteQuietly);
        } catch (IOException e) {
            LOG.warn("删除目录失败 {}：{}", p, e.getMessage());
        }
    }

    private static void deleteQuietly(Path p) {
        try {
            Files.deleteIfExists(p);
        } catch (IOException e) {
            LOG.warn("删除文件失败 {}：{}", p, e.getMessage());
        }
    }

    static String sha256(Path p) throws IOException {
        try (InputStream in = new DigestInputStream(Files.newInputStream(p), MessageDigest.getInstance("SHA-256"))) {
            in.transferTo(OutputStream.nullOutputStream());
            return HexFormat.of().formatHex(((DigestInputStream) in).getMessageDigest().digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String limit(String s, int max) {
        return s == null ? null : s.length() > max ? s.substring(0, max) : s;
    }
}
