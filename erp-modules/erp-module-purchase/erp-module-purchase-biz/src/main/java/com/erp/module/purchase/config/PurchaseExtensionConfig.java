package com.erp.module.purchase.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.common.enums.DocStatus;
import com.erp.module.engineering.api.ecn.EcnImpact;
import com.erp.module.engineering.api.ecn.EcnImpactProvider;
import com.erp.module.engineering.api.material.MaterialReferenceChecker;
import com.erp.module.engineering.api.material.MaterialUsage;
import com.erp.module.purchase.dal.dataobject.OrderDO;
import com.erp.module.purchase.dal.dataobject.OrderLineDO;
import com.erp.module.purchase.dal.dataobject.PriceDO;
import com.erp.module.purchase.dal.dataobject.RequisitionDO;
import com.erp.module.purchase.dal.dataobject.RequisitionLineDO;
import com.erp.module.purchase.dal.dataobject.ReturnDO;
import com.erp.module.purchase.dal.dataobject.SupplierCertDO;
import com.erp.module.purchase.dal.dataobject.SupplierDO;
import com.erp.module.purchase.dal.dataobject.SupplierMaterialDO;
import com.erp.module.purchase.dal.mapper.OrderLineMapper;
import com.erp.module.purchase.dal.mapper.OrderMapper;
import com.erp.module.purchase.dal.mapper.PriceMapper;
import com.erp.module.purchase.dal.mapper.RequisitionLineMapper;
import com.erp.module.purchase.dal.mapper.RequisitionMapper;
import com.erp.module.purchase.dal.mapper.ReturnMapper;
import com.erp.module.purchase.dal.mapper.SupplierCertMapper;
import com.erp.module.purchase.dal.mapper.SupplierMapper;
import com.erp.module.purchase.dal.mapper.SupplierMaterialMapper;
import com.erp.module.purchase.service.PurSupport;
import com.erp.module.purchase.service.order.OrderService;
import com.erp.module.system.api.dict.DictReferenceChecker;
import com.erp.module.system.api.file.FileAccessChecker;
import com.erp.module.system.api.paymentterm.PaymentTermReferenceChecker;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** 资材实现的其他模块扩展点：物料引用、ECN 在途采购影响、付款条件与字典引用、附件访问控制 */
@Configuration
public class PurchaseExtensionConfig {

    static final List<DocStatus> OPEN = List.of(DocStatus.DRAFT, DocStatus.PENDING_APPROVAL, DocStatus.APPROVED, DocStatus.IN_PROGRESS);

    /** 物料被资材引用：未完成的采购订单、采购申请行数；是否曾被可供物料、价格、订单、申请引用 */
    @Bean
    public MaterialReferenceChecker purchaseMaterialChecker(OrderMapper orderMapper, OrderLineMapper orderLineMapper, RequisitionMapper requisitionMapper,
                                                            RequisitionLineMapper requisitionLineMapper, SupplierMaterialMapper supplierMaterialMapper,
                                                            PriceMapper priceMapper) {
        return materialId -> {
            List<OrderLineDO> ols = orderLineMapper.selectList(new LambdaQueryWrapper<OrderLineDO>().eq(OrderLineDO::getMaterialId, materialId));
            List<RequisitionLineDO> rls = requisitionLineMapper.selectList(new LambdaQueryWrapper<RequisitionLineDO>().eq(RequisitionLineDO::getMaterialId, materialId));
            boolean used = !ols.isEmpty() || !rls.isEmpty()
                    || supplierMaterialMapper.selectCount(new LambdaQueryWrapper<SupplierMaterialDO>().eq(SupplierMaterialDO::getMaterialId, materialId)) > 0
                    || priceMapper.selectCount(new LambdaQueryWrapper<PriceDO>().eq(PriceDO::getMaterialId, materialId)) > 0;
            int open = 0;
            if (!ols.isEmpty()) {
                open += Math.toIntExact(orderMapper.selectCount(new LambdaQueryWrapper<OrderDO>().in(OrderDO::getId, ols.stream().map(OrderLineDO::getOrderId)
                        .collect(Collectors.toSet())).in(OrderDO::getStatus, OPEN)));
            }
            if (!rls.isEmpty()) {
                open += Math.toIntExact(requisitionMapper.selectCount(new LambdaQueryWrapper<RequisitionDO>().in(RequisitionDO::getId, rls.stream()
                        .map(RequisitionLineDO::getRequisitionId).collect(Collectors.toSet())).in(RequisitionDO::getStatus, OPEN)));
            }
            return new MaterialUsage(BigDecimal.ZERO, open, used, false);
        };
    }

