package com.erp.module.purchase.service.score;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.common.enums.DocStatus;
import com.erp.common.exception.BizException;
import com.erp.common.result.PageResult;
import com.erp.common.statemachine.StateMachine;
import com.erp.module.engineering.api.material.MaterialDTO;
import com.erp.module.purchase.api.PurchaseErrorCodes;
import com.erp.module.purchase.api.supplier.SupplierStatus;
import com.erp.module.purchase.config.PurchaseModuleConfig;
import com.erp.module.purchase.controller.vo.ScoreVOs.CalculateResult;
import com.erp.module.purchase.controller.vo.ScoreVOs.DelayedLine;
import com.erp.module.purchase.controller.vo.ScoreVOs.Lot;
import com.erp.module.purchase.controller.vo.ScoreVOs.ScoreDetail;
import com.erp.module.purchase.controller.vo.ScoreVOs.ScoreQuery;
import com.erp.module.purchase.controller.vo.ScoreVOs.ScoreRow;
import com.erp.module.purchase.controller.vo.ScoreVOs.ScoreSave;
import com.erp.module.purchase.controller.vo.ScoreVOs.TrendPoint;
import com.erp.module.purchase.dal.dataobject.OrderDO;
import com.erp.module.purchase.dal.dataobject.OrderLineDO;
import com.erp.module.purchase.dal.dataobject.ReceiptDO;
import com.erp.module.purchase.dal.dataobject.ReceiptLineDO;
import com.erp.module.purchase.dal.dataobject.SupplierDO;
import com.erp.module.purchase.dal.dataobject.SupplierScoreDO;
import com.erp.module.purchase.dal.mapper.OrderLineMapper;
import com.erp.module.purchase.dal.mapper.OrderMapper;
import com.erp.module.purchase.dal.mapper.ReceiptLineMapper;
import com.erp.module.purchase.dal.mapper.ReceiptMapper;
import com.erp.module.purchase.dal.mapper.SupplierScoreMapper;
import com.erp.module.purchase.service.PurSupport;
import com.erp.module.purchase.service.order.OrderService;
import com.erp.module.purchase.service.supplier.SupplierService;
import com.erp.module.system.api.notify.AlertRaisedEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Collections;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.erp.module.purchase.service.score.ScoreStatus.CALCULATED;
import static com.erp.module.purchase.service.score.ScoreStatus.PUBLISHED;
import static com.erp.module.purchase.service.score.ScoreStatus.SCORED;

/**
 * 供应商评估（需求 07-10）：质量（批次合格率，特采算不合格）与交期（准时率）由系统计算，价格与服务手工评分，
 * 按权重得出总分与等级（≥90 A、≥80 B、≥70 C、<70 D）；发布后更新供应商等级。
 */
@Service
public class ScoreService {

    public static final String BIZ_TYPE = PurchaseModuleConfig.SCORE;
    static final Pattern MONTH = Pattern.compile("(\\d{4})-(\\d{2})");
    static final Pattern QUARTER = Pattern.compile("(\\d{4})-Q([1-4])");
    static final BigDecimal ONTIME_RATIO = new BigDecimal("0.95");
    static final List<String> JUDGED = List.of("QUALIFIED", "CONCESSION", "REJECTED", "PARTIAL");

    enum Action implements StateMachine.Labeled {
        SCORE("评分"), UNSCORE("清除评分"), PUBLISH("发布"), UNPUBLISH("撤销发布");

        private final String label;

        Action(String label) {
            this.label = label;
        }

        @Override
        public String label() {
            return label;
        }
    }

    static final StateMachine<ScoreStatus, Action> MACHINE = StateMachine.builder(ScoreStatus.class, Action.class)
            .transition(CALCULATED, Action.SCORE, SCORED)
            .transition(SCORED, Action.UNSCORE, CALCULATED)
            .transition(SCORED, Action.PUBLISH, PUBLISHED)
            .transition(PUBLISHED, Action.UNPUBLISH, SCORED)
            .build();

