package com.erp.module.finance.service.verify;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.common.exception.BizException;
import com.erp.common.util.Decimals;
import com.erp.module.crm.api.customer.CustomerDTO;
import com.erp.module.finance.api.FinanceErrorCodes;
import com.erp.module.finance.api.receipt.ReceiptAllocatedEvent;
import com.erp.module.finance.config.FinanceModuleConfig;
import com.erp.module.finance.controller.vo.VerifyVOs.Candidate;
import com.erp.module.finance.controller.vo.VerifyVOs.Candidates;
import com.erp.module.finance.controller.vo.VerifyVOs.Pick;
import com.erp.module.finance.controller.vo.VerifyVOs.VerificationVO;
import com.erp.module.finance.controller.vo.VerifyVOs.VerifyReq;
import com.erp.module.finance.controller.vo.VerifyVOs.VerifyResult;
import com.erp.module.finance.dal.dataobject.FinPayableDO;
import com.erp.module.finance.dal.dataobject.FinPaymentDO;
import com.erp.module.finance.dal.dataobject.FinReceiptDO;
import com.erp.module.finance.dal.dataobject.FinReceivableDO;
import com.erp.module.finance.dal.dataobject.FinReceivableLineDO;
import com.erp.module.finance.dal.dataobject.FinVerificationDO;
import com.erp.module.finance.dal.mapper.FinPayableMapper;
import com.erp.module.finance.dal.mapper.FinPaymentMapper;
import com.erp.module.finance.dal.mapper.FinReceiptMapper;
import com.erp.module.finance.dal.mapper.FinReceivableLineMapper;
import com.erp.module.finance.dal.mapper.FinReceivableMapper;
import com.erp.module.finance.dal.mapper.FinVerificationMapper;
import com.erp.module.finance.service.ArStatus;
import com.erp.module.finance.service.CashStatus;
import com.erp.module.finance.service.FinSupport;
import com.erp.module.purchase.api.supplier.SupplierDTO;
import com.erp.module.sales.api.order.SalesOrderHeaderDTO;
import com.erp.module.sales.api.order.SalesOrderQueryApi;
import com.erp.module.sales.api.order.SalesOrderWritebackApi;
import com.erp.module.system.api.user.UserDTO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 核销（12-03 第 3.3 节、12-05 第 3.3 节）：收款 / 预收 / 红字应收 ↔ 蓝字应收，退款 ↔ 红字应收 / 预收；
 * 付款 / 预付 / 红字应付 ↔ 蓝字应付。汇兑差异 = 贷方本位币（按其汇率）− 借方本位币（按其汇率）。
 */
@Service
public class VerificationService {

    public static final String DOC_RECEIPT = "RECEIPT";
    public static final String DOC_RECEIVABLE = "RECEIVABLE";
    public static final String DOC_PAYMENT = "PAYMENT";
    public static final String DOC_PAYABLE = "PAYABLE";
    public static final String CUSTOMER = "CUSTOMER";
    public static final String SUPPLIER = "SUPPLIER";

    private final FinVerificationMapper mapper;
    private final FinReceiptMapper receiptMapper;
    private final FinReceivableMapper receivableMapper;
    private final FinReceivableLineMapper receivableLineMapper;
    private final FinPaymentMapper paymentMapper;
    private final FinPayableMapper payableMapper;
    private final SalesOrderQueryApi orderQueryApi;
    private final SalesOrderWritebackApi writebackApi;
    private final FinSupport support;

    public VerificationService(FinVerificationMapper mapper, FinReceiptMapper receiptMapper, FinReceivableMapper receivableMapper,
                               FinReceivableLineMapper receivableLineMapper, FinPaymentMapper paymentMapper, FinPayableMapper payableMapper,
                               SalesOrderQueryApi orderQueryApi, SalesOrderWritebackApi writebackApi, FinSupport support) {
        this.mapper = mapper;
        this.receiptMapper = receiptMapper;
        this.receivableMapper = receivableMapper;
        this.receivableLineMapper = receivableLineMapper;
        this.paymentMapper = paymentMapper;
        this.payableMapper = payableMapper;
        this.orderQueryApi = orderQueryApi;
        this.writebackApi = writebackApi;
        this.support = support;
    }

