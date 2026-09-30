package com.erp.module.finance.service.voucher;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.common.exception.BizException;
import com.erp.common.result.PageResult;
import com.erp.common.util.Decimals;
import com.erp.module.crm.api.customer.CustomerDTO;
import com.erp.module.engineering.api.material.MaterialDTO;
import com.erp.module.finance.api.FinanceErrorCodes;
import com.erp.module.finance.config.FinanceModuleConfig;
import com.erp.module.finance.controller.vo.SettingVOs.MappingEntry;
import com.erp.module.finance.controller.vo.VoucherVOs.GenerateReq;
import com.erp.module.finance.controller.vo.VoucherVOs.GenerateResult;
import com.erp.module.finance.controller.vo.VoucherVOs.Pending;
import com.erp.module.finance.controller.vo.VoucherVOs.Preview;
import com.erp.module.finance.controller.vo.VoucherVOs.VoucherDetail;
import com.erp.module.finance.controller.vo.VoucherVOs.VoucherLineReq;
import com.erp.module.finance.controller.vo.VoucherVOs.VoucherLineVO;
import com.erp.module.finance.controller.vo.VoucherVOs.VoucherQuery;
import com.erp.module.finance.controller.vo.VoucherVOs.VoucherRow;
import com.erp.module.finance.controller.vo.VoucherVOs.VoucherSave;
import com.erp.module.finance.dal.dataobject.FinAccountDO;
import com.erp.module.finance.dal.dataobject.FinAccountMappingDO;
import com.erp.module.finance.dal.dataobject.FinBankAccountDO;
import com.erp.module.finance.dal.dataobject.FinCostMaterialDO;
import com.erp.module.finance.dal.dataobject.FinCostRunDO;
import com.erp.module.finance.dal.dataobject.FinFxRevaluationDO;
import com.erp.module.finance.dal.dataobject.FinPayableDO;
import com.erp.module.finance.dal.dataobject.FinPaymentDO;
import com.erp.module.finance.dal.dataobject.FinReceiptDO;
import com.erp.module.finance.dal.dataobject.FinReceivableDO;
import com.erp.module.finance.dal.dataobject.FinVerificationDO;
import com.erp.module.finance.dal.dataobject.FinVoucherDO;
import com.erp.module.finance.dal.dataobject.FinVoucherLineDO;
import com.erp.module.finance.dal.mapper.FinAccountMapper;
import com.erp.module.finance.dal.mapper.FinCostMaterialMapper;
import com.erp.module.finance.dal.mapper.FinCostRunMapper;
import com.erp.module.finance.dal.mapper.FinFxRevaluationMapper;
import com.erp.module.finance.dal.mapper.FinPayableMapper;
import com.erp.module.finance.dal.mapper.FinPaymentMapper;
import com.erp.module.finance.dal.mapper.FinReceiptMapper;
import com.erp.module.finance.dal.mapper.FinReceivableMapper;
import com.erp.module.finance.dal.mapper.FinVerificationMapper;
import com.erp.module.finance.dal.mapper.FinVoucherLineMapper;
import com.erp.module.finance.dal.mapper.FinVoucherMapper;
import com.erp.module.finance.service.ArStatus;
import com.erp.module.finance.service.CashStatus;
import com.erp.module.finance.service.FinAction;
import com.erp.module.finance.service.FinStateMachines;
import com.erp.module.finance.service.FinSupport;
import com.erp.module.finance.service.VoucherStatus;
import com.erp.module.finance.service.setting.SettingService;
import com.erp.module.purchase.api.supplier.SupplierDTO;
import com.erp.module.system.api.user.UserDTO;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Arrays;
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
 * 凭证（12-06）：按科目映射把业务单据生成凭证草稿（按单据 / 按类型汇总），手工凭证，审核、过账，整理凭证号。
 * 每张业务单据只生成一次（单据上记录 voucher_id），凭证删除后可重新生成。
 */
@Service
public class VoucherService {

    private static final Logger log = LoggerFactory.getLogger(VoucherService.class);
    public static final String BIZ_TYPE = FinanceModuleConfig.VOUCHER;
    public static final String SALES_AR = "SALES_AR";
    public static final String SALES_RETURN_AR = "SALES_RETURN_AR";
    public static final String RECEIPT = "RECEIPT";
    public static final String PURCHASE_AP = "PURCHASE_AP";
    public static final String PAYMENT = "PAYMENT";
    public static final String FX_GAIN_LOSS = "FX_GAIN_LOSS";
    public static final String SALES_COST = "STOCK_OUT_SALES_COST";
    public static final String PRODUCTION_ISSUE = "PRODUCTION_ISSUE";
    public static final String PRODUCTION_IN = "PRODUCTION_IN";
    /** 可生成的业务类型（生成顺序） */
    public static final Map<String, String> LABELS = new LinkedHashMap<>();

    static {
        LABELS.put(SALES_AR, "出货应收");
        LABELS.put(RECEIPT, "收款");
        LABELS.put(PURCHASE_AP, "采购应付");
        LABELS.put(PAYMENT, "付款");
        LABELS.put(FX_GAIN_LOSS, "汇兑损益");
        LABELS.put(PRODUCTION_ISSUE, "生产领料");
        LABELS.put(PRODUCTION_IN, "完工入库");
        LABELS.put(SALES_COST, "销售成本结转");
    }

    private final FinVoucherMapper mapper;
    private final FinVoucherLineMapper lineMapper;
    private final FinAccountMapper accountMapper;
    private final FinReceivableMapper receivableMapper;
    private final FinReceiptMapper receiptMapper;
    private final FinPayableMapper payableMapper;
    private final FinPaymentMapper paymentMapper;
    private final FinVerificationMapper verificationMapper;
    private final FinCostRunMapper costRunMapper;
    private final FinCostMaterialMapper costMaterialMapper;
    private final FinFxRevaluationMapper fxMapper;
    private final SettingService settingService;
    private final FinSupport support;

    public VoucherService(FinVoucherMapper mapper, FinVoucherLineMapper lineMapper, FinAccountMapper accountMapper, FinReceivableMapper receivableMapper,
                          FinReceiptMapper receiptMapper, FinPayableMapper payableMapper, FinPaymentMapper paymentMapper,
                          FinVerificationMapper verificationMapper, FinCostRunMapper costRunMapper, FinCostMaterialMapper costMaterialMapper,
                          FinFxRevaluationMapper fxMapper, SettingService settingService, FinSupport support) {
        this.mapper = mapper;
        this.lineMapper = lineMapper;
        this.accountMapper = accountMapper;
        this.receivableMapper = receivableMapper;
        this.receiptMapper = receiptMapper;
        this.payableMapper = payableMapper;
        this.paymentMapper = paymentMapper;
        this.verificationMapper = verificationMapper;
        this.costRunMapper = costRunMapper;
        this.costMaterialMapper = costMaterialMapper;
        this.fxMapper = fxMapper;
        this.settingService = settingService;
        this.support = support;
    }

