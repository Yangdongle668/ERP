package com.erp.module.finance.service.cost;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.common.exception.BizException;
import com.erp.common.util.Decimals;
import com.erp.module.engineering.api.material.MaterialDTO;
import com.erp.module.finance.api.FinanceErrorCodes;
import com.erp.module.finance.api.cost.CostCalculatedEvent;
import com.erp.module.finance.config.FinanceModuleConfig;
import com.erp.module.finance.controller.vo.CostVOs.CheckResult;
import com.erp.module.finance.controller.vo.CostVOs.ExceptionVO;
import com.erp.module.finance.controller.vo.CostVOs.ExpenseLine;
import com.erp.module.finance.controller.vo.CostVOs.ExpenseRow;
import com.erp.module.finance.controller.vo.CostVOs.ExpenseSave;
import com.erp.module.finance.controller.vo.CostVOs.MaterialCostRow;
import com.erp.module.finance.controller.vo.CostVOs.OrderCostVO;
import com.erp.module.finance.controller.vo.CostVOs.OrderMaterialVO;
import com.erp.module.finance.controller.vo.CostVOs.ProductCostRow;
import com.erp.module.finance.controller.vo.CostVOs.RunVO;
import com.erp.module.finance.dal.dataobject.FinCostExceptionDO;
import com.erp.module.finance.dal.dataobject.FinCostExpenseDO;
import com.erp.module.finance.dal.dataobject.FinCostMaterialDO;
import com.erp.module.finance.dal.dataobject.FinCostOrderDO;
import com.erp.module.finance.dal.dataobject.FinCostOrderMaterialDO;
import com.erp.module.finance.dal.dataobject.FinCostRunDO;
import com.erp.module.finance.dal.dataobject.FinPeriodDO;
import com.erp.module.finance.dal.mapper.FinCostExceptionMapper;
import com.erp.module.finance.dal.mapper.FinCostExpenseMapper;
import com.erp.module.finance.dal.mapper.FinCostMaterialMapper;
import com.erp.module.finance.dal.mapper.FinCostOrderMapper;
import com.erp.module.finance.dal.mapper.FinCostOrderMaterialMapper;
import com.erp.module.finance.dal.mapper.FinCostRunMapper;
import com.erp.module.finance.dal.mapper.FinPeriodMapper;
import com.erp.module.finance.dal.mapper.FinVoucherMapper;
import com.erp.module.finance.service.FinSupport;
import com.erp.module.finance.service.setting.SettingService;
import com.erp.module.inventory.api.cost.InventoryCostApi;
import com.erp.module.inventory.api.cost.InventoryCostApi.CostTxn;
import com.erp.module.inventory.api.cost.InventoryCostApi.MaterialBalance;
import com.erp.module.production.api.cost.ProductionCostApi;
import com.erp.module.production.api.cost.ProductionCostApi.CostOrder;
import com.erp.module.production.api.cost.ProductionCostApi.WorkHour;
import com.erp.module.system.api.user.UserDTO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 成本核算（12-07）：月加权平均法计算物料单价；按生产订单归集材料，按车间费用与工时分配人工、制造费用；
 * 完工与在制分配（只留材料 / 约当产量）；按低位码自下而上逐层计算自制件单价；回填库存流水成本与期末结存金额。
 */
@Service
public class CostService {

    static final String RUNNING = "RUNNING";
    static final String SUCCESS = "SUCCESS";
    static final String FAILED = "FAILED";
    /** 参与加权的入库（按流水单价计价） */
    static final Set<String> WEIGHT_IN = Set.of("PURCHASE_IN", "OUTSOURCE_IN", "OTHER_IN", "COUNT_GAIN", "OPENING");
    static final String PRODUCTION_IN = "PRODUCTION_IN";
    /** 按加权单价计价的出库 */
    static final Set<String> OUT = Set.of("PRODUCTION_ISSUE", "OUTSOURCE_ISSUE", "SALES_OUT", "PURCHASE_RETURN", "OTHER_OUT", "COUNT_LOSS");
    /** 冲减出库的入库（按加权单价计价，不参与加权） */
    static final Set<String> OUT_RETURN = Set.of("PRODUCTION_RETURN", "OUTSOURCE_RETURN", "SALES_RETURN");
    static final BigDecimal SWING = new BigDecimal("0.30");
    static final BigDecimal HALF = new BigDecimal("0.5");

    private final FinCostRunMapper runMapper;
    private final FinCostExpenseMapper expenseMapper;
    private final FinCostMaterialMapper materialMapper;
    private final FinCostOrderMapper orderMapper;
    private final FinCostOrderMaterialMapper orderMaterialMapper;
    private final FinCostExceptionMapper exceptionMapper;
    private final FinPeriodMapper periodMapper;
    private final FinVoucherMapper voucherMapper;
    private final InventoryCostApi inventoryCostApi;
    private final ProductionCostApi productionCostApi;
    private final SettingService settingService;
    private final FinSupport support;
    private final TransactionTemplate newTx;

