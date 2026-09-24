package com.erp.module.inventory.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.common.exception.BizException;
import com.erp.common.result.PageResult;
import com.erp.module.engineering.api.material.MaterialApi;
import com.erp.module.engineering.api.material.MaterialDTO;
import com.erp.module.inventory.api.InventoryErrorCodes;
import com.erp.module.inventory.api.batch.BatchApi;
import com.erp.module.inventory.api.batch.BatchDTO;
import com.erp.module.inventory.api.batch.BatchTxn;
import com.erp.module.inventory.controller.vo.BatchVOs.AvailableBatch;
import com.erp.module.inventory.controller.vo.BatchVOs.BatchDetail;
import com.erp.module.inventory.controller.vo.BatchVOs.BatchQuery;
import com.erp.module.inventory.controller.vo.BatchVOs.BatchRow;
import com.erp.module.inventory.controller.vo.BatchVOs.BatchUpdate;
import com.erp.module.inventory.controller.vo.BatchVOs.Distribution;
import com.erp.module.inventory.controller.vo.BatchVOs.SerialHistory;
import com.erp.module.inventory.controller.vo.BatchVOs.SerialQuery;
import com.erp.module.inventory.controller.vo.BatchVOs.SerialRow;
import com.erp.module.inventory.controller.vo.BatchVOs.TxnRow;
import com.erp.module.inventory.dal.dataobject.BatchDO;
import com.erp.module.inventory.dal.dataobject.LocationDO;
import com.erp.module.inventory.dal.dataobject.SerialDO;
import com.erp.module.inventory.dal.dataobject.StockDO;
import com.erp.module.inventory.dal.dataobject.StockTxnDO;
import com.erp.module.inventory.dal.dataobject.WarehouseDO;
import com.erp.module.inventory.dal.mapper.BatchMapper;
import com.erp.module.inventory.dal.mapper.SerialMapper;
import com.erp.module.inventory.dal.mapper.SerialTxnMapper;
import com.erp.module.inventory.dal.mapper.StockMapper;
import com.erp.module.inventory.dal.mapper.StockTxnMapper;
import com.erp.module.system.api.log.DocLogApi;
import com.erp.module.system.api.param.ParamApi;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** 批次与序列号（需求 08-07），同时是 {@link BatchApi} 的实现 */
@Service("invBatchService")
public class BatchService implements BatchApi {

    public static final String BIZ_TYPE = "INV_BATCH";
    static final String MODULE_QUALITY = "quality";

    private final BatchMapper batchMapper;
    private final StockMapper stockMapper;
    private final StockTxnMapper txnMapper;
    private final SerialMapper serialMapper;
    private final SerialTxnMapper serialTxnMapper;
    private final WarehouseService warehouseService;
    private final MaterialApi materialApi;
    private final ParamApi paramApi;
    private final DocLogApi docLogApi;

    public BatchService(BatchMapper batchMapper, StockMapper stockMapper, StockTxnMapper txnMapper, SerialMapper serialMapper,
                        SerialTxnMapper serialTxnMapper, WarehouseService warehouseService, MaterialApi materialApi, ParamApi paramApi,
                        DocLogApi docLogApi) {
        this.batchMapper = batchMapper;
        this.stockMapper = stockMapper;
        this.txnMapper = txnMapper;
        this.serialMapper = serialMapper;
        this.serialTxnMapper = serialTxnMapper;
        this.warehouseService = warehouseService;
        this.materialApi = materialApi;
        this.paramApi = paramApi;
        this.docLogApi = docLogApi;
    }

    // ==================== 批次查询 ====================

