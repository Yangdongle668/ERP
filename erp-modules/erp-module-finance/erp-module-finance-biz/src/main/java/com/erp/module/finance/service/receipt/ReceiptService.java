package com.erp.module.finance.service.receipt;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.common.exception.BizException;
import com.erp.common.result.PageResult;
import com.erp.common.util.Decimals;
import com.erp.framework.excel.ExcelColumn;
import com.erp.framework.excel.ExcelSupport;
import com.erp.framework.excel.ImportRow;
import com.erp.module.crm.api.customer.CustomerDTO;
import com.erp.module.crm.api.customer.CustomerStatus;
import com.erp.module.finance.api.FinanceErrorCodes;
import com.erp.module.finance.api.receipt.ReceiptAllocatedEvent;
import com.erp.module.finance.config.FinanceModuleConfig;
import com.erp.module.finance.controller.vo.ReceiptVOs.BankImportResult;
import com.erp.module.finance.controller.vo.ReceiptVOs.OrderOption;
import com.erp.module.finance.controller.vo.ReceiptVOs.ReceiptDetail;
import com.erp.module.finance.controller.vo.ReceiptVOs.ReceiptQuery;
import com.erp.module.finance.controller.vo.ReceiptVOs.ReceiptRow;
import com.erp.module.finance.controller.vo.ReceiptVOs.ReceiptSave;
import com.erp.module.finance.controller.vo.ReceiptVOs.UnmatchedRow;
import com.erp.module.finance.dal.dataobject.FinBankAccountDO;
import com.erp.module.finance.dal.dataobject.FinReceiptDO;
import com.erp.module.finance.dal.mapper.FinReceiptMapper;
import com.erp.module.finance.service.CashStatus;
import com.erp.module.finance.service.FinAction;
import com.erp.module.finance.service.FinStateMachines;
import com.erp.module.finance.service.FinSupport;
import com.erp.module.finance.service.voucher.VoucherService;
import com.erp.module.finance.service.setting.SettingService;
import com.erp.module.finance.service.verify.VerificationService;
import com.erp.module.sales.api.order.OpenLineFilter;
import com.erp.module.sales.api.order.SalesOrderHeaderDTO;
import com.erp.module.sales.api.order.SalesOrderLineDTO;
import com.erp.module.sales.api.order.SalesOrderQueryApi;
import com.erp.module.sales.api.order.SalesOrderWritebackApi;
import com.erp.module.system.api.user.UserDTO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 收款单（12-03）：登记、确认 / 反确认（预收回写回款计划）、银行流水导入 */
@Service("finReceiptService")
public class ReceiptService {

    public static final String BIZ_TYPE = FinanceModuleConfig.RECEIPT;
    public static final String ADVANCE = "ADVANCE";
    public static final String REFUND = "REFUND";
    static final Set<String> TYPES = Set.of("SALES", ADVANCE, "OTHER", REFUND);

    /** 银行流水导入模板 */
    public static final List<ExcelColumn<Object>> IMPORT_COLUMNS = List.of(
            ExcelColumn.input("date", "日期", true, "到账日期 yyyy-MM-dd"),
            ExcelColumn.input("amount", "金额", true, "到账金额"),
            ExcelColumn.input("currency", "币别", false, "为空取收款账户币别"),
            ExcelColumn.input("payerName", "付款方名称", true, "按客户名称 / 简称 / 英文名匹配"),
            ExcelColumn.input("bankRefNo", "流水号", false, null));

    private final FinReceiptMapper mapper;
    private final SettingService settingService;
    private final VerificationService verificationService;
    private final SalesOrderQueryApi orderQueryApi;
    private final SalesOrderWritebackApi writebackApi;
    private final FinSupport support;
    private final VoucherService voucherService;