    /** FIN-SET-R01：已有凭证的科目视为已使用 */
    @PostConstruct
    void registerUsage() {
        settingService.registerUsageChecker(code -> lineMapper.selectCount(new LambdaQueryWrapper<FinVoucherLineDO>().eq(FinVoucherLineDO::getAccountCode, code)) > 0);
    }

    // ==================== 来源单据 ====================

    /** 生成凭证的来源（业务单据或期间汇总） */
    static final class Src {
        String bizType;
        String sourceType;
        Long id;
        String docNo;
        String sourceNo;
        LocalDate date;
        String period;
        Long customerId;
        Long supplierId;
        Long deptId;
        String partnerName;
        String currency;
        BigDecimal rate;
        String bankAccountCode;
        final Map<String, BigDecimal> amounts = new HashMap<>();
        final Map<String, String> attrs = new HashMap<>();
    }

    private List<Src> candidates(String bizType, String period) {
        YearMonth ym = YearMonth.parse(period, FinSupport.PERIOD);
        LocalDate from = ym.atDay(1);
        LocalDate to = ym.atEndOfMonth();
        List<Src> list = new ArrayList<>();
        switch (bizType) {
            case SALES_AR -> receivableMapper.selectList(new LambdaQueryWrapper<FinReceivableDO>().eq(FinReceivableDO::getArStatus, ArStatus.CONFIRMED.name())
                            .isNull(FinReceivableDO::getVoucherId).between(FinReceivableDO::getBizDate, from, to).orderByAsc(FinReceivableDO::getBizDate))
                    .forEach(r -> list.add(src(r)));
            case RECEIPT -> receiptMapper.selectList(new LambdaQueryWrapper<FinReceiptDO>().eq(FinReceiptDO::getReceiptStatus, CashStatus.CONFIRMED.name())
                            .isNull(FinReceiptDO::getVoucherId).between(FinReceiptDO::getReceiptDate, from, to).orderByAsc(FinReceiptDO::getReceiptDate))
                    .forEach(r -> list.add(src(r)));
            case PURCHASE_AP -> payableMapper.selectList(new LambdaQueryWrapper<FinPayableDO>().eq(FinPayableDO::getApStatus, ArStatus.CONFIRMED.name())
                            .isNull(FinPayableDO::getVoucherId).between(FinPayableDO::getBizDate, from, to).orderByAsc(FinPayableDO::getBizDate))
                    .forEach(p -> list.add(src(p)));
            case PAYMENT -> paymentMapper.selectList(new LambdaQueryWrapper<FinPaymentDO>().eq(FinPaymentDO::getPaymentStatus, CashStatus.CONFIRMED.name())
                            .isNull(FinPaymentDO::getVoucherId).between(FinPaymentDO::getPayDate, from, to).orderByAsc(FinPaymentDO::getPayDate))
                    .forEach(p -> list.add(src(p)));
            case FX_GAIN_LOSS -> verificationMapper.selectList(new LambdaQueryWrapper<FinVerificationDO>().eq(FinVerificationDO::getPeriod, period)
                            .eq(FinVerificationDO::getReversed, false).ne(FinVerificationDO::getFxDiff, BigDecimal.ZERO).isNull(FinVerificationDO::getVoucherId))
                    .forEach(v -> list.add(src(v)));
            case SALES_COST, PRODUCTION_ISSUE, PRODUCTION_IN -> {
                Src s = costSrc(bizType, period);
                if (s != null) list.add(s);
            }
            default -> throw BizException.of(FinanceErrorCodes.VCH_NO_MAPPING, bizType);
        }
        fillPartners(list);
        return list;
    }

    private Src load(String bizType, Long id) {
        Src s = switch (bizType) {
            case SALES_AR, SALES_RETURN_AR -> {
                FinReceivableDO r = receivableMapper.selectById(id);
                yield r == null ? null : src(r);
            }
            case RECEIPT -> {
                FinReceiptDO r = receiptMapper.selectById(id);
                yield r == null ? null : src(r);
            }
            case PURCHASE_AP -> {
                FinPayableDO p = payableMapper.selectById(id);
                yield p == null ? null : src(p);
            }
            case PAYMENT -> {
                FinPaymentDO p = paymentMapper.selectById(id);
                yield p == null ? null : src(p);
            }
            case FX_GAIN_LOSS -> {
                FinVerificationDO v = verificationMapper.selectById(id);
                yield v == null ? null : src(v);
            }
            default -> null;
        };
        if (s == null) throw BizException.of(FinanceErrorCodes.NOT_EXISTS, "单据");
        fillPartners(List.of(s));
        return s;
    }

    private Src src(FinReceivableDO r) {
        Src s = base(FinSupport.nz(r.getTotalAmount()).signum() < 0 && hasMapping(SALES_RETURN_AR) ? SALES_RETURN_AR : SALES_AR, "RECEIVABLE", r.getId(),
                r.getDocNo(), r.getSourceNo(), r.getBizDate(), r.getCurrency(), r.getExchangeRate());
        s.customerId = r.getCustomerId();
        s.deptId = r.getDeptId();
        BigDecimal amount = FinSupport.toBase(r.getAmount(), r.getExchangeRate());
        BigDecimal tax = FinSupport.toBase(r.getTaxAmount(), r.getExchangeRate());
        s.amounts.put("amount", amount);
        s.amounts.put("tax", tax);
        s.amounts.put("totalAmount", amount.add(tax));
        s.attrs.put("arType", r.getArType());
        return s;
    }

    private Src src(FinReceiptDO r) {
        Src s = base(RECEIPT, "RECEIPT", r.getId(), r.getDocNo(), r.getOrderNo(), r.getReceiptDate(), r.getCurrency(), r.getExchangeRate());
        s.customerId = r.getCustomerId();
        s.deptId = r.getDeptId();
        BigDecimal amount = FinSupport.toBase(r.getAmount(), r.getExchangeRate());
        BigDecimal fee = FinSupport.nz(r.getAmount()).signum() < 0 ? BigDecimal.ZERO : FinSupport.toBase(r.getBankFee(), r.getExchangeRate());
        s.amounts.put("amount", amount);
        s.amounts.put("fee", fee);
        s.amounts.put("totalAmount", amount.add(fee));
        s.attrs.put("receiptType", r.getReceiptType());
        s.bankAccountCode = bankCode(r.getBankAccountId());
        return s;
    }

