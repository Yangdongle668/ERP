package com.erp.module.production.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.common.enums.DocStatus;
import com.erp.framework.mybatis.ModuleOrgReferences;
import com.erp.module.production.dal.dataobject.MfgDefectDO;
import com.erp.module.production.dal.dataobject.MfgProdOrderDO;
import com.erp.module.production.dal.mapper.MfgProdOrderMapper;
import com.erp.module.sales.api.order.SalesOrderReferenceChecker;
import com.erp.module.system.api.org.OrgReferenceChecker;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Optional;

/** 生产对其他模块数据的引用检查：销售订单（反审核）、组织（删除） */
@Configuration
public class ProductionReferenceConfig {

    /** SAL-SO-R08：由该订单生成、未作废的生产订单 */
    @Bean
    public SalesOrderReferenceChecker productionSalesOrderChecker(MfgProdOrderMapper orderMapper) {
        return orderId -> Optional.ofNullable(orderMapper.selectOne(new LambdaQueryWrapper<MfgProdOrderDO>().eq(MfgProdOrderDO::getSalesOrderId, orderId)
                .ne(MfgProdOrderDO::getStatus, DocStatus.VOIDED).select(MfgProdOrderDO::getDocNo).last("LIMIT 1"))).map(o -> "生产订单 " + o.getDocNo());
    }

    /** SYS-ORG-R07：生产单据与不良记录的公司 / 部门（车间） */
    @Bean
    public OrgReferenceChecker productionOrgChecker(ApplicationContext context) {
        return new ModuleOrgReferences(context, "com.erp.module.production.", MfgDefectDO.class)::isReferenced;
    }
}
