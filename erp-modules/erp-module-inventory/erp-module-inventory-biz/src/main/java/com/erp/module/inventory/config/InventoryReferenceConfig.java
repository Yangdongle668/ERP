package com.erp.module.inventory.config;

import com.erp.framework.mybatis.ModuleOrgReferences;
import com.erp.module.inventory.dal.dataobject.WarehouseDO;
import com.erp.module.system.api.org.OrgReferenceChecker;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 仓库对系统管理数据的引用检查：组织（删除） */
@Configuration
public class InventoryReferenceConfig {

    /** SYS-ORG-R07：仓库单据与主数据的公司 / 部门 */
    @Bean
    public OrgReferenceChecker inventoryOrgChecker(ApplicationContext context) {
        return new ModuleOrgReferences(context, "com.erp.module.inventory.", WarehouseDO.class)::isReferenced;
    }
}
