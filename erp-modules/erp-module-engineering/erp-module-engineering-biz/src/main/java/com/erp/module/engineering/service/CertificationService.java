package com.erp.module.engineering.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.common.exception.BizException;
import com.erp.common.result.PageResult;
import com.erp.framework.event.DomainEventPublisher;
import com.erp.module.engineering.api.EngineeringErrorCodes;
import com.erp.module.engineering.api.cert.CertificationApi;
import com.erp.module.engineering.api.cert.CertificationDTO;
import com.erp.module.engineering.api.cert.CertificationExpiringEvent;
import com.erp.module.engineering.controller.vo.CertVOs.CertQuery;
import com.erp.module.engineering.controller.vo.CertVOs.CertRow;
import com.erp.module.engineering.controller.vo.CertVOs.CertSave;
import com.erp.module.engineering.controller.vo.CertVOs.MaterialRef;
import com.erp.module.engineering.dal.dataobject.CertificationDO;
import com.erp.module.engineering.dal.dataobject.CertificationMaterialDO;
import com.erp.module.engineering.dal.dataobject.MaterialDO;
import com.erp.module.engineering.dal.mapper.CertificationMapper;
import com.erp.module.engineering.dal.mapper.CertificationMaterialMapper;
import com.erp.module.system.api.file.FileApi;
import com.erp.module.system.api.job.ErpJob;
import com.erp.module.system.api.notify.AlertRaisedEvent;
import com.erp.module.system.api.notify.NotifyApi;
import com.erp.module.system.api.param.ParamApi;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/** 认证证书（需求 05-09）；实现 CertificationApi，每天检查到期提醒 */
@Service
public class CertificationService implements CertificationApi {

    public static final String BIZ_TYPE = "ENG_CERT";
    static final String PARAM_REMIND_DAYS = "eng.cert.remind-days";
    static final int EXPIRING_DAYS = 90;

    private final CertificationMapper mapper;
    private final CertificationMaterialMapper materialMapper;
    private final EngSupport support;
    private final FileApi fileApi;
    private final ParamApi paramApi;
    private final NotifyApi notifyApi;
    private final DomainEventPublisher eventPublisher;

