package com.erp.module.finance.service.close;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.common.exception.BizException;
import com.erp.common.util.Decimals;
import com.erp.module.finance.api.FinanceErrorCodes;
import com.erp.module.finance.api.period.FinPeriodClosedEvent;
import com.erp.module.finance.config.FinanceModuleConfig;
import com.erp.module.finance.controller.vo.CloseVOs.CheckItem;
import com.erp.module.finance.controller.vo.CloseVOs.CheckResult;
import com.erp.module.finance.controller.vo.CloseVOs.FxResult;
import com.erp.module.finance.controller.vo.CloseVOs.FxRow;
import com.erp.module.finance.controller.vo.CloseVOs.PeriodRow;
import com.erp.module.finance.controller.vo.VoucherVOs.Pending;
import com.erp.module.finance.dal.dataobject.FinBankAccountDO;
import com.erp.module.finance.dal.dataobject.FinFxRevaluationDO;
import com.erp.module.finance.dal.dataobject.FinPayableDO;
import com.erp.module.finance.dal.dataobject.FinPaymentDO;
import com.erp.module.finance.dal.dataobject.FinPeriodDO;
import com.erp.module.finance.dal.dataobject.FinPurchaseInvoiceDO;
import com.erp.module.finance.dal.dataobject.FinReceiptDO;
import com.erp.module.finance.dal.dataobject.FinReceivableDO;
import com.erp.module.finance.dal.dataobject.FinVoucherLineDO;
import com.erp.module.finance.dal.mapper.FinBankAccountMapper;
import com.erp.module.finance.dal.mapper.FinFxRevaluationMapper;
import com.erp.module.finance.dal.mapper.FinPayableMapper;
import com.erp.module.finance.dal.mapper.FinPaymentMapper;
import com.erp.module.finance.dal.mapper.FinPeriodMapper;
import com.erp.module.finance.dal.mapper.FinPurchaseInvoiceMapper;
import com.erp.module.finance.dal.mapper.FinReceiptMapper;
import com.erp.module.finance.dal.mapper.FinReceivableMapper;
import com.erp.module.finance.service.ArStatus;
import com.erp.module.finance.service.CashStatus;
import com.erp.module.finance.service.FinSupport;
import com.erp.module.finance.service.ap.PurchaseInvoiceService;
import com.erp.module.finance.service.cost.CostService;
import com.erp.module.finance.service.setting.SettingService;
import com.erp.module.finance.service.voucher.VoucherService;
import com.erp.module.inventory.api.cost.InventoryCostApi;
import com.erp.module.system.api.currency.RateType;
import com.erp.module.system.api.user.UserDTO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;

/**
 * 月结（需求 12-09）：结账检查 → 外币期末重估 → 结账 / 反结账。
 * <p>
 * 外币重估：外币应收、应付（已确认未核销部分）与外币银行存款余额按月末汇率（MONTH_END）重估，差异 = 原币余额 × 月末汇率 − 账面本位币（FIN-CLS-R04）；
 * 生成一张重估凭证草稿（应收 / 应付 / 银行科目 与 660301 汇兑损益），参数 fin.close.fx-auto-reverse 为是时，结账时在下月 1 日生成冲回凭证。
 */
@Service
public class CloseService {

    static final String OPEN = "OPEN";
    static final String CLOSED = "CLOSED";
    static final String NOT_OPEN = "NOT_OPEN";
    static final String FX_ACCOUNT = "660301";
    static final String FX_BIZ_TYPE = "FX_REVALUATION";

    private final FinPeriodMapper periodMapper;
    private final FinReceivableMapper receivableMapper;
    private final FinPayableMapper payableMapper;
    private final FinReceiptMapper receiptMapper;
    private final FinPaymentMapper paymentMapper;
    private final FinPurchaseInvoiceMapper invoiceMapper;
    private final FinBankAccountMapper bankMapper;
    private final FinFxRevaluationMapper fxMapper;
    private final InventoryCostApi inventoryCostApi;
    private final CostService costService;
    private final VoucherService voucherService;
    private final SettingService settingService;
    private final FinSupport support;

