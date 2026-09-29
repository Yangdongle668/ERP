package com.erp.module.quality.service.complaint;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.common.exception.BizException;
import com.erp.common.result.PageResult;
import com.erp.framework.event.DomainEventPublisher;
import com.erp.module.crm.api.customer.ContactDTO;
import com.erp.module.crm.api.customer.CustomerDTO;
import com.erp.module.engineering.api.material.MaterialDTO;
import com.erp.module.quality.api.QualityErrorCodes;
import com.erp.module.quality.api.complaint.ComplaintClaimAgreedEvent;
import com.erp.module.quality.api.complaint.ComplaintCreatedEvent;
import com.erp.module.quality.config.QualityModuleConfig;
import com.erp.module.quality.controller.vo.ComplaintVOs.ComplaintDetail;
import com.erp.module.quality.controller.vo.ComplaintVOs.ComplaintQuery;
import com.erp.module.quality.controller.vo.ComplaintVOs.ComplaintRow;
import com.erp.module.quality.controller.vo.ComplaintVOs.ComplaintSave;
import com.erp.module.quality.controller.vo.ComplaintVOs.HandlingReq;
import com.erp.module.quality.controller.vo.ComplaintVOs.NcrReq;
import com.erp.module.quality.controller.vo.ComplaintVOs.ReplyReq;
import com.erp.module.quality.dal.dataobject.QcCapaDO;
import com.erp.module.quality.dal.dataobject.QcComplaintDO;
import com.erp.module.quality.dal.dataobject.QcNcrDO;
import com.erp.module.quality.dal.mapper.QcCapaMapper;
import com.erp.module.quality.dal.mapper.QcComplaintMapper;
import com.erp.module.quality.dal.mapper.QcNcrMapper;
import com.erp.module.quality.service.ComplaintStatus;
import com.erp.module.quality.service.QcAction;
import com.erp.module.quality.service.QcStateMachines;
import com.erp.module.quality.service.QcSupport;
import com.erp.module.quality.service.capa.CapaService;
import com.erp.module.quality.service.ncr.NcrService;
import com.erp.module.system.api.notify.AlertRaisedEvent;
import com.erp.module.system.api.user.UserDTO;
import com.erp.module.system.api.workflow.ApprovalCompletedEvent;
import com.erp.module.system.api.workflow.StartResult;
import com.erp.module.system.api.workflow.WorkflowApi;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 客诉（10-05） */
@Service
public class ComplaintService {

    public static final String BIZ_TYPE = QualityModuleConfig.COMPLAINT;
    public static final List<String> SEVERITIES = List.of("CRITICAL", "MAJOR", "MINOR");
    public static final List<String> HANDLINGS = List.of("NONE", "RETURN", "REPLACE", "CREDIT", "REWORK_ONSITE");
    static final List<String> OPEN = List.of(ComplaintStatus.OPEN.name(), ComplaintStatus.ANALYZING.name(), ComplaintStatus.REPLIED.name(),
            ComplaintStatus.CLOSING.name());

    private final QcComplaintMapper mapper;
    private final QcCapaMapper capaMapper;
    private final QcNcrMapper ncrMapper;
    private final CapaService capaService;
    private final NcrService ncrService;
    private final WorkflowApi workflowApi;
    private final DomainEventPublisher eventPublisher;
    private final QcSupport support;

    public ComplaintService(QcComplaintMapper mapper, QcCapaMapper capaMapper, QcNcrMapper ncrMapper, CapaService capaService, NcrService ncrService,
                            WorkflowApi workflowApi, DomainEventPublisher eventPublisher, QcSupport support) {
        this.mapper = mapper;
        this.capaMapper = capaMapper;
        this.ncrMapper = ncrMapper;
        this.capaService = capaService;
        this.ncrService = ncrService;
        this.workflowApi = workflowApi;
        this.eventPublisher = eventPublisher;
        this.support = support;
    }

    // ==================== 查询（QC-CPL-R05 按 CRM 客户负责人做数据范围） ====================

