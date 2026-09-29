package com.erp.module.finance.service.ap;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.common.exception.BizException;
import com.erp.common.result.PageResult;
import com.erp.common.util.Decimals;
import com.erp.module.engineering.api.material.MaterialDTO;
import com.erp.module.finance.api.FinanceErrorCodes;
import com.erp.module.finance.config.FinanceModuleConfig;
import com.erp.module.finance.controller.vo.ApVOs.ApDetail;
import com.erp.module.finance.controller.vo.ApVOs.ApLineVO;
import com.erp.module.finance.controller.vo.ApVOs.ApQuery;
import com.erp.module.finance.controller.vo.ApVOs.ApRow;
import com.erp.module.finance.controller.vo.ApVOs.InvoiceMatchRef;
import com.erp.module.finance.controller.vo.ApVOs.OtherApSave;
import com.erp.module.finance.controller.vo.ApVOs.PayableCandidate;
import com.erp.module.finance.controller.vo.ApVOs.RequestRef;
import com.erp.module.finance.controller.vo.ArVOs.OtherLine;
import com.erp.module.finance.controller.vo.ArVOs.SubmitResult;
import com.erp.module.finance.dal.dataobject.FinPayableDO;
import com.erp.module.finance.dal.dataobject.FinPayableLineDO;
import com.erp.module.finance.dal.dataobject.FinPaymentRequestDO;
import com.erp.module.finance.dal.dataobject.FinPaymentRequestLineDO;
import com.erp.module.finance.dal.dataobject.FinPurchaseInvoiceDO;
import com.erp.module.finance.dal.dataobject.FinPurchaseInvoiceLineDO;
import com.erp.module.finance.dal.mapper.FinPayableLineMapper;
import com.erp.module.finance.dal.mapper.FinPayableMapper;
import com.erp.module.finance.dal.mapper.FinPaymentRequestLineMapper;
import com.erp.module.finance.dal.mapper.FinPaymentRequestMapper;
import com.erp.module.finance.dal.mapper.FinPurchaseInvoiceLineMapper;
import com.erp.module.finance.dal.mapper.FinPurchaseInvoiceMapper;
import com.erp.module.finance.service.ArStatus;
import com.erp.module.finance.service.FinAction;
import com.erp.module.finance.service.FinStateMachines;
import com.erp.module.finance.service.FinSupport;
import com.erp.module.finance.service.verify.VerificationService;
import com.erp.module.purchase.api.statement.PurchaseStatementConfirmedEvent;
import com.erp.module.purchase.api.statement.PurchaseStatementUnconfirmingEvent;
import com.erp.module.purchase.api.supplier.SupplierDTO;
import com.erp.module.purchase.api.supplier.SupplierFinanceDTO;
import com.erp.module.system.api.paymentterm.BaseEvent;
import com.erp.module.system.api.paymentterm.DueNode;
import com.erp.module.system.api.paymentterm.PaymentTermApi;
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
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 应付单（12-04）：对账单确认生成、确认 / 反确认 / 作废、其他应付 */
@Service
public class PayableService {

    public static final String BIZ_TYPE = FinanceModuleConfig.PAYABLE;
    public static final String PURCHASE = "PURCHASE";
    public static final String OUTSOURCE = "OUTSOURCE";
    public static final String OTHER = "OTHER";
    /** 三单匹配价差调整行 */
    public static final String PRICE_DIFF = "PRICE_DIFF";

    private final FinPayableMapper mapper;
    private final FinPayableLineMapper lineMapper;
    private final FinPurchaseInvoiceMapper invoiceMapper;
    private final FinPurchaseInvoiceLineMapper invoiceLineMapper;
    private final FinPaymentRequestMapper requestMapper;
    private final FinPaymentRequestLineMapper requestLineMapper;
    private final PaymentTermApi paymentTermApi;
    private final WorkflowApi workflowApi;
    private final VerificationService verificationService;
    private final FinSupport support;

