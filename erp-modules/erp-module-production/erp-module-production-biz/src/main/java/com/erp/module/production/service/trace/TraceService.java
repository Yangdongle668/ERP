package com.erp.module.production.service.trace;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.common.exception.BizException;
import com.erp.module.engineering.api.material.MaterialDTO;
import com.erp.module.engineering.api.material.Tracking;
import com.erp.module.inventory.api.batch.BatchApi;
import com.erp.module.inventory.api.batch.BatchDTO;
import com.erp.module.production.api.ProductionErrorCodes;
import com.erp.module.production.api.trace.TraceApi;
import com.erp.module.production.api.trace.TraceNode;
import com.erp.module.production.controller.vo.TraceVOs.TraceResult;
import com.erp.module.production.controller.vo.TraceVOs.TraceRow;
import com.erp.module.production.controller.vo.TraceVOs.TraceTreeNode;
import com.erp.module.production.dal.dataobject.MfgProdOrderDO;
import com.erp.module.production.dal.dataobject.MfgTraceDO;
import com.erp.module.production.dal.mapper.MfgProdOrderMapper;
import com.erp.module.production.dal.mapper.MfgTraceMapper;
import com.erp.module.production.service.MfgSupport;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 生产追溯查询（需求 09-07）：反向（产品批次 → 投入批次，半成品继续展开）、正向（原材料批次 → 生产订单 → 产品批次，继续向上）。
 * 最多 10 层（R01）；关系按 生产订单 + 投入物料 + 投入批次 汇总（领料 − 退料），数量为 0 的不显示。
 */
@Service("mfgTraceService")
public class TraceService implements TraceApi {

    public static final int MAX_LEVEL = 10;

    private final MfgTraceMapper mapper;
    private final MfgProdOrderMapper orderMapper;
    private final MfgSupport support;
    private final BatchApi batchApi;

    public TraceService(MfgTraceMapper mapper, MfgProdOrderMapper orderMapper, MfgSupport support, BatchApi batchApi) {
        this.mapper = mapper;
        this.orderMapper = orderMapper;
        this.support = support;
        this.batchApi = batchApi;
    }

    // ==================== TraceApi ====================

    @Override
    public List<TraceNode> backward(Long materialId, String batchNo) {
        List<TraceNode> out = new ArrayList<>();
        collectBackward(materialId, batchNo, 1, out, new HashSet<>());
        return out;
    }

    @Override
    public List<TraceNode> forward(Long materialId, String batchNo) {
        List<TraceNode> out = new ArrayList<>();
        collectForward(materialId, batchNo, 1, out, new HashSet<>());
        return out;
    }

    private void collectBackward(Long materialId, String batchNo, int level, List<TraceNode> out, Set<String> seen) {
        if (level > MAX_LEVEL || !seen.add(materialId + "|" + batchNo)) return;
        for (TraceNode n : aggregate(mapper.selectList(new LambdaQueryWrapper<MfgTraceDO>().eq(MfgTraceDO::getProductMaterialId, materialId)
                .eq(MfgTraceDO::getProductBatchNo, batchNo)), level)) {
            out.add(n);
            if (n.componentBatchNo() != null && isProductBatch(n.componentMaterialId(), n.componentBatchNo())) {
                collectBackward(n.componentMaterialId(), n.componentBatchNo(), level + 1, out, seen);
            }
        }
    }

    private void collectForward(Long materialId, String batchNo, int level, List<TraceNode> out, Set<String> seen) {
        if (level > MAX_LEVEL || !seen.add(materialId + "|" + batchNo)) return;
        for (TraceNode n : aggregate(mapper.selectList(new LambdaQueryWrapper<MfgTraceDO>().eq(MfgTraceDO::getComponentMaterialId, materialId)
                .eq(MfgTraceDO::getComponentBatchNo, batchNo)), level)) {
            out.add(n);
            collectForward(n.productMaterialId(), n.productBatchNo(), level + 1, out, seen);
        }
    }

    private boolean isProductBatch(Long materialId, String batchNo) {
        return mapper.selectCount(new LambdaQueryWrapper<MfgTraceDO>().eq(MfgTraceDO::getProductMaterialId, materialId)
                .eq(MfgTraceDO::getProductBatchNo, batchNo)) > 0;
    }