    private Src src(FinPayableDO p) {
        Src s = base(PURCHASE_AP, "PAYABLE", p.getId(), p.getDocNo(), p.getStatementNo(), p.getBizDate(), p.getCurrency(), p.getExchangeRate());
        s.supplierId = p.getSupplierId();
        s.deptId = p.getDeptId();
        BigDecimal amount = FinSupport.toBase(p.getAmount(), p.getExchangeRate());
        BigDecimal tax = FinSupport.toBase(p.getTaxAmount(), p.getExchangeRate());
        s.amounts.put("amount", amount);
        s.amounts.put("tax", tax);
        s.amounts.put("totalAmount", amount.add(tax));
        s.attrs.put("apType", p.getApType());
        return s;
    }

    private Src src(FinPaymentDO p) {
        Src s = base(PAYMENT, "PAYMENT", p.getId(), p.getDocNo(), p.getRequestNo(), p.getPayDate(), p.getCurrency(), p.getExchangeRate());
        s.supplierId = p.getSupplierId();
        s.deptId = p.getDeptId();
        BigDecimal amount = FinSupport.toBase(p.getAmount(), p.getExchangeRate());
        BigDecimal fee = FinSupport.toBase(p.getBankFee(), p.getExchangeRate());
        s.amounts.put("amount", amount);
        s.amounts.put("fee", fee);
        s.amounts.put("totalAmount", amount.add(fee));
        s.attrs.put("requestType", p.getRequestType());
        s.bankAccountCode = bankCode(p.getBankAccountId());
        return s;
    }

    private Src src(FinVerificationDO v) {
        Src s = base(FX_GAIN_LOSS, "VERIFICATION", v.getId(), v.getDocANo() + "/" + v.getDocBNo(), v.getBatchNo(), v.getVerifiedAt().toLocalDate(),
                v.getCurrency(), null);
        if ("CUSTOMER".equals(v.getPartnerType())) s.customerId = v.getPartnerId();
        else s.supplierId = v.getPartnerId();
        s.amounts.put("fxDiff", FinSupport.nz(v.getFxDiff()));
        s.attrs.put("partnerType", v.getPartnerType());
        s.attrs.put("verifyType", v.getVerifyType());
        return s;
    }

    /** 成本类凭证：最近一次成功计算的期间汇总 */
    private Src costSrc(String bizType, String period) {
        FinCostRunDO run = lastSuccessRun(period);
        if (run == null) return null;
        Long existing = switch (bizType) {
            case SALES_COST -> run.getSalesCostVoucherId();
            case PRODUCTION_ISSUE -> run.getIssueVoucherId();
            default -> run.getFinishVoucherId();
        };
        if (existing != null) return null;
        List<FinCostMaterialDO> rows = costMaterialMapper.selectList(new LambdaQueryWrapper<FinCostMaterialDO>().eq(FinCostMaterialDO::getPeriod, period));
        Function<FinCostMaterialDO, BigDecimal> f = switch (bizType) {
            case SALES_COST -> FinCostMaterialDO::getSalesOutAmount;
            case PRODUCTION_ISSUE -> FinCostMaterialDO::getIssueAmount;
            default -> FinCostMaterialDO::getProductionInAmount;
        };
        BigDecimal cost = rows.stream().map(f).map(FinSupport::nz).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (cost.signum() == 0) return null;
        Src s = base(bizType, "COST_RUN", run.getId(), period, null, YearMonth.parse(period, FinSupport.PERIOD).atEndOfMonth(), null, null);
        s.amounts.put("cost", Decimals.amount(cost));
        return s;
    }

    public FinCostRunDO lastSuccessRun(String period) {
        return costRunMapper.selectList(new LambdaQueryWrapper<FinCostRunDO>().eq(FinCostRunDO::getPeriod, period).eq(FinCostRunDO::getRunStatus, "SUCCESS")
                .orderByDesc(FinCostRunDO::getId).last("LIMIT 1")).stream().findFirst().orElse(null);
    }

    private static Src base(String bizType, String sourceType, Long id, String docNo, String sourceNo, LocalDate date, String currency, BigDecimal rate) {
        Src s = new Src();
        s.bizType = bizType;
        s.sourceType = sourceType;
        s.id = id;
        s.docNo = docNo;
        s.sourceNo = sourceNo;
        s.date = date;
        s.period = FinSupport.periodOf(date);
        s.currency = currency;
        s.rate = rate;
        return s;
    }

    private String bankCode(Long bankId) {
        if (bankId == null) return null;
        FinBankAccountDO b = settingService.banks(List.of(bankId)).get(bankId);
        return b == null ? null : b.getAccountCode();
    }

    private void fillPartners(List<Src> list) {
        Map<Long, CustomerDTO> cs = support.customers(list.stream().map(s -> s.customerId).toList());
        Map<Long, SupplierDTO> ss = support.suppliers(list.stream().map(s -> s.supplierId).filter(Objects::nonNull).toList());
        for (Src s : list) {
            if (s.customerId != null) s.partnerName = FinSupport.customerName(cs.get(s.customerId));
            else if (s.supplierId != null && ss.containsKey(s.supplierId)) s.partnerName = ss.get(s.supplierId).name();
        }
    }

    private void markVoucher(Src s, Long voucherId) {
        switch (s.sourceType) {
            case "RECEIVABLE" -> receivableMapper.update(null, new LambdaUpdateWrapper<FinReceivableDO>().set(FinReceivableDO::getVoucherId, voucherId).eq(FinReceivableDO::getId, s.id));
            case "RECEIPT" -> receiptMapper.update(null, new LambdaUpdateWrapper<FinReceiptDO>().set(FinReceiptDO::getVoucherId, voucherId).eq(FinReceiptDO::getId, s.id));
            case "PAYABLE" -> payableMapper.update(null, new LambdaUpdateWrapper<FinPayableDO>().set(FinPayableDO::getVoucherId, voucherId).eq(FinPayableDO::getId, s.id));
            case "PAYMENT" -> paymentMapper.update(null, new LambdaUpdateWrapper<FinPaymentDO>().set(FinPaymentDO::getVoucherId, voucherId).eq(FinPaymentDO::getId, s.id));
            case "VERIFICATION" -> verificationMapper.update(null, new LambdaUpdateWrapper<FinVerificationDO>().set(FinVerificationDO::getVoucherId, voucherId).eq(FinVerificationDO::getId, s.id));
            case "COST_RUN" -> {
                LambdaUpdateWrapper<FinCostRunDO> w = new LambdaUpdateWrapper<FinCostRunDO>().eq(FinCostRunDO::getId, s.id);
                switch (s.bizType) {
                    case SALES_COST -> w.set(FinCostRunDO::getSalesCostVoucherId, voucherId);
                    case PRODUCTION_ISSUE -> w.set(FinCostRunDO::getIssueVoucherId, voucherId);
                    default -> w.set(FinCostRunDO::getFinishVoucherId, voucherId);
                }
                costRunMapper.update(null, w);
            }
            default -> {
            }
        }
    }

