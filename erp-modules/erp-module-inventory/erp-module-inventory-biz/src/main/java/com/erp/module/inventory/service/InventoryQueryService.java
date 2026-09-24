package com.erp.module.inventory.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.common.enums.DocStatus;
import com.erp.common.enums.EnableStatus;
import com.erp.module.engineering.api.material.IssueRule;
import com.erp.module.engineering.api.material.MaterialApi;
import com.erp.module.engineering.api.material.MaterialReferenceChecker;
import com.erp.module.engineering.api.material.MaterialUsage;
import com.erp.module.inventory.api.stock.BatchSuggestion;
import com.erp.module.inventory.api.stock.InventoryApi;
import com.erp.module.inventory.api.stock.InventoryQueryApi;
import com.erp.module.inventory.api.stock.ReservationApi;
import com.erp.module.inventory.api.stock.StockPostingRequest;
import com.erp.module.inventory.api.stock.StockSummary;
import com.erp.module.inventory.api.warehouse.WarehouseType;
import com.erp.module.inventory.dal.dataobject.BatchDO;
import com.erp.module.inventory.dal.dataobject.ReservationDO;
import com.erp.module.inventory.dal.dataobject.StockDO;
import com.erp.module.inventory.dal.dataobject.StockInDO;
import com.erp.module.inventory.dal.dataobject.StockInLineDO;
import com.erp.module.inventory.dal.dataobject.StockOutDO;
import com.erp.module.inventory.dal.dataobject.StockOutLineDO;
import com.erp.module.inventory.dal.dataobject.StockTxnDO;
import com.erp.module.inventory.dal.dataobject.WarehouseDO;
import com.erp.module.inventory.dal.mapper.BatchMapper;
import com.erp.module.inventory.dal.mapper.ReservationMapper;
import com.erp.module.inventory.dal.mapper.StockInLineMapper;
import com.erp.module.inventory.dal.mapper.StockInMapper;
import com.erp.module.inventory.dal.mapper.StockMapper;
import com.erp.module.inventory.dal.mapper.StockOutLineMapper;
import com.erp.module.inventory.dal.mapper.StockOutMapper;
import com.erp.module.inventory.dal.mapper.StockTxnMapper;
import com.erp.module.inventory.dal.mapper.WarehouseMapper;
import com.erp.module.inventory.service.posting.PostingModels;
import com.erp.module.inventory.service.posting.StockPostingService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 库存查询与预留（需求 08-02 第 3 节统一口径）：
 * 可用现存量 = 可用仓且启用、批次未冻结未过期的现存量；可用量 = 可用现存量 − 有效预留。
 * 同时实现研发工程的 {@link MaterialReferenceChecker}（物料停用提示、删除、修改库存管理方式）。
 */
@Service
public class InventoryQueryService implements InventoryQueryApi, ReservationApi, InventoryApi, MaterialReferenceChecker {

    static final String ACTIVE = "ACTIVE";
    static final String RELEASED = "RELEASED";
    private static final List<DocStatus> OPEN = List.of(DocStatus.DRAFT, DocStatus.PENDING_APPROVAL, DocStatus.APPROVED);

    private final StockMapper stockMapper;
    private final WarehouseMapper warehouseMapper;
    private final BatchMapper batchMapper;
    private final ReservationMapper reservationMapper;
    private final StockTxnMapper txnMapper;
    private final StockInMapper stockInMapper;
    private final StockInLineMapper stockInLineMapper;
    private final StockOutMapper stockOutMapper;
    private final StockOutLineMapper stockOutLineMapper;
    private final MaterialApi materialApi;
    private final StockPostingService postingService;

