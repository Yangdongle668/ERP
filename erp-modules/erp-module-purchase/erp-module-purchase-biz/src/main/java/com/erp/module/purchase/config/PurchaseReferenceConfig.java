package com.erp.module.purchase.config;

import com.erp.framework.mybatis.ModuleOrgReferences;
import com.erp.module.purchase.dal.dataobject.SupplierDO;
import com.erp.module.purchase.dal.mapper.OrderMapper;
import com.erp.module.system.api.currency.CurrencyReferenceChecker;
import com.erp.module.system.api.org.OrgReferenceChecker;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 资材对系统管理数据的引用检查：本位币（修改）、组织（删除） */
@Configuration
public class PurchaseReferenceConfig {

    /** SYS-CUR-R03：已有采购订单时不能修改本位币 */
    @Bean
    public CurrencyReferenceChecker purchaseCurrencyChecker(OrderMapper orderMapper) {
        return () -> orderMapper.exists(null);
    }

    /** SYS-ORG-R07：资材单据与供应商的公司 / 部门 */
    @Bean
    public OrgReferenceChecker purchaseOrgChecker(ApplicationContext context) {
        return new ModuleOrgReferences(context, "com.erp.module.purchase.", SupplierDO.class)::isReferenced;
    }
}
