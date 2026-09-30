package com.erp.module.finance.service.report;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.common.util.Decimals;
import com.erp.module.engineering.api.material.MaterialDTO;
import com.erp.module.finance.controller.vo.AnalysisVOs.AccountBalance;
import com.erp.module.finance.controller.vo.AnalysisVOs.AccountBalanceRow;
import com.erp.module.finance.controller.vo.AnalysisVOs.CashDaily;
import com.erp.module.finance.controller.vo.AnalysisVOs.CashDailyRow;
import com.erp.module.finance.controller.vo.AnalysisVOs.Ledger;
import com.erp.module.finance.controller.vo.AnalysisVOs.LedgerLine;
import com.erp.module.finance.controller.vo.AnalysisVOs.MarginReport;
import com.erp.module.finance.controller.vo.AnalysisVOs.MarginRow;
import com.erp.module.finance.controller.vo.AnalysisVOs.PlItem;
import com.erp.module.finance.controller.vo.AnalysisVOs.ProfitLoss;
import com.erp.module.finance.dal.dataobject.FinAccountDO;
import com.erp.module.finance.dal.dataobject.FinBankAccountDO;
import com.erp.module.finance.dal.dataobject.FinCostMaterialDO;
import com.erp.module.finance.dal.dataobject.FinPaymentDO;
import com.erp.module.finance.dal.dataobject.FinReceiptDO;
import com.erp.module.finance.dal.dataobject.FinReceivableDO;
import com.erp.module.finance.dal.dataobject.FinReceivableLineDO;
import com.erp.module.finance.dal.dataobject.FinVoucherDO;
import com.erp.module.finance.dal.dataobject.FinVoucherLineDO;
import com.erp.module.finance.dal.mapper.FinAccountMapper;
import com.erp.module.finance.dal.mapper.FinBankAccountMapper;
import com.erp.module.finance.dal.mapper.FinCostMaterialMapper;
import com.erp.module.finance.dal.mapper.FinPaymentMapper;
import com.erp.module.finance.dal.mapper.FinReceiptMapper;
import com.erp.module.finance.dal.mapper.FinReceivableLineMapper;
import com.erp.module.finance.dal.mapper.FinReceivableMapper;
import com.erp.module.finance.dal.mapper.FinVoucherLineMapper;
import com.erp.module.finance.dal.mapper.FinVoucherMapper;
import com.erp.module.finance.service.ArStatus;
import com.erp.module.finance.service.CashStatus;
import com.erp.module.finance.service.FinSupport;
import com.erp.module.finance.service.VoucherStatus;
import com.erp.module.finance.service.ar.ReceivableService;
import com.erp.module.finance.service.cost.CostService;
import com.erp.module.system.api.user.UserDTO;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 财务分析报表（需求 12-08 P1）。
 * <ul>
 *   <li>收付款日报：已确认收款（到账金额）、付款（付款金额 + 手续费），按日期、银行账户，原币</li>
 *   <li>毛利：收入 = 已确认出货应收 / 退货红字应收明细的不含税本位币金额；成本 = 数量 × 该应收业务期间的物料加权单价（成本计算成功后），否则“未计算”</li>
 *   <li>月度损益：收入取应收，销售成本取成本计算结果的销售出库金额，费用取已过账凭证 6601 / 6602 / 6603 科目发生额</li>
 *   <li>科目余额表、明细账：取已过账凭证（可选包含未过账），上级科目按编码前缀汇总</li>
 * </ul>
 */