    private final SupplierScoreMapper mapper;
    private final ReceiptMapper receiptMapper;
    private final ReceiptLineMapper receiptLineMapper;
    private final OrderMapper orderMapper;
    private final OrderLineMapper orderLineMapper;
    private final SupplierService supplierService;
    private final PurSupport support;

    public ScoreService(SupplierScoreMapper mapper, ReceiptMapper receiptMapper, ReceiptLineMapper receiptLineMapper, OrderMapper orderMapper,
                        OrderLineMapper orderLineMapper, SupplierService supplierService, PurSupport support) {
        this.mapper = mapper;
        this.receiptMapper = receiptMapper;
        this.receiptLineMapper = receiptLineMapper;
        this.orderMapper = orderMapper;
        this.orderLineMapper = orderLineMapper;
        this.supplierService = supplierService;
        this.support = support;
    }

    // ==================== 查询 ====================

    public PageResult<ScoreRow> page(ScoreQuery q) {
        LambdaQueryWrapper<SupplierScoreDO> w = new LambdaQueryWrapper<SupplierScoreDO>()
                .eq(StringUtils.hasText(q.getPeriod()), SupplierScoreDO::getPeriod, q.getPeriod())
                .eq(q.getSupplierId() != null, SupplierScoreDO::getSupplierId, q.getSupplierId())
                .eq(StringUtils.hasText(q.getGrade()), SupplierScoreDO::getGrade, q.getGrade());
        if (StringUtils.hasText(q.getStatuses())) {
            w.in(SupplierScoreDO::getScoreStatus, Arrays.stream(q.getStatuses().split(",")).map(String::trim).map(ScoreStatus::valueOf).toList());
        }
        PageResult<SupplierScoreDO> page = mapper.selectPage(q, w.orderByDesc(SupplierScoreDO::getPeriod).orderByAsc(SupplierScoreDO::getSupplierId));
        Map<Long, SupplierDO> ss = supplierService.byIds(page.list().stream().map(SupplierScoreDO::getSupplierId).toList());
        return new PageResult<>(page.list().stream().map(s -> row(s, ss.get(s.getSupplierId()))).toList(), page.total());
    }

    private static ScoreRow row(SupplierScoreDO s, SupplierDO sup) {
        return new ScoreRow(s.getId(), s.getSupplierId(), sup == null ? null : sup.getCode(), sup == null ? null : sup.getShortName(), s.getPeriod(),
                s.getLotCount(), s.getLotPassCount(), s.getQualityScore(), s.getDueLineCount(), s.getOntimeLineCount(), s.getDeliveryScore(),
                s.getPriceScore(), s.getServiceScore(), s.getTotalScore(), s.getGrade(), s.getScoreStatus().name(), s.getScoreComment(), s.getPublishedAt(),
                s.getVersion());
    }

    /** 选中供应商近 12 期总分 */
    public List<TrendPoint> trend(Long supplierId) {
        List<SupplierScoreDO> list = mapper.selectList(new LambdaQueryWrapper<SupplierScoreDO>().eq(SupplierScoreDO::getSupplierId, supplierId)
                .orderByDesc(SupplierScoreDO::getPeriod).last("LIMIT 12"));
        return list.stream().sorted(Comparator.comparing(SupplierScoreDO::getPeriod)).map(s -> new TrendPoint(s.getPeriod(), s.getTotalScore(), s.getGrade())).toList();
    }

    // ==================== 计算 ====================

