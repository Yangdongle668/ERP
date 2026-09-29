package com.erp.module.finance.service.report;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.common.util.Decimals;
import com.erp.module.crm.api.customer.CustomerDTO;
import com.erp.module.finance.controller.vo.ReportVOs.AgingDoc;
import com.erp.module.finance.controller.vo.ReportVOs.AgingReport;
import com.erp.module.finance.controller.vo.ReportVOs.AgingRow;
import com.erp.module.finance.controller.vo.ReportVOs.Statement;
import com.erp.module.finance.controller.vo.ReportVOs.StatementLine;
import com.erp.module.finance.dal.dataobject.FinPayableDO;
import com.erp.module.finance.dal.dataobject.FinPaymentDO;
import com.erp.module.finance.dal.dataobject.FinReceiptDO;
import com.erp.module.finance.dal.dataobject.FinReceivableDO;
import com.erp.module.finance.dal.dataobject.FinVerificationDO;
import com.erp.module.finance.dal.mapper.FinPayableMapper;
import com.erp.module.finance.dal.mapper.FinPaymentMapper;
import com.erp.module.finance.dal.mapper.FinReceiptMapper;
import com.erp.module.finance.dal.mapper.FinReceivableMapper;
import com.erp.module.finance.dal.mapper.FinVerificationMapper;
import com.erp.module.finance.service.ArStatus;
import com.erp.module.finance.service.CashStatus;
import com.erp.module.finance.service.FinSupport;
import com.erp.module.finance.service.verify.VerificationService;
import com.erp.module.purchase.api.supplier.SupplierDTO;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 应收 / 应付账龄、客户 / 供应商往来对账单（12-08 P0 报表） */
@Service
public class FinReportService {

    public static final List<String> BUCKETS = List.of("NOT_DUE", "D1_30", "D31_60", "D61_90", "D91_180", "OVER_180");

    private final FinReceivableMapper receivableMapper;
    private final FinReceiptMapper receiptMapper;
    private final FinPayableMapper payableMapper;
    private final FinPaymentMapper paymentMapper;
    private final FinVerificationMapper verificationMapper;
    private final FinSupport support;

    public FinReportService(FinReceivableMapper receivableMapper, FinReceiptMapper receiptMapper, FinPayableMapper payableMapper,
                            FinPaymentMapper paymentMapper, FinVerificationMapper verificationMapper, FinSupport support) {
        this.receivableMapper = receivableMapper;
        this.receiptMapper = receiptMapper;
        this.payableMapper = payableMapper;
        this.paymentMapper = paymentMapper;
        this.verificationMapper = verificationMapper;
        this.support = support;
    }

    /** 逾期天数分段（FIN-RPT-R01：以到期日为准，无到期日按业务日期） */
    public static String bucket(int overdueDays) {
        if (overdueDays <= 0) return "NOT_DUE";
        if (overdueDays <= 30) return "D1_30";
        if (overdueDays <= 60) return "D31_60";
        if (overdueDays <= 90) return "D61_90";
        if (overdueDays <= 180) return "D91_180";
        return "OVER_180";
    }

    static int overdue(LocalDate due, LocalDate asOf) {
        return due == null || !due.isBefore(asOf) ? 0 : (int) ChronoUnit.DAYS.between(due, asOf);
    }

    /** 单据（内部） */
    record Item(Long id, String docNo, String docType, String sourceNo, Long partnerId, String currency, LocalDate bizDate, LocalDate dueDate,
                BigDecimal total, BigDecimal open) {
    }

    // ==================== 账龄 ====================

    public AgingReport arAging(LocalDate asOf, Long customerId, String currency) {
        LocalDate d = asOf == null ? LocalDate.now() : asOf;
        List<Item> items = arItems(customerId, currency);
        Map<Long, CustomerDTO> cs = support.customers(items.stream().map(Item::partnerId).toList());
        return aging(d, items, id -> {
            CustomerDTO c = cs.get(id);
            return c == null ? new String[]{null, null} : new String[]{c.code(), FinSupport.customerName(c)};
        });
    }

    public AgingReport apAging(LocalDate asOf, Long supplierId, String currency) {
        LocalDate d = asOf == null ? LocalDate.now() : asOf;
        List<Item> items = apItems(supplierId, currency);
        Map<Long, SupplierDTO> ss = support.suppliers(items.stream().map(Item::partnerId).toList());
        return aging(d, items, id -> {
            SupplierDTO s = ss.get(id);
            return s == null ? new String[]{null, null} : new String[]{s.code(), s.name()};
        });
    }

