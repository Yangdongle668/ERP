package com.erp.module.finance.apiimpl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.common.util.Decimals;
import com.erp.module.crm.api.credit.CreditUsage;
import com.erp.module.crm.api.credit.CreditUsageProvider;
import com.erp.module.finance.api.query.FinPeriodApi;
import com.erp.module.finance.api.query.ReceivableQueryApi;
import com.erp.module.finance.dal.dataobject.FinPeriodDO;
import com.erp.module.finance.dal.dataobject.FinReceiptDO;
import com.erp.module.finance.dal.dataobject.FinReceivableDO;
import com.erp.module.finance.dal.dataobject.FinReceivableLineDO;
import com.erp.module.finance.dal.dataobject.FinVerificationDO;
import com.erp.module.finance.dal.mapper.FinPeriodMapper;
import com.erp.module.finance.dal.mapper.FinReceiptMapper;
import com.erp.module.finance.dal.mapper.FinReceivableLineMapper;
import com.erp.module.finance.dal.mapper.FinReceivableMapper;
import com.erp.module.finance.dal.mapper.FinVerificationMapper;
import com.erp.module.finance.service.ArStatus;
import com.erp.module.finance.service.CashStatus;
import com.erp.module.finance.service.FinSupport;
import com.erp.module.finance.service.ar.ReceivableService;
import com.erp.module.finance.service.verify.VerificationService;
import com.erp.module.inventory.api.period.FinancePeriodChecker;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 财务对外查询（不依赖 FinSupport，避免与 CRM 信用刷新形成循环依赖）：应收余额、订单已收款、会计期间；同时实现 CRM 信用占用（应收余额、逾期）与仓库反结账校验扩展点。
 */
@Component
public class FinanceQueryApiImpl implements ReceivableQueryApi, FinPeriodApi, CreditUsageProvider, FinancePeriodChecker {

    private final FinReceivableMapper receivableMapper;
    private final FinReceivableLineMapper receivableLineMapper;
    private final FinReceiptMapper receiptMapper;
    private final FinVerificationMapper verificationMapper;
    private final FinPeriodMapper periodMapper;

    public FinanceQueryApiImpl(FinReceivableMapper receivableMapper, FinReceivableLineMapper receivableLineMapper, FinReceiptMapper receiptMapper,
                               FinVerificationMapper verificationMapper, FinPeriodMapper periodMapper) {
        this.receivableMapper = receivableMapper;
        this.receivableLineMapper = receivableLineMapper;
        this.receiptMapper = receiptMapper;
        this.verificationMapper = verificationMapper;
        this.periodMapper = periodMapper;
    }

    // ==================== 应收 ====================

    @Override
    public BigDecimal getBalance(Long customerId) {
        return usage(List.of(customerId)).getOrDefault(customerId, new CreditUsage(BigDecimal.ZERO, BigDecimal.ZERO, null)).receivableBalance();
    }

    @Override
    public BigDecimal getOverdue(Long customerId) {
        return usage(List.of(customerId)).getOrDefault(customerId, new CreditUsage(BigDecimal.ZERO, BigDecimal.ZERO, null)).overdueAmount();
    }