    public PageResult<ComplaintRow> page(ComplaintQuery q) {
        IPage<QcComplaintDO> p = mapper.selectScopedPage(new Page<>(q.getPageNo(), q.getPageSize()), query(q));
        return new PageResult<>(rows(p.getRecords()), p.getTotal());
    }

    public List<ComplaintRow> list(ComplaintQuery q) {
        return rows(mapper.selectScopedList(query(q)));
    }

    private LambdaQueryWrapper<QcComplaintDO> query(ComplaintQuery q) {
        List<String> statuses = new ArrayList<>();
        if (StringUtils.hasText(q.getStatuses())) {
            for (String s : q.getStatuses().split(",")) {
                if ("OPEN".equals(s)) statuses.addAll(OPEN);
                else statuses.add(s);
            }
        }
        return new LambdaQueryWrapper<QcComplaintDO>().eq(QcComplaintDO::getDeleted, false)
                .like(StringUtils.hasText(q.getDocNo()), QcComplaintDO::getDocNo, q.getDocNo())
                .eq(q.getCustomerId() != null, QcComplaintDO::getCustomerId, q.getCustomerId())
                .eq(q.getMaterialId() != null, QcComplaintDO::getMaterialId, q.getMaterialId())
                .eq(StringUtils.hasText(q.getComplaintType()), QcComplaintDO::getComplaintType, q.getComplaintType())
                .eq(StringUtils.hasText(q.getSeverity()), QcComplaintDO::getSeverity, q.getSeverity())
                .in(!statuses.isEmpty(), QcComplaintDO::getComplaintStatus, statuses)
                .eq(q.getQeId() != null, QcComplaintDO::getQeId, q.getQeId())
                .ge(q.getReceivedFrom() != null, QcComplaintDO::getReceivedAt, q.getReceivedFrom() == null ? null : q.getReceivedFrom().atStartOfDay())
                .lt(q.getReceivedTo() != null, QcComplaintDO::getReceivedAt, q.getReceivedTo() == null ? null : q.getReceivedTo().plusDays(1).atStartOfDay())
                .orderByDesc(QcComplaintDO::getReceivedAt).orderByDesc(QcComplaintDO::getId);
    }

    private List<ComplaintRow> rows(List<QcComplaintDO> list) {
        Map<Long, CustomerDTO> cus = support.customers(list.stream().map(QcComplaintDO::getCustomerId).toList());
        Map<Long, MaterialDTO> ms = support.materials(list.stream().map(QcComplaintDO::getMaterialId).toList());
        List<Long> uids = new ArrayList<>(list.stream().map(QcComplaintDO::getQeId).toList());
        uids.addAll(list.stream().map(QcComplaintDO::getSalesOwnerId).toList());
        Map<Long, UserDTO> users = support.users(uids);
        return list.stream().map(c -> {
            MaterialDTO m = ms.get(c.getMaterialId());
            return new ComplaintRow(c.getId(), c.getDocNo(), c.getCustomerId(), QcSupport.customerName(cus.get(c.getCustomerId())), c.getMaterialId(),
                    m == null ? null : m.code(), m == null ? null : m.name(), c.getComplaintType(), c.getSeverity(), c.getComplaintQty(), c.getReceivedAt(),
                    c.getReplyDueDate(), replyOverdue(c), c.getRepliedAt(), c.getQeId(), QcSupport.name(users, c.getQeId()), c.getHandling(),
                    c.getComplaintStatus(), QcSupport.name(users, c.getSalesOwnerId()), c.getCreatedAt());
        }).toList();
    }

    static boolean replyOverdue(QcComplaintDO c) {
        return c.getRepliedAt() == null && OPEN.contains(c.getComplaintStatus()) && c.getReplyDueDate() != null && c.getReplyDueDate().isBefore(LocalDate.now());
    }

