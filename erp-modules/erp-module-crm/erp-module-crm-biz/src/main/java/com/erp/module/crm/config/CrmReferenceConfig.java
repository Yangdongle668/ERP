package com.erp.module.crm.config;

import com.erp.framework.mybatis.ModuleOrgReferences;
import com.erp.module.crm.dal.dataobject.CustomerDO;
import com.erp.module.crm.dal.dataobject.OpportunityDO;
import com.erp.module.system.api.org.OrgReferenceChecker;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** CRM对系统管理数据的引用检查：组织（删除） */
@Configuration
public class CrmReferenceConfig {

    /** SYS-ORG-R07：CRM单据与主数据的公司 / 部门 */
    @Bean
    public OrgReferenceChecker crmOrgChecker(ApplicationContext context) {
        return new ModuleOrgReferences(context, "com.erp.module.crm.", CustomerDO.class, OpportunityDO.class)::isReferenced;
    }
}
