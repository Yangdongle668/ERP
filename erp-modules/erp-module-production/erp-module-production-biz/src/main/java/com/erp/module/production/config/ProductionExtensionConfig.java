package com.erp.module.production.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.module.engineering.api.bom.BomReferenceChecker;
import com.erp.module.engineering.api.ecn.EcnImpact;
import com.erp.module.engineering.api.ecn.EcnImpactProvider;
import com.erp.module.engineering.api.material.MaterialReferenceChecker;
import com.erp.module.engineering.api.material.MaterialUsage;
import com.erp.module.engineering.api.routing.RoutingReferenceChecker;
import com.erp.module.production.dal.dataobject.MfgDefectDO;
import com.erp.module.production.dal.dataobject.MfgIssueDO;
import com.erp.module.production.dal.dataobject.MfgProdOrderDO;
import com.erp.module.production.dal.dataobject.MfgProdOrderMaterialDO;
import com.erp.module.production.dal.dataobject.MfgProdOrderOperationDO;
import com.erp.module.production.dal.dataobject.MfgReportDO;
import com.erp.module.production.dal.dataobject.MfgWorkOrderDO;
import com.erp.module.production.dal.mapper.MfgDefectMapper;
import com.erp.module.production.dal.mapper.MfgIssueMapper;
import com.erp.module.production.dal.mapper.MfgProdOrderMapper;
import com.erp.module.production.dal.mapper.MfgProdOrderMaterialMapper;
import com.erp.module.production.dal.mapper.MfgProdOrderOperationMapper;
import com.erp.module.production.dal.mapper.MfgReportMapper;
import com.erp.module.production.dal.mapper.MfgWorkOrderMapper;
import com.erp.module.production.service.MfgSupport;
import com.erp.module.production.service.ProdStatus;
import com.erp.module.production.service.order.MaterialPlanner;
import com.erp.module.system.api.dict.DictReferenceChecker;
import com.erp.module.system.api.file.FileAccessChecker;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 生产实现的其他模块扩展点：BOM / 工艺路线 / 工作中心 / 物料 / 字典引用、ECN 在制影响、附件访问控制 */
@Configuration
public class ProductionExtensionConfig {

    static final List<String> WIP = List.of(ProdStatus.RELEASED.name(), ProdStatus.IN_PROGRESS.name(), ProdStatus.SUSPENDED.name());
    static final List<String> OPEN = List.of(ProdStatus.DRAFT.name(), ProdStatus.PENDING.name(), ProdStatus.PLANNED.name(), ProdStatus.RELEASED.name(),
            ProdStatus.IN_PROGRESS.name(), ProdStatus.SUSPENDED.name());

    /** 被生产订单使用的 BOM 版本不能反审核（ENG-BOM-R09） */
    @Bean
    public BomReferenceChecker productionBomChecker(MfgProdOrderMapper orderMapper) {
        return bomId -> orderMapper.selectCount(new LambdaQueryWrapper<MfgProdOrderDO>().eq(MfgProdOrderDO::getBomId, bomId)
                .ne(MfgProdOrderDO::getProdStatus, ProdStatus.VOIDED.name())) > 0;
    }

    /** 被生产订单使用的工艺路线不能反审核；被工序、工单、报工使用的工作中心不能删除 */
    @Bean
    public RoutingReferenceChecker productionRoutingChecker(MfgProdOrderMapper orderMapper, MfgProdOrderOperationMapper operationMapper,
                                                            MfgWorkOrderMapper workOrderMapper, MfgReportMapper reportMapper) {
        return new RoutingReferenceChecker() {
            @Override
            public boolean isRoutingUsed(Long routingId) {
                return orderMapper.selectCount(new LambdaQueryWrapper<MfgProdOrderDO>().eq(MfgProdOrderDO::getRoutingId, routingId)
                        .ne(MfgProdOrderDO::getProdStatus, ProdStatus.VOIDED.name())) > 0;
            }

            @Override
            public boolean isWorkCenterUsed(Long workCenterId) {
                return operationMapper.selectCount(new LambdaQueryWrapper<MfgProdOrderOperationDO>().eq(MfgProdOrderOperationDO::getWorkCenterId, workCenterId)) > 0
                        || workOrderMapper.selectCount(new LambdaQueryWrapper<MfgWorkOrderDO>().eq(MfgWorkOrderDO::getWorkCenterId, workCenterId)) > 0
                        || reportMapper.selectCount(new LambdaQueryWrapper<MfgReportDO>().eq(MfgReportDO::getWorkCenterId, workCenterId)) > 0;
            }
        };
    }

