package com.erp.module.quality.service.report;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.common.exception.BizException;
import com.erp.module.engineering.api.material.MaterialDTO;
import com.erp.module.inventory.api.batch.BatchApi;
import com.erp.module.inventory.api.batch.BatchDTO;
import com.erp.module.inventory.api.batch.BatchTxn;
import com.erp.module.inventory.api.warehouse.WarehouseApi;
import com.erp.module.inventory.api.warehouse.WarehouseDTO;
import com.erp.module.production.api.trace.TraceApi;
import com.erp.module.production.api.trace.TraceNode;
import com.erp.module.quality.api.QualityErrorCodes;
import com.erp.module.quality.config.QualityModuleConfig;
import com.erp.module.quality.controller.vo.ReportVOs.BatchInspection;
import com.erp.module.quality.controller.vo.ReportVOs.BatchStock;
import com.erp.module.quality.controller.vo.ReportVOs.FreezeItem;
import com.erp.module.quality.controller.vo.ReportVOs.FreezeReq;
import com.erp.module.quality.controller.vo.ReportVOs.FreezeResult;
import com.erp.module.quality.controller.vo.ReportVOs.ShipRow;
import com.erp.module.quality.controller.vo.ReportVOs.TraceResult;
import com.erp.module.quality.controller.vo.ReportVOs.TraceRow;
import com.erp.module.quality.controller.vo.ReportVOs.WarehouseQty;
import com.erp.module.quality.dal.dataobject.QcInspectionDO;
import com.erp.module.quality.dal.dataobject.QcNcrDO;
import com.erp.module.quality.dal.mapper.QcInspectionMapper;
import com.erp.module.quality.dal.mapper.QcNcrMapper;
import com.erp.module.quality.service.InspStatus;
import com.erp.module.quality.service.QcSupport;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** 质量追溯（10-07 第 1 节）：在生产追溯上叠加检验记录、特采 / 冻结标记、库存分布与出货 */
@Service
public class TraceService {

    private final TraceApi traceApi;
    private final BatchApi batchApi;
    private final WarehouseApi warehouseApi;
    private final QcInspectionMapper inspectionMapper;
    private final QcNcrMapper ncrMapper;
    private final QcSupport support;

    public TraceService(TraceApi traceApi, BatchApi batchApi, WarehouseApi warehouseApi, QcInspectionMapper inspectionMapper, QcNcrMapper ncrMapper,
                        QcSupport support) {
        this.traceApi = traceApi;
        this.batchApi = batchApi;
        this.warehouseApi = warehouseApi;
        this.inspectionMapper = inspectionMapper;
        this.ncrMapper = ncrMapper;
        this.support = support;
    }

    /** 反向：成品批次 → 原材料批次，每个节点带检验记录 */
    public TraceResult backward(Long materialId, String batchNo) {
        return build(materialId, batchNo, traceApi.backward(materialId, batchNo.trim()), false);
    }

    /** 正向：原材料批次 → 成品批次 → 库存分布与出货（召回清单） */
    public TraceResult forward(Long materialId, String batchNo) {
        return build(materialId, batchNo, traceApi.forward(materialId, batchNo.trim()), true);
    }

    private TraceResult build(Long materialId, String batchNo, List<TraceNode> nodes, boolean forward) {
        String batch = batchNo.trim();
        Set<Long> mids = new LinkedHashSet<>();
        mids.add(materialId);
        for (TraceNode n : nodes) {
            mids.add(n.productMaterialId());
            mids.add(n.componentMaterialId());
        }
        Map<Long, MaterialDTO> ms = support.materials(mids);
        Map<String, List<BatchInspection>> insCache = new HashMap<>();
        Map<String, Optional<BatchDTO>> batchCache = new HashMap<>();
        List<TraceRow> rows = new ArrayList<>();
        for (TraceNode n : nodes) {
            MaterialDTO p = ms.get(n.productMaterialId());
            MaterialDTO c = ms.get(n.componentMaterialId());
            Optional<BatchDTO> pb = batch(batchCache, n.productMaterialId(), n.productBatchNo());
            Optional<BatchDTO> cb = batch(batchCache, n.componentMaterialId(), n.componentBatchNo());
            rows.add(new TraceRow(n.level(), n.prodOrderId(), n.prodOrderNo(), n.productMaterialId(), p == null ? null : p.code(), p == null ? null : p.name(),
                    n.productBatchNo(), n.componentMaterialId(), c == null ? null : c.code(), c == null ? null : c.name(), n.componentBatchNo(), n.qty(),
                    pb.map(BatchDTO::concession).orElse(false), pb.map(BatchDTO::frozen).orElse(false), cb.map(BatchDTO::concession).orElse(false),
                    cb.map(BatchDTO::frozen).orElse(false), inspections(insCache, n.productMaterialId(), n.productBatchNo()),
                    inspections(insCache, n.componentMaterialId(), n.componentBatchNo())));
        }
        List<BatchStock> affected = new ArrayList<>();
        if (forward) {
            Map<String, FreezeItem> products = new LinkedHashMap<>();
            products.put(materialId + "|" + batch, new FreezeItem(materialId, batch));
            for (TraceNode n : nodes) {
                if (StringUtils.hasText(n.productBatchNo())) products.putIfAbsent(n.productMaterialId() + "|" + n.productBatchNo(), new FreezeItem(n.productMaterialId(), n.productBatchNo()));
            }
            Map<Long, String> whNames = new HashMap<>();
            for (FreezeItem f : products.values()) affected.add(stock(f.materialId(), f.batchNo(), ms, whNames, batchCache));
        }
        MaterialDTO root = ms.get(materialId);
        return new TraceResult(materialId, root == null ? null : root.code(), root == null ? null : root.name(), batch,
                batch(batchCache, materialId, batch).map(BatchDTO::frozen).orElse(false), inspections(insCache, materialId, batch), rows, affected);
    }

