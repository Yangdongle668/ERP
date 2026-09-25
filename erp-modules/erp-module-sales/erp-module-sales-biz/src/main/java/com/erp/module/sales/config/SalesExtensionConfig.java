package com.erp.module.sales.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.common.enums.DocStatus;
import com.erp.module.crm.api.credit.CreditUsage;
import com.erp.module.crm.api.credit.CreditUsageProvider;
import com.erp.module.crm.api.customer.CustomerReferenceChecker;
import com.erp.module.crm.api.part.CustomerPartReferenceChecker;
import com.erp.module.engineering.api.material.MaterialReferenceChecker;
import com.erp.module.engineering.api.material.MaterialUsage;
import com.erp.module.sales.dal.dataobject.SalForecastLineDO;
import com.erp.module.sales.dal.dataobject.SalOrderChangeDO;
import com.erp.module.sales.dal.dataobject.SalOrderDO;
import com.erp.module.sales.dal.dataobject.SalOrderLineDO;
import com.erp.module.sales.dal.dataobject.SalPriceListItemDO;
import com.erp.module.sales.dal.dataobject.SalQuotationDO;
import com.erp.module.sales.dal.dataobject.SalQuotationLineDO;
import com.erp.module.sales.dal.dataobject.SalReturnDO;
import com.erp.module.sales.dal.dataobject.SalRfqDO;
import com.erp.module.sales.dal.mapper.SalForecastLineMapper;
import com.erp.module.sales.dal.mapper.SalOrderChangeMapper;
import com.erp.module.sales.dal.mapper.SalOrderLineMapper;
import com.erp.module.sales.dal.mapper.SalOrderMapper;
import com.erp.module.sales.dal.mapper.SalPriceListItemMapper;
import com.erp.module.sales.dal.mapper.SalQuotationLineMapper;
import com.erp.module.sales.dal.mapper.SalQuotationMapper;
import com.erp.module.sales.dal.mapper.SalReturnMapper;
import com.erp.module.sales.dal.mapper.SalRfqMapper;
import com.erp.module.sales.service.SalSupport;
import com.erp.module.sales.service.order.OpenAmountCalculator;
import com.erp.module.system.api.dict.DictReferenceChecker;
import com.erp.module.system.api.file.FileAccessChecker;
import com.erp.module.system.api.paymentterm.PaymentTermReferenceChecker;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** 销售实现的其他模块扩展点：客户 / 客户料号 / 物料 / 付款条件 / 字典引用、信用占用、附件访问控制 */
@Configuration
public class SalesExtensionConfig {

    static final List<DocStatus> OPEN = List.of(DocStatus.DRAFT, DocStatus.PENDING_APPROVAL, DocStatus.APPROVED, DocStatus.IN_PROGRESS);

    /** CRM 客户引用（需求 03-01 R09）：有 RFQ、报价、订单、退货的客户不能删除 */
    @Bean
    public CustomerReferenceChecker salesCustomerChecker(SalRfqMapper rfqMapper, SalQuotationMapper quotationMapper, SalOrderMapper orderMapper,
                                                         SalReturnMapper returnMapper) {
        return customerId -> rfqMapper.selectCount(new LambdaQueryWrapper<SalRfqDO>().eq(SalRfqDO::getCustomerId, customerId)) > 0
                || quotationMapper.selectCount(new LambdaQueryWrapper<SalQuotationDO>().eq(SalQuotationDO::getCustomerId, customerId)) > 0
                || orderMapper.selectCount(new LambdaQueryWrapper<SalOrderDO>().eq(SalOrderDO::getCustomerId, customerId)) > 0
                || returnMapper.selectCount(new LambdaQueryWrapper<SalReturnDO>().eq(SalReturnDO::getCustomerId, customerId)) > 0;
    }

    /** CRM 客户料号引用（需求 03-02 R04）：被订单引用的对照不能删除 */
    @Bean
    public CustomerPartReferenceChecker salesCustomerPartChecker(SalOrderLineMapper orderLineMapper) {
        return partId -> orderLineMapper.selectCount(new LambdaQueryWrapper<SalOrderLineDO>().eq(SalOrderLineDO::getCustomerPartId, partId)) > 0;
    }

    /** CRM 信用占用：未出货订单金额（本位币含税） */
    @Bean
    public CreditUsageProvider salesCreditUsageProvider(OpenAmountCalculator calculator) {
        return customerIds -> {
            Map<Long, CreditUsage> map = new HashMap<>();
            calculator.openAmounts(customerIds).forEach((id, amount) -> map.put(id, new CreditUsage(null, null, amount)));
            return map;
        };
    }

