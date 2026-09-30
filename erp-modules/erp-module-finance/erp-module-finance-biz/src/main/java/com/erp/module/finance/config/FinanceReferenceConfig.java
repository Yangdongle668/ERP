package com.erp.module.finance.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.common.enums.DocStatus;
import com.erp.framework.mybatis.ModuleOrgReferences;
import com.erp.module.crm.api.customer.CustomerReferenceChecker;
import com.erp.module.engineering.api.material.MaterialReferenceChecker;
import com.erp.module.engineering.api.material.MaterialUsage;
import com.erp.module.finance.dal.dataobject.FinCostExpenseDO;
import com.erp.module.finance.dal.dataobject.FinPayableLineDO;
import com.erp.module.finance.dal.dataobject.FinPurchaseInvoiceLineDO;
import com.erp.module.finance.dal.dataobject.FinReceiptDO;
import com.erp.module.finance.dal.dataobject.FinReceivableDO;
import com.erp.module.finance.dal.dataobject.FinReceivableLineDO;
import com.erp.module.finance.dal.dataobject.FinSalesInvoiceDO;
import com.erp.module.finance.dal.dataobject.FinSalesInvoiceLineDO;
import com.erp.module.finance.dal.mapper.FinPayableLineMapper;
import com.erp.module.finance.dal.mapper.FinPayableMapper;
import com.erp.module.finance.dal.mapper.FinPaymentMapper;
import com.erp.module.finance.dal.mapper.FinPurchaseInvoiceLineMapper;
import com.erp.module.finance.dal.mapper.FinReceiptMapper;
import com.erp.module.finance.dal.mapper.FinReceivableLineMapper;
import com.erp.module.finance.dal.mapper.FinReceivableMapper;
import com.erp.module.finance.dal.mapper.FinSalesInvoiceLineMapper;
import com.erp.module.finance.dal.mapper.FinSalesInvoiceMapper;
import com.erp.module.finance.dal.mapper.FinVoucherMapper;
import com.erp.module.sales.api.order.SalesOrderReferenceChecker;
import com.erp.module.system.api.currency.CurrencyReferenceChecker;
import com.erp.module.system.api.org.OrgReferenceChecker;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** 财务对其他模块数据的引用检查：销售订单（反审核）、本位币（修改）、客户（删除）、物料（删除）、组织（删除） */
@Configuration
public class FinanceReferenceConfig {

    /** SAL-SO-R08：关联该订单、未作废的预收款 / 收款，或来源于该订单的应收 */
    @Bean
    public SalesOrderReferenceChecker financeSalesOrderChecker(FinReceiptMapper receiptMapper, FinReceivableLineMapper arLineMapper,
                                                               FinReceivableMapper arMapper) {
        return orderId -> {
            FinReceiptDO r = receiptMapper.selectOne(new LambdaQueryWrapper<FinReceiptDO>().eq(FinReceiptDO::getOrderId, orderId)
                    .ne(FinReceiptDO::getStatus, DocStatus.VOIDED).select(FinReceiptDO::getDocNo).last("LIMIT 1"));
            if (r != null) return Optional.of("收款单 " + r.getDocNo());
            Set<Long> arIds = arLineMapper.selectList(new LambdaQueryWrapper<FinReceivableLineDO>().eq(FinReceivableLineDO::getOrderId, orderId)
                    .select(FinReceivableLineDO::getReceivableId)).stream().map(FinReceivableLineDO::getReceivableId).collect(Collectors.toSet());
            if (arIds.isEmpty()) return Optional.empty();
            return Optional.ofNullable(arMapper.selectOne(new LambdaQueryWrapper<FinReceivableDO>().in(FinReceivableDO::getId, arIds)
                    .ne(FinReceivableDO::getStatus, DocStatus.VOIDED).select(FinReceivableDO::getDocNo).last("LIMIT 1"))).map(a -> "应收单 " + a.getDocNo());
        };
    }

    /** SYS-CUR-R03：已有应收、应付、收付款或凭证时不能修改本位币 */
    @Bean
    public CurrencyReferenceChecker financeCurrencyChecker(FinReceivableMapper arMapper, FinPayableMapper apMapper, FinReceiptMapper receiptMapper,
                                                           FinPaymentMapper paymentMapper, FinVoucherMapper voucherMapper) {
        return () -> arMapper.exists(null) || apMapper.exists(null) || receiptMapper.exists(null) || paymentMapper.exists(null)
                || voucherMapper.exists(null);
    }

    /** CRM R09：有应收、收款、销项发票的客户不能删除 */
    @Bean
    public CustomerReferenceChecker financeCustomerChecker(FinReceivableMapper arMapper, FinReceiptMapper receiptMapper, FinSalesInvoiceMapper invoiceMapper) {
        return customerId -> arMapper.exists(new LambdaQueryWrapper<FinReceivableDO>().eq(FinReceivableDO::getCustomerId, customerId))
                || receiptMapper.exists(new LambdaQueryWrapper<FinReceiptDO>().eq(FinReceiptDO::getCustomerId, customerId))
                || invoiceMapper.exists(new LambdaQueryWrapper<FinSalesInvoiceDO>().eq(FinSalesInvoiceDO::getCustomerId, customerId));
    }

    /** ENG-MAT-R09：出现在应收 / 应付 / 发票明细中的物料不能删除 */
    @Bean
    public MaterialReferenceChecker financeMaterialChecker(FinReceivableLineMapper arLineMapper, FinPayableLineMapper apLineMapper,
                                                           FinSalesInvoiceLineMapper salesInvoiceLineMapper, FinPurchaseInvoiceLineMapper purchaseInvoiceLineMapper) {
        return materialId -> {
            boolean used = arLineMapper.exists(new LambdaQueryWrapper<FinReceivableLineDO>().eq(FinReceivableLineDO::getMaterialId, materialId))
                    || apLineMapper.exists(new LambdaQueryWrapper<FinPayableLineDO>().eq(FinPayableLineDO::getMaterialId, materialId))
                    || salesInvoiceLineMapper.exists(new LambdaQueryWrapper<FinSalesInvoiceLineDO>().eq(FinSalesInvoiceLineDO::getMaterialId, materialId))
                    || purchaseInvoiceLineMapper.exists(new LambdaQueryWrapper<FinPurchaseInvoiceLineDO>()
                    .eq(FinPurchaseInvoiceLineDO::getMaterialId, materialId));
            return used ? new MaterialUsage(BigDecimal.ZERO, 0, true, false) : MaterialUsage.NONE;
        };
    }

    /** SYS-ORG-R07：财务单据与费用录入的公司 / 部门 */
    @Bean
    public OrgReferenceChecker financeOrgChecker(ApplicationContext context) {
        return new ModuleOrgReferences(context, "com.erp.module.finance.", FinCostExpenseDO.class)::isReferenced;
    }
}
