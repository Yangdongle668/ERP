package com.erp.module.quality.config;

import com.erp.framework.security.LoginUser;
import com.erp.framework.security.SecurityUtils;
import com.erp.module.system.api.file.FileAccessChecker;
import com.erp.module.system.api.file.FileAccessChecker.Rule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;

/** 品质附件访问控制（SYS-FIL-R04）：有单据查看权限可查看附件；有编辑类权限可上传、删除 */
@Configuration
public class QualityFileAccessConfig {

    @Bean
    public FileAccessChecker qualityFileAccessChecker() {
        return FileAccessChecker.byPermissions(Map.ofEntries(
                Map.entry(QualityModuleConfig.INSPECTION, new Rule(List.of("qc:iqc:query", "qc:ipqc:query", "qc:fqc:query", "qc:oqc:query", "qc:return:query"),
                        List.of("qc:inspection:create", "qc:iqc:inspect", "qc:ipqc:inspect", "qc:fqc:inspect", "qc:oqc:inspect", "qc:return:inspect", "qc:iqc:judge", "qc:ipqc:judge", "qc:fqc:judge", "qc:oqc:judge", "qc:return:judge"))),
                Map.entry(QualityModuleConfig.STANDARD, Rule.of("qc:standard:query", "qc:standard:create", "qc:standard:update")),
                Map.entry(QualityModuleConfig.NCR, Rule.of("qc:ncr:query", "qc:ncr:create", "qc:ncr:update", "qc:ncr:submit")),
                Map.entry(QualityModuleConfig.CAPA, Rule.of("qc:capa:query", "qc:capa:create", "qc:capa:update", "qc:capa:verify", "qc:capa:close")),
                Map.entry(QualityModuleConfig.COMPLAINT, Rule.of("qc:complaint:query", "qc:complaint:create", "qc:complaint:update", "qc:complaint:reply", "qc:complaint:close")),
                Map.entry(QualityModuleConfig.SCAR, Rule.of("qc:scar:query", "qc:scar:create", "qc:scar:update", "qc:scar:send", "qc:scar:verify"))), QualityFileAccessConfig::hasPermission);
    }

    private static boolean hasPermission(String permission) {
        LoginUser u = SecurityUtils.getLoginUserOrNull();
        return u == null || u.hasPermission(permission);
    }
}