    /** ECN 影响分析：在制生产订单（产品为被变更父件，或未领用料中含被变更子件） */
    @Bean
    public EcnImpactProvider productionEcnImpactProvider(MfgProdOrderMapper orderMapper, MfgProdOrderMaterialMapper materialMapper) {
        return (componentIds, parentIds) -> {
            List<MfgProdOrderDO> orders = orderMapper.selectList(new LambdaQueryWrapper<MfgProdOrderDO>().in(MfgProdOrderDO::getProdStatus, WIP));
            if (orders.isEmpty()) return List.of();
            Map<Long, MfgProdOrderDO> byId = orders.stream().collect(Collectors.toMap(MfgProdOrderDO::getId, Function.identity()));
            List<EcnImpact> out = new ArrayList<>();
            java.util.Set<Long> added = new java.util.HashSet<>();
            for (MfgProdOrderDO o : orders) {
                if (parentIds != null && parentIds.contains(o.getMaterialId())) {
                    out.add(new EcnImpact(o.getMaterialId(), "WIP", o.getDocNo(),
                            MfgSupport.max0(o.getQty().subtract(o.getCompletedQty()).subtract(o.getScrappedQty()))));
                    added.add(o.getId());
                }
            }
            if (componentIds != null && !componentIds.isEmpty()) {
                for (MfgProdOrderMaterialDO m : materialMapper.selectList(new LambdaQueryWrapper<MfgProdOrderMaterialDO>()
                        .in(MfgProdOrderMaterialDO::getProdOrderId, byId.keySet()).in(MfgProdOrderMaterialDO::getComponentId, componentIds))) {
                    BigDecimal open = MaterialPlanner.openQty(m);
                    if (open.signum() <= 0 || added.contains(m.getProdOrderId())) continue;
                    MfgProdOrderDO o = byId.get(m.getProdOrderId());
                    out.add(new EcnImpact(o.getMaterialId(), "WIP", o.getDocNo(), open));
                    added.add(o.getId());
                }
            }
            return out;
        };
    }

    /** 物料被生产引用：未完成订单张数（作为产品或用料）；是否曾被引用 */
    @Bean
    public MaterialReferenceChecker productionMaterialChecker(MfgProdOrderMapper orderMapper, MfgProdOrderMaterialMapper materialMapper) {
        return materialId -> {
            List<MfgProdOrderDO> asProduct = orderMapper.selectList(new LambdaQueryWrapper<MfgProdOrderDO>().eq(MfgProdOrderDO::getMaterialId, materialId));
            List<Long> asComponent = materialMapper.selectList(new LambdaQueryWrapper<MfgProdOrderMaterialDO>().eq(MfgProdOrderMaterialDO::getComponentId, materialId))
                    .stream().map(MfgProdOrderMaterialDO::getProdOrderId).distinct().toList();
            boolean used = !asProduct.isEmpty() || !asComponent.isEmpty();
            Set<Long> openIds = asProduct.stream().filter(o -> OPEN.contains(o.getProdStatus())).map(MfgProdOrderDO::getId).collect(Collectors.toSet());
            if (!asComponent.isEmpty()) {
                orderMapper.selectBatchIds(asComponent).stream().filter(o -> OPEN.contains(o.getProdStatus())).forEach(o -> openIds.add(o.getId()));
            }
            return new MaterialUsage(BigDecimal.ZERO, openIds.size(), used, false);
        };
    }

    /** 生产声明的字典项是否被业务数据使用 */
    @Bean
    public DictReferenceChecker productionDictChecker(MfgDefectMapper defectMapper, MfgReportMapper reportMapper, MfgIssueMapper issueMapper,
                                                      MfgWorkOrderMapper workOrderMapper) {
        Set<String> types = Set.of("mfg_defect_code", "mfg_scrap_reason", "mfg_over_issue_reason", "mfg_shift");
        return new DictReferenceChecker() {
            @Override
            public boolean supports(String typeCode) {
                return types.contains(typeCode);
            }

            @Override
            public boolean isReferenced(String typeCode, String value) {
                return switch (typeCode) {
                    case "mfg_defect_code" -> defectMapper.selectCount(new LambdaQueryWrapper<MfgDefectDO>().eq(MfgDefectDO::getDefectCode, value)) > 0;
                    case "mfg_scrap_reason" -> reportMapper.selectCount(new LambdaQueryWrapper<MfgReportDO>().eq(MfgReportDO::getScrapReason, value)) > 0;
                    case "mfg_over_issue_reason" -> issueMapper.selectCount(new LambdaQueryWrapper<MfgIssueDO>().eq(MfgIssueDO::getOverReason, value)) > 0;
                    default -> reportMapper.selectCount(new LambdaQueryWrapper<MfgReportDO>().eq(MfgReportDO::getShift, value)) > 0
                            || workOrderMapper.selectCount(new LambdaQueryWrapper<MfgWorkOrderDO>().eq(MfgWorkOrderDO::getShift, value)) > 0;
                };
            }
        };
    }

    /** 附件访问：生产订单附件、不良照片 */
    @Bean
    public FileAccessChecker productionFileAccessChecker() {
        Map<String, String[]> perms = Map.of(
                ProductionModuleConfig.PROD_ORDER, new String[]{"mfg:prod-order:query", "mfg:prod-order:update"},
                ProductionModuleConfig.DEFECT, new String[]{"mfg:defect:query", "mfg:report:create"});
        return new FileAccessChecker() {
            @Override
            public boolean supports(String bizType) {
                return perms.containsKey(bizType);
            }

            @Override
            public boolean canView(String bizType, Long bizId) {
                return MfgSupport.hasPermission(perms.get(bizType)[0]) || MfgSupport.hasPermission("mfg:report:query");
            }

            @Override
            public boolean canEdit(String bizType, Long bizId) {
                return MfgSupport.hasPermission(perms.get(bizType)[1]);
            }
        };
    }
}