    /** 核销参与方（内部） */
    static final class Doc {
        String type;
        Long id;
        String no;
        /** RECEIPT / ADVANCE / REFUND / RED / BLUE / PAYMENT / PREPAY */
        String kind;
        Long partnerId;
        String currency;
        BigDecimal rate;
        /** 可核销金额（绝对值） */
        BigDecimal available;
        BigDecimal remaining;
        LocalDate date;
        LocalDate dueDate;
        Long orderId;
        String orderNo;
        Set<Long> orderIds = new HashSet<>();
        String sourceNo;
        BigDecimal total;
        String remark;

        boolean credit() {
            return !"BLUE".equals(kind) && !"REFUND".equals(kind);
        }
    }

    record Pair(Doc credit, Doc debit, BigDecimal amount) {
    }

    // ==================== 候选 ====================

    /** 收款核销候选：左侧未核销收款、预收、退款、红字应收；右侧未核销蓝字应收（按到期日） */
    public Candidates receiptCandidates(Long customerId, String currency) {
        CustomerDTO c = support.customer(customerId);
        String cur = currency != null ? currency : c.currency();
        List<Doc> left = new ArrayList<>(receipts(customerId, cur));
        List<Doc> ars = receivables(customerId, cur);
        left.addAll(ars.stream().filter(d -> "RED".equals(d.kind)).toList());
        List<Doc> right = ars.stream().filter(d -> "BLUE".equals(d.kind)).toList();
        return new Candidates(customerId, FinSupport.customerName(c), cur, left.stream().map(VerificationService::candidate).toList(),
                right.stream().map(VerificationService::candidate).toList());
    }

    /** 付款核销候选：左侧未核销付款、预付、红字应付；右侧未核销蓝字应付 */
    public Candidates paymentCandidates(Long supplierId, String currency) {
        SupplierDTO s = support.supplier(supplierId);
        String cur = currency != null ? currency : s.currency();
        List<Doc> left = new ArrayList<>(payments(supplierId, cur));
        List<Doc> aps = payables(supplierId, cur);
        left.addAll(aps.stream().filter(d -> "RED".equals(d.kind)).toList());
        List<Doc> right = aps.stream().filter(d -> "BLUE".equals(d.kind)).toList();
        return new Candidates(supplierId, s.name(), cur, left.stream().map(VerificationService::candidate).toList(),
                right.stream().map(VerificationService::candidate).toList());
    }

    private static Candidate candidate(Doc d) {
        BigDecimal signed = d.credit() && !"RED".equals(d.kind) || "BLUE".equals(d.kind) ? d.available : d.available.negate();
        return new Candidate(d.type, d.id, d.no, d.kind, d.date, d.dueDate, d.orderId, d.orderNo, d.sourceNo, d.total, signed, d.rate, d.remark);
    }

    private List<Doc> receipts(Long customerId, String currency) {
        return receiptMapper.selectList(new LambdaQueryWrapper<FinReceiptDO>().eq(FinReceiptDO::getCustomerId, customerId)
                        .eq(FinReceiptDO::getCurrency, currency).eq(FinReceiptDO::getReceiptStatus, CashStatus.CONFIRMED.name())
                        .orderByAsc(FinReceiptDO::getReceiptDate).orderByAsc(FinReceiptDO::getId))
                .stream().map(VerificationService::doc).filter(d -> d.available.signum() > 0).toList();
    }

    private List<Doc> receivables(Long customerId, String currency) {
        List<FinReceivableDO> list = receivableMapper.selectList(new LambdaQueryWrapper<FinReceivableDO>().eq(FinReceivableDO::getCustomerId, customerId)
                .eq(FinReceivableDO::getCurrency, currency).eq(FinReceivableDO::getArStatus, ArStatus.CONFIRMED.name())
                .apply("verified_amount <> total_amount").orderByAsc(FinReceivableDO::getDueDate).orderByAsc(FinReceivableDO::getId));
        Map<Long, Set<Long>> orders = orderIds(list.stream().map(FinReceivableDO::getId).toList());
        return list.stream().map(r -> {
            Doc d = doc(r);
            d.orderIds.addAll(orders.getOrDefault(r.getId(), Set.of()));
            return d;
        }).filter(d -> d.available.signum() > 0).toList();
    }

    private Map<Long, Set<Long>> orderIds(List<Long> arIds) {
        return receivableLineMapper.selectByParents(arIds).stream().filter(l -> l.getOrderId() != null)
                .collect(Collectors.groupingBy(FinReceivableLineDO::getReceivableId, Collectors.mapping(FinReceivableLineDO::getOrderId, Collectors.toSet())));
    }