    public CloseService(FinPeriodMapper periodMapper, FinReceivableMapper receivableMapper, FinPayableMapper payableMapper, FinReceiptMapper receiptMapper,
                        FinPaymentMapper paymentMapper, FinPurchaseInvoiceMapper invoiceMapper, FinBankAccountMapper bankMapper, FinFxRevaluationMapper fxMapper,
                        InventoryCostApi inventoryCostApi, CostService costService, VoucherService voucherService, SettingService settingService, FinSupport support) {
        this.periodMapper = periodMapper;
        this.receivableMapper = receivableMapper;
        this.payableMapper = payableMapper;
        this.receiptMapper = receiptMapper;
        this.paymentMapper = paymentMapper;
        this.invoiceMapper = invoiceMapper;
        this.bankMapper = bankMapper;
        this.fxMapper = fxMapper;
        this.inventoryCostApi = inventoryCostApi;
        this.costService = costService;
        this.voucherService = voucherService;
        this.settingService = settingService;
        this.support = support;
    }

    // ==================== 期间列表 ====================

    public List<PeriodRow> periods(Integer year) {
        List<FinPeriodDO> list = periodMapper.selectList(new LambdaQueryWrapper<FinPeriodDO>()
                .likeRight(year != null, FinPeriodDO::getPeriod, String.valueOf(year)).orderByDesc(FinPeriodDO::getPeriod));
        Map<Long, UserDTO> users = support.users(list.stream().map(FinPeriodDO::getClosedBy).toList());
        String latestClosed = latestClosed();
        String firstOpen = firstOpen();
        TreeSet<String> fxDone = new TreeSet<>(fxMapper.selectList(new LambdaQueryWrapper<FinFxRevaluationDO>().select(FinFxRevaluationDO::getPeriod))
                .stream().map(FinFxRevaluationDO::getPeriod).toList());
        return list.stream().map(p -> new PeriodRow(p.getPeriod(), p.getStartDate(), p.getEndDate(), p.getPeriodStatus(), Boolean.TRUE.equals(p.getCostLocked()),
                fxDone.contains(p.getPeriod()), FinSupport.name(users, p.getClosedBy()), p.getClosedAt(),
                OPEN.equals(p.getPeriodStatus()) && p.getPeriod().equals(firstOpen), CLOSED.equals(p.getPeriodStatus()) && p.getPeriod().equals(latestClosed))).toList();
    }

    private String latestClosed() {
        return periodMapper.selectList(new LambdaQueryWrapper<FinPeriodDO>().eq(FinPeriodDO::getPeriodStatus, CLOSED).orderByDesc(FinPeriodDO::getPeriod).last("LIMIT 1"))
                .stream().findFirst().map(FinPeriodDO::getPeriod).orElse(null);
    }

    private String firstOpen() {
        return periodMapper.selectList(new LambdaQueryWrapper<FinPeriodDO>().eq(FinPeriodDO::getPeriodStatus, OPEN).orderByAsc(FinPeriodDO::getPeriod).last("LIMIT 1"))
                .stream().findFirst().map(FinPeriodDO::getPeriod).orElse(null);
    }

    // ==================== 检查 ====================