    public PageResult<BatchRow> page(BatchQuery q) {
        LocalDate today = LocalDate.now();
        int warnDays = paramApi.getInt("inv.alert.expiry-warn-days");
        LambdaQueryWrapper<BatchDO> w = new LambdaQueryWrapper<BatchDO>()
                .eq(q.getMaterialId() != null, BatchDO::getMaterialId, q.getMaterialId())
                .likeRight(StringUtils.hasText(q.getBatchNo()), BatchDO::getBatchNo, q.getBatchNo() == null ? null : q.getBatchNo().trim())
                .like(StringUtils.hasText(q.getSupplierBatchNo()), BatchDO::getSupplierBatchNo, q.getSupplierBatchNo())
                .eq(q.getSupplierId() != null, BatchDO::getSupplierId, q.getSupplierId())
                .eq(q.getFrozen() != null, BatchDO::getFrozen, q.getFrozen());
        if ("EXPIRED".equals(q.getExpiry())) w.lt(BatchDO::getExpireDate, today);
        else if ("SOON".equals(q.getExpiry())) w.ge(BatchDO::getExpireDate, today).le(BatchDO::getExpireDate, today.plusDays(warnDays));
        else if ("NORMAL".equals(q.getExpiry())) w.and(x -> x.isNull(BatchDO::getExpireDate).or().gt(BatchDO::getExpireDate, today.plusDays(warnDays)));
        Set<Long> allowed = warehouseService.accessibleIds();
        if (!Boolean.FALSE.equals(q.getHasStock())) {
            // 有库存：批次在（有权限的）仓库中现存量不为 0
            String whFilter = allowed == null ? "" : allowed.isEmpty() ? " AND 1 = 0"
                    : " AND s.warehouse_id IN (" + allowed.stream().map(String::valueOf).collect(Collectors.joining(",")) + ")";
            w.exists("SELECT 1 FROM inv_stock s WHERE s.deleted = 0 AND s.material_id = inv_batch.material_id AND s.batch_no = inv_batch.batch_no"
                    + " AND s.qty <> 0" + whFilter);
        }
        w.orderByAsc(BatchDO::getMaterialId).orderByAsc(BatchDO::getFirstInDate).orderByAsc(BatchDO::getBatchNo);
        PageResult<BatchDO> page = batchMapper.selectPage(q, w);
        return new PageResult<>(toRows(page.list(), allowed), page.total());
    }

    private List<BatchRow> toRows(List<BatchDO> list, Set<Long> allowed) {
        if (list.isEmpty()) return List.of();
        Set<Long> materialIds = list.stream().map(BatchDO::getMaterialId).collect(Collectors.toSet());
        Map<Long, MaterialDTO> materials = materialApi.getMaterials(materialIds).stream().collect(Collectors.toMap(MaterialDTO::id, m -> m));
        List<StockDO> stocks = stockMapper.selectList(new LambdaQueryWrapper<StockDO>().in(StockDO::getMaterialId, materialIds)
                .in(StockDO::getBatchNo, list.stream().map(BatchDO::getBatchNo).collect(Collectors.toSet())));
        Map<Long, WarehouseDO> whs = warehouseService.byIds(stocks.stream().map(StockDO::getWarehouseId).toList());
        Map<String, BigDecimal> onHand = new HashMap<>();
        Map<String, BigDecimal> available = new HashMap<>();
        for (StockDO s : stocks) {
            if (allowed != null && !allowed.contains(s.getWarehouseId())) continue;
            String key = s.getMaterialId() + "|" + s.getBatchNo();
            onHand.merge(key, s.getQty(), BigDecimal::add);
            WarehouseDO w = whs.get(s.getWarehouseId());
            if (w != null && w.getWarehouseType().available()) available.merge(key, s.getQty(), BigDecimal::add);
        }
        LocalDate today = LocalDate.now();
        return list.stream().map(b -> {
            MaterialDTO m = materials.get(b.getMaterialId());
            String key = b.getMaterialId() + "|" + b.getBatchNo();
            boolean unusable = Boolean.TRUE.equals(b.getFrozen()) || (b.getExpireDate() != null && b.getExpireDate().isBefore(today));
            return new BatchRow(b.getId(), b.getMaterialId(), m == null ? null : m.code(), m == null ? null : m.name(), m == null ? null : m.spec(),
                    m == null ? null : m.baseUom(), b.getBatchNo(), b.getSupplierBatchNo(), b.getSupplierId(), b.getProductionDate(), b.getExpireDate(),
                    b.getExpireDate() == null ? null : ChronoUnit.DAYS.between(today, b.getExpireDate()), b.getFirstInDate(),
                    onHand.getOrDefault(key, BigDecimal.ZERO), unusable ? BigDecimal.ZERO : available.getOrDefault(key, BigDecimal.ZERO),
                    Boolean.TRUE.equals(b.getIsConcession()), Boolean.TRUE.equals(b.getFrozen()), b.getFrozenReason(), b.getFrozenByModule(),
                    b.getSourceType(), b.getSourceNo(), b.getRemark(), b.getVersion());
        }).toList();
    }