    /** 评估期 → [开始, 结束] */
    static LocalDate[] range(String period) {
        Matcher m = MONTH.matcher(period == null ? "" : period);
        if (m.matches()) {
            int month = Integer.parseInt(m.group(2));
            if (month < 1 || month > 12) throw new BizException(PurchaseErrorCodes.SCORE_PERIOD);
            YearMonth ym = YearMonth.of(Integer.parseInt(m.group(1)), month);
            return new LocalDate[]{ym.atDay(1), ym.atEndOfMonth()};
        }
        m = QUARTER.matcher(period == null ? "" : period);
        if (m.matches()) {
            int q = Integer.parseInt(m.group(2));
            YearMonth start = YearMonth.of(Integer.parseInt(m.group(1)), (q - 1) * 3 + 1);
            return new LocalDate[]{start.atDay(1), start.plusMonths(2).atEndOfMonth()};
        }
        throw new BizException(PurchaseErrorCodes.SCORE_PERIOD);
    }

    /** R01：权重取参数（质量、交期、价格、服务），合计必须为 100 */
    int[] weights() {
        String v = support.params().getString(PurchaseModuleConfig.P_SCORE_WEIGHTS);
        try {
            int[] w = Arrays.stream(v.split(",")).map(String::trim).mapToInt(Integer::parseInt).toArray();
            if (w.length != 4 || Arrays.stream(w).sum() != 100 || Arrays.stream(w).anyMatch(x -> x < 0)) throw new BizException(PurchaseErrorCodes.SCORE_WEIGHTS);
            return w;
        } catch (NumberFormatException e) {
            throw new BizException(PurchaseErrorCodes.SCORE_WEIGHTS);
        }
    }

    /** 为期内有业务的合格/暂停供应商计算质量和交期得分；已发布的不重算 */
    @Transactional(rollbackFor = Exception.class)
    public CalculateResult calculate(String period) {
        LocalDate[] r = range(period);
        int[] w = weights();
        int tolerance = support.params().getInt(PurchaseModuleConfig.P_ONTIME_TOLERANCE);
        Map<Long, List<ReceiptLineDO>> lots = lots(null, r[0], r[1]);
        Map<Long, List<OrderLineDO>> due = dueLines(null, r[0], r[1]);
        Set<Long> suppliers = new LinkedHashSet<>(lots.keySet());
        suppliers.addAll(due.keySet());
        Map<Long, SupplierDO> ss = supplierService.byIds(suppliers);
        List<Long> ids = new ArrayList<>();
        for (Long sid : suppliers) {
            SupplierDO s = ss.get(sid);
            if (s == null || (s.getSupplierStatus() != SupplierStatus.QUALIFIED && s.getSupplierStatus() != SupplierStatus.SUSPENDED)) continue;
            SupplierScoreDO d = mapper.selectOne(new LambdaQueryWrapper<SupplierScoreDO>().eq(SupplierScoreDO::getSupplierId, sid)
                    .eq(SupplierScoreDO::getPeriod, period));
            if (d != null && d.getScoreStatus() == PUBLISHED) continue;
            boolean creating = d == null;
            if (creating) {
                d = new SupplierScoreDO();
                d.setSupplierId(sid);
                d.setPeriod(period);
                d.setScoreStatus(CALCULATED);
            }
            List<ReceiptLineDO> ls = lots.getOrDefault(sid, List.of());
            d.setLotCount(ls.size());
            d.setLotPassCount((int) ls.stream().filter(l -> "QUALIFIED".equals(l.getInspectStatus())).count());
            d.setQualityScore(ratio(d.getLotPassCount(), d.getLotCount()));
            List<OrderLineDO> dl = due.getOrDefault(sid, List.of());
            d.setDueLineCount(dl.size());
            d.setOntimeLineCount((int) dl.stream().filter(l -> ontime(l, tolerance)).count());
            d.setDeliveryScore(ratio(d.getOntimeLineCount(), d.getDueLineCount()));
            total(d, w);
            if (creating) mapper.insert(d);
            else mapper.updateByIdOrFail(d);
            ids.add(d.getId());
        }
        return new CalculateResult(ids.size(), ids);
    }

