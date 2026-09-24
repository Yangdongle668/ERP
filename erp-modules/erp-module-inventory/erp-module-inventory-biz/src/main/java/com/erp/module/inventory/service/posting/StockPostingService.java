package com.erp.module.inventory.service.posting;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.erp.common.enums.EnableStatus;
import com.erp.common.exception.BizException;
import com.erp.common.util.Decimals;
import com.erp.framework.event.DomainEventPublisher;
import com.erp.framework.security.SecurityUtils;
import com.erp.module.engineering.api.material.MaterialApi;
import com.erp.module.engineering.api.material.MaterialDTO;
import com.erp.module.engineering.api.material.MaterialStatus;
import com.erp.module.engineering.api.material.Tracking;
import com.erp.module.inventory.api.InventoryErrorCodes;
import com.erp.module.inventory.api.stock.StockChangedEvent;
import com.erp.module.inventory.api.stock.StockDirection;
import com.erp.module.inventory.dal.dataobject.BatchDO;
import com.erp.module.inventory.dal.dataobject.LocationDO;
import com.erp.module.inventory.dal.dataobject.SerialDO;
import com.erp.module.inventory.dal.dataobject.StockDO;
import com.erp.module.inventory.dal.dataobject.StockTxnDO;
import com.erp.module.inventory.dal.dataobject.WarehouseDO;
import com.erp.module.inventory.dal.mapper.BatchMapper;
import com.erp.module.inventory.dal.mapper.LocationMapper;
import com.erp.module.inventory.dal.mapper.SerialMapper;
import com.erp.module.inventory.dal.mapper.SerialTxnMapper;
import com.erp.module.inventory.dal.mapper.StockMapper;
import com.erp.module.inventory.dal.mapper.StockTxnMapper;
import com.erp.module.inventory.dal.mapper.WarehouseMapper;
import com.erp.module.inventory.service.posting.PostingModels.PostCommand;
import com.erp.module.inventory.service.posting.PostingModels.PostLine;
import com.erp.module.inventory.service.posting.PostingModels.PostedLine;
import com.erp.module.system.api.param.ParamApi;
import com.erp.module.system.api.uom.UomApi;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 过账引擎（需求 08-02 第 4 节）：只由入库单、出库单、调拨单、盘点单的“确认”动作在调用方事务内调用。
 *
 * <p>并发：入库用行级 {@code qty = qty + ?}，出库用条件更新 {@code qty >= ?}，按库存维度排序后逐行处理，避免死锁；
 * 同一单据重复过账由流水幂等校验拒绝（R02），单据状态的乐观锁兜底并发确认。
 */
@Service
public class StockPostingService {

    public static final String PARAM_ALLOW_NEGATIVE = "inv.stock.allow-negative";
    public static final String SERIAL_IN_STOCK = "IN_STOCK";
    /** 期初入库流水的期间标记 */
    public static final String OPENING_PERIOD = "OPENING";
    public static final String SERIAL_OUT = "OUT";

    private final StockMapper stockMapper;
    private final StockTxnMapper txnMapper;
    private final BatchMapper batchMapper;
    private final SerialMapper serialMapper;
    private final SerialTxnMapper serialTxnMapper;
    private final WarehouseMapper warehouseMapper;
    private final LocationMapper locationMapper;
    private final MaterialApi materialApi;
    private final UomApi uomApi;
    private final ParamApi paramApi;
    private final PeriodGuard periodGuard;
    private final CountFreeze countFreeze;
    private final DomainEventPublisher eventPublisher;

    public StockPostingService(StockMapper stockMapper, StockTxnMapper txnMapper, BatchMapper batchMapper, SerialMapper serialMapper,
                               SerialTxnMapper serialTxnMapper, WarehouseMapper warehouseMapper, LocationMapper locationMapper,
                               MaterialApi materialApi, UomApi uomApi, ParamApi paramApi, PeriodGuard periodGuard, CountFreeze countFreeze,
                               DomainEventPublisher eventPublisher) {
        this.stockMapper = stockMapper;
        this.txnMapper = txnMapper;
        this.batchMapper = batchMapper;
        this.serialMapper = serialMapper;
        this.serialTxnMapper = serialTxnMapper;
        this.warehouseMapper = warehouseMapper;
        this.locationMapper = locationMapper;
        this.materialApi = materialApi;
        this.uomApi = uomApi;
        this.paramApi = paramApi;
        this.periodGuard = periodGuard;
        this.countFreeze = countFreeze;
        this.eventPublisher = eventPublisher;
    }

