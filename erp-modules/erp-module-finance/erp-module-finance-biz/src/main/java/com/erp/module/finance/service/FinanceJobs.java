package com.erp.module.finance.service;

import com.erp.module.finance.dal.dataobject.FinPayableDO;
import com.erp.module.finance.service.ap.PayableService;
import com.erp.module.purchase.api.supplier.SupplierDTO;
import com.erp.module.system.api.job.ErpJob;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 财务定时任务 */
@Component
public class FinanceJobs {

    private final PayableService payableService;
    private final FinSupport support;

    public FinanceJobs(PayableService payableService, FinSupport support) {
        this.payableService = payableService;
        this.support = support;
    }

    /** FIN-PAY-R07：每周一给应付会计推送本周到期（含已逾期）的未付应付清单 */
    @ErpJob(code = "FIN_AP_DUE_WEEKLY", name = "本周到期应付提醒", cron = "0 50 8 ? * MON")
    public String apDueWeekly() {
        LocalDate sunday = LocalDate.now().with(DayOfWeek.SUNDAY);
        List<FinPayableDO> list = payableService.dueBy(sunday);
        if (list.isEmpty()) return "本周无到期应付";
        Map<Long, SupplierDTO> ss = support.suppliers(list.stream().map(FinPayableDO::getSupplierId).toList());
        Map<String, BigDecimal> byCurrency = list.stream().collect(Collectors.groupingBy(FinPayableDO::getCurrency,
                Collectors.reducing(BigDecimal.ZERO, p -> p.getTotalAmount().subtract(FinSupport.nz(p.getVerifiedAmount())), BigDecimal::add)));
        String totals = byCurrency.entrySet().stream().map(e -> e.getKey() + " " + FinSupport.plain(e.getValue())).collect(Collectors.joining("，"));
        String detail = list.stream().limit(20).map(p -> {
            SupplierDTO s = ss.get(p.getSupplierId());
            return p.getDocNo() + " " + (s == null ? "" : s.name()) + " 到期 " + p.getDueDate() + " "
                    + p.getCurrency() + " " + FinSupport.plain(p.getTotalAmount().subtract(FinSupport.nz(p.getVerifiedAmount())));
        }).collect(Collectors.joining("\n"));
        support.message(support.usersWithPermission("fin:payable:confirm"), "本周到期应付 " + list.size() + " 张（" + totals + "）",
                detail + (list.size() > 20 ? "\n……" : ""), "/finance/payable?dueTo=" + sunday);
        return "推送 " + list.size() + " 张到期应付";
    }
}