    public ComplaintDetail detail(Long id) {
        QcComplaintDO c = get(id);
        CustomerDTO cus = support.customers(List.of(c.getCustomerId())).get(c.getCustomerId());
        MaterialDTO m = c.getMaterialId() == null ? null : support.materials(List.of(c.getMaterialId())).get(c.getMaterialId());
        String contactName = null;
        if (c.getContactId() != null) {
            contactName = support.customerApi().getContacts(c.getCustomerId()).stream().filter(x -> x.id().equals(c.getContactId()))
                    .map(ContactDTO::name).findFirst().orElse(null);
        }
        QcCapaDO capa = c.getCapaId() == null ? null : capaMapper.selectById(c.getCapaId());
        QcNcrDO ncr = c.getNcrId() == null ? null : ncrMapper.selectById(c.getNcrId());
        Map<Long, UserDTO> users = support.users(java.util.Arrays.asList(c.getQeId(), c.getSalesOwnerId()));
        return new ComplaintDetail(c.getId(), c.getDocNo(), c.getCustomerId(), QcSupport.customerName(cus), c.getContactId(), contactName, c.getComplaintType(),
                c.getSeverity(), c.getMaterialId(), m == null ? null : m.code(), m == null ? null : m.name(), c.getCustomerPartNo(), c.getOrderNo(),
                c.getShipmentNo(), c.getBatchNo(), c.getSerialNos(), c.getComplaintQty(), c.getDescription(), c.getReceivedAt(), c.getReplyDueDate(),
                replyOverdue(c), c.getQeId(), QcSupport.name(users, c.getQeId()), c.getSalesOwnerId(), QcSupport.name(users, c.getSalesOwnerId()),
                c.getRootCause(), c.getReplyContent(), c.getRepliedAt(), c.getHandling(), c.getHandlingRemark(), c.getClaimAmount(), c.getAgreedAmount(),
                c.getCurrency(), c.getComplaintStatus(), c.getCapaId(), capa == null ? null : capa.getDocNo(), capa == null ? null : capa.getCapaStatus(),
                c.getNcrId(), ncr == null ? null : ncr.getDocNo(), ncr == null ? null : ncr.getStatus().name(), c.getReturnId(), closeMissing(c),
                workflowApi.isRunning(QualityModuleConfig.COMPLAINT_CLOSE, id), c.getCancelReason(), c.getClosedAt(), c.getCreatedAt(), c.getVersion());
    }

    public QcComplaintDO get(Long id) {
        QcComplaintDO c = id == null ? null : mapper.selectById(id);
        if (c == null) throw BizException.of(QualityErrorCodes.NOT_EXISTS, "客诉");
        return c;
    }

    // ==================== 登记 / 编辑 ====================

    /** QC-CPL-R01：致命客诉立即通知品质主管和管理层，并自动生成 CAPA */
    @Transactional(rollbackFor = Exception.class)
    public Long create(ComplaintSave req) {
        QcComplaintDO c = new QcComplaintDO();
        c.setDocNo(support.nextNo(BIZ_TYPE));
        c.setDocDate(LocalDate.now());
        c.setComplaintStatus(ComplaintStatus.OPEN.name());
        c.setStatus(ComplaintStatus.OPEN.docStatus());
        support.fillOwner(c, null);
        fill(c, req);
        mapper.insert(c);
        support.bindFiles(req.fileIds(), BIZ_TYPE, c.getId());
        support.log(BIZ_TYPE, c.getId(), c.getDocNo(), QcAction.CREATE.name(), QcAction.CREATE.label(), null, c.getComplaintStatus(), null);
        eventPublisher.publish(new ComplaintCreatedEvent(c.getId(), c.getDocNo(), c.getCustomerId(), c.getMaterialId(), c.getSeverity(), c.getComplaintType()));
        String route = "/quality/complaint/" + c.getId();
        String customer = QcSupport.customerName(support.customers(List.of(c.getCustomerId())).get(c.getCustomerId()));
        if (!c.getQeId().equals(support.currentUser())) {
            support.message(List.of(c.getQeId()), "新客诉：" + c.getDocNo(), customer + "，回复期限 " + c.getReplyDueDate(), route);
        }
        if ("CRITICAL".equals(c.getSeverity())) {
            List<Long> to = new ArrayList<>(support.managers());
            to.addAll(support.params().getUserIds(QualityModuleConfig.P_EXECUTIVES));
            support.alert("QC_CPL_CRITICAL_" + c.getId(), AlertRaisedEvent.Level.CRITICAL, to, BIZ_TYPE, c.getId(), "致命客诉：" + c.getDocNo() + " " + customer,
                    QcSupport.limit(c.getDescription(), 500), route);
            QcCapaDO capa = capaService.createFromComplaint(get(c.getId()));
            QcComplaintDO fresh = get(c.getId());
            fresh.setCapaId(capa.getId());
            mapper.updateByIdOrFail(fresh);
        }
        return c.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, ComplaintSave req) {
        QcComplaintDO c = get(id);
        requireOpen(c, "修改");
        if (req.version() != null) c.setVersion(req.version());
        fill(c, req);
        mapper.updateByIdOrFail(c);
        support.bindFiles(req.fileIds(), BIZ_TYPE, id);
    }