    public BatchDetail detail(Long id) {
        BatchDO b = getOrThrow(id);
        Set<Long> allowed = warehouseService.accessibleIds();
        BatchRow row = toRows(List.of(b), allowed).get(0);
        List<StockDO> stocks = stockMapper.selectList(new LambdaQueryWrapper<StockDO>().eq(StockDO::getMaterialId, b.getMaterialId())
                .eq(StockDO::getBatchNo, b.getBatchNo()).ne(StockDO::getQty, 0));
        Map<Long, WarehouseDO> whs = warehouseService.byIds(stocks.stream().map(StockDO::getWarehouseId).toList());
        Map<Long, LocationDO> locs = warehouseService.locationsByIds(stocks.stream().map(StockDO::getLocationId).toList());
        List<Distribution> dist = stocks.stream().filter(s -> allowed == null || allowed.contains(s.getWarehouseId()))
                .map(s -> new Distribution(s.getWarehouseId(), whs.containsKey(s.getWarehouseId()) ? whs.get(s.getWarehouseId()).getName() : null,
                        s.getLocationId(), locs.containsKey(s.getLocationId()) ? locs.get(s.getLocationId()).getCode() : null, s.getQty()))
                .toList();
        List<StockTxnDO> txns = txnMapper.selectList(new LambdaQueryWrapper<StockTxnDO>().eq(StockTxnDO::getMaterialId, b.getMaterialId())
                .eq(StockTxnDO::getBatchNo, b.getBatchNo()).orderByAsc(StockTxnDO::getCreatedAt).orderByAsc(StockTxnDO::getId));
        Map<Long, WarehouseDO> txnWhs = warehouseService.byIds(txns.stream().map(StockTxnDO::getWarehouseId).toList());
        return new BatchDetail(row, dist, txns.stream().map(t -> new TxnRow(t.getId(), t.getBizDate(), t.getDirection(), t.getBizType(), t.getDocType(),
                t.getDocId(), t.getDocNo(), t.getSourceType(), t.getSourceNo(), t.getWarehouseId(),
                txnWhs.containsKey(t.getWarehouseId()) ? txnWhs.get(t.getWarehouseId()).getName() : null, t.getQty(), t.getBalanceQty(),
                Boolean.TRUE.equals(t.getIsReversal()), t.getCreatedAt())).toList());
    }

    /** BatchSelect：本仓库某物料有库存的批次（冻结、过期标记出来，前端不可选） */
    public List<AvailableBatch> available(Long materialId, Long warehouseId) {
        List<StockDO> stocks = stockMapper.selectList(new LambdaQueryWrapper<StockDO>().eq(StockDO::getMaterialId, materialId)
                .eq(StockDO::getWarehouseId, warehouseId).gt(StockDO::getQty, 0).ne(StockDO::getBatchNo, ""));
        if (stocks.isEmpty()) return List.of();
        Map<String, BatchDO> batches = batchMapper.selectList(new LambdaQueryWrapper<BatchDO>().eq(BatchDO::getMaterialId, materialId)
                        .in(BatchDO::getBatchNo, stocks.stream().map(StockDO::getBatchNo).collect(Collectors.toSet())))
                .stream().collect(Collectors.toMap(BatchDO::getBatchNo, x -> x));
        Map<Long, LocationDO> locs = warehouseService.locationsByIds(stocks.stream().map(StockDO::getLocationId).toList());
        LocalDate today = LocalDate.now();
        return stocks.stream().map(s -> {
            BatchDO b = batches.get(s.getBatchNo());
            return new AvailableBatch(s.getBatchNo(), s.getLocationId() == 0 ? null : s.getLocationId(),
                    locs.containsKey(s.getLocationId()) ? locs.get(s.getLocationId()).getCode() : null, s.getQty(),
                    b == null ? null : b.getProductionDate(), b == null ? null : b.getExpireDate(), b == null ? null : b.getFirstInDate(),
                    b != null && Boolean.TRUE.equals(b.getFrozen()), b != null && b.getExpireDate() != null && b.getExpireDate().isBefore(today));
        }).sorted(Comparator.comparing((AvailableBatch a) -> a.inDate() == null ? LocalDate.MAX : a.inDate()).thenComparing(AvailableBatch::batchNo))
                .toList();
    }

    // ==================== 维护 ====================

    @Transactional(rollbackFor = Exception.class)
    public void freezeById(Long id, String reason) {
        BatchDO b = getOrThrow(id);
        doFreeze(b, reason, null, null);
    }