    // ==================== 过账 ====================

    @Transactional(rollbackFor = Exception.class)
    public List<PostedLine> post(PostCommand cmd) {
        periodGuard.checkPostable(cmd.bizDate(), cmd.opening());
        if (isPosted(cmd.docType(), cmd.docId())) throw BizException.of(InventoryErrorCodes.DUPLICATE_POSTING, cmd.docNo());
        if (cmd.lines().isEmpty()) throw new BizException(InventoryErrorCodes.NO_LINES);

        Map<Long, MaterialDTO> materials = materialApi.getMaterials(cmd.lines().stream().map(PostLine::materialId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(MaterialDTO::id, m -> m));
        Map<Long, WarehouseDO> warehouses = warehouseMapper.selectBatchIds(cmd.lines().stream().map(PostLine::warehouseId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(WarehouseDO::getId, w -> w));
        boolean globalNegative = paramApi.getBool(PARAM_ALLOW_NEGATIVE);
        for (PostLine l : cmd.lines()) validate(cmd, l, materials.get(l.materialId()), warehouses.get(l.warehouseId()));

        // 排序加锁：同一单据内按库存维度排序；同维度先入后出（调拨同仓换库位不受影响）
        List<PostLine> sorted = new ArrayList<>(cmd.lines());
        sorted.sort(Comparator.comparing(PostLine::materialId).thenComparing(PostLine::warehouseId)
                .thenComparing(l -> nz(l.locationId())).thenComparing(l -> batch(l.batchNo()))
                .thenComparing(l -> l.direction() == StockDirection.IN ? 0 : 1));
        Long operator = SecurityUtils.getLoginUserIdOrNull();
        List<PostedLine> result = new ArrayList<>();
        List<Runnable> serialOuts = new ArrayList<>();
        List<Runnable> serialIns = new ArrayList<>();
        for (PostLine l : sorted) {
            MaterialDTO m = materials.get(l.materialId());
            WarehouseDO w = warehouses.get(l.warehouseId());
            boolean negative = globalNegative && Boolean.TRUE.equals(w.getAllowNegative());
            StockTxnDO txn = l.direction() == StockDirection.IN ? postIn(cmd, l, m, operator) : postOut(cmd, l, m, w, negative, operator);
            result.add(new PostedLine(l.docLineId(), txn.getId(), txn.getBalanceQty()));
            if (l.serialNos() == null || l.serialNos().isEmpty()) continue;
            // 序列号在数量过账之后处理，且先出后入：调拨时序列号先离开调出仓再进入调入仓
            if (l.direction() == StockDirection.IN) {
                serialIns.add(() -> l.serialNos().forEach(sn -> serialIn(m, sn, batch(l.batchNo()), l, txn, cmd.docNo())));
            } else {
                serialOuts.add(() -> l.serialNos().forEach(sn -> serialOut(m, w, sn, txn, cmd.docNo(), cmd.customerId())));
            }
        }
        serialOuts.forEach(Runnable::run);
        serialIns.forEach(Runnable::run);
        eventPublisher.publish(new StockChangedEvent(cmd.lines().stream().map(PostLine::materialId).collect(Collectors.toSet())));
        return result;
    }

    /** 该单据是否已过账且未被冲销 */
    public boolean isPosted(String docType, Long docId) {
        long original = txnMapper.selectCount(new LambdaQueryWrapper<StockTxnDO>().eq(StockTxnDO::getDocType, docType)
                .eq(StockTxnDO::getDocId, docId).eq(StockTxnDO::getIsReversal, false));
        long reversed = txnMapper.selectCount(new LambdaQueryWrapper<StockTxnDO>().eq(StockTxnDO::getDocType, docType)
                .eq(StockTxnDO::getDocId, docId).eq(StockTxnDO::getIsReversal, true));
        return original > reversed;
    }

    private void validate(PostCommand cmd, PostLine l, MaterialDTO m, WarehouseDO w) {
        if (m == null) throw BizException.of(InventoryErrorCodes.MATERIAL_NOT_USABLE, String.valueOf(l.materialId()));
        // 入库要求物料启用；出库允许停用物料（清理库存）
        boolean usable = m.status() == MaterialStatus.ENABLED || (l.direction() == StockDirection.OUT && m.status() == MaterialStatus.DISABLED);
        if (!usable && !cmd.opening()) throw BizException.of(InventoryErrorCodes.MATERIAL_NOT_USABLE, m.code());
        if (w == null) throw new BizException(InventoryErrorCodes.WAREHOUSE_NOT_EXISTS);
        if (w.getStatus() != EnableStatus.ENABLED) throw BizException.of(InventoryErrorCodes.WAREHOUSE_DISABLED, w.getName());
        if (Boolean.TRUE.equals(w.getLocationEnabled())) {
            if (l.locationId() == null || l.locationId() == 0) throw BizException.of(InventoryErrorCodes.LOCATION_REQUIRED, w.getName());
            LocationDO loc = locationMapper.selectById(l.locationId());
            if (loc == null || !loc.getWarehouseId().equals(w.getId())) throw new BizException(InventoryErrorCodes.LOCATION_NOT_EXISTS);
        }
        if (l.qty() == null || l.qty().signum() <= 0) throw new BizException(InventoryErrorCodes.QTY_NOT_POSITIVE);
        int precision = precision(m.baseUom());
        if (l.qty().stripTrailingZeros().scale() > precision) throw BizException.of(InventoryErrorCodes.QTY_PRECISION, m.code(), precision);
        Tracking tracking = m.tracking() == null ? Tracking.NONE : m.tracking();
        if (tracking == Tracking.BATCH && !StringUtils.hasText(l.batchNo())) throw BizException.of(InventoryErrorCodes.BATCH_REQUIRED, m.code());
        if (tracking == Tracking.SERIAL) {
            int n = l.serialNos() == null ? 0 : l.serialNos().size();
            if (BigDecimal.valueOf(n).compareTo(l.qty()) != 0) {
                throw BizException.of(InventoryErrorCodes.SERIAL_COUNT_MISMATCH, m.code(), n, l.qty().stripTrailingZeros().toPlainString());
            }
        }
        if (!cmd.skipCountFreeze()) {
            String countNo = countFreeze.frozenBy(w.getId(), m.id());
            if (countNo != null) throw BizException.of(InventoryErrorCodes.COUNT_FROZEN, countNo);
        }
    }

    private int precision(String uom) {
        try {
            return uomApi.precision(uom);
        } catch (BizException e) {
            return Decimals.QTY_SCALE;
        }
    }

    private StockTxnDO postIn(PostCommand cmd, PostLine l, MaterialDTO m, Long operator) {
        String batchNo = batch(l.batchNo());
        if (!batchNo.isEmpty()) ensureBatch(cmd, l, m, batchNo);
        StockDO stock = getOrCreateStock(l.materialId(), l.warehouseId(), nz(l.locationId()), batchNo);
        stockMapper.increase(stock.getId(), l.qty(), cmd.bizDate());
        BigDecimal balance = stockMapper.selectById(stock.getId()).getQty();
        return writeTxn(cmd, l, StockDirection.IN, l.qty(), l.unitCost(), balance, operator, false, null);
    }

    private StockTxnDO postOut(PostCommand cmd, PostLine l, MaterialDTO m, WarehouseDO w, boolean negative, Long operator) {
        String batchNo = batch(l.batchNo());
        if (!batchNo.isEmpty() && !l.allowFrozen()) {
            BatchDO b = batchMapper.selectOne(new LambdaQueryWrapper<BatchDO>().eq(BatchDO::getMaterialId, m.id()).eq(BatchDO::getBatchNo, batchNo));
            if (b != null && Boolean.TRUE.equals(b.getFrozen())) throw BizException.of(InventoryErrorCodes.BATCH_FROZEN, batchNo);
            if (b != null && b.getExpireDate() != null && b.getExpireDate().isBefore(cmd.bizDate())) {
                throw BizException.of(InventoryErrorCodes.BATCH_EXPIRED, batchNo, b.getExpireDate());
            }
        }
        StockDO stock = stockMapper.selectByDim(l.materialId(), l.warehouseId(), nz(l.locationId()), batchNo);
        if (stock == null) {
            if (!negative) throw notEnough(m, w, batchNo, l.qty(), BigDecimal.ZERO);
            stock = getOrCreateStock(l.materialId(), l.warehouseId(), nz(l.locationId()), batchNo);
        }
        if (stockMapper.decrease(stock.getId(), l.qty(), cmd.bizDate(), negative) == 0) {
            throw notEnough(m, w, batchNo, l.qty(), stockMapper.selectById(stock.getId()).getQty());
        }
        BigDecimal balance = stockMapper.selectById(stock.getId()).getQty();
        return writeTxn(cmd, l, StockDirection.OUT, l.qty(), l.unitCost(), balance, operator, false, null);
    }

    private static BizException notEnough(MaterialDTO m, WarehouseDO w, String batchNo, BigDecimal need, BigDecimal have) {
        return BizException.of(InventoryErrorCodes.STOCK_NOT_ENOUGH, m.code(), w.getName(), batchNo.isEmpty() ? "" : "批次 " + batchNo + " ",
                need.stripTrailingZeros().toPlainString(), have.stripTrailingZeros().toPlainString());
    }

    /** 库存行不存在时插入（唯一键兜底并发插入） */
    private StockDO getOrCreateStock(Long materialId, Long warehouseId, Long locationId, String batchNo) {
        StockDO s = stockMapper.selectByDim(materialId, warehouseId, locationId, batchNo);
        if (s != null) return s;
        s = new StockDO();
        s.setMaterialId(materialId);
        s.setWarehouseId(warehouseId);
        s.setLocationId(locationId);
        s.setBatchNo(batchNo);
        s.setQty(BigDecimal.ZERO);
        try {
            stockMapper.insert(s);
            return s;
        } catch (DuplicateKeyException e) {
            StockDO again = stockMapper.selectByDim(materialId, warehouseId, locationId, batchNo);
            if (again == null) throw new BizException(InventoryErrorCodes.STOCK_BUSY);
            return again;
        }
    }

    /** 批次档案：不存在则创建；存在时校验（生产日期不同只提示，由单据层给出警告） */
    private void ensureBatch(PostCommand cmd, PostLine l, MaterialDTO m, String batchNo) {
        BatchDO b = batchMapper.selectOne(new LambdaQueryWrapper<BatchDO>().eq(BatchDO::getMaterialId, m.id()).eq(BatchDO::getBatchNo, batchNo));
        if (b != null) return;
        b = new BatchDO();
        b.setMaterialId(m.id());
        b.setBatchNo(batchNo);
        PostingModels.BatchAttrs a = l.batchAttrs();
        if (a != null) {
            b.setSupplierId(a.supplierId());
            b.setSupplierBatchNo(a.supplierBatchNo());
            b.setProductionDate(a.productionDate());
            b.setExpireDate(a.expireDate());
        }
        b.setFirstInDate(cmd.bizDate());
        b.setFirstInAt(LocalDateTime.now());
        b.setSourceType(cmd.sourceType() != null ? cmd.sourceType() : cmd.bizType());
        b.setSourceNo(cmd.sourceNo() != null ? cmd.sourceNo() : cmd.docNo());
        b.setIsConcession(false);
        b.setFrozen(false);
        try {
            batchMapper.insert(b);
        } catch (DuplicateKeyException ignored) {
            // 并发创建同一批次：已存在即可
        }
    }

    private StockTxnDO writeTxn(PostCommand cmd, PostLine l, StockDirection dir, BigDecimal qty, BigDecimal unitCost, BigDecimal balance,
                                Long operator, boolean reversal, Long reversedId) {
        StockTxnDO t = new StockTxnDO();
        t.setId(IdWorker.getId());
        t.setTxnNo(String.valueOf(t.getId()));
        t.setDocType(cmd.docType());
        t.setBizType(cmd.bizType());
        t.setDocId(cmd.docId());
        t.setDocLineId(l.docLineId());
        t.setDocNo(cmd.docNo());
        t.setSourceType(cmd.sourceType());
        t.setSourceId(cmd.sourceId());
        t.setSourceLineId(l.sourceLineId());
        t.setSourceNo(cmd.sourceNo());
        t.setDirection(dir.name());
        t.setMaterialId(l.materialId());
        t.setWarehouseId(l.warehouseId());
        t.setLocationId(nz(l.locationId()));
        t.setBatchNo(batch(l.batchNo()));
        t.setQty(qty);
        t.setUnitCost(unitCost);
        t.setAmount(unitCost == null ? null : Decimals.multiplyAmount(qty, unitCost));
        t.setBalanceQty(balance);
        t.setBizDate(cmd.bizDate());
        t.setPeriod(cmd.opening() ? OPENING_PERIOD : PeriodGuard.periodOf(cmd.bizDate()));
        t.setIsReversal(reversal);
        t.setReversedTxnId(reversedId);
        t.setOperatorId(operator);
        txnMapper.insert(t);
        return t;
    }

    // ==================== 序列号 ====================

    private void serialIn(MaterialDTO m, String sn, String batchNo, PostLine l, StockTxnDO txn, String docNo) {
        SerialDO s = serialMapper.selectOne(new LambdaQueryWrapper<SerialDO>().eq(SerialDO::getMaterialId, m.id()).eq(SerialDO::getSerialNo, sn));
        if (s != null && SERIAL_IN_STOCK.equals(s.getSerialStatus())) throw BizException.of(InventoryErrorCodes.SERIAL_IN_STOCK, sn);
        boolean fresh = s == null;
        if (fresh) {
            s = new SerialDO();
            s.setMaterialId(m.id());
            s.setSerialNo(sn);
        }
        s.setBatchNo(batchNo.isEmpty() ? null : batchNo);
        s.setSerialStatus(SERIAL_IN_STOCK);
        s.setWarehouseId(l.warehouseId());
        s.setLocationId(l.locationId());
        s.setLastTxnId(txn.getId());
        if (fresh) serialMapper.insert(s);
        else serialMapper.updateByIdOrFail(s);
        serialTxnMapper.insert(IdWorker.getId(), s.getId(), txn.getId(), StockDirection.IN.name(), docNo);
    }

    private void serialOut(MaterialDTO m, WarehouseDO w, String sn, StockTxnDO txn, String docNo, Long customerId) {
        SerialDO s = serialMapper.selectOne(new LambdaQueryWrapper<SerialDO>().eq(SerialDO::getMaterialId, m.id()).eq(SerialDO::getSerialNo, sn));
        if (s == null || !SERIAL_IN_STOCK.equals(s.getSerialStatus()) || !w.getId().equals(s.getWarehouseId())) {
            throw BizException.of(InventoryErrorCodes.SERIAL_NOT_IN_WAREHOUSE, sn, w.getName());
        }
        s.setSerialStatus(SERIAL_OUT);
        s.setWarehouseId(null);
        s.setLocationId(null);
        s.setLastTxnId(txn.getId());
        if (customerId != null) s.setCustomerId(customerId);
        serialMapper.updateByIdOrFail(s);
        serialTxnMapper.insert(IdWorker.getId(), s.getId(), txn.getId(), StockDirection.OUT.name(), docNo);
    }

    // ==================== 冲销 ====================

    /**
     * 冲销单据此前的过账（反确认）：每条原流水生成方向相反的流水。冲销入库时库存必须足够（R06）。
     *
     * @param serialsByLine 单据行上的序列号（冲销时恢复状态）
     */
    @Transactional(rollbackFor = Exception.class)
    public void reverse(String docType, Long docId, String docNo, Map<Long, List<String>> serialsByLine) {
        List<StockTxnDO> all = txnMapper.selectList(new LambdaQueryWrapper<StockTxnDO>().eq(StockTxnDO::getDocType, docType)
                .eq(StockTxnDO::getDocId, docId).orderByAsc(StockTxnDO::getId));
        Set<Long> reversed = all.stream().filter(t -> Boolean.TRUE.equals(t.getIsReversal())).map(StockTxnDO::getReversedTxnId)
                .collect(Collectors.toSet());
        List<StockTxnDO> originals = all.stream().filter(t -> !Boolean.TRUE.equals(t.getIsReversal()) && !reversed.contains(t.getId())).toList();
        if (originals.isEmpty()) return;
        for (StockTxnDO t : originals) periodGuard.checkNotClosed(t.getBizDate());
        Map<Long, MaterialDTO> materials = materialApi.getMaterials(originals.stream().map(StockTxnDO::getMaterialId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(MaterialDTO::id, m -> m));
        Long operator = SecurityUtils.getLoginUserIdOrNull();
        // 先冲回出库（增加库存），再冲回入库，避免调拨同维度时误报库存不足
        List<StockTxnDO> ordered = new ArrayList<>(originals);
        ordered.sort(Comparator.comparing((StockTxnDO t) -> "OUT".equals(t.getDirection()) ? 0 : 1).thenComparing(StockTxnDO::getMaterialId));
        for (StockTxnDO t : ordered) {
            StockDO stock = stockMapper.selectByDim(t.getMaterialId(), t.getWarehouseId(), t.getLocationId(), t.getBatchNo());
            MaterialDTO m = materials.get(t.getMaterialId());
            String code = m == null ? String.valueOf(t.getMaterialId()) : m.code();
            boolean wasIn = "IN".equals(t.getDirection());
            BigDecimal delta = wasIn ? t.getQty().negate() : t.getQty();
            if (stock == null) stock = getOrCreateStock(t.getMaterialId(), t.getWarehouseId(), t.getLocationId(), t.getBatchNo());
            if (stockMapper.adjust(stock.getId(), delta, false) == 0) {
                throw BizException.of(InventoryErrorCodes.REVERSE_STOCK_USED, code, stockMapper.selectById(stock.getId()).getQty().stripTrailingZeros().toPlainString(),
                        t.getQty().stripTrailingZeros().toPlainString());
            }
            BigDecimal balance = stockMapper.selectById(stock.getId()).getQty();
            PostCommand cmd = new PostCommand(t.getDocType(), t.getBizType(), t.getDocId(), docNo, t.getSourceType(), t.getSourceId(), t.getSourceNo(),
                    t.getBizDate(), List.of(), true, OPENING_PERIOD.equals(t.getPeriod()), null);
            PostLine line = new PostLine(t.getDocLineId(), t.getSourceLineId(), wasIn ? StockDirection.OUT : StockDirection.IN, t.getMaterialId(),
                    t.getWarehouseId(), t.getLocationId(), t.getBatchNo(), t.getQty(), null, null, true, null);
            StockTxnDO rev = writeTxn(cmd, line, wasIn ? StockDirection.OUT : StockDirection.IN, t.getQty(), t.getUnitCost(), balance, operator, true, t.getId());
            List<String> sns = serialsByLine == null ? null : serialsByLine.get(t.getDocLineId());
            if (sns != null) revertSerials(t, sns, rev);
        }
        eventPublisher.publish(new StockChangedEvent(originals.stream().map(StockTxnDO::getMaterialId).collect(Collectors.toSet())));
    }

    private void revertSerials(StockTxnDO original, List<String> serialNos, StockTxnDO rev) {
        boolean wasIn = "IN".equals(original.getDirection());
        for (String sn : new LinkedHashSet<>(serialNos)) {
            SerialDO s = serialMapper.selectOne(new LambdaQueryWrapper<SerialDO>().eq(SerialDO::getMaterialId, original.getMaterialId()).eq(SerialDO::getSerialNo, sn));
            if (s == null) continue;
            if (wasIn && Objects.equals(s.getWarehouseId(), original.getWarehouseId())) {
                s.setSerialStatus(SERIAL_OUT);
                s.setWarehouseId(null);
                s.setLocationId(null);
            } else if (!wasIn) {
                s.setSerialStatus(SERIAL_IN_STOCK);
                s.setWarehouseId(original.getWarehouseId());
                s.setLocationId(original.getLocationId() == 0 ? null : original.getLocationId());
            }
            s.setLastTxnId(rev.getId());
            serialMapper.updateByIdOrFail(s);
            serialTxnMapper.insert(IdWorker.getId(), s.getId(), rev.getId(), rev.getDirection(), rev.getDocNo());
        }
    }

    // ==================== 工具 ====================

    static Long nz(Long v) {
        return v == null ? 0L : v;
    }

    static String batch(String b) {
        return StringUtils.hasText(b) ? b.trim() : "";
    }

    /** 当前库存行（给单据层查可用批次等） */
    public Map<String, BigDecimal> qtyByBatch(Long materialId, Long warehouseId) {
        Map<String, BigDecimal> map = new HashMap<>();
        stockMapper.selectList(new LambdaQueryWrapper<StockDO>().eq(StockDO::getMaterialId, materialId).eq(StockDO::getWarehouseId, warehouseId))
                .forEach(s -> map.merge(s.getBatchNo(), s.getQty(), BigDecimal::add));
        return map;
    }

    public static LocalDate today() {
        return LocalDate.now();
    }
}
