package com.erp.module.finance.service.payment;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.common.exception.BizException;
import com.erp.common.result.PageResult;
import com.erp.common.util.Decimals;
import com.erp.module.finance.api.FinanceErrorCodes;
import com.erp.module.finance.config.FinanceModuleConfig;
import com.erp.module.finance.controller.vo.PayVOs.PaymentDetail;
import com.erp.module.finance.controller.vo.PayVOs.PaymentQuery;
import com.erp.module.finance.controller.vo.PayVOs.PaymentRow;
import com.erp.module.finance.controller.vo.PayVOs.PaymentSave;
import com.erp.module.finance.dal.dataobject.FinBankAccountDO;
import com.erp.module.finance.dal.dataobject.FinPayableDO;
import com.erp.module.finance.dal.dataobject.FinPaymentDO;
import com.erp.module.finance.dal.dataobject.FinPaymentRequestDO;
import com.erp.module.finance.dal.dataobject.FinPaymentRequestLineDO;
import com.erp.module.finance.dal.dataobject.FinVerificationDO;
import com.erp.module.finance.dal.mapper.FinPayableMapper;
import com.erp.module.finance.dal.mapper.FinPaymentMapper;
import com.erp.module.finance.service.CashStatus;
import com.erp.module.finance.service.FinAction;
import com.erp.module.finance.service.FinStateMachines;
import com.erp.module.finance.service.FinSupport;
import com.erp.module.finance.service.RequestStatus;
import com.erp.module.finance.service.setting.SettingService;
import com.erp.module.finance.service.verify.VerificationService;
import com.erp.module.purchase.api.supplier.SupplierDTO;
import com.erp.module.system.api.user.UserDTO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 付款单（12-05 第 3.2 节）：基于已审批的付款申请付款，确认后自动核销申请中的应付；预付款记为预付余额 */
@Service
public class PaymentService {

    public static final String BIZ_TYPE = FinanceModuleConfig.PAYMENT;

    private final FinPaymentMapper mapper;
    private final FinPayableMapper payableMapper;
    private final PaymentRequestService requestService;
    private final SettingService settingService;
    private final VerificationService verificationService;
    private final FinSupport support;

    public PaymentService(FinPaymentMapper mapper, FinPayableMapper payableMapper, PaymentRequestService requestService, SettingService settingService,
                          VerificationService verificationService, FinSupport support) {
        this.mapper = mapper;
        this.payableMapper = payableMapper;
        this.requestService = requestService;
        this.settingService = settingService;
        this.verificationService = verificationService;
        this.support = support;
    }

    // ==================== 保存 ====================

    @Transactional(rollbackFor = Exception.class)
    public Long create(PaymentSave req) {
        FinPaymentRequestDO r = requestService.get(req.requestId());
        FinPaymentDO p = new FinPaymentDO();
        p.setDocNo(support.nextNo(BIZ_TYPE));
        p.setDocDate(LocalDate.now());
        p.setAllocatedAmount(BigDecimal.ZERO);
        p.setPaymentStatus(CashStatus.DRAFT.name());
        p.setStatus(CashStatus.DRAFT.docStatus());
        fill(p, r, req);
        support.fillOwner(p, null);
        mapper.insert(p);
        support.bindFiles(req.fileIds(), BIZ_TYPE, p.getId());
        support.log(BIZ_TYPE, p.getId(), p.getDocNo(), FinAction.CREATE.name(), FinAction.CREATE.label(), null, p.getPaymentStatus(), "付款申请 " + r.getDocNo());
        return p.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, PaymentSave req) {
        FinPaymentDO p = get(id);
        requireDraft(p, "修改");
        fill(p, requestService.get(req.requestId()), req);
        mapper.updateByIdOrFail(p);
        support.bindFiles(req.fileIds(), BIZ_TYPE, id);
    }

