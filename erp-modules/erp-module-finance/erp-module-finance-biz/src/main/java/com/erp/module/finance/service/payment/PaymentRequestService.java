package com.erp.module.finance.service.payment;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.common.exception.BizException;
import com.erp.common.result.PageResult;
import com.erp.common.util.Decimals;
import com.erp.module.finance.api.FinanceErrorCodes;
import com.erp.module.finance.config.FinanceModuleConfig;
import com.erp.module.finance.controller.vo.PayVOs.PaymentRef;
import com.erp.module.finance.controller.vo.PayVOs.PurchaseOrderOption;
import com.erp.module.finance.controller.vo.PayVOs.RequestDetail;
import com.erp.module.finance.controller.vo.PayVOs.RequestLineReq;
import com.erp.module.finance.controller.vo.PayVOs.RequestLineVO;
import com.erp.module.finance.controller.vo.PayVOs.RequestQuery;
import com.erp.module.finance.controller.vo.PayVOs.RequestResult;
import com.erp.module.finance.controller.vo.PayVOs.RequestRow;
import com.erp.module.finance.controller.vo.PayVOs.RequestSave;
import com.erp.module.finance.controller.vo.PayVOs.SupplierBankOption;
import com.erp.module.finance.dal.dataobject.FinPayableDO;
import com.erp.module.finance.dal.dataobject.FinPaymentDO;
import com.erp.module.finance.dal.dataobject.FinPaymentRequestDO;
import com.erp.module.finance.dal.dataobject.FinPaymentRequestLineDO;
import com.erp.module.finance.dal.mapper.FinPayableMapper;
import com.erp.module.finance.dal.mapper.FinPaymentMapper;
import com.erp.module.finance.dal.mapper.FinPaymentRequestLineMapper;
import com.erp.module.finance.dal.mapper.FinPaymentRequestMapper;
import com.erp.module.finance.service.ArStatus;
import com.erp.module.finance.service.FinAction;
import com.erp.module.finance.service.FinStateMachines;
import com.erp.module.finance.service.FinSupport;
import com.erp.module.finance.service.RequestStatus;
import com.erp.module.finance.service.ap.PayableService;
import com.erp.module.purchase.api.order.PurchaseOrderHeaderDTO;
import com.erp.module.purchase.api.order.PurchaseQueryApi;
import com.erp.module.purchase.api.supplier.SupplierDTO;
import com.erp.module.purchase.api.supplier.SupplierFinanceDTO;
import com.erp.module.system.api.user.UserDTO;
import com.erp.module.system.api.workflow.ApprovalCompletedEvent;
import com.erp.module.system.api.workflow.StartResult;
import com.erp.module.system.api.workflow.WorkflowApi;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 付款申请（12-05 第 3.1 节）：按应付 / 预付款申请，提交后占用应付可申请金额，审批后由出纳付款 */
@Service
public class PaymentRequestService {

    public static final String BIZ_TYPE = FinanceModuleConfig.PAYMENT_REQUEST;
    public static final String PAYABLE = "PAYABLE";
    public static final String PREPAYMENT = "PREPAYMENT";

    private final FinPaymentRequestMapper mapper;
    private final FinPaymentRequestLineMapper lineMapper;
    private final FinPayableMapper payableMapper;
    private final FinPaymentMapper paymentMapper;
    private final PurchaseQueryApi purchaseQueryApi;
    private final WorkflowApi workflowApi;
    private final FinSupport support;

    public PaymentRequestService(FinPaymentRequestMapper mapper, FinPaymentRequestLineMapper lineMapper, FinPayableMapper payableMapper,
                                 FinPaymentMapper paymentMapper, PurchaseQueryApi purchaseQueryApi, WorkflowApi workflowApi, FinSupport support) {
        this.mapper = mapper;
        this.lineMapper = lineMapper;
        this.payableMapper = payableMapper;
        this.paymentMapper = paymentMapper;
        this.purchaseQueryApi = purchaseQueryApi;
        this.workflowApi = workflowApi;
        this.support = support;
    }

    // ==================== 选项 ====================

    public List<SupplierBankOption> supplierBanks(Long supplierId) {
        return support.supplierApi().getFinanceInfo(supplierId).map(SupplierFinanceDTO::banks).orElse(List.of()).stream()
                .map(b -> new SupplierBankOption(b.id(), b.bankName(), b.accountName(), b.accountNo(), b.swift(), b.currency(), b.isDefault())).toList();
    }