    public InventoryQueryService(StockMapper stockMapper, WarehouseMapper warehouseMapper, BatchMapper batchMapper, ReservationMapper reservationMapper,
                                 StockTxnMapper txnMapper, StockInMapper stockInMapper, StockInLineMapper stockInLineMapper,
                                 StockOutMapper stockOutMapper, StockOutLineMapper stockOutLineMapper, MaterialApi materialApi,
                                 StockPostingService postingService) {
        this.stockMapper = stockMapper;
        this.warehouseMapper = warehouseMapper;
        this.batchMapper = batchMapper;
        this.reservationMapper = reservationMapper;
        this.txnMapper = txnMapper;
        this.stockInMapper = stockInMapper;
        this.stockInLineMapper = stockInLineMapper;
        this.stockOutMapper = stockOutMapper;
        this.stockOutLineMapper = stockOutLineMapper;
        this.materialApi = materialApi;
        this.postingService = postingService;
    }

    // ==================== 可用量 ====================

    /** 库存行是否计入可用量：仓库为可用仓且启用、批次未冻结未过期 */
    public UsableFilter usableFilter(Collection<StockDO> stocks) {
        Set<Long> whIds = stocks.stream().map(StockDO::getWarehouseId).collect(Collectors.toSet());
        Map<Long, WarehouseDO> whs = whIds.isEmpty() ? Map.of()
                : warehouseMapper.selectBatchIds(whIds).stream().collect(Collectors.toMap(WarehouseDO::getId, w -> w));
        Set<String> blocked = new HashSet<>();
        Set<Long> materialIds = stocks.stream().filter(s -> !s.getBatchNo().isEmpty()).map(StockDO::getMaterialId).collect(Collectors.toSet());
        if (!materialIds.isEmpty()) {
            LocalDate today = LocalDate.now();
            batchMapper.selectList(new LambdaQueryWrapper<BatchDO>().in(BatchDO::getMaterialId, materialIds)
                            .and(x -> x.eq(BatchDO::getFrozen, true).or().lt(BatchDO::getExpireDate, today)))
                    .forEach(b -> blocked.add(b.getMaterialId() + "|" + b.getBatchNo()));
        }
        return new UsableFilter(whs, blocked);
    }

    public record UsableFilter(Map<Long, WarehouseDO> warehouses, Set<String> blockedBatches) {
        public boolean usable(StockDO s) {
            WarehouseDO w = warehouses.get(s.getWarehouseId());
            return w != null && w.getWarehouseType().available() && w.getStatus() == EnableStatus.ENABLED
                    && !blockedBatches.contains(s.getMaterialId() + "|" + s.getBatchNo());
        }

        public WarehouseType typeOf(StockDO s) {
            WarehouseDO w = warehouses.get(s.getWarehouseId());
            return w == null ? null : w.getWarehouseType();
        }
    }

    /** 有效预留（qty − released），按物料；warehouseId 非空时只算该仓库的预留 */
    public Map<Long, BigDecimal> reserved(Collection<Long> materialIds, Long warehouseId) {
        if (materialIds.isEmpty()) return Map.of();
        Map<Long, BigDecimal> map = new HashMap<>();
        reservationMapper.selectList(new LambdaQueryWrapper<ReservationDO>().in(ReservationDO::getMaterialId, materialIds)
                        .eq(ReservationDO::getStatus, ACTIVE).eq(warehouseId != null, ReservationDO::getWarehouseId, warehouseId))
                .forEach(r -> map.merge(r.getMaterialId(), r.getQty().subtract(r.getReleasedQty()).max(BigDecimal.ZERO), BigDecimal::add));
        return map;
    }

    @Override
    public BigDecimal getAvailableQty(Long materialId) {
        return getStockSummary(List.of(materialId)).get(materialId).availableQty();
    }

    @Override
    public BigDecimal getAvailableQty(Long materialId, Long warehouseId) {
        List<StockDO> stocks = stockMapper.selectList(new LambdaQueryWrapper<StockDO>().eq(StockDO::getMaterialId, materialId).eq(StockDO::getWarehouseId, warehouseId));
        UsableFilter f = usableFilter(stocks);
        BigDecimal usable = stocks.stream().filter(f::usable).map(StockDO::getQty).reduce(BigDecimal.ZERO, BigDecimal::add);
        return usable.subtract(reserved(List.of(materialId), warehouseId).getOrDefault(materialId, BigDecimal.ZERO));
    }