    /** FIN-PAY-R03：付款金额 ≤ 申请未付金额；付款账户币别 = 申请币别 */
    private void fill(FinPaymentDO p, FinPaymentRequestDO r, PaymentSave req) {
        requireApproved(r);
        FinBankAccountDO bank = settingService.bank(req.bankAccountId());
        if (!bank.getCurrency().equals(r.getCurrency())) throw new BizException(FinanceErrorCodes.PAY_CURRENCY_MISMATCH);
        if (req.amount() == null || req.amount().signum() <= 0) throw BizException.of(FinanceErrorCodes.AMOUNT_INVALID, "付款金额");
        BigDecimal amount = Decimals.amount(req.amount());
        if (amount.compareTo(unpaid(r)) > 0) throw new BizException(FinanceErrorCodes.PAY_AMOUNT_EXCEED);
        BigDecimal rate = r.getCurrency().equals(support.baseCurrency()) ? BigDecimal.ONE
                : req.exchangeRate() != null && req.exchangeRate().signum() > 0 ? req.exchangeRate() : support.rate(r.getCurrency(), req.payDate());
        p.setRequestId(r.getId());
        p.setRequestNo(r.getDocNo());
        p.setRequestType(r.getRequestType());
        p.setSupplierId(r.getSupplierId());
        p.setOrderId(r.getOrderId());
        p.setBankAccountId(bank.getId());
        p.setSettlementMethod(req.settlementMethod());
        p.setPayDate(req.payDate());
        p.setCurrency(r.getCurrency());
        p.setExchangeRate(rate);
        p.setAmount(amount);
        p.setBankFee(Decimals.amount(FinSupport.nz(req.bankFee()).abs()));
        p.setAmountBase(FinSupport.toBase(amount, rate));
        p.setBankRefNo(FinSupport.limit(req.bankRefNo(), 64));
        p.setRemark(FinSupport.trim(req.remark()));
    }

    private static void requireApproved(FinPaymentRequestDO r) {
        if (!RequestStatus.APPROVED.name().equals(r.getRequestStatus()) && !RequestStatus.PARTIAL.name().equals(r.getRequestStatus())) {
            throw new BizException(FinanceErrorCodes.PAY_REQUEST_NOT_APPROVED);
        }
    }

    static BigDecimal unpaid(FinPaymentRequestDO r) {
        return FinSupport.nz(r.getAmount()).subtract(FinSupport.nz(r.getPaidAmount()));
    }

