package com.erp.module.production.service.report;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.common.enums.DocStatus;
import com.erp.common.exception.BizException;
import com.erp.common.result.PageResult;
import com.erp.framework.datascope.DataScopes;
import com.erp.framework.event.DomainEventPublisher;
import com.erp.module.engineering.api.material.MaterialDTO;
import com.erp.module.engineering.api.routing.WorkCenterDTO;
import com.erp.module.engineering.api.tooling.ToolingApi;
import com.erp.module.engineering.api.tooling.ToolingDTO;
import com.erp.module.production.api.ProductionErrorCodes;
import com.erp.module.production.api.defect.DefectRegisteredEvent;
import com.erp.module.production.api.report.IpqcTriggerEvent;
import com.erp.module.production.api.report.WorkReportApprovedEvent;
import com.erp.module.production.api.report.WorkReportReversedEvent;
import com.erp.module.production.config.ProductionModuleConfig;
import com.erp.module.production.controller.vo.CommonVOs.BatchResult;
import com.erp.module.production.controller.vo.ReportVOs.Context;
import com.erp.module.production.controller.vo.ReportVOs.DefectResp;
import com.erp.module.production.controller.vo.ReportVOs.DefectSave;
import com.erp.module.production.controller.vo.ReportVOs.OperationOption;
import com.erp.module.production.controller.vo.ReportVOs.OperatorResp;
import com.erp.module.production.controller.vo.ReportVOs.OperatorSave;
import com.erp.module.production.controller.vo.ReportVOs.ReportDetail;
import com.erp.module.production.controller.vo.ReportVOs.ReportQuery;
import com.erp.module.production.controller.vo.ReportVOs.ReportRow;
import com.erp.module.production.controller.vo.ReportVOs.ReportSave;
import com.erp.module.production.controller.vo.ReportVOs.SaveResult;
import com.erp.module.production.controller.vo.ReportVOs.ToolingOption;
import com.erp.module.production.dal.dataobject.MfgDefectDO;
import com.erp.module.production.dal.dataobject.MfgIssueDO;
import com.erp.module.production.dal.dataobject.MfgProdOrderDO;
import com.erp.module.production.dal.dataobject.MfgProdOrderMaterialDO;
import com.erp.module.production.dal.dataobject.MfgProdOrderOperationDO;
import com.erp.module.production.dal.dataobject.MfgReportDO;
import com.erp.module.production.dal.dataobject.MfgReportOperatorDO;
import com.erp.module.production.dal.dataobject.MfgWorkOrderDO;
import com.erp.module.production.dal.mapper.MfgDefectMapper;
import com.erp.module.production.dal.mapper.MfgIssueMapper;
import com.erp.module.production.dal.mapper.MfgProdOrderMapper;
import com.erp.module.production.dal.mapper.MfgProdOrderMaterialMapper;
import com.erp.module.production.dal.mapper.MfgProdOrderOperationMapper;
import com.erp.module.production.dal.mapper.MfgReportMapper;
import com.erp.module.production.dal.mapper.MfgReportOperatorMapper;
import com.erp.module.production.dal.mapper.MfgWorkOrderMapper;
import com.erp.module.production.service.MfgAction;
import com.erp.module.production.service.MfgStateMachines;
import com.erp.module.production.service.MfgSupport;
import com.erp.module.production.service.ProdStatus;
import com.erp.module.production.service.WoStatus;
import com.erp.module.production.service.material.IssueService;
import com.erp.module.production.service.material.IssueService.BackflushNeed;
import com.erp.module.production.service.order.OrderProgressService;
import com.erp.module.production.service.workorder.WorkOrderService;
import com.erp.module.system.api.file.FileApi;
import com.erp.module.system.api.user.UserDTO;
import com.erp.module.quality.api.inspection.InspectionQueryApi;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 报工（需求 09-04）：保存（可报数量、工装、不良明细校验）→ 审核（工序 / 工单 / 订单数量、倒冲、工装次数、IPQC、事件）→ 反审核。
 * 不良的返修 / 报废处置以补充报工单（REPAIR / SCRAP）记账（需求 09-06）。
 */
@Service("mfgReportService")
public class ReportService {

    public static final String BIZ_TYPE = ProductionModuleConfig.REPORT;
    public static final String NORMAL = "NORMAL";
    public static final String REPAIR = "REPAIR";
    public static final String SCRAP = "SCRAP";
    static final BigDecimal SECONDS_PER_HOUR = BigDecimal.valueOf(3600);

    private final MfgReportMapper mapper;
    private final MfgReportOperatorMapper operatorMapper;
    private final MfgDefectMapper defectMapper;
    private final MfgProdOrderMapper orderMapper;
    private final MfgProdOrderMaterialMapper materialMapper;
    private final MfgProdOrderOperationMapper operationMapper;
    private final MfgWorkOrderMapper workOrderMapper;
    private final MfgIssueMapper issueMapper;
    private final OrderProgressService progress;
    private final WorkOrderService workOrderService;
    private final IssueService issueService;
    private final MfgSupport support;
    private final ObjectProvider<ToolingApi> toolingApi;
    private final ObjectProvider<InspectionQueryApi> inspectionQueryApi;
    private final FileApi fileApi;
    private final DomainEventPublisher eventPublisher;
    private final TransactionTemplate tx;

    public ReportService(MfgReportMapper mapper, MfgReportOperatorMapper operatorMapper, MfgDefectMapper defectMapper, MfgProdOrderMapper orderMapper,
                         MfgProdOrderMaterialMapper materialMapper, MfgProdOrderOperationMapper operationMapper, MfgWorkOrderMapper workOrderMapper,
                         MfgIssueMapper issueMapper, OrderProgressService progress, WorkOrderService workOrderService, IssueService issueService,
                         MfgSupport support, ObjectProvider<ToolingApi> toolingApi, FileApi fileApi, DomainEventPublisher eventPublisher,
                         PlatformTransactionManager transactionManager, ObjectProvider<InspectionQueryApi> inspectionQueryApi) {
        this.mapper = mapper;
        this.operatorMapper = operatorMapper;
        this.defectMapper = defectMapper;
        this.orderMapper = orderMapper;
        this.materialMapper = materialMapper;
        this.operationMapper = operationMapper;
        this.workOrderMapper = workOrderMapper;
        this.issueMapper = issueMapper;
        this.progress = progress;
        this.workOrderService = workOrderService;
        this.issueService = issueService;
        this.support = support;
        this.toolingApi = toolingApi;
        this.inspectionQueryApi = inspectionQueryApi;
        this.fileApi = fileApi;
        this.eventPublisher = eventPublisher;
        this.tx = new TransactionTemplate(transactionManager);
    }

