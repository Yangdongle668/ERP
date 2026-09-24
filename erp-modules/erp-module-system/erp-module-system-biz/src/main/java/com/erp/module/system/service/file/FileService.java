package com.erp.module.system.service.file;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.erp.common.exception.BizException;
import com.erp.framework.security.LoginUser;
import com.erp.framework.security.SecurityUtils;
import com.erp.module.system.api.SystemErrorCodes;
import com.erp.module.system.api.file.FileAccessChecker;
import com.erp.module.system.api.file.FileApi;
import com.erp.module.system.api.file.FileInfo;
import com.erp.module.system.api.param.ParamApi;
import com.erp.module.system.dal.dataobject.FileDO;
import com.erp.module.system.dal.mapper.FileMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collection;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * 附件（需求 01-系统管理/12 第 1 节）：上传校验（大小、扩展名、文件头）、访问控制（FileAccessChecker）、绑定、清理。
 */
@Slf4j
@Service
public class FileService implements FileApi {

    /** 超过此时长未绑定的文件由清理任务删除（R03） */
    static final int UNBOUND_HOURS = 24;
    /** 逻辑删除后物理文件保留天数（R05） */
    static final int DELETED_KEEP_DAYS = 30;
    private static final int HEAD_BYTES = 8192;
    private static final int CLEANUP_BATCH = 500;
    private static final DateTimeFormatter DIR = DateTimeFormatter.ofPattern("yyyy/MM");

    private final FileMapper fileMapper;
    private final FileStorage storage;
    private final ParamApi paramApi;
    /** 延迟获取：业务模块的 checker 可能依赖 FileApi，避免循环依赖 */
    private final ObjectProvider<FileAccessChecker> checkers;

    public FileService(FileMapper fileMapper, FileStorage storage, ParamApi paramApi, ObjectProvider<FileAccessChecker> checkers) {
        this.fileMapper = fileMapper;
        this.storage = storage;
        this.paramApi = paramApi;
        this.checkers = checkers;
    }

    public record Policy(int maxSizeMb, List<String> allowedExt) {
    }

    public Policy policy() {
        return new Policy(paramApi.getInt("sys.file.max-size-mb"), allowedExt());
    }

    private List<String> allowedExt() {
        return Arrays.stream(paramApi.getString("sys.file.allowed-ext").split(","))
                .map(s -> s.trim().toLowerCase(Locale.ROOT)).filter(s -> !s.isEmpty()).toList();
    }

    // ==================== 上传 ====================

    @Transactional
    public FileDO upload(MultipartFile file, String bizType, Long bizId, String category) {
        if (file == null || file.isEmpty()) throw BizException.of(SystemErrorCodes.FILE_EMPTY);
        int maxMb = paramApi.getInt("sys.file.max-size-mb");
        if (file.getSize() > maxMb * 1024L * 1024L) throw BizException.of(SystemErrorCodes.FILE_TOO_LARGE, maxMb);
        String fileName = cleanName(file.getOriginalFilename());
        String ext = extOf(fileName);
        if (!allowedExt().contains(ext)) throw BizException.of(SystemErrorCodes.FILE_TYPE_NOT_ALLOWED, ext.isEmpty() ? "无扩展名" : ext);
        boolean bind = bizType != null && !bizType.isBlank() && bizId != null;
        if (bind) checkEdit(bizType, bizId, null);
        try (InputStream in = new BufferedInputStream(file.getInputStream(), HEAD_BYTES * 2)) {
            in.mark(HEAD_BYTES + 1);
            byte[] head = in.readNBytes(HEAD_BYTES);
            in.reset();
            if (!FileTypeGuard.matches(ext, head)) throw BizException.of(SystemErrorCodes.FILE_TYPE_NOT_ALLOWED, ext);
            return store(in, fileName, ext, contentTypeOf(ext, file.getContentType()), file.getSize(),
                    bind ? bizType : null, bind ? bizId : null, blankToNull(category));
        } catch (IOException e) {
            log.error("[附件] 读取上传文件失败 {}", fileName, e);
            throw BizException.of(SystemErrorCodes.FILE_STORAGE_FAILED);
        }
    }