    public List<AgingDoc> arAgingDocs(LocalDate asOf, Long customerId, String currency) {
        return docs(asOf == null ? LocalDate.now() : asOf, arItems(customerId, currency));
    }

    public List<AgingDoc> apAgingDocs(LocalDate asOf, Long supplierId, String currency) {
        return docs(asOf == null ? LocalDate.now() : asOf, apItems(supplierId, currency));
    }

    private List<Item> arItems(Long customerId, String currency) {
        return receivableMapper.selectList(new LambdaQueryWrapper<FinReceivableDO>().eq(FinReceivableDO::getArStatus, ArStatus.CONFIRMED.name())
                        .eq(customerId != null, FinReceivableDO::getCustomerId, customerId)
                        .eq(StringUtils.hasText(currency), FinReceivableDO::getCurrency, currency)
                        .apply("verified_amount <> total_amount"))
                .stream().map(r -> new Item(r.getId(), r.getDocNo(), r.getArType(), r.getSourceNo(), r.getCustomerId(), r.getCurrency(), r.getBizDate(),
                        r.getDueDate() != null ? r.getDueDate() : r.getBizDate(), r.getTotalAmount(),
                        r.getTotalAmount().subtract(FinSupport.nz(r.getVerifiedAmount()))))
                .toList();
    }

    private List<Item> apItems(Long supplierId, String currency) {
        return payableMapper.selectList(new LambdaQueryWrapper<FinPayableDO>().eq(FinPayableDO::getApStatus, ArStatus.CONFIRMED.name())
                        .eq(supplierId != null, FinPayableDO::getSupplierId, supplierId)
                        .eq(StringUtils.hasText(currency), FinPayableDO::getCurrency, currency)
                        .apply("verified_amount <> total_amount"))
                .stream().map(p -> new Item(p.getId(), p.getDocNo(), p.getApType(), p.getStatementNo(), p.getSupplierId(), p.getCurrency(), p.getBizDate(),
                        p.getDueDate() != null ? p.getDueDate() : p.getBizDate(), p.getTotalAmount(),
                        p.getTotalAmount().subtract(FinSupport.nz(p.getVerifiedAmount()))))
                .toList();
    }