    /** 按 生产订单 + 投入物料 + 投入批次 汇总 */
    private List<TraceNode> aggregate(List<MfgTraceDO> rows, int level) {
        Map<String, BigDecimal> sum = new LinkedHashMap<>();
        Map<String, MfgTraceDO> first = new LinkedHashMap<>();
        for (MfgTraceDO t : rows) {
            String k = t.getProdOrderId() + "|" + t.getComponentMaterialId() + "|" + t.getComponentBatchNo();
            sum.merge(k, t.getQty(), BigDecimal::add);
            first.putIfAbsent(k, t);
        }
        Map<Long, String> orderNos = new LinkedHashMap<>();
        List<Long> orderIds = first.values().stream().map(MfgTraceDO::getProdOrderId).distinct().toList();
        if (!orderIds.isEmpty()) orderMapper.selectBatchIds(orderIds).forEach(o -> orderNos.put(o.getId(), o.getDocNo()));
        List<TraceNode> out = new ArrayList<>();
        for (Map.Entry<String, MfgTraceDO> e : first.entrySet()) {
            BigDecimal qty = sum.get(e.getKey());
            if (qty.signum() == 0) continue;
            MfgTraceDO t = e.getValue();
            out.add(new TraceNode(level, t.getProdOrderId(), orderNos.get(t.getProdOrderId()), t.getProductMaterialId(), t.getProductBatchNo(),
                    t.getComponentMaterialId(), t.getComponentBatchNo(), qty));
        }
        return out;
    }

    // ==================== 页面 ====================

    /** 反向追溯树：根为产品批次，子节点为投入物料批次（半成品批次继续展开） */
    public TraceResult backwardTree(Long materialId, String batchNo) {
        MaterialDTO root = support.material(materialId);
        List<TraceNode> nodes = backward(materialId, batchNo);
        Map<Long, MaterialDTO> ms = support.materials(collectMaterials(nodes, materialId));
        MfgProdOrderDO order = orderMapper.selectOne(new LambdaQueryWrapper<MfgProdOrderDO>().eq(MfgProdOrderDO::getMaterialId, materialId)
                .eq(MfgProdOrderDO::getBatchNo, batchNo).last("LIMIT 1"));
        TraceTreeNode tree = buildBackward(root, batchNo, order, nodes, ms, 0, new HashSet<>());
        Set<Long> orders = new HashSet<>();
        Set<String> batches = new HashSet<>();
        for (TraceNode n : nodes) {
            orders.add(n.prodOrderId());
            if (n.componentBatchNo() != null) batches.add(n.componentMaterialId() + "|" + n.componentBatchNo());
        }
        List<String> notes = new ArrayList<>();
        if (nodes.isEmpty()) notes.add("没有找到该批次的投入记录");
        if (nodes.stream().anyMatch(n -> n.componentBatchNo() == null)) notes.add("部分物料未启用批次管理，只显示数量");
        return new TraceResult("BACKWARD", tree, orders.size(), batches.size(), notes);
    }

    private TraceTreeNode buildBackward(MaterialDTO m, String batchNo, MfgProdOrderDO order, List<TraceNode> nodes, Map<Long, MaterialDTO> ms, int level,
                                        Set<String> path) {
        List<TraceTreeNode> children = new ArrayList<>();
        if (path.add(m.id() + "|" + batchNo) && level < MAX_LEVEL) {
            for (TraceNode n : nodes) {
                if (!n.productMaterialId().equals(m.id()) || !Objects.equals(n.productBatchNo(), batchNo)) continue;
                MaterialDTO c = ms.get(n.componentMaterialId());
                if (c == null) continue;
                boolean sub = n.componentBatchNo() != null && nodes.stream().anyMatch(x -> x.productMaterialId().equals(c.id())
                        && Objects.equals(x.productBatchNo(), n.componentBatchNo()));
                if (sub) {
                    MfgProdOrderDO so = orderMapper.selectOne(new LambdaQueryWrapper<MfgProdOrderDO>().eq(MfgProdOrderDO::getMaterialId, c.id())
                            .eq(MfgProdOrderDO::getBatchNo, n.componentBatchNo()).last("LIMIT 1"));
                    TraceTreeNode child = buildBackward(c, n.componentBatchNo(), so, nodes, ms, level + 1, new HashSet<>(path));
                    children.add(withQty(child, n.qty()));
                } else {
                    children.add(leaf(c, n.componentBatchNo(), n.qty(), level + 1));
                }
            }
        }
        return new TraceTreeNode(m.id() + "|" + batchNo + "|" + level, level, m.id(), m.code(), m.name(), batchNo, m.tracking() != Tracking.NONE, null,
                order == null ? null : order.getId(), order == null ? null : order.getDocNo(), order == null ? null : order.getCompletedQty(), null, null,
                null, false, children);
    }

    private static TraceTreeNode withQty(TraceTreeNode n, BigDecimal qty) {
        return new TraceTreeNode(n.key(), n.level(), n.materialId(), n.materialCode(), n.materialName(), n.batchNo(), n.batchTracked(), qty, n.prodOrderId(),
                n.prodOrderNo(), n.completedQty(), n.supplierId(), n.supplierBatchNo(), n.batchSourceNo(), n.concession(), n.children());
    }