    /** 物料被销售引用：未完成订单张数；是否曾被价格表、报价、订单、预测引用 */
    @Bean
    public MaterialReferenceChecker salesMaterialChecker(SalOrderMapper orderMapper, SalOrderLineMapper orderLineMapper, SalQuotationLineMapper quotationLineMapper,
                                                         SalPriceListItemMapper priceListItemMapper, SalForecastLineMapper forecastLineMapper) {
        return materialId -> {
            List<SalOrderLineDO> ols = orderLineMapper.selectList(new LambdaQueryWrapper<SalOrderLineDO>().eq(SalOrderLineDO::getMaterialId, materialId));
            boolean used = !ols.isEmpty()
                    || quotationLineMapper.selectCount(new LambdaQueryWrapper<SalQuotationLineDO>().eq(SalQuotationLineDO::getMaterialId, materialId)) > 0
                    || priceListItemMapper.selectCount(new LambdaQueryWrapper<SalPriceListItemDO>().eq(SalPriceListItemDO::getMaterialId, materialId)) > 0
                    || forecastLineMapper.selectCount(new LambdaQueryWrapper<SalForecastLineDO>().eq(SalForecastLineDO::getMaterialId, materialId)) > 0;
            int open = ols.isEmpty() ? 0 : Math.toIntExact(orderMapper.selectCount(new LambdaQueryWrapper<SalOrderDO>()
                    .in(SalOrderDO::getId, ols.stream().map(SalOrderLineDO::getOrderId).collect(Collectors.toSet())).in(SalOrderDO::getStatus, OPEN)));
            return new MaterialUsage(BigDecimal.ZERO, open, used, false);
        };
    }

    /** 付款条件被报价单、订单引用 */
    @Bean
    public PaymentTermReferenceChecker salesPaymentTermChecker(SalQuotationMapper quotationMapper, SalOrderMapper orderMapper) {
        return termId -> quotationMapper.selectCount(new LambdaQueryWrapper<SalQuotationDO>().eq(SalQuotationDO::getPaymentTermId, termId)) > 0
                || orderMapper.selectCount(new LambdaQueryWrapper<SalOrderDO>().eq(SalOrderDO::getPaymentTermId, termId)) > 0;
    }

    /** 销售声明的字典项是否被业务数据使用 */
    @Bean
    public DictReferenceChecker salesDictChecker(SalOrderMapper orderMapper, SalQuotationMapper quotationMapper, SalReturnMapper returnMapper,
                                                 SalOrderChangeMapper changeMapper) {
        Set<String> types = Set.of("sal_order_type", "sal_quote_lost_reason", "sal_return_reason", "sal_change_reason");
        return new DictReferenceChecker() {
            @Override
            public boolean supports(String typeCode) {
                return types.contains(typeCode);
            }

            @Override
            public boolean isReferenced(String typeCode, String value) {
                return switch (typeCode) {
                    case "sal_order_type" -> orderMapper.selectCount(new LambdaQueryWrapper<SalOrderDO>().eq(SalOrderDO::getOrderType, value)) > 0;
                    case "sal_quote_lost_reason" -> quotationMapper.selectCount(new LambdaQueryWrapper<SalQuotationDO>().eq(SalQuotationDO::getLostReason, value)) > 0;
                    case "sal_return_reason" -> returnMapper.selectCount(new LambdaQueryWrapper<SalReturnDO>().eq(SalReturnDO::getReturnReason, value)) > 0;
                    default -> changeMapper.selectCount(new LambdaQueryWrapper<SalOrderChangeDO>().eq(SalOrderChangeDO::getChangeReason, value)) > 0;
                };
            }
        };
    }

    /** 附件访问：有对应单据查看权限即可查看；有编辑类权限可上传、删除 */
    @Bean
    public FileAccessChecker salesFileAccessChecker() {
        Map<String, String[]> perms = Map.of(
                SalesModuleConfig.RFQ, new String[]{"sales:rfq:query", "sales:rfq:update"},
                SalesModuleConfig.QUOTATION, new String[]{"sales:quotation:query", "sales:quotation:update"},
                SalesModuleConfig.ORDER, new String[]{"sales:order:query", "sales:order:update"},
                SalesModuleConfig.RETURN, new String[]{"sales:return:query", "sales:return:update"});
        return new FileAccessChecker() {
            @Override
            public boolean supports(String bizType) {
                return perms.containsKey(bizType);
            }

            @Override
            public boolean canView(String bizType, Long bizId) {
                return SalSupport.hasPermission(perms.get(bizType)[0]);
            }

            @Override
            public boolean canEdit(String bizType, Long bizId) {
                return SalSupport.hasPermission(perms.get(bizType)[1]);
            }
        };
    }
}