    /** 删除凭证时清除来源单据上的凭证关联（可重新生成） */
    private void clearRefs(Long voucherId) {
        receivableMapper.update(null, new LambdaUpdateWrapper<FinReceivableDO>().set(FinReceivableDO::getVoucherId, null).eq(FinReceivableDO::getVoucherId, voucherId));
        receiptMapper.update(null, new LambdaUpdateWrapper<FinReceiptDO>().set(FinReceiptDO::getVoucherId, null).eq(FinReceiptDO::getVoucherId, voucherId));
        payableMapper.update(null, new LambdaUpdateWrapper<FinPayableDO>().set(FinPayableDO::getVoucherId, null).eq(FinPayableDO::getVoucherId, voucherId));
        paymentMapper.update(null, new LambdaUpdateWrapper<FinPaymentDO>().set(FinPaymentDO::getVoucherId, null).eq(FinPaymentDO::getVoucherId, voucherId));
        verificationMapper.update(null, new LambdaUpdateWrapper<FinVerificationDO>().set(FinVerificationDO::getVoucherId, null).eq(FinVerificationDO::getVoucherId, voucherId));
        fxMapper.update(null, new LambdaUpdateWrapper<FinFxRevaluationDO>().set(FinFxRevaluationDO::getVoucherId, null).eq(FinFxRevaluationDO::getVoucherId, voucherId));
        fxMapper.update(null, new LambdaUpdateWrapper<FinFxRevaluationDO>().set(FinFxRevaluationDO::getReversalVoucherId, null)
                .eq(FinFxRevaluationDO::getReversalVoucherId, voucherId));
        costRunMapper.update(null, new LambdaUpdateWrapper<FinCostRunDO>().set(FinCostRunDO::getSalesCostVoucherId, null).eq(FinCostRunDO::getSalesCostVoucherId, voucherId));
        costRunMapper.update(null, new LambdaUpdateWrapper<FinCostRunDO>().set(FinCostRunDO::getIssueVoucherId, null).eq(FinCostRunDO::getIssueVoucherId, voucherId));
        costRunMapper.update(null, new LambdaUpdateWrapper<FinCostRunDO>().set(FinCostRunDO::getFinishVoucherId, null).eq(FinCostRunDO::getFinishVoucherId, voucherId));
    }

    // ==================== 映射 → 分录 ====================

    private boolean hasMapping(String bizType) {
        return !settingService.activeMappings(bizType).isEmpty();
    }

    /** 条件全部满足的映射中优先级最高者；没有时取无条件的默认映射 */
    private FinAccountMappingDO resolve(String bizType, Map<String, String> attrs) {
        FinAccountMappingDO fallback = null;
        for (FinAccountMappingDO m : settingService.activeMappings(bizType)) {
            Map<String, String> cond = settingService.condition(m);
            if (cond.isEmpty()) {
                if (fallback == null) fallback = m;
                continue;
            }
            if (cond.entrySet().stream().allMatch(e -> Objects.equals(e.getValue(), attrs.get(e.getKey())))) return m;
        }
        if (fallback == null) throw BizException.of(FinanceErrorCodes.VCH_NO_MAPPING, LABELS.getOrDefault(bizType, bizType));
        return fallback;
    }

    /** 按映射生成分录；金额为负时借贷方向互换 */
    private List<FinVoucherLineDO> build(Src s) {
        FinAccountMappingDO m = resolve(s.bizType, s.attrs);
        Map<String, FinAccountDO> accounts = accounts();
        List<FinVoucherLineDO> lines = new ArrayList<>();
        for (MappingEntry e : settingService.entries(m)) {
            BigDecimal amount = Decimals.amount(FinSupport.nz(s.amounts.get(e.amountField())));
            if (amount.signum() == 0) continue;
            String code = e.accountCode();
            if (code.startsWith("1002") && StringUtils.hasText(s.bankAccountCode)) code = s.bankAccountCode;
            FinAccountDO acc = accounts.get(code);
            FinVoucherLineDO l = new FinVoucherLineDO();
            l.setSummary(FinSupport.limit(summary(e.summaryTemplate(), s), 256));
            l.setAccountCode(code);
            boolean debit = "DEBIT".equals(e.direction()) == (amount.signum() > 0);
            l.setDebit(debit ? amount.abs() : BigDecimal.ZERO);
            l.setCredit(debit ? BigDecimal.ZERO : amount.abs());
            List<String> aux = acc == null ? List.of() : SettingService.aux(acc.getAuxTypes());
            if ("CUSTOMER".equals(e.auxFrom()) || aux.contains("CUSTOMER")) l.setAuxCustomerId(s.customerId);
            if ("SUPPLIER".equals(e.auxFrom()) || aux.contains("SUPPLIER")) l.setAuxSupplierId(s.supplierId);
            if ("DEPT".equals(e.auxFrom()) || aux.contains("DEPT")) l.setAuxDeptId(s.deptId);
            if (acc != null && Boolean.TRUE.equals(acc.getCurrencyAccounting()) && s.currency != null && s.rate != null && s.rate.signum() > 0) {
                l.setCurrency(s.currency);
                l.setExchangeRate(s.rate);
                l.setFcAmount(amount.abs().divide(s.rate, 2, RoundingMode.HALF_UP));
            }
            l.setSourceType(s.sourceType);
            l.setSourceId(s.id);
            lines.add(l);
        }
        return lines;
    }

    private static String summary(String template, Src s) {
        String t = StringUtils.hasText(template) ? template : LABELS.getOrDefault(s.bizType, s.bizType) + " {docNo}";
        return t.replace("{docNo}", Objects.toString(s.docNo, "")).replace("{sourceNo}", Objects.toString(s.sourceNo, s.docNo == null ? "" : s.docNo))
                .replace("{partner}", Objects.toString(s.partnerName, "")).replace("{period}", Objects.toString(s.period, ""))
                .replace("{date}", Objects.toString(s.date, "")).trim();
    }

    private Map<String, FinAccountDO> accounts() {
        return accountMapper.selectList(null).stream().collect(Collectors.toMap(FinAccountDO::getCode, Function.identity()));
    }

    // ==================== 生成 ====================

    /** 待生成凭证的单据数 */
    public List<Pending> pending(String period) {
        List<Pending> list = new ArrayList<>();
        for (Map.Entry<String, String> e : LABELS.entrySet()) list.add(new Pending(e.getKey(), e.getValue(), candidates(e.getKey(), period).size()));
        return list;
    }

