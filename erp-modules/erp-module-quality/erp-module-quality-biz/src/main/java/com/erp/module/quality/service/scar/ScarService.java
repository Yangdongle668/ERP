package com.erp.module.quality.service.scar;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.common.exception.BizException;
import com.erp.common.result.PageResult;
import com.erp.framework.event.DomainEventPublisher;
import com.erp.module.engineering.api.material.MaterialDTO;
import com.erp.module.purchase.api.supplier.SupplierDTO;
import com.erp.module.quality.api.QualityErrorCodes;
import com.erp.module.quality.api.scar.ScarClosedEvent;
import com.erp.module.quality.config.QualityModuleConfig;
import com.erp.module.quality.controller.vo.ScarVOs.ReplyReq;
import com.erp.module.quality.controller.vo.ScarVOs.ScarDetail;
import com.erp.module.quality.controller.vo.ScarVOs.ScarQuery;
import com.erp.module.quality.controller.vo.ScarVOs.ScarRow;
import com.erp.module.quality.controller.vo.ScarVOs.ScarSave;
import com.erp.module.quality.controller.vo.ScarVOs.VerifyReq;
import com.erp.module.quality.dal.dataobject.QcNcrDO;
import com.erp.module.quality.dal.dataobject.QcScarDO;
import com.erp.module.quality.dal.mapper.QcNcrMapper;
import com.erp.module.quality.dal.mapper.QcScarMapper;
import com.erp.module.quality.service.QcAction;
import com.erp.module.quality.service.QcStateMachines;
import com.erp.module.quality.service.QcSupport;
import com.erp.module.quality.service.ScarStatus;
import com.erp.module.system.api.user.UserDTO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/** SCAR 供应商纠正措施要求（10-06） */
@Service
public class ScarService {

    public static final String BIZ_TYPE = QualityModuleConfig.SCAR;
    static final List<String> OPEN = List.of(ScarStatus.DRAFT.name(), ScarStatus.SENT.name(), ScarStatus.REPLIED.name(), ScarStatus.VERIFYING.name());

    private final QcScarMapper mapper;
    private final QcNcrMapper ncrMapper;
    private final DomainEventPublisher eventPublisher;
    private final QcSupport support;

    public ScarService(QcScarMapper mapper, QcNcrMapper ncrMapper, DomainEventPublisher eventPublisher, QcSupport support) {
        this.mapper = mapper;
        this.ncrMapper = ncrMapper;
        this.eventPublisher = eventPublisher;
        this.support = support;
    }

    // ==================== 查询 ====================

    public PageResult<ScarRow> page(ScarQuery q) {
        PageResult<QcScarDO> p = mapper.selectPage(q, query(q));
        return new PageResult<>(rows(p.list()), p.total());
    }

    private LambdaQueryWrapper<QcScarDO> query(ScarQuery q) {
        List<String> statuses = new ArrayList<>();
        if (StringUtils.hasText(q.getStatuses())) {
            for (String s : q.getStatuses().split(",")) {
                if ("OPEN".equals(s)) statuses.addAll(OPEN);
                else statuses.add(s);
            }
        }
        return new LambdaQueryWrapper<QcScarDO>()
                .like(StringUtils.hasText(q.getDocNo()), QcScarDO::getDocNo, q.getDocNo())
                .eq(q.getSupplierId() != null, QcScarDO::getSupplierId, q.getSupplierId())
                .eq(q.getMaterialId() != null, QcScarDO::getMaterialId, q.getMaterialId())
                .in(!statuses.isEmpty(), QcScarDO::getScarStatus, statuses)
                .eq(Boolean.TRUE.equals(q.getOverdue()), QcScarDO::getScarStatus, ScarStatus.SENT.name())
                .lt(Boolean.TRUE.equals(q.getOverdue()), QcScarDO::getReplyDueDate, LocalDate.now())
                .orderByDesc(QcScarDO::getCreatedAt).orderByDesc(QcScarDO::getId);
    }