    private List<Doc> payments(Long supplierId, String currency) {
        return paymentMapper.selectList(new LambdaQueryWrapper<FinPaymentDO>().eq(FinPaymentDO::getSupplierId, supplierId)
                        .eq(FinPaymentDO::getCurrency, currency).eq(FinPaymentDO::getPaymentStatus, CashStatus.CONFIRMED.name())
                        .orderByAsc(FinPaymentDO::getPayDate).orderByAsc(FinPaymentDO::getId))
                .stream().map(VerificationService::doc).filter(d -> d.available.signum() > 0).toList();
    }

    private List<Doc> payables(Long supplierId, String currency) {
        return payableMapper.selectList(new LambdaQueryWrapper<FinPayableDO>().eq(FinPayableDO::getSupplierId, supplierId)
                        .eq(FinPayableDO::getCurrency, currency).eq(FinPayableDO::getApStatus, ArStatus.CONFIRMED.name())
                        .apply("verified_amount <> total_amount").orderByAsc(FinPayableDO::getDueDate).orderByAsc(FinPayableDO::getId))
                .stream().map(VerificationService::doc).filter(d -> d.available.signum() > 0).toList();
    }

    // ==================== 单据 → 核销参与方 ====================

    static Doc doc(FinReceiptDO r) {
        Doc d = new Doc();
        d.type = DOC_RECEIPT;
        d.id = r.getId();
        d.no = r.getDocNo();
        d.kind = "REFUND".equals(r.getReceiptType()) ? "REFUND" : "ADVANCE".equals(r.getReceiptType()) ? "ADVANCE" : "RECEIPT";
        d.partnerId = r.getCustomerId();
        d.currency = r.getCurrency();
        d.rate = r.getExchangeRate();
        d.available = receiptOpen(r).abs();
        d.date = r.getReceiptDate();
        d.orderId = r.getOrderId();
        d.orderNo = r.getOrderNo();
        d.total = receiptGross(r);
        d.remark = r.getRemark();
        return d;
    }

    /** 客户付款金额 = 到账 + 手续费（退款不含手续费） */
    public static BigDecimal receiptGross(FinReceiptDO r) {
        BigDecimal amount = FinSupport.nz(r.getAmount());
        return amount.signum() > 0 ? amount.add(FinSupport.nz(r.getBankFee())) : amount;
    }

    /** 未核销金额 = 到账 + 手续费 − 已核销（退款为负） */
    public static BigDecimal receiptOpen(FinReceiptDO r) {
        return receiptGross(r).subtract(FinSupport.nz(r.getAllocatedAmount()));
    }

    static Doc doc(FinReceivableDO r) {
        Doc d = new Doc();
        d.type = DOC_RECEIVABLE;
        d.id = r.getId();
        d.no = r.getDocNo();
        d.kind = FinSupport.nz(r.getTotalAmount()).signum() < 0 ? "RED" : "BLUE";
        d.partnerId = r.getCustomerId();
        d.currency = r.getCurrency();
        d.rate = r.getExchangeRate();
        d.available = FinSupport.nz(r.getTotalAmount()).subtract(FinSupport.nz(r.getVerifiedAmount())).abs();
        d.date = r.getBizDate();
        d.dueDate = r.getDueDate();
        d.orderId = r.getOrderId();
        if (r.getOrderId() != null) d.orderIds.add(r.getOrderId());
        d.sourceNo = r.getSourceNo();
        d.total = r.getTotalAmount();
        d.remark = r.getDescription();
        return d;
    }

    static Doc doc(FinPaymentDO p) {
        Doc d = new Doc();
        d.type = DOC_PAYMENT;
        d.id = p.getId();
        d.no = p.getDocNo();
        d.kind = "PREPAYMENT".equals(p.getRequestType()) ? "PREPAY" : "PAYMENT";
        d.partnerId = p.getSupplierId();
        d.currency = p.getCurrency();
        d.rate = p.getExchangeRate();
        d.available = FinSupport.nz(p.getAmount()).subtract(FinSupport.nz(p.getAllocatedAmount())).abs();
        d.date = p.getPayDate();
        d.orderId = p.getOrderId();
        d.sourceNo = p.getRequestNo();
        d.total = p.getAmount();
        d.remark = p.getRemark();
        return d;
    }