    /** 结账检查（向导第 1 步）：阻止项全部通过才能结账 */
    public CheckResult check(String period) {
        FinPeriodDO p = periodOf(period);
        YearMonth ym = YearMonth.parse(period, FinSupport.PERIOD);
        LocalDate from = ym.atDay(1);
        LocalDate to = ym.atEndOfMonth();
        List<CheckItem> items = new ArrayList<>();

        String prev = ym.minusMonths(1).format(FinSupport.PERIOD);
        FinPeriodDO pp = periodMapper.selectOne(new LambdaQueryWrapper<FinPeriodDO>().eq(FinPeriodDO::getPeriod, prev));
        boolean prevOk = pp == null || !OPEN.equals(pp.getPeriodStatus());
        items.add(new CheckItem("PREV", "上期已结账", prevOk, true, prevOk ? null : "请先结账 " + prev, prevOk ? 0 : 1, "/finance/close"));

        boolean inv = inventoryCostApi.isPeriodClosed(period);
        items.add(new CheckItem("INVENTORY", "库存本期已月结", inv, true, inv ? null : "库存期间 " + period + " 尚未月结", inv ? 0 : 1, "/inventory/period"));

        boolean locked = Boolean.TRUE.equals(p.getCostLocked());
        items.add(new CheckItem("COST", "成本已锁定", locked, true, locked ? null : "成本未锁定", locked ? 0 : 1, "/finance/cost"));

        long ar = receivableMapper.selectCount(new LambdaQueryWrapper<FinReceivableDO>().between(FinReceivableDO::getBizDate, from, to)
                .in(FinReceivableDO::getArStatus, ArStatus.DRAFT.name(), ArStatus.PENDING.name()));
        items.add(draftItem("AR", "无草稿应收单", ar, "/finance/receivable"));
        long ap = payableMapper.selectCount(new LambdaQueryWrapper<FinPayableDO>().between(FinPayableDO::getBizDate, from, to)
                .in(FinPayableDO::getApStatus, ArStatus.DRAFT.name(), ArStatus.PENDING.name()));
        items.add(draftItem("AP", "无草稿应付单", ap, "/finance/payable"));
        long rc = receiptMapper.selectCount(new LambdaQueryWrapper<FinReceiptDO>().between(FinReceiptDO::getReceiptDate, from, to)
                .eq(FinReceiptDO::getReceiptStatus, CashStatus.DRAFT.name()));
        items.add(draftItem("RECEIPT", "无草稿收款单", rc, "/finance/receipt"));
        long pm = paymentMapper.selectCount(new LambdaQueryWrapper<FinPaymentDO>().between(FinPaymentDO::getPayDate, from, to)
                .eq(FinPaymentDO::getPaymentStatus, CashStatus.DRAFT.name()));
        items.add(draftItem("PAYMENT", "无草稿付款单", pm, "/finance/payment"));

        long diff = invoiceMapper.selectCount(new LambdaQueryWrapper<FinPurchaseInvoiceDO>().le(FinPurchaseInvoiceDO::getInvoiceDate, to)
                .eq(FinPurchaseInvoiceDO::getMatchStatus, PurchaseInvoiceService.DIFF).eq(FinPurchaseInvoiceDO::getInvoiceStatus, PurchaseInvoiceService.REGISTERED));
        items.add(new CheckItem("INVOICE_DIFF", "进项发票差异已确认", diff == 0, true, diff == 0 ? null : diff + " 张进项发票差异未确认", diff,
                "/finance/payable/invoice"));

        FxResult fx = fxPreview(period);
        boolean fxOk = fx.done() || fx.rows().isEmpty();
        items.add(new CheckItem("FX", "外币期末重估", fxOk, true, fxOk ? (fx.rows().isEmpty() ? "无外币余额，跳过" : null) : "有外币余额，尚未重估",
                fxOk ? 0 : fx.rows().size(), "/finance/close"));

        long pending = voucherService.pending(period).stream().mapToLong(Pending::count).sum();
        long vouchers = voucherService.count(period);
        items.add(new CheckItem("VOUCHER_PENDING", "业务单据已生成凭证", pending == 0 || vouchers == 0, false,
                pending == 0 ? null : pending + " 张业务单据尚未生成凭证" + (vouchers == 0 ? "（未启用凭证，不阻止）" : ""), pending, "/finance/voucher"));
        long unposted = voucherService.unposted(period);
        items.add(new CheckItem("VOUCHER_POSTED", "凭证已全部过账", unposted == 0, true, unposted == 0 ? (vouchers == 0 ? "本期无凭证" : null) : unposted + " 张凭证未过账",
                unposted, "/finance/voucher"));

        boolean passed = items.stream().allMatch(i -> i.passed() || !i.blocking());
        return new CheckResult(period, p.getPeriodStatus(), passed, items);
    }

    private static CheckItem draftItem(String key, String label, long count, String route) {
        return new CheckItem(key, label, count == 0, true, count == 0 ? null : count + " 张未确认", count, route);
    }

    // ==================== 外币重估 ====================

    /** 重估预览：已重估的期间返回保存的结果；否则按当前余额与月末汇率试算（缺少汇率时列出币别） */
    public FxResult fxPreview(String period) {
        List<FinFxRevaluationDO> saved = fxMapper.selectList(new LambdaQueryWrapper<FinFxRevaluationDO>().eq(FinFxRevaluationDO::getPeriod, period)
                .orderByAsc(FinFxRevaluationDO::getId));
        if (!saved.isEmpty()) return result(period, true, saved, List.of());
        List<String> missing = new ArrayList<>();
        return result(period, false, compute(period, missing, false), missing);
    }