    /** 预付款可选采购订单：可预付 = 订单价税合计 − 已预付 */
    public List<PurchaseOrderOption> orderOptions(Long supplierId) {
        return purchaseQueryApi.getOpenOrders(supplierId).stream().map(o -> {
            BigDecimal prepaid = prepaid(o.id(), null);
            return new PurchaseOrderOption(o.id(), o.docNo(), o.docDate(), o.currency(), o.totalAmount(), prepaid,
                    FinSupport.nz(o.totalAmount()).subtract(prepaid).max(BigDecimal.ZERO));
        }).toList();
    }

    /** 订单累计预付：已提交的预付申请金额（已关闭的取已付金额） */
    BigDecimal prepaid(Long orderId, Long excludeId) {
        return mapper.selectList(new LambdaQueryWrapper<FinPaymentRequestDO>().eq(FinPaymentRequestDO::getRequestType, PREPAYMENT)
                        .eq(FinPaymentRequestDO::getOrderId, orderId).ne(excludeId != null, FinPaymentRequestDO::getId, excludeId)
                        .notIn(FinPaymentRequestDO::getRequestStatus, RequestStatus.DRAFT.name(), RequestStatus.VOIDED.name()))
                .stream().map(r -> RequestStatus.CLOSED.name().equals(r.getRequestStatus()) ? FinSupport.nz(r.getPaidAmount()) : r.getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // ==================== 保存 ====================

    @Transactional(rollbackFor = Exception.class)
    public RequestResult create(RequestSave req) {
        FinPaymentRequestDO r = new FinPaymentRequestDO();
        r.setDocNo(support.nextNo(BIZ_TYPE));
        r.setDocDate(LocalDate.now());
        r.setPaidAmount(BigDecimal.ZERO);
        r.setRequestStatus(RequestStatus.DRAFT.name());
        r.setStatus(RequestStatus.DRAFT.docStatus());
        List<String> warnings = new ArrayList<>();
        List<RequestLineReq> lines = fill(r, req, warnings);
        support.fillOwner(r, null);
        mapper.insert(r);
        saveLines(r, lines);
        support.bindFiles(req.fileIds(), BIZ_TYPE, r.getId());
        support.log(BIZ_TYPE, r.getId(), r.getDocNo(), FinAction.CREATE.name(), FinAction.CREATE.label(), null, r.getRequestStatus(),
                warnings.isEmpty() ? null : String.join("；", warnings));
        return new RequestResult(r.getId(), r.getRequestStatus(), warnings);
    }

    @Transactional(rollbackFor = Exception.class)
    public RequestResult update(Long id, RequestSave req) {
        FinPaymentRequestDO r = get(id);
        if (!RequestStatus.DRAFT.name().equals(r.getRequestStatus())) throw BizException.of(FinanceErrorCodes.STATUS_NOT_ALLOWED, status(r).label(), "修改");
        List<String> warnings = new ArrayList<>();
        List<RequestLineReq> lines = fill(r, req, warnings);
        mapper.updateByIdOrFail(r);
        lineMapper.deleteByParent(id);
        saveLines(r, lines);
        support.bindFiles(req.fileIds(), BIZ_TYPE, id);
        return new RequestResult(id, r.getRequestStatus(), warnings);
    }

    /** FIN-PAY-R01 / R02 / R06 */
    private List<RequestLineReq> fill(FinPaymentRequestDO r, RequestSave req, List<String> warnings) {
        SupplierDTO s = support.supplier(req.supplierId());
        SupplierFinanceDTO fin = support.supplierApi().getFinanceInfo(req.supplierId()).orElse(null);
        support.currencyApi().validate(req.currency());
        if (!PAYABLE.equals(req.requestType()) && !PREPAYMENT.equals(req.requestType())) throw BizException.of(FinanceErrorCodes.REASON_REQUIRED, "申请类型");
        r.setSupplierId(s.id());
        r.setRequestType(req.requestType());
        r.setCurrency(req.currency());
        r.setPlanPayDate(req.planPayDate());
        r.setReason(FinSupport.limit(req.reason(), 512));
        r.setRemark(FinSupport.trim(req.remark()));
        SupplierFinanceDTO.Bank bank = fin == null || req.supplierBankId() == null ? null
                : fin.banks().stream().filter(b -> b.id().equals(req.supplierBankId())).findFirst().orElse(null);
        if (bank == null) throw BizException.of(FinanceErrorCodes.REASON_REQUIRED, "收款账户");
        r.setSupplierBankId(bank.id());
        r.setSupplierBankText(FinSupport.limit(String.join(" / ", List.of(Objects.toString(bank.bankName(), ""), Objects.toString(bank.accountName(), ""),
                Objects.toString(bank.accountNo(), ""))), 256));
        List<RequestLineReq> lines = List.of();
        boolean uninvoiced = false;
        if (PAYABLE.equals(req.requestType())) {
            lines = req.lines() == null ? List.of() : req.lines().stream().filter(l -> l.amount() != null && l.amount().signum() > 0).toList();
            if (lines.isEmpty()) throw new BizException(FinanceErrorCodes.NO_LINES);
            BigDecimal total = BigDecimal.ZERO;
            for (RequestLineReq l : lines) {
                FinPayableDO p = payableMapper.selectById(l.payableId());
                check(p, r, l.amount());
                if (FinSupport.nz(p.getInvoicedAmount()).compareTo(p.getTotalAmount()) < 0) {
                    if (!support.params().getBool(FinanceModuleConfig.P_ALLOW_UNINVOICED)) throw BizException.of(FinanceErrorCodes.PAY_UNINVOICED, p.getDocNo());
                    uninvoiced = true;
                    warnings.add("应付「" + p.getDocNo() + "」尚未收到发票");
                }
                total = total.add(Decimals.amount(l.amount()));
            }
            r.setAmount(total);
            r.setOrderId(null);
            r.setOrderNo(null);
        } else {
            if (req.orderId() == null) throw new BizException(FinanceErrorCodes.PAY_ORDER_REQUIRED);
            PurchaseOrderHeaderDTO o = purchaseQueryApi.getOrderHeader(req.orderId()).orElseThrow(() -> BizException.of(FinanceErrorCodes.NOT_EXISTS, "采购订单"));
            if (!o.supplierId().equals(s.id()) || !o.currency().equals(req.currency())) throw BizException.of(FinanceErrorCodes.PAY_SUPPLIER_MISMATCH, o.docNo());
            if (req.amount() == null || req.amount().signum() <= 0) throw BizException.of(FinanceErrorCodes.AMOUNT_INVALID, "申请金额");
            BigDecimal amount = Decimals.amount(req.amount());
            if (amount.add(prepaid(o.id(), r.getId())).compareTo(FinSupport.nz(o.totalAmount())) > 0) throw new BizException(FinanceErrorCodes.PAY_PREPAY_EXCEED);
            r.setAmount(amount);
            r.setOrderId(o.id());
            r.setOrderNo(o.docNo());
        }
        r.setUninvoicedWarning(uninvoiced);
        r.setAmountBase(FinSupport.toBase(r.getAmount(), support.rate(r.getCurrency(), r.getPlanPayDate())));
        return lines;
    }

    /** 应付属于该供应商、同币别、已确认；申请金额 ≤ 可申请金额 */
    private void check(FinPayableDO p, FinPaymentRequestDO r, BigDecimal amount) {
        if (p == null) throw BizException.of(FinanceErrorCodes.NOT_EXISTS, "应付单");
        if (!p.getSupplierId().equals(r.getSupplierId()) || !p.getCurrency().equals(r.getCurrency())) {
            throw BizException.of(FinanceErrorCodes.PAY_SUPPLIER_MISMATCH, p.getDocNo());
        }
        BigDecimal available = ArStatus.CONFIRMED.name().equals(p.getApStatus()) ? PayableService.requestable(p).max(BigDecimal.ZERO) : BigDecimal.ZERO;
        if (Decimals.amount(amount).compareTo(available) > 0) throw BizException.of(FinanceErrorCodes.PAY_REQUEST_EXCEED, p.getDocNo(), FinSupport.plain(available));
    }

    private void saveLines(FinPaymentRequestDO r, List<RequestLineReq> lines) {
        int no = 1;
        for (RequestLineReq l : lines) {
            FinPayableDO p = payableMapper.selectById(l.payableId());
            FinPaymentRequestLineDO x = new FinPaymentRequestLineDO();
            x.setRequestId(r.getId());
            x.setLineNo(no++);
            x.setPayableId(p.getId());
            x.setPayableNo(p.getDocNo());
            x.setAmount(Decimals.amount(l.amount()));
            x.setPaidAmount(BigDecimal.ZERO);
            lineMapper.insert(x);
        }
    }

    // ==================== 提交、审批、关闭、作废 ====================

    /** 提交：重新校验并占用应付可申请金额；审批流 FIN_PAYMENT_REQUEST（未配置时直接通过） */
    @Transactional(rollbackFor = Exception.class)
    public RequestResult submit(Long id) {
        FinPaymentRequestDO r = get(id);
        List<FinPaymentRequestLineDO> lines = lineMapper.selectByParent(id);
        if (PAYABLE.equals(r.getRequestType())) {
            for (FinPaymentRequestLineDO l : lines) check(payableMapper.selectById(l.getPayableId()), r, l.getAmount());
        } else if (FinSupport.nz(r.getAmount()).add(prepaid(r.getOrderId(), id))
                .compareTo(purchaseQueryApi.getOrderHeader(r.getOrderId()).map(PurchaseOrderHeaderDTO::totalAmount).orElse(BigDecimal.ZERO)) > 0) {
            throw new BizException(FinanceErrorCodes.PAY_PREPAY_EXCEED);
        }
        fire(r, FinAction.SUBMIT, null);
        reserve(lines, 1);
        Map<String, Object> vars = new HashMap<>();
        vars.put("amountBase", FinSupport.nz(r.getAmountBase()));
        vars.put("hasPrepayment", PREPAYMENT.equals(r.getRequestType()));
        vars.put("supplierLevel", support.supplierApi().getFinanceInfo(r.getSupplierId()).map(SupplierFinanceDTO::supplierLevel).orElse(null));
        Map<String, Long> users = new HashMap<>();
        if (r.getOwnerId() != null) users.put("ownerId", r.getOwnerId());
        StartResult sr = workflowApi.start(BIZ_TYPE, id, r.getDocNo(), "付款申请 " + r.getDocNo() + "（" + support.supplier(r.getSupplierId()).name() + " "
                + r.getCurrency() + " " + FinSupport.plain(r.getAmount()) + "）", vars, users, support.currentUser());
        if (!sr.isStarted()) approve(get(id));
        FinPaymentRequestDO fresh = get(id);
        return new RequestResult(id, fresh.getRequestStatus(), Boolean.TRUE.equals(fresh.getUninvoicedWarning()) ? List.of("含尚未收到发票的应付") : List.of());
    }

    /** 占用 / 释放应付已申请金额 */
    private void reserve(List<FinPaymentRequestLineDO> lines, int sign) {
        for (FinPaymentRequestLineDO l : lines) {
            BigDecimal amt = l.getAmount().subtract(FinSupport.nz(l.getPaidAmount()));
            if (amt.signum() == 0) continue;
            FinPayableDO p = payableMapper.selectById(l.getPayableId());
            p.setRequestedAmount(Decimals.amount(FinSupport.nz(p.getRequestedAmount()).add(amt.multiply(BigDecimal.valueOf(sign)))));
            payableMapper.updateByIdOrFail(p);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void withdraw(Long id) {
        FinPaymentRequestDO r = get(id);
        if (!RequestStatus.PENDING.name().equals(r.getRequestStatus())) throw BizException.of(FinanceErrorCodes.STATUS_NOT_ALLOWED, status(r).label(), "撤回");
        workflowApi.withdraw(BIZ_TYPE, id, support.currentUser());
        FinPaymentRequestDO fresh = get(id);
        if (RequestStatus.PENDING.name().equals(fresh.getRequestStatus())) {
            reserve(lineMapper.selectByParent(id), -1);
            fire(fresh, FinAction.WITHDRAW, null);
        }
    }

    @EventListener
    public void onApproval(ApprovalCompletedEvent e) {
        if (!BIZ_TYPE.equals(e.getBizType())) return;
        FinPaymentRequestDO r = mapper.selectById(e.getBizId());
        if (r == null || !RequestStatus.PENDING.name().equals(r.getRequestStatus())) return;
        switch (e.getResult()) {
            case APPROVED -> approve(r);
            case WITHDRAWN -> {
                reserve(lineMapper.selectByParent(r.getId()), -1);
                fire(r, FinAction.WITHDRAW, null);
            }
            default -> {
                reserve(lineMapper.selectByParent(r.getId()), -1);
                fire(r, FinAction.REJECT, e.getComment());
            }
        }
    }

    private void approve(FinPaymentRequestDO r) {
        r.setApprovedAt(LocalDateTime.now());
        fire(r, FinAction.APPROVE, null);
        support.message(support.usersWithPermission("fin:payment:create"), "付款申请待付款：" + r.getDocNo(),
                "付款申请 " + r.getDocNo() + " 已审批，计划付款日 " + r.getPlanPayDate() + "，金额 " + r.getCurrency() + " " + FinSupport.plain(r.getAmount()),
                "/finance/payment/request/" + r.getId());
    }

    /** 关闭：部分付款后剩余不再支付，释放应付占用 */
    @Transactional(rollbackFor = Exception.class)
    public void close(Long id, String reason) {
        String why = FinSupport.requireText(reason, "关闭原因");
        FinPaymentRequestDO r = get(id);
        if (paymentMapper.selectCount(new LambdaQueryWrapper<FinPaymentDO>().eq(FinPaymentDO::getRequestId, id)
                .eq(FinPaymentDO::getPaymentStatus, "DRAFT")) > 0) {
            throw BizException.of(FinanceErrorCodes.STATUS_NOT_ALLOWED, "有草稿付款单", "关闭");
        }
        fire(r, FinAction.CLOSE, why);
        reserve(lineMapper.selectByParent(id), -1);
    }

    @Transactional(rollbackFor = Exception.class)
    public void voidDoc(Long id, String reason) {
        String why = FinSupport.requireText(reason, "作废原因");
        fire(get(id), FinAction.VOID, why);
    }

    // ==================== 付款回写（付款服务调用） ====================

    /** 付款确认 / 反确认：更新申请已付金额与状态（已关闭的申请只更新金额） */
    public void onPaid(FinPaymentRequestDO r, BigDecimal delta, String paymentNo) {
        r.setPaidAmount(Decimals.amount(FinSupport.nz(r.getPaidAmount()).add(delta)));
        if (RequestStatus.CLOSED.name().equals(r.getRequestStatus())) {
            mapper.updateByIdOrFail(r);
            return;
        }
        FinAction action;
        if (delta.signum() > 0) action = r.getPaidAmount().compareTo(r.getAmount()) >= 0 ? FinAction.PAY_ALL : FinAction.PAY;
        else action = r.getPaidAmount().signum() == 0 ? FinAction.UNPAY_ALL : FinAction.UNPAY;
        fire(r, action, "付款单 " + paymentNo + " " + FinSupport.plain(delta));
    }

    private void fire(FinPaymentRequestDO r, FinAction action, String reason) {
        RequestStatus from = status(r);
        RequestStatus to = FinStateMachines.REQUEST.next(from, action)
                .orElseThrow(() -> BizException.of(FinanceErrorCodes.STATUS_NOT_ALLOWED, from.label(), action.label()));
        r.setRequestStatus(to.name());
        r.setStatus(to.docStatus());
        mapper.updateByIdOrFail(r);
        support.log(BIZ_TYPE, r.getId(), r.getDocNo(), action.name(), action.label(), from.name(), to.name(), reason);
    }

    static RequestStatus status(FinPaymentRequestDO r) {
        return RequestStatus.valueOf(r.getRequestStatus());
    }

    // ==================== 查询 ====================

    public FinPaymentRequestDO get(Long id) {
        FinPaymentRequestDO r = id == null ? null : mapper.selectById(id);
        if (r == null) throw BizException.of(FinanceErrorCodes.NOT_EXISTS, "付款申请");
        return r;
    }

    public List<FinPaymentRequestLineDO> lines(Long id) {
        return lineMapper.selectByParent(id);
    }

    public void updateLine(FinPaymentRequestLineDO l) {
        lineMapper.updateByIdOrFail(l);
    }

    public PageResult<RequestRow> page(RequestQuery q) {
        IPage<FinPaymentRequestDO> p = mapper.selectScopedPage(new Page<>(q.getPageNo(), q.getPageSize()), query(q));
        return new PageResult<>(rows(p.getRecords()), p.getTotal());
    }

    public List<RequestRow> list(RequestQuery q) {
        return rows(mapper.selectScopedList(query(q)));
    }

    private LambdaQueryWrapper<FinPaymentRequestDO> query(RequestQuery q) {
        List<String> statuses = new ArrayList<>();
        if (StringUtils.hasText(q.getStatuses())) {
            for (String s : q.getStatuses().split(",")) {
                if ("TO_PAY".equals(s.trim())) {
                    statuses.add(RequestStatus.APPROVED.name());
                    statuses.add(RequestStatus.PARTIAL.name());
                } else if (StringUtils.hasText(s)) statuses.add(s.trim());
            }
        }
        LocalDate from = q.getPlanFrom();
        LocalDate to = q.getPlanTo();
        if (Boolean.TRUE.equals(q.getThisWeek())) {
            LocalDate today = LocalDate.now();
            from = today.with(DayOfWeek.MONDAY);
            to = today.with(DayOfWeek.SUNDAY);
        }
        return new LambdaQueryWrapper<FinPaymentRequestDO>().eq(FinPaymentRequestDO::getDeleted, false)
                .like(StringUtils.hasText(q.getDocNo()), FinPaymentRequestDO::getDocNo, q.getDocNo())
                .eq(q.getSupplierId() != null, FinPaymentRequestDO::getSupplierId, q.getSupplierId())
                .eq(StringUtils.hasText(q.getRequestType()), FinPaymentRequestDO::getRequestType, q.getRequestType())
                .in(!statuses.isEmpty(), FinPaymentRequestDO::getRequestStatus, statuses)
                .ge(from != null, FinPaymentRequestDO::getPlanPayDate, from)
                .le(to != null, FinPaymentRequestDO::getPlanPayDate, to)
                .orderByDesc(FinPaymentRequestDO::getId);
    }

    private List<RequestRow> rows(List<FinPaymentRequestDO> list) {
        Map<Long, SupplierDTO> ss = support.suppliers(list.stream().map(FinPaymentRequestDO::getSupplierId).toList());
        Map<Long, UserDTO> users = support.users(list.stream().map(FinPaymentRequestDO::getOwnerId).toList());
        return list.stream().map(r -> {
            SupplierDTO s = ss.get(r.getSupplierId());
            return new RequestRow(r.getId(), r.getDocNo(), r.getRequestType(), r.getSupplierId(), s == null ? null : s.name(), r.getCurrency(), r.getAmount(),
                    r.getAmountBase(), r.getPaidAmount(), FinSupport.nz(r.getAmount()).subtract(FinSupport.nz(r.getPaidAmount())), r.getPlanPayDate(),
                    r.getOrderId(), r.getOrderNo(), r.getRequestStatus(), Boolean.TRUE.equals(r.getUninvoicedWarning()), FinSupport.name(users, r.getOwnerId()),
                    r.getReason(), r.getCreatedAt());
        }).toList();
    }

    public RequestDetail detail(Long id) {
        FinPaymentRequestDO r = get(id);
        List<FinPaymentRequestLineDO> lines = lineMapper.selectByParent(id);
        Map<Long, FinPayableDO> aps = lines.isEmpty() ? Map.of() : payableMapper.selectBatchIds(lines.stream().map(FinPaymentRequestLineDO::getPayableId)
                .toList()).stream().collect(Collectors.toMap(FinPayableDO::getId, Function.identity()));
        List<RequestLineVO> vos = lines.stream().map(l -> {
            FinPayableDO p = aps.get(l.getPayableId());
            return new RequestLineVO(l.getId(), l.getLineNo(), l.getPayableId(), l.getPayableNo(), p == null ? null : p.getStatementNo(),
                    p == null ? null : p.getDueDate(), p == null ? null : p.getTotalAmount(), l.getAmount(), l.getPaidAmount());
        }).toList();
        List<PaymentRef> payments = paymentMapper.selectList(new LambdaQueryWrapper<FinPaymentDO>().eq(FinPaymentDO::getRequestId, id)
                        .orderByAsc(FinPaymentDO::getId))
                .stream().map(p -> new PaymentRef(p.getId(), p.getDocNo(), p.getPayDate(), p.getAmount(), p.getPaymentStatus())).toList();
        return new RequestDetail(rows(List.of(r)).get(0), r.getSupplierBankId(), r.getSupplierBankText(), r.getApprovedAt(), r.getRemark(), vos, payments);
    }

    /** 付款申请单打印数据 */
    public Map<String, Object> printData(Long id) {
        RequestDetail d = detail(id);
        RequestRow h = d.header();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("docNo", h.docNo());
        m.put("supplierName", h.supplierName());
        m.put("requestTypeName", PREPAYMENT.equals(h.requestType()) ? "预付款" : "按应付付款");
        m.put("currency", h.currency());
        m.put("amount", h.amount());
        m.put("amountInWords", ChineseAmount.of(h.amount()));
        m.put("planPayDate", h.planPayDate());
        m.put("bankText", d.supplierBankText());
        m.put("reason", h.reason());
        m.put("ownerName", h.ownerName());
        m.put("orderNo", h.orderNo());
        m.put("status", RequestStatus.valueOf(h.status()).label());
        m.put("lines", d.lines().stream().map(l -> {
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("payableNo", l.payableNo());
            x.put("statementNo", l.statementNo());
            x.put("dueDate", l.dueDate());
            x.put("amount", l.amount());
            return x;
        }).toList());
        return m;
    }
}