    public CostService(FinCostRunMapper runMapper, FinCostExpenseMapper expenseMapper, FinCostMaterialMapper materialMapper, FinCostOrderMapper orderMapper,
                       FinCostOrderMaterialMapper orderMaterialMapper, FinCostExceptionMapper exceptionMapper, FinPeriodMapper periodMapper,
                       FinVoucherMapper voucherMapper, InventoryCostApi inventoryCostApi, ProductionCostApi productionCostApi, SettingService settingService,
                       FinSupport support, PlatformTransactionManager txManager) {
        this.runMapper = runMapper;
        this.expenseMapper = expenseMapper;
        this.materialMapper = materialMapper;
        this.orderMapper = orderMapper;
        this.orderMaterialMapper = orderMaterialMapper;
        this.exceptionMapper = exceptionMapper;
        this.periodMapper = periodMapper;
        this.voucherMapper = voucherMapper;
        this.inventoryCostApi = inventoryCostApi;
        this.productionCostApi = productionCostApi;
        this.settingService = settingService;
        this.support = support;
        this.newTx = new TransactionTemplate(txManager);
        this.newTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    // ==================== 检查、费用 ====================

    public CheckResult check(String period) {
        boolean invClosed = inventoryCostApi.isPeriodClosed(period);
        boolean finClosed = support.isClosed(period);
        boolean locked = locked(period);
        boolean running = running(period) != null;
        List<FinCostExpenseDO> expenses = expenses(period);
        BigDecimal labor = expenses.stream().map(FinCostExpenseDO::getLaborAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal overhead = expenses.stream().map(FinCostExpenseDO::getOverheadAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        YearMonth ym = YearMonth.parse(period, FinSupport.PERIOD);
        int orders = (int) productionCostApi.getWorkHours(ym.atDay(1), ym.atEndOfMonth()).stream().map(WorkHour::prodOrderId).distinct().count();
        List<String> messages = new ArrayList<>();
        if (!invClosed) messages.add("库存期间 " + period + " 尚未月结");
        if (finClosed) messages.add("会计期间 " + period + " 已结账");
        if (locked) messages.add("成本已锁定");
        if (running) messages.add("成本计算正在进行中");
        if (expenses.isEmpty() && orders > 0) messages.add("本期有报工但尚未录入人工、制造费用");
        FinCostRunDO last = lastRun(period);
        return new CheckResult(period, invClosed, finClosed, locked, running, !expenses.isEmpty(), orders, labor, overhead, last == null ? null : runVO(last),
                invClosed && !finClosed && !locked && !running, messages);
    }

    /** 费用录入表：本期有报工的车间 + 已录入的车间 */
    public List<ExpenseRow> expenseRows(String period) {
        YearMonth ym = YearMonth.parse(period, FinSupport.PERIOD);
        Map<Long, BigDecimal> hours = new LinkedHashMap<>();
        for (WorkHour h : productionCostApi.getWorkHours(ym.atDay(1), ym.atEndOfMonth())) {
            if (h.deptId() != null) hours.merge(h.deptId(), FinSupport.nz(h.workHours()), BigDecimal::add);
        }
        Map<Long, FinCostExpenseDO> saved = expenses(period).stream().collect(Collectors.toMap(FinCostExpenseDO::getDeptId, Function.identity()));
        Set<Long> depts = new java.util.LinkedHashSet<>(hours.keySet());
        depts.addAll(saved.keySet());
        Map<Long, String> names = support.orgNames(depts);
        return depts.stream().map(d -> {
            FinCostExpenseDO e = saved.get(d);
            return new ExpenseRow(d, names.get(d), hours.getOrDefault(d, BigDecimal.ZERO), e == null ? BigDecimal.ZERO : e.getLaborAmount(),
                    e == null ? BigDecimal.ZERO : e.getOverheadAmount(), e == null ? null : e.getRemark());
        }).toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public void saveExpenses(ExpenseSave req) {
        String period = req.period();
        if (support.isClosed(period)) throw BizException.of(FinanceErrorCodes.PERIOD_CLOSED, period);
        if (locked(period)) throw BizException.of(FinanceErrorCodes.CST_LOCKED, period);
        Map<Long, FinCostExpenseDO> saved = expenses(period).stream().collect(Collectors.toMap(FinCostExpenseDO::getDeptId, Function.identity()));
        for (ExpenseLine l : req.lines() == null ? List.<ExpenseLine>of() : req.lines()) {
            FinCostExpenseDO e = saved.get(l.deptId());
            BigDecimal labor = Decimals.amount(FinSupport.nz(l.laborAmount()));
            BigDecimal overhead = Decimals.amount(FinSupport.nz(l.overheadAmount()));
            if (e == null) {
                if (labor.signum() == 0 && overhead.signum() == 0) continue;
                e = new FinCostExpenseDO();
                e.setPeriod(period);
                e.setDeptId(l.deptId());
                e.setLaborAmount(labor);
                e.setOverheadAmount(overhead);
                e.setRemark(FinSupport.limit(l.remark(), 256));
                expenseMapper.insert(e);
            } else {
                e.setLaborAmount(labor);
                e.setOverheadAmount(overhead);
                e.setRemark(FinSupport.limit(l.remark(), 256));
                expenseMapper.updateByIdOrFail(e);
            }
        }
    }

    private List<FinCostExpenseDO> expenses(String period) {
        return expenseMapper.selectList(new LambdaQueryWrapper<FinCostExpenseDO>().eq(FinCostExpenseDO::getPeriod, period));
    }

    // ==================== 计算 ====================

    /**
     * FIN-CST-R01 库存期间已月结；R02 同一期间同时只有一个计算；R03 失败回滚本次全部结果（保留上次成功结果）
     */
    public RunVO calculate(String period) {
        if (!inventoryCostApi.isPeriodClosed(period)) throw BizException.of(FinanceErrorCodes.CST_INV_NOT_CLOSED, period);
        if (support.isClosed(period)) throw BizException.of(FinanceErrorCodes.PERIOD_CLOSED, period);
        if (locked(period)) throw BizException.of(FinanceErrorCodes.CST_LOCKED, period);
        if (running(period) != null) throw new BizException(FinanceErrorCodes.CST_RUNNING);
        FinCostRunDO prev = lastSuccess(period);
        if (prev != null && java.util.stream.Stream.of(prev.getSalesCostVoucherId(), prev.getIssueVoucherId(), prev.getFinishVoucherId())
                .anyMatch(v -> v != null && voucherMapper.selectById(v) != null)) {
            throw BizException.of(FinanceErrorCodes.CST_VOUCHER_EXISTS, period);
        }
        FinCostRunDO run = newTx.execute(s -> {
            FinCostRunDO r = new FinCostRunDO();
            r.setPeriod(period);
            r.setRunStatus(RUNNING);
            r.setStartedAt(LocalDateTime.now());
            r.setOperatorId(support.currentUser());
            r.setMaterialCount(0);
            r.setOrderCount(0);
            r.setTotalCost(BigDecimal.ZERO);
            r.setExceptionCount(0);
            runMapper.insert(r);
            return r;
        });
        try {
            newTx.executeWithoutResult(s -> new Calc(period, run.getId()).execute());
        } catch (RuntimeException e) {
            String msg = e instanceof BizException ? e.getMessage() : e.getClass().getSimpleName() + ": " + Objects.toString(e.getMessage(), "");
            newTx.executeWithoutResult(s -> {
                FinCostRunDO r = runMapper.selectById(run.getId());
                r.setRunStatus(FAILED);
                r.setFinishedAt(LocalDateTime.now());
                r.setErrorMessage(FinSupport.limit(msg, 1000));
                runMapper.updateByIdOrFail(r);
            });
            throw BizException.of(FinanceErrorCodes.CST_FAILED, msg);
        }
        return runVO(runMapper.selectById(run.getId()));
    }

    /** 物料计算状态 */
    static final class Mat {
        final Long id;
        BigDecimal openQty = BigDecimal.ZERO;
        BigDecimal openAmt = BigDecimal.ZERO;
        BigDecimal inQty = BigDecimal.ZERO;
        BigDecimal inAmt = BigDecimal.ZERO;
        BigDecimal prodInQty = BigDecimal.ZERO;
        BigDecimal prodInAmt = BigDecimal.ZERO;
        BigDecimal outQty = BigDecimal.ZERO;
        BigDecimal salesQty = BigDecimal.ZERO;
        BigDecimal issueQty = BigDecimal.ZERO;
        BigDecimal prevUnit;
        BigDecimal unit;
        boolean noPriceIn;

        Mat(Long id) {
            this.id = id;
        }
    }

    /** 订单计算状态 */
    static final class Ord {
        final Long id;
        CostOrder info;
        final Map<Long, BigDecimal[]> materials = new LinkedHashMap<>();
        BigDecimal finishedQty = BigDecimal.ZERO;
        BigDecimal hours = BigDecimal.ZERO;
        BigDecimal labor = BigDecimal.ZERO;
        BigDecimal overhead = BigDecimal.ZERO;
        FinCostOrderDO prev;
        FinCostOrderDO result;

        Ord(Long id) {
            this.id = id;
        }
    }

    /** 一次成本计算 */
    final class Calc {
        final String period;
        final Long runId;
        final Map<Long, Mat> mats = new HashMap<>();
        final Map<Long, Ord> ords = new LinkedHashMap<>();
        final List<FinCostExceptionDO> exceptions = new ArrayList<>();
        final boolean equivalent = "EQUIVALENT".equals(support.params().getString(FinanceModuleConfig.P_WIP_METHOD));

        Calc(String period, Long runId) {
            this.period = period;
            this.runId = runId;
        }

        Mat mat(Long id) {
            return mats.computeIfAbsent(id, Mat::new);
        }

        Ord ord(Long id) {
            return ords.computeIfAbsent(id, Ord::new);
        }

        void execute() {
            clearResults(period);
            YearMonth ym = YearMonth.parse(period, FinSupport.PERIOD);
            String prevPeriod = ym.minusMonths(1).format(FinSupport.PERIOD);
            for (MaterialBalance b : inventoryCostApi.getOpeningBalances(period)) {
                Mat m = mat(b.materialId());
                m.openQty = FinSupport.nz(b.qty());
                m.openAmt = FinSupport.nz(b.amount());
            }
            for (FinCostMaterialDO p : materialMapper.selectList(new LambdaQueryWrapper<FinCostMaterialDO>().eq(FinCostMaterialDO::getPeriod, prevPeriod))) {
                mat(p.getMaterialId()).prevUnit = p.getUnitCost();
            }
            List<CostTxn> txns = inventoryCostApi.getPeriodTxns(period);
            Map<Long, Long> issueOrders = productionCostApi.getOrderIdsBySource(ProductionCostApi.SOURCE_ISSUE, sourceIds(txns, ProductionCostApi.SOURCE_ISSUE));
            Map<Long, Long> returnOrders = productionCostApi.getOrderIdsBySource(ProductionCostApi.SOURCE_RETURN, sourceIds(txns, ProductionCostApi.SOURCE_RETURN));
            Map<Long, Long> finishOrders = productionCostApi.getOrderIdsBySource(ProductionCostApi.SOURCE_FINISH, sourceIds(txns, ProductionCostApi.SOURCE_FINISH));
            // 1. 汇总流水
            for (CostTxn t : txns) {
                Mat m = mat(t.materialId());
                String type = t.bizType();
                boolean naturalIn = !OUT.contains(type);
                BigDecimal s = ("IN".equals(t.direction()) == naturalIn) ? t.qty() : t.qty().negate();
                if (WEIGHT_IN.contains(type)) {
                    m.inQty = m.inQty.add(s);
                    if (t.unitCost() == null) m.noPriceIn = true;
                    else m.inAmt = m.inAmt.add(s.multiply(t.unitCost()));
                } else if (PRODUCTION_IN.equals(type)) {
                    Long orderId = finishOrders.get(t.sourceId());
                    if (orderId != null) ord(orderId).finishedQty = ord(orderId).finishedQty.add(s);
                    m.prodInQty = m.prodInQty.add(s);
                } else if (OUT.contains(type) || OUT_RETURN.contains(type)) {
                    BigDecimal out = OUT.contains(type) ? s : s.negate();
                    m.outQty = m.outQty.add(out);
                    if ("SALES_OUT".equals(type) || "SALES_RETURN".equals(type)) m.salesQty = m.salesQty.add(out);
                    if ("PRODUCTION_ISSUE".equals(type) || "PRODUCTION_RETURN".equals(type)) {
                        m.issueQty = m.issueQty.add(out);
                        Long orderId = "PRODUCTION_ISSUE".equals(type) ? issueOrders.get(t.sourceId()) : returnOrders.get(t.sourceId());
                        if (orderId != null) {
                            BigDecimal[] q = ord(orderId).materials.computeIfAbsent(t.materialId(), k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
                            if ("PRODUCTION_ISSUE".equals(type)) q[0] = q[0].add(s);
                            else q[1] = q[1].add(s);
                        }
                    }
                }
            }
            // 2. 订单：工时、上期在制
            for (WorkHour h : productionCostApi.getWorkHours(ym.atDay(1), ym.atEndOfMonth())) ord(h.prodOrderId()).hours = ord(h.prodOrderId()).hours.add(FinSupport.nz(h.workHours()));
            for (FinCostOrderDO p : orderMapper.selectList(new LambdaQueryWrapper<FinCostOrderDO>().eq(FinCostOrderDO::getPeriod, prevPeriod))) {
                if (FinSupport.nz(p.getEndingWip()).signum() != 0 || ords.containsKey(p.getProdOrderId())) ord(p.getProdOrderId()).prev = p;
            }
            for (CostOrder o : productionCostApi.getOrders(ords.keySet())) ords.get(o.prodOrderId()).info = o;
            ords.values().removeIf(o -> o.info == null);
            allocate(ym);
            // 3. 逐层计算：非自制件先定单价；自制件在其订单的全部子件单价确定后计算
            Set<Long> produced = ords.values().stream().filter(o -> o.finishedQty.signum() != 0).map(o -> o.info.materialId()).collect(Collectors.toSet());
            for (Mat m : mats.values()) if (!produced.contains(m.id)) weigh(m);
            Set<Long> pending = new HashSet<>(ords.keySet());
            boolean progress = true;
            while (!pending.isEmpty()) {
                if (!progress) {
                    // 循环引用或无法确定：未定单价的子件取期初 / 上期单价
                    for (Long id : pending) {
                        for (Long c : ords.get(id).materials.keySet()) {
                            Mat cm = mat(c);
                            if (cm.unit == null) {
                                cm.unit = fallback(cm);
                                exception("CYCLE", c, id, null, "物料单价无法按层级确定（循环引用），取期初 / 上期单价 " + plain6(cm.unit));
                            }
                        }
                    }
                }
                progress = false;
                for (Long id : new ArrayList<>(pending)) {
                    Ord o = ords.get(id);
                    if (o.materials.keySet().stream().anyMatch(c -> mat(c).unit == null && !c.equals(o.info.materialId()))) continue;
                    compute(o);
                    pending.remove(id);
                    progress = true;
                }
                for (Long p : produced) {
                    Mat m = mat(p);
                    if (m.unit != null) continue;
                    boolean ready = ords.values().stream().filter(o -> o.info.materialId().equals(p) && o.finishedQty.signum() != 0).allMatch(o -> o.result != null);
                    if (ready) {
                        weigh(m);
                        progress = true;
                    }
                }
            }
            for (Mat m : mats.values()) if (m.unit == null) weigh(m);
            save();
        }

        List<Long> sourceIds(List<CostTxn> txns, String sourceType) {
            return txns.stream().filter(t -> sourceType.equals(t.sourceType())).map(CostTxn::sourceId).filter(Objects::nonNull).distinct().toList();
        }

        /** 人工、制造费用：车间费用 × 订单工时 ÷ 车间总工时（参数为 OUTPUT 时按报工合格数） */
        void allocate(YearMonth ym) {
            String laborBasis = support.params().getString(FinanceModuleConfig.P_LABOR_ALLOC);
            String overheadBasis = support.params().getString(FinanceModuleConfig.P_OVERHEAD_ALLOC);
            List<WorkHour> hours = productionCostApi.getWorkHours(ym.atDay(1), ym.atEndOfMonth()).stream().filter(h -> ords.containsKey(h.prodOrderId())).toList();
            Map<String, String> names = new HashMap<>();
            Map<Long, String> depts = support.orgNames(expenses(period).stream().map(FinCostExpenseDO::getDeptId).toList());
            depts.forEach((k, v) -> names.put(String.valueOf(k), v));
            for (FinCostExpenseDO e : expenses(period)) {
                List<WorkHour> inDept = hours.stream().filter(h -> e.getDeptId().equals(h.deptId())).toList();
                spread(e, inDept, "OUTPUT".equals(laborBasis), true, names);
                spread(e, inDept, "OUTPUT".equals(overheadBasis), false, names);
            }
        }

        void spread(FinCostExpenseDO e, List<WorkHour> inDept, boolean byOutput, boolean labor, Map<String, String> names) {
            BigDecimal amount = labor ? FinSupport.nz(e.getLaborAmount()) : FinSupport.nz(e.getOverheadAmount());
            if (amount.signum() == 0) return;
            Function<WorkHour, BigDecimal> basis = byOutput ? h -> FinSupport.nz(h.goodQty()) : h -> FinSupport.nz(h.workHours());
            BigDecimal total = inDept.stream().map(basis).reduce(BigDecimal.ZERO, BigDecimal::add);
            if (total.signum() == 0) {
                exception("NO_HOURS", null, null, e.getDeptId(), "车间「" + Objects.toString(names.get(String.valueOf(e.getDeptId())), "") + "」有"
                        + (labor ? "人工" : "制造") + "费用 " + FinSupport.plain(amount) + " 但本期没有报工工时，费用未分配");
                return;
            }
            BigDecimal allocated = BigDecimal.ZERO;
            List<WorkHour> list = inDept.stream().filter(h -> basis.apply(h).signum() != 0).toList();
            for (int i = 0; i < list.size(); i++) {
                WorkHour h = list.get(i);
                BigDecimal share = i == list.size() - 1 ? amount.subtract(allocated) : Decimals.amount(amount.multiply(basis.apply(h)).divide(total, 6, RoundingMode.HALF_UP));
                allocated = allocated.add(share);
                Ord o = ords.get(h.prodOrderId());
                if (labor) o.labor = o.labor.add(share);
                else o.overhead = o.overhead.add(share);
            }
        }

        /** 加权单价 = (期初金额 + 本期入库金额) ÷ (期初数量 + 本期入库数量)；数量为 0 时沿用期初 / 上期单价 */
        void weigh(Mat m) {
            BigDecimal qty = m.openQty.add(m.inQty).add(m.prodInQty);
            BigDecimal amt = m.openAmt.add(m.inAmt).add(m.prodInAmt);
            m.unit = qty.signum() > 0 && m.inQty.add(m.prodInQty).add(m.openQty).signum() > 0 ? amt.divide(qty, 6, RoundingMode.HALF_UP) : fallback(m);
            if (m.unit.signum() < 0) m.unit = fallback(m).max(BigDecimal.ZERO);
        }

        BigDecimal fallback(Mat m) {
            if (m.openQty.signum() > 0) return m.openAmt.divide(m.openQty, 6, RoundingMode.HALF_UP);
            return m.prevUnit != null ? m.prevUnit : BigDecimal.ZERO;
        }

        /** 订单成本：期初在制 + 本期材料 + 人工 + 制费；完工与在制按参数分配 */
        void compute(Ord o) {
            FinCostOrderDO r = new FinCostOrderDO();
            r.setPeriod(period);
            r.setProdOrderId(o.id);
            r.setProdOrderNo(o.info.docNo());
            r.setMaterialId(o.info.materialId());
            r.setDeptId(o.info.deptId());
            r.setOrderType(o.info.orderType());
            r.setWorkHours(Decimals.qty(o.hours));
            BigDecimal openWip = o.prev == null ? BigDecimal.ZERO : FinSupport.nz(o.prev.getEndingWip());
            BigDecimal openWipMat = o.prev == null ? BigDecimal.ZERO : FinSupport.nz(o.prev.getEndingWipMaterial());
            BigDecimal material = BigDecimal.ZERO;
            for (Map.Entry<Long, BigDecimal[]> e : o.materials.entrySet()) {
                Mat cm = mat(e.getKey());
                BigDecimal unit = cm.unit == null ? fallback(cm) : cm.unit;
                material = material.add(Decimals.amount(e.getValue()[0].subtract(e.getValue()[1]).multiply(unit)));
            }
            BigDecimal total = openWip.add(material).add(o.labor).add(o.overhead);
            BigDecimal finished = o.finishedQty;
            BigDecimal cum = (o.prev == null ? BigDecimal.ZERO : FinSupport.nz(o.prev.getCumFinishedQty())).add(finished);
            boolean completed = "COMPLETED".equals(o.info.prodStatus()) || "CLOSED".equals(o.info.prodStatus());
            BigDecimal matTotal = openWipMat.add(material);
            BigDecimal endWip;
            BigDecimal endWipMat;
            if (finished.signum() <= 0) {
                endWip = total;
                endWipMat = matTotal;
            } else if (completed) {
                endWip = BigDecimal.ZERO;
                endWipMat = BigDecimal.ZERO;
            } else {
                BigDecimal remaining = FinSupport.nz(o.info.planQty()).subtract(cum).max(BigDecimal.ZERO);
                endWipMat = remaining.signum() == 0 ? BigDecimal.ZERO
                        : Decimals.amount(matTotal.multiply(remaining).divide(finished.add(remaining), 6, RoundingMode.HALF_UP));
                BigDecimal conv = total.subtract(matTotal);
                BigDecimal convEnd = BigDecimal.ZERO;
                if (equivalent && remaining.signum() > 0 && conv.signum() != 0) {
                    BigDecimal eq = remaining.multiply(HALF);
                    convEnd = Decimals.amount(conv.multiply(eq).divide(finished.add(eq), 6, RoundingMode.HALF_UP));
                }
                endWip = endWipMat.add(convEnd);
            }
            BigDecimal finishedCost = Decimals.amount(total.subtract(endWip));
            BigDecimal finishedMat = Decimals.amount(matTotal.subtract(endWipMat));
            BigDecimal conv = total.subtract(matTotal);
            BigDecimal ratio = conv.signum() == 0 ? BigDecimal.ONE : finishedCost.subtract(finishedMat).divide(conv, 6, RoundingMode.HALF_UP);
            BigDecimal finishedLabor = Decimals.amount(o.labor.multiply(ratio));
            r.setOpeningWip(Decimals.amount(openWip));
            r.setOpeningWipMaterial(Decimals.amount(openWipMat));
            r.setMaterialCost(Decimals.amount(material));
            r.setLaborCost(Decimals.amount(o.labor));
            r.setOverheadCost(Decimals.amount(o.overhead));
            r.setTotalCost(Decimals.amount(total));
            r.setFinishedQty(Decimals.qty(finished));
            r.setFinishedCost(finishedCost);
            r.setFinishedMaterial(finishedMat);
            r.setFinishedLabor(finishedLabor);
            r.setFinishedOverhead(finishedCost.subtract(finishedMat).subtract(finishedLabor));
            r.setEndingWip(Decimals.amount(endWip));
            r.setEndingWipMaterial(Decimals.amount(endWipMat));
            r.setCumFinishedQty(Decimals.qty(cum));
            r.setUnitCost(finished.signum() > 0 ? finishedCost.divide(finished, 6, RoundingMode.HALF_UP) : BigDecimal.ZERO);
            o.result = r;
            if (finished.signum() != 0) {
                Mat pm = mat(o.info.materialId());
                pm.prodInAmt = pm.prodInAmt.add(finishedCost);
            }
            if ((o.labor.signum() != 0 || o.overhead.signum() != 0) && o.hours.signum() == 0) {
                exception("NO_HOURS", null, o.id, o.info.deptId(), "订单 " + o.info.docNo() + " 无工时却分配了费用");
            }
        }

        void exception(String type, Long materialId, Long orderId, Long deptId, String message) {
            FinCostExceptionDO e = new FinCostExceptionDO();
            e.setPeriod(period);
            e.setRunId(runId);
            e.setExType(type);
            e.setMaterialId(materialId);
            e.setProdOrderId(orderId);
            e.setDeptId(deptId);
            e.setMessage(FinSupport.limit(message, 512));
            exceptions.add(e);
        }

        /** 保存结果、回填流水与期末结存（FIN-CST-R04） */
        void save() {
            Map<Long, MaterialDTO> ms = support.materials(mats.keySet());
            Map<Long, BigDecimal> unitByMaterial = new HashMap<>();
            for (Mat m : mats.values()) {
                FinCostMaterialDO r = new FinCostMaterialDO();
                r.setPeriod(period);
                r.setMaterialId(m.id);
                r.setOpeningQty(Decimals.qty(m.openQty));
                r.setOpeningAmount(Decimals.amount(m.openAmt));
                r.setInQty(Decimals.qty(m.inQty.add(m.prodInQty)));
                r.setInAmount(Decimals.amount(m.inAmt.add(m.prodInAmt)));
                r.setUnitCost(m.unit);
                r.setPrevUnitCost(m.prevUnit);
                r.setOutQty(Decimals.qty(m.outQty));
                r.setOutAmount(Decimals.multiplyAmount(m.outQty, m.unit));
                BigDecimal closingQty = m.openQty.add(m.inQty).add(m.prodInQty).subtract(m.outQty);
                r.setClosingQty(Decimals.qty(closingQty));
                r.setClosingAmount(Decimals.multiplyAmount(closingQty, m.unit));
                r.setSalesOutQty(Decimals.qty(m.salesQty));
                r.setSalesOutAmount(Decimals.multiplyAmount(m.salesQty, m.unit));
                r.setIssueAmount(Decimals.multiplyAmount(m.issueQty, m.unit));
                r.setProductionInAmount(Decimals.amount(m.prodInAmt));
                materialMapper.insert(r);
                unitByMaterial.put(m.id, m.unit);
                MaterialDTO md = ms.get(m.id);
                String name = md == null ? String.valueOf(m.id) : md.code() + " " + md.name();
                if (closingQty.signum() < 0) exception("NEGATIVE_BALANCE", m.id, null, null, name + " 期末结存为负数 " + Decimals.qty(closingQty).stripTrailingZeros().toPlainString());
                if (m.prevUnit != null && m.prevUnit.signum() > 0
                        && m.unit.subtract(m.prevUnit).abs().divide(m.prevUnit, 6, RoundingMode.HALF_UP).compareTo(SWING) > 0) {
                    exception("PRICE_SWING", m.id, null, null, name + " 单价 " + plain6(m.unit) + " 与上期 " + plain6(m.prevUnit) + " 相比波动超过 30%");
                }
                if (m.outQty.signum() > 0 && m.unit.signum() == 0) exception("ZERO_COST_OUT", m.id, null, null, name + " 有出库但单价为 0");
                if (m.noPriceIn) exception("NO_PRICE_IN", m.id, null, null, name + " 有未计价的入库（金额按 0 参与加权）");
            }
            BigDecimal totalFinished = BigDecimal.ZERO;
            Map<Long, BigDecimal> unitByOrder = new HashMap<>();
            for (Ord o : ords.values()) {
                if (o.result == null) continue;
                orderMapper.insert(o.result);
                unitByOrder.put(o.id, o.result.getUnitCost());
                totalFinished = totalFinished.add(o.result.getFinishedCost());
                for (Map.Entry<Long, BigDecimal[]> e : o.materials.entrySet()) {
                    FinCostOrderMaterialDO x = new FinCostOrderMaterialDO();
                    x.setCostOrderId(o.result.getId());
                    x.setPeriod(period);
                    x.setProdOrderId(o.id);
                    x.setMaterialId(e.getKey());
                    x.setIssueQty(Decimals.qty(e.getValue()[0]));
                    x.setReturnQty(Decimals.qty(e.getValue()[1]));
                    BigDecimal unit = mat(e.getKey()).unit;
                    x.setUnitCost(unit);
                    x.setAmount(Decimals.multiplyAmount(e.getValue()[0].subtract(e.getValue()[1]), unit));
                    orderMaterialMapper.insert(x);
                }
            }
            // 回填流水：出库、冲减出库的入库按加权单价；生产入库按订单完工单位成本；未计价入库按加权单价
            Map<Long, BigDecimal> byTxn = new HashMap<>();
            List<CostTxn> txns = inventoryCostApi.getPeriodTxns(period);
            Map<Long, Long> finishOrders = productionCostApi.getOrderIdsBySource(ProductionCostApi.SOURCE_FINISH, sourceIds(txns, ProductionCostApi.SOURCE_FINISH));
            for (CostTxn t : txns) {
                BigDecimal unit = unitByMaterial.get(t.materialId());
                if (OUT.contains(t.bizType()) || OUT_RETURN.contains(t.bizType())) byTxn.put(t.txnId(), unit);
                else if (PRODUCTION_IN.equals(t.bizType())) {
                    BigDecimal u = unitByOrder.get(finishOrders.get(t.sourceId()));
                    byTxn.put(t.txnId(), u != null ? u : unit);
                } else if (t.unitCost() == null) byTxn.put(t.txnId(), unit);
            }
            byTxn.values().removeIf(Objects::isNull);
            inventoryCostApi.applyCosts(period, byTxn);
            inventoryCostApi.saveClosingCosts(period, unitByMaterial);
            for (FinCostExceptionDO e : exceptions) exceptionMapper.insert(e);
            FinCostRunDO run = runMapper.selectById(runId);
            run.setRunStatus(SUCCESS);
            run.setFinishedAt(LocalDateTime.now());
            run.setMaterialCount(mats.size());
            run.setOrderCount((int) ords.values().stream().filter(o -> o.result != null).count());
            run.setTotalCost(Decimals.amount(totalFinished));
            run.setExceptionCount(exceptions.size());
            runMapper.updateByIdOrFail(run);
        }
    }

    static String plain6(BigDecimal v) {
        return v == null ? "" : v.stripTrailingZeros().toPlainString();
    }

    private void clearResults(String period) {
        orderMaterialMapper.deleteByPeriod(period);
        orderMapper.deleteByPeriod(period);
        materialMapper.deleteByPeriod(period);
        exceptionMapper.deleteByPeriod(period);
    }

    // ==================== 锁定 ====================

    /** 锁定：最近一次计算成功；锁定后不能再计算；发布 CostCalculatedEvent（FIN-CST-R05） */
    @Transactional(rollbackFor = Exception.class)
    public void lock(String period) {
        if (lastSuccess(period) == null || !SUCCESS.equals(lastRun(period).getRunStatus())) throw BizException.of(FinanceErrorCodes.CST_NOT_CALCULATED, period);
        if (running(period) != null) throw new BizException(FinanceErrorCodes.CST_RUNNING);
        FinPeriodDO p = settingService.ensurePeriod(period);
        if (Boolean.TRUE.equals(p.getCostLocked())) return;
        p.setCostLocked(true);
        periodMapper.updateByIdOrFail(p);
        support.log(FinanceModuleConfig.VOUCHER, p.getId(), period, "COST_LOCK", "成本锁定", null, null, null);
        support.events().publish(new CostCalculatedEvent(period));
    }

    /** 解锁：期间已结账时需先反结账 */
    @Transactional(rollbackFor = Exception.class)
    public void unlock(String period) {
        if (support.isClosed(period)) throw BizException.of(FinanceErrorCodes.CST_UNLOCK_CLOSED, period);
        FinPeriodDO p = settingService.ensurePeriod(period);
        p.setCostLocked(false);
        periodMapper.updateByIdOrFail(p);
        support.log(FinanceModuleConfig.VOUCHER, p.getId(), period, "COST_UNLOCK", "成本解锁", null, null, null);
    }

    public boolean locked(String period) {
        FinPeriodDO p = periodMapper.selectOne(new LambdaQueryWrapper<FinPeriodDO>().eq(FinPeriodDO::getPeriod, period));
        return p != null && Boolean.TRUE.equals(p.getCostLocked());
    }

    /** 进行中的计算（超过 1 小时视为中断） */
    private FinCostRunDO running(String period) {
        return runMapper.selectList(new LambdaQueryWrapper<FinCostRunDO>().eq(FinCostRunDO::getPeriod, period).eq(FinCostRunDO::getRunStatus, RUNNING)
                .gt(FinCostRunDO::getStartedAt, LocalDateTime.now().minusHours(1))).stream().findFirst().orElse(null);
    }

    private FinCostRunDO lastRun(String period) {
        return runMapper.selectList(new LambdaQueryWrapper<FinCostRunDO>().eq(FinCostRunDO::getPeriod, period).orderByDesc(FinCostRunDO::getId).last("LIMIT 1"))
                .stream().findFirst().orElse(null);
    }

    public FinCostRunDO lastSuccess(String period) {
        return runMapper.selectList(new LambdaQueryWrapper<FinCostRunDO>().eq(FinCostRunDO::getPeriod, period).eq(FinCostRunDO::getRunStatus, SUCCESS)
                .orderByDesc(FinCostRunDO::getId).last("LIMIT 1")).stream().findFirst().orElse(null);
    }

    // ==================== 查询 ====================

    public RunVO run(Long id) {
        FinCostRunDO r = runMapper.selectById(id);
        if (r == null) throw BizException.of(FinanceErrorCodes.NOT_EXISTS, "计算记录");
        return runVO(r);
    }

    public List<RunVO> runs(String period) {
        return runMapper.selectList(new LambdaQueryWrapper<FinCostRunDO>().eq(FinCostRunDO::getPeriod, period).orderByDesc(FinCostRunDO::getId).last("LIMIT 20"))
                .stream().map(this::runVO).toList();
    }

    private RunVO runVO(FinCostRunDO r) {
        return new RunVO(r.getId(), r.getPeriod(), r.getRunStatus(), r.getStartedAt(), r.getFinishedAt(), support.userName(r.getOperatorId()), r.getErrorMessage(),
                r.getMaterialCount(), r.getOrderCount(), r.getTotalCost(), r.getExceptionCount());
    }

    public List<ExceptionVO> exceptions(String period) {
        List<FinCostExceptionDO> list = exceptionMapper.selectList(new LambdaQueryWrapper<FinCostExceptionDO>().eq(FinCostExceptionDO::getPeriod, period)
                .orderByAsc(FinCostExceptionDO::getExType).orderByAsc(FinCostExceptionDO::getId));
        Map<Long, MaterialDTO> ms = support.materials(list.stream().map(FinCostExceptionDO::getMaterialId).toList());
        Map<Long, String> depts = support.orgNames(list.stream().map(FinCostExceptionDO::getDeptId).toList());
        Map<Long, String> orderNos = orderMapper.selectList(new LambdaQueryWrapper<FinCostOrderDO>().eq(FinCostOrderDO::getPeriod, period)).stream()
                .collect(Collectors.toMap(FinCostOrderDO::getProdOrderId, x -> Objects.toString(x.getProdOrderNo(), ""), (a, b) -> a));
        return list.stream().map(e -> {
            MaterialDTO m = ms.get(e.getMaterialId());
            return new ExceptionVO(e.getExType(), e.getMaterialId(), m == null ? null : m.code(), m == null ? null : m.name(), e.getProdOrderId(),
                    orderNos.get(e.getProdOrderId()), depts.get(e.getDeptId()), e.getMessage());
        }).toList();
    }

    public List<MaterialCostRow> materials(String period, String keyword) {
        List<FinCostMaterialDO> list = materialMapper.selectList(new LambdaQueryWrapper<FinCostMaterialDO>().eq(FinCostMaterialDO::getPeriod, period));
        Map<Long, MaterialDTO> ms = support.materials(list.stream().map(FinCostMaterialDO::getMaterialId).toList());
        return list.stream().map(r -> {
                    MaterialDTO m = ms.get(r.getMaterialId());
                    return new MaterialCostRow(r.getMaterialId(), m == null ? null : m.code(), m == null ? null : m.name(), m == null ? null : m.spec(),
                            m == null ? null : m.baseUom(), r.getOpeningQty(), r.getOpeningAmount(), r.getInQty(), r.getInAmount(), r.getUnitCost(),
                            r.getPrevUnitCost(), r.getOutQty(), r.getOutAmount(), r.getClosingQty(), r.getClosingAmount());
                })
                .filter(r -> !StringUtils.hasText(keyword) || Objects.toString(r.materialCode(), "").contains(keyword) || Objects.toString(r.materialName(), "").contains(keyword))
                .sorted(Comparator.comparing(r -> Objects.toString(r.materialCode(), ""))).toList();
    }

    /** 产品成本表：按产品汇总本期完工订单 */
    public List<ProductCostRow> products(String period, Long materialId) {
        List<FinCostOrderDO> list = orderMapper.selectList(new LambdaQueryWrapper<FinCostOrderDO>().eq(FinCostOrderDO::getPeriod, period)
                .eq(materialId != null, FinCostOrderDO::getMaterialId, materialId).ne(FinCostOrderDO::getFinishedQty, BigDecimal.ZERO));
        Map<Long, List<FinCostOrderDO>> byProduct = list.stream().collect(Collectors.groupingBy(FinCostOrderDO::getMaterialId, LinkedHashMap::new, Collectors.toList()));
        Map<Long, MaterialDTO> ms = support.materials(byProduct.keySet());
        List<ProductCostRow> rows = new ArrayList<>();
        byProduct.forEach((m, os) -> {
            BigDecimal qty = os.stream().map(FinCostOrderDO::getFinishedQty).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal cost = os.stream().map(FinCostOrderDO::getFinishedCost).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal mat = os.stream().map(FinCostOrderDO::getFinishedMaterial).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal lab = os.stream().map(FinCostOrderDO::getFinishedLabor).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal ovh = os.stream().map(FinCostOrderDO::getFinishedOverhead).reduce(BigDecimal.ZERO, BigDecimal::add);
            MaterialDTO md = ms.get(m);
            rows.add(new ProductCostRow(m, md == null ? null : md.code(), md == null ? null : md.name(), qty,
                    qty.signum() == 0 ? BigDecimal.ZERO : cost.divide(qty, 6, RoundingMode.HALF_UP), mat, lab, ovh, cost, null, null, null, os.size()));
        });
        return rows;
    }

    public OrderCostVO order(Long prodOrderId, String period) {
        FinCostOrderDO o = orderMapper.selectList(new LambdaQueryWrapper<FinCostOrderDO>().eq(FinCostOrderDO::getProdOrderId, prodOrderId)
                .eq(StringUtils.hasText(period), FinCostOrderDO::getPeriod, period).orderByDesc(FinCostOrderDO::getPeriod).last("LIMIT 1")).stream().findFirst()
                .orElseThrow(() -> BizException.of(FinanceErrorCodes.NOT_EXISTS, "订单成本"));
        List<FinCostOrderMaterialDO> lines = orderMaterialMapper.selectByParent(o.getId());
        Map<Long, MaterialDTO> ms = support.materials(java.util.stream.Stream.concat(lines.stream().map(FinCostOrderMaterialDO::getMaterialId),
                java.util.stream.Stream.of(o.getMaterialId())).toList());
        MaterialDTO p = ms.get(o.getMaterialId());
        List<OrderMaterialVO> mats = lines.stream().map(l -> {
            MaterialDTO m = ms.get(l.getMaterialId());
            return new OrderMaterialVO(l.getMaterialId(), m == null ? null : m.code(), m == null ? null : m.name(), l.getIssueQty(), l.getReturnQty(), l.getUnitCost(), l.getAmount());
        }).toList();
        return new OrderCostVO(o.getPeriod(), o.getProdOrderId(), o.getProdOrderNo(), o.getMaterialId(), p == null ? null : p.code(), p == null ? null : p.name(),
                support.orgNames(java.util.Collections.singletonList(o.getDeptId())).get(o.getDeptId()), o.getWorkHours(), o.getOpeningWip(), o.getMaterialCost(),
                o.getLaborCost(), o.getOverheadCost(), o.getTotalCost(), o.getFinishedQty(), o.getFinishedCost(), o.getEndingWip(), o.getUnitCost(), mats);
    }

    /** 订单成本列表（下钻） */
    public List<OrderCostVO> orders(String period, Long materialId) {
        return orderMapper.selectList(new LambdaQueryWrapper<FinCostOrderDO>().eq(FinCostOrderDO::getPeriod, period)
                        .eq(materialId != null, FinCostOrderDO::getMaterialId, materialId).orderByAsc(FinCostOrderDO::getProdOrderNo))
                .stream().map(o -> order(o.getProdOrderId(), period)).toList();
    }

    /** 物料期间单价（成本查询、毛利报表） */
    public Map<Long, BigDecimal> unitCosts(String period, java.util.Collection<Long> materialIds) {
        if (lastSuccess(period) == null || materialIds.isEmpty()) return Map.of();
        return materialMapper.selectList(new LambdaQueryWrapper<FinCostMaterialDO>().eq(FinCostMaterialDO::getPeriod, period)
                        .in(FinCostMaterialDO::getMaterialId, materialIds))
                .stream().collect(Collectors.toMap(FinCostMaterialDO::getMaterialId, FinCostMaterialDO::getUnitCost, (a, b) -> a));
    }

    public boolean calculated(String period) {
        return lastSuccess(period) != null;
    }

    public Map<Long, UserDTO> users(List<Long> ids) {
        return support.users(ids);
    }

    static LocalDate end(String period) {
        return YearMonth.parse(period, FinSupport.PERIOD).atEndOfMonth();
    }
}