    public ReceiptService(FinReceiptMapper mapper, SettingService settingService, VerificationService verificationService,
                          SalesOrderQueryApi orderQueryApi, SalesOrderWritebackApi writebackApi, FinSupport support, VoucherService voucherService) {
        this.mapper = mapper;
        this.settingService = settingService;
        this.verificationService = verificationService;
        this.orderQueryApi = orderQueryApi;
        this.writebackApi = writebackApi;
        this.support = support;
        this.voucherService = voucherService;
    }

    // ==================== 保存 ====================

    @Transactional(rollbackFor = Exception.class)
    public Long create(ReceiptSave req) {
        FinReceiptDO r = new FinReceiptDO();
        r.setDocNo(support.nextNo(BIZ_TYPE));
        r.setDocDate(LocalDate.now());
        r.setAllocatedAmount(BigDecimal.ZERO);
        r.setReceiptStatus(CashStatus.DRAFT.name());
        r.setStatus(CashStatus.DRAFT.docStatus());
        fill(r, req);
        support.fillOwner(r, null);
        mapper.insert(r);
        support.bindFiles(req.fileIds(), BIZ_TYPE, r.getId());
        support.log(BIZ_TYPE, r.getId(), r.getDocNo(), FinAction.CREATE.name(), FinAction.CREATE.label(), null, r.getReceiptStatus(), null);
        return r.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, ReceiptSave req) {
        FinReceiptDO r = get(id);
        requireDraft(r, "修改");
        fill(r, req);
        mapper.updateByIdOrFail(r);
        support.bindFiles(req.fileIds(), BIZ_TYPE, id);
    }

    /** FIN-RV-R01：币别 = 收款账户币别；汇率 > 0；预收款必须选择订单；退款金额存为负数 */
    private void fill(FinReceiptDO r, ReceiptSave req) {
        if (!TYPES.contains(req.receiptType())) throw BizException.of(FinanceErrorCodes.REASON_REQUIRED, "收款类型");
        CustomerDTO c = support.customer(req.customerId());
        FinBankAccountDO bank = settingService.bank(req.bankAccountId());
        if (req.amount() == null || req.amount().signum() == 0) throw BizException.of(FinanceErrorCodes.AMOUNT_INVALID, "到账金额");
        String currency = bank.getCurrency();
        BigDecimal rate = req.exchangeRate() != null && req.exchangeRate().signum() > 0 ? req.exchangeRate() : support.rate(currency, req.receiptDate());
        if (currency.equals(support.baseCurrency())) rate = BigDecimal.ONE;
        if (rate == null || rate.signum() <= 0) throw new BizException(FinanceErrorCodes.RV_EXCHANGE_RATE);
        r.setCustomerId(c.id());
        r.setReceiptType(req.receiptType());
        r.setBankAccountId(bank.getId());
        r.setSettlementMethod(req.settlementMethod());
        r.setReceiptDate(req.receiptDate());
        r.setCurrency(currency);
        r.setExchangeRate(rate);
        BigDecimal amount = Decimals.amount(req.amount().abs());
        r.setAmount(REFUND.equals(req.receiptType()) ? amount.negate() : amount);
        BigDecimal fee = Decimals.amount(FinSupport.nz(req.bankFee()).abs());
        r.setBankFee(fee);
        r.setAmountBase(FinSupport.toBase(r.getAmount(), rate));
        r.setBankRefNo(FinSupport.limit(req.bankRefNo(), 64));
        r.setPayerName(FinSupport.limit(req.payerName(), 128));
        r.setRemark(FinSupport.trim(req.remark()));
        if (ADVANCE.equals(req.receiptType())) {
            if (req.orderId() == null) throw new BizException(FinanceErrorCodes.RV_ORDER_REQUIRED);
            SalesOrderHeaderDTO o = orderQueryApi.getOrderHeaders(List.of(req.orderId())).get(req.orderId());
            if (o == null || !c.id().equals(o.customerId())) throw BizException.of(FinanceErrorCodes.NOT_EXISTS, "销售订单");
            r.setOrderId(o.orderId());
            r.setOrderNo(o.orderNo());
        } else {
            r.setOrderId(null);
            r.setOrderNo(null);
        }
    }