    private AgingReport aging(LocalDate asOf, List<Item> items, Function<Long, String[]> names) {
        Map<String, List<Item>> groups = items.stream().collect(Collectors.groupingBy(i -> i.partnerId() + "|" + i.currency(), LinkedHashMap::new,
                Collectors.toList()));
        Map<String, BigDecimal> rates = new LinkedHashMap<>();
        List<AgingRow> rows = new ArrayList<>();
        for (List<Item> g : groups.values()) {
            Item first = g.get(0);
            BigDecimal rate = rates.computeIfAbsent(first.currency(), c -> support.rate(c, asOf));
            BigDecimal[] b = new BigDecimal[BUCKETS.size()];
            java.util.Arrays.fill(b, BigDecimal.ZERO);
            for (Item i : g) {
                int idx = BUCKETS.indexOf(bucket(overdue(i.dueDate(), asOf)));
                b[idx] = b[idx].add(i.open());
            }
            BigDecimal total = java.util.Arrays.stream(b).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal overdue = total.subtract(b[0]);
            String[] n = names.apply(first.partnerId());
            rows.add(new AgingRow(first.partnerId(), n[0], n[1], first.currency(), rate, total, b[0], b[1], b[2], b[3], b[4], b[5],
                    FinSupport.toBase(total, rate), FinSupport.toBase(overdue, rate)));
        }
        rows.sort(Comparator.comparing(AgingRow::totalBase).reversed());
        return new AgingReport(asOf, support.baseCurrency(), rows, rows.stream().map(AgingRow::totalBase).reduce(BigDecimal.ZERO, BigDecimal::add),
                rows.stream().map(AgingRow::overdueBase).reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private static List<AgingDoc> docs(LocalDate asOf, List<Item> items) {
        return items.stream().sorted(Comparator.comparing(Item::dueDate)).map(i -> {
            int days = overdue(i.dueDate(), asOf);
            return new AgingDoc(i.id(), i.docNo(), i.docType(), i.sourceNo(), i.bizDate(), i.dueDate(), days, bucket(days), i.currency(), i.total(), i.open());
        }).toList();
    }

    // ==================== 往来对账单 ====================

    /** 客户对账单：本期应收（确认的应收单，红字为负）、本期收款（到账 + 手续费，含预收；退款为负） */
    public Statement customerStatement(Long customerId, String currency, LocalDate from, LocalDate to) {
        CustomerDTO c = support.customer(customerId);
        String cur = StringUtils.hasText(currency) ? currency : c.currency();
        LocalDate f = from != null ? from : YearMonth.now().atDay(1);
        LocalDate t = to != null ? to : YearMonth.from(f).atEndOfMonth();
        List<FinReceivableDO> ars = receivableMapper.selectList(new LambdaQueryWrapper<FinReceivableDO>().eq(FinReceivableDO::getCustomerId, customerId)
                .eq(FinReceivableDO::getCurrency, cur).eq(FinReceivableDO::getArStatus, ArStatus.CONFIRMED.name()).le(FinReceivableDO::getBizDate, t));
        List<FinReceiptDO> rvs = receiptMapper.selectList(new LambdaQueryWrapper<FinReceiptDO>().eq(FinReceiptDO::getCustomerId, customerId)
                .eq(FinReceiptDO::getCurrency, cur).eq(FinReceiptDO::getReceiptStatus, CashStatus.CONFIRMED.name()).le(FinReceiptDO::getReceiptDate, t));
        BigDecimal opening = BigDecimal.ZERO;
        List<StatementLine> lines = new ArrayList<>();
        for (FinReceivableDO r : ars) {
            if (r.getBizDate().isBefore(f)) opening = opening.add(r.getTotalAmount());
            else lines.add(new StatementLine(r.getBizDate(), "RECEIVABLE", r.getId(), r.getDocNo(), summary(r), cur, r.getTotalAmount(), BigDecimal.ZERO, null));
        }
        for (FinReceiptDO r : rvs) {
            BigDecimal gross = VerificationService.receiptGross(r);
            if (r.getReceiptDate().isBefore(f)) opening = opening.subtract(gross);
            else lines.add(new StatementLine(r.getReceiptDate(), "RECEIPT", r.getId(), r.getDocNo(), receiptSummary(r), cur, BigDecimal.ZERO, gross, null));
        }
        BigDecimal ledger = ars.stream().map(r -> r.getTotalAmount().subtract(FinSupport.nz(r.getVerifiedAmount()))).reduce(BigDecimal.ZERO, BigDecimal::add)
                .subtract(rvs.stream().map(VerificationService::receiptOpen).reduce(BigDecimal.ZERO, BigDecimal::add));
        BigDecimal verified = verified(VerificationService.CUSTOMER, customerId, cur, f, t);
        return statement(c.id(), c.code(), FinSupport.customerName(c), c.nameEn(), cur, f, t, opening, lines, verified, ledger);
    }

    /** 供应商对账单：本期应付、本期付款（付款金额，不含我方手续费） */
    public Statement supplierStatement(Long supplierId, String currency, LocalDate from, LocalDate to) {
        SupplierDTO s = support.supplier(supplierId);
        String cur = StringUtils.hasText(currency) ? currency : s.currency();
        LocalDate f = from != null ? from : YearMonth.now().atDay(1);
        LocalDate t = to != null ? to : YearMonth.from(f).atEndOfMonth();
        List<FinPayableDO> aps = payableMapper.selectList(new LambdaQueryWrapper<FinPayableDO>().eq(FinPayableDO::getSupplierId, supplierId)
                .eq(FinPayableDO::getCurrency, cur).eq(FinPayableDO::getApStatus, ArStatus.CONFIRMED.name()).le(FinPayableDO::getBizDate, t));
        List<FinPaymentDO> pys = paymentMapper.selectList(new LambdaQueryWrapper<FinPaymentDO>().eq(FinPaymentDO::getSupplierId, supplierId)
                .eq(FinPaymentDO::getCurrency, cur).eq(FinPaymentDO::getPaymentStatus, CashStatus.CONFIRMED.name()).le(FinPaymentDO::getPayDate, t));
        BigDecimal opening = BigDecimal.ZERO;
        List<StatementLine> lines = new ArrayList<>();
        for (FinPayableDO p : aps) {
            if (p.getBizDate().isBefore(f)) opening = opening.add(p.getTotalAmount());
            else lines.add(new StatementLine(p.getBizDate(), "PAYABLE", p.getId(), p.getDocNo(),
                    p.getStatementNo() != null ? "对账单 " + p.getStatementNo() : p.getDescription(), cur, p.getTotalAmount(), BigDecimal.ZERO, null));
        }
        for (FinPaymentDO p : pys) {
            if (p.getPayDate().isBefore(f)) opening = opening.subtract(p.getAmount());
            else lines.add(new StatementLine(p.getPayDate(), "PAYMENT", p.getId(), p.getDocNo(),
                    ("PREPAYMENT".equals(p.getRequestType()) ? "预付款 " : "付款 ") + p.getRequestNo(), cur, BigDecimal.ZERO, p.getAmount(), null));
        }
        BigDecimal ledger = aps.stream().map(p -> p.getTotalAmount().subtract(FinSupport.nz(p.getVerifiedAmount()))).reduce(BigDecimal.ZERO, BigDecimal::add)
                .subtract(pys.stream().map(p -> p.getAmount().subtract(FinSupport.nz(p.getAllocatedAmount()))).reduce(BigDecimal.ZERO, BigDecimal::add));
        BigDecimal verified = verified(VerificationService.SUPPLIER, supplierId, cur, f, t);
        return statement(s.id(), s.code(), s.name(), null, cur, f, t, opening, lines, verified, ledger);
    }

    private Statement statement(Long id, String code, String name, String nameEn, String cur, LocalDate f, LocalDate t, BigDecimal opening,
                                List<StatementLine> raw, BigDecimal verified, BigDecimal ledger) {
        raw.sort(Comparator.comparing(StatementLine::date).thenComparing(l -> l.debit().signum() == 0 ? 1 : 0).thenComparing(StatementLine::docNo));
        BigDecimal balance = Decimals.amount(opening);
        BigDecimal debit = BigDecimal.ZERO;
        BigDecimal credit = BigDecimal.ZERO;
        List<StatementLine> lines = new ArrayList<>();
        for (StatementLine l : raw) {
            balance = balance.add(l.debit()).subtract(l.credit());
            debit = debit.add(l.debit());
            credit = credit.add(l.credit());
            lines.add(new StatementLine(l.date(), l.docType(), l.docId(), l.docNo(), l.summary(), l.currency(), l.debit(), l.credit(), balance));
        }
        boolean mismatch = !t.isBefore(LocalDate.now()) && balance.compareTo(Decimals.amount(ledger)) != 0;
        return new Statement(id, code, name, nameEn, cur, f, t, Decimals.amount(opening), debit, credit, verified, balance, Decimals.amount(ledger), mismatch, lines);
    }

    private BigDecimal verified(String partnerType, Long partnerId, String currency, LocalDate from, LocalDate to) {
        return verificationMapper.selectList(new LambdaQueryWrapper<FinVerificationDO>().eq(FinVerificationDO::getPartnerType, partnerType)
                        .eq(FinVerificationDO::getPartnerId, partnerId).eq(FinVerificationDO::getCurrency, currency).eq(FinVerificationDO::getReversed, false)
                        .ge(FinVerificationDO::getVerifiedAt, from.atStartOfDay()).lt(FinVerificationDO::getVerifiedAt, to.plusDays(1).atStartOfDay()))
                .stream().map(FinVerificationDO::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static String summary(FinReceivableDO r) {
        String type = switch (r.getArType()) {
            case "SALES" -> "出货";
            case "SALES_RETURN" -> "退货";
            case "DISCOUNT" -> "折让";
            default -> "其他应收";
        };
        return type + (r.getSourceNo() != null ? " " + r.getSourceNo() : r.getDescription() != null ? " " + r.getDescription() : "");
    }

    private static String receiptSummary(FinReceiptDO r) {
        String type = switch (r.getReceiptType()) {
            case "ADVANCE" -> "预收款";
            case "REFUND" -> "退款";
            case "OTHER" -> "其他收款";
            default -> "收款";
        };
        return type + (r.getOrderNo() != null ? " " + r.getOrderNo() : "") + (r.getBankRefNo() != null ? " " + r.getBankRefNo() : "");
    }

    /** 客户对账单打印数据 */
    public Map<String, Object> customerStatementPrint(Long customerId, String currency, LocalDate from, LocalDate to) {
        Statement s = customerStatement(customerId, currency, from, to);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("customerName", s.partnerName());
        m.put("customerNameEn", s.partnerNameEn());
        m.put("currency", s.currency());
        m.put("dateFrom", s.dateFrom());
        m.put("dateTo", s.dateTo());
        m.put("opening", s.opening());
        m.put("debit", s.debit());
        m.put("credit", s.credit());
        m.put("closing", s.closing());
        m.put("lines", s.lines().stream().map(l -> {
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("date", l.date());
            x.put("docNo", l.docNo());
            x.put("summary", l.summary());
            x.put("debit", l.debit());
            x.put("credit", l.credit());
            x.put("balance", l.balance());
            return x;
        }).toList());
        return m;
    }
}
