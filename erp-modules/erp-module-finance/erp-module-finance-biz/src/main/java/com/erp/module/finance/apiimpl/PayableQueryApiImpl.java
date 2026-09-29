package com.erp.module.finance.apiimpl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.common.util.Decimals;
import com.erp.module.finance.api.query.PayableQueryApi;
import com.erp.module.finance.dal.dataobject.FinPayableDO;
import com.erp.module.finance.dal.dataobject.FinPaymentDO;
import com.erp.module.finance.dal.mapper.FinPayableMapper;
import com.erp.module.finance.dal.mapper.FinPaymentMapper;
import com.erp.module.finance.service.ArStatus;
import com.erp.module.finance.service.CashStatus;
import com.erp.module.finance.service.FinSupport;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** 应付余额（资材）：已确认未付应付 − 未冲销预付（本位币） */
@Component
public class PayableQueryApiImpl implements PayableQueryApi {

    private final FinPayableMapper payableMapper;
    private final FinPaymentMapper paymentMapper;

    public PayableQueryApiImpl(FinPayableMapper payableMapper, FinPaymentMapper paymentMapper) {
        this.payableMapper = payableMapper;
        this.paymentMapper = paymentMapper;
    }

    @Override
    public BigDecimal getBalance(Long supplierId) {
        BigDecimal balance = BigDecimal.ZERO;
        for (FinPayableDO p : payableMapper.selectList(new LambdaQueryWrapper<FinPayableDO>().eq(FinPayableDO::getSupplierId, supplierId)
                .eq(FinPayableDO::getApStatus, ArStatus.CONFIRMED.name()).apply("verified_amount <> total_amount"))) {
            BigDecimal total = FinSupport.nz(p.getTotalAmount());
            if (total.signum() == 0) continue;
            balance = balance.add(FinSupport.nz(p.getTotalAmountBase()).multiply(total.subtract(FinSupport.nz(p.getVerifiedAmount())))
                    .divide(total, 2, RoundingMode.HALF_UP));
        }
        for (FinPaymentDO p : paymentMapper.selectList(new LambdaQueryWrapper<FinPaymentDO>().eq(FinPaymentDO::getSupplierId, supplierId)
                .eq(FinPaymentDO::getRequestType, "PREPAYMENT").eq(FinPaymentDO::getPaymentStatus, CashStatus.CONFIRMED.name()))) {
            balance = balance.subtract(FinSupport.toBase(FinSupport.nz(p.getAmount()).subtract(FinSupport.nz(p.getAllocatedAmount())), p.getExchangeRate()));
        }
        return Decimals.amount(balance);
    }
}
