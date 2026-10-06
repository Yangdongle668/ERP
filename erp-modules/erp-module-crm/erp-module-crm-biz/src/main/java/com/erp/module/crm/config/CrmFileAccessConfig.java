package com.erp.module.crm.config;

import com.erp.framework.security.LoginUser;
import com.erp.framework.security.SecurityUtils;
import com.erp.module.crm.service.CustomerPartService;
import com.erp.module.system.api.file.FileAccessChecker;
import com.erp.module.system.api.file.FileAccessChecker.Rule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;

/** CRM 附件访问控制（SYS-FIL-R04）：有单据查看权限可查看附件；有编辑类权限可上传、删除 */
@Configuration
public class CrmFileAccessConfig {

    @Bean
    public FileAccessChecker crmFileAccessChecker() {
        return FileAccessChecker.byPermissions(Map.ofEntries(
                Map.entry(CrmModuleConfig.CUSTOMER, Rule.of("crm:customer:query", "crm:customer:create", "crm:customer:update")),
                Map.entry(CrmModuleConfig.FOLLOWUP, new Rule(List.of("crm:followup:query", "crm:customer:query"),
                        List.of("crm:followup:create", "crm:followup:update"))),
                Map.entry(CustomerPartService.BIZ_TYPE, Rule.of("crm:customer-part:query", "crm:customer-part:create", "crm:customer-part:update"))), CrmFileAccessConfig::hasPermission);
    }

    private static boolean hasPermission(String permission) {
        LoginUser u = SecurityUtils.getLoginUserOrNull();
        return u == null || u.hasPermission(permission);
    }
}