    static Doc doc(FinPayableDO p) {
        Doc d = new Doc();
        d.type = DOC_PAYABLE;
        d.id = p.getId();
        d.no = p.getDocNo();
        d.kind = FinSupport.nz(p.getTotalAmount()).signum() < 0 ? "RED" : "BLUE";
        d.partnerId = p.getSupplierId();
        d.currency = p.getCurrency();
        d.rate = p.getExchangeRate();
        d.available = FinSupport.nz(p.getTotalAmount()).subtract(FinSupport.nz(p.getVerifiedAmount())).abs();
        d.date = p.getBizDate();
        d.dueDate = p.getDueDate();
        d.sourceNo = p.getStatementNo();
        d.total = p.getTotalAmount();
        d.remark = p.getDescription();
        return d;
    }

    private Doc load(String type, Long id) {
        return switch (type) {
            case DOC_RECEIPT -> {
                FinReceiptDO r = receiptMapper.selectById(id);
                if (r == null || !CashStatus.CONFIRMED.name().equals(r.getReceiptStatus())) throw BizException.of(FinanceErrorCodes.NOT_EXISTS, "已确认的收款单");
                yield doc(r);
            }
            case DOC_RECEIVABLE -> {
                FinReceivableDO r = receivableMapper.selectById(id);
                if (r == null || !ArStatus.CONFIRMED.name().equals(r.getArStatus())) throw BizException.of(FinanceErrorCodes.NOT_EXISTS, "已确认的应收单");
                Doc d = doc(r);
                d.orderIds.addAll(orderIds(List.of(id)).getOrDefault(id, Set.of()));
                yield d;
            }
            case DOC_PAYMENT -> {
                FinPaymentDO p = paymentMapper.selectById(id);
                if (p == null || !CashStatus.CONFIRMED.name().equals(p.getPaymentStatus())) throw BizException.of(FinanceErrorCodes.NOT_EXISTS, "已确认的付款单");
                yield doc(p);
            }
            case DOC_PAYABLE -> {
                FinPayableDO p = payableMapper.selectById(id);
                if (p == null || !ArStatus.CONFIRMED.name().equals(p.getApStatus())) throw BizException.of(FinanceErrorCodes.NOT_EXISTS, "已确认的应付单");
                yield doc(p);
            }
            default -> throw BizException.of(FinanceErrorCodes.NOT_EXISTS, "单据类型 " + type);
        };
    }

    // ==================== 核销 ====================

    /** 收款核销（FIN-RV-R02 ~ R04、R07） */
    @Transactional(rollbackFor = Exception.class)
    public VerifyResult verifyReceipt(VerifyReq req) {
        return verify(CUSTOMER, req);
    }

    /** 付款 / 预付核销（FIN-PAY-R04、12-05 第 3.3 节） */
    @Transactional(rollbackFor = Exception.class)
    public VerifyResult verifyPayment(VerifyReq req) {
        return verify(SUPPLIER, req);
    }

    /** 自动核销建议：按到期日先后把左侧金额分配到应收 / 应付（预收优先冲对应订单），返回右侧金额，不保存 */
    public List<Pick> auto(String partnerType, VerifyReq req) {
        Candidates c = CUSTOMER.equals(partnerType) ? receiptCandidates(req.partnerId(), req.currency()) : paymentCandidates(req.partnerId(), req.currency());
        List<Doc> credits = new ArrayList<>();
        List<Doc> debits = new ArrayList<>();
        for (Pick p : req.left() == null ? List.<Pick>of() : req.left()) {
            Doc d = load(p.docType(), p.docId());
            d.remaining = p.amount() == null ? d.available : p.amount().abs().min(d.available);
            (d.credit() ? credits : debits).add(d);
        }
        List<Long> chosen = req.right() == null ? List.of() : req.right().stream().map(Pick::docId).toList();
        for (Candidate x : c.right()) {
            if (!chosen.isEmpty() && !chosen.contains(x.docId())) continue;
            Doc d = load(x.docType(), x.docId());
            d.remaining = d.available;
            debits.add(d);
        }
        Map<String, BigDecimal> used = new LinkedHashMap<>();
        for (Pair p : pair(credits, debits, anyOrder())) {
            if (!"BLUE".equals(p.debit().kind)) continue;
            used.merge(p.debit().type + ":" + p.debit().id, p.amount(), BigDecimal::add);
        }
        List<Pick> result = new ArrayList<>();
        used.forEach((k, v) -> {
            String[] s = k.split(":");
            result.add(new Pick(s[0], Long.valueOf(s[1]), v));
        });
        return result;
    }