    /** 批量生成：按单据（每张单据一张凭证）或按类型汇总（同一业务类型一张凭证，同科目同辅助项合并） */
    @Transactional(rollbackFor = Exception.class)
    public GenerateResult generate(GenerateReq req) {
        String period = req.period();
        if (support.isClosed(period)) throw BizException.of(FinanceErrorCodes.PERIOD_CLOSED, period);
        List<String> types = req.bizTypes() == null || req.bizTypes().isEmpty() ? new ArrayList<>(LABELS.keySet()) : req.bizTypes();
        boolean summary = "SUMMARY".equals(req.mode());
        List<Long> ids = new ArrayList<>();
        List<String> messages = new ArrayList<>();
        int docs = 0;
        for (String type : LABELS.keySet()) {
            if (!types.contains(type)) continue;
            List<Src> list = candidates(type, period);
            if (list.isEmpty()) continue;
            try {
                if (summary || list.get(0).sourceType.equals("COST_RUN")) {
                    ids.addAll(summarize(type, period, list));
                } else {
                    for (Src s : list) ids.add(create(s.date, s.bizType, List.of(s), build(s), null));
                }
                docs += list.size();
            } catch (BizException e) {
                messages.add(LABELS.get(type) + "：" + e.getMessage());
            }
        }
        if (ids.isEmpty() && messages.isEmpty()) throw new BizException(FinanceErrorCodes.VCH_EMPTY);
        return new GenerateResult(ids.size(), docs, ids, messages);
    }

    /** 按类型汇总：红字（退货）单据与蓝字合并时按映射业务类型分组 */
    private List<Long> summarize(String type, String period, List<Src> list) {
        Map<String, List<Src>> groups = list.stream().collect(Collectors.groupingBy(s -> s.bizType, LinkedHashMap::new, Collectors.toList()));
        List<Long> ids = new ArrayList<>();
        for (Map.Entry<String, List<Src>> g : groups.entrySet()) {
            Map<String, FinVoucherLineDO> merged = new LinkedHashMap<>();
            Map<Long, String> partnerNames = new HashMap<>();
            for (Src s : g.getValue()) {
                if (s.customerId != null) partnerNames.put(s.customerId, s.partnerName);
                if (s.supplierId != null) partnerNames.put(s.supplierId, s.partnerName);
                for (FinVoucherLineDO l : build(s)) {
                    String key = String.join("|", l.getAccountCode(), l.getDebit().signum() > 0 ? "D" : "C", Objects.toString(l.getAuxCustomerId(), ""),
                            Objects.toString(l.getAuxSupplierId(), ""), Objects.toString(l.getAuxDeptId(), ""), Objects.toString(l.getCurrency(), ""));
                    FinVoucherLineDO m = merged.get(key);
                    if (m == null) {
                        String label = LABELS.getOrDefault(type, type) + " " + period;
                        Long partner = l.getAuxCustomerId() != null ? l.getAuxCustomerId() : l.getAuxSupplierId();
                        l.setSummary(FinSupport.limit(partner != null && partnerNames.get(partner) != null ? label + " " + partnerNames.get(partner) : label, 256));
                        l.setSourceType(null);
                        l.setSourceId(null);
                        merged.put(key, l);
                    } else {
                        m.setDebit(m.getDebit().add(l.getDebit()));
                        m.setCredit(m.getCredit().add(l.getCredit()));
                        if (m.getFcAmount() != null && l.getFcAmount() != null) m.setFcAmount(m.getFcAmount().add(l.getFcAmount()));
                    }
                }
            }
            LocalDate date = YearMonth.parse(period, FinSupport.PERIOD).atEndOfMonth();
            ids.add(create(date, g.getKey(), g.getValue(), new ArrayList<>(merged.values()), null));
        }
        return ids;
    }

    /** 单据确认后自动生成凭证草稿（参数 fin.voucher.auto-generate）；没有映射等问题不影响单据确认 */
    public void autoGenerate(String bizType, Long docId) {
        if (!support.params().getBool(FinanceModuleConfig.P_VOUCHER_AUTO)) return;
        try {
            Src s = load(bizType, docId);
            if (support.isClosed(s.period)) return;
            create(s.date, s.bizType, List.of(s), build(s), null);
        } catch (BizException e) {
            log.warn("自动生成凭证失败 {} {}：{}", bizType, docId, e.getMessage());
        }
    }

    /** 科目映射测试：选择一张业务单据预览凭证（FIN-SET-T02） */
    public Preview preview(String bizType, Long docId) {
        Src s = load(bizType, docId);
        List<FinVoucherLineDO> lines = build(s);
        List<VoucherLineVO> vos = lineVOs(lines);
        BigDecimal d = lines.stream().map(FinVoucherLineDO::getDebit).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal c = lines.stream().map(FinVoucherLineDO::getCredit).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Preview(s.bizType, vos, d, c, d.compareTo(c) == 0);
    }

    /** 由其他服务（外币重估）生成凭证草稿 */
    public Long createSystem(LocalDate date, String bizType, List<FinVoucherLineDO> lines, String remark, Long reversalOfId) {
        Long id = create(date, bizType, List.of(), lines, remark);
        if (reversalOfId != null) {
            FinVoucherDO v = get(id);
            v.setReversalOfId(reversalOfId);
            mapper.updateByIdOrFail(v);
        }
        return id;
    }

    private Long create(LocalDate date, String bizType, List<Src> sources, List<FinVoucherLineDO> lines, String remark) {
        if (lines.isEmpty()) throw new BizException(FinanceErrorCodes.VCH_EMPTY);
        String period = FinSupport.periodOf(date);
        if (support.isClosed(period)) throw BizException.of(FinanceErrorCodes.PERIOD_CLOSED, period);
        FinVoucherDO v = newVoucher(date, period);
        v.setVoucherSource("AUTO");
        v.setBizType(bizType);
        v.setSourceIds(sources.isEmpty() ? null : FinSupport.limit(sources.stream().map(s -> String.valueOf(s.id)).collect(Collectors.joining(",")), 60000));
        v.setAttachmentCount(sources.size());
        v.setRemark(remark);
        support.fillOwner(v, null);
        mapper.insert(v);
        saveLines(v, lines);
        for (Src s : sources) markVoucher(s, v.getId());
        support.log(BIZ_TYPE, v.getId(), v.getDocNo(), FinAction.CREATE.name(), "生成凭证", null, v.getVoucherStatus(),
                LABELS.getOrDefault(bizType, bizType) + (sources.isEmpty() ? "" : "，" + sources.size() + " 张单据"));
        return v.getId();
    }

    private FinVoucherDO newVoucher(LocalDate date, String period) {
        FinVoucherDO v = new FinVoucherDO();
        int seq = nextSeq(period);
        v.setVoucherWord("记");
        v.setVoucherSeq(seq);
        v.setDocNo(voucherNo(period, seq));
        v.setDocDate(date);
        v.setPeriod(period);
        v.setVoucherStatus(VoucherStatus.DRAFT.name());
        v.setStatus(VoucherStatus.DRAFT.docStatus());
        v.setTotalDebit(BigDecimal.ZERO);
        v.setTotalCredit(BigDecimal.ZERO);
        v.setAttachmentCount(0);
        return v;
    }