    /** 评估期内 IQC 已判定的到货批次（需检物料），按供应商 */
    private Map<Long, List<ReceiptLineDO>> lots(Long supplierId, LocalDate from, LocalDate to) {
        List<ReceiptDO> receipts = receiptMapper.selectList(new LambdaQueryWrapper<ReceiptDO>().eq(supplierId != null, ReceiptDO::getSupplierId, supplierId)
                .in(ReceiptDO::getStatus, DocStatus.APPROVED, DocStatus.COMPLETED));
        if (receipts.isEmpty()) return Collections.emptyMap();
        Map<Long, Long> supplierOf = receipts.stream().collect(Collectors.toMap(ReceiptDO::getId, ReceiptDO::getSupplierId));
        return receiptLineMapper.selectList(new LambdaQueryWrapper<ReceiptLineDO>().in(ReceiptLineDO::getReceiptId, supplierOf.keySet())
                        .eq(ReceiptLineDO::getInspectRequired, true).in(ReceiptLineDO::getInspectStatus, JUDGED)
                        .ge(ReceiptLineDO::getJudgedDate, from).le(ReceiptLineDO::getJudgedDate, to))
                .stream().collect(Collectors.groupingBy(l -> supplierOf.get(l.getReceiptId())));
    }

    /** 评估期内应到货的订单行（确认交期，没有则要求日期），按供应商；未到货就关闭的行不计 */
    private Map<Long, List<OrderLineDO>> dueLines(Long supplierId, LocalDate from, LocalDate to) {
        List<OrderDO> orders = orderMapper.selectList(new LambdaQueryWrapper<OrderDO>().eq(supplierId != null, OrderDO::getSupplierId, supplierId)
                .in(OrderDO::getStatus, DocStatus.APPROVED, DocStatus.IN_PROGRESS, DocStatus.COMPLETED, DocStatus.CLOSED));
        if (orders.isEmpty()) return Collections.emptyMap();
        Map<Long, Long> supplierOf = orders.stream().collect(Collectors.toMap(OrderDO::getId, OrderDO::getSupplierId));
        return orderLineMapper.selectList(new LambdaQueryWrapper<OrderLineDO>().in(OrderLineDO::getOrderId, supplierOf.keySet())).stream()
                .filter(l -> {
                    LocalDate due = OrderService.dueDate(l);
                    return !due.isBefore(from) && !due.isAfter(to) && !(OrderService.CLOSED.equals(l.getLineStatus()) && l.getReceivedQty().signum() == 0);
                })
                .collect(Collectors.groupingBy(l -> supplierOf.get(l.getOrderId())));
    }

    /** 准时：首次到货日期 ≤ 确认交期（无则要求日期）+ 容差天数，且到货数量 ≥ 订购数量的 95% */
    static boolean ontime(OrderLineDO l, int tolerance) {
        return l.getFirstReceivedDate() != null && !l.getFirstReceivedDate().isAfter(OrderService.dueDate(l).plusDays(tolerance))
                && l.getReceivedQty().compareTo(l.getBaseQty().multiply(ONTIME_RATIO)) >= 0;
    }

    static BigDecimal ratio(int part, int total) {
        return total == 0 ? null : BigDecimal.valueOf(part).multiply(PurSupport.HUNDRED).divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
    }

    /** 总分 = Σ 得分 × 权重 ÷ 可用权重合计（期内无批次/无应到行的维度不计分，权重按比例分给其他维度）；价格、服务都填写后才计算 */
    static void total(SupplierScoreDO d, int[] w) {
        if (d.getPriceScore() == null || d.getServiceScore() == null) {
            d.setTotalScore(null);
            d.setGrade(null);
            return;
        }
        BigDecimal sum = BigDecimal.ZERO;
        int weight = 0;
        BigDecimal[] scores = {d.getQualityScore(), d.getDeliveryScore(), d.getPriceScore(), d.getServiceScore()};
        for (int i = 0; i < 4; i++) {
            if (scores[i] == null) continue;
            sum = sum.add(scores[i].multiply(BigDecimal.valueOf(w[i])));
            weight += w[i];
        }
        BigDecimal total = weight == 0 ? BigDecimal.ZERO : sum.divide(BigDecimal.valueOf(weight), 2, RoundingMode.HALF_UP);
        d.setTotalScore(total);
        d.setGrade(grade(total));
    }

