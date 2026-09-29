package com.erp.module.production.service.order;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.module.engineering.api.bom.BomDTO;
import com.erp.module.engineering.api.ecn.EcnApprovedEvent;
import com.erp.module.engineering.api.ecn.EcnEffectiveEvent;
import com.erp.module.engineering.api.material.MaterialDTO;
import com.erp.module.production.config.ProductionModuleConfig;
import com.erp.module.production.dal.dataobject.MfgProdOrderDO;
import com.erp.module.production.dal.dataobject.MfgProdOrderMaterialDO;
import com.erp.module.production.dal.mapper.MfgProdOrderMapper;
import com.erp.module.production.dal.mapper.MfgProdOrderMaterialMapper;
import com.erp.module.production.service.MfgSupport;
import com.erp.module.production.service.ProdStatus;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * ECN 生效（MFG-MO-R07）：影响分析中处理方式为“更新用料”的在制订单，按新 BOM 版本替换未领部分的用料：
 * 新 BOM 中没有的旧料应领改为已领 − 已退（不再发料，已领的旧料由现场按 ECN 处理方式退料）；新增子件按剩余数量生成用料行；
 * 保留的子件按新用量重算应领（不低于已领 − 已退）。
 */
@Component("mfgEcnListener")
public class EcnListener {

    static final Set<String> WIP = Set.of(ProdStatus.RELEASED.name(), ProdStatus.IN_PROGRESS.name(), ProdStatus.SUSPENDED.name());

    private final MfgProdOrderMapper orderMapper;
    private final MfgProdOrderMaterialMapper materialMapper;
    private final MaterialPlanner planner;
    private final MfgSupport support;

    public EcnListener(MfgProdOrderMapper orderMapper, MfgProdOrderMaterialMapper materialMapper, MaterialPlanner planner, MfgSupport support) {
        this.orderMapper = orderMapper;
        this.materialMapper = materialMapper;
        this.planner = planner;
        this.support = support;
    }

    @EventListener
    public void onEcnEffective(EcnEffectiveEvent e) {
        if (e.getUpdateWipDocNos() == null || e.getUpdateWipDocNos().isEmpty()) return;
        Map<Long, Long> newBomByMaterial = new HashMap<>();
        for (EcnApprovedEvent.BomChange c : e.getChanges()) newBomByMaterial.put(c.materialId(), c.newBomId());
        for (MfgProdOrderDO o : orderMapper.selectList(new LambdaQueryWrapper<MfgProdOrderDO>().in(MfgProdOrderDO::getDocNo, e.getUpdateWipDocNos()))) {
            if (!WIP.contains(o.getProdStatus())) continue;
            Long bomId = newBomByMaterial.get(o.getMaterialId());
            if (bomId == null) continue;
            planner.bomApi().getBom(bomId).ifPresent(bom -> apply(o, bom, e.getEcnNo()));
        }
    }

    private void apply(MfgProdOrderDO o, BomDTO bom, String ecnNo) {
        BigDecimal remainQty = MfgSupport.max0(o.getQty().subtract(o.getCompletedQty()).subtract(o.getScrappedQty()));
        List<MfgProdOrderMaterialDO> mats = materialMapper.selectByParent(o.getId());
        List<MaterialPlanner.PlannedLine> lines = planner.explode(bom);
        Map<Long, MaterialPlanner.PlannedLine> byComponent = new HashMap<>();
        for (MaterialPlanner.PlannedLine l : lines) byComponent.putIfAbsent(l.componentId(), l);
        Map<Long, MaterialDTO> ms = support.materials(lines.stream().map(MaterialPlanner.PlannedLine::componentId).toList());
        List<String> changes = new ArrayList<>();
        Set<Long> kept = new java.util.HashSet<>();
        for (MfgProdOrderMaterialDO m : mats) {
            if (Boolean.TRUE.equals(m.getIsAdded()) || m.getSubstituteOfId() != null) continue;
            MaterialPlanner.PlannedLine l = byComponent.get(m.getComponentId());
            BigDecimal net = MaterialPlanner.netQty(m);
            if (l == null) {
                if (m.getRequiredQty().compareTo(net) != 0) {
                    m.setRequiredQty(MfgSupport.max0(net));
                    materialMapper.updateByIdOrFail(m);
                    changes.add("停用 " + support.material(m.getComponentId()).code());
                }
                continue;
            }
            kept.add(m.getComponentId());
            MaterialDTO c = ms.get(m.getComponentId());
            BigDecimal consumed = planner.required(o.getCompletedQty().add(o.getScrappedQty()), m.getQtyPer(), m.getScrapRate(), c == null ? null : c.baseUom());
            BigDecimal required = consumed.add(planner.required(remainQty, l.qtyPer(), l.scrapRate(), c == null ? null : c.baseUom())).max(net);
            if (required.compareTo(m.getRequiredQty()) != 0 || l.qtyPer().compareTo(m.getQtyPer()) != 0) {
                m.setQtyPer(l.qtyPer());
                m.setScrapRate(l.scrapRate());
                m.setIssueMethod(l.issueMethod());
                m.setRequiredQty(required);
                materialMapper.updateByIdOrFail(m);
                changes.add((c == null ? "" : c.code()) + " 应领 " + MfgSupport.plain(required));
            }
        }
        int no = mats.stream().mapToInt(MfgProdOrderMaterialDO::getLineNo).max().orElse(0);
        for (MaterialPlanner.PlannedLine l : lines) {
            if (kept.contains(l.componentId()) || mats.stream().anyMatch(m -> m.getComponentId().equals(l.componentId()))) continue;
            MaterialDTO c = ms.get(l.componentId());
            MfgProdOrderMaterialDO n = ProdOrderService.newMaterial(o.getId(), ++no, l.componentId(), l.qtyPer(), l.scrapRate(), l.issueMethod(), l.operationSeq());
            n.setRequiredQty(planner.required(remainQty, l.qtyPer(), l.scrapRate(), c == null ? null : c.baseUom()));
            n.setRemark("ECN " + ecnNo);
            materialMapper.insert(n);
            kept.add(l.componentId());
            changes.add("新增 " + (c == null ? "" : c.code()) + " " + MfgSupport.plain(n.getRequiredQty()));
        }
        o.setBomId(bom.id());
        orderMapper.updateByIdOrFail(o);
        support.log(ProductionModuleConfig.PROD_ORDER, o.getId(), o.getDocNo(), "ECN_UPDATE", "ECN 更新用料", o.getProdStatus(), o.getProdStatus(),
                "ECN " + ecnNo + (changes.isEmpty() ? "" : "：" + String.join("；", changes)));
    }
}