    private FileDO store(InputStream in, String fileName, String ext, String contentType, long size,
                         String bizType, Long bizId, String category) throws IOException {
        String path = LocalDateTime.now().format(DIR) + "/" + UUID.randomUUID().toString().replace("-", "") + (ext.isEmpty() ? "" : "." + ext);
        MessageDigest digest = sha256();
        try (DigestInputStream din = new DigestInputStream(in, digest)) {
            storage.put(path, din);
        }
        FileDO f = new FileDO();
        f.setBizType(bizType);
        f.setBizId(bizId);
        f.setCategory(category);
        f.setFileName(fileName);
        f.setExt(ext);
        f.setContentType(contentType);
        f.setFileSize(size);
        f.setSha256(HexFormat.of().formatHex(digest.digest()));
        f.setStorage(storage.type());
        f.setPath(path);
        f.setBound(bizType != null);
        // 数据库写入失败（事务回滚）时删除刚写入的物理文件
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status == STATUS_ROLLED_BACK) deleteQuietly(path);
                }
            });
        }
        fileMapper.insert(f);
        return f;
    }

    // ==================== 查询 / 下载 / 删除（带权限） ====================

    /** 页面附件列表：有 FileAccessChecker 的按单据查看权限；否则只列出本人上传的（管理员全部） */
    public List<FileDO> listForCurrentUser(String bizType, Long bizId) {
        List<FileDO> files = selectByBiz(bizType, bizId);
        Optional<FileAccessChecker> checker = checkerOf(bizType);
        if (checker.isPresent()) {
            if (!checker.get().canView(bizType, bizId)) throw BizException.of(SystemErrorCodes.FILE_ACCESS_DENIED);
            return files;
        }
        LoginUser me = SecurityUtils.getLoginUser();
        return isAdmin(me) ? files : files.stream().filter(f -> me.id().equals(f.getCreatedBy())).toList();
    }

    /** 下载、预览前取文件并校验查看权限（R04） */
    public FileDO getForView(Long id) {
        FileDO f = get(id);
        LoginUser me = SecurityUtils.getLoginUser();
        boolean ok;
        if (!Boolean.TRUE.equals(f.getBound())) ok = me.id().equals(f.getCreatedBy()) || isAdmin(me);
        else ok = checkerOf(f.getBizType()).map(c -> c.canView(f.getBizType(), f.getBizId()))
                .orElseGet(() -> me.id().equals(f.getCreatedBy()) || isAdmin(me));
        if (!ok) throw BizException.of(SystemErrorCodes.FILE_ACCESS_DENIED);
        return f;
    }

    public InputStream open(FileDO f) {
        try {
            return storage.open(f.getPath());
        } catch (IOException e) {
            log.error("[附件] 物理文件不存在或无法读取 id={} path={}", f.getId(), f.getPath(), e);
            throw BizException.of(SystemErrorCodes.FILE_NOT_EXISTS);
        }
    }

    @Transactional
    public void delete(Long id) {
        FileDO f = get(id);
        if (Boolean.TRUE.equals(f.getBound())) checkEdit(f.getBizType(), f.getBizId(), f);
        else {
            LoginUser me = SecurityUtils.getLoginUser();
            if (!me.id().equals(f.getCreatedBy()) && !isAdmin(me)) throw BizException.of(SystemErrorCodes.FILE_ACCESS_DENIED);
        }
        fileMapper.deleteById(f.getId());
    }

    private FileDO get(Long id) {
        FileDO f = id == null ? null : fileMapper.selectById(id);
        if (f == null) throw BizException.of(SystemErrorCodes.FILE_NOT_EXISTS);
        return f;
    }

    /** 编辑权限：有 checker 按 checker；没有时已有文件只允许上传人和管理员删除，上传到单据不限制 */
    private void checkEdit(String bizType, Long bizId, FileDO existing) {
        Optional<FileAccessChecker> checker = checkerOf(bizType);
        boolean ok;
        if (checker.isPresent()) ok = checker.get().canEdit(bizType, bizId);
        else if (existing == null) ok = true;
        else {
            LoginUser me = SecurityUtils.getLoginUser();
            ok = me.id().equals(existing.getCreatedBy()) || isAdmin(me);
        }
        if (!ok) throw BizException.of(SystemErrorCodes.FILE_ACCESS_DENIED);
    }

    private Optional<FileAccessChecker> checkerOf(String bizType) {
        return bizType == null ? Optional.empty() : checkers.orderedStream().filter(c -> c.supports(bizType)).findFirst();
    }

    private static boolean isAdmin(LoginUser u) {
        return u.permissions().contains(LoginUser.ALL_PERMISSION);
    }

    // ==================== FileApi ====================

    @Override
    @Transactional
    public void bind(Collection<Long> fileIds, String bizType, Long bizId) {
        if (fileIds == null || fileIds.isEmpty()) return;
        Long me = SecurityUtils.getLoginUserIdOrNull();
        for (FileDO f : fileMapper.selectBatchIds(fileIds)) {
            if (Boolean.TRUE.equals(f.getBound())) {
                if (bizType.equals(f.getBizType()) && bizId.equals(f.getBizId())) continue;
                throw BizException.of(SystemErrorCodes.FILE_NOT_EXISTS);
            }
            if (me != null && !me.equals(f.getCreatedBy())) throw BizException.of(SystemErrorCodes.FILE_ACCESS_DENIED);
            fileMapper.update(null, new LambdaUpdateWrapper<FileDO>().eq(FileDO::getId, f.getId()).eq(FileDO::getBound, false)
                    .set(FileDO::getBizType, bizType).set(FileDO::getBizId, bizId).set(FileDO::getBound, true)
                    .set(FileDO::getUpdatedAt, LocalDateTime.now()));
        }
    }

    @Override
    public List<FileInfo> list(String bizType, Long bizId) {
        return selectByBiz(bizType, bizId).stream().map(FileService::toInfo).toList();
    }

    @Override
    @Transactional
    public void deleteByBiz(String bizType, Long bizId) {
        List<FileDO> files = selectByBiz(bizType, bizId);
        if (!files.isEmpty()) fileMapper.deleteBatchIds(files.stream().map(FileDO::getId).toList());
    }

    @Override
    @Transactional
    public Long saveGenerated(String bizType, Long bizId, String fileName, String contentType, byte[] content) {
        String name = cleanName(fileName);
        String ext = extOf(name);
        try {
            return store(new ByteArrayInputStream(content), name, ext, contentType == null ? contentTypeOf(ext, null) : contentType,
                    content.length, bizType, bizId, null).getId();
        } catch (IOException e) {
            log.error("[附件] 保存生成文件失败 {}", name, e);
            throw BizException.of(SystemErrorCodes.FILE_STORAGE_FAILED);
        }
    }

    private List<FileDO> selectByBiz(String bizType, Long bizId) {
        return fileMapper.selectList(new LambdaQueryWrapper<FileDO>().eq(FileDO::getBizType, bizType).eq(FileDO::getBizId, bizId)
                .eq(FileDO::getBound, true).orderByAsc(FileDO::getCreatedAt).orderByAsc(FileDO::getId));
    }

    public static FileInfo toInfo(FileDO f) {
        return new FileInfo(f.getId(), f.getBizType(), f.getBizId(), f.getCategory(), f.getFileName(), f.getExt(), f.getContentType(),
                f.getFileSize(), f.getCreatedBy(), f.getCreatedAt());
    }

    // ==================== 清理（R03、R05） ====================

    /** 删除超过 24 小时未绑定的文件，清理逻辑删除超过 30 天的物理文件。返回清理数量。 */
    public int cleanup() {
        int count = 0;
        LocalDateTime unboundBefore = LocalDateTime.now().minusHours(UNBOUND_HOURS);
        while (true) {
            List<FileDO> batch = fileMapper.selectList(new LambdaQueryWrapper<FileDO>().eq(FileDO::getBound, false)
                    .lt(FileDO::getCreatedAt, unboundBefore).last("LIMIT " + CLEANUP_BATCH));
            batch.forEach(this::purge);
            count += batch.size();
            if (batch.size() < CLEANUP_BATCH) break;
        }
        LocalDateTime deletedBefore = LocalDateTime.now().minusDays(DELETED_KEEP_DAYS);
        while (true) {
            List<FileDO> batch = fileMapper.selectDeletedBefore(deletedBefore, CLEANUP_BATCH);
            batch.forEach(this::purge);
            count += batch.size();
            if (batch.size() < CLEANUP_BATCH) break;
        }
        if (count > 0) log.info("[附件清理] 清理 {} 个文件", count);
        return count;
    }

    private void purge(FileDO f) {
        fileMapper.purge(f.getId());
        if (fileMapper.countAlive(f.getPath()) == 0) deleteQuietly(f.getPath());
    }

    private void deleteQuietly(String path) {
        try {
            storage.delete(path);
        } catch (IOException | RuntimeException e) {
            log.warn("[附件] 删除物理文件失败 {}", path, e);
        }
    }

    // ==================== 工具 ====================

    /** 只保留文件名（去掉路径），去掉控制字符，最长 255 */
    static String cleanName(String original) {
        String n = original == null ? "file" : original.replace('\\', '/');
        n = n.substring(n.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "").trim();
        if (n.isEmpty()) n = "file";
        return n.length() > 255 ? n.substring(n.length() - 255) : n;
    }

    static String extOf(String fileName) {
        int i = fileName.lastIndexOf('.');
        return i < 0 || i == fileName.length() - 1 ? "" : fileName.substring(i + 1).toLowerCase(Locale.ROOT);
    }

    private static String contentTypeOf(String ext, String declared) {
        return switch (ext) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            case "pdf" -> "application/pdf";
            case "txt", "csv", "log" -> "text/plain";
            case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            default -> declared == null || declared.isBlank() ? "application/octet-stream" : declared;
        };
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
