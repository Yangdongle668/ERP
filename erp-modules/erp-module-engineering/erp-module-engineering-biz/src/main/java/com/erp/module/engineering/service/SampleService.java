package com.erp.module.engineering.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.common.enums.DocAction;
import com.erp.common.enums.DocStatus;
import com.erp.common.exception.BizException;
import com.erp.common.result.PageResult;
import com.erp.common.statemachine.DocStateMachines;
import com.erp.common.statemachine.StateMachine;
import com.erp.module.engineering.api.EngineeringErrorCodes;
import com.erp.module.engineering.api.material.MaterialStatus;
import com.erp.module.engineering.api.sample.SampleApi;
import com.erp.module.engineering.api.sample.SampleOrderCreator;
import com.erp.module.engineering.api.sample.SampleOrderRequest;
import com.erp.module.engineering.controller.vo.SampleVOs.FeedbackReq;
import com.erp.module.engineering.controller.vo.SampleVOs.SampleDetail;
import com.erp.module.engineering.controller.vo.SampleVOs.SampleQuery;
import com.erp.module.engineering.controller.vo.SampleVOs.SampleRow;
import com.erp.module.engineering.controller.vo.SampleVOs.SampleSave;
import com.erp.module.engineering.controller.vo.SampleVOs.ShipReq;
import com.erp.module.engineering.dal.dataobject.MaterialDO;
import com.erp.module.engineering.dal.dataobject.ProjectDO;
import com.erp.module.engineering.dal.dataobject.SampleDO;
import com.erp.module.engineering.dal.mapper.BomMapper;
import com.erp.module.engineering.dal.mapper.ProjectMapper;
import com.erp.module.engineering.dal.mapper.SampleMapper;
import com.erp.module.inventory.api.doc.InventoryDocApi;
import com.erp.module.inventory.api.doc.SourceRef;
import com.erp.module.inventory.api.doc.StockDocEvent;
import com.erp.module.inventory.api.doc.StockOutConfirmedEvent;
import com.erp.module.inventory.api.doc.StockOutRequest;
import com.erp.module.inventory.api.doc.StockOutType;
import com.erp.module.system.api.file.FileApi;
import com.erp.module.system.api.job.ErpJob;
import com.erp.module.system.api.notify.MessageSendEvent;
import com.erp.module.system.api.notify.NotifyApi;
import com.erp.module.system.api.notify.TodoCreatedEvent;
import com.erp.module.system.api.user.UserDTO;
import com.erp.module.system.api.workflow.ApprovalCompletedEvent;
import com.erp.module.system.api.workflow.StartResult;
import com.erp.module.system.api.workflow.WorkflowApi;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 样品单（需求 05-07）：申请 → 审批（ENG_SAMPLE）→ 制作（样品生产订单 / 从库存领取）→ 出库 → 寄样 → 客户反馈 → 关闭。
 * 样品生产订单由生产模块实现 {@link SampleOrderCreator}，完工后回调 {@link SampleApi#onProductionCompleted}；
 * 样品出库走仓库“其他出库”，出库单确认后（StockOutConfirmedEvent）才能登记寄出。
 */
@Service
public class SampleService implements SampleApi {

    public static final String BIZ_TYPE = "ENG_SAMPLE";
    static final String CODE_RULE = "ENG_SAMPLE";
    static final Set<String> TYPES = Set.of("CUSTOMER", "ENGINEERING", "CERTIFICATION");
    static final Set<String> RESULTS = Set.of("APPROVED", "CONDITIONAL", "REJECTED");

    public enum State implements StateMachine.Labeled {
        DRAFT("草稿"), PENDING("待审批"), APPROVED("已审批"), MAKING("制作中"), READY("待寄出"), SHIPPED("已寄出"), FEEDBACK("已反馈"),
        CLOSED("已关闭"), VOIDED("已作废");

        private final String label;

        State(String label) {
            this.label = label;
        }

        @Override
        public String label() {
            return label;
        }
    }

    public enum Op implements StateMachine.Labeled {
        SUBMIT("提交"), BACK("驳回/撤回"), APPROVE("审批通过"), PRODUCE("生成生产订单"), STOCK_OUT("出库"), PRODUCED("完工"), SHIP("登记寄出"),
        FEEDBACK("登记反馈"), CLOSE("关闭"), VOID("作废");

        private final String label;

        Op(String label) {
            this.label = label;
        }

        @Override
        public String label() {
            return label;
        }
    }

    /** 第 4 节状态流转；关闭：已反馈，或任意非草稿状态（原因必填） */
    static final StateMachine<State, Op> MACHINE = StateMachine.builder(State.class, Op.class)
            .transition(State.DRAFT, Op.SUBMIT, State.PENDING)
            .transition(State.PENDING, Op.BACK, State.DRAFT)
            .transition(State.PENDING, Op.APPROVE, State.APPROVED)
            .transition(State.APPROVED, Op.PRODUCE, State.MAKING)
            .transition(State.APPROVED, Op.STOCK_OUT, State.READY)
            .transition(State.MAKING, Op.PRODUCED, State.READY)
            .transition(State.READY, Op.SHIP, State.SHIPPED)
            .transition(State.SHIPPED, Op.FEEDBACK, State.FEEDBACK)
            .transition(State.PENDING, Op.CLOSE, State.CLOSED)
            .transition(State.APPROVED, Op.CLOSE, State.CLOSED)
            .transition(State.MAKING, Op.CLOSE, State.CLOSED)
            .transition(State.READY, Op.CLOSE, State.CLOSED)
            .transition(State.SHIPPED, Op.CLOSE, State.CLOSED)
            .transition(State.FEEDBACK, Op.CLOSE, State.CLOSED)
            .transition(State.DRAFT, Op.VOID, State.VOIDED)
            .transition(State.APPROVED, Op.VOID, State.VOIDED)
            .build();

    private final SampleMapper mapper;
    private final ProjectMapper projectMapper;
    private final BomMapper bomMapper;
    private final EngSupport support;
    private final WorkflowApi workflowApi;
    private final FileApi fileApi;
    private final NotifyApi notifyApi;
    private final InventoryDocApi inventoryDocApi;
    private final ObjectProvider<SampleOrderCreator> orderCreators;

    public SampleService(SampleMapper mapper, ProjectMapper projectMapper, BomMapper bomMapper, EngSupport support, WorkflowApi workflowApi,
                         FileApi fileApi, NotifyApi notifyApi, InventoryDocApi inventoryDocApi, ObjectProvider<SampleOrderCreator> orderCreators) {
        this.mapper = mapper;
        this.projectMapper = projectMapper;
        this.bomMapper = bomMapper;
        this.support = support;
        this.workflowApi = workflowApi;
        this.fileApi = fileApi;
        this.notifyApi = notifyApi;
        this.inventoryDocApi = inventoryDocApi;
        this.orderCreators = orderCreators;
    }

    // ==================== 编辑 ====================

    @Transactional(rollbackFor = Exception.class)
    public Long create(SampleSave req) {
        SampleDO s = new SampleDO();
        s.setDocNo(support.nextNo(CODE_RULE));
        s.setDocDate(LocalDate.now());
        s.setStatus(DocStatus.DRAFT);
        s.setSampleStatus(State.DRAFT.name());
        s.setOwnerId(support.currentUser());
        s.setStockOutDone(false);
        fill(s, req);
        mapper.insert(s);
        if (req.fileIds() != null && !req.fileIds().isEmpty()) fileApi.bind(req.fileIds(), BIZ_TYPE, s.getId());
        return s.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, SampleSave req) {
        SampleDO s = getOrThrow(id);
        requireState(s, "修改", State.DRAFT);
        if (req.version() != null) s.setVersion(req.version());
        fill(s, req);
        mapper.updateByIdOrFail(s);
        if (req.fileIds() != null && !req.fileIds().isEmpty()) fileApi.bind(req.fileIds(), BIZ_TYPE, id);
    }

    /** R01：客户样必须选择客户；要求日期 ≥ 今天 */
    private void fill(SampleDO s, SampleSave req) {
        if (!TYPES.contains(req.sampleType())) throw BizException.of(com.erp.common.exception.GlobalErrorCodes.BAD_REQUEST, "样品类型");
        if ("CUSTOMER".equals(req.sampleType()) && req.customerId() == null) throw new BizException(EngineeringErrorCodes.SAMPLE_CUSTOMER_REQUIRED);
        if (req.requiredDate().isBefore(LocalDate.now())) throw new BizException(EngineeringErrorCodes.SAMPLE_REQUIRED_DATE);
        MaterialDO m = support.material(req.materialId());
        if (m.getStatus() == MaterialStatus.DISABLED) throw BizException.of(EngineeringErrorCodes.SAMPLE_MATERIAL_DRAFT, m.getCode());
        if (req.projectId() != null && projectMapper.selectById(req.projectId()) == null) throw new BizException(EngineeringErrorCodes.PROJECT_NOT_EXISTS);
        s.setSampleType(req.sampleType());
        s.setCustomerId(req.customerId());
        s.setContactId(req.contactId());
        s.setProjectId(req.projectId());
        s.setMaterialId(m.getId());
        s.setCustomerPartNo(EngSupport.trim(req.customerPartNo()));
        s.setQty(req.qty());
        s.setRequiredDate(req.requiredDate());
        s.setMakeMethod("FROM_STOCK".equals(req.makeMethod()) ? "FROM_STOCK" : "PRODUCE");
        s.setPurpose(req.purpose().trim());
        s.setRequirements(EngSupport.trim(req.requirements()));
        s.setShipAddress(EngSupport.trim(req.shipAddress()));
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        SampleDO s = getOrThrow(id);
        requireState(s, "删除", State.DRAFT);
        mapper.deleteById(id);
        fileApi.deleteByBiz(BIZ_TYPE, id);
    }

    // ==================== 审批 ====================

    /** 提交：发起审批 ENG_SAMPLE；未配置审批流时直接通过 */
    @Transactional(rollbackFor = Exception.class)
    public String submit(Long id) {
        SampleDO s = getOrThrow(id);
        requireState(s, "提交", State.DRAFT);
        s.setStatus(DocStateMachines.STANDARD.fire(s.getStatus(), DocAction.SUBMIT));
        fire(s, Op.SUBMIT, null);
        Map<String, Object> vars = new HashMap<>();
        vars.put("sampleType", s.getSampleType());
        vars.put("qty", s.getQty());
        StartResult r = workflowApi.start(BIZ_TYPE, s.getId(), s.getDocNo(), "样品单 " + s.getDocNo(), vars, Map.of(), support.currentUser());
        if (!r.isStarted()) approve(s);
        return s.getSampleStatus();
    }

    private void approve(SampleDO s) {
        s.setStatus(DocStateMachines.STANDARD.fire(s.getStatus(), DocAction.APPROVE));
        fire(s, Op.APPROVE, null);
    }

    @EventListener
    public void onApproval(ApprovalCompletedEvent e) {
        if (!BIZ_TYPE.equals(e.getBizType())) return;
        SampleDO s = getOrThrow(e.getBizId());
        if (!State.PENDING.name().equals(s.getSampleStatus())) return;
        switch (e.getResult()) {
            case APPROVED -> approve(s);
            case WITHDRAWN -> {
                s.setStatus(DocStateMachines.STANDARD.fire(s.getStatus(), DocAction.WITHDRAW));
                fire(s, Op.BACK, "撤回");
            }
            default -> {
                s.setStatus(DocStateMachines.STANDARD.fire(s.getStatus(), DocAction.REJECT));
                fire(s, Op.BACK, e.getComment());
            }
        }
    }

    // ==================== 制作与出库 ====================

    public boolean productionAvailable() {
        return orderCreators.getIfAvailable() != null;
    }

    /** 生成样品生产订单（R02：物料必须已启用且有已审核的 BOM） */
    @Transactional(rollbackFor = Exception.class)
    public void createProdOrder(Long id) {
        SampleDO s = getOrThrow(id);
        requireState(s, "生成生产订单", State.APPROVED);
        if (!"PRODUCE".equals(s.getMakeMethod())) throw BizException.of(EngineeringErrorCodes.SAMPLE_STATUS, State.APPROVED.label(), "生成生产订单（制作方式为从库存领取）");
        MaterialDO m = support.material(s.getMaterialId());
        if (m.getStatus() != MaterialStatus.ENABLED) throw BizException.of(EngineeringErrorCodes.SAMPLE_MATERIAL_DRAFT, m.getCode());
        if (bomMapper.selectDefault(m.getId()) == null) throw BizException.of(EngineeringErrorCodes.SAMPLE_NO_BOM, m.getCode());
        SampleOrderCreator creator = orderCreators.getIfAvailable();
        if (creator == null) throw new BizException(EngineeringErrorCodes.SAMPLE_PRODUCTION_UNAVAILABLE);
        SampleOrderCreator.Ref ref = creator.create(new SampleOrderRequest(s.getId(), s.getDocNo(), m.getId(), s.getQty(), s.getRequiredDate(), s.getProjectId()));
        s.setProdOrderId(ref.orderId());
        s.setProdOrderNo(ref.orderNo());
        fire(s, Op.PRODUCE, ref.orderNo());
    }

    /** R03：样品生产订单完工入库 → 待寄出，通知申请人 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void onProductionCompleted(Long sampleId) {
        SampleDO s = getOrThrow(sampleId);
        if (!State.MAKING.name().equals(s.getSampleStatus())) return;
        fire(s, Op.PRODUCED, null);
        notifyOwner(s, "样品已完工：" + s.getDocNo(), "样品生产订单 " + Objects.toString(s.getProdOrderNo(), "") + " 已完工入库，请申请出库并寄出");
    }

    /** 申请出库：生成仓库“其他出库”草稿，由仓库确认出库 */
    @Transactional(rollbackFor = Exception.class)
    public String requestStockOut(Long id) {
        SampleDO s = getOrThrow(id);
        State st = State.valueOf(s.getSampleStatus());
        boolean fromStock = st == State.APPROVED && "FROM_STOCK".equals(s.getMakeMethod());
        if (!fromStock && st != State.READY) throw BizException.of(EngineeringErrorCodes.SAMPLE_STATUS, st.label(), "申请出库");
        if (s.getStockOutId() != null) throw BizException.of(EngineeringErrorCodes.SAMPLE_STATUS, st.label(), "重复申请出库（出库单 " + s.getStockOutNo() + "）");
        MaterialDO m = support.material(s.getMaterialId());
        List<Long> ids = inventoryDocApi.createStockOut(new StockOutRequest(StockOutType.OTHER_OUT, new SourceRef(BIZ_TYPE, s.getId(), s.getDocNo()), null,
                null, null, s.getOwnerId(), null, s.getCustomerId(), List.of(new StockOutRequest.Line(s.getId(), m.getId(), m.getBaseUom(), s.getQty(), null, null))));
        s.setStockOutId(ids.get(0));
        s.setStockOutNo(null);
        s.setStockOutDone(false);
        mapper.updateByIdOrFail(s);
        support.log(BIZ_TYPE, s.getId(), s.getDocNo(), "REQUEST_STOCK_OUT", "申请出库", st.name(), st.name(), null);
        return String.valueOf(ids.get(0));
    }

    /** 出库单确认：记录出库完成；从库存领取的样品 → 待寄出 */
    @EventListener
    public void onStockOut(StockOutConfirmedEvent e) {
        if (e.getSource() == null || !BIZ_TYPE.equals(e.getSource().sourceType())) return;
        SampleDO s = mapper.selectById(e.getSource().sourceId());
        if (s == null) return;
        s.setStockOutId(e.getStockOutId());
        s.setStockOutNo(e.getStockOutNo());
        s.setStockOutDone(true);
        if (State.APPROVED.name().equals(s.getSampleStatus())) fire(s, Op.STOCK_OUT, e.getStockOutNo());
        else mapper.updateByIdOrFail(s);
    }

    /** 出库单反确认、退回：出库未完成；退回（作废）时可重新申请 */
    @EventListener
    public void onStockDoc(StockDocEvent e) {
        if (e.getSource() == null || !BIZ_TYPE.equals(e.getSource().sourceType())) return;
        if (e.getKind() != StockDocEvent.Kind.OUT_REVERSED && e.getKind() != StockDocEvent.Kind.REJECTED) return;
        SampleDO s = mapper.selectById(e.getSource().sourceId());
        if (s == null) return;
        if (State.SHIPPED.name().equals(s.getSampleStatus()) || State.FEEDBACK.name().equals(s.getSampleStatus())) {
            throw BizException.of(EngineeringErrorCodes.SAMPLE_STATUS, State.valueOf(s.getSampleStatus()).label(), "反确认样品出库单");
        }
        s.setStockOutDone(false);
        if (e.getKind() == StockDocEvent.Kind.REJECTED) {
            s.setStockOutId(null);
            s.setStockOutNo(null);
            notifyOwner(s, "样品出库单被退回：" + s.getDocNo(), Objects.toString(e.getReason(), ""));
        }
        mapper.updateByIdOrFail(s);
    }

    /** 登记寄出（R04：出库单必须已确认） */
    @Transactional(rollbackFor = Exception.class)
    public void ship(Long id, ShipReq req) {
        SampleDO s = getOrThrow(id);
        requireState(s, "登记寄出", State.READY);
        if (!Boolean.TRUE.equals(s.getStockOutDone())) throw new BizException(EngineeringErrorCodes.SAMPLE_NOT_OUT);
        s.setShipDate(req.shipDate());
        s.setCourier(req.courier().trim());
        s.setTrackingNo(req.trackingNo().trim());
        if (StringUtils.hasText(req.shipAddress())) s.setShipAddress(req.shipAddress().trim());
        fire(s, Op.SHIP, req.courier() + " " + req.trackingNo());
        notifyOwner(s, "样品已寄出：" + s.getDocNo(), s.getCourier() + " " + s.getTrackingNo());
    }

    /** 登记反馈（R05：客户承认且物料为草稿时，提醒数据管理员启用物料） */
    @Transactional(rollbackFor = Exception.class)
    public void feedback(Long id, FeedbackReq req) {
        SampleDO s = getOrThrow(id);
        requireState(s, "登记反馈", State.SHIPPED);
        if (!RESULTS.contains(req.result())) throw BizException.of(com.erp.common.exception.GlobalErrorCodes.BAD_REQUEST, "反馈结果");
        s.setFeedbackResult(req.result());
        s.setFeedbackDate(req.feedbackDate());
        s.setFeedbackContent(EngSupport.trim(req.content()));
        fire(s, Op.FEEDBACK, req.result());
        if (req.fileIds() != null && !req.fileIds().isEmpty()) fileApi.bind(req.fileIds(), BIZ_TYPE, id);
        MaterialDO m = support.material(s.getMaterialId());
        if ("APPROVED".equals(req.result()) && m.getStatus() == MaterialStatus.DRAFT) {
            List<Long> admins = support.usersWithPermission("eng:material:enable");
            if (!admins.isEmpty()) {
                notifyApi.todo(new TodoCreatedEvent("ENG_SAMPLE_ENABLE:" + m.getId(), admins, TodoCreatedEvent.Category.TASK, "ENG_MATERIAL", m.getId(),
                        m.getCode(), "客户已承认样品，请启用物料 " + m.getCode() + " " + m.getName(), "/engineering/material/" + m.getId(),
                        TodoCreatedEvent.Priority.NORMAL, null));
            }
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void close(Long id, String reason) {
        SampleDO s = getOrThrow(id);
        if (!State.FEEDBACK.name().equals(s.getSampleStatus()) && !StringUtils.hasText(reason)) throw new BizException(EngineeringErrorCodes.SAMPLE_REASON_REQUIRED);
        s.setCloseReason(EngSupport.trim(reason));
        fire(s, Op.CLOSE, reason);
    }

    /** 作废：草稿、已审批（未生成生产订单），原因必填 */
    @Transactional(rollbackFor = Exception.class)
    public void voidSample(Long id, String reason) {
        SampleDO s = getOrThrow(id);
        if (!StringUtils.hasText(reason)) throw new BizException(EngineeringErrorCodes.SAMPLE_REASON_REQUIRED);
        if (s.getProdOrderId() != null || s.getStockOutId() != null) throw BizException.of(EngineeringErrorCodes.SAMPLE_STATUS, label(s), "作废（已生成生产订单或出库单）");
        s.setCloseReason(reason.trim());
        if (DocStateMachines.STANDARD.canFire(s.getStatus(), DocAction.VOID)) s.setStatus(DocStateMachines.STANDARD.fire(s.getStatus(), DocAction.VOID));
        fire(s, Op.VOID, reason);
    }

    private void fire(SampleDO s, Op op, String reason) {
        State from = State.valueOf(s.getSampleStatus());
        if (!MACHINE.canFire(from, op)) throw BizException.of(EngineeringErrorCodes.SAMPLE_STATUS, from.label(), op.label());
        State to = MACHINE.fire(from, op);
        s.setSampleStatus(to.name());
        mapper.updateByIdOrFail(s);
        support.log(BIZ_TYPE, s.getId(), s.getDocNo(), op.name(), op.label(), from.name(), to.name(), reason);
    }

    private static void requireState(SampleDO s, String action, State expected) {
        if (!expected.name().equals(s.getSampleStatus())) throw BizException.of(EngineeringErrorCodes.SAMPLE_STATUS, label(s), action);
    }

    private static String label(SampleDO s) {
        return State.valueOf(s.getSampleStatus()).label();
    }

    private void notifyOwner(SampleDO s, String title, String content) {
        if (s.getOwnerId() == null) return;
        notifyApi.message(new MessageSendEvent(List.of(s.getOwnerId()), MessageSendEvent.Type.REMIND, title, content, "/engineering/sample/" + s.getId(), false));
    }

    public SampleDO getOrThrow(Long id) {
        SampleDO s = id == null ? null : mapper.selectById(id);
        if (s == null) throw new BizException(EngineeringErrorCodes.SAMPLE_NOT_EXISTS);
        return s;
    }

    // ==================== 查询 ====================

    public PageResult<SampleRow> page(SampleQuery q) {
        LambdaQueryWrapper<SampleDO> w = new LambdaQueryWrapper<SampleDO>()
                .likeRight(StringUtils.hasText(q.getDocNo()), SampleDO::getDocNo, q.getDocNo() == null ? null : q.getDocNo().trim().toUpperCase())
                .eq(q.getCustomerId() != null, SampleDO::getCustomerId, q.getCustomerId())
                .eq(q.getMaterialId() != null, SampleDO::getMaterialId, q.getMaterialId())
                .eq(q.getProjectId() != null, SampleDO::getProjectId, q.getProjectId())
                .eq(StringUtils.hasText(q.getSampleType()), SampleDO::getSampleType, q.getSampleType())
                .eq(q.getCreatedBy() != null, SampleDO::getCreatedBy, q.getCreatedBy())
                .ge(q.getRequiredFrom() != null, SampleDO::getRequiredDate, q.getRequiredFrom())
                .le(q.getRequiredTo() != null, SampleDO::getRequiredDate, q.getRequiredTo());
        if (StringUtils.hasText(q.getStatuses())) w.in(SampleDO::getSampleStatus, Arrays.asList(q.getStatuses().split(",")));
        w.orderByDesc(SampleDO::getId);
        PageResult<SampleDO> page = mapper.selectPage(q, w);
        List<SampleDO> list = page.list();
        Map<Long, MaterialDO> ms = support.materials(list.stream().map(SampleDO::getMaterialId).toList());
        Map<Long, UserDTO> users = support.users(list.stream().map(SampleDO::getCreatedBy).toList());
        Map<Long, String> customers = support.customers(list.stream().map(SampleDO::getCustomerId).toList());
        LocalDate today = LocalDate.now();
        return new PageResult<>(list.stream().map(s -> {
            MaterialDO m = ms.get(s.getMaterialId());
            return new SampleRow(s.getId(), s.getDocNo(), s.getSampleType(), s.getCustomerId(), customers.get(s.getCustomerId()), s.getMaterialId(),
                    m == null ? null : m.getCode(), m == null ? null : m.getName(), m == null ? null : m.getBaseUom(), s.getQty(), s.getRequiredDate(),
                    overdue(s, today), s.getMakeMethod(), s.getSampleStatus(), s.getFeedbackResult(), EngSupport.name(users, s.getCreatedBy()), s.getCreatedAt());
        }).toList(), page.total());
    }

    static boolean overdue(SampleDO s, LocalDate today) {
        State st = State.valueOf(s.getSampleStatus());
        return s.getRequiredDate().isBefore(today) && st.ordinal() < State.SHIPPED.ordinal();
    }

    public SampleDetail detail(Long id) {
        SampleDO s = getOrThrow(id);
        MaterialDO m = support.material(s.getMaterialId());
        ProjectDO p = s.getProjectId() == null ? null : projectMapper.selectById(s.getProjectId());
        Map<Long, String> customers = support.customers(s.getCustomerId() == null ? List.of() : List.of(s.getCustomerId()));
        return new SampleDetail(s.getId(), s.getDocNo(), s.getSampleType(), s.getCustomerId(), customers.get(s.getCustomerId()), s.getContactId(),
                s.getProjectId(), p == null ? null : p.getDocNo(), p == null ? null : p.getName(), m.getId(), m.getCode(), m.getName(), m.getSpec(),
                m.getStatus().name(), m.getBaseUom(), s.getCustomerPartNo(), s.getQty(), s.getRequiredDate(), s.getMakeMethod(), s.getPurpose(),
                s.getRequirements(), s.getShipAddress(), s.getProdOrderId(), s.getProdOrderNo(), s.getStockOutId(), s.getStockOutNo(),
                Boolean.TRUE.equals(s.getStockOutDone()), s.getShipDate(), s.getCourier(), s.getTrackingNo(), s.getFeedbackResult(), s.getFeedbackDate(),
                s.getFeedbackContent(), s.getSampleStatus(), s.getStatus().name(), s.getCloseReason(), productionAvailable(),
                support.userName(s.getCreatedBy()), s.getCreatedAt(), s.getVersion());
    }

    static final Map<String, String> TYPE_NAMES = Map.of("CUSTOMER", "客户样", "ENGINEERING", "工程验证样", "CERTIFICATION", "认证送样");

    public Map<String, Object> printData(Long id) {
        SampleDetail d = detail(id);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("docNo", d.docNo());
        data.put("sampleType", TYPE_NAMES.get(d.sampleType()));
        data.put("status", State.valueOf(d.sampleStatus()).label());
        data.put("customerName", Objects.toString(d.customerName(), d.customerId() == null ? "" : String.valueOf(d.customerId())));
        data.put("customerPartNo", Objects.toString(d.customerPartNo(), ""));
        data.put("materialCode", d.materialCode());
        data.put("materialName", d.materialName());
        data.put("materialSpec", Objects.toString(d.materialSpec(), ""));
        data.put("uom", d.uom());
        data.put("qty", d.qty());
        data.put("requiredDate", d.requiredDate());
        data.put("makeMethod", "FROM_STOCK".equals(d.makeMethod()) ? "从库存领取" : "生产制作");
        data.put("purpose", d.purpose());
        data.put("requirements", Objects.toString(d.requirements(), ""));
        data.put("shipAddress", Objects.toString(d.shipAddress(), ""));
        data.put("shipDate", d.shipDate());
        data.put("courier", Objects.toString(d.courier(), ""));
        data.put("trackingNo", Objects.toString(d.trackingNo(), ""));
        data.put("createdByName", Objects.toString(d.createdByName(), ""));
        return data;
    }

    // ==================== 提醒（R06） ====================

    /** 每天 08:30：要求日期前 2 天仍未寄出的样品单提醒申请人 */
    @ErpJob(code = "ENG_SAMPLE_REMIND", name = "样品寄出提醒", cron = "0 30 8 * * ?")
    public String remind() {
        LocalDate today = LocalDate.now();
        List<SampleDO> list = mapper.selectList(new LambdaQueryWrapper<SampleDO>()
                .in(SampleDO::getSampleStatus, State.APPROVED.name(), State.MAKING.name(), State.READY.name(), State.PENDING.name())
                .le(SampleDO::getRequiredDate, today.plusDays(2)));
        list.forEach(s -> notifyOwner(s, (s.getRequiredDate().isBefore(today) ? "样品已逾期未寄出：" : "样品即将到期未寄出：") + s.getDocNo(),
                "要求寄出日期 " + s.getRequiredDate() + "，当前状态 " + label(s)));
        return "提醒 " + list.size() + " 条";
    }
}