    private List<ScarRow> rows(List<QcScarDO> list) {
        Map<Long, SupplierDTO> sups = support.suppliers(list.stream().map(QcScarDO::getSupplierId).toList());
        Map<Long, MaterialDTO> ms = support.materials(list.stream().map(QcScarDO::getMaterialId).toList());
        Map<Long, UserDTO> users = support.users(list.stream().map(QcScarDO::getOwnerId).toList());
        List<Long> ncrIds = list.stream().map(QcScarDO::getNcrId).filter(Objects::nonNull).toList();
        Map<Long, String> ncrNos = ncrIds.isEmpty() ? new java.util.HashMap<>() : ncrMapper.selectBatchIds(ncrIds).stream().collect(Collectors.toMap(QcNcrDO::getId, QcNcrDO::getDocNo));
        return list.stream().map(s -> {
            MaterialDTO m = ms.get(s.getMaterialId());
            return new ScarRow(s.getId(), s.getDocNo(), s.getSupplierId(), sups.containsKey(s.getSupplierId()) ? sups.get(s.getSupplierId()).name() : null,
                    s.getMaterialId(), m == null ? null : m.code(), m == null ? null : m.name(), QcSupport.limit(s.getProblemDescription(), 80), s.getSentAt(),
                    s.getReplyDueDate(), overdue(s), s.getRepliedAt(), s.getInvalidCount() == null ? 0 : s.getInvalidCount(), s.getScarStatus(),
                    QcSupport.name(users, s.getOwnerId()), s.getNcrId(), ncrNos.get(s.getNcrId()), s.getCreatedAt());
        }).toList();
    }

    static boolean overdue(QcScarDO s) {
        return ScarStatus.SENT.name().equals(s.getScarStatus()) && s.getReplyDueDate() != null && s.getReplyDueDate().isBefore(LocalDate.now());
    }

    public ScarDetail detail(Long id) {
        QcScarDO s = get(id);
        SupplierDTO sup = support.suppliers(List.of(s.getSupplierId())).get(s.getSupplierId());
        MaterialDTO m = support.materials(List.of(s.getMaterialId())).get(s.getMaterialId());
        QcNcrDO n = s.getNcrId() == null ? null : ncrMapper.selectById(s.getNcrId());
        return new ScarDetail(s.getId(), s.getDocNo(), s.getSupplierId(), sup == null ? null : sup.name(), s.getNcrId(), n == null ? null : n.getDocNo(),
                s.getMaterialId(), m == null ? null : m.code(), m == null ? null : m.name(), s.getBatchNo(), s.getProblemDescription(), s.getRequirement(),
                s.getReplyDueDate(), overdue(s), s.getSentAt(), s.getReplyContent(), s.getRepliedAt(), s.getVerifyPlan(), s.getVerifyResult(),
                s.getInvalidCount() == null ? 0 : s.getInvalidCount(), s.getScarStatus(), s.getOwnerId(), support.userName(s.getOwnerId()),
                s.getCancelReason(), s.getClosedAt(), s.getCreatedAt(), s.getVersion());
    }

    public QcScarDO get(Long id) {
        QcScarDO s = id == null ? null : mapper.selectById(id);
        if (s == null) throw BizException.of(QualityErrorCodes.NOT_EXISTS, "SCAR");
        return s;
    }

    // ==================== 新建 / 编辑 ====================

    @Transactional(rollbackFor = Exception.class)
    public Long create(ScarSave req) {
        QcScarDO s = new QcScarDO();
        s.setDocNo(support.nextNo(BIZ_TYPE));
        s.setDocDate(LocalDate.now());
        s.setInvalidCount(0);
        s.setScarStatus(ScarStatus.DRAFT.name());
        s.setStatus(ScarStatus.DRAFT.docStatus());
        support.fillOwner(s, null);
        fill(s, req);
        mapper.insert(s);
        support.bindFiles(req.fileIds(), BIZ_TYPE, s.getId());
        support.log(BIZ_TYPE, s.getId(), s.getDocNo(), QcAction.CREATE.name(), QcAction.CREATE.label(), null, s.getScarStatus(), null);
        return s.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, ScarSave req) {
        QcScarDO s = get(id);
        if (!ScarStatus.DRAFT.name().equals(s.getScarStatus())) throw BizException.of(QualityErrorCodes.STATUS_NOT_ALLOWED, label(s), "修改");
        if (req.version() != null) s.setVersion(req.version());
        fill(s, req);
        mapper.updateByIdOrFail(s);
        support.bindFiles(req.fileIds(), BIZ_TYPE, s.getId());
    }

    private void fill(QcScarDO s, ScarSave req) {
        if (support.suppliers(List.of(req.supplierId())).isEmpty()) throw BizException.of(QualityErrorCodes.NOT_EXISTS, "供应商");
        support.material(req.materialId());
        s.setSupplierId(req.supplierId());
        s.setNcrId(req.ncrId());
        s.setSourceType(req.ncrId() == null ? null : QualityModuleConfig.NCR);
        s.setSourceId(req.ncrId());
        s.setMaterialId(req.materialId());
        s.setBatchNo(QcSupport.trim(req.batchNo()));
        s.setProblemDescription(req.problemDescription().trim());
        s.setRequirement(req.requirement().trim());
        s.setReplyDueDate(req.replyDueDate());
    }