    private static void requireDraft(FinPaymentDO p, String action) {
        if (!CashStatus.DRAFT.name().equals(p.getPaymentStatus())) {
            throw BizException.of(FinanceErrorCodes.STATUS_NOT_ALLOWED, CashStatus.valueOf(p.getPaymentStatus()).label(), action);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        FinPaymentDO p = get(id);
        requireDraft(p, "删除");
        mapper.deleteById(id);
        support.log(BIZ_TYPE, id, p.getDocNo(), "DELETE", "删除", p.getPaymentStatus(), null, null);
    }

    @Transactional(rollbackFor = Exception.class)
    public void voidDoc(Long id, String reason) {
        String why = FinSupport.requireText(reason, "作废原因");
        fire(get(id), FinAction.VOID, why);
    }

    // ==================== 确认、反确认 ====================

    /** FIN-PAY-R04：按申请行顺序分配到应付并自动核销；更新申请已付金额与状态 */
    @Transactional(rollbackFor = Exception.class)
    public void confirm(Long id) {
        FinPaymentDO p = get(id);
        FinPaymentRequestDO r = requestService.get(p.getRequestId());
        requireApproved(r);
        if (p.getAmount().compareTo(unpaid(r)) > 0) throw new BizException(FinanceErrorCodes.PAY_AMOUNT_EXCEED);
        support.requireOpen(p.getPayDate());
        p.setConfirmedAt(LocalDateTime.now());
        fire(p, FinAction.CONFIRM, null);
        if (PaymentRequestService.PAYABLE.equals(r.getRequestType())) {
            BigDecimal remaining = p.getAmount();
            Map<Long, BigDecimal> alloc = new LinkedHashMap<>();
            for (FinPaymentRequestLineDO l : requestService.lines(r.getId())) {
                if (remaining.signum() == 0) break;
                BigDecimal open = l.getAmount().subtract(FinSupport.nz(l.getPaidAmount()));
                BigDecimal x = remaining.min(open);
                if (x.signum() <= 0) continue;
                l.setPaidAmount(Decimals.amount(FinSupport.nz(l.getPaidAmount()).add(x)));
                requestService.updateLine(l);
                FinPayableDO ap = payableMapper.selectById(l.getPayableId());
                ap.setRequestedAmount(Decimals.amount(FinSupport.nz(ap.getRequestedAmount()).subtract(x)));
                payableMapper.updateByIdOrFail(ap);
                alloc.merge(l.getPayableId(), x, BigDecimal::add);
                remaining = remaining.subtract(x);
            }
            verificationService.verifyPaymentToPayables(id, alloc);
        }
        requestService.onPaid(requestService.get(r.getId()), p.getAmount(), p.getDocNo());
    }

    /** FIN-PAY-R05：期间未结账；反核销后回到草稿（预付已用于冲销应付时需先反核销） */
    @Transactional(rollbackFor = Exception.class)
    public void unconfirm(Long id, String reason) {
        String why = FinSupport.requireText(reason, "反确认原因");
        FinPaymentDO p = get(id);
        if (!CashStatus.CONFIRMED.name().equals(p.getPaymentStatus())) {
            throw BizException.of(FinanceErrorCodes.STATUS_NOT_ALLOWED, CashStatus.valueOf(p.getPaymentStatus()).label(), "反确认");
        }
        if (p.getVoucherId() != null) throw new BizException(FinanceErrorCodes.VCH_AUDITED);
        support.requireOpen(p.getPayDate());
        List<FinVerificationDO> vs = verificationService.active(VerificationService.DOC_PAYMENT, id);
        if (vs.stream().anyMatch(v -> !"PAYMENT_AP".equals(v.getVerifyType()))) throw new BizException(FinanceErrorCodes.PAY_VERIFIED);
        FinPaymentRequestDO r = requestService.get(p.getRequestId());
        boolean closed = RequestStatus.CLOSED.name().equals(r.getRequestStatus());
        List<FinPaymentRequestLineDO> lines = requestService.lines(r.getId());
        for (FinVerificationDO v : vs) {
            verificationService.reverse(v.getId());
            BigDecimal amt = v.getAmount();
            for (FinPaymentRequestLineDO l : lines) {
                if (amt.signum() == 0) break;
                if (!l.getPayableId().equals(v.getDocBId()) || FinSupport.nz(l.getPaidAmount()).signum() == 0) continue;
                BigDecimal x = amt.min(l.getPaidAmount());
                l.setPaidAmount(Decimals.amount(l.getPaidAmount().subtract(x)));
                requestService.updateLine(l);
                amt = amt.subtract(x);
                if (!closed) {
                    FinPayableDO ap = payableMapper.selectById(l.getPayableId());
                    ap.setRequestedAmount(Decimals.amount(FinSupport.nz(ap.getRequestedAmount()).add(x)));
                    payableMapper.updateByIdOrFail(ap);
                }
            }
        }
        p = get(id);
        p.setConfirmedAt(null);
        fire(p, FinAction.UNCONFIRM, why);
        requestService.onPaid(requestService.get(r.getId()), p.getAmount().negate(), p.getDocNo());
    }

    private void fire(FinPaymentDO p, FinAction action, String reason) {
        CashStatus from = CashStatus.valueOf(p.getPaymentStatus());
        CashStatus to = FinStateMachines.CASH.next(from, action)
                .orElseThrow(() -> BizException.of(FinanceErrorCodes.STATUS_NOT_ALLOWED, from.label(), action.label()));
        p.setPaymentStatus(to.name());
        p.setStatus(to.docStatus());
        mapper.updateByIdOrFail(p);
        support.log(BIZ_TYPE, p.getId(), p.getDocNo(), action.name(), action.label(), from.name(), to.name(), reason);
    }

    // ==================== 查询 ====================

    public FinPaymentDO get(Long id) {
        FinPaymentDO p = id == null ? null : mapper.selectById(id);
        if (p == null) throw BizException.of(FinanceErrorCodes.NOT_EXISTS, "付款单");
        return p;
    }

    public PageResult<PaymentRow> page(PaymentQuery q) {
        IPage<FinPaymentDO> p = mapper.selectScopedPage(new Page<>(q.getPageNo(), q.getPageSize()), query(q));
        return new PageResult<>(rows(p.getRecords()), p.getTotal());
    }

    public List<PaymentRow> list(PaymentQuery q) {
        return rows(mapper.selectScopedList(query(q)));
    }

    private LambdaQueryWrapper<FinPaymentDO> query(PaymentQuery q) {
        List<String> statuses = StringUtils.hasText(q.getStatuses())
                ? Arrays.stream(q.getStatuses().split(",")).map(String::trim).filter(StringUtils::hasText).toList() : List.of();
        return new LambdaQueryWrapper<FinPaymentDO>().eq(FinPaymentDO::getDeleted, false)
                .like(StringUtils.hasText(q.getDocNo()), FinPaymentDO::getDocNo, q.getDocNo())
                .like(StringUtils.hasText(q.getRequestNo()), FinPaymentDO::getRequestNo, q.getRequestNo())
                .eq(q.getSupplierId() != null, FinPaymentDO::getSupplierId, q.getSupplierId())
                .eq(q.getBankAccountId() != null, FinPaymentDO::getBankAccountId, q.getBankAccountId())
                .ge(q.getDateFrom() != null, FinPaymentDO::getPayDate, q.getDateFrom())
                .le(q.getDateTo() != null, FinPaymentDO::getPayDate, q.getDateTo())
                .in(!statuses.isEmpty(), FinPaymentDO::getPaymentStatus, statuses)
                .orderByDesc(FinPaymentDO::getPayDate).orderByDesc(FinPaymentDO::getId);
    }

    private List<PaymentRow> rows(List<FinPaymentDO> list) {
        Map<Long, SupplierDTO> ss = support.suppliers(list.stream().map(FinPaymentDO::getSupplierId).toList());
        Map<Long, UserDTO> users = support.users(list.stream().map(FinPaymentDO::getOwnerId).toList());
        Map<Long, FinBankAccountDO> banks = settingService.banks(list.stream().map(FinPaymentDO::getBankAccountId).toList());
        return list.stream().map(p -> {
            SupplierDTO s = ss.get(p.getSupplierId());
            FinBankAccountDO b = banks.get(p.getBankAccountId());
            return new PaymentRow(p.getId(), p.getDocNo(), p.getRequestId(), p.getRequestNo(), p.getRequestType(), p.getSupplierId(), s == null ? null : s.name(),
                    p.getBankAccountId(), b == null ? null : b.getName(), p.getSettlementMethod(), p.getPayDate(), p.getCurrency(), p.getExchangeRate(),
                    p.getAmount(), p.getBankFee(), p.getAmountBase(), p.getAllocatedAmount(), p.getBankRefNo(), p.getPaymentStatus(),
                    FinSupport.name(users, p.getOwnerId()), p.getRemark(), p.getCreatedAt());
        }).toList();
    }

    public PaymentDetail detail(Long id) {
        FinPaymentDO p = get(id);
        return new PaymentDetail(rows(List.of(p)).get(0), p.getConfirmedAt(), p.getVoucherId(), verificationService.listByDoc(VerificationService.DOC_PAYMENT, id));
    }

    public boolean bankUsed(Long bankAccountId) {
        return mapper.selectCount(new LambdaQueryWrapper<FinPaymentDO>().eq(FinPaymentDO::getBankAccountId, bankAccountId)) > 0;
    }
}
