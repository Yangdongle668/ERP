package com.erp.module.production.service.order;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.module.engineering.api.bom.BomApi;
import com.erp.module.engineering.api.bom.BomDTO;
import com.erp.module.engineering.api.bom.IssueMethod;
import com.erp.module.engineering.api.material.MaterialDTO;
import com.erp.module.engineering.api.material.MaterialType;
import com.erp.module.inventory.api.stock.InventoryQueryApi;
import com.erp.module.production.controller.vo.ProdOrderVOs.Shortage;
import com.erp.module.production.dal.dataobject.MfgProdOrderDO;
import com.erp.module.production.dal.dataobject.MfgProdOrderMaterialDO;
import com.erp.module.production.dal.mapper.MfgProdOrderMapper;
import com.erp.module.production.dal.mapper.MfgProdOrderMaterialMapper;
import com.erp.module.production.service.MfgSupport;
import com.erp.module.production.service.ProdStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 用料计算：BOM 展开一层（虚拟件透过，MFG-MO-R04）、应领数量、未领、已分配量、齐套检查（R03）。
 */
@Component("mfgMaterialPlanner")
public class MaterialPlanner {

    static final int MAX_PHANTOM_DEPTH = 10;

    /** 一行计划用料：qtyPer 为每 1 个产品的基本单位用量（不含损耗） */
    public record PlannedLine(Long componentId, BigDecimal qtyPer, BigDecimal scrapRate, String issueMethod, Integer operationSeq) {
    }

    private final BomApi bomApi;
    private final InventoryQueryApi inventoryQueryApi;
    private final MfgProdOrderMapper orderMapper;
    private final MfgProdOrderMaterialMapper materialMapper;
    private final MfgSupport support;

    public MaterialPlanner(BomApi bomApi, InventoryQueryApi inventoryQueryApi, MfgProdOrderMapper orderMapper, MfgProdOrderMaterialMapper materialMapper,
                           MfgSupport support) {
        this.bomApi = bomApi;
        this.inventoryQueryApi = inventoryQueryApi;
        this.orderMapper = orderMapper;
        this.materialMapper = materialMapper;
        this.support = support;
    }

    public BomApi bomApi() {
        return bomApi;
    }

    public InventoryQueryApi inventory() {
        return inventoryQueryApi;
    }

    /** BOM 展开一层；子件为虚拟件且有默认 BOM 时继续展开（用量相乘，虚拟件自身损耗计入） */
    public List<PlannedLine> explode(BomDTO bom) {
        List<PlannedLine> out = new ArrayList<>();
        expand(bom, BigDecimal.ONE, out, 0);
        return out;
    }

    private void expand(BomDTO bom, BigDecimal multiplier, List<PlannedLine> out, int depth) {
        BigDecimal base = bom.baseQty() == null || bom.baseQty().signum() <= 0 ? BigDecimal.ONE : bom.baseQty();
        Map<Long, MaterialDTO> ms = support.materials(bom.lines().stream().map(BomDTO.Line::componentId).toList());
        for (BomDTO.Line l : bom.lines()) {
            MaterialDTO m = ms.get(l.componentId());
            BigDecimal qty = l.qtyPer();
            if (m != null && l.uom() != null && !l.uom().equals(m.baseUom())) qty = support.materialApi().convertToBase(l.componentId(), qty, l.uom());
            BigDecimal qtyPer = multiplier.multiply(qty).divide(base, 8, RoundingMode.HALF_UP);
            BigDecimal scrap = MfgSupport.nz(l.scrapRate());
            if (m != null && m.materialType() == MaterialType.PHANTOM && depth < MAX_PHANTOM_DEPTH) {
                var child = bomApi.getDefaultBom(l.componentId(), LocalDate.now());
                if (child.isPresent()) {
                    expand(child.get(), qtyPer.multiply(BigDecimal.ONE.add(scrap)), out, depth + 1);
                    continue;
                }
            }
            String method = l.issueMethod() == null ? IssueMethod.PICK.name() : l.issueMethod().name();
            out.add(new PlannedLine(l.componentId(), qtyPer.setScale(6, RoundingMode.HALF_UP), scrap, method, l.operationSeq()));
        }
    }

    /** 应领 = 数量 × 单位用量 × (1 + 损耗)，按子件单位精度向上取整 */
    public BigDecimal required(BigDecimal orderQty, BigDecimal qtyPer, BigDecimal scrapRate, String uom) {
        BigDecimal v = orderQty.multiply(qtyPer).multiply(BigDecimal.ONE.add(MfgSupport.nz(scrapRate)));
        return support.roundUp(v, uom);
    }