    /** NCR 生成 SCAR（责任为供应商） */
    @Transactional(rollbackFor = Exception.class)
    public QcScarDO createFromNcr(QcNcrDO n) {
        if (n.getSupplierId() == null) throw new BizException(QualityErrorCodes.NCR_SCAR_NO_SUPPLIER);
        int days = support.params().getInt(QualityModuleConfig.P_SCAR_REPLY_DAYS);
        QcScarDO s = new QcScarDO();
        s.setDocNo(support.nextNo(BIZ_TYPE));
        s.setDocDate(LocalDate.now());
        s.setSupplierId(n.getSupplierId());
        s.setNcrId(n.getId());
        s.setSourceType(QualityModuleConfig.NCR);
        s.setSourceId(n.getId());
        s.setSourceNo(n.getDocNo());
        s.setMaterialId(n.getMaterialId());
        s.setBatchNo(n.getBatchNo());
        s.setProblemDescription(n.getDefectDescription());
        s.setRequirement("请于 " + days + " 天内提交 8D 改善报告：临时围堵措施、根本原因分析、永久纠正措施及实施计划，并提供改善后的出货检验数据。");
        s.setInvalidCount(0);
        s.setScarStatus(ScarStatus.DRAFT.name());
        s.setStatus(ScarStatus.DRAFT.docStatus());
        support.fillOwner(s, null);
        mapper.insert(s);
        support.log(BIZ_TYPE, s.getId(), s.getDocNo(), QcAction.CREATE.name(), QcAction.CREATE.label(), null, s.getScarStatus(), "来源 " + n.getDocNo());
        return s;
    }

    // ==================== 流转 ====================

    /** 发出：回复期限默认发出日 + 参数天数（QC-SCAR-T01） */
    @Transactional(rollbackFor = Exception.class)
    public void send(Long id) {
        QcScarDO s = get(id);
        s.setSentAt(LocalDateTime.now());
        if (s.getReplyDueDate() == null || s.getReplyDueDate().isBefore(LocalDate.now())) {
            s.setReplyDueDate(LocalDate.now().plusDays(support.params().getInt(QualityModuleConfig.P_SCAR_REPLY_DAYS)));
        }
        fire(s, QcAction.SEND, "回复期限 " + s.getReplyDueDate());
    }

    /** 登记回复（QC-SCAR-R01：必须上传供应商回复文件） */
    @Transactional(rollbackFor = Exception.class)
    public void reply(Long id, ReplyReq req) {
        QcScarDO s = get(id);
        if (!ScarStatus.SENT.name().equals(s.getScarStatus())) throw BizException.of(QualityErrorCodes.STATUS_NOT_ALLOWED, label(s), "登记回复");
        if (req.fileIds() == null || req.fileIds().isEmpty()) throw new BizException(QualityErrorCodes.SCAR_REPLY_FILE);
        support.bindFiles(req.fileIds(), BIZ_TYPE, id);
        s.setReplyContent(req.content().trim());
        s.setRepliedAt(req.repliedAt() != null ? req.repliedAt() : LocalDateTime.now());
        fire(s, QcAction.REPLY, null);
        support.resolve("QC_SCAR_OVERDUE_" + id);
    }

    @Transactional(rollbackFor = Exception.class)
    public void startVerify(Long id, String plan) {
        QcScarDO s = get(id);
        s.setVerifyPlan(QcSupport.limit(QcSupport.requireText(plan, "验证方式"), 512));
        fire(s, QcAction.START_VERIFY, s.getVerifyPlan());
    }