@Service
public class FinAnalysisService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final FinReceivableMapper receivableMapper;
    private final FinReceivableLineMapper receivableLineMapper;
    private final FinReceiptMapper receiptMapper;
    private final FinPaymentMapper paymentMapper;
    private final FinBankAccountMapper bankMapper;
    private final FinCostMaterialMapper costMaterialMapper;
    private final FinVoucherMapper voucherMapper;
    private final FinVoucherLineMapper voucherLineMapper;
    private final FinAccountMapper accountMapper;
    private final CostService costService;
    private final FinSupport support;

    public FinAnalysisService(FinReceivableMapper receivableMapper, FinReceivableLineMapper receivableLineMapper, FinReceiptMapper receiptMapper,
                              FinPaymentMapper paymentMapper, FinBankAccountMapper bankMapper, FinCostMaterialMapper costMaterialMapper,
                              FinVoucherMapper voucherMapper, FinVoucherLineMapper voucherLineMapper, FinAccountMapper accountMapper, CostService costService,
                              FinSupport support) {
        this.receivableMapper = receivableMapper;
        this.receivableLineMapper = receivableLineMapper;
        this.receiptMapper = receiptMapper;
        this.paymentMapper = paymentMapper;
        this.bankMapper = bankMapper;
        this.costMaterialMapper = costMaterialMapper;
        this.voucherMapper = voucherMapper;
        this.voucherLineMapper = voucherLineMapper;
        this.accountMapper = accountMapper;
        this.costService = costService;
        this.support = support;
    }

    // ==================== 收付款日报 ====================

    public CashDaily cashDaily(LocalDate dateFrom, LocalDate dateTo, Long bankAccountId) {
        LocalDate to = dateTo == null ? LocalDate.now() : dateTo;
        LocalDate from = dateFrom == null ? to.withDayOfMonth(1) : dateFrom;
        List<FinBankAccountDO> banks = bankMapper.selectList(new LambdaQueryWrapper<FinBankAccountDO>().eq(bankAccountId != null, FinBankAccountDO::getId, bankAccountId)
                .orderByAsc(FinBankAccountDO::getCode));
        List<FinReceiptDO> receipts = receiptMapper.selectList(new LambdaQueryWrapper<FinReceiptDO>().eq(FinReceiptDO::getReceiptStatus, CashStatus.CONFIRMED.name())
                .le(FinReceiptDO::getReceiptDate, to).eq(bankAccountId != null, FinReceiptDO::getBankAccountId, bankAccountId));
        List<FinPaymentDO> payments = paymentMapper.selectList(new LambdaQueryWrapper<FinPaymentDO>().eq(FinPaymentDO::getPaymentStatus, CashStatus.CONFIRMED.name())
                .le(FinPaymentDO::getPayDate, to).eq(bankAccountId != null, FinPaymentDO::getBankAccountId, bankAccountId));
        List<CashDailyRow> rows = new ArrayList<>();
        for (FinBankAccountDO b : banks) {
            BigDecimal balance = BigDecimal.ZERO;
            TreeMap<LocalDate, BigDecimal[]> days = new TreeMap<>();
            for (FinReceiptDO r : receipts) {
                if (!b.getId().equals(r.getBankAccountId())) continue;
                BigDecimal v = FinSupport.nz(r.getAmount());
                if (r.getReceiptDate().isBefore(from)) balance = balance.add(v);
                else add(days, r.getReceiptDate(), v, BigDecimal.ZERO, true);
            }
            for (FinPaymentDO p : payments) {
                if (!b.getId().equals(p.getBankAccountId())) continue;
                BigDecimal v = FinSupport.nz(p.getAmount()).add(FinSupport.nz(p.getBankFee()));
                if (p.getPayDate().isBefore(from)) balance = balance.subtract(v);
                else add(days, p.getPayDate(), BigDecimal.ZERO, v, false);
            }
            for (Map.Entry<LocalDate, BigDecimal[]> e : days.entrySet()) {
                BigDecimal[] s = e.getValue();
                BigDecimal closing = balance.add(s[0]).subtract(s[1]);
                rows.add(new CashDailyRow(e.getKey(), b.getId(), b.getCode(), b.getName(), b.getCurrency(), Decimals.amount(balance), Decimals.amount(s[0]),
                        Decimals.amount(s[1]), Decimals.amount(closing), s[2].intValue(), s[3].intValue()));
                balance = closing;
            }
        }
        rows.sort(Comparator.comparing(CashDailyRow::date).thenComparing(r -> Objects.toString(r.bankCode(), "")));
        return new CashDaily(from, to, rows);
    }

    private static void add(TreeMap<LocalDate, BigDecimal[]> days, LocalDate d, BigDecimal in, BigDecimal out, boolean receipt) {
        BigDecimal[] s = days.computeIfAbsent(d, k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO});
        s[0] = s[0].add(in);
        s[1] = s[1].add(out);
        if (receipt) s[2] = s[2].add(BigDecimal.ONE);
        else s[3] = s[3].add(BigDecimal.ONE);
    }

    // ==================== 毛利 ====================

    private record MarginLine(FinReceivableDO ar, FinReceivableLineDO line, BigDecimal revenue, BigDecimal cost) {
    }

    /** group：LINE / ORDER / CUSTOMER / SALESMAN / PRODUCT */
    public MarginReport margin(LocalDate dateFrom, LocalDate dateTo, String group, Long customerId, Long materialId) {
        LocalDate to = dateTo == null ? LocalDate.now() : dateTo;
        LocalDate from = dateFrom == null ? to.withDayOfMonth(1) : dateFrom;
        String g = StringUtils.hasText(group) ? group : "LINE";
        List<FinReceivableDO> ars = receivableMapper.selectList(new LambdaQueryWrapper<FinReceivableDO>().eq(FinReceivableDO::getArStatus, ArStatus.CONFIRMED.name())
                .in(FinReceivableDO::getArType, ReceivableService.SALES, ReceivableService.SALES_RETURN).between(FinReceivableDO::getBizDate, from, to)
                .eq(customerId != null, FinReceivableDO::getCustomerId, customerId));
        Map<Long, FinReceivableDO> arById = ars.stream().collect(Collectors.toMap(FinReceivableDO::getId, Function.identity()));
        List<FinReceivableLineDO> lines = arById.isEmpty() ? List.of() : receivableLineMapper.selectList(new LambdaQueryWrapper<FinReceivableLineDO>()
                .in(FinReceivableLineDO::getReceivableId, arById.keySet()).isNotNull(FinReceivableLineDO::getMaterialId)
                .eq(materialId != null, FinReceivableLineDO::getMaterialId, materialId));
        // 期间单价
        Map<String, Map<Long, BigDecimal>> unitByPeriod = new HashMap<>();
        Set<String> uncalculated = new TreeSet<>();
        Map<String, Set<Long>> matsByPeriod = new HashMap<>();
        for (FinReceivableLineDO l : lines) {
            matsByPeriod.computeIfAbsent(FinSupport.periodOf(arById.get(l.getReceivableId()).getBizDate()), k -> new java.util.HashSet<>()).add(l.getMaterialId());
        }
        matsByPeriod.forEach((p, ids) -> {
            if (costService.calculated(p)) unitByPeriod.put(p, costService.unitCosts(p, ids));
            else uncalculated.add(p);
        });
        List<MarginLine> mls = new ArrayList<>();
        for (FinReceivableLineDO l : lines) {
            FinReceivableDO ar = arById.get(l.getReceivableId());
            BigDecimal revenue = FinSupport.toBase(l.getAmount(), ar.getExchangeRate());
            Map<Long, BigDecimal> units = unitByPeriod.get(FinSupport.periodOf(ar.getBizDate()));
            BigDecimal unit = units == null ? null : units.get(l.getMaterialId());
            BigDecimal cost = units == null ? null : Decimals.amount(FinSupport.nz(l.getQty()).multiply(FinSupport.nz(unit)));
            mls.add(new MarginLine(ar, l, revenue, cost));
        }
        Function<MarginLine, String> key = switch (g) {
            case "ORDER" -> m -> String.valueOf(m.line.getOrderId());
            case "CUSTOMER" -> m -> String.valueOf(m.ar.getCustomerId());
            case "SALESMAN" -> m -> String.valueOf(m.ar.getOwnerId());
            case "PRODUCT" -> m -> String.valueOf(m.line.getMaterialId());
            default -> m -> m.ar.getId() + "-" + m.line.getId();
        };
        Map<String, List<MarginLine>> grouped = mls.stream().collect(Collectors.groupingBy(key, LinkedHashMap::new, Collectors.toList()));
        Map<Long, String> customers = new HashMap<>();
        support.customers(ars.stream().map(FinReceivableDO::getCustomerId).toList()).forEach((k, v) -> customers.put(k, FinSupport.customerName(v)));
        Map<Long, UserDTO> users = support.users(ars.stream().map(FinReceivableDO::getOwnerId).toList());
        Map<Long, MaterialDTO> mats = support.materials(lines.stream().map(FinReceivableLineDO::getMaterialId).toList());
        boolean single = "LINE".equals(g);
        List<MarginRow> rows = new ArrayList<>();
        grouped.forEach((k, list) -> {
            MarginLine f = list.get(0);
            BigDecimal revenue = Decimals.amount(list.stream().map(MarginLine::revenue).reduce(BigDecimal.ZERO, BigDecimal::add));
            boolean missing = list.stream().anyMatch(m -> m.cost == null);
            BigDecimal cost = missing ? null : Decimals.amount(list.stream().map(MarginLine::cost).reduce(BigDecimal.ZERO, BigDecimal::add));
            BigDecimal qty = list.stream().map(m -> FinSupport.nz(m.line.getQty())).reduce(BigDecimal.ZERO, BigDecimal::add);
            boolean byOrder = single || "ORDER".equals(g);
            boolean byMaterial = single || "PRODUCT".equals(g);
            boolean byCustomer = single || "ORDER".equals(g) || "CUSTOMER".equals(g);
            MaterialDTO md = byMaterial ? mats.get(f.line.getMaterialId()) : null;
            rows.add(new MarginRow(k, byOrder ? f.line.getOrderId() : null, byOrder ? f.line.getOrderNo() : null, byCustomer ? f.ar.getCustomerId() : null,
                    byCustomer ? customers.get(f.ar.getCustomerId()) : null, single || "SALESMAN".equals(g) ? f.ar.getOwnerId() : null,
                    single || "SALESMAN".equals(g) ? FinSupport.name(users, f.ar.getOwnerId()) : null, byMaterial ? f.line.getMaterialId() : null,
                    md == null ? null : md.code(), md == null ? null : md.name(), byMaterial ? qty : null, revenue, cost,
                    cost == null ? null : revenue.subtract(cost), cost == null ? null : rate(revenue.subtract(cost), revenue), 0));
        });
        if (!single) rows.sort(Comparator.comparing((MarginRow r) -> r.margin() == null ? r.revenue() : r.margin()).reversed());
        List<MarginRow> ranked = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            MarginRow r = rows.get(i);
            ranked.add(new MarginRow(r.key(), r.orderId(), r.orderNo(), r.customerId(), r.customerName(), r.salesmanId(), r.salesmanName(), r.materialId(),
                    r.materialCode(), r.materialName(), r.qty(), r.revenue(), r.cost(), r.margin(), r.marginRate(), i + 1));
        }
        BigDecimal totalRevenue = Decimals.amount(mls.stream().map(MarginLine::revenue).reduce(BigDecimal.ZERO, BigDecimal::add));
        boolean anyMissing = mls.stream().anyMatch(m -> m.cost == null);
        BigDecimal totalCost = anyMissing ? null : Decimals.amount(mls.stream().map(MarginLine::cost).reduce(BigDecimal.ZERO, BigDecimal::add));
        BigDecimal totalMargin = totalCost == null ? null : totalRevenue.subtract(totalCost);
        return new MarginReport(from, to, g, ranked, totalRevenue, totalCost, totalMargin, totalMargin == null ? null : rate(totalMargin, totalRevenue),
                new ArrayList<>(uncalculated));
    }

    private static BigDecimal rate(BigDecimal part, BigDecimal whole) {
        return whole.signum() == 0 ? null : part.multiply(HUNDRED).divide(whole, 2, RoundingMode.HALF_UP);
    }

    // ==================== 月度损益 ====================

    public ProfitLoss profitLoss(String period) {
        String p = StringUtils.hasText(period) ? period : FinSupport.periodOf(LocalDate.now());
        YearMonth ym = YearMonth.parse(p, FinSupport.PERIOD);
        String yearStart = ym.withMonth(1).format(FinSupport.PERIOD);
        String lastYear = ym.minusYears(1).format(FinSupport.PERIOD);
        Set<String> uncalculated = new TreeSet<>();
        BigDecimal[] revenue = {revenue(p, p), revenue(yearStart, p), revenue(lastYear, lastYear)};
        BigDecimal[] cost = {salesCost(p, p, uncalculated), salesCost(yearStart, p, uncalculated), salesCost(lastYear, lastYear, uncalculated)};
        String[][] expenseAccounts = {{"6601", "销售费用"}, {"6602", "管理费用"}, {"6603", "财务费用"}};
        List<PlItem> items = new ArrayList<>();
        items.add(new PlItem("REVENUE", "营业收入", revenue[0], revenue[1], revenue[2], false));
        items.add(new PlItem("COST", "营业成本", cost[0], cost[1], cost[2], false));
        BigDecimal[] gross = new BigDecimal[3];
        for (int i = 0; i < 3; i++) gross[i] = cost[i] == null ? null : revenue[i].subtract(cost[i]);
        items.add(new PlItem("GROSS", "毛利", gross[0], gross[1], gross[2], true));
        BigDecimal[] expenses = {BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO};
        String[][] ranges = {{p, p}, {yearStart, p}, {lastYear, lastYear}};
        for (String[] a : expenseAccounts) {
            BigDecimal[] v = new BigDecimal[3];
            for (int i = 0; i < 3; i++) {
                v[i] = occurred(a[0], ranges[i][0], ranges[i][1]);
                expenses[i] = expenses[i].add(v[i]);
            }
            items.add(new PlItem("EXP_" + a[0], a[1], v[0], v[1], v[2], false));
        }
        BigDecimal[] profit = new BigDecimal[3];
        for (int i = 0; i < 3; i++) profit[i] = gross[i] == null ? null : gross[i].subtract(expenses[i]);
        items.add(new PlItem("PROFIT", "利润", profit[0], profit[1], profit[2], true));
        return new ProfitLoss(p, items, new ArrayList<>(uncalculated));
    }

    /** 收入：已确认应收（出货、退货、其他）不含税本位币 */
    private BigDecimal revenue(String fromPeriod, String toPeriod) {
        LocalDate from = YearMonth.parse(fromPeriod, FinSupport.PERIOD).atDay(1);
        LocalDate to = YearMonth.parse(toPeriod, FinSupport.PERIOD).atEndOfMonth();
        return Decimals.amount(receivableMapper.selectList(new LambdaQueryWrapper<FinReceivableDO>().eq(FinReceivableDO::getArStatus, ArStatus.CONFIRMED.name())
                        .between(FinReceivableDO::getBizDate, from, to)).stream()
                .map(r -> FinSupport.toBase(r.getAmount(), r.getExchangeRate())).reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    /** 销售成本：成本计算结果的销售出库金额；区间内有应收但未计算成本的期间返回空 */
    private BigDecimal salesCost(String fromPeriod, String toPeriod, Set<String> uncalculated) {
        BigDecimal sum = BigDecimal.ZERO;
        boolean missing = false;
        for (YearMonth ym = YearMonth.parse(fromPeriod, FinSupport.PERIOD); !ym.isAfter(YearMonth.parse(toPeriod, FinSupport.PERIOD)); ym = ym.plusMonths(1)) {
            String p = ym.format(FinSupport.PERIOD);
            if (!costService.calculated(p)) {
                if (revenue(p, p).signum() != 0) {
                    missing = true;
                    uncalculated.add(p);
                }
                continue;
            }
            sum = sum.add(costMaterialMapper.selectList(new LambdaQueryWrapper<FinCostMaterialDO>().eq(FinCostMaterialDO::getPeriod, p)).stream()
                    .map(FinCostMaterialDO::getSalesOutAmount).map(FinSupport::nz).reduce(BigDecimal.ZERO, BigDecimal::add));
        }
        return missing ? null : Decimals.amount(sum);
    }

    /** 损益类科目借方发生净额（已过账凭证） */
    private BigDecimal occurred(String accountPrefix, String fromPeriod, String toPeriod) {
        return Decimals.amount(voucherLines(fromPeriod, toPeriod, false).stream().filter(l -> l.getAccountCode().startsWith(accountPrefix))
                .map(l -> FinSupport.nz(l.getDebit()).subtract(FinSupport.nz(l.getCredit()))).reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    // ==================== 科目余额表 / 明细账 ====================

    private List<FinVoucherLineDO> voucherLines(String fromPeriod, String toPeriod, boolean includeUnposted) {
        return voucherLines(fromPeriod, toPeriod, includeUnposted, null);
    }

    private List<FinVoucherLineDO> voucherLines(String fromPeriod, String toPeriod, boolean includeUnposted, Map<Long, FinVoucherDO> vouchersOut) {
        List<FinVoucherDO> vs = voucherMapper.selectList(new LambdaQueryWrapper<FinVoucherDO>()
                .ge(fromPeriod != null, FinVoucherDO::getPeriod, fromPeriod).le(toPeriod != null, FinVoucherDO::getPeriod, toPeriod)
                .eq(!includeUnposted, FinVoucherDO::getVoucherStatus, VoucherStatus.POSTED.name()));
        if (vs.isEmpty()) return List.of();
        if (vouchersOut != null) vs.forEach(v -> vouchersOut.put(v.getId(), v));
        List<Long> ids = vs.stream().map(FinVoucherDO::getId).toList();
        List<FinVoucherLineDO> lines = new ArrayList<>();
        for (int i = 0; i < ids.size(); i += 500) {
            lines.addAll(voucherLineMapper.selectList(new LambdaQueryWrapper<FinVoucherLineDO>().in(FinVoucherLineDO::getVoucherId, ids.subList(i, Math.min(ids.size(), i + 500)))));
        }
        return lines;
    }

    public AccountBalance accountBalance(String periodFrom, String periodTo, boolean includeUnposted, Integer maxLevel) {
        String to = StringUtils.hasText(periodTo) ? periodTo : FinSupport.periodOf(LocalDate.now());
        String from = StringUtils.hasText(periodFrom) ? periodFrom : to;
        String before = YearMonth.parse(from, FinSupport.PERIOD).minusMonths(1).format(FinSupport.PERIOD);
        List<FinVoucherLineDO> opening = voucherLines(null, before, includeUnposted);
        List<FinVoucherLineDO> current = voucherLines(from, to, includeUnposted);
        List<FinAccountDO> accounts = accountMapper.selectList(new LambdaQueryWrapper<FinAccountDO>().orderByAsc(FinAccountDO::getCode));
        List<AccountBalanceRow> rows = new ArrayList<>();
        BigDecimal td = BigDecimal.ZERO;
        BigDecimal tc = BigDecimal.ZERO;
        for (FinAccountDO a : accounts) {
            int level = a.getAccountLevel() == null ? 1 : a.getAccountLevel();
            if (maxLevel != null && level > maxLevel) continue;
            BigDecimal o = net(opening, a.getCode());
            BigDecimal d = sum(current, a.getCode(), true);
            BigDecimal c = sum(current, a.getCode(), false);
            if (o.signum() == 0 && d.signum() == 0 && c.signum() == 0) continue;
            boolean credit = "CREDIT".equals(a.getDirection());
            BigDecimal openingSigned = credit ? o.negate() : o;
            BigDecimal closing = credit ? openingSigned.add(c).subtract(d) : openingSigned.add(d).subtract(c);
            rows.add(new AccountBalanceRow(a.getCode(), a.getName(), level, Boolean.TRUE.equals(a.getIsLeaf()), credit ? "CREDIT" : "DEBIT",
                    Decimals.amount(openingSigned), Decimals.amount(d), Decimals.amount(c), Decimals.amount(closing)));
            if (level == 1) {
                td = td.add(d);
                tc = tc.add(c);
            }
        }
        return new AccountBalance(from, to, includeUnposted, rows, Decimals.amount(td), Decimals.amount(tc));
    }

    private static boolean under(String code, String account) {
        return code != null && code.startsWith(account);
    }

    private static BigDecimal net(List<FinVoucherLineDO> lines, String account) {
        return lines.stream().filter(l -> under(l.getAccountCode(), account)).map(l -> FinSupport.nz(l.getDebit()).subtract(FinSupport.nz(l.getCredit())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal sum(List<FinVoucherLineDO> lines, String account, boolean debit) {
        return lines.stream().filter(l -> under(l.getAccountCode(), account)).map(l -> FinSupport.nz(debit ? l.getDebit() : l.getCredit()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public Ledger ledger(String accountCode, String periodFrom, String periodTo, boolean includeUnposted) {
        String code = FinSupport.requireText(accountCode, "科目");
        String to = StringUtils.hasText(periodTo) ? periodTo : FinSupport.periodOf(LocalDate.now());
        String from = StringUtils.hasText(periodFrom) ? periodFrom : to;
        FinAccountDO a = accountMapper.selectOne(new LambdaQueryWrapper<FinAccountDO>().eq(FinAccountDO::getCode, code));
        boolean credit = a != null && "CREDIT".equals(a.getDirection());
        String before = YearMonth.parse(from, FinSupport.PERIOD).minusMonths(1).format(FinSupport.PERIOD);
        BigDecimal o = net(voucherLines(null, before, includeUnposted), code);
        BigDecimal balance = credit ? o.negate() : o;
        BigDecimal opening = Decimals.amount(balance);
        Map<Long, FinVoucherDO> vs = new HashMap<>();
        List<FinVoucherLineDO> lines = voucherLines(from, to, includeUnposted, vs).stream().filter(l -> under(l.getAccountCode(), code))
                .sorted(Comparator.comparing((FinVoucherLineDO l) -> vs.get(l.getVoucherId()).getDocDate())
                        .thenComparing(l -> vs.get(l.getVoucherId()).getVoucherSeq()).thenComparing(FinVoucherLineDO::getLineNo)).toList();
        List<LedgerLine> out = new ArrayList<>();
        BigDecimal d = BigDecimal.ZERO;
        BigDecimal c = BigDecimal.ZERO;
        for (FinVoucherLineDO l : lines) {
            FinVoucherDO v = vs.get(l.getVoucherId());
            BigDecimal ld = FinSupport.nz(l.getDebit());
            BigDecimal lc = FinSupport.nz(l.getCredit());
            d = d.add(ld);
            c = c.add(lc);
            balance = credit ? balance.add(lc).subtract(ld) : balance.add(ld).subtract(lc);
            out.add(new LedgerLine(v.getDocDate(), v.getPeriod(), v.getId(), v.getDocNo(), l.getSummary(), l.getAccountCode(), ld, lc,
                    credit ? "CREDIT" : "DEBIT", Decimals.amount(balance)));
        }
        return new Ledger(code, a == null ? null : a.getName(), credit ? "CREDIT" : "DEBIT", from, to, opening, Decimals.amount(d), Decimals.amount(c),
                Decimals.amount(balance), out);
    }
}