    private Optional<BatchDTO> batch(Map<String, Optional<BatchDTO>> cache, Long materialId, String batchNo) {
        if (materialId == null || !StringUtils.hasText(batchNo)) return Optional.empty();
        return cache.computeIfAbsent(materialId + "|" + batchNo, k -> batchApi.get(materialId, batchNo));
    }

    private List<BatchInspection> inspections(Map<String, List<BatchInspection>> cache, Long materialId, String batchNo) {
        if (materialId == null || !StringUtils.hasText(batchNo)) return List.of();
        return cache.computeIfAbsent(materialId + "|" + batchNo, k -> {
            List<QcInspectionDO> list = inspectionMapper.selectList(new LambdaQueryWrapper<QcInspectionDO>().eq(QcInspectionDO::getMaterialId, materialId)
                    .eq(QcInspectionDO::getBatchNo, batchNo).ne(QcInspectionDO::getInspStatus, InspStatus.CANCELED.name()).orderByAsc(QcInspectionDO::getId));
            List<Long> ncrIds = list.stream().map(QcInspectionDO::getNcrId).filter(Objects::nonNull).toList();
            Map<Long, String> ncrNos = new HashMap<>();
            if (!ncrIds.isEmpty()) ncrMapper.selectBatchIds(ncrIds).forEach(n -> ncrNos.put(n.getId(), n.getDocNo()));
            return list.stream().map(d -> new BatchInspection(d.getId(), d.getDocNo(), d.getInspectType(), d.getResult(), d.getQualifiedQty(), d.getConcessionQty(),
                    d.getRejectedQty(), d.getNcrId(), ncrNos.get(d.getNcrId()), d.getJudgeAt())).toList();
        });
    }

    /** 批次在各仓库的结存（流水汇总）与销售出库记录 */
    private BatchStock stock(Long materialId, String batchNo, Map<Long, MaterialDTO> ms, Map<Long, String> whNames, Map<String, Optional<BatchDTO>> batchCache) {
        List<BatchTxn> txns = batchApi.trace(materialId, batchNo);
        Map<Long, BigDecimal> byWh = new LinkedHashMap<>();
        List<ShipRow> ships = new ArrayList<>();
        BigDecimal shipped = BigDecimal.ZERO;
        for (BatchTxn t : txns) {
            BigDecimal q = t.qty() == null ? BigDecimal.ZERO : t.qty();
            boolean in = "IN".equals(t.direction());
            byWh.merge(t.warehouseId(), in ? q : q.negate(), BigDecimal::add);
            if (!in && "SALES_OUT".equals(t.bizType())) {
                ships.add(new ShipRow(t.bizDate(), t.docNo(), t.sourceNo(), q));
                shipped = shipped.add(q);
            }
        }
        List<WarehouseQty> whs = new ArrayList<>();
        BigDecimal onHand = BigDecimal.ZERO;
        for (Map.Entry<Long, BigDecimal> e : byWh.entrySet()) {
            if (e.getValue().signum() <= 0) continue;
            String name = whNames.computeIfAbsent(e.getKey(), id -> warehouseApi.get(id).map(WarehouseDTO::name).orElse(String.valueOf(id)));
            whs.add(new WarehouseQty(e.getKey(), name, e.getValue()));
            onHand = onHand.add(e.getValue());
        }
        MaterialDTO m = ms.get(materialId);
        return new BatchStock(materialId, m == null ? null : m.code(), m == null ? null : m.name(), batchNo,
                batch(batchCache, materialId, batchNo).map(BatchDTO::frozen).orElse(false), whs, onHand, shipped, ships);
    }

    /** 冻结全部在库批次（需关联 NCR，冻结来源为品质 + NCR 编号） */
    @Transactional(rollbackFor = Exception.class)
    public FreezeResult freeze(FreezeReq req) {
        if (req.ncrId() == null) throw new BizException(QualityErrorCodes.TRACE_NCR_REQUIRED);
        QcNcrDO ncr = ncrMapper.selectById(req.ncrId());
        if (ncr == null) throw BizException.of(QualityErrorCodes.NOT_EXISTS, "NCR");
        int n = 0;
        List<String> skipped = new ArrayList<>();
        for (FreezeItem f : req.items() == null ? List.<FreezeItem>of() : req.items()) {
            Optional<BatchDTO> b = batchApi.get(f.materialId(), f.batchNo());
            if (b.isEmpty()) {
                skipped.add(f.batchNo() + "（批次不存在）");
                continue;
            }
            if (b.get().frozen()) {
                skipped.add(f.batchNo() + "（已冻结）");
                continue;
            }
            batchApi.freeze(f.materialId(), f.batchNo(), "质量追溯冻结（NCR " + ncr.getDocNo() + "）", QualityModuleConfig.MODULE, ncr.getDocNo());
            n++;
        }
        support.log(QualityModuleConfig.NCR, ncr.getId(), ncr.getDocNo(), "TRACE_FREEZE", "追溯冻结批次", ncr.getStatus().name(), ncr.getStatus().name(),
                "冻结 " + n + " 个批次");
        return new FreezeResult(n, skipped);
    }
}