    static String grade(BigDecimal total) {
        if (total.compareTo(new BigDecimal("90")) >= 0) return "A";
        if (total.compareTo(new BigDecimal("80")) >= 0) return "B";
        if (total.compareTo(new BigDecimal("70")) >= 0) return "C";
        return "D";
    }

    // ==================== 手工评分 / 发布 ====================

    /** R02：价格、服务得分都填写后状态变为已评分 */
    @Transactional(rollbackFor = Exception.class)
    public ScoreRow update(Long id, ScoreSave req) {
        SupplierScoreDO d = getOrThrow(id);
        if (d.getScoreStatus() == PUBLISHED) throw new BizException(PurchaseErrorCodes.SCORE_PUBLISHED);
        checkRange(req.priceScore());
        checkRange(req.serviceScore());
        if (req.version() != null) d.setVersion(req.version());
        d.setPriceScore(req.priceScore() == null ? null : req.priceScore().setScale(2, RoundingMode.HALF_UP));
        d.setServiceScore(req.serviceScore() == null ? null : req.serviceScore().setScale(2, RoundingMode.HALF_UP));
        d.setScoreComment(PurSupport.trim(req.comment()));
        total(d, weights());
        boolean complete = d.getPriceScore() != null && d.getServiceScore() != null;
        if (complete && d.getScoreStatus() == CALCULATED) d.setScoreStatus(MACHINE.fire(CALCULATED, Action.SCORE));
        else if (!complete && d.getScoreStatus() == SCORED) d.setScoreStatus(MACHINE.fire(SCORED, Action.UNSCORE));
        mapper.updateByIdOrFail(d);
        return row(d, supplierService.find(d.getSupplierId()).orElse(null));
    }

    private static void checkRange(BigDecimal v) {
        if (v != null && (v.signum() < 0 || v.compareTo(PurSupport.HUNDRED) > 0)) throw new BizException(PurchaseErrorCodes.SCORE_RANGE);
    }

    /** R02、R03：只有已评分才能发布；发布后供应商等级更新为本期等级，D 级提醒采购主管评估是否暂停 */
    @Transactional(rollbackFor = Exception.class)
    public int publish(List<Long> ids) {
        int n = 0;
        for (Long id : ids == null ? List.<Long>of() : ids) {
            SupplierScoreDO d = getOrThrow(id);
            if (d.getScoreStatus() != SCORED) throw new BizException(PurchaseErrorCodes.SCORE_NOT_SCORED);
            d.setScoreStatus(MACHINE.fire(d.getScoreStatus(), Action.PUBLISH));
            d.setPublishedAt(LocalDateTime.now().withNano(0));
            mapper.updateByIdOrFail(d);
            SupplierDO s = supplierService.getOrThrow(d.getSupplierId());
            supplierService.updateLevel(s.getId(), d.getGrade(), "供应商评估 " + d.getPeriod() + " 发布");
            if ("D".equals(d.getGrade())) {
                support.notifyApi().alert(new AlertRaisedEvent("PUR_SCORE_D:" + s.getId() + ":" + d.getPeriod(), "PUR_SUPPLIER_SCORE_D",
                        AlertRaisedEvent.Level.WARNING, List.of(), "pur:supplier:suspend", PurchaseModuleConfig.SUPPLIER, s.getId(),
                        "供应商评估为 D 级", "供应商「" + s.getName() + "」" + d.getPeriod() + " 评估总分 " + PurSupport.plain(d.getTotalScore())
                        + "，等级 D，建议评估是否暂停", "/purchase/supplier/" + s.getId()));
            }
            n++;
        }
        return n;
    }