    private int nextSeq(String period) {
        return mapper.selectList(new LambdaQueryWrapper<FinVoucherDO>().eq(FinVoucherDO::getPeriod, period).orderByDesc(FinVoucherDO::getVoucherSeq).last("LIMIT 1"))
                .stream().findFirst().map(v -> v.getVoucherSeq() + 1).orElse(1);
    }

    static String voucherNo(String period, int seq) {
        return String.format("记-%s-%04d", period, seq);
    }

    private void saveLines(FinVoucherDO v, List<FinVoucherLineDO> lines) {
        int no = 1;
        BigDecimal d = BigDecimal.ZERO;
        BigDecimal c = BigDecimal.ZERO;
        for (FinVoucherLineDO l : lines) {
            l.setId(null);
            l.setVoucherId(v.getId());
            l.setLineNo(no++);
            l.setDebit(Decimals.amount(FinSupport.nz(l.getDebit())));
            l.setCredit(Decimals.amount(FinSupport.nz(l.getCredit())));
            if (!StringUtils.hasText(l.getSummary())) l.setSummary("-");
            lineMapper.insert(l);
            d = d.add(l.getDebit());
            c = c.add(l.getCredit());
        }
        v.setTotalDebit(d);
        v.setTotalCredit(c);
        mapper.updateByIdOrFail(v);
    }

    // ==================== 手工凭证 ====================

    @Transactional(rollbackFor = Exception.class)
    public Long create(VoucherSave req) {
        String period = FinSupport.periodOf(req.voucherDate());
        if (support.isClosed(period)) throw BizException.of(FinanceErrorCodes.PERIOD_CLOSED, period);
        List<FinVoucherLineDO> lines = manualLines(req.lines());
        FinVoucherDO v = newVoucher(req.voucherDate(), period);
        v.setVoucherSource("MANUAL");
        v.setAttachmentCount(req.attachmentCount() == null ? 0 : req.attachmentCount());
        v.setRemark(FinSupport.trim(req.remark()));
        support.fillOwner(v, null);
        mapper.insert(v);
        saveLines(v, lines);
        support.log(BIZ_TYPE, v.getId(), v.getDocNo(), FinAction.CREATE.name(), "新建凭证", null, v.getVoucherStatus(), null);
        return v.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, VoucherSave req) {
        FinVoucherDO v = get(id);
        requireDraft(v, "修改");
        requireOpen(v.getPeriod());
        String period = FinSupport.periodOf(req.voucherDate());
        requireOpen(period);
        List<FinVoucherLineDO> lines = manualLines(req.lines());
        if (!period.equals(v.getPeriod())) {
            v.setPeriod(period);
            v.setVoucherSeq(nextSeq(period));
            v.setDocNo(voucherNo(period, v.getVoucherSeq()));
        }
        v.setDocDate(req.voucherDate());
        v.setAttachmentCount(req.attachmentCount() == null ? v.getAttachmentCount() : req.attachmentCount());
        v.setRemark(FinSupport.trim(req.remark()));
        mapper.updateByIdOrFail(v);
        lineMapper.deleteByParent(id);
        saveLines(get(id), lines);
        support.log(BIZ_TYPE, id, v.getDocNo(), "UPDATE", "修改凭证", null, null, null);
    }

    /** 科目须为启用的末级科目；有辅助核算的科目必须填写对应辅助项；每行借、贷只能填一个 */
    private List<FinVoucherLineDO> manualLines(List<VoucherLineReq> reqs) {
        List<VoucherLineReq> list = reqs == null ? List.of() : reqs.stream().filter(r -> r != null && StringUtils.hasText(r.accountCode())).toList();
        if (list.size() < 2) throw new BizException(FinanceErrorCodes.VCH_NO_LINES);
        List<FinVoucherLineDO> lines = new ArrayList<>();
        int no = 1;
        for (VoucherLineReq r : list) {
            FinAccountDO acc = settingService.requireLeaf(r.accountCode().trim());
            BigDecimal d = Decimals.amount(FinSupport.nz(r.debit()));
            BigDecimal c = Decimals.amount(FinSupport.nz(r.credit()));
            if ((d.signum() != 0) == (c.signum() != 0)) throw BizException.of(FinanceErrorCodes.VCH_LINE_INVALID, no);
            List<String> missing = new ArrayList<>();
            for (String aux : SettingService.aux(acc.getAuxTypes())) {
                boolean ok = switch (aux) {
                    case "CUSTOMER" -> r.auxCustomerId() != null;
                    case "SUPPLIER" -> r.auxSupplierId() != null;
                    case "DEPT" -> r.auxDeptId() != null;
                    case "MATERIAL" -> r.auxMaterialId() != null;
                    default -> r.auxProjectId() != null;
                };
                if (!ok) missing.add(AUX_LABELS.getOrDefault(aux, aux));
            }
            if (!missing.isEmpty()) throw BizException.of(FinanceErrorCodes.VCH_AUX_REQUIRED, no, acc.getCode(), String.join("、", missing));
            FinVoucherLineDO l = new FinVoucherLineDO();
            l.setSummary(FinSupport.limit(StringUtils.hasText(r.summary()) ? r.summary() : "-", 256));
            l.setAccountCode(acc.getCode());
            l.setDebit(d);
            l.setCredit(c);
            if (Boolean.TRUE.equals(acc.getCurrencyAccounting()) && StringUtils.hasText(r.currency())) {
                l.setCurrency(r.currency());
                l.setFcAmount(r.fcAmount() == null ? null : Decimals.amount(r.fcAmount()));
                l.setExchangeRate(r.exchangeRate());
            }
            l.setAuxCustomerId(r.auxCustomerId());
            l.setAuxSupplierId(r.auxSupplierId());
            l.setAuxDeptId(r.auxDeptId());
            l.setAuxMaterialId(r.auxMaterialId());
            l.setAuxProjectId(r.auxProjectId());
            lines.add(l);
            no++;
        }
        return lines;
    }

