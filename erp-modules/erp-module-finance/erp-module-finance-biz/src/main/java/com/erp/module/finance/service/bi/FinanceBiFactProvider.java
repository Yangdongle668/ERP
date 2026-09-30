package com.erp.module.finance.service.bi;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.common.util.Decimals;
import com.erp.module.bi.api.fact.BiFactProvider;
import com.erp.module.bi.api.fact.BiFacts.FinanceFact;
import com.erp.module.bi.api.fact.BiFacts.SalesFact;
import com.erp.module.finance.dal.dataobject.FinPayableDO;
import com.erp.module.finance.dal.dataobject.FinReceiptDO;
import com.erp.module.finance.dal.dataobject.FinReceivableDO;
import com.erp.module.finance.dal.dataobject.FinVerificationDO;
import com.erp.module.finance.dal.mapper.FinPayableMapper;
import com.erp.module.finance.dal.mapper.FinReceiptMapper;
import com.erp.module.finance.dal.mapper.FinReceivableMapper;
import com.erp.module.finance.dal.mapper.FinVerificationMapper;
import com.erp.module.finance.service.ArStatus;
import com.erp.module.finance.service.CashStatus;
import com.erp.module.finance.service.FinSupport;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * BI 财务事实（需求 13-01）：
 * <ul>
 *   <li>回款额：收款核销应收（RECEIPT_AR，按应收本位币，核销日）+ 预收款确认（收款日）；业务员由 BI 按客户补全</li>
 *   <li>往来余额：余额 = 截止日前已确认应收（应付）价税合计本位币 − 截止日前收款（付款、预付）核销金额；红蓝对冲净额为零不计；
 *       逾期 = 到期日早于期末（且早于今天）的单据按当前未核销金额</li>
 * </ul>
 */
@Component
public class FinanceBiFactProvider implements BiFactProvider {

    static final List<String> AR_SETTLE = List.of("RECEIPT_AR", "ADVANCE_AR");
    static final List<String> AP_SETTLE = List.of("PAYMENT_AP", "PREPAY_AP");

    private final FinVerificationMapper verificationMapper;
    private final FinReceiptMapper receiptMapper;
    private final FinReceivableMapper receivableMapper;
    private final FinPayableMapper payableMapper;

    public FinanceBiFactProvider(FinVerificationMapper verificationMapper, FinReceiptMapper receiptMapper, FinReceivableMapper receivableMapper,
                                 FinPayableMapper payableMapper) {
        this.verificationMapper = verificationMapper;
        this.receiptMapper = receiptMapper;
        this.receivableMapper = receivableMapper;
        this.payableMapper = payableMapper;
    }

    @Override
    public List<SalesFact> salesFacts(LocalDate from, LocalDate to) {
        List<SalesFact> facts = new ArrayList<>();
        for (FinVerificationDO v : verificationMapper.selectList(new LambdaQueryWrapper<FinVerificationDO>().eq(FinVerificationDO::getVerifyType, "RECEIPT_AR")
                .eq(FinVerificationDO::getReversed, false).ge(FinVerificationDO::getVerifiedAt, from.atStartOfDay())
                .lt(FinVerificationDO::getVerifiedAt, to.plusDays(1).atStartOfDay()))) {
            facts.add(receipt(v.getVerifiedAt().toLocalDate(), v.getPartnerId(), FinSupport.nz(v.getAmountBaseB())));
        }
        for (FinReceiptDO r : receiptMapper.selectList(new LambdaQueryWrapper<FinReceiptDO>().eq(FinReceiptDO::getReceiptType, "ADVANCE")
                .eq(FinReceiptDO::getReceiptStatus, CashStatus.CONFIRMED.name()).between(FinReceiptDO::getReceiptDate, from, to))) {
            facts.add(receipt(r.getReceiptDate(), r.getCustomerId(), FinSupport.nz(r.getAmountBase())));
        }
        return facts;
    }

    private static SalesFact receipt(LocalDate date, Long customerId, BigDecimal amount) {
        return new SalesFact(date, customerId, null, null, null, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0, 0, Decimals.amount(amount));
    }

