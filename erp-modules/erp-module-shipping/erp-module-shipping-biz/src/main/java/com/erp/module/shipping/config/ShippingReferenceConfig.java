package com.erp.module.shipping.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.common.enums.DocStatus;
import com.erp.framework.mybatis.ModuleOrgReferences;
import com.erp.module.crm.api.customer.CustomerReferenceChecker;
import com.erp.module.engineering.api.material.MaterialReferenceChecker;
import com.erp.module.engineering.api.material.MaterialUsage;
import com.erp.module.sales.api.order.SalesOrderReferenceChecker;
import com.erp.module.shipping.dal.dataobject.ShpInvoiceDO;
import com.erp.module.shipping.dal.dataobject.ShpNoticeDO;
import com.erp.module.shipping.dal.dataobject.ShpNoticeLineDO;
import com.erp.module.shipping.dal.dataobject.ShpShipmentDO;
import com.erp.module.shipping.dal.mapper.ShpInvoiceMapper;
import com.erp.module.shipping.dal.mapper.ShpNoticeLineMapper;
import com.erp.module.shipping.dal.mapper.ShpNoticeMapper;
import com.erp.module.shipping.dal.mapper.ShpShipmentMapper;
import com.erp.module.system.api.org.OrgReferenceChecker;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** 出货对其他模块数据的引用检查：销售订单（反审核）、客户（删除）、物料（停用 / 删除）、组织（删除） */
@Configuration
public class ShippingReferenceConfig {

    static final List<DocStatus> FINISHED = List.of(DocStatus.COMPLETED, DocStatus.CLOSED, DocStatus.VOIDED);

    /** SAL-SO-R08：包含该订单行、未作废的出货通知（含草稿） */
    @Bean
    public SalesOrderReferenceChecker shippingSalesOrderChecker(ShpNoticeLineMapper lineMapper, ShpNoticeMapper noticeMapper) {
        return orderId -> {
            Set<Long> noticeIds = lineMapper.selectList(new LambdaQueryWrapper<ShpNoticeLineDO>().eq(ShpNoticeLineDO::getOrderId, orderId)
                    .select(ShpNoticeLineDO::getNoticeId)).stream().map(ShpNoticeLineDO::getNoticeId).collect(Collectors.toSet());
            if (noticeIds.isEmpty()) return Optional.empty();
            return Optional.ofNullable(noticeMapper.selectOne(new LambdaQueryWrapper<ShpNoticeDO>().in(ShpNoticeDO::getId, noticeIds)
                    .ne(ShpNoticeDO::getStatus, DocStatus.VOIDED).select(ShpNoticeDO::getDocNo).last("LIMIT 1"))).map(n -> "出货通知 " + n.getDocNo());
        };
    }

    /** CRM R09：有出货通知、出货单、商业发票的客户不能删除 */
    @Bean
    public CustomerReferenceChecker shippingCustomerChecker(ShpNoticeMapper noticeMapper, ShpShipmentMapper shipmentMapper, ShpInvoiceMapper invoiceMapper) {
        return customerId -> noticeMapper.exists(new LambdaQueryWrapper<ShpNoticeDO>().eq(ShpNoticeDO::getCustomerId, customerId))
                || shipmentMapper.exists(new LambdaQueryWrapper<ShpShipmentDO>().eq(ShpShipmentDO::getCustomerId, customerId))
                || invoiceMapper.exists(new LambdaQueryWrapper<ShpInvoiceDO>().eq(ShpInvoiceDO::getCustomerId, customerId));
    }

    /** ENG-MAT-R08 / R09：出货通知中使用的物料；未完成的出货通知计入未完成单据 */
    @Bean
    public MaterialReferenceChecker shippingMaterialChecker(ShpNoticeLineMapper lineMapper, ShpNoticeMapper noticeMapper) {
        return materialId -> {
            Set<Long> noticeIds = lineMapper.selectList(new LambdaQueryWrapper<ShpNoticeLineDO>().eq(ShpNoticeLineDO::getMaterialId, materialId)
                    .select(ShpNoticeLineDO::getNoticeId)).stream().map(ShpNoticeLineDO::getNoticeId).collect(Collectors.toSet());
            if (noticeIds.isEmpty()) return MaterialUsage.NONE;
            int open = Math.toIntExact(noticeMapper.selectCount(new LambdaQueryWrapper<ShpNoticeDO>().in(ShpNoticeDO::getId, noticeIds)
                    .notIn(ShpNoticeDO::getStatus, FINISHED)));
            return new MaterialUsage(BigDecimal.ZERO, open, true, false);
        };
    }

    /** SYS-ORG-R07：出货单据的公司 / 部门 */
    @Bean
    public OrgReferenceChecker shippingOrgChecker(ApplicationContext context) {
        return new ModuleOrgReferences(context, "com.erp.module.shipping.")::isReferenced;
    }
}
