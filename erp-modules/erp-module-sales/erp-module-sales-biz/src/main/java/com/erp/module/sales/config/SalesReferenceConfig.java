package com.erp.module.sales.config;

import com.erp.framework.mybatis.ModuleOrgReferences;
import com.erp.module.sales.dal.mapper.SalOrderMapper;
import com.erp.module.sales.dal.mapper.SalQuotationMapper;
import com.erp.module.system.api.currency.CurrencyReferenceChecker;
import com.erp.module.system.api.org.OrgReferenceChecker;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 销售对系统管理数据的引用检查：本位币（修改）、组织（删除） */
@Configuration
public class SalesReferenceConfig {

    /** SYS-CUR-R03：已有报价或订单时不能修改本位币 */
    @Bean
    public CurrencyReferenceChecker salesCurrencyChecker(SalOrderMapper orderMapper, SalQuotationMapper quotationMapper) {
        return () -> orderMapper.exists(null) || quotationMapper.exists(null);
    }

    /** SYS-ORG-R07：销售单据的公司 / 部门 */
    @Bean
    public OrgReferenceChecker salesOrgChecker(ApplicationContext context) {
        return new ModuleOrgReferences(context, "com.erp.module.sales.")::isReferenced;
    }
}
