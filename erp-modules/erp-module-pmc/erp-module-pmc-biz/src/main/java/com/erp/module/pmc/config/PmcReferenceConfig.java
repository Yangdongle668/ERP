package com.erp.module.pmc.config;

import com.erp.framework.mybatis.ModuleOrgReferences;
import com.erp.module.system.api.org.OrgReferenceChecker;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** PMC对系统管理数据的引用检查：组织（删除） */
@Configuration
public class PmcReferenceConfig {

    /** SYS-ORG-R07：PMC单据的公司 / 部门 */
    @Bean
    public OrgReferenceChecker pmcOrgChecker(ApplicationContext context) {
        return new ModuleOrgReferences(context, "com.erp.module.pmc.")::isReferenced;
    }
}
