package com.erp.module.production.service.workorder;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.common.exception.BizException;
import com.erp.common.result.PageResult;
import com.erp.framework.datascope.DataScopes;
import com.erp.module.engineering.api.material.MaterialDTO;
import com.erp.module.engineering.api.routing.WorkCenterDTO;
import com.erp.module.production.api.ProductionErrorCodes;
import com.erp.module.production.config.ProductionModuleConfig;
import com.erp.module.production.controller.vo.WorkOrderVOs.Dispatchable;
import com.erp.module.production.controller.vo.WorkOrderVOs.DispatchLine;
import com.erp.module.production.controller.vo.WorkOrderVOs.DispatchReq;
import com.erp.module.production.controller.vo.WorkOrderVOs.DispatchResult;
import com.erp.module.production.controller.vo.WorkOrderVOs.Load;
import com.erp.module.production.controller.vo.WorkOrderVOs.WorkOrderQuery;
import com.erp.module.production.controller.vo.WorkOrderVOs.WorkOrderRow;
import com.erp.module.production.dal.dataobject.MfgProdOrderDO;
import com.erp.module.production.dal.dataobject.MfgProdOrderOperationDO;
import com.erp.module.production.dal.dataobject.MfgReportDO;
import com.erp.module.production.dal.dataobject.MfgWorkOrderDO;
import com.erp.module.production.dal.mapper.MfgProdOrderMapper;
import com.erp.module.production.dal.mapper.MfgProdOrderOperationMapper;
import com.erp.module.production.dal.mapper.MfgReportMapper;
import com.erp.module.production.dal.mapper.MfgWorkOrderMapper;
import com.erp.module.production.service.MfgAction;
import com.erp.module.production.service.MfgStateMachines;
import com.erp.module.production.service.MfgSupport;
import com.erp.module.production.service.ProdStatus;
import com.erp.module.production.service.WoStatus;
import com.erp.module.production.service.order.OrderProgressService;
import com.erp.module.system.api.user.UserDTO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 工单派工（需求 09-02） */
@Service("mfgWorkOrderService")
public class WorkOrderService {

    public static final String BIZ_TYPE = ProductionModuleConfig.WORK_ORDER;
    static final BigDecimal SECONDS_PER_HOUR = BigDecimal.valueOf(3600);

    private final MfgWorkOrderMapper mapper;
    private final MfgProdOrderMapper orderMapper;
    private final MfgProdOrderOperationMapper operationMapper;
    private final MfgReportMapper reportMapper;
    private final OrderProgressService progress;
    private final MfgSupport support;

    public WorkOrderService(MfgWorkOrderMapper mapper, MfgProdOrderMapper orderMapper, MfgProdOrderOperationMapper operationMapper,
                            MfgReportMapper reportMapper, OrderProgressService progress, MfgSupport support) {
        this.mapper = mapper;
        this.orderMapper = orderMapper;
        this.operationMapper = operationMapper;
        this.reportMapper = reportMapper;
        this.progress = progress;
        this.support = support;
    }

    public MfgWorkOrderDO getOrThrow(Long id) {
        MfgWorkOrderDO w = id == null ? null : mapper.selectById(id);
        if (w == null) throw new BizException(ProductionErrorCodes.WORK_ORDER_NOT_EXISTS);
        return w;
    }

    // ==================== 查询 ====================