    public MfgReportDO getOrThrow(Long id) {
        MfgReportDO r = id == null ? null : mapper.selectById(id);
        if (r == null) throw new BizException(ProductionErrorCodes.REPORT_NOT_EXISTS);
        return r;
    }

    // ==================== 查询 ====================

    public PageResult<ReportRow> page(ReportQuery q) {
        IPage<MfgReportDO> p = mapper.selectScopedPage(new Page<>(q.getPageNo(), q.getPageSize()), wrapper(q));
        return new PageResult<>(rows(p.getRecords()), p.getTotal());
    }

    private LambdaQueryWrapper<MfgReportDO> wrapper(ReportQuery q) {
        LambdaQueryWrapper<MfgReportDO> w = new LambdaQueryWrapper<MfgReportDO>().eq(MfgReportDO::getDeleted, false);
        if (StringUtils.hasText(q.getDocNo())) w.likeRight(MfgReportDO::getDocNo, q.getDocNo().trim());
        if (q.getProdOrderId() != null) w.eq(MfgReportDO::getProdOrderId, q.getProdOrderId());
        if (StringUtils.hasText(q.getProdOrderNo())) w.likeRight(MfgReportDO::getSourceNo, q.getProdOrderNo().trim());
        if (q.getMaterialId() != null) {
            List<Long> ids = orderMapper.selectList(new LambdaQueryWrapper<MfgProdOrderDO>().eq(MfgProdOrderDO::getMaterialId, q.getMaterialId()))
                    .stream().map(MfgProdOrderDO::getId).toList();
            w.in(MfgReportDO::getProdOrderId, ids.isEmpty() ? List.of(0L) : ids);
        }
        if (q.getOperationSeq() != null) w.eq(MfgReportDO::getOperationSeq, q.getOperationSeq());
        if (q.getWorkCenterId() != null) w.eq(MfgReportDO::getWorkCenterId, q.getWorkCenterId());
        if (q.getWorkOrderId() != null) w.eq(MfgReportDO::getWorkOrderId, q.getWorkOrderId());
        if (q.getDateFrom() != null) w.ge(MfgReportDO::getReportDate, q.getDateFrom());
        if (q.getDateTo() != null) w.le(MfgReportDO::getReportDate, q.getDateTo());
        if (StringUtils.hasText(q.getShift())) w.eq(MfgReportDO::getShift, q.getShift());
        if (q.getOperatorId() != null) {
            List<Long> ids = operatorMapper.selectList(new LambdaQueryWrapper<MfgReportOperatorDO>().eq(MfgReportOperatorDO::getUserId, q.getOperatorId()))
                    .stream().map(MfgReportOperatorDO::getReportId).distinct().toList();
            w.in(MfgReportDO::getId, ids.isEmpty() ? List.of(0L) : ids);
        }
        if (StringUtils.hasText(q.getStatuses())) w.in(MfgReportDO::getStatus, Arrays.stream(q.getStatuses().split(",")).map(DocStatus::valueOf).toList());
        if (StringUtils.hasText(q.getReportKind())) w.eq(MfgReportDO::getReportKind, q.getReportKind());
        return w.orderByDesc(MfgReportDO::getReportDate).orderByDesc(MfgReportDO::getId);
    }