    @Transactional(rollbackFor = Exception.class)
    public void unfreezeById(Long id, String reason) {
        BatchDO b = getOrThrow(id);
        doUnfreeze(b, reason, null);
    }

    private void doFreeze(BatchDO b, String reason, String module, String sourceNo) {
        if (!StringUtils.hasText(reason)) throw new BizException(InventoryErrorCodes.BATCH_REASON_REQUIRED);
        b.setFrozen(true);
        b.setFrozenReason(reason.trim());
        b.setFrozenByModule(module);
        b.setFrozenSourceNo(sourceNo);
        batchMapper.updateByIdOrFail(b);
        docLogApi.record(BIZ_TYPE, b.getId(), b.getBatchNo(), "FREEZE", "冻结", "NORMAL", "FROZEN", reason.trim());
    }

    /** INV-BAT-R05：品质冻结的批次，仓库人员不能手工解冻 */
    private void doUnfreeze(BatchDO b, String reason, String module) {
        if (!StringUtils.hasText(reason)) throw new BizException(InventoryErrorCodes.BATCH_REASON_REQUIRED);
        if (MODULE_QUALITY.equals(b.getFrozenByModule()) && !MODULE_QUALITY.equals(module)) {
            throw BizException.of(InventoryErrorCodes.BATCH_FROZEN_BY_QUALITY, Objects.toString(b.getFrozenSourceNo(), ""));
        }
        b.setFrozen(false);
        b.setFrozenReason(null);
        b.setFrozenByModule(null);
        b.setFrozenSourceNo(null);
        batchMapper.updateByIdOrFail(b);
        docLogApi.record(BIZ_TYPE, b.getId(), b.getBatchNo(), "UNFREEZE", "解冻", "FROZEN", "NORMAL", reason.trim());
    }

