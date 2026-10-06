package com.erp.module.finance.config;

import com.erp.framework.security.LoginUser;
import com.erp.framework.security.SecurityUtils;
import com.erp.module.system.api.file.FileAccessChecker;
import com.erp.module.system.api.file.FileAccessChecker.Rule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/** 财务附件访问控制（SYS-FIL-R04）：有单据查看权限可查看附件；有编辑类权限可上传、删除 */
@Configuration
public class FinanceFileAccessConfig {

    @Bean
    public FileAccessChecker financeFileAccessChecker() {
        return FileAccessChecker.byPermissions(Map.ofEntries(
                Map.entry(FinanceModuleConfig.RECEIVABLE, Rule.of("fin:receivable:query", "fin:receivable:confirm", "fin:receivable:create-other", "fin:receivable:invoice")),
                Map.entry(FinanceModuleConfig.OTHER_RECEIVABLE, Rule.of("fin:receivable:query", "fin:receivable:confirm", "fin:receivable:create-other")),
                Map.entry(FinanceModuleConfig.SALES_INVOICE, Rule.of("fin:receivable:query", "fin:receivable:invoice")),
                Map.entry(FinanceModuleConfig.RECEIPT, Rule.of("fin:receipt:query", "fin:receipt:create", "fin:receipt:update", "fin:receipt:confirm", "fin:receipt:verify")),
                Map.entry(FinanceModuleConfig.PAYABLE, Rule.of("fin:payable:query", "fin:payable:confirm", "fin:payable:create-other", "fin:payable:invoice")),
                Map.entry(FinanceModuleConfig.OTHER_PAYABLE, Rule.of("fin:payable:query", "fin:payable:confirm", "fin:payable:create-other")),
                Map.entry(FinanceModuleConfig.PURCHASE_INVOICE, Rule.of("fin:payable:query", "fin:payable:invoice")),
                Map.entry(FinanceModuleConfig.PAYMENT_REQUEST, Rule.of("fin:payment-request:query", "fin:payment-request:create", "fin:payment-request:submit")),
                Map.entry(FinanceModuleConfig.PAYMENT, Rule.of("fin:payment:query", "fin:payment:create", "fin:payment:confirm", "fin:payment:verify")),
                Map.entry(FinanceModuleConfig.VOUCHER, Rule.of("fin:voucher:query", "fin:voucher:create", "fin:voucher:update"))), FinanceFileAccessConfig::hasPermission);
    }

    private static boolean hasPermission(String permission) {
        LoginUser u = SecurityUtils.getLoginUserOrNull();
        return u == null || u.hasPermission(permission);
    }
}