    /** R03：撤销发布（原因必填），发布后不能修改，需要时由采购主管撤销 */
    @Transactional(rollbackFor = Exception.class)
    public void unpublish(Long id, String reason) {
        SupplierScoreDO d = getOrThrow(id);
        String why = PurSupport.requireReason(reason, "撤销发布");
        d.setScoreStatus(MACHINE.fire(d.getScoreStatus(), Action.UNPUBLISH));
        d.setUnpublishReason(why);
        d.setPublishedAt(null);
        mapper.updateByIdOrFail(d);
    }

    /** 明细：本期不合格批次、延误订单行 */
    public ScoreDetail details(Long id) {
        SupplierScoreDO d = getOrThrow(id);
        LocalDate[] r = range(d.getPeriod());
        int tolerance = support.params().getInt(PurchaseModuleConfig.P_ONTIME_TOLERANCE);
        List<ReceiptLineDO> bad = lots(d.getSupplierId(), r[0], r[1]).getOrDefault(d.getSupplierId(), List.of()).stream()
                .filter(l -> !"QUALIFIED".equals(l.getInspectStatus())).toList();
        List<OrderLineDO> late = dueLines(d.getSupplierId(), r[0], r[1]).getOrDefault(d.getSupplierId(), List.of()).stream()
                .filter(l -> !ontime(l, tolerance)).toList();
        List<Long> materialIds = new ArrayList<>(bad.stream().map(ReceiptLineDO::getMaterialId).toList());
        materialIds.addAll(late.stream().map(OrderLineDO::getMaterialId).toList());
        Map<Long, MaterialDTO> ms = support.materials(materialIds);
        Map<Long, ReceiptDO> receipts = bad.isEmpty() ? Map.of() : receiptMapper.selectBatchIds(bad.stream().map(ReceiptLineDO::getReceiptId)
                .collect(Collectors.toSet())).stream().collect(Collectors.toMap(ReceiptDO::getId, x -> x));
        Map<Long, OrderDO> orders = late.isEmpty() ? new HashMap<>() : orderMapper.selectBatchIds(late.stream().map(OrderLineDO::getOrderId)
                .collect(Collectors.toSet())).stream().collect(Collectors.toMap(OrderDO::getId, x -> x));
        return new ScoreDetail(d.getId(), d.getPeriod(), bad.stream().map(l -> {
            MaterialDTO m = ms.get(l.getMaterialId());
            ReceiptDO rc = receipts.get(l.getReceiptId());
            return new Lot(l.getReceiptId(), rc == null ? null : rc.getDocNo(), m == null ? null : m.code(), m == null ? null : m.name(), l.getBaseQty(),
                    l.getInspectStatus(), l.getInspectionNo(), l.getJudgedDate());
        }).toList(), late.stream().map(l -> {
            MaterialDTO m = ms.get(l.getMaterialId());
            OrderDO o = orders.get(l.getOrderId());
            return new DelayedLine(l.getOrderId(), o == null ? null : o.getDocNo(), l.getLineNo(), m == null ? null : m.code(), m == null ? null : m.name(),
                    l.getBaseQty(), OrderService.dueDate(l), l.getFirstReceivedDate(), l.getReceivedQty());
        }).toList());
    }

    /** R04：每月 3 日自动计算上月（参数可关闭） */
    public String monthlyJob() {
        if (!support.params().getBool(PurchaseModuleConfig.P_SCORE_AUTO)) return "参数已关闭";
        String period = YearMonth.now().minusMonths(1).toString();
        return "计算 " + period + "：" + calculate(period).count() + " 家供应商";
    }

    public SupplierScoreDO getOrThrow(Long id) {
        SupplierScoreDO d = id == null ? null : mapper.selectById(id);
        if (d == null) throw new BizException(PurchaseErrorCodes.SCORE_NOT_EXISTS);
        return d;
    }
}