    /** 验证：有效 → 结案；无效 → 退回已发出并要求重新回复（QC-SCAR-T03） */
    @Transactional(rollbackFor = Exception.class)
    public void verify(Long id, VerifyReq req) {
        QcScarDO s = get(id);
        boolean ok = "EFFECTIVE".equals(req.result());
        if (!ok && !"INEFFECTIVE".equals(req.result())) throw BizException.of(QualityErrorCodes.NOT_EXISTS, "验证结果 " + req.result());
        s.setVerifyResult(req.result());
        if (ok) {
            s.setClosedAt(LocalDateTime.now());
            fire(s, QcAction.VERIFY_OK, req.remark());
            boolean late = s.getRepliedAt() != null && s.getReplyDueDate() != null && s.getRepliedAt().toLocalDate().isAfter(s.getReplyDueDate());
            eventPublisher.publish(new ScarClosedEvent(s.getId(), s.getDocNo(), s.getSupplierId(), s.getMaterialId(), s.getVerifyResult(),
                    s.getInvalidCount() == null ? 0 : s.getInvalidCount(), late));
        } else {
            s.setInvalidCount((s.getInvalidCount() == null ? 0 : s.getInvalidCount()) + 1);
            s.setSentAt(LocalDateTime.now());
            s.setReplyDueDate(LocalDate.now().plusDays(support.params().getInt(QualityModuleConfig.P_SCAR_REPLY_DAYS)));
            fire(s, QcAction.VERIFY_FAIL, "验证无效，要求重新回复" + (req.remark() == null ? "" : "：" + req.remark()));
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id, String reason) {
        QcScarDO s = get(id);
        s.setCancelReason(QcSupport.limit(QcSupport.requireText(reason, "取消原因"), 256));
        fire(s, QcAction.CANCEL, s.getCancelReason());
        support.resolve("QC_SCAR_OVERDUE_" + id);
    }

    private static String label(QcScarDO s) {
        return ScarStatus.valueOf(s.getScarStatus()).label();
    }

    private void fire(QcScarDO s, QcAction action, String reason) {
        ScarStatus from = ScarStatus.valueOf(s.getScarStatus());
        ScarStatus to = QcStateMachines.SCAR.fire(from, action);
        s.setScarStatus(to.name());
        s.setStatus(to.docStatus());
        mapper.updateByIdOrFail(s);
        support.log(BIZ_TYPE, s.getId(), s.getDocNo(), action.name(), action.label(), from.name(), to.name(), reason);
    }

    // ==================== 提醒（QC-SCAR-R02） ====================

    /** 逾期未回复每 3 天提醒 SQE，并抄送负责采购员 */
    @Transactional(rollbackFor = Exception.class)
    public int remind(LocalDate today) {
        int n = 0;
        for (QcScarDO s : mapper.selectList(new LambdaQueryWrapper<QcScarDO>().eq(QcScarDO::getScarStatus, ScarStatus.SENT.name())
                .lt(QcScarDO::getReplyDueDate, today))) {
            if (s.getLastRemindDate() != null && ChronoUnit.DAYS.between(s.getLastRemindDate(), today) < 3) continue;
            List<Long> to = new ArrayList<>();
            to.add(s.getOwnerId());
            SupplierDTO sup = support.suppliers(List.of(s.getSupplierId())).get(s.getSupplierId());
            if (sup != null && sup.buyerId() != null) to.add(sup.buyerId());
            support.message(to, "SCAR 逾期未回复：" + s.getDocNo(), (sup == null ? "" : sup.name()) + " 回复期限 " + s.getReplyDueDate() + "，已逾期 "
                    + ChronoUnit.DAYS.between(s.getReplyDueDate(), today) + " 天", "/quality/scar/" + s.getId());
            s.setLastRemindDate(today);
            mapper.updateByIdOrFail(s);
            n++;
        }
        return n;
    }

    /** 统计：供应商在期间内的 SCAR */
    public List<QcScarDO> bySupplier(Long supplierId, LocalDate from, LocalDate to) {
        return mapper.selectList(new LambdaQueryWrapper<QcScarDO>().eq(supplierId != null, QcScarDO::getSupplierId, supplierId)
                .ne(QcScarDO::getScarStatus, ScarStatus.CANCELED.name()).isNotNull(QcScarDO::getSentAt)
                .ge(from != null, QcScarDO::getSentAt, from == null ? null : from.atStartOfDay())
                .lt(to != null, QcScarDO::getSentAt, to == null ? null : to.plusDays(1).atStartOfDay()));
    }

    // ==================== 打印 ====================

    public Map<String, Object> printData(Long id) {
        ScarDetail d = detail(id);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("docNo", d.docNo());
        m.put("supplierName", d.supplierName());
        m.put("materialCode", d.materialCode());
        m.put("materialName", d.materialName());
        m.put("batchNo", d.batchNo());
        m.put("problemDescription", d.problemDescription());
        m.put("requirement", d.requirement());
        m.put("replyDueDate", d.replyDueDate());
        m.put("sentAt", d.sentAt());
        m.put("ownerName", d.ownerName());
        m.put("status", ScarStatus.DRAFT.name().equals(d.status()) ? "DRAFT" : d.status());
        return m;
    }

    static List<String> split(String s) {
        return s == null ? List.of() : Arrays.asList(s.split(","));
    }
}