    @Override
    public List<FinanceFact> financeFacts(String period) {
        YearMonth ym = YearMonth.parse(period, FinSupport.PERIOD);
        LocalDate start = ym.atDay(1);
        LocalDate end = ym.atEndOfMonth();
        List<FinanceFact> facts = new ArrayList<>();
        // 应收
        List<FinReceivableDO> ars = receivableMapper.selectList(new LambdaQueryWrapper<FinReceivableDO>().eq(FinReceivableDO::getArStatus, ArStatus.CONFIRMED.name())
                .le(FinReceivableDO::getBizDate, end));
        Map<Long, BigDecimal[]> ar = new HashMap<>();
        for (FinReceivableDO r : ars) {
            BigDecimal base = FinSupport.toBase(r.getTotalAmount(), r.getExchangeRate());
            BigDecimal[] s = ar.computeIfAbsent(r.getCustomerId(), k -> zeros());
            if (r.getBizDate().isBefore(start)) s[0] = s[0].add(base);
            else s[1] = s[1].add(base);
            if (r.getDueDate() != null && r.getDueDate().isBefore(min(end.plusDays(1), LocalDate.now())) && FinSupport.nz(r.getTotalAmount()).signum() > 0) {
                s[3] = s[3].add(FinSupport.toBase(FinSupport.nz(r.getTotalAmount()).subtract(FinSupport.nz(r.getVerifiedAmount())), r.getExchangeRate()));
            }
        }
        settle(ar, AR_SETTLE, start, end);
        ar.forEach((id, s) -> facts.add(fact(period, "CUSTOMER", id, s)));
        // 应付
        Map<Long, BigDecimal[]> ap = new HashMap<>();
        for (FinPayableDO p : payableMapper.selectList(new LambdaQueryWrapper<FinPayableDO>().eq(FinPayableDO::getApStatus, ArStatus.CONFIRMED.name())
                .le(FinPayableDO::getBizDate, end))) {
            BigDecimal base = FinSupport.toBase(p.getTotalAmount(), p.getExchangeRate());
            BigDecimal[] s = ap.computeIfAbsent(p.getSupplierId(), k -> zeros());
            if (p.getBizDate().isBefore(start)) s[0] = s[0].add(base);
            else s[1] = s[1].add(base);
            if (p.getDueDate() != null && p.getDueDate().isBefore(min(end.plusDays(1), LocalDate.now()))) {
                s[3] = s[3].add(FinSupport.toBase(FinSupport.nz(p.getTotalAmount()).subtract(FinSupport.nz(p.getVerifiedAmount())), p.getExchangeRate()));
            }
        }
        settle(ap, AP_SETTLE, start, end);
        ap.forEach((id, s) -> facts.add(fact(period, "SUPPLIER", id, s)));
        return facts;
    }

    /** s[0] 期初（先放期前发生额，再减期前核销）、s[1] 本期增加、s[2] 本期核销、s[3] 逾期 */
    private void settle(Map<Long, BigDecimal[]> sums, List<String> types, LocalDate start, LocalDate end) {
        LocalDateTime startAt = start.atStartOfDay();
        for (FinVerificationDO v : verificationMapper.selectList(new LambdaQueryWrapper<FinVerificationDO>().in(FinVerificationDO::getVerifyType, types)
                .eq(FinVerificationDO::getReversed, false).lt(FinVerificationDO::getVerifiedAt, end.plusDays(1).atStartOfDay()))) {
            BigDecimal[] s = sums.computeIfAbsent(v.getPartnerId(), k -> zeros());
            BigDecimal amt = FinSupport.nz(v.getAmountBaseB());
            if (v.getVerifiedAt().isBefore(startAt)) s[0] = s[0].subtract(amt);
            else s[2] = s[2].add(amt);
        }
    }

    private static FinanceFact fact(String period, String type, Long id, BigDecimal[] s) {
        return new FinanceFact(period, type, id, Decimals.amount(s[0]), Decimals.amount(s[1]), Decimals.amount(s[2]),
                Decimals.amount(s[0].add(s[1]).subtract(s[2])), Decimals.amount(s[3].max(BigDecimal.ZERO)));
    }

    private static BigDecimal[] zeros() {
        return new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO};
    }

    private static LocalDate min(LocalDate a, LocalDate b) {
        return a.isBefore(b) ? a : b;
    }
}