    /** 未领 = max(0, 应领 − 已领 + 已退良品) */
    public static BigDecimal openQty(MfgProdOrderMaterialDO m) {
        return MfgSupport.max0(m.getRequiredQty().subtract(MfgSupport.nz(m.getIssuedQty())).add(MfgSupport.nz(m.getReturnedGoodQty())));
    }

    /** 净耗用 = 已领 − 已退 */
    public static BigDecimal netQty(MfgProdOrderMaterialDO m) {
        return MfgSupport.nz(m.getIssuedQty()).subtract(MfgSupport.nz(m.getReturnedQty()));
    }

    /** 理论耗用（余料、可退数量口径）= (合格 + 报废) × 单位用量，按单位精度向上取整 */
    public BigDecimal theoretical(MfgProdOrderDO o, MfgProdOrderMaterialDO m, String uom) {
        BigDecimal done = MfgSupport.nz(o.getCompletedQty()).add(MfgSupport.nz(o.getScrappedQty()));
        return support.roundUp(done.multiply(m.getQtyPer()), uom);
    }

    /**
     * 其他已下达未关闭订单对这些子件的未领需求（按下达先后分配：只算 releasedBefore 之前下达的；为空时算全部）。
     */
    public Map<Long, BigDecimal> allocatedByOthers(Collection<Long> componentIds, Long excludeOrderId, LocalDateTime releasedBefore) {
        if (componentIds.isEmpty()) return Map.of();
        List<MfgProdOrderDO> orders = orderMapper.selectList(new LambdaQueryWrapper<MfgProdOrderDO>()
                .in(MfgProdOrderDO::getProdStatus, List.of(ProdStatus.RELEASED.name(), ProdStatus.IN_PROGRESS.name(), ProdStatus.SUSPENDED.name()))
                .ne(excludeOrderId != null, MfgProdOrderDO::getId, excludeOrderId)
                .lt(releasedBefore != null, MfgProdOrderDO::getReleasedAt, releasedBefore));
        if (orders.isEmpty()) return Map.of();
        Map<Long, BigDecimal> map = new HashMap<>();
        for (MfgProdOrderMaterialDO m : materialMapper.selectList(new LambdaQueryWrapper<MfgProdOrderMaterialDO>()
                .in(MfgProdOrderMaterialDO::getProdOrderId, orders.stream().map(MfgProdOrderDO::getId).toList())
                .in(MfgProdOrderMaterialDO::getComponentId, componentIds))) {
            map.merge(m.getComponentId(), openQty(m), BigDecimal::add);
        }
        return map;
    }

    /**
     * 齐套检查：需求（子件 → 数量）与 可用量 − 其他订单已分配 比较，返回缺料清单。
     */
    public List<Shortage> shortages(Map<Long, BigDecimal> needs, Long excludeOrderId, LocalDateTime releasedBefore) {
        Map<Long, BigDecimal> positive = needs.entrySet().stream().filter(e -> e.getValue().signum() > 0)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, BigDecimal::add, LinkedHashMap::new));
        if (positive.isEmpty()) return List.of();
        Map<Long, BigDecimal> allocated = allocatedByOthers(positive.keySet(), excludeOrderId, releasedBefore);
        Map<Long, MaterialDTO> ms = support.materials(positive.keySet());
        List<Shortage> out = new ArrayList<>();
        for (Map.Entry<Long, BigDecimal> e : positive.entrySet()) {
            BigDecimal available = MfgSupport.max0(inventoryQueryApi.getAvailableQty(e.getKey()).subtract(allocated.getOrDefault(e.getKey(), BigDecimal.ZERO)));
            if (available.compareTo(e.getValue()) >= 0) continue;
            MaterialDTO m = ms.get(e.getKey());
            out.add(new Shortage(e.getKey(), m == null ? null : m.code(), m == null ? null : m.name(), m == null ? null : m.baseUom(), e.getValue(),
                    available, e.getValue().subtract(available)));
        }
        return out;
    }

    /** 缺料清单文字：“螺丝 缺 108” */
    public static String shortageText(List<Shortage> list) {
        return list.stream().map(s -> (s.code() == null ? "" : s.code() + " ") + (s.name() == null ? "" : s.name()) + " 缺 " + MfgSupport.plain(s.shortageQty()))
                .collect(Collectors.joining("、"));
    }

    public Map<Long, BigDecimal> available(Set<Long> componentIds) {
        Map<Long, BigDecimal> map = new HashMap<>();
        for (Long id : componentIds) map.put(id, inventoryQueryApi.getAvailableQty(id));
        return map;
    }
}