    /** 生成重估（向导第 2 步）：重新计算并生成凭证草稿；已有重估时先删除原凭证（草稿）再重算 */
    @Transactional(rollbackFor = Exception.class)
    public FxResult revalue(String period) {
        FinPeriodDO p = periodOf(period);
        if (CLOSED.equals(p.getPeriodStatus())) throw BizException.of(FinanceErrorCodes.PERIOD_CLOSED, period);
        List<FinFxRevaluationDO> old = fxMapper.selectList(new LambdaQueryWrapper<FinFxRevaluationDO>().eq(FinFxRevaluationDO::getPeriod, period));
        old.stream().map(FinFxRevaluationDO::getVoucherId).filter(Objects::nonNull).distinct().forEach(voucherService::releaseForDoc);
        if (!old.isEmpty()) fxMapper.delete(new LambdaQueryWrapper<FinFxRevaluationDO>().eq(FinFxRevaluationDO::getPeriod, period));
        List<FinFxRevaluationDO> rows = compute(period, new ArrayList<>(), true);
        if (rows.isEmpty()) return result(period, true, rows, List.of());
        List<FinVoucherLineDO> lines = revaluationLines(period, rows, false);
        Long voucherId = lines.isEmpty() ? null : voucherService.createSystem(end(period), FX_BIZ_TYPE, lines, "外币期末重估 " + period, null);
        for (FinFxRevaluationDO r : rows) {
            r.setVoucherId(voucherId);
            fxMapper.insert(r);
        }
        support.log(FinanceModuleConfig.VOUCHER, p.getId(), period, "FX_REVALUE", "外币重估", null, null, rows.size() + " 笔");
        return result(period, true, rows, List.of());
    }

    private FxResult result(String period, boolean done, List<FinFxRevaluationDO> rows, List<String> missing) {
        Map<Long, String> customers = new HashMap<>();
        support.customers(rows.stream().filter(r -> "AR".equals(r.getDocType())).map(FinFxRevaluationDO::getPartnerId).toList())
                .forEach((k, v) -> customers.put(k, FinSupport.customerName(v)));
        Map<Long, String> suppliers = new HashMap<>();
        support.suppliers(rows.stream().filter(r -> "AP".equals(r.getDocType())).map(FinFxRevaluationDO::getPartnerId).toList())
                .forEach((k, v) -> suppliers.put(k, v.name()));
        List<FxRow> list = rows.stream().map(r -> new FxRow(r.getDocType(), r.getDocId(), r.getDocNo(),
                "AR".equals(r.getDocType()) ? customers.get(r.getPartnerId()) : "AP".equals(r.getDocType()) ? suppliers.get(r.getPartnerId()) : null,
                r.getCurrency(), r.getFcBalance(), r.getBookBase(), r.getPeriodEndRate(), r.getRevaluedBase(), r.getDiff())).toList();
        BigDecimal total = rows.stream().map(FinFxRevaluationDO::getDiff).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        Long vid = rows.stream().map(FinFxRevaluationDO::getVoucherId).filter(Objects::nonNull).findFirst().orElse(null);
        String vno = null;
        if (vid != null) {
            try {
                vno = voucherService.get(vid).getDocNo();
            } catch (BizException e) {
                vid = null;
            }
        }
        return new FxResult(period, done, list, Decimals.amount(total), missing, vid, vno);
    }