    /** 修改批次属性；修改到期日期需要原因 */
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, BatchUpdate req) {
        BatchDO b = getOrThrow(id);
        boolean expireChanged = !Objects.equals(b.getExpireDate(), req.expireDate());
        if (expireChanged && !StringUtils.hasText(req.reason())) throw new BizException(InventoryErrorCodes.BATCH_REASON_REQUIRED);
        b.setSupplierBatchNo(StringUtils.hasText(req.supplierBatchNo()) ? req.supplierBatchNo().trim() : null);
        b.setProductionDate(req.productionDate());
        b.setExpireDate(req.expireDate());
        b.setRemark(StringUtils.hasText(req.remark()) ? req.remark().trim() : null);
        if (req.version() != null) b.setVersion(req.version());
        batchMapper.updateByIdOrFail(b);
        docLogApi.record(BIZ_TYPE, b.getId(), b.getBatchNo(), "UPDATE", expireChanged ? "修改到期日期" : "修改属性", null, null,
                expireChanged ? req.reason().trim() : null);
    }

    /** 特采标记（检验调拨确认时） */
    @Transactional(rollbackFor = Exception.class)
    public void markConcession(Long materialId, String batchNo) {
        BatchDO b = batchMapper.selectOne(new LambdaQueryWrapper<BatchDO>().eq(BatchDO::getMaterialId, materialId).eq(BatchDO::getBatchNo, batchNo));
        if (b == null || Boolean.TRUE.equals(b.getIsConcession())) return;
        b.setIsConcession(true);
        batchMapper.updateByIdOrFail(b);
    }

    public BatchDO getOrThrow(Long id) {
        BatchDO b = id == null ? null : batchMapper.selectById(id);
        if (b == null) throw new BizException(InventoryErrorCodes.BATCH_NOT_EXISTS);
        return b;
    }

    public BatchDO find(Long materialId, String batchNo) {
        if (!StringUtils.hasText(batchNo)) return null;
        return batchMapper.selectOne(new LambdaQueryWrapper<BatchDO>().eq(BatchDO::getMaterialId, materialId).eq(BatchDO::getBatchNo, batchNo.trim()));
    }

    // ==================== BatchApi ====================

    @Override
    public Optional<BatchDTO> get(Long materialId, String batchNo) {
        return Optional.ofNullable(find(materialId, batchNo)).map(b -> new BatchDTO(b.getId(), b.getMaterialId(), b.getBatchNo(), b.getSupplierId(),
                b.getSupplierBatchNo(), b.getProductionDate(), b.getExpireDate(), b.getFirstInDate(), b.getSourceType(), b.getSourceNo(),
                Boolean.TRUE.equals(b.getIsConcession()), Boolean.TRUE.equals(b.getFrozen()), b.getFrozenReason()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void freeze(Long materialId, String batchNo, String reason, String moduleCode, String sourceNo) {
        BatchDO b = find(materialId, batchNo);
        if (b == null) throw new BizException(InventoryErrorCodes.BATCH_NOT_EXISTS);
        doFreeze(b, reason, moduleCode, sourceNo);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unfreeze(Long materialId, String batchNo, String reason, String moduleCode) {
        BatchDO b = find(materialId, batchNo);
        if (b == null) throw new BizException(InventoryErrorCodes.BATCH_NOT_EXISTS);
        doUnfreeze(b, reason, moduleCode);
    }

    @Override
    public List<BatchTxn> trace(Long materialId, String batchNo) {
        return txnMapper.selectList(new LambdaQueryWrapper<StockTxnDO>().eq(StockTxnDO::getMaterialId, materialId).eq(StockTxnDO::getBatchNo, batchNo)
                        .orderByAsc(StockTxnDO::getCreatedAt).orderByAsc(StockTxnDO::getId))
                .stream().map(t -> new BatchTxn(t.getId(), t.getBizDate(), t.getDirection(), t.getBizType(), t.getDocNo(), t.getSourceType(),
                        t.getSourceNo(), t.getWarehouseId(), t.getQty())).toList();
    }

    // ==================== 序列号 ====================

    public PageResult<SerialRow> serials(SerialQuery q) {
        LambdaQueryWrapper<SerialDO> w = new LambdaQueryWrapper<SerialDO>()
                .eq(q.getMaterialId() != null, SerialDO::getMaterialId, q.getMaterialId())
                .likeRight(StringUtils.hasText(q.getSerialNo()), SerialDO::getSerialNo, q.getSerialNo() == null ? null : q.getSerialNo().trim())
                .eq(StringUtils.hasText(q.getStatus()), SerialDO::getSerialStatus, q.getStatus())
                .eq(q.getCustomerId() != null, SerialDO::getCustomerId, q.getCustomerId())
                .orderByAsc(SerialDO::getMaterialId).orderByAsc(SerialDO::getSerialNo);
        PageResult<SerialDO> page = serialMapper.selectPage(q, w);
        Map<Long, MaterialDTO> materials = materialApi.getMaterials(page.list().stream().map(SerialDO::getMaterialId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(MaterialDTO::id, m -> m));
        Map<Long, WarehouseDO> whs = warehouseService.byIds(page.list().stream().map(SerialDO::getWarehouseId).toList());
        Map<Long, LocationDO> locs = warehouseService.locationsByIds(page.list().stream().map(SerialDO::getLocationId).toList());
        Set<Long> txnIds = new HashSet<>();
        page.list().forEach(s -> {
            if (s.getLastTxnId() != null) txnIds.add(s.getLastTxnId());
        });
        Map<Long, String> docNos = txnIds.isEmpty() ? Map.of() : txnMapper.selectBatchIds(txnIds).stream()
                .collect(Collectors.toMap(StockTxnDO::getId, StockTxnDO::getDocNo));
        List<SerialRow> rows = new ArrayList<>();
        for (SerialDO s : page.list()) {
            MaterialDTO m = materials.get(s.getMaterialId());
            rows.add(new SerialRow(s.getId(), s.getMaterialId(), m == null ? null : m.code(), m == null ? null : m.name(), s.getSerialNo(),
                    s.getBatchNo(), s.getSerialStatus(), s.getWarehouseId(), whs.containsKey(s.getWarehouseId()) ? whs.get(s.getWarehouseId()).getName() : null,
                    s.getLocationId(), s.getLocationId() != null && locs.containsKey(s.getLocationId()) ? locs.get(s.getLocationId()).getCode() : null,
                    s.getCustomerId(), docNos.get(s.getLastTxnId())));
        }
        return new PageResult<>(rows, page.total());
    }

    public List<SerialHistory> serialHistory(Long serialId) {
        return serialTxnMapper.selectBySerial(serialId).stream()
                .map(r -> new SerialHistory(r.getTxnId(), r.getDirection(), r.getDocNo(), r.getCreatedAt())).toList();
    }
}
