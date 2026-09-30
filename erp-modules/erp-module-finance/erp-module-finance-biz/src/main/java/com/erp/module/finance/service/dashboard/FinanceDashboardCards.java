package com.erp.module.finance.service.dashboard;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.common.util.Decimals;
import com.erp.module.finance.dal.dataobject.FinPayableDO;
import com.erp.module.finance.dal.dataobject.FinReceivableDO;
import com.erp.module.finance.dal.dataobject.FinVerificationDO;
import com.erp.module.finance.dal.mapper.FinPayableMapper;
import com.erp.module.finance.dal.mapper.FinReceivableMapper;
import com.erp.module.finance.dal.mapper.FinVerificationMapper;
import com.erp.module.finance.service.ArStatus;
import com.erp.module.finance.service.FinSupport;
import com.erp.module.workbench.api.dashboard.CardData;
import com.erp.module.workbench.api.dashboard.DashboardCard;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 工作台看板卡片（需求 02-01 第 3 节）：本月回款额、逾期应收、本周到期应付（本位币） */
@Configuration
public class FinanceDashboardCards {

    private final FinVerificationMapper verificationMapper;
    private final FinReceivableMapper receivableMapper;
    private final FinPayableMapper payableMapper;

    public FinanceDashboardCards(FinVerificationMapper verificationMapper, FinReceivableMapper receivableMapper, FinPayableMapper payableMapper) {
        this.verificationMapper = verificationMapper;
        this.receivableMapper = receivableMapper;
        this.payableMapper = payableMapper;
    }

    /** 本月收款核销金额（收款 / 预收冲应收，按应收本位币） */
    @Bean
    public DashboardCard finReceivedMonthCard() {
        return DashboardCard.of("FIN_RECEIVED_MONTH", "本月回款额", "fin:receipt:query|sales:order:query", 30, "/finance/receipt", () -> {
            YearMonth ym = YearMonth.now();
            return CardData.amount(received(ym), null).compare(received(ym.minusMonths(1)), "环比");
        });
    }

    @Bean
    public DashboardCard finArOverdueCard() {
        return DashboardCard.of("FIN_AR_OVERDUE", "逾期应收", "fin:receivable:query", 40, "/finance/report", () -> {
            LocalDate today = LocalDate.now();
            BigDecimal sum = BigDecimal.ZERO;
            Set<Long> customers = new HashSet<>();
            for (FinReceivableDO r : receivableMapper.selectList(new LambdaQueryWrapper<FinReceivableDO>().eq(FinReceivableDO::getArStatus, ArStatus.CONFIRMED.name())
                    .gt(FinReceivableDO::getTotalAmount, BigDecimal.ZERO).lt(FinReceivableDO::getDueDate, today).apply("verified_amount < total_amount"))) {
                sum = sum.add(FinSupport.toBase(FinSupport.nz(r.getTotalAmount()).subtract(FinSupport.nz(r.getVerifiedAmount())), r.getExchangeRate()));
                customers.add(r.getCustomerId());
            }
            return CardData.amount(Decimals.amount(sum), customers.size() + " 家客户").asCost();
        });
    }

    @Bean
    public DashboardCard finApWeekCard() {
        return DashboardCard.of("FIN_AP_WEEK", "本周到期应付", "fin:payable:query", 300, "/finance/payable", () -> {
            LocalDate monday = LocalDate.now().with(DayOfWeek.MONDAY);
            BigDecimal sum = BigDecimal.ZERO;
            Set<Long> suppliers = new HashSet<>();
            for (FinPayableDO p : payableMapper.selectList(new LambdaQueryWrapper<FinPayableDO>().eq(FinPayableDO::getApStatus, ArStatus.CONFIRMED.name())
                    .between(FinPayableDO::getDueDate, monday, monday.plusDays(6)).apply("verified_amount < total_amount"))) {
                sum = sum.add(FinSupport.toBase(FinSupport.nz(p.getTotalAmount()).subtract(FinSupport.nz(p.getVerifiedAmount())), p.getExchangeRate()));
                suppliers.add(p.getSupplierId());
            }
            return CardData.amount(Decimals.amount(sum), suppliers.size() + " 家供应商");
        });
    }

    private BigDecimal received(YearMonth ym) {
        return verificationMapper.selectList(new LambdaQueryWrapper<FinVerificationDO>().in(FinVerificationDO::getVerifyType, List.of("RECEIPT_AR", "ADVANCE_AR"))
                        .eq(FinVerificationDO::getReversed, false)
                        .ge(FinVerificationDO::getVerifiedAt, ym.atDay(1).atStartOfDay()).lt(FinVerificationDO::getVerifiedAt, ym.plusMonths(1).atDay(1).atStartOfDay()))
                .stream().map(v -> FinSupport.nz(v.getAmountBaseB())).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