    public PayableService(FinPayableMapper mapper, FinPayableLineMapper lineMapper, FinPurchaseInvoiceMapper invoiceMapper,
                          FinPurchaseInvoiceLineMapper invoiceLineMapper, FinPaymentRequestMapper requestMapper,
                          FinPaymentRequestLineMapper requestLineMapper, PaymentTermApi paymentTermApi, WorkflowApi workflowApi,
                          VerificationService verificationService, FinSupport support) {
        this.mapper = mapper;
        this.lineMapper = lineMapper;
        this.invoiceMapper = invoiceMapper;
        this.invoiceLineMapper = invoiceLineMapper;
        this.requestMapper = requestMapper;
        this.requestLineMapper = requestLineMapper;
        this.paymentTermApi = paymentTermApi;
        this.workflowApi = workflowApi;
        this.verificationService = verificationService;
        this.support = support;
    }

    // ==================== 对账单事件 ====================

    /** FIN-AP-R01：对账单确认生成应付（行来自对账单明细，含退货负数、扣款）；同一对账单只生成一次 */
    @EventListener
    public void onStatementConfirmed(PurchaseStatementConfirmedEvent e) {
        if (active(e.getStatementId()) != null) return;
        if (e.getLines() == null || e.getLines().isEmpty()) return;
        boolean outsource = e.getLines().stream().allMatch(l -> "PROCESS_FEE".equals(l.lineType()));
        LocalDate bizDate = e.getPeriodTo() != null ? e.getPeriodTo() : LocalDate.now();
        FinPayableDO p = header(outsource ? OUTSOURCE : PURCHASE, e.getSupplierId(), e.getCurrency(), null, bizDate);
        p.setExchangeRate(support.rateOrZero(p.getCurrency(), LocalDate.now()));
        p.setStatementId(e.getStatementId());
        p.setStatementNo(e.getStatementNo());
        p.setSourceType("PUR_STATEMENT");
        p.setSourceId(e.getStatementId());
        p.setSourceNo(e.getStatementNo());
        p.setDescription("对账单 " + e.getStatementNo());
        SupplierDTO s = support.supplierApi().getSupplier(e.getSupplierId()).orElse(null);
        support.fillOwner(p, s == null ? null : s.buyerId());
        mapper.insert(p);
        int no = 1;
        List<FinPayableLineDO> saved = new ArrayList<>();
        for (PurchaseStatementConfirmedEvent.Line l : e.getLines()) {
            FinPayableLineDO x = new FinPayableLineDO();
            x.setPayableId(p.getId());
            x.setLineNo(no++);
            x.setLineType(l.lineType());
            x.setStatementLineId(l.lineId());
            x.setSourceNo(l.sourceNo());
            x.setOrderNo(l.orderNo());
            x.setMaterialId(l.materialId());
            x.setQty(l.qty() == null ? null : Decimals.qty(l.qty()));
            x.setPriceInclTax(l.priceInclTax() == null ? null : Decimals.price(l.priceInclTax()));
            x.setTaxRate(FinSupport.nz(l.taxRate()));
            if (l.totalAmount() != null) {
                x.setTotalAmount(Decimals.amount(l.totalAmount()));
                x.setTaxAmount(Decimals.amount(FinSupport.nz(l.taxAmount())));
                x.setAmount(x.getTotalAmount().subtract(x.getTaxAmount()));
            } else {
                BigDecimal[] sp = FinSupport.split(l.amount(), l.taxRate());
                x.setAmount(sp[0]);
                x.setTaxAmount(sp[1]);
                x.setTotalAmount(sp[2]);
            }
            x.setInvoicedQty(BigDecimal.ZERO);
            x.setInvoicedAmount(BigDecimal.ZERO);
            lineMapper.insert(x);
            saved.add(x);
        }
        totals(p, saved);
        p.setDueDate(dueDate(p.getSupplierId(), bizDate));
        mapper.updateByIdOrFail(p);
        created(p, "对账单 " + e.getStatementNo());
        if (support.params().getBool(FinanceModuleConfig.P_AP_AUTO_CONFIRM) && !support.isClosed(FinSupport.periodOf(bizDate))
                && get(p.getId()).getExchangeRate().signum() != 0) {
            confirmInternal(get(p.getId()), "自动确认");
        }
    }