    /** 计算外币余额与重估差异；strict 时缺少月末汇率直接报错（FIN-CLS-R02） */
    private List<FinFxRevaluationDO> compute(String period, List<String> missing, boolean strict) {
        String base = support.baseCurrency();
        LocalDate to = end(period);
        Map<String, BigDecimal> rates = new HashMap<>();
        List<FinFxRevaluationDO> rows = new ArrayList<>();
        for (FinReceivableDO r : receivableMapper.selectList(new LambdaQueryWrapper<FinReceivableDO>().ne(FinReceivableDO::getCurrency, base)
                .eq(FinReceivableDO::getArStatus, ArStatus.CONFIRMED.name()).le(FinReceivableDO::getBizDate, to).apply("verified_amount <> total_amount"))) {
            BigDecimal fc = FinSupport.nz(r.getTotalAmount()).subtract(FinSupport.nz(r.getVerifiedAmount()));
            rows.add(row(period, "AR", r.getId(), r.getDocNo(), r.getCustomerId(), r.getCurrency(), fc, FinSupport.toBase(fc, r.getExchangeRate())));
        }
        for (FinPayableDO r : payableMapper.selectList(new LambdaQueryWrapper<FinPayableDO>().ne(FinPayableDO::getCurrency, base)
                .eq(FinPayableDO::getApStatus, ArStatus.CONFIRMED.name()).le(FinPayableDO::getBizDate, to).apply("verified_amount <> total_amount"))) {
            BigDecimal fc = FinSupport.nz(r.getTotalAmount()).subtract(FinSupport.nz(r.getVerifiedAmount()));
            rows.add(row(period, "AP", r.getId(), r.getDocNo(), r.getSupplierId(), r.getCurrency(), fc, FinSupport.toBase(fc, r.getExchangeRate())));
        }
        for (FinBankAccountDO b : bankMapper.selectList(new LambdaQueryWrapper<FinBankAccountDO>().ne(FinBankAccountDO::getCurrency, base))) {
            BigDecimal fc = BigDecimal.ZERO;
            BigDecimal book = BigDecimal.ZERO;
            for (FinReceiptDO r : receiptMapper.selectList(new LambdaQueryWrapper<FinReceiptDO>().eq(FinReceiptDO::getBankAccountId, b.getId())
                    .eq(FinReceiptDO::getReceiptStatus, CashStatus.CONFIRMED.name()).le(FinReceiptDO::getReceiptDate, to))) {
                fc = fc.add(FinSupport.nz(r.getAmount()));
                book = book.add(FinSupport.toBase(r.getAmount(), r.getExchangeRate()));
            }
            for (FinPaymentDO r : paymentMapper.selectList(new LambdaQueryWrapper<FinPaymentDO>().eq(FinPaymentDO::getBankAccountId, b.getId())
                    .eq(FinPaymentDO::getPaymentStatus, CashStatus.CONFIRMED.name()).le(FinPaymentDO::getPayDate, to))) {
                BigDecimal out = FinSupport.nz(r.getAmount()).add(FinSupport.nz(r.getBankFee()));
                fc = fc.subtract(out);
                book = book.subtract(FinSupport.toBase(out, r.getExchangeRate()));
            }
            // 以前期间重估差异计入账面（未冲回时）
            book = book.add(priorBankDiff(b.getId(), period));
            if (fc.signum() != 0) rows.add(row(period, "BANK", b.getId(), b.getCode() + " " + b.getName(), null, b.getCurrency(), fc, Decimals.amount(book)));
        }
        List<FinFxRevaluationDO> result = new ArrayList<>();
        for (FinFxRevaluationDO r : rows) {
            BigDecimal rate = rates.computeIfAbsent(r.getCurrency(), c -> {
                try {
                    return support.currencyApi().getRate(c, to, RateType.MONTH_END);
                } catch (RuntimeException e) {
                    return null;
                }
            });
            if (rate == null || rate.signum() <= 0) {
                if (strict) throw BizException.of(FinanceErrorCodes.CLS_RATE_MISSING, r.getCurrency(), period);
                if (!missing.contains(r.getCurrency())) missing.add(r.getCurrency());
                result.add(r);
                continue;
            }
            r.setPeriodEndRate(rate);
            r.setRevaluedBase(FinSupport.toBase(r.getFcBalance(), rate));
            r.setDiff(Decimals.amount(r.getRevaluedBase().subtract(r.getBookBase())));
            if (!strict || r.getDiff().signum() != 0) result.add(r);
        }
        return result;
    }