    /** CRM 信用占用：应收余额 = 未核销应收 − 未核销预收；逾期 = 到期日早于今天的未核销蓝字应收（本位币） */
    @Override
    public Map<Long, CreditUsage> usage(Collection<Long> customerIds) {
        List<Long> ids = customerIds.stream().filter(Objects::nonNull).distinct().toList();
        Map<Long, CreditUsage> result = new HashMap<>();
        if (ids.isEmpty()) return result;
        Map<Long, BigDecimal[]> sums = new HashMap<>();
        LocalDate today = LocalDate.now();
        for (FinReceivableDO r : receivableMapper.selectList(new LambdaQueryWrapper<FinReceivableDO>().in(FinReceivableDO::getCustomerId, ids)
                .eq(FinReceivableDO::getArStatus, ArStatus.CONFIRMED.name()).apply("verified_amount <> total_amount"))) {
            BigDecimal[] s = sums.computeIfAbsent(r.getCustomerId(), k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            BigDecimal open = ReceivableService.openBase(r);
            s[0] = s[0].add(open);
            if (open.signum() > 0 && r.getDueDate() != null && r.getDueDate().isBefore(today)) s[1] = s[1].add(open);
        }
        for (FinReceiptDO r : receiptMapper.selectList(new LambdaQueryWrapper<FinReceiptDO>().in(FinReceiptDO::getCustomerId, ids)
                .eq(FinReceiptDO::getReceiptType, "ADVANCE").eq(FinReceiptDO::getReceiptStatus, CashStatus.CONFIRMED.name()))) {
            BigDecimal open = VerificationService.receiptOpen(r);
            if (open.signum() == 0) continue;
            BigDecimal[] s = sums.computeIfAbsent(r.getCustomerId(), k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            s[0] = s[0].subtract(FinSupport.toBase(open, r.getExchangeRate()));
        }
        for (Long id : ids) {
            BigDecimal[] s = sums.getOrDefault(id, new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            result.put(id, new CreditUsage(Decimals.amount(s[0]), Decimals.amount(s[1]), null));
        }
        return result;
    }

    /** 订单已收款（原币）= 该订单预收款 + 普通收款核销到含该订单应收的金额（按应收明细订单占比） */
    @Override
    public BigDecimal getOrderReceived(Long orderId) {
        BigDecimal advance = receiptMapper.selectList(new LambdaQueryWrapper<FinReceiptDO>().eq(FinReceiptDO::getOrderId, orderId)
                        .eq(FinReceiptDO::getReceiptType, "ADVANCE").eq(FinReceiptDO::getReceiptStatus, CashStatus.CONFIRMED.name()))
                .stream().map(VerificationService::receiptGross).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<Long> arIds = receivableLineMapper.selectList(new LambdaQueryWrapper<FinReceivableLineDO>().eq(FinReceivableLineDO::getOrderId, orderId))
                .stream().map(FinReceivableLineDO::getReceivableId).distinct().toList();
        if (arIds.isEmpty()) return Decimals.amount(advance);
        Map<Long, List<FinReceivableLineDO>> lines = receivableLineMapper.selectByParents(arIds).stream()
                .collect(Collectors.groupingBy(FinReceivableLineDO::getReceivableId));
        BigDecimal received = BigDecimal.ZERO;
        for (FinVerificationDO v : verificationMapper.selectList(new LambdaQueryWrapper<FinVerificationDO>().eq(FinVerificationDO::getVerifyType, "RECEIPT_AR")
                .eq(FinVerificationDO::getDocAType, VerificationService.DOC_RECEIPT).eq(FinVerificationDO::getDocBType, VerificationService.DOC_RECEIVABLE)
                .in(FinVerificationDO::getDocBId, arIds).eq(FinVerificationDO::getReversed, false))) {
            List<FinReceivableLineDO> ls = lines.getOrDefault(v.getDocBId(), List.of());
            BigDecimal all = ls.stream().map(FinReceivableLineDO::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal mine = ls.stream().filter(l -> orderId.equals(l.getOrderId())).map(FinReceivableLineDO::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            if (all.signum() == 0) continue;
            received = received.add(v.getAmount().multiply(mine).divide(all, 2, RoundingMode.HALF_UP));
        }
        return Decimals.amount(advance.add(received));
    }

    // ==================== 会计期间 ====================

    @Override
    public boolean isClosed(String period) {
        FinPeriodDO p = periodMapper.selectOne(new LambdaQueryWrapper<FinPeriodDO>().eq(FinPeriodDO::getPeriod, period));
        return p != null && "CLOSED".equals(p.getPeriodStatus());
    }
}