    @Override
    public Map<Long, StockSummary> getStockSummary(Collection<Long> materialIds) {
        Map<Long, StockSummary> result = new HashMap<>();
        if (materialIds == null || materialIds.isEmpty()) return result;
        List<StockDO> stocks = stockMapper.selectList(new LambdaQueryWrapper<StockDO>().in(StockDO::getMaterialId, materialIds));
        UsableFilter f = usableFilter(stocks);
        Map<Long, BigDecimal> reserved = reserved(materialIds, null);
        Map<Long, BigDecimal[]> acc = new HashMap<>();
        for (StockDO s : stocks) {
            BigDecimal[] a = acc.computeIfAbsent(s.getMaterialId(), k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO});
            a[0] = a[0].add(s.getQty());
            if (f.usable(s)) a[1] = a[1].add(s.getQty());
            if (f.typeOf(s) == WarehouseType.QC) a[2] = a[2].add(s.getQty());
            if (f.typeOf(s) == WarehouseType.NG) a[3] = a[3].add(s.getQty());
        }
        for (Long id : materialIds) {
            BigDecimal[] a = acc.getOrDefault(id, new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO});
            BigDecimal r = reserved.getOrDefault(id, BigDecimal.ZERO);
            result.put(id, new StockSummary(id, a[0], a[1].subtract(r), r, a[2], a[3]));
        }
        return result;
    }

    @Override
    public List<BatchSuggestion> suggestBatches(Long materialId, Long warehouseId, BigDecimal qty) {
        List<StockDO> stocks = stockMapper.selectList(new LambdaQueryWrapper<StockDO>().eq(StockDO::getMaterialId, materialId)
                .eq(StockDO::getWarehouseId, warehouseId).gt(StockDO::getQty, 0));
        UsableFilter f = usableFilter(stocks);
        Map<String, BatchDO> batches = stocks.stream().anyMatch(s -> !s.getBatchNo().isEmpty())
                ? batchMapper.selectList(new LambdaQueryWrapper<BatchDO>().eq(BatchDO::getMaterialId, materialId)).stream()
                .collect(Collectors.toMap(BatchDO::getBatchNo, b -> b))
                : Map.of();
        boolean fefo = materialApi.getStockAttr(materialId).issueRule() == IssueRule.FEFO;
        // 冻结、过期批次不参与推荐；仓库是否可用由调用方决定（报废出库可从不良品仓出）
        List<StockDO> candidates = stocks.stream().filter(s -> !f.blockedBatches().contains(s.getMaterialId() + "|" + s.getBatchNo()))
                .sorted(Comparator.comparing((StockDO s) -> {
                    BatchDO b = batches.get(s.getBatchNo());
                    if (b == null) return LocalDate.MAX;
                    return fefo ? (b.getExpireDate() == null ? LocalDate.MAX : b.getExpireDate()) : b.getFirstInDate();
                }).thenComparing(StockDO::getBatchNo).thenComparing(StockDO::getLocationId))
                .toList();
        List<BatchSuggestion> out = new ArrayList<>();
        BigDecimal rest = qty == null ? BigDecimal.ZERO : qty;
        for (StockDO s : candidates) {
            if (rest.signum() <= 0) break;
            BigDecimal take = s.getQty().min(rest);
            BatchDO b = batches.get(s.getBatchNo());
            out.add(new BatchSuggestion(s.getBatchNo().isEmpty() ? null : s.getBatchNo(), s.getLocationId() == 0 ? null : s.getLocationId(), take,
                    b == null ? null : b.getProductionDate(), b == null ? null : b.getExpireDate()));
            rest = rest.subtract(take);
        }
        return out;
    }

    @Override
    public Map<WarehouseType, BigDecimal> getOnHandByWarehouseType(Long materialId) {
        List<StockDO> stocks = stockMapper.selectList(new LambdaQueryWrapper<StockDO>().eq(StockDO::getMaterialId, materialId));
        UsableFilter f = usableFilter(stocks);
        Map<WarehouseType, BigDecimal> map = new EnumMap<>(WarehouseType.class);
        stocks.forEach(s -> {
            WarehouseType t = f.typeOf(s);
            if (t != null) map.merge(t, s.getQty(), BigDecimal::add);
        });
        return map;
    }

    // ==================== 预留 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reserve(String bizType, Long bizId, String bizNo, List<ReservationApi.Line> lines) {
        release(bizType, bizId);
        for (ReservationApi.Line l : lines) {
            if (l.qty() == null || l.qty().signum() <= 0) continue;
            ReservationDO r = new ReservationDO();
            r.setMaterialId(l.materialId());
            r.setWarehouseId(l.warehouseId());
            r.setBatchNo(l.batchNo());
            r.setQty(l.qty());
            r.setReleasedQty(BigDecimal.ZERO);
            r.setBizType(bizType);
            r.setBizId(bizId);
            r.setBizLineId(l.bizLineId());
            r.setBizNo(bizNo);
            r.setStatus(ACTIVE);
            reservationMapper.insert(r);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void release(String bizType, Long bizId) {
        for (ReservationDO r : reservationMapper.selectList(new LambdaQueryWrapper<ReservationDO>().eq(ReservationDO::getBizType, bizType)
                .eq(ReservationDO::getBizId, bizId).eq(ReservationDO::getStatus, ACTIVE))) {
            r.setReleasedQty(r.getQty());
            r.setStatus(RELEASED);
            reservationMapper.updateByIdOrFail(r);
        }
    }

    // ==================== InventoryApi（旧契约，模块内部使用） ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void post(StockPostingRequest request) {
        List<PostingModels.PostLine> lines = request.lines().stream().map(l -> new PostingModels.PostLine(l.bizLineId(), l.bizLineId(),
                request.direction(), l.materialId(), l.warehouseId(), l.locationId(), l.batchNo(), l.qty(), l.unitCost(), l.serialNos(), false, null)).toList();
        postingService.post(new PostingModels.PostCommand("API", request.bizType(), request.bizId(), request.bizNo(), request.bizType(), request.bizId(),
                request.bizNo(), request.bizDate() == null ? LocalDate.now() : request.bizDate(), lines, false, false, null));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reverse(String bizType, Long bizId) {
        postingService.reverse("API", bizId, bizType, Map.of());
    }

    // ==================== 物料引用（研发工程扩展点） ====================

    @Override
    public MaterialUsage usage(Long materialId) {
        BigDecimal stock = stockMapper.selectList(new LambdaQueryWrapper<StockDO>().eq(StockDO::getMaterialId, materialId)).stream()
                .map(StockDO::getQty).reduce(BigDecimal.ZERO, BigDecimal::add);
        Set<Long> inDocs = stockInLineMapper.selectList(new LambdaQueryWrapper<StockInLineDO>().select(StockInLineDO::getStockInId)
                .eq(StockInLineDO::getMaterialId, materialId)).stream().map(StockInLineDO::getStockInId).collect(Collectors.toSet());
        Set<Long> outDocs = stockOutLineMapper.selectList(new LambdaQueryWrapper<StockOutLineDO>().select(StockOutLineDO::getStockOutId)
                .eq(StockOutLineDO::getMaterialId, materialId)).stream().map(StockOutLineDO::getStockOutId).collect(Collectors.toSet());
        long openIn = inDocs.isEmpty() ? 0 : stockInMapper.selectCount(new LambdaQueryWrapper<StockInDO>().in(StockInDO::getId, inDocs).in(StockInDO::getStatus, OPEN));
        long openOut = outDocs.isEmpty() ? 0 : stockOutMapper.selectCount(new LambdaQueryWrapper<StockOutDO>().in(StockOutDO::getId, outDocs).in(StockOutDO::getStatus, OPEN));
        boolean used = !inDocs.isEmpty() || !outDocs.isEmpty()
                || txnMapper.selectCount(new LambdaQueryWrapper<StockTxnDO>().eq(StockTxnDO::getMaterialId, materialId)) > 0;
        int open = (int) (openIn + openOut);
        return new MaterialUsage(stock, open, used, stock.signum() != 0 || open > 0);
    }
}