    private TraceTreeNode leaf(MaterialDTO c, String batchNo, BigDecimal qty, int level) {
        BatchDTO b = batchNo == null ? null : batchApi.get(c.id(), batchNo).orElse(null);
        return new TraceTreeNode(c.id() + "|" + batchNo + "|" + level + "|" + qty, level, c.id(), c.code(), c.name(), batchNo, c.tracking() != Tracking.NONE,
                qty, null, null, null, b == null ? null : b.supplierId(), b == null ? null : b.supplierBatchNo(), b == null ? null : b.sourceNo(),
                b != null && b.concession(), List.of());
    }

    /** 正向追溯树：根为原材料批次，子节点为使用它的产品批次（继续向上） */
    public TraceResult forwardTree(Long materialId, String batchNo) {
        MaterialDTO root = support.material(materialId);
        if (root.tracking() == Tracking.NONE) throw new BizException(ProductionErrorCodes.TRACE_NO_BATCH);
        List<TraceNode> nodes = forward(materialId, batchNo);
        Map<Long, MaterialDTO> ms = support.materials(collectMaterials(nodes, materialId));
        TraceTreeNode tree = buildForward(root, batchNo, null, nodes, ms, 0, new HashSet<>());
        Set<Long> orders = new HashSet<>();
        Set<String> batches = new HashSet<>();
        for (TraceNode n : nodes) {
            orders.add(n.prodOrderId());
            batches.add(n.productMaterialId() + "|" + n.productBatchNo());
        }
        List<String> notes = new ArrayList<>();
        if (nodes.isEmpty()) notes.add("该批次还没有被生产领用");
        notes.add("出货客户需出货模块上线后显示");
        return new TraceResult("FORWARD", tree, orders.size(), batches.size(), notes);
    }

    private TraceTreeNode buildForward(MaterialDTO m, String batchNo, BigDecimal qty, List<TraceNode> nodes, Map<Long, MaterialDTO> ms, int level,
                                       Set<String> path) {
        List<TraceTreeNode> children = new ArrayList<>();
        if (path.add(m.id() + "|" + batchNo) && level < MAX_LEVEL) {
            for (TraceNode n : nodes) {
                if (!n.componentMaterialId().equals(m.id()) || !Objects.equals(n.componentBatchNo(), batchNo)) continue;
                MaterialDTO p = ms.get(n.productMaterialId());
                if (p == null) continue;
                TraceTreeNode child = buildForward(p, n.productBatchNo(), n.qty(), nodes, ms, level + 1, new HashSet<>(path));
                MfgProdOrderDO o = orderMapper.selectById(n.prodOrderId());
                children.add(new TraceTreeNode(child.key() + "|" + n.prodOrderId(), child.level(), child.materialId(), child.materialCode(), child.materialName(),
                        child.batchNo(), child.batchTracked(), n.qty(), n.prodOrderId(), n.prodOrderNo(), o == null ? null : o.getCompletedQty(), null, null,
                        null, false, child.children()));
            }
        }
        BatchDTO b = level == 0 && batchNo != null ? batchApi.get(m.id(), batchNo).orElse(null) : null;
        return new TraceTreeNode(m.id() + "|" + batchNo + "|" + level, level, m.id(), m.code(), m.name(), batchNo, m.tracking() != Tracking.NONE, qty, null,
                null, null, b == null ? null : b.supplierId(), b == null ? null : b.supplierBatchNo(), b == null ? null : b.sourceNo(),
                b != null && b.concession(), children);
    }

    private static Set<Long> collectMaterials(List<TraceNode> nodes, Long root) {
        Set<Long> ids = new HashSet<>();
        ids.add(root);
        for (TraceNode n : nodes) {
            ids.add(n.productMaterialId());
            ids.add(n.componentMaterialId());
        }
        return ids;
    }

    /** 导出（召回清单）：扁平行 */
    public List<TraceRow> exportRows(String direction, Long materialId, String batchNo) {
        List<TraceNode> nodes = "FORWARD".equals(direction) ? forward(materialId, batchNo) : backward(materialId, batchNo);
        Map<Long, MaterialDTO> ms = support.materials(collectMaterials(nodes, materialId));
        List<TraceRow> out = new ArrayList<>();
        for (TraceNode n : nodes) {
            MaterialDTO p = ms.get(n.productMaterialId());
            MaterialDTO c = ms.get(n.componentMaterialId());
            BatchDTO b = n.componentBatchNo() == null ? null : batchApi.get(n.componentMaterialId(), n.componentBatchNo()).orElse(null);
            out.add(new TraceRow(n.level(), n.prodOrderNo(), p == null ? null : p.code(), p == null ? null : p.name(), n.productBatchNo(),
                    c == null ? null : c.code(), c == null ? null : c.name(), n.componentBatchNo(), n.qty(), b == null ? null : b.supplierId()));
        }
        return out;
    }
}
