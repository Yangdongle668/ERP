package com.erp.module.engineering.config;

import com.erp.framework.security.LoginUser;
import com.erp.framework.security.SecurityUtils;
import com.erp.module.engineering.service.BomService;
import com.erp.module.engineering.service.CertificationService;
import com.erp.module.engineering.service.EcnService;
import com.erp.module.engineering.service.MaterialService;
import com.erp.module.engineering.service.RoutingService;
import com.erp.module.engineering.service.SampleService;
import com.erp.module.engineering.service.ToolingService;
import com.erp.module.system.api.file.FileAccessChecker;
import com.erp.module.system.api.file.FileAccessChecker.Rule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/** 研发工程附件访问控制（SYS-FIL-R04）：有单据查看权限可查看附件；有编辑类权限可上传、删除 */
@Configuration
public class EngineeringFileAccessConfig {

    @Bean
    public FileAccessChecker engineeringFileAccessChecker() {
        return FileAccessChecker.byPermissions(Map.ofEntries(
                Map.entry(MaterialService.BIZ_TYPE, Rule.of("eng:material:query", "eng:material:create", "eng:material:update")),
                Map.entry(BomService.BIZ_TYPE, Rule.of("eng:bom:query", "eng:bom:create", "eng:bom:update")),
                Map.entry(RoutingService.BIZ_TYPE, Rule.of("eng:routing:query", "eng:routing:create", "eng:routing:update")),
                Map.entry(EcnService.BIZ_TYPE, Rule.of("eng:ecn:query", "eng:ecn:create", "eng:ecn:update")),
                Map.entry(CertificationService.BIZ_TYPE, Rule.of("eng:cert:query", "eng:cert:create", "eng:cert:update")),
                Map.entry(ToolingService.BIZ_TYPE, Rule.of("eng:tooling:query", "eng:tooling:create", "eng:tooling:update", "eng:tooling:record")),
                Map.entry(SampleService.BIZ_TYPE, Rule.of("eng:sample:query", "eng:sample:create", "eng:sample:update", "eng:sample:feedback", "eng:sample:ship"))), EngineeringFileAccessConfig::hasPermission);
    }

    private static boolean hasPermission(String permission) {
        LoginUser u = SecurityUtils.getLoginUserOrNull();
        return u == null || u.hasPermission(permission);
    }
}