    /** 银行存款以前期间未冲回的重估差异 */
    private BigDecimal priorBankDiff(Long bankId, String period) {
        if (support.params().getBool(FinanceModuleConfig.P_FX_REVERSE)) return BigDecimal.ZERO;
        return fxMapper.selectList(new LambdaQueryWrapper<FinFxRevaluationDO>().eq(FinFxRevaluationDO::getDocType, "BANK").eq(FinFxRevaluationDO::getDocId, bankId)
                .lt(FinFxRevaluationDO::getPeriod, period)).stream().map(FinFxRevaluationDO::getDiff).map(FinSupport::nz).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static FinFxRevaluationDO row(String period, String type, Long id, String no, Long partnerId, String currency, BigDecimal fc, BigDecimal book) {
        FinFxRevaluationDO r = new FinFxRevaluationDO();
        r.setPeriod(period);
        r.setDocType(type);
        r.setDocId(id);
        r.setDocNo(no);
        r.setPartnerId(partnerId);
        r.setCurrency(currency);
        r.setFcBalance(Decimals.amount(fc));
        r.setBookBase(Decimals.amount(book));
        return r;
    }

    /**
     * 重估凭证分录：应收 / 银行差异为正 = 收益（借 1122 或银行科目，贷 660301）；应付差异为正 = 损失（借 660301，贷 2202）。
     * 同科目同往来单位合并；reverse = 冲回凭证（借贷互换）
     */
    private List<FinVoucherLineDO> revaluationLines(String period, List<FinFxRevaluationDO> rows, boolean reverse) {
        Map<String, FinVoucherLineDO> merged = new LinkedHashMap<>();
        BigDecimal fx = BigDecimal.ZERO;
        String summary = (reverse ? "冲回 " : "") + period + " 外币重估";
        for (FinFxRevaluationDO r : rows) {
            BigDecimal diff = FinSupport.nz(r.getDiff());
            if (diff.signum() == 0) continue;
            String account = switch (r.getDocType()) {
                case "AR" -> "1122";
                case "AP" -> "2202";
                default -> bankAccountCode(r.getDocId());
            };
            // 资产类（应收、银行）借方增加；负债类（应付）贷方增加
            BigDecimal debitSide = "AP".equals(r.getDocType()) ? diff.negate() : diff;
            if (reverse) debitSide = debitSide.negate();
            String key = account + "|" + Objects.toString(r.getPartnerId(), "") + "|" + r.getCurrency();
            FinVoucherLineDO l = merged.computeIfAbsent(key, k -> {
                FinVoucherLineDO x = new FinVoucherLineDO();
                x.setSummary(summary + " " + r.getCurrency());
                x.setAccountCode(account);
                x.setDebit(BigDecimal.ZERO);
                x.setCredit(BigDecimal.ZERO);
                if ("AR".equals(r.getDocType())) x.setAuxCustomerId(r.getPartnerId());
                if ("AP".equals(r.getDocType())) x.setAuxSupplierId(r.getPartnerId());
                x.setSourceType("FX_REVALUATION");
                return x;
            });
            l.setDebit(l.getDebit().add(debitSide));
            fx = fx.add(debitSide);
        }
        List<FinVoucherLineDO> lines = new ArrayList<>();
        for (FinVoucherLineDO l : merged.values()) {
            BigDecimal v = l.getDebit();
            if (v.signum() == 0) continue;
            l.setDebit(v.signum() > 0 ? v : BigDecimal.ZERO);
            l.setCredit(v.signum() < 0 ? v.negate() : BigDecimal.ZERO);
            lines.add(l);
        }
        if (fx.signum() != 0) {
            FinVoucherLineDO l = new FinVoucherLineDO();
            l.setSummary(summary + " 汇兑损益");
            l.setAccountCode(FX_ACCOUNT);
            l.setDebit(fx.signum() < 0 ? fx.negate() : BigDecimal.ZERO);
            l.setCredit(fx.signum() > 0 ? fx : BigDecimal.ZERO);
            l.setSourceType("FX_REVALUATION");
            lines.add(l);
        }
        return lines;
    }

    private String bankAccountCode(Long bankId) {
        FinBankAccountDO b = bankMapper.selectById(bankId);
        return b != null && b.getAccountCode() != null && !b.getAccountCode().isBlank() ? b.getAccountCode() : "1002";
    }

    // ==================== 结账 / 反结账 ====================

    /** 结账（向导第 4 步）：按期间顺序（FIN-CLS-R01）、检查全部通过；期间 CLOSED，下期 OPEN，发布 FinPeriodClosedEvent */
    @Transactional(rollbackFor = Exception.class)
    public void close(String period) {
        FinPeriodDO p = periodOf(period);
        if (CLOSED.equals(p.getPeriodStatus())) throw BizException.of(FinanceErrorCodes.PERIOD_CLOSED, period);
        String prev = YearMonth.parse(period, FinSupport.PERIOD).minusMonths(1).format(FinSupport.PERIOD);
        FinPeriodDO pp = periodMapper.selectOne(new LambdaQueryWrapper<FinPeriodDO>().eq(FinPeriodDO::getPeriod, prev));
        if (pp != null && OPEN.equals(pp.getPeriodStatus())) throw BizException.of(FinanceErrorCodes.CLS_ORDER, prev);
        CheckResult c = check(period);
        if (!c.passed()) {
            throw BizException.of(FinanceErrorCodes.CLS_BLOCKED, String.join("；", c.items().stream().filter(i -> i.blocking() && !i.passed())
                    .map(i -> i.message() == null ? i.label() : i.message()).toList()));
        }
        String next = YearMonth.parse(period, FinSupport.PERIOD).plusMonths(1).format(FinSupport.PERIOD);
        FinPeriodDO np = settingService.ensurePeriod(next);
        if (NOT_OPEN.equals(np.getPeriodStatus())) {
            np.setPeriodStatus(OPEN);
            periodMapper.updateByIdOrFail(np);
        }
        // 外币重估冲回（下月 1 日）
        if (support.params().getBool(FinanceModuleConfig.P_FX_REVERSE)) {
            List<FinFxRevaluationDO> rows = fxMapper.selectList(new LambdaQueryWrapper<FinFxRevaluationDO>().eq(FinFxRevaluationDO::getPeriod, period)
                    .isNotNull(FinFxRevaluationDO::getVoucherId).isNull(FinFxRevaluationDO::getReversalVoucherId));
            List<FinVoucherLineDO> lines = revaluationLines(period, rows, true);
            if (!lines.isEmpty()) {
                Long rid = voucherService.createSystem(YearMonth.parse(next, FinSupport.PERIOD).atDay(1), FX_BIZ_TYPE, lines, "冲回 " + period + " 外币重估",
                        rows.get(0).getVoucherId());
                for (FinFxRevaluationDO r : rows) {
                    r.setReversalVoucherId(rid);
                    fxMapper.updateByIdOrFail(r);
                }
            }
        }
        p.setPeriodStatus(CLOSED);
        p.setClosedBy(support.currentUser());
        p.setClosedAt(LocalDateTime.now());
        periodMapper.updateByIdOrFail(p);
        support.log(FinanceModuleConfig.VOUCHER, p.getId(), period, "CLOSE", "财务结账", OPEN, CLOSED, null);
        support.events().publish(new FinPeriodClosedEvent(period, false));
    }

    /** 反结账：只能反结账最近一个已结账期间；原因必填；成本解锁；删除下月冲回凭证草稿 */
    @Transactional(rollbackFor = Exception.class)
    public void reopen(String period, String reason) {
        String why = FinSupport.requireText(reason, "反结账原因");
        FinPeriodDO p = periodOf(period);
        if (!CLOSED.equals(p.getPeriodStatus())) throw BizException.of(FinanceErrorCodes.CLS_NOT_CLOSED, period);
        String latest = latestClosed();
        if (!period.equals(latest)) throw BizException.of(FinanceErrorCodes.CLS_REOPEN_LATEST, latest);
        List<FinFxRevaluationDO> rows = fxMapper.selectList(new LambdaQueryWrapper<FinFxRevaluationDO>().eq(FinFxRevaluationDO::getPeriod, period)
                .isNotNull(FinFxRevaluationDO::getReversalVoucherId));
        rows.stream().map(FinFxRevaluationDO::getReversalVoucherId).distinct().forEach(voucherService::releaseForDoc);
        for (FinFxRevaluationDO r : rows) {
            r.setReversalVoucherId(null);
            fxMapper.updateByIdOrFail(r);
        }
        p.setPeriodStatus(OPEN);
        p.setClosedBy(null);
        p.setClosedAt(null);
        periodMapper.updateByIdOrFail(p);
        costService.unlock(period);
        support.log(FinanceModuleConfig.VOUCHER, p.getId(), period, "REOPEN", "财务反结账", CLOSED, OPEN, why);
        support.events().publish(new FinPeriodClosedEvent(period, true));
    }

    private FinPeriodDO periodOf(String period) {
        if (period == null || !period.matches("\\d{6}")) throw BizException.of(FinanceErrorCodes.NOT_EXISTS, "会计期间");
        return settingService.ensurePeriod(period);
    }

    static LocalDate end(String period) {
        return YearMonth.parse(period, FinSupport.PERIOD).atEndOfMonth();
    }
}