    private boolean anyOrder() {
        return support.params().getBool(FinanceModuleConfig.P_ADVANCE_ANY_ORDER);
    }

    private VerifyResult verify(String partnerType, VerifyReq req) {
        List<Pick> picks = new ArrayList<>();
        if (req.left() != null) picks.addAll(req.left());
        if (req.right() != null) picks.addAll(req.right());
        picks = picks.stream().filter(p -> p.amount() != null && p.amount().signum() != 0).toList();
        if (picks.isEmpty()) throw new BizException(FinanceErrorCodes.VF_NOTHING);
        List<Doc> credits = new ArrayList<>();
        List<Doc> debits = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Pick p : picks) {
            if (!seen.add(p.docType() + ":" + p.docId())) continue;
            boolean customerDoc = DOC_RECEIPT.equals(p.docType()) || DOC_RECEIVABLE.equals(p.docType());
            if (customerDoc != CUSTOMER.equals(partnerType)) throw BizException.of(FinanceErrorCodes.VF_PARTNER_MISMATCH, partner(partnerType));
            Doc d = load(p.docType(), p.docId());
            if (!Objects.equals(d.partnerId, req.partnerId()) || !Objects.equals(d.currency, req.currency())) {
                throw BizException.of(FinanceErrorCodes.VF_PARTNER_MISMATCH, partner(partnerType));
            }
            BigDecimal amt = Decimals.amount(p.amount().abs());
            if (amt.compareTo(d.available) > 0) throw BizException.of(FinanceErrorCodes.VF_EXCEED, d.no, FinSupport.plain(d.available));
            d.remaining = amt;
            (d.credit() ? credits : debits).add(d);
        }
        BigDecimal creditSum = credits.stream().map(d -> d.remaining).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal debitSum = debits.stream().map(d -> d.remaining).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (creditSum.compareTo(debitSum) != 0 || creditSum.signum() == 0) throw new BizException(FinanceErrorCodes.RV_UNBALANCED);
        List<Pair> pairs = pair(credits, debits, anyOrder());
        for (Doc d : credits) {
            if (d.remaining.signum() == 0) continue;
            if ("ADVANCE".equals(d.kind)) throw BizException.of(FinanceErrorCodes.VF_ADVANCE_ORDER, Objects.toString(d.orderNo, ""));
            throw new BizException(FinanceErrorCodes.RV_UNBALANCED);
        }
        for (Doc d : debits) {
            if (d.remaining.signum() != 0) throw new BizException("REFUND".equals(d.kind) ? FinanceErrorCodes.VF_REFUND : FinanceErrorCodes.RV_UNBALANCED);
        }
        return persist(partnerType, req.partnerId(), req.currency(), pairs);
    }

    private static String partner(String partnerType) {
        return CUSTOMER.equals(partnerType) ? "客户" : "供应商";
    }

    /**
     * 配对：退款先冲红字应收再冲收款；蓝字单据按传入顺序，依次用预收（同订单）、收款 / 付款、红字冲销
     */
    static List<Pair> pair(List<Doc> credits, List<Doc> debits, boolean anyOrder) {
        List<Pair> pairs = new ArrayList<>();
        List<Doc> ordered = new ArrayList<>(debits.stream().filter(d -> "REFUND".equals(d.kind)).toList());
        ordered.addAll(debits.stream().filter(d -> !"REFUND".equals(d.kind)).toList());
        for (Doc debit : ordered) {
            List<Doc> pool = new ArrayList<>(credits);
            if ("REFUND".equals(debit.kind)) {
                pool.sort(Comparator.comparingInt(c -> "RED".equals(c.kind) ? 0 : 1));
            } else {
                pool.sort(Comparator.comparingInt(c -> switch (c.kind) {
                    case "ADVANCE", "PREPAY" -> 0;
                    case "RED" -> 2;
                    default -> 1;
                }));
            }
            for (Doc credit : pool) {
                if (debit.remaining.signum() == 0) break;
                if (credit.remaining.signum() == 0 || !compatible(credit, debit, anyOrder)) continue;
                BigDecimal amt = credit.remaining.min(debit.remaining);
                credit.remaining = credit.remaining.subtract(amt);
                debit.remaining = debit.remaining.subtract(amt);
                pairs.add(new Pair(credit, debit, amt));
            }
        }
        return pairs;
    }

    private static boolean compatible(Doc credit, Doc debit, boolean anyOrder) {
        if ("REFUND".equals(debit.kind)) return "RED".equals(credit.kind) || "RECEIPT".equals(credit.kind) || "ADVANCE".equals(credit.kind);
        if ("ADVANCE".equals(credit.kind) && !anyOrder && credit.orderId != null) return debit.orderIds.contains(credit.orderId);
        return true;
    }

    private VerifyResult persist(String partnerType, Long partnerId, String currency, List<Pair> pairs) {
        LocalDate today = LocalDate.now();
        String period = FinSupport.periodOf(today);
        if (support.isClosed(period)) throw BizException.of(FinanceErrorCodes.PERIOD_CLOSED, period);
        String batch = FinSupport.batchNo();
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal fx = BigDecimal.ZERO;
        for (Pair p : pairs) {
            FinVerificationDO v = new FinVerificationDO();
            v.setVerifyType(verifyType(p));
            v.setBatchNo(batch);
            v.setPartnerType(partnerType);
            v.setPartnerId(partnerId);
            v.setCurrency(currency);
            boolean refund = "REFUND".equals(p.debit().kind);
            Doc a = refund ? p.debit() : p.credit();
            Doc b = refund ? p.credit() : p.debit();
            v.setDocAType(a.type);
            v.setDocAId(a.id);
            v.setDocANo(a.no);
            v.setDocBType(b.type);
            v.setDocBId(b.id);
            v.setDocBNo(b.no);
            v.setOrderId(p.credit().orderId != null ? p.credit().orderId : p.debit().orderId);
            v.setAmount(p.amount());
            v.setAmountBaseA(FinSupport.toBase(p.amount(), a.rate));
            v.setAmountBaseB(FinSupport.toBase(p.amount(), b.rate));
            BigDecimal creditBase = FinSupport.toBase(p.amount(), p.credit().rate);
            BigDecimal debitBase = FinSupport.toBase(p.amount(), p.debit().rate);
            v.setFxDiff(creditBase.subtract(debitBase));
            v.setPeriod(period);
            v.setVerifiedAt(LocalDateTime.now());
            v.setOperatorId(support.currentUser());
            v.setReversed(false);
            mapper.insert(v);
            apply(p.credit(), p.amount(), 1);
            apply(p.debit(), p.amount(), 1);
            writeback(v, p.credit(), p.debit(), 1);
            total = total.add(p.amount());
            fx = fx.add(v.getFxDiff());
        }
        if (CUSTOMER.equals(partnerType)) support.balanceChanged(List.of(partnerId));
        return new VerifyResult(batch, pairs.size(), total, fx);
    }

    private static String verifyType(Pair p) {
        Doc c = p.credit();
        Doc d = p.debit();
        if (DOC_PAYMENT.equals(c.type)) return "PREPAY".equals(c.kind) ? "PREPAY_AP" : "PAYMENT_AP";
        if (DOC_PAYABLE.equals(c.type)) return "RED_BLUE_AP";
        if ("REFUND".equals(d.kind)) return "RED".equals(c.kind) ? "RECEIPT_AR" : "ADVANCE_AR";
        if ("RED".equals(c.kind)) return "RED_BLUE_AR";
        return "ADVANCE".equals(c.kind) ? "ADVANCE_AR" : "RECEIPT_AR";
    }

    /** 更新双方已核销金额（与单据同号）；sign = −1 反核销 */
    private void apply(Doc d, BigDecimal amount, int sign) {
        BigDecimal delta = amount.multiply(BigDecimal.valueOf(sign));
        switch (d.type) {
            case DOC_RECEIPT -> {
                FinReceiptDO r = receiptMapper.selectById(d.id);
                BigDecimal signed = FinSupport.nz(r.getAmount()).signum() < 0 ? delta.negate() : delta;
                r.setAllocatedAmount(Decimals.amount(FinSupport.nz(r.getAllocatedAmount()).add(signed)));
                receiptMapper.updateByIdOrFail(r);
            }
            case DOC_RECEIVABLE -> {
                FinReceivableDO r = receivableMapper.selectById(d.id);
                BigDecimal signed = FinSupport.nz(r.getTotalAmount()).signum() < 0 ? delta.negate() : delta;
                r.setVerifiedAmount(Decimals.amount(FinSupport.nz(r.getVerifiedAmount()).add(signed)));
                receivableMapper.updateByIdOrFail(r);
            }
            case DOC_PAYMENT -> {
                FinPaymentDO p = paymentMapper.selectById(d.id);
                p.setAllocatedAmount(Decimals.amount(FinSupport.nz(p.getAllocatedAmount()).add(delta)));
                paymentMapper.updateByIdOrFail(p);
            }
            case DOC_PAYABLE -> {
                FinPayableDO p = payableMapper.selectById(d.id);
                BigDecimal signed = FinSupport.nz(p.getTotalAmount()).signum() < 0 ? delta.negate() : delta;
                p.setVerifiedAmount(Decimals.amount(FinSupport.nz(p.getVerifiedAmount()).add(signed)));
                payableMapper.updateByIdOrFail(p);
            }
            default -> {
            }
        }
    }

    /**
     * FIN-RV-R04：收款（非预收、非退款）核销蓝字应收 → 按应收明细的订单占比回写销售回款计划、发布 ReceiptAllocatedEvent；
     * 预收在收款确认时已回写，冲销时不再发布
     */
    private void writeback(FinVerificationDO v, Doc credit, Doc debit, int sign) {
        if (!DOC_RECEIPT.equals(credit.type) || !"RECEIPT".equals(credit.kind) || !DOC_RECEIVABLE.equals(debit.type) || !"BLUE".equals(debit.kind)) return;
        FinReceiptDO r = receiptMapper.selectById(credit.id);
        Map<Long, BigDecimal> byOrder = new LinkedHashMap<>();
        for (FinReceivableLineDO l : receivableLineMapper.selectByParent(debit.id)) {
            if (l.getOrderId() != null) byOrder.merge(l.getOrderId(), FinSupport.nz(l.getTotalAmount()), BigDecimal::add);
        }
        BigDecimal base = byOrder.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (base.signum() == 0) return;
        BigDecimal amount = v.getAmount().multiply(BigDecimal.valueOf(sign));
        BigDecimal allocated = BigDecimal.ZERO;
        List<Long> orders = new ArrayList<>(byOrder.keySet());
        for (int i = 0; i < orders.size(); i++) {
            Long orderId = orders.get(i);
            BigDecimal share = i == orders.size() - 1 ? amount.subtract(allocated)
                    : amount.multiply(byOrder.get(orderId)).divide(base, 2, RoundingMode.HALF_UP);
            allocated = allocated.add(share);
            if (share.signum() == 0) continue;
            writebackApi.onReceiptAllocated(orderId, share, r.getReceiptDate());
            support.events().publish(new ReceiptAllocatedEvent(r.getId(), r.getDocNo(), r.getCustomerId(), orderId, r.getCurrency(), share, r.getReceiptDate()));
        }
    }

    // ==================== 付款确认自动核销（付款服务调用） ====================

    /** 付款确认：按申请行把付款核销到应付（FIN-PAY-R04） */
    public void verifyPaymentToPayables(Long paymentId, Map<Long, BigDecimal> amounts) {
        Doc credit = load(DOC_PAYMENT, paymentId);
        List<Pair> pairs = new ArrayList<>();
        for (Map.Entry<Long, BigDecimal> e : amounts.entrySet()) {
            if (e.getValue() == null || e.getValue().signum() <= 0) continue;
            Doc debit = load(DOC_PAYABLE, e.getKey());
            BigDecimal amt = e.getValue().min(debit.available);
            if (amt.signum() > 0) pairs.add(new Pair(credit, debit, amt));
        }
        if (!pairs.isEmpty()) persist(SUPPLIER, credit.partnerId, credit.currency, pairs);
    }

    // ==================== 反核销 ====================

    /** FIN-RV-R05：核销所在会计期间未结账；反向更新并发布事件 */
    @Transactional(rollbackFor = Exception.class)
    public void reverse(Long id) {
        FinVerificationDO v = mapper.selectById(id);
        if (v == null || Boolean.TRUE.equals(v.getReversed())) throw BizException.of(FinanceErrorCodes.NOT_EXISTS, "核销记录");
        if (support.isClosed(v.getPeriod())) throw new BizException(FinanceErrorCodes.VF_PERIOD_CLOSED);
        Doc a = raw(v.getDocAType(), v.getDocAId());
        Doc b = raw(v.getDocBType(), v.getDocBId());
        boolean refund = "REFUND".equals(a.kind);
        Doc credit = refund ? b : a;
        Doc debit = refund ? a : b;
        apply(credit, v.getAmount(), -1);
        apply(debit, v.getAmount(), -1);
        writeback(v, credit, debit, -1);
        v.setReversed(true);
        v.setReversedAt(LocalDateTime.now());
        v.setReversedBy(support.currentUser());
        mapper.updateByIdOrFail(v);
        if (CUSTOMER.equals(v.getPartnerType())) support.balanceChanged(List.of(v.getPartnerId()));
    }

    /** 反核销单据的全部核销记录（付款反确认） */
    public void reverseByDoc(String docType, Long docId) {
        for (FinVerificationDO v : active(docType, docId)) reverse(v.getId());
    }

    private Doc raw(String type, Long id) {
        return switch (type) {
            case DOC_RECEIPT -> doc(receiptMapper.selectById(id));
            case DOC_RECEIVABLE -> doc(receivableMapper.selectById(id));
            case DOC_PAYMENT -> doc(paymentMapper.selectById(id));
            default -> doc(payableMapper.selectById(id));
        };
    }

    // ==================== 查询 ====================

    public List<FinVerificationDO> active(String docType, Long docId) {
        return mapper.selectList(new LambdaQueryWrapper<FinVerificationDO>().eq(FinVerificationDO::getReversed, false)
                .and(w -> w.and(x -> x.eq(FinVerificationDO::getDocAType, docType).eq(FinVerificationDO::getDocAId, docId))
                        .or(x -> x.eq(FinVerificationDO::getDocBType, docType).eq(FinVerificationDO::getDocBId, docId))));
    }

    public List<VerificationVO> listByDoc(String docType, Long docId) {
        List<FinVerificationDO> list = mapper.selectList(new LambdaQueryWrapper<FinVerificationDO>()
                .and(w -> w.and(x -> x.eq(FinVerificationDO::getDocAType, docType).eq(FinVerificationDO::getDocAId, docId))
                        .or(x -> x.eq(FinVerificationDO::getDocBType, docType).eq(FinVerificationDO::getDocBId, docId)))
                .orderByDesc(FinVerificationDO::getVerifiedAt).orderByDesc(FinVerificationDO::getId));
        return vos(list);
    }

    public List<VerificationVO> list(String partnerType, Long partnerId, LocalDate from, LocalDate to, Boolean includeReversed, int limit) {
        List<FinVerificationDO> list = mapper.selectList(new LambdaQueryWrapper<FinVerificationDO>()
                .eq(partnerType != null, FinVerificationDO::getPartnerType, partnerType)
                .eq(partnerId != null, FinVerificationDO::getPartnerId, partnerId)
                .ge(from != null, FinVerificationDO::getVerifiedAt, from == null ? null : from.atStartOfDay())
                .lt(to != null, FinVerificationDO::getVerifiedAt, to == null ? null : to.plusDays(1).atStartOfDay())
                .eq(!Boolean.TRUE.equals(includeReversed), FinVerificationDO::getReversed, false)
                .orderByDesc(FinVerificationDO::getVerifiedAt).orderByDesc(FinVerificationDO::getId)
                .last("LIMIT " + Math.max(1, Math.min(limit, 2000))));
        return vos(list);
    }

    private List<VerificationVO> vos(List<FinVerificationDO> list) {
        Map<Long, UserDTO> users = support.users(list.stream().map(FinVerificationDO::getOperatorId).toList());
        return list.stream().map(v -> new VerificationVO(v.getId(), v.getVerifyType(), v.getBatchNo(), v.getCurrency(), v.getDocAType(), v.getDocAId(),
                v.getDocANo(), v.getDocBType(), v.getDocBId(), v.getDocBNo(), v.getAmount(), v.getAmountBaseA(), v.getAmountBaseB(), v.getFxDiff(),
                v.getPeriod(), v.getVerifiedAt(), FinSupport.name(users, v.getOperatorId()), Boolean.TRUE.equals(v.getReversed()), v.getReversedAt())).toList();
    }

    /** 订单号（预收候选显示） */
    public Map<Long, String> orderNos(Set<Long> orderIds) {
        if (orderIds.isEmpty()) return Map.of();
        return orderQueryApi.getOrderHeaders(orderIds).values().stream().collect(Collectors.toMap(SalesOrderHeaderDTO::orderId, SalesOrderHeaderDTO::orderNo));
    }
}