    private void fill(QcComplaintDO c, ComplaintSave req) {
        CustomerDTO cus = support.customerApi().getCustomer(req.customerId()).orElseThrow(() -> BizException.of(QualityErrorCodes.NOT_EXISTS, "客户"));
        support.dictApi().validate("qc_complaint_type", req.complaintType(), "客诉类型");
        if (req.severity() == null || !SEVERITIES.contains(req.severity())) throw BizException.of(QualityErrorCodes.NOT_EXISTS, "严重度 " + req.severity());
        if (req.materialId() != null) support.material(req.materialId());
        c.setCustomerId(cus.id());
        c.setSalesOwnerId(cus.ownerId());
        c.setSalesDeptId(cus.deptId());
        c.setContactId(req.contactId());
        c.setComplaintType(req.complaintType());
        c.setSeverity(req.severity());
        c.setMaterialId(req.materialId());
        c.setCustomerPartNo(QcSupport.trim(req.customerPartNo()));
        c.setOrderNo(QcSupport.trim(req.orderNo()));
        c.setShipmentNo(QcSupport.trim(req.shipmentNo()));
        c.setBatchNo(QcSupport.trim(req.batchNo()));
        c.setSerialNos(QcSupport.trim(req.serialNos()));
        c.setComplaintQty(req.complaintQty());
        c.setDescription(req.description().trim());
        c.setReceivedAt(req.receivedAt());
        c.setReplyDueDate(req.replyDueDate() != null ? req.replyDueDate()
                : req.receivedAt().toLocalDate().plusDays(support.params().getInt(QualityModuleConfig.P_COMPLAINT_REPLY_DAYS)));
        c.setQeId(req.qeId());
    }

    // ==================== 处理 ====================

    @Transactional(rollbackFor = Exception.class)
    public void start(Long id) {
        fire(get(id), QcAction.START, null);
    }

    /** 记录回复（首次回复时间用于及时率统计） */
    @Transactional(rollbackFor = Exception.class)
    public void reply(Long id, ReplyReq req) {
        QcComplaintDO c = get(id);
        c.setReplyContent(req.content().trim());
        if (StringUtils.hasText(req.rootCause())) c.setRootCause(req.rootCause().trim());
        if (c.getRepliedAt() == null) c.setRepliedAt(req.repliedAt() != null ? req.repliedAt() : LocalDateTime.now());
        support.bindFiles(req.fileIds(), BIZ_TYPE, id);
        fire(c, QcAction.REPLY, null);
        support.resolve("QC_CPL_REPLY_" + id);
    }

    @Transactional(rollbackFor = Exception.class)
    public void handling(Long id, HandlingReq req) {
        QcComplaintDO c = get(id);
        requireOpen(c, "登记处理结果");
        if (req.handling() == null || !HANDLINGS.contains(req.handling())) throw BizException.of(QualityErrorCodes.NOT_EXISTS, "处理方式 " + req.handling());
        c.setHandling(req.handling());
        c.setClaimAmount(req.claimAmount());
        c.setAgreedAmount(req.agreedAmount());
        c.setCurrency(QcSupport.trim(req.currency()));
        c.setHandlingRemark(QcSupport.limit(req.remark(), 512));
        if (req.returnId() != null) c.setReturnId(req.returnId());
        mapper.updateByIdOrFail(c);
        support.log(BIZ_TYPE, id, c.getDocNo(), QcAction.HANDLING.name(), QcAction.HANDLING.label(), c.getComplaintStatus(), c.getComplaintStatus(),
                req.handling() + (req.agreedAmount() == null ? "" : " " + QcSupport.plain(req.agreedAmount()) + " " + (req.currency() == null ? "" : req.currency())));
    }