    /** ECN 影响分析：旧子件的在途采购（已审核/执行中订单的未到货数量） */
    @Bean
    public EcnImpactProvider purchaseEcnImpactProvider(OrderMapper orderMapper, OrderLineMapper orderLineMapper) {
        return (Collection<Long> componentIds, Collection<Long> parentIds) -> {
            if (componentIds == null || componentIds.isEmpty()) return List.of();
            List<OrderLineDO> lines = orderLineMapper.selectList(new LambdaQueryWrapper<OrderLineDO>().in(OrderLineDO::getMaterialId, componentIds)
                    .eq(OrderLineDO::getLineStatus, OrderService.OPEN));
            if (lines.isEmpty()) return List.of();
            Map<Long, OrderDO> orders = orderMapper.selectList(new LambdaQueryWrapper<OrderDO>().in(OrderDO::getId, lines.stream()
                    .map(OrderLineDO::getOrderId).collect(Collectors.toSet())).in(OrderDO::getStatus, DocStatus.APPROVED, DocStatus.IN_PROGRESS)).stream()
                    .collect(Collectors.toMap(OrderDO::getId, o -> o));
            List<EcnImpact> list = new ArrayList<>();
            for (OrderLineDO l : lines) {
                OrderDO o = orders.get(l.getOrderId());
                BigDecimal open = l.getBaseQty().subtract(l.getReceivedQty());
                if (o != null && open.signum() > 0) list.add(new EcnImpact(l.getMaterialId(), "PURCHASE", o.getDocNo(), open));
            }
            return list;
        };
    }

    /** 付款条件被供应商或采购订单引用 */
    @Bean
    public PaymentTermReferenceChecker purchasePaymentTermChecker(SupplierMapper supplierMapper, OrderMapper orderMapper) {
        return termId -> supplierMapper.selectCount(new LambdaQueryWrapper<SupplierDO>().eq(SupplierDO::getPaymentTermId, termId)) > 0
                || orderMapper.selectCount(new LambdaQueryWrapper<OrderDO>().eq(OrderDO::getPaymentTermId, termId)) > 0;
    }

    /** 资材声明的字典项是否被业务数据使用 */
    @Bean
    public DictReferenceChecker purchaseDictChecker(SupplierMapper supplierMapper, SupplierCertMapper certMapper, RequisitionMapper requisitionMapper,
                                                    ReturnMapper returnMapper) {
        Set<String> types = Set.of("pur_supplier_type", "pur_supplier_level", "pur_cert_type", "pur_requisition_type", "pur_return_reason");
        return new DictReferenceChecker() {
            @Override
            public boolean supports(String typeCode) {
                return types.contains(typeCode);
            }

            @Override
            public boolean isReferenced(String typeCode, String value) {
                return switch (typeCode) {
                    case "pur_supplier_type" -> supplierMapper.selectCount(new LambdaQueryWrapper<SupplierDO>().eq(SupplierDO::getSupplierType, value)) > 0;
                    case "pur_supplier_level" -> supplierMapper.selectCount(new LambdaQueryWrapper<SupplierDO>().eq(SupplierDO::getSupplierLevel, value)) > 0;
                    case "pur_cert_type" -> certMapper.selectCount(new LambdaQueryWrapper<SupplierCertDO>().eq(SupplierCertDO::getCertType, value)) > 0;
                    case "pur_requisition_type" -> requisitionMapper.selectCount(new LambdaQueryWrapper<RequisitionDO>().eq(RequisitionDO::getRequisitionType, value)) > 0;
                    default -> returnMapper.selectCount(new LambdaQueryWrapper<ReturnDO>().eq(ReturnDO::getReturnReason, value)) > 0;
                };
            }
        };
    }

    /** 附件访问：有对应单据查看权限即可查看；有编辑类权限可上传、删除 */
    @Bean
    public FileAccessChecker purchaseFileAccessChecker() {
        Map<String, String[]> perms = Map.ofEntries(
                Map.entry(PurchaseModuleConfig.SUPPLIER, new String[]{"pur:supplier:query", "pur:supplier:update"}),
                Map.entry(PurchaseModuleConfig.PRICE_ADJUST, new String[]{"pur:price:query", "pur:price:adjust"}),
                Map.entry(PurchaseModuleConfig.REQUISITION, new String[]{"pur:requisition:query", "pur:requisition:update"}),
                Map.entry(PurchaseModuleConfig.RFQ, new String[]{"pur:rfq:query", "pur:rfq:update"}),
                Map.entry(PurchaseModuleConfig.ORDER, new String[]{"pur:order:query", "pur:order:update"}),
                Map.entry(PurchaseModuleConfig.ORDER_CHANGE, new String[]{"pur:order:query", "pur:order:change"}),
                Map.entry(PurchaseModuleConfig.RECEIPT, new String[]{"pur:receipt:query", "pur:receipt:update"}),
                Map.entry(PurchaseModuleConfig.OUTSOURCING, new String[]{"pur:outsourcing:query", "pur:outsourcing:update"}),
                Map.entry(PurchaseModuleConfig.RETURN, new String[]{"pur:return:query", "pur:return:update"}),
                Map.entry(PurchaseModuleConfig.STATEMENT, new String[]{"pur:statement:query", "pur:statement:confirm"}));
        return new FileAccessChecker() {
            @Override
            public boolean supports(String bizType) {
                return perms.containsKey(bizType);
            }

            @Override
            public boolean canView(String bizType, Long bizId) {
                return PurSupport.hasPermission(perms.get(bizType)[0]);
            }

            @Override
            public boolean canEdit(String bizType, Long bizId) {
                return PurSupport.hasPermission(perms.get(bizType)[1]);
            }
        };
    }
}