    /** FIN-AP-R02：对账单取消确认前校验；未处理的应付作废 */
    @EventListener
    public void onStatementUnconfirming(PurchaseStatementUnconfirmingEvent e) {
        FinPayableDO p = active(e.getStatementId());
        if (p == null) return;
        if (processed(p)) throw new BizException(FinanceErrorCodes.AP_BLOCK_UNCONFIRM);
        if (ArStatus.PENDING.name().equals(p.getApStatus())) throw new BizException(FinanceErrorCodes.AP_BLOCK_UNCONFIRM);
        if (ArStatus.CONFIRMED.name().equals(p.getApStatus())) support.requireOpen(p.getBizDate());
        p.setVoidReason("对账单取消确认");
        fire(p, FinAction.VOID, "对账单 " + e.getStatementNo() + " 取消确认");
    }

    private FinPayableDO active(Long statementId) {
        return mapper.selectList(new LambdaQueryWrapper<FinPayableDO>().eq(FinPayableDO::getStatementId, statementId)
                .ne(FinPayableDO::getApStatus, ArStatus.VOIDED.name())).stream().findFirst().orElse(null);
    }

    /** 到期日：供应商付款条件（月结：区间结束月末 + 天数）；无付款条件取业务日期 */
    LocalDate dueDate(Long supplierId, LocalDate bizDate) {
        Long termId = support.supplierApi().getFinanceInfo(supplierId).map(SupplierFinanceDTO::paymentTermId).orElse(null);
        if (termId == null) return bizDate;
        try {
            Map<BaseEvent, LocalDate> events = new EnumMap<>(BaseEvent.class);
            for (BaseEvent be : BaseEvent.values()) events.put(be, bizDate);
            for (DueNode n : paymentTermApi.calcDueDates(termId, BigDecimal.ONE, events)) {
                if (n.dueDate() != null && n.percent() != null && n.percent().signum() > 0) return n.dueDate();
            }
        } catch (BizException ignored) {
            // 付款条件已删除
        }
        return bizDate;
    }

    private FinPayableDO header(String type, Long supplierId, String currency, BigDecimal rate, LocalDate bizDate) {
        FinPayableDO p = new FinPayableDO();
        p.setDocNo(support.nextNo(BIZ_TYPE));
        p.setDocDate(LocalDate.now());
        p.setApType(type);
        p.setSupplierId(supplierId);
        p.setCurrency(StringUtils.hasText(currency) ? currency : support.baseCurrency());
        p.setExchangeRate(rate != null && rate.signum() > 0 ? rate : support.rateOrZero(p.getCurrency(), bizDate));
        p.setBizDate(bizDate);
        p.setAmount(BigDecimal.ZERO);
        p.setTaxAmount(BigDecimal.ZERO);
        p.setTotalAmount(BigDecimal.ZERO);
        p.setTotalAmountBase(BigDecimal.ZERO);
        p.setInvoicedAmount(BigDecimal.ZERO);
        p.setRequestedAmount(BigDecimal.ZERO);
        p.setVerifiedAmount(BigDecimal.ZERO);
        p.setApStatus(ArStatus.DRAFT.name());
        p.setStatus(ArStatus.DRAFT.docStatus());
        return p;
    }