    @Transactional(rollbackFor = Exception.class)
    public Long createNcr(Long id, NcrReq req) {
        QcComplaintDO c = get(id);
        requireOpen(c, "生成 NCR");
        if (c.getNcrId() != null) throw BizException.of(QualityErrorCodes.NCR_FOLLOW_EXISTS, "NCR", ncrMapper.selectById(c.getNcrId()).getDocNo());
        if (c.getMaterialId() == null) throw BizException.of(QualityErrorCodes.REASON_REQUIRED, "客诉物料");
        BigDecimal qty = req != null && req.qty() != null ? req.qty() : c.getComplaintQty();
        QcNcrDO n = ncrService.createExternal("COMPLAINT", c.getDocNo(), c.getMaterialId(), c.getBatchNo(), null, c.getCustomerId(), qty,
                c.getDescription(), List.of(), "CRITICAL".equals(c.getSeverity()) ? "CRITICAL" : "MINOR".equals(c.getSeverity()) ? "MINOR" : "MAJOR",
                req != null && req.responsibility() != null ? req.responsibility() : "UNKNOWN", List.of());
        ncrService.linkComplaint(n.getId(), id);
        if (c.getCapaId() != null) ncrService.linkCapa(n.getId(), c.getCapaId());
        c = get(id);
        c.setNcrId(n.getId());
        mapper.updateByIdOrFail(c);
        return n.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public Long createCapa(Long id) {
        QcComplaintDO c = get(id);
        requireOpen(c, "生成 CAPA");
        if (c.getCapaId() != null) throw BizException.of(QualityErrorCodes.NCR_FOLLOW_EXISTS, "CAPA", capaMapper.selectById(c.getCapaId()).getDocNo());
        QcCapaDO capa = capaService.createFromComplaint(c);
        c = get(id);
        c.setCapaId(capa.getId());
        mapper.updateByIdOrFail(c);
        if (c.getNcrId() != null) ncrService.linkCapa(c.getNcrId(), capa.getId());
        return capa.getId();
    }

    /** QC-CPL-R03：结案前需要客户回复、CAPA（严重度 ≥ 严重）、处理结果；审批流 QC_COMPLAINT_CLOSE */
    @Transactional(rollbackFor = Exception.class)
    public void close(Long id) {
        QcComplaintDO c = get(id);
        List<String> missing = closeMissing(c);
        if (!missing.isEmpty()) throw BizException.of(QualityErrorCodes.CPL_CLOSE_MISSING, String.join("、", missing));
        fire(c, QcAction.SUBMIT, null);
        Map<String, Long> users = c.getQeId() == null ? Map.of() : Map.of("qeId", c.getQeId());
        StartResult r = workflowApi.start(QualityModuleConfig.COMPLAINT_CLOSE, id, c.getDocNo(), "客诉结案 " + c.getDocNo(), Map.of("severity", c.getSeverity()),
                users, support.currentUser());
        if (!r.isStarted()) doClose(get(id));
    }

    List<String> closeMissing(QcComplaintDO c) {
        List<String> missing = new ArrayList<>();
        if (c.getRepliedAt() == null) missing.add("客户回复");
        if (!"MINOR".equals(c.getSeverity()) && c.getCapaId() == null) missing.add("CAPA");
        if (!StringUtils.hasText(c.getHandling())) missing.add("处理结果");
        return missing;
    }

    @EventListener
    public void onApproval(ApprovalCompletedEvent e) {
        if (!QualityModuleConfig.COMPLAINT_CLOSE.equals(e.getBizType())) return;
        QcComplaintDO c = mapper.selectById(e.getBizId());
        if (c == null || !ComplaintStatus.CLOSING.name().equals(c.getComplaintStatus())) return;
        switch (e.getResult()) {
            case APPROVED -> doClose(c);
            case WITHDRAWN -> fire(c, QcAction.WITHDRAW, null);
            default -> fire(c, QcAction.REJECT, e.getComment());
        }
    }

    /** QC-CPL-R04：同意赔偿金额 > 0 时发布 ComplaintClaimAgreedEvent */
    private void doClose(QcComplaintDO c) {
        c.setClosedAt(LocalDateTime.now());
        QcAction action = ComplaintStatus.CLOSING.name().equals(c.getComplaintStatus()) ? QcAction.APPROVE : QcAction.CLOSE;
        fire(c, action, "结案");
        support.resolve("QC_CPL_CRITICAL_" + c.getId());
        support.resolve("QC_CPL_REPLY_" + c.getId());
        if (c.getAgreedAmount() != null && c.getAgreedAmount().signum() > 0) {
            eventPublisher.publish(new ComplaintClaimAgreedEvent(c.getId(), c.getDocNo(), c.getCustomerId(), c.getCurrency(), c.getAgreedAmount(), c.getHandling()));
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id, String reason) {
        QcComplaintDO c = get(id);
        c.setCancelReason(QcSupport.limit(QcSupport.requireText(reason, "取消原因"), 256));
        fire(c, QcAction.CANCEL, c.getCancelReason());
        support.resolve("QC_CPL_CRITICAL_" + id);
        support.resolve("QC_CPL_REPLY_" + id);
    }

    private void requireOpen(QcComplaintDO c, String action) {
        ComplaintStatus s = ComplaintStatus.valueOf(c.getComplaintStatus());
        if (s == ComplaintStatus.CLOSING) throw new BizException(QualityErrorCodes.DOC_PENDING);
        if (!OPEN.contains(s.name())) throw BizException.of(QualityErrorCodes.STATUS_NOT_ALLOWED, s.label(), action);
    }

    private void fire(QcComplaintDO c, QcAction action, String reason) {
        ComplaintStatus from = ComplaintStatus.valueOf(c.getComplaintStatus());
        ComplaintStatus to = QcStateMachines.COMPLAINT.fire(from, action);
        c.setComplaintStatus(to.name());
        c.setStatus(to.docStatus());
        mapper.updateByIdOrFail(c);
        support.log(BIZ_TYPE, c.getId(), c.getDocNo(), action.name(), action.label(), from.name(), to.name(), reason);
    }

    // ==================== 提醒（QC-CPL-R02） ====================

    /** 回复期限前 1 天未回复提醒负责 QE；逾期每天提醒并抄送品质主管 */
    @Transactional(rollbackFor = Exception.class)
    public int remind(LocalDate today) {
        int n = 0;
        for (QcComplaintDO c : mapper.selectList(new LambdaQueryWrapper<QcComplaintDO>().in(QcComplaintDO::getComplaintStatus, OPEN)
                .isNull(QcComplaintDO::getRepliedAt))) {
            if (c.getReplyDueDate() == null || today.equals(c.getLastRemindDate())) continue;
            long days = ChronoUnit.DAYS.between(today, c.getReplyDueDate());
            String route = "/quality/complaint/" + c.getId();
            if (days == 1) {
                support.message(List.of(c.getQeId()), "客诉明天到期：" + c.getDocNo(), "回复期限 " + c.getReplyDueDate(), route);
            } else if (days < 0) {
                List<Long> to = new ArrayList<>(support.managers());
                to.add(c.getQeId());
                support.alert("QC_CPL_REPLY_" + c.getId(), AlertRaisedEvent.Level.WARNING, to, BIZ_TYPE, c.getId(), "客诉逾期未回复：" + c.getDocNo(),
                        "回复期限 " + c.getReplyDueDate() + "，已逾期 " + (-days) + " 天", route);
            } else {
                continue;
            }
            c.setLastRemindDate(today);
            mapper.updateByIdOrFail(c);
            n++;
        }
        return n;
    }
}