    List<ReportRow> rows(List<MfgReportDO> list) {
        if (list.isEmpty()) return List.of();
        Map<Long, MfgProdOrderDO> orders = orderMapper.selectBatchIds(list.stream().map(MfgReportDO::getProdOrderId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(MfgProdOrderDO::getId, Function.identity()));
        Map<Long, MaterialDTO> ms = support.materials(orders.values().stream().map(MfgProdOrderDO::getMaterialId).toList());
        Map<String, MfgProdOrderOperationDO> ops = operationMapper.selectByParents(orders.keySet()).stream()
                .collect(Collectors.toMap(op -> op.getProdOrderId() + "#" + op.getSeq(), Function.identity(), (a, b) -> a));
        Map<Long, WorkCenterDTO> wcs = support.workCenters();
        List<Long> woIds = list.stream().map(MfgReportDO::getWorkOrderId).filter(Objects::nonNull).distinct().toList();
        Map<Long, String> woNos = woIds.isEmpty() ? Map.of()
                : workOrderMapper.selectBatchIds(woIds).stream().collect(Collectors.toMap(MfgWorkOrderDO::getId, MfgWorkOrderDO::getDocNo));
        Map<Long, List<MfgReportOperatorDO>> operators = operatorMapper.selectByParents(list.stream().map(MfgReportDO::getId).toList()).stream()
                .collect(Collectors.groupingBy(MfgReportOperatorDO::getReportId));
        Map<Long, UserDTO> users = support.users(operators.values().stream().flatMap(List::stream).map(MfgReportOperatorDO::getUserId).toList());
        ToolingApi tapi = toolingApi.getIfAvailable();
        return list.stream().map(r -> {
            MfgProdOrderDO o = orders.get(r.getProdOrderId());
            MaterialDTO m = o == null ? null : ms.get(o.getMaterialId());
            MfgProdOrderOperationDO op = ops.get(r.getProdOrderId() + "#" + r.getOperationSeq());
            WorkCenterDTO wc = r.getWorkCenterId() == null ? null : wcs.get(r.getWorkCenterId());
            BigDecimal total = r.getGoodQty().add(r.getDefectQty()).add(r.getScrapQty());
            BigDecimal yield = total.signum() == 0 ? null : r.getGoodQty().divide(total, 4, RoundingMode.HALF_UP);
            BigDecimal std = op == null ? BigDecimal.ZERO : r.getGoodQty().multiply(op.getStdRunSeconds()).divide(SECONDS_PER_HOUR, 4, RoundingMode.HALF_UP);
            BigDecimal eff = r.getWorkHours().signum() == 0 ? null : std.divide(r.getWorkHours(), 4, RoundingMode.HALF_UP);
            String names = operators.getOrDefault(r.getId(), List.of()).stream()
                    .map(x -> x.getUserId() != null ? MfgSupport.name(users, x.getUserId()) : x.getOperatorName()).filter(Objects::nonNull)
                    .collect(Collectors.joining("、"));
            String tooling = r.getToolingId() == null || tapi == null ? null : tapi.get(r.getToolingId()).map(ToolingDTO::code).orElse(null);
            return new ReportRow(r.getId(), r.getDocNo(), r.getReportDate(), r.getShift(), r.getProdOrderId(), o == null ? null : o.getDocNo(),
                    o == null ? null : o.getMaterialId(), m == null ? null : m.code(), m == null ? null : m.name(), r.getOperationSeq(),
                    op == null ? null : op.getOperation(), r.getWorkCenterId(), wc == null ? null : wc.name(), r.getWorkOrderId(), woNos.get(r.getWorkOrderId()),
                    r.getReportKind(), r.getGoodQty(), r.getDefectQty(), r.getScrapQty(), yield, r.getWorkHours(), std, eff, names, r.getToolingId(), tooling,
                    r.getStatus().name(), r.getApprovedAt());
        }).toList();
    }

    public ReportDetail detail(Long id) {
        MfgReportDO r = getOrThrow(id);
        DataScopes.check(r.getOrgId(), r.getDeptId(), r.getOwnerId(), "报工单");
        MfgProdOrderDO o = progress.getOrThrow(r.getProdOrderId());
        MaterialDTO m = support.material(o.getMaterialId());
        MfgProdOrderOperationDO op = OrderProgressService.operation(progress.operations(o.getId()), r.getOperationSeq());
        WorkCenterDTO wc = support.workCenter(r.getWorkCenterId());
        MfgWorkOrderDO wo = r.getWorkOrderId() == null ? null : workOrderMapper.selectById(r.getWorkOrderId());
        List<MfgReportOperatorDO> ops = operatorMapper.selectByParent(id);
        Map<Long, UserDTO> users = support.users(ops.stream().map(MfgReportOperatorDO::getUserId).toList());
        List<OperatorResp> operators = ops.stream().map(x -> new OperatorResp(x.getUserId(), MfgSupport.name(users, x.getUserId()), x.getOperatorName(),
                x.getHours())).toList();
        List<DefectResp> defects = defectMapper.selectList(new LambdaQueryWrapper<MfgDefectDO>().eq(MfgDefectDO::getReportId, id).orderByAsc(MfgDefectDO::getId))
                .stream().map(d -> new DefectResp(d.getId(), d.getDefectCode(), d.getQty(), d.getPosition(), d.getDescription(), fileIds(d.getImageFileIds()),
                        d.getDisposition(), d.getRepairedQty(), d.getScrappedQty(), d.getNcrNo())).toList();
        List<String> backflush = issueMapper.selectList(new LambdaQueryWrapper<MfgIssueDO>().eq(MfgIssueDO::getReportId, id)
                .ne(MfgIssueDO::getStatus, DocStatus.VOIDED)).stream().map(MfgIssueDO::getDocNo).toList();
        ToolingApi tapi = toolingApi.getIfAvailable();
        String tooling = r.getToolingId() == null || tapi == null ? null : tapi.get(r.getToolingId()).map(ToolingDTO::code).orElse(null);
        return new ReportDetail(r.getId(), r.getDocNo(), r.getStatus().name(), r.getReportKind(), o.getId(), o.getDocNo(), o.getProdStatus(), m.id(), m.code(),
                m.name(), o.getBatchNo(), r.getOperationSeq(), op == null ? null : op.getOperation(), r.getWorkOrderId(), wo == null ? null : wo.getDocNo(),
                r.getWorkCenterId(), wc == null ? null : wc.name(), r.getReportDate(), r.getShift(), r.getGoodQty(), r.getDefectQty(), r.getScrapQty(),
                r.getScrapReason(), r.getWorkHours(), r.getMachineHours(), r.getToolingId(), tooling, r.getStartTime(), r.getEndTime(), r.getDefectId(),
                r.getRemark(), r.getOwnerId(), support.userName(r.getOwnerId()), r.getApprovedAt(), support.userName(r.getApprovedBy()), r.getCreatedAt(),
                r.getVersion(), operators, defects, backflush);
    }

    static List<Long> fileIds(String s) {
        if (!StringUtils.hasText(s)) return List.of();
        return Arrays.stream(s.split(",")).filter(StringUtils::hasText).map(Long::valueOf).toList();
    }

    // ==================== 报工上下文 ====================

    /** 扫码：工单号 → 工单；“订单号#工序号” → 订单工序；订单号 → 订单（第一个还有可报数量的报工点） */
    public Context contextByBarcode(String barcode) {
        String code = barcode == null ? "" : barcode.trim();
        if (code.isEmpty()) throw BizException.of(ProductionErrorCodes.REPORT_BARCODE_UNKNOWN, code);
        MfgWorkOrderDO wo = workOrderMapper.selectOne(new LambdaQueryWrapper<MfgWorkOrderDO>().eq(MfgWorkOrderDO::getDocNo, code));
        if (wo != null) return context(wo.getProdOrderId(), wo.getOperationSeq(), wo.getId());
        String no = code;
        Integer seq = null;
        int hash = code.lastIndexOf('#');
        if (hash > 0) {
            no = code.substring(0, hash);
            try {
                seq = Integer.valueOf(code.substring(hash + 1));
            } catch (NumberFormatException e) {
                throw BizException.of(ProductionErrorCodes.REPORT_BARCODE_UNKNOWN, code);
            }
        }
        MfgProdOrderDO o = orderMapper.selectOne(new LambdaQueryWrapper<MfgProdOrderDO>().eq(MfgProdOrderDO::getDocNo, no));
        if (o == null) throw BizException.of(ProductionErrorCodes.REPORT_BARCODE_UNKNOWN, code);
        return context(o.getId(), seq, null);
    }

    public Context context(Long prodOrderId, Integer seq, Long workOrderId) {
        MfgProdOrderDO o = progress.getOrThrow(prodOrderId);
        DataScopes.check(o.getOrgId(), o.getDeptId(), o.getOwnerId(), "生产订单");
        MaterialDTO m = support.material(o.getMaterialId());
        List<MfgProdOrderOperationDO> ops = progress.operations(o.getId());
        Map<Integer, BigDecimal> drafts = draftInputs(o.getId(), null);
        List<OperationOption> options = ops.stream().map(op -> new OperationOption(op.getSeq(), op.getOperation(), Boolean.TRUE.equals(op.getIsReportPoint()),
                Boolean.TRUE.equals(op.getIsReportPoint()) ? reportable(o, ops, op, drafts) : BigDecimal.ZERO)).toList();
        MfgProdOrderOperationDO op = seq != null ? OrderProgressService.operation(ops, seq) : ops.stream().filter(x -> Boolean.TRUE.equals(x.getIsReportPoint()))
                .filter(x -> reportable(o, ops, x, drafts).signum() > 0).findFirst().orElse(OrderProgressService.lastReportPoint(ops));
        MfgWorkOrderDO wo = workOrderId == null ? null : workOrderMapper.selectById(workOrderId);
        WorkCenterDTO wc = support.workCenter(wo != null ? wo.getWorkCenterId() : op == null ? null : op.getWorkCenterId());
        ToolingApi tapi = toolingApi.getIfAvailable();
        List<ToolingOption> toolings = tapi == null ? List.of() : tapi.listUsable(o.getMaterialId()).stream()
                .map(t -> new ToolingOption(t.id(), t.code(), t.name())).toList();
        BigDecimal limit = op == null ? BigDecimal.ZERO : progress.inputLimit(o, ops, op.getSeq());
        BigDecimal reported = op == null ? BigDecimal.ZERO : OrderProgressService.input(op).add(drafts.getOrDefault(op.getSeq(), BigDecimal.ZERO));
        return new Context(o.getId(), o.getDocNo(), o.getProdStatus(), m.id(), m.code(), m.name(), m.baseUom(), o.getBatchNo(), o.getQty(),
                op == null ? null : op.getSeq(), op == null ? null : op.getOperation(), op != null && Boolean.TRUE.equals(op.getIsReportPoint()),
                op != null && Boolean.TRUE.equals(op.getIsInspectionPoint()), wc == null ? null : wc.id(), wc == null ? null : wc.name(),
                wo == null ? null : wo.getId(), wo == null ? null : wo.getDocNo(), wo == null ? null : wo.getPlanQty(),
                wo == null ? null : wo.getGoodQty().add(wo.getDefectQty()).add(wo.getScrapQty()), limit, reported, MfgSupport.max0(limit.subtract(reported)),
                options, toolings, support.params().getBool(ProductionModuleConfig.P_REQUIRE_WORK_ORDER), support.params().getBool(ProductionModuleConfig.P_AUTO_APPROVE),
                o.getReleasedAt() == null ? null : o.getReleasedAt().toLocalDate());
    }

    /** 未审核（草稿）的正常报工占用的投入，键：工序号 */
    private Map<Integer, BigDecimal> draftInputs(Long orderId, Long excludeId) {
        return mapper.selectList(new LambdaQueryWrapper<MfgReportDO>().eq(MfgReportDO::getProdOrderId, orderId).eq(MfgReportDO::getStatus, DocStatus.DRAFT)
                        .eq(MfgReportDO::getReportKind, NORMAL).ne(excludeId != null, MfgReportDO::getId, excludeId))
                .stream().collect(Collectors.groupingBy(MfgReportDO::getOperationSeq,
                        Collectors.reducing(BigDecimal.ZERO, r -> r.getGoodQty().add(r.getDefectQty()).add(r.getScrapQty()), BigDecimal::add)));
    }

    private BigDecimal reportable(MfgProdOrderDO o, List<MfgProdOrderOperationDO> ops, MfgProdOrderOperationDO op, Map<Integer, BigDecimal> drafts) {
        return MfgSupport.max0(progress.inputLimit(o, ops, op.getSeq()).subtract(OrderProgressService.input(op))
                .subtract(drafts.getOrDefault(op.getSeq(), BigDecimal.ZERO)));
    }

    // ==================== 保存 ====================

    /** 品质 QC-INS-R09：参数开启时首件检验通过前不能报工（品质模块未启用时跳过） */
    private void checkFirstArticle(MfgReportDO r) {
        InspectionQueryApi api = inspectionQueryApi.getIfAvailable();
        if (api != null && r.getProdOrderId() != null) api.checkFirstArticle(r.getProdOrderId());
    }

    @Transactional(rollbackFor = Exception.class)
    public SaveResult create(ReportSave req) {
        MfgReportDO r = new MfgReportDO();
        r.setDocNo(support.nextNo(BIZ_TYPE));
        r.setDocDate(LocalDate.now());
        r.setStatus(DocStatus.DRAFT);
        r.setReportKind(NORMAL);
        List<String> warnings = fill(r, req);
        checkFirstArticle(r);
        mapper.insert(r);
        saveChildren(r, req);
        return afterSave(r, warnings);
    }

    @Transactional(rollbackFor = Exception.class)
    public SaveResult update(Long id, ReportSave req) {
        MfgReportDO r = getOrThrow(id);
        DataScopes.check(r.getOrgId(), r.getDeptId(), r.getOwnerId(), "报工单");
        if (r.getStatus() != DocStatus.DRAFT || !NORMAL.equals(r.getReportKind())) throw new BizException(ProductionErrorCodes.REPORT_NOT_DRAFT);
        if (req.version() != null) r.setVersion(req.version());
        List<String> warnings = fill(r, req);
        checkFirstArticle(r);
        mapper.updateByIdOrFail(r);
        operatorMapper.deleteByParent(id);
        defectMapper.delete(new LambdaQueryWrapper<MfgDefectDO>().eq(MfgDefectDO::getReportId, id));
        saveChildren(r, req);
        return afterSave(r, warnings);
    }

    /** 参数“报工自动审核”打开时保存即审核 */
    private SaveResult afterSave(MfgReportDO r, List<String> warnings) {
        if (support.params().getBool(ProductionModuleConfig.P_AUTO_APPROVE)) doApprove(r);
        MfgReportDO fresh = getOrThrow(r.getId());
        return new SaveResult(fresh.getId(), fresh.getDocNo(), fresh.getStatus().name(), warnings);
    }

    /** 校验（R01～R04、R08、DEF-R01）并填充字段 */
    private List<String> fill(MfgReportDO r, ReportSave req) {
        List<String> warnings = new ArrayList<>();
        MfgProdOrderDO o = progress.getOrThrow(req.prodOrderId());
        DataScopes.check(o.getOrgId(), o.getDeptId(), o.getOwnerId(), "生产订单");
        OrderProgressService.requireRunning(o);
        List<MfgProdOrderOperationDO> ops = progress.operations(o.getId());
        MfgProdOrderOperationDO op = OrderProgressService.operation(ops, req.operationSeq());
        if (op == null || !Boolean.TRUE.equals(op.getIsReportPoint())) throw BizException.of(ProductionErrorCodes.REPORT_NOT_POINT, req.operationSeq());
        MfgWorkOrderDO wo = null;
        if (req.workOrderId() != null) {
            wo = workOrderService.getOrThrow(req.workOrderId());
            if (!WorkOrderService.matches(wo, o.getId(), op.getSeq())) throw BizException.of(ProductionErrorCodes.REPORT_WORK_ORDER_MISMATCH, op.getSeq());
            if (!WorkOrderService.openStatuses().contains(wo.getWoStatus())) throw new BizException(ProductionErrorCodes.WORK_ORDER_CLOSED);
        } else if (support.params().getBool(ProductionModuleConfig.P_REQUIRE_WORK_ORDER)) {
            throw new BizException(ProductionErrorCodes.REPORT_WORK_ORDER_REQUIRED);
        }
        LocalDate date = req.reportDate() != null ? req.reportDate() : LocalDate.now();
        LocalDate released = o.getReleasedAt() == null ? null : o.getReleasedAt().toLocalDate();
        if (date.isAfter(LocalDate.now()) || (released != null && date.isBefore(released))) {
            throw BizException.of(ProductionErrorCodes.REPORT_DATE_INVALID, released == null ? "" : released.toString());
        }
        BigDecimal good = MfgSupport.nz(req.goodQty());
        BigDecimal defect = MfgSupport.nz(req.defectQty());
        BigDecimal scrap = MfgSupport.nz(req.scrapQty());
        if (good.signum() < 0 || defect.signum() < 0 || scrap.signum() < 0) throw BizException.of(ProductionErrorCodes.LINE_QTY_POSITIVE, 1);
        BigDecimal total = good.add(defect).add(scrap);
        if (total.signum() == 0) throw new BizException(ProductionErrorCodes.REPORT_QTY_ZERO);
        if (scrap.signum() > 0 && !StringUtils.hasText(req.scrapReason())) throw new BizException(ProductionErrorCodes.REPORT_SCRAP_REASON);
        BigDecimal hours = MfgSupport.nz(req.workHours());
        if (hours.signum() <= 0) throw new BizException(ProductionErrorCodes.REPORT_HOURS_POSITIVE);
        List<OperatorSave> operators = req.operators() == null ? List.of()
                : req.operators().stream().filter(x -> x.userId() != null || StringUtils.hasText(x.operatorName())).toList();
        if (operators.size() > 1) {
            BigDecimal sum = MfgSupport.sum(operators.stream().map(OperatorSave::hours).toList());
            if (sum.compareTo(hours) != 0) throw BizException.of(ProductionErrorCodes.REPORT_OPERATOR_HOURS, MfgSupport.plain(sum), MfgSupport.plain(hours));
        }
        List<DefectSave> defects = req.defects() == null ? List.of() : req.defects().stream().filter(d -> d.qty() != null && d.qty().signum() > 0).toList();
        BigDecimal defectSum = MfgSupport.sum(defects.stream().map(DefectSave::qty).toList());
        if (defect.signum() > 0 && defectSum.compareTo(defect) != 0) {
            throw BizException.of(ProductionErrorCodes.DEFECT_SUM_MISMATCH, MfgSupport.plain(defectSum), MfgSupport.plain(defect));
        }
        BigDecimal reportable = reportable(o, ops, op, draftInputs(o.getId(), r.getId()));
        if (total.compareTo(reportable) > 0) throw BizException.of(ProductionErrorCodes.REPORT_OVER_QTY, MfgSupport.plain(reportable));
        if (req.toolingId() != null) {
            ToolingApi tapi = toolingApi.getIfAvailable();
            if (tapi != null) {
                tapi.validateUsable(req.toolingId());
                if (tapi.listUsable(o.getMaterialId()).stream().noneMatch(t -> t.id().equals(req.toolingId()))) {
                    warnings.add("所选工装的适用物料不包含本产品");
                }
            }
        }
        WorkCenterDTO wc = support.workCenter(req.workCenterId() != null ? req.workCenterId() : wo != null ? wo.getWorkCenterId() : op.getWorkCenterId());
        if (wc != null && wc.hoursPerShift() != null && wc.hoursPerShift().signum() > 0) {
            BigDecimal max = wc.hoursPerShift().multiply(BigDecimal.valueOf(Math.max(1, operators.size())));
            if (hours.compareTo(max) > 0) warnings.add("工时 " + MfgSupport.plain(hours) + "h 超过班次时长 × 人数（" + MfgSupport.plain(max) + "h）");
        }
        r.setProdOrderId(o.getId());
        r.setSourceType(ProductionModuleConfig.PROD_ORDER);
        r.setSourceId(o.getId());
        r.setSourceNo(o.getDocNo());
        r.setOperationSeq(op.getSeq());
        r.setWorkOrderId(wo == null ? null : wo.getId());
        r.setWorkCenterId(wc == null ? null : wc.id());
        r.setReportDate(date);
        r.setShift(StringUtils.hasText(req.shift()) ? req.shift() : wo != null ? wo.getShift() : "DAY");
        r.setGoodQty(good);
        r.setDefectQty(defect);
        r.setScrapQty(scrap);
        r.setScrapReason(scrap.signum() > 0 ? req.scrapReason() : null);
        r.setWorkHours(hours);
        r.setMachineHours(req.machineHours());
        r.setToolingId(req.toolingId());
        r.setStartTime(req.startTime());
        r.setEndTime(req.endTime());
        r.setRemark(MfgSupport.trim(req.remark()));
        if (r.getId() == null) {
            support.fillOwner(r, null, o.getDeptId());
            r.setDeptId(o.getDeptId());
        }
        return warnings;
    }

    private void saveChildren(MfgReportDO r, ReportSave req) {
        List<OperatorSave> operators = req.operators() == null ? List.of()
                : req.operators().stream().filter(x -> x.userId() != null || StringUtils.hasText(x.operatorName())).toList();
        for (OperatorSave x : operators) {
            MfgReportOperatorDO d = new MfgReportOperatorDO();
            d.setReportId(r.getId());
            d.setUserId(x.userId());
            d.setOperatorName(MfgSupport.trim(x.operatorName()));
            d.setHours(operators.size() == 1 && x.hours() == null ? r.getWorkHours() : x.hours());
            operatorMapper.insert(d);
        }
        if (r.getDefectQty().signum() <= 0 || req.defects() == null) return;
        MfgProdOrderDO o = progress.getOrThrow(r.getProdOrderId());
        for (DefectSave x : req.defects()) {
            if (x.qty() == null || x.qty().signum() <= 0) continue;
            MfgDefectDO d = new MfgDefectDO();
            d.setReportId(r.getId());
            d.setProdOrderId(o.getId());
            d.setOperationSeq(r.getOperationSeq());
            d.setMaterialId(o.getMaterialId());
            d.setReportDate(r.getReportDate());
            d.setDeptId(o.getDeptId());
            d.setDefectCode(x.defectCode());
            d.setQty(x.qty());
            d.setPosition(MfgSupport.trim(x.position()));
            d.setDescription(MfgSupport.trim(x.description()));
            d.setImageFileIds(x.imageFileIds() == null || x.imageFileIds().isEmpty() ? null
                    : x.imageFileIds().stream().map(String::valueOf).collect(Collectors.joining(",")));
            d.setDisposition("DRAFT");
            d.setRepairedQty(BigDecimal.ZERO);
            d.setScrappedQty(BigDecimal.ZERO);
            defectMapper.insert(d);
            if (x.imageFileIds() != null && !x.imageFileIds().isEmpty()) fileApi.bind(x.imageFileIds(), ProductionModuleConfig.DEFECT, d.getId());
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        MfgReportDO r = getOrThrow(id);
        DataScopes.check(r.getOrgId(), r.getDeptId(), r.getOwnerId(), "报工单");
        if (r.getStatus() != DocStatus.DRAFT) throw new BizException(ProductionErrorCodes.REPORT_NOT_DRAFT);
        operatorMapper.deleteByParent(id);
        defectMapper.delete(new LambdaQueryWrapper<MfgDefectDO>().eq(MfgDefectDO::getReportId, id));
        mapper.deleteById(id);
    }

    // ==================== 审核 ====================

    @Transactional(rollbackFor = Exception.class)
    public String approve(Long id) {
        MfgReportDO r = getOrThrow(id);
        DataScopes.check(r.getOrgId(), r.getDeptId(), r.getOwnerId(), "报工单");
        if (r.getStatus() != DocStatus.DRAFT) throw new BizException(ProductionErrorCodes.REPORT_NOT_DRAFT);
        doApprove(r);
        return DocStatus.APPROVED.name();
    }

    /** 批量审核：逐张独立事务 */
    public BatchResult batchApprove(List<Long> ids) {
        int ok = 0;
        List<String> errors = new ArrayList<>();
        for (Long id : ids == null ? List.<Long>of() : ids) {
            try {
                tx.executeWithoutResult(s -> approve(id));
                ok++;
            } catch (BizException e) {
                MfgReportDO r = mapper.selectById(id);
                errors.add((r == null ? String.valueOf(id) : r.getDocNo()) + "：" + e.getMessage());
            }
        }
        return new BatchResult(ok, errors);
    }

    /**
     * 审核（R05）：工序（及其前面的非报工点工序）数量、工时 → 工单 → 订单完工 / 报废 → 倒冲 → 工装次数 → 不良进入待处理 →
     * 首次报工进入生产中 → 完工判断 → 事件（报工审核、IPQC、不良、进度）。
     */
    private void doApprove(MfgReportDO r) {
        MfgProdOrderDO o = progress.getOrThrow(r.getProdOrderId());
        boolean normal = NORMAL.equals(r.getReportKind());
        if (normal) OrderProgressService.requireRunning(o);
        else OrderProgressService.requireActive(o);
        List<MfgProdOrderOperationDO> ops = progress.operations(o.getId());
        MfgProdOrderOperationDO op = OrderProgressService.operation(ops, r.getOperationSeq());
        if (op == null) throw BizException.of(ProductionErrorCodes.REPORT_NOT_POINT, r.getOperationSeq());
        BigDecimal good = r.getGoodQty();
        BigDecimal defect = r.getDefectQty();
        BigDecimal scrap = r.getScrapQty();
        if (normal) {
            BigDecimal reportable = reportable(o, ops, op, draftInputs(o.getId(), r.getId()));
            if (good.add(defect).add(scrap).compareTo(reportable) > 0) throw BizException.of(ProductionErrorCodes.REPORT_OVER_QTY, MfgSupport.plain(reportable));
        }
        applyOperation(o, ops, op, r, 1);
        workOrderService.onReport(r.getWorkOrderId(), good, normal ? defect : BigDecimal.ZERO, normal ? scrap : BigDecimal.ZERO, 1);
        if (normal) {
            issueService.backflush(o, r, backflushNeeds(o, ops, op, good.add(scrap)));
            ToolingApi tapi = toolingApi.getIfAvailable();
            if (r.getToolingId() != null && tapi != null) {
                int count = tapi.usageOf(r.getToolingId(), good.add(defect));
                if (count > 0) tapi.addUsage(r.getToolingId(), count, r.getDocNo());
            }
            for (MfgDefectDO d : defectMapper.selectList(new LambdaQueryWrapper<MfgDefectDO>().eq(MfgDefectDO::getReportId, r.getId()))) {
                d.setDisposition("PENDING");
                defectMapper.updateByIdOrFail(d);
                eventPublisher.publish(new DefectRegisteredEvent(d.getId(), r.getId(), o.getId(), o.getDocNo(), o.getMaterialId(), r.getOperationSeq(),
                        d.getDefectCode(), d.getQty(), r.getReportDate()));
            }
        }
        r.setApprovedAt(LocalDateTime.now());
        r.setApprovedBy(support.currentUser());
        support.fire(MfgStateMachines.REPORT, mapper, r, BIZ_TYPE, MfgAction.APPROVE, null);
        // 倒冲出库自动确认时回写会更新订单，重新读取后再累加完工 / 报废
        o = progress.getOrThrow(o.getId());
        MfgProdOrderOperationDO last = OrderProgressService.lastReportPoint(ops);
        if (last != null && last.getSeq() == op.getSeq()) o.setCompletedQty(o.getCompletedQty().add(good));
        o.setScrappedQty(o.getScrappedQty().add(scrap));
        progress.markStarted(o);
        progress.checkCompletion(o);
        eventPublisher.publish(new WorkReportApprovedEvent(r.getId(), r.getDocNo(), o.getId(), o.getDocNo(), o.getMaterialId(), r.getOperationSeq(),
                r.getWorkCenterId(), normal ? r.getToolingId() : null, r.getReportDate(), good, normal ? defect : BigDecimal.ZERO, scrap, r.getWorkHours(),
                r.getMachineHours()));
        if (Boolean.TRUE.equals(op.getIsInspectionPoint()) && good.signum() > 0) {
            eventPublisher.publish(new IpqcTriggerEvent(r.getId(), r.getDocNo(), o.getId(), o.getDocNo(), o.getMaterialId(), o.getBatchNo(), op.getSeq(),
                    r.getWorkCenterId(), good));
        }
        progress.publishProgress(o);
    }

    /**
     * 工序数量（sign = 1 审核 / -1 反审核）：正常报工 合格/一次合格/不良/报废 增加；返修 合格/返修 增加、不良减少；报废补报 报废增加、不良减少。
     * 前面的非报工点工序随本报工点同步合格数量。
     */
    private void applyOperation(MfgProdOrderDO o, List<MfgProdOrderOperationDO> ops, MfgProdOrderOperationDO op, MfgReportDO r, int sign) {
        BigDecimal s = BigDecimal.valueOf(sign);
        BigDecimal good = r.getGoodQty().multiply(s);
        BigDecimal defect = r.getDefectQty().multiply(s);
        BigDecimal scrap = r.getScrapQty().multiply(s);
        switch (r.getReportKind()) {
            case REPAIR -> {
                op.setGoodQty(op.getGoodQty().add(good));
                op.setRepairedQty(op.getRepairedQty().add(good));
                op.setDefectQty(op.getDefectQty().subtract(good));
            }
            case SCRAP -> {
                op.setScrapQty(op.getScrapQty().add(scrap));
                op.setDefectQty(op.getDefectQty().subtract(scrap));
            }
            default -> {
                op.setGoodQty(op.getGoodQty().add(good));
                op.setFirstGoodQty(op.getFirstGoodQty().add(good));
                op.setDefectQty(op.getDefectQty().add(defect));
                op.setScrapQty(op.getScrapQty().add(scrap));
            }
        }
        op.setActualHours(op.getActualHours().add(r.getWorkHours().multiply(s)));
        op.setOpStatus(opStatus(o, op));
        operationMapper.updateByIdOrFail(op);
        MfgProdOrderOperationDO prev = OrderProgressService.previousReportPoint(ops, op.getSeq());
        for (MfgProdOrderOperationDO x : ops) {
            if (Boolean.TRUE.equals(x.getIsReportPoint()) || x.getSeq() >= op.getSeq() || (prev != null && x.getSeq() <= prev.getSeq())) continue;
            if (REPAIR.equals(r.getReportKind()) || NORMAL.equals(r.getReportKind())) x.setGoodQty(MfgSupport.max0(x.getGoodQty().add(good)));
            x.setOpStatus(opStatus(o, x));
            operationMapper.updateByIdOrFail(x);
        }
    }

    private static String opStatus(MfgProdOrderDO o, MfgProdOrderOperationDO op) {
        if (op.getGoodQty().add(op.getScrapQty()).compareTo(o.getQty()) >= 0) return "DONE";
        return OrderProgressService.input(op).signum() > 0 ? "RUNNING" : "WAITING";
    }

    /** 倒冲需求（ISS-R05）：该工序的倒冲用料（没有工序或工序不存在的挂末道报工点），数量 = (合格 + 报废) × 单位用量 × (1 + 损耗) */
    private List<BackflushNeed> backflushNeeds(MfgProdOrderDO o, List<MfgProdOrderOperationDO> ops, MfgProdOrderOperationDO op, BigDecimal output) {
        if (output.signum() <= 0) return List.of();
        Set<Integer> seqs = ops.stream().map(MfgProdOrderOperationDO::getSeq).collect(Collectors.toSet());
        MfgProdOrderOperationDO last = OrderProgressService.lastReportPoint(ops);
        MfgProdOrderOperationDO prev = OrderProgressService.previousReportPoint(ops, op.getSeq());
        List<MfgProdOrderMaterialDO> mats = materialMapper.selectByParent(o.getId()).stream().filter(m -> "BACKFLUSH".equals(m.getIssueMethod())).toList();
        Map<Long, MaterialDTO> ms = support.materials(mats.stream().map(MfgProdOrderMaterialDO::getComponentId).toList());
        List<BackflushNeed> out = new ArrayList<>();
        for (MfgProdOrderMaterialDO m : mats) {
            Integer seq = m.getOperationSeq();
            boolean mine;
            if (seq == null || !seqs.contains(seq)) mine = last != null && last.getSeq() == op.getSeq();
            else mine = seq <= op.getSeq() && (prev == null || seq > prev.getSeq());
            if (!mine) continue;
            MaterialDTO c = ms.get(m.getComponentId());
            BigDecimal qty = support.roundUp(output.multiply(m.getQtyPer()).multiply(BigDecimal.ONE.add(MfgSupport.nz(m.getScrapRate()))),
                    c == null ? null : c.baseUom());
            out.add(new BackflushNeed(m, qty));
        }
        return out;
    }

    // ==================== 反审核 ====================

    /** 反审核（R06）：下道工序已报工或已申请完工入库时不能反审核；冲回数量、倒冲、工装次数 */
    @Transactional(rollbackFor = Exception.class)
    public void unapprove(Long id) {
        MfgReportDO r = getOrThrow(id);
        DataScopes.check(r.getOrgId(), r.getDeptId(), r.getOwnerId(), "报工单");
        if (r.getStatus() != DocStatus.APPROVED) throw BizException.of(ProductionErrorCodes.STATUS_NOT_ALLOWED, r.getStatus().label(), "反审核");
        MfgProdOrderDO o = progress.getOrThrow(r.getProdOrderId());
        ProdStatus st = OrderProgressService.status(o);
        if (st == ProdStatus.CLOSED || st == ProdStatus.VOIDED) throw BizException.of(ProductionErrorCodes.STATUS_NOT_ALLOWED, st.label(), "反审核报工");
        List<MfgProdOrderOperationDO> ops = progress.operations(o.getId());
        MfgProdOrderOperationDO op = OrderProgressService.operation(ops, r.getOperationSeq());
        boolean normal = NORMAL.equals(r.getReportKind());
        BigDecimal goodDelta = SCRAP.equals(r.getReportKind()) ? BigDecimal.ZERO : r.getGoodQty();
        MfgProdOrderOperationDO next = OrderProgressService.nextReportPoint(ops, op.getSeq());
        if (next != null && OrderProgressService.input(next).compareTo(op.getGoodQty().subtract(goodDelta)) > 0) {
            throw new BizException(ProductionErrorCodes.REPORT_NEXT_REPORTED);
        }
        MfgProdOrderOperationDO last = OrderProgressService.lastReportPoint(ops);
        boolean isLast = last != null && last.getSeq() == op.getSeq();
        if (isLast && o.getCompletedQty().subtract(goodDelta).compareTo(o.getFinishedRequestQty()) < 0) {
            throw new BizException(ProductionErrorCodes.REPORT_FINISH_REQUESTED);
        }
        List<MfgDefectDO> defects = defectMapper.selectList(new LambdaQueryWrapper<MfgDefectDO>().eq(MfgDefectDO::getReportId, id));
        if (normal) {
            if (defects.stream().anyMatch(d -> d.getRepairedQty().signum() > 0 || d.getScrappedQty().signum() > 0 || d.getNcrNo() != null)) {
                throw new BizException(ProductionErrorCodes.DEFECT_HANDLED);
            }
            issueService.reverseBackflush(id);
            ToolingApi tapi = toolingApi.getIfAvailable();
            if (r.getToolingId() != null && tapi != null) {
                int count = tapi.usageOf(r.getToolingId(), r.getGoodQty().add(r.getDefectQty()));
                if (count > 0) tapi.addUsage(r.getToolingId(), -count, r.getDocNo());
            }
            for (MfgDefectDO d : defects) {
                d.setDisposition("DRAFT");
                defectMapper.updateByIdOrFail(d);
            }
        } else if (r.getDefectId() != null) {
            MfgDefectDO d = defectMapper.selectById(r.getDefectId());
            if (d != null) {
                if (REPAIR.equals(r.getReportKind())) d.setRepairedQty(MfgSupport.max0(d.getRepairedQty().subtract(r.getGoodQty())));
                else d.setScrappedQty(MfgSupport.max0(d.getScrappedQty().subtract(r.getScrapQty())));
                d.setDisposition(disposition(d));
                defectMapper.updateByIdOrFail(d);
            }
        }
        applyOperation(o, ops, op, r, -1);
        o = progress.getOrThrow(o.getId());
        if (isLast) o.setCompletedQty(o.getCompletedQty().subtract(goodDelta));
        o.setScrappedQty(o.getScrappedQty().subtract(r.getScrapQty()));
        orderMapper.updateByIdOrFail(o);
        workOrderService.onReport(r.getWorkOrderId(), r.getGoodQty(), normal ? r.getDefectQty() : BigDecimal.ZERO, normal ? r.getScrapQty() : BigDecimal.ZERO, -1);
        r.setApprovedAt(null);
        r.setApprovedBy(null);
        support.fire(MfgStateMachines.REPORT, mapper, r, BIZ_TYPE, MfgAction.UNAPPROVE, null);
        progress.checkCompletion(o);
        eventPublisher.publish(new WorkReportReversedEvent(r.getId(), r.getDocNo(), o.getId(), o.getDocNo(), o.getMaterialId(), r.getOperationSeq(),
                r.getWorkCenterId(), normal ? r.getToolingId() : null, r.getReportDate(), r.getGoodQty(), normal ? r.getDefectQty() : BigDecimal.ZERO,
                r.getScrapQty(), r.getWorkHours(), r.getMachineHours()));
        progress.publishProgress(o);
        if (!normal) {
            // 补充报工单反审核后删除（不良回到待处理）
            mapper.deleteById(r.getId());
        }
    }

    // ==================== 不良处置（补充报工） ====================

    /**
     * 返修完成 / 报废（需求 09-06 3.1）：生成并审核一张补充报工单（返修：合格 = 数量；报废：报废 = 数量），更新不良处置数量。
     */
    public MfgReportDO supplementary(MfgDefectDO d, String kind, BigDecimal qty, String scrapReason) {
        MfgReportDO origin = getOrThrow(d.getReportId());
        MfgProdOrderDO o = progress.getOrThrow(d.getProdOrderId());
        MfgReportDO r = new MfgReportDO();
        r.setDocNo(support.nextNo(BIZ_TYPE));
        r.setDocDate(LocalDate.now());
        r.setStatus(DocStatus.DRAFT);
        r.setReportKind(kind);
        r.setDefectId(d.getId());
        r.setProdOrderId(o.getId());
        r.setSourceType(ProductionModuleConfig.PROD_ORDER);
        r.setSourceId(o.getId());
        r.setSourceNo(o.getDocNo());
        r.setOperationSeq(d.getOperationSeq());
        r.setWorkCenterId(origin.getWorkCenterId());
        r.setReportDate(LocalDate.now());
        r.setShift(origin.getShift());
        r.setGoodQty(REPAIR.equals(kind) ? qty : BigDecimal.ZERO);
        r.setDefectQty(BigDecimal.ZERO);
        r.setScrapQty(SCRAP.equals(kind) ? qty : BigDecimal.ZERO);
        r.setScrapReason(SCRAP.equals(kind) ? scrapReason : null);
        r.setWorkHours(BigDecimal.ZERO);
        r.setRemark((REPAIR.equals(kind) ? "返修" : "不良报废") + "（报工单 " + origin.getDocNo() + "）");
        support.fillOwner(r, null, o.getDeptId());
        r.setDeptId(o.getDeptId());
        mapper.insert(r);
        if (REPAIR.equals(kind)) d.setRepairedQty(d.getRepairedQty().add(qty));
        else d.setScrappedQty(d.getScrappedQty().add(qty));
        d.setDisposition(disposition(d));
        d.setHandledBy(support.currentUser());
        d.setHandledAt(LocalDateTime.now());
        defectMapper.updateByIdOrFail(d);
        doApprove(r);
        return r;
    }

    /** 处置状态（DEF-R02）：全部处置后按数量多者显示已返修 / 已报废，否则待处理 */
    public static String disposition(MfgDefectDO d) {
        BigDecimal handled = d.getRepairedQty().add(d.getScrappedQty());
        if (handled.compareTo(d.getQty()) < 0) return "PENDING";
        return d.getRepairedQty().compareTo(d.getScrappedQty()) >= 0 ? "REPAIRED" : "SCRAPPED";
    }

    public boolean isOpenWorkOrder(MfgWorkOrderDO w) {
        return !WoStatus.CANCELED.name().equals(w.getWoStatus());
    }
}