    public PageResult<WorkOrderRow> page(WorkOrderQuery q) {
        LambdaQueryWrapper<MfgWorkOrderDO> w = new LambdaQueryWrapper<MfgWorkOrderDO>().eq(MfgWorkOrderDO::getDeleted, false);
        if (StringUtils.hasText(q.getDocNo())) w.likeRight(MfgWorkOrderDO::getDocNo, q.getDocNo().trim());
        if (q.getProdOrderId() != null) w.eq(MfgWorkOrderDO::getProdOrderId, q.getProdOrderId());
        if (StringUtils.hasText(q.getProdOrderNo())) w.likeRight(MfgWorkOrderDO::getSourceNo, q.getProdOrderNo().trim());
        if (q.getOperationSeq() != null) w.eq(MfgWorkOrderDO::getOperationSeq, q.getOperationSeq());
        if (q.getWorkCenterId() != null) w.eq(MfgWorkOrderDO::getWorkCenterId, q.getWorkCenterId());
        if (q.getDateFrom() != null) w.ge(MfgWorkOrderDO::getPlanDate, q.getDateFrom());
        if (q.getDateTo() != null) w.le(MfgWorkOrderDO::getPlanDate, q.getDateTo());
        if (StringUtils.hasText(q.getShift())) w.eq(MfgWorkOrderDO::getShift, q.getShift());
        if (StringUtils.hasText(q.getStatuses())) w.in(MfgWorkOrderDO::getWoStatus, Arrays.asList(q.getStatuses().split(",")));
        w.orderByDesc(MfgWorkOrderDO::getPlanDate).orderByDesc(MfgWorkOrderDO::getId);
        IPage<MfgWorkOrderDO> p = mapper.selectScopedPage(new Page<>(q.getPageNo(), q.getPageSize()), w);
        return new PageResult<>(rows(p.getRecords()), p.getTotal());
    }