    public static void totals(FinPayableDO p, List<FinPayableLineDO> lines) {
        p.setAmount(lines.stream().map(FinPayableLineDO::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
        p.setTaxAmount(lines.stream().map(FinPayableLineDO::getTaxAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
        p.setTotalAmount(lines.stream().map(FinPayableLineDO::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
        p.setTotalAmountBase(FinSupport.toBase(p.getTotalAmount(), p.getExchangeRate()));
    }

    private void created(FinPayableDO p, String from) {
        support.log(BIZ_TYPE, p.getId(), p.getDocNo(), FinAction.CREATE.name(), FinAction.CREATE.label(), null, p.getApStatus(), from);
    }

    /** 已匹配发票、已申请付款或已付款核销 */
    public static boolean processed(FinPayableDO p) {
        return FinSupport.nz(p.getInvoicedAmount()).signum() != 0 || FinSupport.nz(p.getRequestedAmount()).signum() != 0
                || FinSupport.nz(p.getVerifiedAmount()).signum() != 0;
    }

    /** 可申请金额 = 价税合计 − 已付款 − 已申请未付 */
    public static BigDecimal requestable(FinPayableDO p) {
        return FinSupport.nz(p.getTotalAmount()).subtract(FinSupport.nz(p.getVerifiedAmount())).subtract(FinSupport.nz(p.getRequestedAmount()));
    }

    // ==================== 确认、反确认、作废 ====================

    /** FIN-AP-R06：业务日期所在期间未结账 */
    @Transactional(rollbackFor = Exception.class)
    public void confirm(Long id) {
        FinPayableDO p = get(id);
        if (ArStatus.PENDING.name().equals(p.getApStatus())) throw new BizException(FinanceErrorCodes.DOC_PENDING);
        confirmInternal(p, null);
    }

    private void confirmInternal(FinPayableDO p, String reason) {
        support.requireOpen(p.getBizDate());
        rerate(p);
        p.setConfirmedAt(LocalDateTime.now());
        fire(p, FinAction.CONFIRM, reason);
    }

    /** 生成时汇率未维护（为 0）：确认时取汇率并重算本位币 */
    private void rerate(FinPayableDO p) {
        if (p.getExchangeRate().signum() != 0) return;
        p.setExchangeRate(support.rate(p.getCurrency(), p.getBizDate()));
        p.setTotalAmountBase(FinSupport.toBase(p.getTotalAmount(), p.getExchangeRate()));
    }

    /** FIN-AP-R05：已匹配发票 / 已申请付款 / 已核销的应付不能反确认 */
    @Transactional(rollbackFor = Exception.class)
    public void unconfirm(Long id, String reason) {
        String why = FinSupport.requireText(reason, "反确认原因");
        FinPayableDO p = get(id);
        if (processed(p)) throw BizException.of(FinanceErrorCodes.AP_PROCESSED, "反确认");
        if (p.getVoucherId() != null) throw new BizException(FinanceErrorCodes.VCH_AUDITED);
        support.requireOpen(p.getBizDate());
        p.setConfirmedAt(null);
        fire(p, FinAction.UNCONFIRM, why);
    }

    @Transactional(rollbackFor = Exception.class)
    public void voidDoc(Long id, String reason) {
        String why = FinSupport.requireText(reason, "作废原因");
        FinPayableDO p = get(id);
        if (!ArStatus.DRAFT.name().equals(p.getApStatus())) throw BizException.of(FinanceErrorCodes.STATUS_NOT_ALLOWED, status(p).label(), "作废");
        p.setVoidReason(FinSupport.limit(why, 256));
        fire(p, FinAction.VOID, why);
    }

    private void fire(FinPayableDO p, FinAction action, String reason) {
        ArStatus from = status(p);
        ArStatus to = FinStateMachines.AR.next(from, action)
                .orElseThrow(() -> BizException.of(FinanceErrorCodes.STATUS_NOT_ALLOWED, from.label(), action.label()));
        p.setApStatus(to.name());
        p.setStatus(to.docStatus());
        mapper.updateByIdOrFail(p);
        support.log(BIZ_TYPE, p.getId(), p.getDocNo(), action.name(), action.label(), from.name(), to.name(), reason);
    }

    static ArStatus status(FinPayableDO p) {
        return ArStatus.valueOf(p.getApStatus());
    }

    // ==================== 其他应付 ====================

    @Transactional(rollbackFor = Exception.class)
    public Long createOther(OtherApSave req) {
        support.supplier(req.supplierId());
        support.currencyApi().validate(req.currency());
        FinPayableDO p = header(OUTSOURCE.equals(req.apType()) ? OUTSOURCE : OTHER, req.supplierId(), req.currency(), req.exchangeRate(), req.bizDate());
        fillOther(p, req);
        support.fillOwner(p, null);
        mapper.insert(p);
        saveOtherLines(p, req.lines());
        support.bindFiles(req.fileIds(), BIZ_TYPE, p.getId());
        created(p, "手工新建");
        return p.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateOther(Long id, OtherApSave req) {
        FinPayableDO p = get(id);
        if (p.getStatementId() != null || !ArStatus.DRAFT.name().equals(p.getApStatus())) {
            throw BizException.of(FinanceErrorCodes.STATUS_NOT_ALLOWED, status(p).label(), "修改");
        }
        support.supplier(req.supplierId());
        support.currencyApi().validate(req.currency());
        p.setSupplierId(req.supplierId());
        p.setApType(OUTSOURCE.equals(req.apType()) ? OUTSOURCE : OTHER);
        p.setCurrency(req.currency());
        p.setBizDate(req.bizDate());
        p.setExchangeRate(req.exchangeRate() != null && req.exchangeRate().signum() > 0 ? req.exchangeRate() : BigDecimal.ZERO);
        fillOther(p, req);
        mapper.updateByIdOrFail(p);
        lineMapper.deleteByParent(id);
        saveOtherLines(get(id), req.lines());
        support.bindFiles(req.fileIds(), BIZ_TYPE, id);
    }

    private void fillOther(FinPayableDO p, OtherApSave req) {
        if (p.getExchangeRate() == null || p.getExchangeRate().signum() <= 0) p.setExchangeRate(support.rate(p.getCurrency(), p.getBizDate()));
        p.setDueDate(req.dueDate() != null ? req.dueDate() : req.bizDate());
        p.setDescription(FinSupport.limit(req.description(), 512));
        p.setRemark(FinSupport.trim(req.remark()));
    }

    private void saveOtherLines(FinPayableDO p, List<OtherLine> reqLines) {
        List<OtherLine> lines = reqLines == null ? List.of() : reqLines.stream().filter(l -> l.totalAmount() != null && l.totalAmount().signum() != 0).toList();
        if (lines.isEmpty()) throw new BizException(FinanceErrorCodes.NO_LINES);
        int no = 1;
        List<FinPayableLineDO> saved = new ArrayList<>();
        for (OtherLine l : lines) {
            FinPayableLineDO x = new FinPayableLineDO();
            x.setPayableId(p.getId());
            x.setLineNo(no++);
            x.setLineType("ADJUST");
            x.setDescription(FinSupport.limit(l.description(), 256));
            x.setTaxRate(FinSupport.nz(l.taxRate()));
            BigDecimal[] sp = FinSupport.split(l.totalAmount(), l.taxRate());
            x.setAmount(sp[0]);
            x.setTaxAmount(sp[1]);
            x.setTotalAmount(sp[2]);
            x.setInvoicedQty(BigDecimal.ZERO);
            x.setInvoicedAmount(BigDecimal.ZERO);
            lineMapper.insert(x);
            saved.add(x);
        }
        totals(p, saved);
        mapper.updateByIdOrFail(p);
    }

    /** 其他应付提交审批（FIN_OTHER_PAYABLE）；对账单生成的应付直接确认 */
    @Transactional(rollbackFor = Exception.class)
    public SubmitResult submit(Long id) {
        FinPayableDO p = get(id);
        if (p.getStatementId() != null) throw BizException.of(FinanceErrorCodes.STATUS_NOT_ALLOWED, status(p).label(), "提交");
        support.requireOpen(p.getBizDate());
        fire(p, FinAction.SUBMIT, null);
        Map<String, Object> vars = new HashMap<>();
        vars.put("amountBase", FinSupport.nz(p.getTotalAmountBase()).abs());
        Map<String, Long> users = new HashMap<>();
        if (p.getOwnerId() != null) users.put("ownerId", p.getOwnerId());
        StartResult sr = workflowApi.start(FinanceModuleConfig.OTHER_PAYABLE, id, p.getDocNo(),
                "其他应付 " + p.getDocNo() + "（" + support.supplier(p.getSupplierId()).name() + "）", vars, users, support.currentUser());
        if (!sr.isStarted()) approve(get(id));
        return new SubmitResult(id, get(id).getApStatus());
    }

    @Transactional(rollbackFor = Exception.class)
    public void withdraw(Long id) {
        FinPayableDO p = get(id);
        if (!ArStatus.PENDING.name().equals(p.getApStatus())) throw BizException.of(FinanceErrorCodes.STATUS_NOT_ALLOWED, status(p).label(), "撤回");
        workflowApi.withdraw(FinanceModuleConfig.OTHER_PAYABLE, id, support.currentUser());
        FinPayableDO fresh = get(id);
        if (ArStatus.PENDING.name().equals(fresh.getApStatus())) fire(fresh, FinAction.WITHDRAW, null);
    }

    @EventListener
    public void onApproval(ApprovalCompletedEvent e) {
        if (!FinanceModuleConfig.OTHER_PAYABLE.equals(e.getBizType())) return;
        FinPayableDO p = mapper.selectById(e.getBizId());
        if (p == null || !ArStatus.PENDING.name().equals(p.getApStatus())) return;
        switch (e.getResult()) {
            case APPROVED -> approve(p);
            case WITHDRAWN -> fire(p, FinAction.WITHDRAW, null);
            default -> fire(p, FinAction.REJECT, e.getComment());
        }
    }

    private void approve(FinPayableDO p) {
        support.requireOpen(p.getBizDate());
        rerate(p);
        p.setConfirmedAt(LocalDateTime.now());
        fire(p, FinAction.APPROVE, null);
    }

    // ==================== 查询 ====================

    public FinPayableDO get(Long id) {
        FinPayableDO p = id == null ? null : mapper.selectById(id);
        if (p == null) throw BizException.of(FinanceErrorCodes.NOT_EXISTS, "应付单");
        return p;
    }

    /** 付款申请可选应付：已确认、可申请金额 > 0，按到期日 */
    public List<PayableCandidate> candidates(Long supplierId, String currency) {
        LocalDate today = LocalDate.now();
        return mapper.selectList(new LambdaQueryWrapper<FinPayableDO>().eq(FinPayableDO::getSupplierId, supplierId)
                        .eq(StringUtils.hasText(currency), FinPayableDO::getCurrency, currency)
                        .eq(FinPayableDO::getApStatus, ArStatus.CONFIRMED.name()).gt(FinPayableDO::getTotalAmount, 0)
                        .orderByAsc(FinPayableDO::getDueDate).orderByAsc(FinPayableDO::getId))
                .stream().filter(p -> requestable(p).signum() > 0)
                .map(p -> new PayableCandidate(p.getId(), p.getDocNo(), p.getStatementNo(), p.getBizDate(), p.getDueDate(),
                        p.getDueDate() != null && p.getDueDate().isBefore(today) ? (int) ChronoUnit.DAYS.between(p.getDueDate(), today) : 0,
                        p.getCurrency(), p.getTotalAmount(), p.getInvoicedAmount(), p.getRequestedAmount(), p.getVerifiedAmount(), requestable(p),
                        FinSupport.nz(p.getInvoicedAmount()).compareTo(p.getTotalAmount()) < 0))
                .toList();
    }

    /** 本周（至周日）到期、未付清的应付（FIN-PAY-R07） */
    public List<FinPayableDO> dueBy(LocalDate to) {
        return mapper.selectList(new LambdaQueryWrapper<FinPayableDO>().eq(FinPayableDO::getApStatus, ArStatus.CONFIRMED.name())
                        .le(FinPayableDO::getDueDate, to).gt(FinPayableDO::getTotalAmount, 0).apply("verified_amount < total_amount")
                        .orderByAsc(FinPayableDO::getDueDate))
                .stream().toList();
    }

    public PageResult<ApRow> page(ApQuery q) {
        IPage<FinPayableDO> p = mapper.selectScopedPage(new Page<>(q.getPageNo(), q.getPageSize()), query(q));
        return new PageResult<>(rows(p.getRecords()), p.getTotal());
    }

    public List<ApRow> list(ApQuery q) {
        return rows(mapper.selectScopedList(query(q)));
    }

    public Map<String, BigDecimal> summary(ApQuery q) {
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal unpaid = BigDecimal.ZERO;
        for (FinPayableDO p : mapper.selectScopedList(query(q))) {
            total = total.add(FinSupport.nz(p.getTotalAmountBase()));
            BigDecimal t = FinSupport.nz(p.getTotalAmount());
            if (t.signum() != 0) {
                unpaid = unpaid.add(FinSupport.nz(p.getTotalAmountBase()).multiply(t.subtract(FinSupport.nz(p.getVerifiedAmount())))
                        .divide(t, 2, java.math.RoundingMode.HALF_UP));
            }
        }
        return Map.of("totalAmountBase", total, "unpaidBase", unpaid);
    }

    private LambdaQueryWrapper<FinPayableDO> query(ApQuery q) {
        List<String> types = split(q.getApTypes());
        List<String> statuses = split(q.getStatuses());
        LambdaQueryWrapper<FinPayableDO> w = new LambdaQueryWrapper<FinPayableDO>().eq(FinPayableDO::getDeleted, false)
                .like(StringUtils.hasText(q.getDocNo()), FinPayableDO::getDocNo, q.getDocNo())
                .eq(q.getSupplierId() != null, FinPayableDO::getSupplierId, q.getSupplierId())
                .in(!types.isEmpty(), FinPayableDO::getApType, types)
                .in(!statuses.isEmpty(), FinPayableDO::getApStatus, statuses)
                .ge(q.getBizDateFrom() != null, FinPayableDO::getBizDate, q.getBizDateFrom())
                .le(q.getBizDateTo() != null, FinPayableDO::getBizDate, q.getBizDateTo())
                .like(StringUtils.hasText(q.getStatementNo()), FinPayableDO::getStatementNo, q.getStatementNo())
                .eq(StringUtils.hasText(q.getCurrency()), FinPayableDO::getCurrency, q.getCurrency())
                .ge(q.getDueFrom() != null, FinPayableDO::getDueDate, q.getDueFrom())
                .le(q.getDueTo() != null, FinPayableDO::getDueDate, q.getDueTo());
        state(w, q.getInvoiceState(), "invoiced_amount");
        state(w, q.getPayState(), "verified_amount");
        if (Boolean.TRUE.equals(q.getOverdueOnly())) {
            w.eq(FinPayableDO::getApStatus, ArStatus.CONFIRMED.name()).lt(FinPayableDO::getDueDate, LocalDate.now())
                    .apply("verified_amount <> total_amount").gt(FinPayableDO::getTotalAmount, 0);
        }
        return w.orderByDesc(FinPayableDO::getBizDate).orderByDesc(FinPayableDO::getId);
    }

    private static void state(LambdaQueryWrapper<FinPayableDO> w, String state, String column) {
        if (!StringUtils.hasText(state)) return;
        switch (state) {
            case "NONE" -> w.apply(column + " = 0");
            case "PARTIAL" -> w.apply(column + " <> 0 AND " + column + " <> total_amount");
            case "FULL" -> w.apply(column + " = total_amount");
            case "OPEN" -> w.apply(column + " <> total_amount");
            default -> {
            }
        }
    }

    static List<String> split(String text) {
        return StringUtils.hasText(text) ? Arrays.stream(text.split(",")).map(String::trim).filter(StringUtils::hasText).toList() : List.of();
    }

    private List<ApRow> rows(List<FinPayableDO> list) {
        Map<Long, SupplierDTO> ss = support.suppliers(list.stream().map(FinPayableDO::getSupplierId).toList());
        Map<Long, UserDTO> users = support.users(list.stream().map(FinPayableDO::getOwnerId).toList());
        LocalDate today = LocalDate.now();
        return list.stream().map(p -> {
            BigDecimal unpaid = FinSupport.nz(p.getTotalAmount()).subtract(FinSupport.nz(p.getVerifiedAmount()));
            int overdue = ArStatus.CONFIRMED.name().equals(p.getApStatus()) && p.getDueDate() != null && unpaid.signum() > 0 && p.getDueDate().isBefore(today)
                    ? (int) ChronoUnit.DAYS.between(p.getDueDate(), today) : 0;
            SupplierDTO s = ss.get(p.getSupplierId());
            return new ApRow(p.getId(), p.getDocNo(), p.getApType(), p.getSupplierId(), s == null ? null : s.name(), p.getStatementId(), p.getStatementNo(),
                    p.getBizDate(), p.getCurrency(), p.getExchangeRate(), p.getTotalAmount(), p.getTotalAmountBase(), p.getInvoicedAmount(),
                    p.getRequestedAmount(), p.getVerifiedAmount(), unpaid, requestable(p).max(BigDecimal.ZERO), p.getDueDate(), overdue, p.getApStatus(),
                    FinSupport.name(users, p.getOwnerId()), p.getDescription(), p.getCreatedAt());
        }).toList();
    }

    public ApDetail detail(Long id) {
        FinPayableDO p = get(id);
        List<FinPayableLineDO> lines = lineMapper.selectByParent(id);
        Map<Long, MaterialDTO> ms = support.materials(lines.stream().map(FinPayableLineDO::getMaterialId).toList());
        List<ApLineVO> lineVOs = lines.stream().map(l -> {
            MaterialDTO m = ms.get(l.getMaterialId());
            return new ApLineVO(l.getId(), l.getLineNo(), l.getLineType(), l.getSourceNo(), l.getOrderNo(), l.getMaterialId(), m == null ? null : m.code(),
                    m == null ? null : m.name(), l.getDescription(), l.getQty(), l.getPriceInclTax(), l.getTaxRate(), l.getAmount(), l.getTaxAmount(),
                    l.getTotalAmount(), l.getInvoicedQty(), l.getInvoicedAmount());
        }).toList();
        List<FinPurchaseInvoiceLineDO> ils = invoiceLineMapper.selectList(new LambdaQueryWrapper<FinPurchaseInvoiceLineDO>()
                .eq(FinPurchaseInvoiceLineDO::getPayableId, id));
        Map<Long, FinPurchaseInvoiceDO> invs = ils.isEmpty() ? Map.of() : invoiceMapper.selectBatchIds(ils.stream()
                .map(FinPurchaseInvoiceLineDO::getInvoiceId).distinct().toList()).stream().collect(Collectors.toMap(FinPurchaseInvoiceDO::getId, Function.identity()));
        List<InvoiceMatchRef> invoices = ils.stream().collect(Collectors.groupingBy(FinPurchaseInvoiceLineDO::getInvoiceId)).entrySet().stream()
                .filter(en -> invs.containsKey(en.getKey())).map(en -> {
                    FinPurchaseInvoiceDO inv = invs.get(en.getKey());
                    return new InvoiceMatchRef(inv.getId(), inv.getDocNo(), inv.getInvoiceNo(), inv.getInvoiceDate(), inv.getMatchStatus(),
                            en.getValue().stream().map(x -> FinSupport.nz(x.getQty())).reduce(BigDecimal.ZERO, BigDecimal::add),
                            en.getValue().stream().map(FinPurchaseInvoiceLineDO::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add), inv.getInvoiceStatus());
                }).toList();
        List<FinPaymentRequestLineDO> rls = requestLineMapper.selectList(new LambdaQueryWrapper<FinPaymentRequestLineDO>()
                .eq(FinPaymentRequestLineDO::getPayableId, id));
        Map<Long, FinPaymentRequestDO> reqs = rls.isEmpty() ? Map.of() : requestMapper.selectBatchIds(rls.stream()
                .map(FinPaymentRequestLineDO::getRequestId).distinct().toList()).stream().collect(Collectors.toMap(FinPaymentRequestDO::getId, Function.identity()));
        List<RequestRef> requests = rls.stream().filter(l -> reqs.containsKey(l.getRequestId())).map(l -> {
            FinPaymentRequestDO r = reqs.get(l.getRequestId());
            return new RequestRef(r.getId(), r.getDocNo(), r.getRequestStatus(), l.getAmount(), l.getPaidAmount(), r.getPlanPayDate());
        }).toList();
        return new ApDetail(rows(List.of(p)).get(0), p.getAmount(), p.getTaxAmount(), p.getConfirmedAt(), p.getVoidReason(), p.getVoucherId(),
                p.getRemark(), lineVOs, invoices, requests, verificationService.listByDoc(VerificationService.DOC_PAYABLE, id));
    }
}