    static final Map<String, String> AUX_LABELS = Map.of("CUSTOMER", "客户", "SUPPLIER", "供应商", "DEPT", "部门", "MATERIAL", "物料", "PROJECT", "项目");

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        FinVoucherDO v = get(id);
        requireDraft(v, "删除");
        requireOpen(v.getPeriod());
        clearRefs(id);
        lineMapper.deleteByParent(id);
        mapper.deletePhysical(id);
        support.log(BIZ_TYPE, id, v.getDocNo(), "DELETE", "删除凭证", v.getVoucherStatus(), null, null);
    }

    /** FIN-VCH-R05：业务单据反确认时，草稿凭证自动删除；已审核 / 过账的凭证需先反审核 / 反过账 */
    public void releaseForDoc(Long voucherId) {
        if (voucherId == null) return;
        FinVoucherDO v = mapper.selectById(voucherId);
        if (v == null) return;
        if (!VoucherStatus.DRAFT.name().equals(v.getVoucherStatus())) throw new BizException(FinanceErrorCodes.VCH_AUDITED);
        delete(voucherId);
    }

    // ==================== 审核、过账 ====================

    /** FIN-VCH-R01 借贷相等；FIN-VCH-R02 审核人不能是制单人 */
    @Transactional(rollbackFor = Exception.class)
    public void audit(Long id) {
        FinVoucherDO v = get(id);
        BigDecimal diff = FinSupport.nz(v.getTotalDebit()).subtract(FinSupport.nz(v.getTotalCredit()));
        if (diff.signum() != 0) throw BizException.of(FinanceErrorCodes.VCH_UNBALANCED, FinSupport.plain(diff.abs()));
        if (lineMapper.selectByParent(id).size() < 2) throw new BizException(FinanceErrorCodes.VCH_NO_LINES);
        Long me = support.currentUser();
        if (me != null && me.equals(v.getCreatedBy())) throw new BizException(FinanceErrorCodes.VCH_SAME_AUDITOR);
        requireOpen(v.getPeriod());
        v.setAuditorId(me);
        v.setAuditedAt(LocalDateTime.now());
        fire(v, FinAction.AUDIT, null);
    }

    @Transactional(rollbackFor = Exception.class)
    public void unaudit(Long id) {
        FinVoucherDO v = get(id);
        requireOpen(v.getPeriod());
        v.setAuditorId(null);
        v.setAuditedAt(null);
        fire(v, FinAction.UNAUDIT, null);
    }

    /** FIN-VCH-R03：已结账期间不能过账 / 反过账 */
    @Transactional(rollbackFor = Exception.class)
    public void post(Long id) {
        FinVoucherDO v = get(id);
        requireOpen(v.getPeriod());
        v.setPosterId(support.currentUser());
        v.setPostedAt(LocalDateTime.now());
        fire(v, FinAction.POST, null);
    }

    @Transactional(rollbackFor = Exception.class)
    public void unpost(Long id) {
        FinVoucherDO v = get(id);
        requireOpen(v.getPeriod());
        v.setPosterId(null);
        v.setPostedAt(null);
        fire(v, FinAction.UNPOST, null);
    }

    /** 批量审核 / 过账：返回失败说明 */
    @Transactional(rollbackFor = Exception.class)
    public List<String> batch(List<Long> ids, boolean post) {
        List<String> errors = new ArrayList<>();
        for (Long id : ids == null ? List.<Long>of() : ids) {
            FinVoucherDO v = get(id);
            try {
                if (post) post(id);
                else audit(id);
            } catch (BizException e) {
                errors.add(v.getDocNo() + "：" + e.getMessage());
            }
        }
        return errors;
    }

    /** FIN-VCH-R04：整理凭证号——按日期重排草稿与已审核凭证号，已过账的不变 */
    @Transactional(rollbackFor = Exception.class)
    public int renumber(String period) {
        requireOpen(period);
        List<FinVoucherDO> all = mapper.selectList(new LambdaQueryWrapper<FinVoucherDO>().eq(FinVoucherDO::getPeriod, period));
        Set<Integer> fixed = all.stream().filter(v -> VoucherStatus.POSTED.name().equals(v.getVoucherStatus())).map(FinVoucherDO::getVoucherSeq)
                .collect(Collectors.toCollection(HashSet::new));
        List<FinVoucherDO> movable = all.stream().filter(v -> !VoucherStatus.POSTED.name().equals(v.getVoucherStatus()))
                .sorted(Comparator.comparing(FinVoucherDO::getDocDate).thenComparing(FinVoucherDO::getVoucherSeq)).toList();
        for (FinVoucherDO v : movable) {
            mapper.update(null, new LambdaUpdateWrapper<FinVoucherDO>().set(FinVoucherDO::getDocNo, "TMP-" + v.getId()).eq(FinVoucherDO::getId, v.getId()));
        }
        int seq = 1;
        int changed = 0;
        for (FinVoucherDO v : movable) {
            while (fixed.contains(seq)) seq++;
            if (v.getVoucherSeq() != seq) changed++;
            mapper.update(null, new LambdaUpdateWrapper<FinVoucherDO>().set(FinVoucherDO::getVoucherSeq, seq).set(FinVoucherDO::getDocNo, voucherNo(period, seq))
                    .eq(FinVoucherDO::getId, v.getId()));
            seq++;
        }
        return changed;
    }

    private void fire(FinVoucherDO v, FinAction action, String reason) {
        VoucherStatus from = VoucherStatus.valueOf(v.getVoucherStatus());
        VoucherStatus to = FinStateMachines.VOUCHER.next(from, action)
                .orElseThrow(() -> BizException.of(FinanceErrorCodes.STATUS_NOT_ALLOWED, from.label(), action.label()));
        v.setVoucherStatus(to.name());
        v.setStatus(to.docStatus());
        mapper.updateByIdOrFail(v);
        support.log(BIZ_TYPE, v.getId(), v.getDocNo(), action.name(), action.label(), from.name(), to.name(), reason);
    }

    private static void requireDraft(FinVoucherDO v, String action) {
        if (!VoucherStatus.DRAFT.name().equals(v.getVoucherStatus())) {
            throw BizException.of(FinanceErrorCodes.STATUS_NOT_ALLOWED, VoucherStatus.valueOf(v.getVoucherStatus()).label(), action);
        }
    }

    private void requireOpen(String period) {
        if (support.isClosed(period)) throw BizException.of(FinanceErrorCodes.PERIOD_CLOSED, period);
    }

    // ==================== 查询 ====================

    public FinVoucherDO get(Long id) {
        FinVoucherDO v = id == null ? null : mapper.selectById(id);
        if (v == null) throw BizException.of(FinanceErrorCodes.NOT_EXISTS, "凭证");
        return v;
    }

    /** 期间内未过账的凭证数（月结检查） */
    public long unposted(String period) {
        return mapper.selectCount(new LambdaQueryWrapper<FinVoucherDO>().eq(FinVoucherDO::getPeriod, period).ne(FinVoucherDO::getVoucherStatus, VoucherStatus.POSTED.name()));
    }

    public long count(String period) {
        return mapper.selectCount(new LambdaQueryWrapper<FinVoucherDO>().eq(FinVoucherDO::getPeriod, period));
    }

    public PageResult<VoucherRow> page(VoucherQuery q) {
        LambdaQueryWrapper<FinVoucherDO> w = query(q);
        if (w == null) return new PageResult<>(List.of(), 0L);
        IPage<FinVoucherDO> p = mapper.selectPage(new Page<>(q.getPageNo(), q.getPageSize()), w);
        return new PageResult<>(rows(p.getRecords()), p.getTotal());
    }

    public List<FinVoucherDO> list(VoucherQuery q) {
        LambdaQueryWrapper<FinVoucherDO> w = query(q);
        return w == null ? List.of() : mapper.selectList(w);
    }

    private LambdaQueryWrapper<FinVoucherDO> query(VoucherQuery q) {
        List<Long> byLine = null;
        if (StringUtils.hasText(q.getAccountCode()) || StringUtils.hasText(q.getSummary())) {
            byLine = lineMapper.selectList(new LambdaQueryWrapper<FinVoucherLineDO>()
                            .likeRight(StringUtils.hasText(q.getAccountCode()), FinVoucherLineDO::getAccountCode, q.getAccountCode())
                            .like(StringUtils.hasText(q.getSummary()), FinVoucherLineDO::getSummary, q.getSummary()))
                    .stream().map(FinVoucherLineDO::getVoucherId).distinct().toList();
            if (byLine.isEmpty()) return null;
        }
        List<String> statuses = StringUtils.hasText(q.getStatuses()) ? Arrays.stream(q.getStatuses().split(",")).map(String::trim).toList() : List.of();
        return new LambdaQueryWrapper<FinVoucherDO>()
                .eq(StringUtils.hasText(q.getPeriod()), FinVoucherDO::getPeriod, q.getPeriod())
                .like(StringUtils.hasText(q.getVoucherNo()), FinVoucherDO::getDocNo, q.getVoucherNo())
                .in(!statuses.isEmpty(), FinVoucherDO::getVoucherStatus, statuses)
                .eq(StringUtils.hasText(q.getSource()), FinVoucherDO::getVoucherSource, q.getSource())
                .eq(q.getCreatorId() != null, FinVoucherDO::getCreatedBy, q.getCreatorId())
                .in(byLine != null, FinVoucherDO::getId, byLine)
                .orderByDesc(FinVoucherDO::getPeriod).orderByAsc(FinVoucherDO::getVoucherSeq);
    }

    private List<VoucherRow> rows(List<FinVoucherDO> list) {
        Map<Long, UserDTO> users = support.users(list.stream().flatMap(v -> java.util.stream.Stream.of(v.getCreatedBy(), v.getAuditorId(), v.getPosterId())).toList());
        Map<Long, String> firstSummary = new HashMap<>();
        for (FinVoucherLineDO l : lineMapper.selectByParents(list.stream().map(FinVoucherDO::getId).toList())) {
            if (l.getLineNo() == 1) firstSummary.put(l.getVoucherId(), l.getSummary());
        }
        return list.stream().map(v -> new VoucherRow(v.getId(), v.getDocNo(), v.getPeriod(), v.getDocDate(), firstSummary.get(v.getId()), v.getTotalDebit(),
                v.getTotalCredit(), v.getAttachmentCount() == null ? 0 : v.getAttachmentCount(), v.getVoucherSource(), v.getBizType(), v.getVoucherStatus(),
                FinSupport.name(users, v.getCreatedBy()), FinSupport.name(users, v.getAuditorId()), FinSupport.name(users, v.getPosterId()), v.getCreatedAt())).toList();
    }

    public VoucherDetail detail(Long id) {
        FinVoucherDO v = get(id);
        return new VoucherDetail(rows(List.of(v)).get(0), v.getRemark(), v.getCreatedBy(), v.getAuditedAt(), v.getPostedAt(), lineVOs(lineMapper.selectByParent(id)));
    }

    public List<VoucherLineVO> lineVOs(List<FinVoucherLineDO> lines) {
        Map<String, FinAccountDO> accounts = accounts();
        Map<Long, CustomerDTO> cs = support.customers(lines.stream().map(FinVoucherLineDO::getAuxCustomerId).toList());
        Map<Long, SupplierDTO> ss = support.suppliers(lines.stream().map(FinVoucherLineDO::getAuxSupplierId).filter(Objects::nonNull).toList());
        Map<Long, String> depts = support.orgNames(lines.stream().map(FinVoucherLineDO::getAuxDeptId).toList());
        Map<Long, MaterialDTO> ms = support.materials(lines.stream().map(FinVoucherLineDO::getAuxMaterialId).toList());
        List<VoucherLineVO> list = new ArrayList<>();
        int no = 1;
        for (FinVoucherLineDO l : lines) {
            FinAccountDO a = accounts.get(l.getAccountCode());
            SupplierDTO s = l.getAuxSupplierId() == null ? null : ss.get(l.getAuxSupplierId());
            MaterialDTO m = ms.get(l.getAuxMaterialId());
            list.add(new VoucherLineVO(l.getId(), l.getLineNo() == null ? no : l.getLineNo(), l.getSummary(), l.getAccountCode(),
                    a == null ? null : SettingService.fullName(a, accounts), l.getDebit(), l.getCredit(), l.getCurrency(), l.getFcAmount(), l.getExchangeRate(),
                    l.getAuxCustomerId(), FinSupport.customerName(cs.get(l.getAuxCustomerId())), l.getAuxSupplierId(), s == null ? null : s.name(),
                    l.getAuxDeptId(), depts.get(l.getAuxDeptId()), l.getAuxMaterialId(), m == null ? null : m.code() + " " + m.name(), l.getAuxProjectId(),
                    l.getSourceType(), l.getSourceId()));
            no++;
        }
        return list;
    }

    /** 导出（通用 Excel）：凭证 × 分录 */
    public record ExportRow(String voucherNo, LocalDate voucherDate, String status, String summary, String accountCode, String accountName, BigDecimal debit,
                            BigDecimal credit, String currency, BigDecimal fcAmount, BigDecimal exchangeRate, String aux) {
    }

    public List<ExportRow> exportRows(VoucherQuery q, int limit) {
        List<FinVoucherDO> vs = list(q).stream().limit(limit).toList();
        Map<Long, List<FinVoucherLineDO>> lines = lineMapper.selectByParents(vs.stream().map(FinVoucherDO::getId).toList()).stream()
                .collect(Collectors.groupingBy(FinVoucherLineDO::getVoucherId));
        List<ExportRow> rows = new ArrayList<>();
        for (FinVoucherDO v : vs) {
            for (VoucherLineVO l : lineVOs(lines.getOrDefault(v.getId(), List.of()))) {
                String aux = java.util.stream.Stream.of(l.auxCustomerName(), l.auxSupplierName(), l.auxDeptName(), l.auxMaterialName())
                        .filter(Objects::nonNull).collect(Collectors.joining(" / "));
                rows.add(new ExportRow(v.getDocNo(), v.getDocDate(), VoucherStatus.valueOf(v.getVoucherStatus()).label(), l.summary(), l.accountCode(),
                        l.accountName(), l.debit(), l.credit(), l.currency(), l.fcAmount(), l.exchangeRate(), aux));
            }
        }
        return rows;
    }
}