    public CertificationService(CertificationMapper mapper, CertificationMaterialMapper materialMapper, EngSupport support, FileApi fileApi,
                                ParamApi paramApi, NotifyApi notifyApi, DomainEventPublisher eventPublisher) {
        this.mapper = mapper;
        this.materialMapper = materialMapper;
        this.support = support;
        this.fileApi = fileApi;
        this.paramApi = paramApi;
        this.notifyApi = notifyApi;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(CertSave req) {
        CertificationDO c = new CertificationDO();
        c.setCertStatus("VALID");
        fill(c, req, null);
        if (req.fileIds() == null || req.fileIds().isEmpty()) throw new BizException(EngineeringErrorCodes.CERT_FILE_REQUIRED);
        mapper.insert(c);
        saveMaterials(c.getId(), req.materialIds());
        fileApi.bind(req.fileIds(), BIZ_TYPE, c.getId());
        return c.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, CertSave req) {
        CertificationDO c = getOrThrow(id);
        if (req.version() != null) c.setVersion(req.version());
        fill(c, req, id);
        mapper.updateByIdOrFail(c);
        saveMaterials(id, req.materialIds());
        if (req.fileIds() != null && !req.fileIds().isEmpty()) fileApi.bind(req.fileIds(), BIZ_TYPE, id);
        if (fileApi.list(BIZ_TYPE, id).isEmpty()) throw new BizException(EngineeringErrorCodes.CERT_FILE_REQUIRED);
    }

    /** R01：同类型证书编号唯一；到期日期 ≥ 发证日期 */
    private void fill(CertificationDO c, CertSave req, Long id) {
        String no = req.certNo().trim();
        if (mapper.selectCount(new LambdaQueryWrapper<CertificationDO>().eq(CertificationDO::getCertType, req.certType())
                .eq(CertificationDO::getCertNo, no).ne(id != null, CertificationDO::getId, id)) > 0) {
            throw new BizException(EngineeringErrorCodes.CERT_DUPLICATE);
        }
        if (req.expireDate() != null && req.expireDate().isBefore(req.issueDate())) throw new BizException(EngineeringErrorCodes.CERT_DATE_RANGE);
        boolean expireChanged = c.getExpireDate() == null ? req.expireDate() != null : !c.getExpireDate().equals(req.expireDate());
        c.setCertType(req.certType());
        c.setCertNo(no);
        c.setName(req.name().trim());
        c.setIssuingBody(req.issuingBody().trim());
        c.setHolder(EngSupport.trim(req.holder()));
        c.setIssueDate(req.issueDate());
        c.setExpireDate(req.expireDate());
        c.setCountries(req.countries() == null || req.countries().isEmpty() ? null
                : req.countries().stream().map(String::trim).filter(StringUtils::hasText).map(String::toUpperCase).distinct().collect(Collectors.joining(",")));
        c.setScope(EngSupport.trim(req.scope()));
        c.setRemark(EngSupport.trim(req.remark()));
        // 续期后重新提醒
        if (expireChanged) c.setRemindedDays(null);
    }

    private void saveMaterials(Long certId, List<Long> materialIds) {
        materialMapper.deleteByParent(certId);
        if (materialIds == null) return;
        for (Long mid : new LinkedHashSet<>(materialIds)) {
            support.material(mid);
            CertificationMaterialDO x = new CertificationMaterialDO();
            x.setCertificationId(certId);
            x.setMaterialId(mid);
            materialMapper.insert(x);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void revoke(Long id, String reason) {
        CertificationDO c = getOrThrow(id);
        c.setCertStatus("REVOKED");
        c.setRevokeReason(EngSupport.trim(reason));
        mapper.updateByIdOrFail(c);
        support.log(BIZ_TYPE, id, c.getCertNo(), "REVOKE", "撤销", "VALID", "REVOKED", reason);
    }

    /** R04：逻辑删除 */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        getOrThrow(id);
        materialMapper.deleteByParent(id);
        mapper.deleteById(id);
    }

    public CertificationDO getOrThrow(Long id) {
        CertificationDO c = id == null ? null : mapper.selectById(id);
        if (c == null) throw new BizException(EngineeringErrorCodes.CERT_NOT_EXISTS);
        return c;
    }

    // ==================== 查询 ====================

    public PageResult<CertRow> page(CertQuery q) {
        LocalDate today = LocalDate.now();
        LambdaQueryWrapper<CertificationDO> w = new LambdaQueryWrapper<CertificationDO>()
                .likeRight(StringUtils.hasText(q.getCertNo()), CertificationDO::getCertNo, q.getCertNo() == null ? null : q.getCertNo().trim())
                .eq(StringUtils.hasText(q.getCertType()), CertificationDO::getCertType, q.getCertType())
                .ge(q.getExpireFrom() != null, CertificationDO::getExpireDate, q.getExpireFrom())
                .le(q.getExpireTo() != null, CertificationDO::getExpireDate, q.getExpireTo());
        if (q.getMaterialId() != null) {
            w.inSql(CertificationDO::getId, "SELECT certification_id FROM eng_certification_material WHERE deleted = 0 AND material_id = "
                    + q.getMaterialId().longValue());
        }
        String v = q.getValidity();
        if ("REVOKED".equals(v)) w.eq(CertificationDO::getCertStatus, "REVOKED");
        else if ("VALID".equals(v)) w.eq(CertificationDO::getCertStatus, "VALID").and(x -> x.isNull(CertificationDO::getExpireDate).or().ge(CertificationDO::getExpireDate, today));
        else if ("EXPIRING".equals(v)) w.eq(CertificationDO::getCertStatus, "VALID").between(CertificationDO::getExpireDate, today, today.plusDays(EXPIRING_DAYS));
        else if ("EXPIRED".equals(v)) w.eq(CertificationDO::getCertStatus, "VALID").lt(CertificationDO::getExpireDate, today);
        w.orderByAsc(CertificationDO::getExpireDate).orderByDesc(CertificationDO::getId);
        PageResult<CertificationDO> page = mapper.selectPage(q, w);
        return new PageResult<>(toRows(page.list()), page.total());
    }

    public List<CertRow> byMaterial(Long materialId) {
        List<Long> ids = materialMapper.selectList(new LambdaQueryWrapper<CertificationMaterialDO>().eq(CertificationMaterialDO::getMaterialId, materialId))
                .stream().map(CertificationMaterialDO::getCertificationId).toList();
        return ids.isEmpty() ? List.of() : toRows(mapper.selectBatchIds(ids));
    }

    public List<CertRow> listForExport(CertQuery q, int limit) {
        q.setPageNo(1);
        q.setPageSize(Math.min(limit, 100_000));
        return page(q).list();
    }

    private List<CertRow> toRows(List<CertificationDO> list) {
        if (list.isEmpty()) return List.of();
        Map<Long, List<CertificationMaterialDO>> links = materialMapper.selectByParents(list.stream().map(CertificationDO::getId).toList()).stream()
                .collect(Collectors.groupingBy(CertificationMaterialDO::getCertificationId));
        Map<Long, MaterialDO> ms = support.materials(links.values().stream().flatMap(List::stream).map(CertificationMaterialDO::getMaterialId).toList());
        LocalDate today = LocalDate.now();
        return list.stream().map(c -> {
            Long days = c.getExpireDate() == null ? null : ChronoUnit.DAYS.between(today, c.getExpireDate());
            List<MaterialRef> refs = links.getOrDefault(c.getId(), List.of()).stream().map(l -> ms.get(l.getMaterialId())).filter(java.util.Objects::nonNull)
                    .map(m -> new MaterialRef(m.getId(), m.getCode(), m.getName())).toList();
            return new CertRow(c.getId(), c.getCertType(), c.getCertNo(), c.getName(), c.getIssuingBody(), c.getHolder(), c.getIssueDate(), c.getExpireDate(),
                    countries(c.getCountries()), c.getScope(), c.getCertStatus(), validity(c, today), days, refs, fileApi.list(BIZ_TYPE, c.getId()).size(),
                    c.getRevokeReason(), c.getRemark(), c.getVersion());
        }).toList();
    }

    static String validity(CertificationDO c, LocalDate today) {
        if ("REVOKED".equals(c.getCertStatus())) return "REVOKED";
        if (c.getExpireDate() == null) return "LONG_TERM";
        if (c.getExpireDate().isBefore(today)) return "EXPIRED";
        return ChronoUnit.DAYS.between(today, c.getExpireDate()) <= EXPIRING_DAYS ? "EXPIRING" : "VALID";
    }

    static List<String> countries(String csv) {
        return StringUtils.hasText(csv) ? Arrays.asList(csv.split(",")) : List.of();
    }

    // ==================== CertificationApi ====================

    @Override
    public List<CertificationDTO> listValid(Long materialId) {
        LocalDate today = LocalDate.now();
        List<Long> ids = materialMapper.selectList(new LambdaQueryWrapper<CertificationMaterialDO>().eq(CertificationMaterialDO::getMaterialId, materialId))
                .stream().map(CertificationMaterialDO::getCertificationId).toList();
        if (ids.isEmpty()) return List.of();
        return mapper.selectBatchIds(ids).stream().filter(c -> "VALID".equals(c.getCertStatus()) && (c.getExpireDate() == null || !c.getExpireDate().isBefore(today)))
                .map(c -> new CertificationDTO(c.getId(), c.getCertType(), c.getCertNo(), c.getName(), c.getIssuingBody(), c.getIssueDate(), c.getExpireDate(),
                        countries(c.getCountries()))).toList();
    }

    // ==================== 到期提醒（R02） ====================

    /** 每天 08:00：到期前 N 天（参数，默认 90/30/7）各提醒一次，已过期当天再提醒一次 */
    @ErpJob(code = "ENG_CERT_EXPIRY", name = "认证证书到期提醒", cron = "0 0 8 * * ?")
    @Transactional(rollbackFor = Exception.class)
    public String remindExpiring() {
        LocalDate today = LocalDate.now();
        TreeSet<Integer> thresholds = new TreeSet<>();
        for (String s : String.valueOf(paramApi.getString(PARAM_REMIND_DAYS)).split("[,，\\s]+")) {
            if (s.matches("\\d+")) thresholds.add(Integer.valueOf(s));
        }
        int max = thresholds.isEmpty() ? 90 : thresholds.last();
        List<CertificationDO> list = mapper.selectList(new LambdaQueryWrapper<CertificationDO>().eq(CertificationDO::getCertStatus, "VALID")
                .isNotNull(CertificationDO::getExpireDate).le(CertificationDO::getExpireDate, today.plusDays(max)));
        int sent = 0;
        for (CertificationDO c : list) {
            long days = ChronoUnit.DAYS.between(today, c.getExpireDate());
            Set<String> reminded = new LinkedHashSet<>(StringUtils.hasText(c.getRemindedDays()) ? Arrays.asList(c.getRemindedDays().split(",")) : List.of());
            // 命中的最小阈值（如剩 25 天命中 30），已过期记为 0
            String key = days < 0 ? "0" : thresholds.stream().filter(t -> days <= t).findFirst().map(String::valueOf).orElse(null);
            if (key == null || reminded.contains(key)) continue;
            reminded.add(key);
            c.setRemindedDays(String.join(",", reminded));
            mapper.updateByIdOrFail(c);
            String title = days < 0 ? c.getCertType() + " 证书 " + c.getCertNo() + " 已过期"
                    : c.getCertType() + " 证书 " + c.getCertNo() + " 将在 " + days + " 天后到期";
            notifyApi.alert(new AlertRaisedEvent("ENG_CERT:" + c.getId(), "ENG_CERT_EXPIRING",
                    days < 0 ? AlertRaisedEvent.Level.CRITICAL : AlertRaisedEvent.Level.WARNING, List.of(), "eng:cert:update", BIZ_TYPE, c.getId(),
                    title, c.getName() + "，到期日 " + c.getExpireDate(), "/engineering/cert"));
            eventPublisher.publish(new CertificationExpiringEvent(c.getId(), c.getCertType(), c.getCertNo(), c.getExpireDate(), days));
            sent++;
        }
        return "发出提醒 " + sent + " 条";
    }

}