    private static void requireDraft(FinReceiptDO r, String action) {
        if (!CashStatus.DRAFT.name().equals(r.getReceiptStatus())) {
            throw BizException.of(FinanceErrorCodes.STATUS_NOT_ALLOWED, CashStatus.valueOf(r.getReceiptStatus()).label(), action);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        FinReceiptDO r = get(id);
        requireDraft(r, "删除");
        mapper.deleteById(id);
        support.log(BIZ_TYPE, id, r.getDocNo(), "DELETE", "删除", r.getReceiptStatus(), null, null);
    }

    @Transactional(rollbackFor = Exception.class)
    public void voidDoc(Long id, String reason) {
        String why = FinSupport.requireText(reason, "作废原因");
        FinReceiptDO r = get(id);
        fire(r, FinAction.VOID, why);
    }

    // ==================== 确认、反确认 ====================

    /** 确认后才能核销；预收款立即回写销售回款计划（12-03 第 3.4 节） */
    @Transactional(rollbackFor = Exception.class)
    public void confirm(Long id) {
        FinReceiptDO r = get(id);
        support.requireOpen(r.getReceiptDate());
        r.setConfirmedAt(LocalDateTime.now());
        fire(r, FinAction.CONFIRM, null);
        if (ADVANCE.equals(r.getReceiptType())) advance(r, 1);
        voucherService.autoGenerate(VoucherService.RECEIPT, id);
    }

    /** FIN-RV-R06：有核销记录时不能反确认 */
    @Transactional(rollbackFor = Exception.class)
    public void unconfirm(Long id, String reason) {
        String why = FinSupport.requireText(reason, "反确认原因");
        FinReceiptDO r = get(id);
        if (!verificationService.active(VerificationService.DOC_RECEIPT, id).isEmpty() || FinSupport.nz(r.getAllocatedAmount()).signum() != 0) {
            throw new BizException(FinanceErrorCodes.RV_VERIFIED);
        }
        voucherService.releaseForDoc(r.getVoucherId());
        r.setVoucherId(null);
        support.requireOpen(r.getReceiptDate());
        r.setConfirmedAt(null);
        fire(r, FinAction.UNCONFIRM, why);
        if (ADVANCE.equals(r.getReceiptType())) advance(r, -1);
    }

    private void advance(FinReceiptDO r, int sign) {
        if (r.getOrderId() == null) return;
        BigDecimal amount = VerificationService.receiptGross(r).multiply(BigDecimal.valueOf(sign));
        writebackApi.onReceiptAllocated(r.getOrderId(), amount, r.getReceiptDate());
        support.events().publish(new ReceiptAllocatedEvent(r.getId(), r.getDocNo(), r.getCustomerId(), r.getOrderId(), r.getCurrency(), amount,
                r.getReceiptDate()));
    }

    private void fire(FinReceiptDO r, FinAction action, String reason) {
        CashStatus from = CashStatus.valueOf(r.getReceiptStatus());
        CashStatus to = FinStateMachines.CASH.next(from, action)
                .orElseThrow(() -> BizException.of(FinanceErrorCodes.STATUS_NOT_ALLOWED, from.label(), action.label()));
        r.setReceiptStatus(to.name());
        r.setStatus(to.docStatus());
        mapper.updateByIdOrFail(r);
        support.log(BIZ_TYPE, r.getId(), r.getDocNo(), action.name(), action.label(), from.name(), to.name(), reason);
    }

    // ==================== 银行流水导入 ====================

    /** 按付款方名称匹配客户（名称 / 简称 / 英文名完全一致）生成草稿收款单；无法匹配的行返回由用户手工处理 */
    @Transactional(rollbackFor = Exception.class)
    public BankImportResult importBank(MultipartFile file, Long bankAccountId, String settlementMethod) {
        FinBankAccountDO bank = settingService.bank(bankAccountId);
        List<ImportRow> rows = ExcelSupport.read(file, IMPORT_COLUMNS);
        List<Long> ids = new ArrayList<>();
        List<UnmatchedRow> unmatched = new ArrayList<>();
        Map<String, CustomerDTO> cache = new LinkedHashMap<>();
        for (ImportRow row : rows) {
            LocalDate date;
            BigDecimal amount;
            try {
                date = LocalDate.parse(row.get("date"));
                amount = new BigDecimal(row.get("amount").replace(",", ""));
            } catch (Exception e) {
                unmatched.add(new UnmatchedRow(row.rowNo(), null, null, row.get("payerName"), row.get("bankRefNo"), "日期或金额格式不正确"));
                continue;
            }
            String payer = row.get("payerName");
            String currency = row.get("currency");
            if (currency != null && !currency.equalsIgnoreCase(bank.getCurrency())) {
                unmatched.add(new UnmatchedRow(row.rowNo(), date, amount, payer, row.get("bankRefNo"), "币别与收款账户不一致"));
                continue;
            }
            CustomerDTO c = payer == null ? null : cache.computeIfAbsent(payer, this::matchCustomer);
            if (c == null) {
                unmatched.add(new UnmatchedRow(row.rowNo(), date, amount, payer, row.get("bankRefNo"), "未匹配到客户"));
                continue;
            }
            String bankRef = row.get("bankRefNo");
            if (bankRef != null && mapper.selectCount(new LambdaQueryWrapper<FinReceiptDO>().eq(FinReceiptDO::getBankRefNo, bankRef)
                    .ne(FinReceiptDO::getReceiptStatus, CashStatus.VOIDED.name())) > 0) {
                unmatched.add(new UnmatchedRow(row.rowNo(), date, amount, payer, bankRef, "流水号已登记"));
                continue;
            }
            ids.add(create(new ReceiptSave(c.id(), amount.signum() < 0 ? REFUND : "SALES", bank.getId(),
                    StringUtils.hasText(settlementMethod) ? settlementMethod : "TT", date, null, amount, BigDecimal.ZERO, bankRef, payer, null,
                    "银行流水导入", null)));
        }
        return new BankImportResult(ids.size(), ids, unmatched);
    }

    private CustomerDTO matchCustomer(String payer) {
        String key = payer.trim();
        return support.customerApi().search(key, List.of(CustomerStatus.values()), 20).stream()
                .filter(c -> key.equalsIgnoreCase(c.name()) || key.equalsIgnoreCase(c.shortName()) || key.equalsIgnoreCase(c.nameEn()))
                .findFirst().orElse(null);
    }

    // ==================== 查询 ====================

    public FinReceiptDO get(Long id) {
        FinReceiptDO r = id == null ? null : mapper.selectById(id);
        if (r == null) throw BizException.of(FinanceErrorCodes.NOT_EXISTS, "收款单");
        return r;
    }

    /** 预收款可选订单：客户未出完的订单 */
    public List<OrderOption> orderOptions(Long customerId) {
        Map<Long, OrderOption> map = new LinkedHashMap<>();
        for (SalesOrderLineDTO l : orderQueryApi.getOpenLines(new OpenLineFilter(customerId, null, null, null, null))) {
            map.putIfAbsent(l.orderId(), new OrderOption(l.orderId(), l.orderNo(), l.currency()));
        }
        return new ArrayList<>(map.values());
    }

    public PageResult<ReceiptRow> page(ReceiptQuery q) {
        IPage<FinReceiptDO> p = mapper.selectScopedPage(new Page<>(q.getPageNo(), q.getPageSize()), query(q));
        return new PageResult<>(rows(p.getRecords()), p.getTotal());
    }

    public List<ReceiptRow> list(ReceiptQuery q) {
        return rows(mapper.selectScopedList(query(q)));
    }

    private LambdaQueryWrapper<FinReceiptDO> query(ReceiptQuery q) {
        List<String> types = split(q.getReceiptTypes());
        List<String> statuses = split(q.getStatuses());
        LambdaQueryWrapper<FinReceiptDO> w = new LambdaQueryWrapper<FinReceiptDO>().eq(FinReceiptDO::getDeleted, false)
                .like(StringUtils.hasText(q.getDocNo()), FinReceiptDO::getDocNo, q.getDocNo())
                .eq(q.getCustomerId() != null, FinReceiptDO::getCustomerId, q.getCustomerId())
                .in(!types.isEmpty(), FinReceiptDO::getReceiptType, types)
                .eq(q.getBankAccountId() != null, FinReceiptDO::getBankAccountId, q.getBankAccountId())
                .ge(q.getDateFrom() != null, FinReceiptDO::getReceiptDate, q.getDateFrom())
                .le(q.getDateTo() != null, FinReceiptDO::getReceiptDate, q.getDateTo())
                .in(!statuses.isEmpty(), FinReceiptDO::getReceiptStatus, statuses)
                .like(StringUtils.hasText(q.getOrderNo()), FinReceiptDO::getOrderNo, q.getOrderNo());
        if (Boolean.TRUE.equals(q.getOpenOnly())) {
            w.eq(FinReceiptDO::getReceiptStatus, CashStatus.CONFIRMED.name())
                    .apply("allocated_amount <> (CASE WHEN amount > 0 THEN amount + bank_fee ELSE amount END)");
        }
        return w.orderByDesc(FinReceiptDO::getReceiptDate).orderByDesc(FinReceiptDO::getId);
    }

    static List<String> split(String text) {
        return StringUtils.hasText(text) ? Arrays.stream(text.split(",")).map(String::trim).filter(StringUtils::hasText).toList() : List.of();
    }

    private List<ReceiptRow> rows(List<FinReceiptDO> list) {
        Map<Long, CustomerDTO> cs = support.customers(list.stream().map(FinReceiptDO::getCustomerId).toList());
        Map<Long, UserDTO> users = support.users(list.stream().map(FinReceiptDO::getOwnerId).toList());
        Map<Long, FinBankAccountDO> banks = settingService.banks(list.stream().map(FinReceiptDO::getBankAccountId).toList());
        return list.stream().map(r -> {
            FinBankAccountDO b = banks.get(r.getBankAccountId());
            return new ReceiptRow(r.getId(), r.getDocNo(), r.getCustomerId(), FinSupport.customerName(cs.get(r.getCustomerId())), r.getReceiptType(),
                    r.getBankAccountId(), b == null ? null : b.getName(), r.getSettlementMethod(), r.getReceiptDate(), r.getCurrency(), r.getExchangeRate(),
                    r.getAmount(), r.getBankFee(), r.getAmountBase(), r.getAllocatedAmount(), VerificationService.receiptOpen(r), r.getOrderId(),
                    r.getOrderNo(), r.getBankRefNo(), r.getPayerName(), r.getReceiptStatus(), FinSupport.name(users, r.getOwnerId()), r.getRemark(),
                    r.getCreatedAt());
        }).toList();
    }

    public ReceiptDetail detail(Long id) {
        FinReceiptDO r = get(id);
        return new ReceiptDetail(rows(List.of(r)).get(0), r.getConfirmedAt(), r.getVoucherId(),
                verificationService.listByDoc(VerificationService.DOC_RECEIPT, id));
    }

    /** 银行账户是否被收款引用 */
    public boolean bankUsed(Long bankAccountId) {
        return mapper.selectCount(new LambdaQueryWrapper<FinReceiptDO>().eq(FinReceiptDO::getBankAccountId, bankAccountId)) > 0;
    }
}