    public List<WorkOrderRow> rows(List<MfgWorkOrderDO> list) {
        if (list.isEmpty()) return List.of();
        Map<Long, MfgProdOrderDO> orders = orderMapper.selectBatchIds(list.stream().map(MfgWorkOrderDO::getProdOrderId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(MfgProdOrderDO::getId, Function.identity()));
        Map<Long, MaterialDTO> ms = support.materials(orders.values().stream().map(MfgProdOrderDO::getMaterialId).toList());
        Map<String, MfgProdOrderOperationDO> ops = operationMapper.selectByParents(orders.keySet()).stream()
                .collect(Collectors.toMap(op -> op.getProdOrderId() + "#" + op.getSeq(), Function.identity(), (a, b) -> a));
        Map<Long, WorkCenterDTO> wcs = support.workCenters();
        Map<Long, UserDTO> users = support.users(list.stream().map(MfgWorkOrderDO::getTeamLeaderId).toList());
        return list.stream().map(w -> {
            MfgProdOrderDO o = orders.get(w.getProdOrderId());
            MaterialDTO m = o == null ? null : ms.get(o.getMaterialId());
            MfgProdOrderOperationDO op = ops.get(w.getProdOrderId() + "#" + w.getOperationSeq());
            WorkCenterDTO wc = wcs.get(w.getWorkCenterId());
            BigDecimal done = w.getGoodQty().add(w.getDefectQty()).add(w.getScrapQty());
            BigDecimal rate = w.getPlanQty().signum() == 0 ? BigDecimal.ZERO : done.divide(w.getPlanQty(), 4, RoundingMode.HALF_UP);
            return new WorkOrderRow(w.getId(), w.getDocNo(), w.getProdOrderId(), o == null ? null : o.getDocNo(), o == null ? null : o.getMaterialId(),
                    m == null ? null : m.code(), m == null ? null : m.name(), w.getOperationSeq(), op == null ? null : op.getOperation(), w.getWorkCenterId(),
                    wc == null ? null : wc.name(), w.getPlanDate(), w.getShift(), w.getPlanQty(), w.getGoodQty(), w.getDefectQty(), w.getScrapQty(), rate,
                    w.getTeamLeaderId(), MfgSupport.name(users, w.getTeamLeaderId()), w.getWoStatus(), w.getRemark());
        }).toList();
    }

    /** 待派工：已下达/生产中订单的报工点工序，未派 > 0，按优先级、计划开工排序 */
    public List<Dispatchable> dispatchable(Long deptId, Long prodOrderId) {
        LambdaQueryWrapper<MfgProdOrderDO> w = new LambdaQueryWrapper<MfgProdOrderDO>().eq(MfgProdOrderDO::getDeleted, false)
                .in(MfgProdOrderDO::getProdStatus, List.of(ProdStatus.RELEASED.name(), ProdStatus.IN_PROGRESS.name()));
        if (deptId != null) w.in(MfgProdOrderDO::getDeptId, support.deptAndChildren(deptId));
        if (prodOrderId != null) w.eq(MfgProdOrderDO::getId, prodOrderId);
        List<MfgProdOrderDO> orders = orderMapper.selectScopedList(w);
        if (orders.isEmpty()) return List.of();
        Map<Long, MaterialDTO> ms = support.materials(orders.stream().map(MfgProdOrderDO::getMaterialId).toList());
        Map<Long, List<MfgProdOrderOperationDO>> opsByOrder = operationMapper.selectByParents(orders.stream().map(MfgProdOrderDO::getId).toList())
                .stream().collect(Collectors.groupingBy(MfgProdOrderOperationDO::getProdOrderId));
        Map<String, BigDecimal> noWo = reportedWithoutWorkOrder(orders.stream().map(MfgProdOrderDO::getId).toList());
        Map<Long, WorkCenterDTO> wcs = support.workCenters();
        BigDecimal factor = MfgSupport.onePlusPct(support.paramDecimal(ProductionModuleConfig.P_OVER_PRODUCE_PCT));
        List<Dispatchable> out = new ArrayList<>();
        for (MfgProdOrderDO o : orders.stream().sorted(Comparator.comparing(MfgProdOrderDO::getPriority).thenComparing(MfgProdOrderDO::getPlanStart)).toList()) {
            MaterialDTO m = ms.get(o.getMaterialId());
            for (MfgProdOrderOperationDO op : opsByOrder.getOrDefault(o.getId(), List.of())) {
                if (!Boolean.TRUE.equals(op.getIsReportPoint())) continue;
                BigDecimal undispatched = undispatched(o, op, factor, noWo.getOrDefault(o.getId() + "#" + op.getSeq(), BigDecimal.ZERO));
                if (undispatched.signum() <= 0) continue;
                WorkCenterDTO wc = op.getWorkCenterId() == null ? null : wcs.get(op.getWorkCenterId());
                out.add(new Dispatchable(o.getId(), o.getDocNo(), o.getMaterialId(), m == null ? null : m.code(), m == null ? null : m.name(), o.getPriority(),
                        o.getPlanStart(), o.getPlanEnd(), o.getDeptId(), op.getSeq(), op.getOperation(), op.getWorkCenterId(), wc == null ? null : wc.name(),
                        o.getQty(), op.getDispatchedQty(), undispatched, op.getStdRunSeconds(), op.getStdSetupMinutes()));
            }
        }
        return out;
    }

    private static BigDecimal undispatched(MfgProdOrderDO o, MfgProdOrderOperationDO op, BigDecimal factor, BigDecimal reportedWithoutWo) {
        return MfgSupport.max0(o.getQty().multiply(factor).setScale(4, RoundingMode.DOWN).subtract(MfgSupport.nz(op.getDispatchedQty()))
                .subtract(reportedWithoutWo));
    }

    /** 未经派工直接报工的投入（合格 + 不良 + 报废，首次报工口径），键：订单 ID#工序号 */
    private Map<String, BigDecimal> reportedWithoutWorkOrder(List<Long> orderIds) {
        Map<String, BigDecimal> map = new LinkedHashMap<>();
        if (orderIds.isEmpty()) return map;
        for (MfgReportDO r : reportMapper.selectList(new LambdaQueryWrapper<MfgReportDO>().in(MfgReportDO::getProdOrderId, orderIds)
                .isNull(MfgReportDO::getWorkOrderId).eq(MfgReportDO::getReportKind, "NORMAL"))) {
            map.merge(r.getProdOrderId() + "#" + r.getOperationSeq(), r.getGoodQty().add(r.getDefectQty()).add(r.getScrapQty()), BigDecimal::add);
        }
        return map;
    }

    /** 工作中心当天已派工时（派工数量 × 标准秒 ÷ 3600 + 准备时间）与日产能 */
    public Load load(Long workCenterId, LocalDate date) {
        WorkCenterDTO wc = support.workCenter(workCenterId);
        return new Load(workCenterId, date, loadHours(workCenterId, date, null), wc == null ? null : wc.capacityHoursPerDay());
    }

    private BigDecimal loadHours(Long workCenterId, LocalDate date, Long excludeId) {
        List<MfgWorkOrderDO> list = mapper.selectList(new LambdaQueryWrapper<MfgWorkOrderDO>().eq(MfgWorkOrderDO::getWorkCenterId, workCenterId)
                .eq(MfgWorkOrderDO::getPlanDate, date).ne(MfgWorkOrderDO::getWoStatus, WoStatus.CANCELED.name())
                .ne(excludeId != null, MfgWorkOrderDO::getId, excludeId));
        if (list.isEmpty()) return BigDecimal.ZERO;
        Map<String, MfgProdOrderOperationDO> ops = operationMapper.selectByParents(list.stream().map(MfgWorkOrderDO::getProdOrderId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(op -> op.getProdOrderId() + "#" + op.getSeq(), Function.identity(), (a, b) -> a));
        BigDecimal total = BigDecimal.ZERO;
        for (MfgWorkOrderDO w : list) total = total.add(hours(ops.get(w.getProdOrderId() + "#" + w.getOperationSeq()), w.getPlanQty()));
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal hours(MfgProdOrderOperationDO op, BigDecimal qty) {
        if (op == null) return BigDecimal.ZERO;
        return qty.multiply(MfgSupport.nz(op.getStdRunSeconds())).divide(SECONDS_PER_HOUR, 6, RoundingMode.HALF_UP)
                .add(MfgSupport.nz(op.getStdSetupMinutes()).divide(BigDecimal.valueOf(60), 6, RoundingMode.HALF_UP));
    }

    // ==================== 派工 ====================

    /** 派工（R01、R02）：生成工单；超出工作中心日产能时提示 */
    @Transactional(rollbackFor = Exception.class)
    public DispatchResult dispatch(DispatchReq req) {
        MfgProdOrderDO o = progress.getOrThrow(req.prodOrderId());
        DataScopes.check(o.getOrgId(), o.getDeptId(), o.getOwnerId(), "生产订单");
        OrderProgressService.requireRunning(o);
        List<MfgProdOrderOperationDO> ops = progress.operations(o.getId());
        MfgProdOrderOperationDO op = OrderProgressService.operation(ops, req.operationSeq());
        if (op == null || !Boolean.TRUE.equals(op.getIsReportPoint())) throw BizException.of(ProductionErrorCodes.REPORT_NOT_POINT, req.operationSeq());
        BigDecimal factor = MfgSupport.onePlusPct(support.paramDecimal(ProductionModuleConfig.P_OVER_PRODUCE_PCT));
        BigDecimal limit = undispatched(o, op, factor, reportedWithoutWorkOrder(List.of(o.getId())).getOrDefault(o.getId() + "#" + op.getSeq(), BigDecimal.ZERO));
        BigDecimal total = BigDecimal.ZERO;
        int no = 0;
        for (DispatchLine l : req.lines()) {
            no++;
            if (l.planQty() == null || l.planQty().signum() <= 0) throw BizException.of(ProductionErrorCodes.LINE_QTY_POSITIVE, no);
            if (support.workCenter(l.workCenterId()) == null) throw BizException.of(ProductionErrorCodes.LINE_FIELD_REQUIRED, no, "工作中心");
            total = total.add(l.planQty());
        }
        if (total.compareTo(limit) > 0) throw BizException.of(ProductionErrorCodes.WORK_ORDER_OVER_QTY, MfgSupport.plain(limit));
        List<Long> ids = new ArrayList<>();
        Map<String, BigDecimal> added = new LinkedHashMap<>();
        for (DispatchLine l : req.lines()) {
            MfgWorkOrderDO w = new MfgWorkOrderDO();
            w.setDocNo(support.nextNo(BIZ_TYPE));
            w.setDocDate(LocalDate.now());
            w.setProdOrderId(o.getId());
            w.setSourceType(ProductionModuleConfig.PROD_ORDER);
            w.setSourceId(o.getId());
            w.setSourceNo(o.getDocNo());
            w.setOperationSeq(op.getSeq());
            w.setWorkCenterId(l.workCenterId());
            w.setPlanDate(l.planDate());
            w.setShift(l.shift());
            w.setTeamLeaderId(l.teamLeaderId());
            w.setPlanQty(l.planQty());
            w.setGoodQty(BigDecimal.ZERO);
            w.setDefectQty(BigDecimal.ZERO);
            w.setScrapQty(BigDecimal.ZERO);
            w.setWoStatus(WoStatus.DISPATCHED.name());
            w.setStatus(WoStatus.DISPATCHED.docStatus());
            w.setRemark(MfgSupport.trim(l.remark()));
            support.fillOwner(w, l.teamLeaderId() != null ? l.teamLeaderId() : support.currentUser(), o.getDeptId());
            w.setDeptId(o.getDeptId());
            mapper.insert(w);
            ids.add(w.getId());
            added.merge(l.workCenterId() + "#" + l.planDate(), hours(op, l.planQty()), BigDecimal::add);
        }
        op.setDispatchedQty(MfgSupport.nz(op.getDispatchedQty()).add(total));
        operationMapper.updateByIdOrFail(op);
        support.log(ProductionModuleConfig.PROD_ORDER, o.getId(), o.getDocNo(), "DISPATCH", "派工", o.getProdStatus(), o.getProdStatus(),
                "工序 " + op.getSeq() + " 派工 " + MfgSupport.plain(total) + "（" + ids.size() + " 张工单）");
        List<String> warnings = new ArrayList<>();
        for (String key : added.keySet()) {
            String[] k = key.split("#");
            WorkCenterDTO wc = support.workCenter(Long.valueOf(k[0]));
            if (wc == null || wc.capacityHoursPerDay() == null || wc.capacityHoursPerDay().signum() <= 0) continue;
            BigDecimal load = loadHours(wc.id(), LocalDate.parse(k[1]), null);
            if (load.compareTo(wc.capacityHoursPerDay()) > 0) {
                warnings.add("工作中心「" + wc.name() + "」" + k[1] + " 已派 " + MfgSupport.plain(load) + "h，超过日产能 " + MfgSupport.plain(wc.capacityHoursPerDay()) + "h");
            }
        }
        return new DispatchResult(ids, warnings);
    }

    /** 取消（R03）：未报工的工单，数量回到“未派” */
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id) {
        MfgWorkOrderDO w = getOrThrow(id);
        DataScopes.check(w.getOrgId(), w.getDeptId(), w.getOwnerId(), "工单");
        if (reportMapper.selectCount(new LambdaQueryWrapper<MfgReportDO>().eq(MfgReportDO::getWorkOrderId, id)) > 0) {
            throw new BizException(ProductionErrorCodes.WORK_ORDER_REPORTED);
        }
        if (!WoStatus.DISPATCHED.name().equals(w.getWoStatus())) throw new BizException(ProductionErrorCodes.WORK_ORDER_CLOSED);
        fire(w, MfgAction.CANCEL, null);
        releaseDispatched(w, w.getPlanQty());
    }

    /** 手工完成（R04）：剩余数量不再生产，回到“未派” */
    @Transactional(rollbackFor = Exception.class)
    public void complete(Long id) {
        MfgWorkOrderDO w = getOrThrow(id);
        DataScopes.check(w.getOrgId(), w.getDeptId(), w.getOwnerId(), "工单");
        WoStatus s = WoStatus.valueOf(w.getWoStatus());
        if (s != WoStatus.DISPATCHED && s != WoStatus.RUNNING) throw new BizException(ProductionErrorCodes.WORK_ORDER_CLOSED);
        BigDecimal done = w.getGoodQty().add(w.getDefectQty()).add(w.getScrapQty());
        fire(w, MfgAction.COMPLETE, "手工完成");
        BigDecimal remain = w.getPlanQty().subtract(done);
        if (remain.signum() > 0) releaseDispatched(w, remain);
    }

    private void releaseDispatched(MfgWorkOrderDO w, BigDecimal qty) {
        MfgProdOrderOperationDO op = OrderProgressService.operation(progress.operations(w.getProdOrderId()), w.getOperationSeq());
        if (op == null) return;
        op.setDispatchedQty(MfgSupport.max0(MfgSupport.nz(op.getDispatchedQty()).subtract(qty)));
        operationMapper.updateByIdOrFail(op);
    }

    private void fire(MfgWorkOrderDO w, MfgAction action, String reason) {
        WoStatus from = WoStatus.valueOf(w.getWoStatus());
        WoStatus to = MfgStateMachines.WORK_ORDER.fire(from, action);
        w.setWoStatus(to.name());
        w.setStatus(to.docStatus());
        mapper.updateByIdOrFail(w);
        support.log(BIZ_TYPE, w.getId(), w.getDocNo(), action.name(), action.label(), from.name(), to.name(), reason);
    }

    // ==================== 报工回写 ====================

    /** 报工审核 / 反审核（sign = 1 / -1）：回写数量；首次报工进行中，报满自动完成（R04），反审核后恢复 */
    public void onReport(Long workOrderId, BigDecimal good, BigDecimal defect, BigDecimal scrap, int sign) {
        if (workOrderId == null) return;
        MfgWorkOrderDO w = getOrThrow(workOrderId);
        BigDecimal s = BigDecimal.valueOf(sign);
        w.setGoodQty(w.getGoodQty().add(good.multiply(s)));
        w.setDefectQty(w.getDefectQty().add(defect.multiply(s)));
        w.setScrapQty(w.getScrapQty().add(scrap.multiply(s)));
        BigDecimal done = w.getGoodQty().add(w.getDefectQty()).add(w.getScrapQty());
        WoStatus st = WoStatus.valueOf(w.getWoStatus());
        if (sign > 0) {
            if (st == WoStatus.DISPATCHED) st = move(w, MfgAction.START);
            if (st == WoStatus.RUNNING && done.compareTo(w.getPlanQty()) >= 0) st = move(w, MfgAction.COMPLETE);
        } else {
            if (st == WoStatus.DONE && done.compareTo(w.getPlanQty()) < 0) st = move(w, MfgAction.REOPEN);
            if (st == WoStatus.RUNNING && done.signum() == 0) move(w, MfgAction.RESET);
        }
        mapper.updateByIdOrFail(w);
    }

    private static WoStatus move(MfgWorkOrderDO w, MfgAction action) {
        WoStatus to = MfgStateMachines.WORK_ORDER.fire(WoStatus.valueOf(w.getWoStatus()), action);
        w.setWoStatus(to.name());
        w.setStatus(to.docStatus());
        return to;
    }

    // ==================== 打印 ====================

    public Map<String, Object> printData(Long id) {
        MfgWorkOrderDO w = getOrThrow(id);
        WorkOrderRow r = rows(List.of(w)).get(0);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("docNo", r.docNo());
        data.put("prodOrderNo", r.prodOrderNo());
        data.put("materialCode", r.materialCode());
        data.put("materialName", r.materialName());
        data.put("operationSeq", r.operationSeq());
        data.put("operation", r.operation());
        data.put("workCenterName", r.workCenterName());
        data.put("planDate", r.planDate());
        data.put("shiftName", support.dictLabel("mfg_shift", r.shift()));
        data.put("planQty", r.planQty());
        data.put("teamLeaderName", r.teamLeaderName());
        data.put("remark", r.remark());
        return data;
    }

    /** 工单是否属于某订单工序（报工校验） */
    public static boolean matches(MfgWorkOrderDO w, Long prodOrderId, int seq) {
        return w.getProdOrderId().equals(prodOrderId) && w.getOperationSeq() == seq;
    }

    public static Set<String> openStatuses() {
        return Set.of(WoStatus.DISPATCHED.name(), WoStatus.RUNNING.name());
    }
}
