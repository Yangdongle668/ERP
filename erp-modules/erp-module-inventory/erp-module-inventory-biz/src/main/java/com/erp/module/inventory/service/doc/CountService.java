package com.erp.module.inventory.service.doc;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.common.enums.DocStatus;
import com.erp.common.exception.BizException;
import com.erp.common.result.PageResult;
import com.erp.common.util.Decimals;
import com.erp.framework.excel.ImportResult;
import com.erp.framework.excel.ImportRow;
import com.erp.framework.security.SecurityUtils;
import com.erp.module.engineering.api.category.MaterialCategoryApi;
import com.erp.module.engineering.api.material.MaterialApi;
import com.erp.module.engineering.api.material.MaterialDTO;
import com.erp.module.engineering.api.material.Tracking;
import com.erp.module.inventory.api.InventoryErrorCodes;
import com.erp.module.inventory.api.doc.StockInType;
import com.erp.module.inventory.api.doc.StockOutType;
import com.erp.module.inventory.config.InventoryModuleConfig;
import com.erp.module.inventory.controller.vo.CountVOs.AddLine;
import com.erp.module.inventory.controller.vo.CountVOs.CountDetail;
import com.erp.module.inventory.controller.vo.CountVOs.CountLineRow;
import com.erp.module.inventory.controller.vo.CountVOs.CountQuery;
import com.erp.module.inventory.controller.vo.CountVOs.CountRow;
import com.erp.module.inventory.controller.vo.CountVOs.CountSave;
import com.erp.module.inventory.controller.vo.CountVOs.LineInput;
import com.erp.module.inventory.controller.vo.CountVOs.LineQuery;
import com.erp.module.inventory.controller.vo.CountVOs.PendingDocs;
import com.erp.module.inventory.controller.vo.CountVOs.RelatedDoc;
import com.erp.module.inventory.dal.dataobject.CountDO;
import com.erp.module.inventory.dal.dataobject.CountLineDO;
import com.erp.module.inventory.dal.dataobject.LocationDO;
import com.erp.module.inventory.dal.dataobject.StockDO;
import com.erp.module.inventory.dal.dataobject.StockInDO;
import com.erp.module.inventory.dal.dataobject.StockInLineDO;
import com.erp.module.inventory.dal.dataobject.StockOutDO;
import com.erp.module.inventory.dal.dataobject.StockOutLineDO;
import com.erp.module.inventory.dal.dataobject.TransferDO;
import com.erp.module.inventory.dal.dataobject.WarehouseDO;
import com.erp.module.inventory.dal.mapper.CountLineMapper;
import com.erp.module.inventory.dal.mapper.CountMapper;
import com.erp.module.inventory.dal.mapper.StockInMapper;
import com.erp.module.inventory.dal.mapper.StockMapper;
import com.erp.module.inventory.dal.mapper.StockOutMapper;
import com.erp.module.inventory.dal.mapper.TransferMapper;
import com.erp.module.inventory.service.posting.CountFreeze;
import com.erp.module.system.api.param.ParamApi;
import com.erp.module.system.api.user.UserDTO;
import com.erp.module.system.api.workflow.ApprovalCompletedEvent;
import com.erp.module.system.api.workflow.StartResult;
import com.erp.module.system.api.workflow.WorkflowApi;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 盘点（需求 08-06）：新建范围 → 生成盘点表（快照 + 冻结）→ 录入实盘/复盘 → 提交 → 审批 → 生成盘盈入库、盘亏出库并自动确认。
 */
@Service("invCountService")
public class CountService {

    public static final String BIZ_TYPE = "INV_COUNT";
    public static final String APPROVAL_TYPE = "INV_COUNT";
    static final String PARAM_PCT = "inv.count.recount-threshold-pct";
    static final String PARAM_AMOUNT = "inv.count.recount-threshold-amount";
    static final String FULL = "FULL";
    static final String PARTIAL = "PARTIAL";

    private final CountMapper docMapper;
    private final CountLineMapper lineMapper;
    private final StockMapper stockMapper;
    private final StockInMapper stockInMapper;
    private final StockOutMapper stockOutMapper;
    private final TransferMapper transferMapper;
    private final StockInService stockInService;
    private final StockOutService stockOutService;
    private final DocSupport support;
    private final MaterialApi materialApi;
    private final MaterialCategoryApi categoryApi;
    private final ParamApi paramApi;
    private final WorkflowApi workflowApi;

    public CountService(CountMapper docMapper, CountLineMapper lineMapper, StockMapper stockMapper, StockInMapper stockInMapper,
                        StockOutMapper stockOutMapper, TransferMapper transferMapper, StockInService stockInService, StockOutService stockOutService,
                        DocSupport support, MaterialApi materialApi, MaterialCategoryApi categoryApi, ParamApi paramApi, WorkflowApi workflowApi) {
        this.docMapper = docMapper;
        this.lineMapper = lineMapper;
        this.stockMapper = stockMapper;
        this.stockInMapper = stockInMapper;
        this.stockOutMapper = stockOutMapper;
        this.transferMapper = transferMapper;
        this.stockInService = stockInService;
        this.stockOutService = stockOutService;
        this.support = support;
        this.materialApi = materialApi;
        this.categoryApi = categoryApi;
        this.paramApi = paramApi;
        this.workflowApi = workflowApi;
    }

    // ==================== 新建 ====================

    @Transactional(rollbackFor = Exception.class)
    public Long create(CountSave req) {
        CountDO d = new CountDO();
        d.setDocNo(support.nextNo(InventoryModuleConfig.CODE_COUNT));
        d.setStatus(DocStatus.DRAFT);
        d.setCountStatus(CountStatus.DRAFT.name());
        d.setOwnerId(support.currentUser());
        StockInService.LoginOrg.fill(d);
        fill(d, req);
        docMapper.insert(d);
        return d.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, CountSave req) {
        CountDO d = getOrThrow(id);
        checkAccess(d);
        requireStatus(d, CountStatus.DRAFT);
        if (req.version() != null) d.setVersion(req.version());
        fill(d, req);
        docMapper.updateByIdOrFail(d);
    }

    private void fill(CountDO d, CountSave req) {
        if (!FULL.equals(req.countType()) && !PARTIAL.equals(req.countType())) throw BizException.of(InventoryErrorCodes.LINE_FIELD_REQUIRED, 1, "盘点类型");
        for (Long wid : req.warehouseIds()) {
            support.warehouse(wid);
            support.warehouses().checkAccess(wid);
        }
        d.setCountType(req.countType());
        d.setWarehouseIds(csv(req.warehouseIds()));
        boolean partial = PARTIAL.equals(req.countType());
        d.setCategoryIds(partial ? csv(req.categoryIds()) : null);
        d.setLocationIds(partial ? csv(req.locationIds()) : null);
        d.setMaterialIds(partial ? csv(req.materialIds()) : null);
        d.setIncludeZero(Boolean.TRUE.equals(req.includeZero()));
        d.setBlindCount(req.blindCount() == null || req.blindCount());
        d.setDocDate(req.docDate() != null ? req.docDate() : LocalDate.now());
        DocSupport.checkDate(d.getDocDate(), null, "盘点");
        d.setRemark(StringUtils.hasText(req.remark()) ? req.remark().trim() : null);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        CountDO d = getOrThrow(id);
        checkAccess(d);
        requireStatus(d, CountStatus.DRAFT);
        lineMapper.deleteByDoc(id);
        docMapper.deleteById(id);
    }

    // ==================== 生成盘点表 ====================

    /** R01：范围内尚未确认的出入库单、调拨单（提示，不阻止） */
    public PendingDocs pendingDocs(Long id) {
        CountDO d = getOrThrow(id);
        List<Long> whs = ids(d.getWarehouseIds());
        List<String> nos = new ArrayList<>();
        List<DocStatus> open = List.of(DocStatus.DRAFT, DocStatus.PENDING_APPROVAL, DocStatus.APPROVED);
        stockInMapper.selectList(new LambdaQueryWrapper<StockInDO>().in(StockInDO::getWarehouseId, whs).in(StockInDO::getStatus, open).last("LIMIT 20"))
                .forEach(x -> nos.add(x.getDocNo()));
        stockOutMapper.selectList(new LambdaQueryWrapper<StockOutDO>().in(StockOutDO::getWarehouseId, whs).in(StockOutDO::getStatus, open).last("LIMIT 20"))
                .forEach(x -> nos.add(x.getDocNo()));
        transferMapper.selectList(new LambdaQueryWrapper<TransferDO>().eq(TransferDO::getStatus, DocStatus.DRAFT)
                        .and(w -> w.in(TransferDO::getFromWarehouseId, whs).or().in(TransferDO::getToWarehouseId, whs)).last("LIMIT 20"))
                .forEach(x -> nos.add(x.getDocNo()));
        return new PendingDocs(nos);
    }

    /** 快照账面数量并冻结范围（R01、R02） */
    @Transactional(rollbackFor = Exception.class)
    public int generate(Long id) {
        CountDO d = getOrThrow(id);
        checkAccess(d);
        requireStatus(d, CountStatus.DRAFT);
        List<Long> whs = ids(d.getWarehouseIds());
        LambdaQueryWrapper<StockDO> w = new LambdaQueryWrapper<StockDO>().in(StockDO::getWarehouseId, whs);
        if (!Boolean.TRUE.equals(d.getIncludeZero())) w.ne(StockDO::getQty, BigDecimal.ZERO);
        boolean partial = PARTIAL.equals(d.getCountType());
        if (partial && !ids(d.getLocationIds()).isEmpty()) w.in(StockDO::getLocationId, ids(d.getLocationIds()));
        if (partial && !ids(d.getMaterialIds()).isEmpty()) w.in(StockDO::getMaterialId, ids(d.getMaterialIds()));
        List<StockDO> stocks = stockMapper.selectList(w);
        Map<Long, MaterialDTO> materials = support.materials(stocks.stream().map(StockDO::getMaterialId).toList());
        if (partial && !ids(d.getCategoryIds()).isEmpty()) {
            Set<Long> cats = new HashSet<>();
            for (Long c : ids(d.getCategoryIds())) {
                cats.add(c);
                cats.addAll(categoryApi.getDescendantIds(c));
            }
            stocks = stocks.stream().filter(s -> materials.containsKey(s.getMaterialId()) && cats.contains(materials.get(s.getMaterialId()).categoryId())).toList();
        }
        if (stocks.isEmpty()) throw new BizException(InventoryErrorCodes.COUNT_EMPTY);
        checkOverlap(d, stocks, materials);
        Map<Long, LocationDO> locs = support.warehouses().locationsByIds(stocks.stream().map(StockDO::getLocationId).toList());
        List<StockDO> sorted = new ArrayList<>(stocks);
        sorted.sort(Comparator.comparing(StockDO::getWarehouseId)
                .thenComparing(s -> locs.containsKey(s.getLocationId()) ? locs.get(s.getLocationId()).getCode() : "")
                .thenComparing(s -> materials.containsKey(s.getMaterialId()) ? materials.get(s.getMaterialId()).code() : "")
                .thenComparing(StockDO::getBatchNo));
        Map<Long, BigDecimal> costs = new HashMap<>();
        int no = 0;
        for (StockDO s : sorted) {
            CountLineDO l = new CountLineDO();
            l.setCountId(d.getId());
            l.setLineNo(++no);
            l.setWarehouseId(s.getWarehouseId());
            l.setLocationId(s.getLocationId());
            l.setMaterialId(s.getMaterialId());
            l.setBatchNo(s.getBatchNo());
            l.setBookQty(s.getQty());
            l.setRefCost(costs.computeIfAbsent(s.getMaterialId(), support::refCost));
            l.setNeedRecount(false);
            l.setIsAdded(false);
            lineMapper.insert(l);
        }
        d.setSnapshotAt(LocalDateTime.now());
        fire(d, CountStatus.Action.GENERATE, null);
        return no;
    }

    /** 同一库存维度（仓库 + 物料）不能同时在两张盘点中 */
    private void checkOverlap(CountDO d, List<StockDO> stocks, Map<Long, MaterialDTO> materials) {
        List<CountDO> active = docMapper.selectList(new LambdaQueryWrapper<CountDO>().in(CountDO::getCountStatus, CountFreeze.ACTIVE)
                .ne(CountDO::getId, d.getId()));
        if (active.isEmpty()) return;
        Set<Long> myWhs = new HashSet<>(ids(d.getWarehouseIds()));
        for (CountDO c : active) {
            if (FULL.equals(c.getCountType()) && ids(c.getWarehouseIds()).stream().anyMatch(myWhs::contains)) {
                StockDO hit = stocks.stream().filter(s -> ids(c.getWarehouseIds()).contains(s.getWarehouseId())).findFirst().orElse(stocks.get(0));
                throw BizException.of(InventoryErrorCodes.COUNT_ALREADY_IN, code(materials, hit.getMaterialId()), c.getDocNo());
            }
        }
        Set<String> mine = stocks.stream().map(s -> s.getWarehouseId() + "|" + s.getMaterialId()).collect(Collectors.toSet());
        Map<Long, CountDO> byId = active.stream().collect(Collectors.toMap(CountDO::getId, c -> c));
        for (CountLineDO l : lineMapper.selectList(new LambdaQueryWrapper<CountLineDO>().in(CountLineDO::getCountId, byId.keySet())
                .in(CountLineDO::getWarehouseId, myWhs))) {
            if (mine.contains(l.getWarehouseId() + "|" + l.getMaterialId())) {
                throw BizException.of(InventoryErrorCodes.COUNT_ALREADY_IN, code(materials, l.getMaterialId()), byId.get(l.getCountId()).getDocNo());
            }
        }
    }

    // ==================== 录入 ====================

    @Transactional(rollbackFor = Exception.class)
    public void input(Long id, List<LineInput> inputs) {
        CountDO d = getOrThrow(id);
        checkAccess(d);
        requireStatus(d, CountStatus.COUNTING);
        if (inputs == null || inputs.isEmpty()) return;
        Map<Long, CountLineDO> lines = lineMapper.selectByDoc(id).stream().collect(Collectors.toMap(CountLineDO::getId, l -> l));
        Long me = SecurityUtils.getLoginUserIdOrNull();
        for (LineInput in : inputs) {
            CountLineDO l = lines.get(in.id());
            if (l == null) continue;
            boolean changed = !Objects.equals(scaled(l.getCountQty()), scaled(in.countQty())) || !Objects.equals(scaled(l.getRecountQty()), scaled(in.recountQty()));
            l.setCountQty(in.countQty());
            l.setRecountQty(Boolean.TRUE.equals(l.getNeedRecount()) ? in.recountQty() : null);
            l.setReason(StringUtils.hasText(in.reason()) ? in.reason() : null);
            l.setRemark(StringUtils.hasText(in.remark()) ? in.remark().trim() : null);
            if (changed) {
                l.setCounterId(me);
                l.setCountedAt(LocalDateTime.now());
            }
            compute(l);
            lineMapper.updateByIdOrFail(l);
        }
    }

    /** 最终数量、差异、差异金额；录入实盘后按阈值判定是否需要复盘（R04） */
    void compute(CountLineDO l) {
        if (l.getCountQty() == null) {
            l.setFinalQty(null);
            l.setDiffQty(null);
            l.setDiffAmount(null);
            l.setNeedRecount(false);
            l.setRecountQty(null);
            return;
        }
        BigDecimal countDiff = l.getCountQty().subtract(l.getBookQty());
        BigDecimal countAmount = l.getRefCost() == null ? BigDecimal.ZERO : Decimals.multiplyAmount(countDiff, l.getRefCost());
        boolean need = false;
        if (countDiff.signum() != 0 && !Boolean.TRUE.equals(l.getIsAdded())) {
            BigDecimal pct = l.getBookQty().signum() == 0 ? new BigDecimal("100")
                    : countDiff.abs().multiply(new BigDecimal("100")).divide(l.getBookQty().abs(), 4, RoundingMode.HALF_UP);
            BigDecimal pctLimit = paramApi.getDecimal(PARAM_PCT);
            BigDecimal amountLimit = paramApi.getDecimal(PARAM_AMOUNT);
            need = (pctLimit != null && pct.compareTo(pctLimit) >= 0) || (amountLimit != null && countAmount.abs().compareTo(amountLimit) >= 0);
        }
        l.setNeedRecount(need);
        if (!need) l.setRecountQty(null);
        BigDecimal fin = l.getRecountQty() != null ? l.getRecountQty() : l.getCountQty();
        l.setFinalQty(fin);
        l.setDiffQty(fin.subtract(l.getBookQty()));
        l.setDiffAmount(l.getRefCost() == null ? null : Decimals.multiplyAmount(l.getDiffQty(), l.getRefCost()));
    }

    private static BigDecimal scaled(BigDecimal v) {
        return v == null ? null : v.stripTrailingZeros();
    }

    /** [新增盘点外物料]：账面没有的实物（R06：批次物料必须填写批次） */
    @Transactional(rollbackFor = Exception.class)
    public Long addLine(Long id, AddLine req) {
        CountDO d = getOrThrow(id);
        checkAccess(d);
        requireStatus(d, CountStatus.COUNTING);
        if (!ids(d.getWarehouseIds()).contains(req.warehouseId())) throw new BizException(InventoryErrorCodes.WAREHOUSE_NOT_EXISTS);
        MaterialDTO m = materialApi.validateUsable(req.materialId());
        if (m.tracking() == Tracking.BATCH && !StringUtils.hasText(req.batchNo())) throw new BizException(InventoryErrorCodes.COUNT_BATCH_REQUIRED);
        WarehouseDO w = support.warehouse(req.warehouseId());
        if (Boolean.TRUE.equals(w.getLocationEnabled()) && req.locationId() == null) throw BizException.of(InventoryErrorCodes.LOCATION_REQUIRED, w.getName());
        List<CountLineDO> lines = lineMapper.selectByDoc(id);
        String batch = StringUtils.hasText(req.batchNo()) ? req.batchNo().trim() : "";
        long loc = req.locationId() == null ? 0L : req.locationId();
        for (CountLineDO l : lines) {
            if (l.getWarehouseId().equals(req.warehouseId()) && l.getMaterialId().equals(m.id()) && l.getLocationId() == loc && l.getBatchNo().equals(batch)) {
                throw BizException.of(InventoryErrorCodes.COUNT_ALREADY_IN, m.code(), d.getDocNo());
            }
        }
        // 账面上存在该维度（例如数量为 0 未纳入盘点表）时以当前账面为准
        StockDO s = stockMapper.selectByDim(m.id(), req.warehouseId(), loc, batch);
        CountLineDO l = new CountLineDO();
        l.setCountId(id);
        l.setLineNo(lines.stream().mapToInt(CountLineDO::getLineNo).max().orElse(0) + 1);
        l.setWarehouseId(req.warehouseId());
        l.setLocationId(loc);
        l.setMaterialId(m.id());
        l.setBatchNo(batch);
        l.setBookQty(s == null ? BigDecimal.ZERO : s.getQty());
        l.setRefCost(support.refCost(m.id()));
        l.setCountQty(req.countQty());
        l.setIsAdded(true);
        l.setNeedRecount(false);
        l.setReason(req.reason());
        l.setRemark(req.remark());
        l.setCounterId(SecurityUtils.getLoginUserIdOrNull());
        l.setCountedAt(LocalDateTime.now());
        compute(l);
        lineMapper.insert(l);
        return l.getId();
    }

    // ==================== 提交与审核 ====================

    @Transactional(rollbackFor = Exception.class)
    public String submit(Long id) {
        CountDO d = getOrThrow(id);
        checkAccess(d);
        requireStatus(d, CountStatus.COUNTING);
        List<CountLineDO> lines = lineMapper.selectByDoc(id);
        long uninput = lines.stream().filter(l -> l.getCountQty() == null).count();
        if (uninput > 0) throw BizException.of(InventoryErrorCodes.COUNT_LINES_UNINPUT, uninput);
        long recount = lines.stream().filter(l -> Boolean.TRUE.equals(l.getNeedRecount()) && l.getRecountQty() == null).count();
        if (recount > 0) throw BizException.of(InventoryErrorCodes.COUNT_RECOUNT_UNINPUT, recount);
        for (CountLineDO l : lines) {
            if (l.getDiffQty() != null && l.getDiffQty().signum() != 0 && !StringUtils.hasText(l.getReason())) {
                throw BizException.of(InventoryErrorCodes.COUNT_REASON_REQUIRED, l.getLineNo());
            }
        }
        fire(d, CountStatus.Action.SUBMIT, null);
        BigDecimal amount = lines.stream().map(CountLineDO::getDiffAmount).filter(Objects::nonNull).map(BigDecimal::abs).reduce(BigDecimal.ZERO, BigDecimal::add);
        StartResult r = workflowApi.start(APPROVAL_TYPE, d.getId(), d.getDocNo(), "盘点 " + d.getDocNo(), Map.of("amountBase", amount), Map.of(),
                SecurityUtils.getLoginUserIdOrNull());
        if (r.isStarted()) {
            DocStatus old = d.getStatus();
            d.setStatus(InvDocAction.MACHINE.fire(old, InvDocAction.SUBMIT));
            docMapper.updateByIdOrFail(d);
        }
        return d.getCountStatus();
    }

    /** 审核（无审批流时由有 inv:count:approve 权限的人执行） */
    @Transactional(rollbackFor = Exception.class)
    public void approve(Long id) {
        CountDO d = getOrThrow(id);
        checkAccess(d);
        requireStatus(d, CountStatus.SUBMITTED);
        if (d.getStatus() == DocStatus.PENDING_APPROVAL) throw BizException.of(InventoryErrorCodes.COUNT_STATUS, "审批中");
        doApprove(d);
    }

    /** 驳回（无审批流时）：回到盘点中 */
    @Transactional(rollbackFor = Exception.class)
    public void reject(Long id, String reason) {
        CountDO d = getOrThrow(id);
        checkAccess(d);
        requireStatus(d, CountStatus.SUBMITTED);
        if (d.getStatus() == DocStatus.PENDING_APPROVAL) throw BizException.of(InventoryErrorCodes.COUNT_STATUS, "审批中");
        fire(d, CountStatus.Action.REJECT, reason);
    }

    @EventListener
    public void onApproval(ApprovalCompletedEvent e) {
        if (!APPROVAL_TYPE.equals(e.getBizType())) return;
        CountDO d = getOrThrow(e.getBizId());
        if (d.getStatus() != DocStatus.PENDING_APPROVAL) return;
        switch (e.getResult()) {
            case APPROVED -> {
                d.setStatus(InvDocAction.MACHINE.fire(d.getStatus(), InvDocAction.APPROVE));
                doApprove(d);
            }
            case WITHDRAWN -> {
                d.setStatus(InvDocAction.MACHINE.fire(d.getStatus(), InvDocAction.WITHDRAW));
                fire(d, CountStatus.Action.REJECT, "撤回");
            }
            default -> {
                d.setStatus(InvDocAction.MACHINE.fire(d.getStatus(), InvDocAction.REJECT));
                fire(d, CountStatus.Action.REJECT, e.getComment());
            }
        }
    }

    /** R05：按仓库生成盘盈入库单、盘亏出库单，日期 = 盘点单日期，自动确认；成功后解除冻结 */
    private void doApprove(CountDO d) {
        List<CountLineDO> lines = lineMapper.selectByDoc(d.getId());
        Map<Long, MaterialDTO> materials = support.materials(lines.stream().map(CountLineDO::getMaterialId).toList());
        Map<Long, List<CountLineDO>> gains = new LinkedHashMap<>();
        Map<Long, List<CountLineDO>> losses = new LinkedHashMap<>();
        for (CountLineDO l : lines) {
            if (l.getDiffQty() == null || l.getDiffQty().signum() == 0) continue;
            (l.getDiffQty().signum() > 0 ? gains : losses).computeIfAbsent(l.getWarehouseId(), k -> new ArrayList<>()).add(l);
        }
        Long firstGain = null;
        Long firstLoss = null;
        for (Map.Entry<Long, List<CountLineDO>> e : gains.entrySet()) {
            List<StockInLineDO> ins = e.getValue().stream().map(l -> {
                MaterialDTO m = materials.get(l.getMaterialId());
                StockInLineDO x = new StockInLineDO();
                x.setMaterialId(l.getMaterialId());
                x.setUom(m.baseUom());
                x.setQty(l.getDiffQty());
                x.setBaseQty(l.getDiffQty());
                x.setLocationId(l.getLocationId() == 0 ? null : l.getLocationId());
                x.setBatchNo(l.getBatchNo().isEmpty() ? null : l.getBatchNo());
                x.setUnitCost(l.getRefCost());
                x.setAmount(DocSupport.amount(l.getDiffQty(), l.getRefCost()));
                x.setRemark(l.getReason());
                return x;
            }).toList();
            Long inId = stockInService.createSystem(StockInType.COUNT_GAIN, e.getKey(), d.getDocDate(), BIZ_TYPE, d.getId(), d.getDocNo(), "盘盈", ins);
            stockInService.confirm(inId, null, null, false);
            if (firstGain == null) firstGain = inId;
        }
        for (Map.Entry<Long, List<CountLineDO>> e : losses.entrySet()) {
            List<StockOutLineDO> outs = e.getValue().stream().map(l -> {
                MaterialDTO m = materials.get(l.getMaterialId());
                BigDecimal q = l.getDiffQty().negate();
                StockOutLineDO x = new StockOutLineDO();
                x.setMaterialId(l.getMaterialId());
                x.setUom(m.baseUom());
                x.setRequestQty(q);
                x.setQty(q);
                x.setBaseQty(q);
                x.setLocationId(l.getLocationId() == 0 ? null : l.getLocationId());
                x.setBatchNo(l.getBatchNo().isEmpty() ? null : l.getBatchNo());
                x.setRemark(l.getReason());
                return x;
            }).toList();
            Long outId = stockOutService.createSystem(StockOutType.COUNT_LOSS, e.getKey(), d.getDocDate(), BIZ_TYPE, d.getId(), d.getDocNo(), "盘亏", outs);
            stockOutService.confirm(outId, null, null, false);
            if (firstLoss == null) firstLoss = outId;
        }
        d.setGainInId(firstGain);
        d.setLossOutId(firstLoss);
        if (d.getStatus() == DocStatus.DRAFT) d.setStatus(InvDocAction.MACHINE.fire(d.getStatus(), InvDocAction.APPROVE));
        fire(d, CountStatus.Action.APPROVE, null);
    }

    @Transactional(rollbackFor = Exception.class)
    public void voidDoc(Long id, String reason) {
        CountDO d = getOrThrow(id);
        checkAccess(d);
        d.setStatus(InvDocAction.MACHINE.fire(d.getStatus(), InvDocAction.VOID));
        fire(d, CountStatus.Action.VOID, reason);
    }

    private void fire(CountDO d, CountStatus.Action action, String reason) {
        CountStatus old = CountStatus.valueOf(d.getCountStatus());
        CountStatus next = CountStatus.MACHINE.fire(old, action);
        d.setCountStatus(next.name());
        docMapper.updateByIdOrFail(d);
        support.log(BIZ_TYPE, d.getId(), d.getDocNo(), action.name(), action.label(), old.name(), next.name(), reason);
    }

    private static void requireStatus(CountDO d, CountStatus expected) {
        if (!expected.name().equals(d.getCountStatus())) {
            throw BizException.of(InventoryErrorCodes.COUNT_STATUS, CountStatus.valueOf(d.getCountStatus()).label());
        }
    }

    /** 盘点仓库中任一有权限即可操作 */
    private void checkAccess(CountDO d) {
        Set<Long> allowed = support.warehouses().accessibleIds();
        List<Long> whs = ids(d.getWarehouseIds());
        if (allowed == null || whs.stream().anyMatch(allowed::contains)) return;
        support.warehouses().checkAccess(whs.get(0));
    }

    public CountDO getOrThrow(Long id) {
        CountDO d = id == null ? null : docMapper.selectById(id);
        if (d == null) throw new BizException(InventoryErrorCodes.DOC_NOT_EXISTS);
        return d;
    }

    // ==================== 查询 ====================

    public PageResult<CountRow> page(CountQuery q) {
        Set<Long> allowed = support.warehouses().accessibleIds();
        if (allowed != null && allowed.isEmpty()) return PageResult.empty();
        LambdaQueryWrapper<CountDO> w = new LambdaQueryWrapper<CountDO>()
                .likeRight(StringUtils.hasText(q.getDocNo()), CountDO::getDocNo, q.getDocNo() == null ? null : q.getDocNo().trim().toUpperCase())
                .eq(StringUtils.hasText(q.getCountType()), CountDO::getCountType, q.getCountType())
                .ge(q.getDateFrom() != null, CountDO::getDocDate, q.getDateFrom())
                .le(q.getDateTo() != null, CountDO::getDocDate, q.getDateTo());
        if (StringUtils.hasText(q.getCountStatuses())) w.in(CountDO::getCountStatus, Arrays.asList(q.getCountStatuses().split(",")));
        if (q.getWarehouseId() != null) w.apply("CONCAT(',', warehouse_ids, ',') LIKE {0}", "%," + q.getWarehouseId() + ",%");
        if (allowed != null) {
            w.and(x -> {
                for (Long a : allowed) x.or().apply("CONCAT(',', warehouse_ids, ',') LIKE {0}", "%," + a + ",%");
            });
        }
        w.orderByDesc(CountDO::getDocDate).orderByDesc(CountDO::getId);
        PageResult<CountDO> page = docMapper.selectPage(q, w);
        List<CountDO> docs = page.list();
        if (docs.isEmpty()) return new PageResult<>(List.of(), page.total());
        Map<Long, List<CountLineDO>> byDoc = lineMapper.selectByDocs(docs.stream().map(CountDO::getId).toList()).stream()
                .collect(Collectors.groupingBy(CountLineDO::getCountId));
        Map<Long, WarehouseDO> whs = support.warehouses().byIds(docs.stream().flatMap(d -> ids(d.getWarehouseIds()).stream()).toList());
        Map<Long, UserDTO> users = support.users(docs.stream().map(CountDO::getCreatedBy).toList());
        boolean cost = StockInService.canViewCost();
        return new PageResult<>(docs.stream().map(d -> {
            List<CountLineDO> ls = byDoc.getOrDefault(d.getId(), List.of());
            int input = (int) ls.stream().filter(l -> l.getCountQty() != null).count();
            int diff = (int) ls.stream().filter(l -> l.getDiffQty() != null && l.getDiffQty().signum() != 0).count();
            BigDecimal amount = cost ? ls.stream().map(CountLineDO::getDiffAmount).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add) : null;
            return new CountRow(d.getId(), d.getDocNo(), d.getCountType(), warehouseNames(d, whs), scope(d), ls.size(), input, diff, amount,
                    d.getCountStatus(), DocSupport.name(users, d.getCreatedBy()), d.getDocDate());
        }).toList(), page.total());
    }

    public CountDetail detail(Long id) {
        CountDO d = getOrThrow(id);
        checkAccess(d);
        List<CountLineDO> ls = lineMapper.selectByDoc(id);
        Map<Long, WarehouseDO> whs = support.warehouses().byIds(ids(d.getWarehouseIds()));
        Map<Long, UserDTO> users = support.users(List.of(d.getCreatedBy() == null ? 0L : d.getCreatedBy()));
        boolean cost = StockInService.canViewCost();
        int input = (int) ls.stream().filter(l -> l.getCountQty() != null).count();
        int diff = (int) ls.stream().filter(l -> l.getDiffQty() != null && l.getDiffQty().signum() != 0).count();
        int recount = (int) ls.stream().filter(l -> Boolean.TRUE.equals(l.getNeedRecount())).count();
        BigDecimal amount = cost ? ls.stream().map(CountLineDO::getDiffAmount).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add) : null;
        List<RelatedDoc> docs = new ArrayList<>();
        stockInMapper.selectList(new LambdaQueryWrapper<StockInDO>().eq(StockInDO::getSourceType, BIZ_TYPE).eq(StockInDO::getSourceId, id))
                .forEach(x -> docs.add(new RelatedDoc("STOCK_IN", x.getId(), x.getDocNo(), x.getStatus().name())));
        stockOutMapper.selectList(new LambdaQueryWrapper<StockOutDO>().eq(StockOutDO::getSourceType, BIZ_TYPE).eq(StockOutDO::getSourceId, id))
                .forEach(x -> docs.add(new RelatedDoc("STOCK_OUT", x.getId(), x.getDocNo(), x.getStatus().name())));
        return new CountDetail(d.getId(), d.getDocNo(), d.getCountType(), ids(d.getWarehouseIds()), warehouseNames(d, whs), ids(d.getCategoryIds()),
                ids(d.getLocationIds()), ids(d.getMaterialIds()), scope(d), Boolean.TRUE.equals(d.getIncludeZero()), Boolean.TRUE.equals(d.getBlindCount()),
                d.getSnapshotAt(), d.getCountStatus(), d.getStatus().name(), d.getDocDate(), d.getRemark(), d.getGainInId(), d.getLossOutId(), docs,
                DocSupport.name(users, d.getCreatedBy()), d.getCreatedAt(), d.getVersion(), ls.size(), input, diff, recount, amount, bookVisible(d));
    }

    /** 盲盘且盘点中时，只有审核权限的人能看账面数量 */
    static boolean bookVisible(CountDO d) {
        if (!Boolean.TRUE.equals(d.getBlindCount()) || !CountStatus.COUNTING.name().equals(d.getCountStatus())) return true;
        var u = SecurityUtils.getLoginUserOrNull();
        return u == null || u.hasPermission("inv:count:approve");
    }

    public PageResult<CountLineRow> lines(Long id, LineQuery q) {
        CountDO d = getOrThrow(id);
        checkAccess(d);
        LambdaQueryWrapper<CountLineDO> w = new LambdaQueryWrapper<CountLineDO>().eq(CountLineDO::getCountId, id);
        String f = q.getFilter() == null ? "ALL" : q.getFilter();
        switch (f) {
            case "UNINPUT" -> w.isNull(CountLineDO::getCountQty);
            case "DIFF" -> w.isNotNull(CountLineDO::getDiffQty).ne(CountLineDO::getDiffQty, BigDecimal.ZERO);
            case "RECOUNT" -> w.eq(CountLineDO::getNeedRecount, true);
            default -> {
            }
        }
        if (StringUtils.hasText(q.getKeyword())) {
            List<Long> mids = materialApi.search(q.getKeyword().trim(), null, 500).stream().map(MaterialDTO::id).toList();
            if (mids.isEmpty()) return new PageResult<>(List.of(), 0);
            w.in(CountLineDO::getMaterialId, mids);
        }
        w.orderByAsc(CountLineDO::getLineNo);
        PageResult<CountLineDO> page = lineMapper.selectPage(q, w);
        return new PageResult<>(toRows(d, page.list()), page.total());
    }

    List<CountLineRow> toRows(CountDO d, List<CountLineDO> ls) {
        if (ls.isEmpty()) return List.of();
        Map<Long, MaterialDTO> materials = support.materials(ls.stream().map(CountLineDO::getMaterialId).toList());
        Map<Long, WarehouseDO> whs = support.warehouses().byIds(ls.stream().map(CountLineDO::getWarehouseId).toList());
        Map<Long, LocationDO> locs = support.warehouses().locationsByIds(ls.stream().map(CountLineDO::getLocationId).toList());
        Map<Long, UserDTO> users = support.users(ls.stream().map(CountLineDO::getCounterId).toList());
        boolean book = bookVisible(d);
        boolean cost = StockInService.canViewCost() && book;
        return ls.stream().map(l -> {
            MaterialDTO m = materials.get(l.getMaterialId());
            WarehouseDO w = whs.get(l.getWarehouseId());
            LocationDO loc = locs.get(l.getLocationId());
            return new CountLineRow(l.getId(), l.getLineNo(), l.getWarehouseId(), w == null ? null : w.getName(), l.getLocationId() == 0 ? null : l.getLocationId(),
                    loc == null ? null : loc.getCode(), l.getMaterialId(), m == null ? null : m.code(), m == null ? null : m.name(), m == null ? null : m.spec(),
                    m == null ? null : m.baseUom(), l.getBatchNo().isEmpty() ? null : l.getBatchNo(), book ? l.getBookQty() : null, l.getCountQty(),
                    l.getRecountQty(), l.getFinalQty(), book ? l.getDiffQty() : null, cost ? l.getDiffAmount() : null, Boolean.TRUE.equals(l.getNeedRecount()),
                    Boolean.TRUE.equals(l.getIsAdded()), l.getReason(), DocSupport.name(users, l.getCounterId()), l.getCountedAt(), l.getRemark());
        }).toList();
    }

    /** 导出盘点表（全部行，按仓库 → 库位 → 物料） */
    public List<CountLineRow> sheet(Long id) {
        CountDO d = getOrThrow(id);
        checkAccess(d);
        return toRows(d, lineMapper.selectByDoc(id));
    }

    public boolean isBookVisible(Long id) {
        return bookVisible(getOrThrow(id));
    }

    /** 导入实盘校验：行 ID 属于本盘点单、实盘数量为非负数字；错误写入行 */
    public Map<Integer, String> checkImport(Long id, List<ImportRow> rows) {
        CountDO d = getOrThrow(id);
        checkAccess(d);
        requireStatus(d, CountStatus.COUNTING);
        Set<Long> lineIds = lineMapper.selectByDoc(id).stream().map(CountLineDO::getId).collect(Collectors.toSet());
        Map<Integer, String> actions = new HashMap<>();
        for (ImportRow r : rows) {
            String lid = r.get("id");
            if (!StringUtils.hasText(lid) || !lid.trim().matches("\\d+") || !lineIds.contains(Long.valueOf(lid.trim()))) {
                r.error("行 ID 不属于本盘点单");
                continue;
            }
            String qty = r.get("countQty");
            if (!StringUtils.hasText(qty)) {
                actions.put(r.rowNo(), "跳过（未填实盘）");
                continue;
            }
            try {
                if (new BigDecimal(qty.trim()).signum() < 0) r.error("实盘数量不能小于 0");
            } catch (NumberFormatException e) {
                r.error("实盘数量格式不正确");
            }
            if (!r.hasError()) actions.put(r.rowNo(), "录入");
        }
        return actions;
    }

    /** 导入实盘：按行 ID 写入实盘数量、差异原因、备注；partial 为 false 时有错误行则整体不导入 */
    @Transactional(rollbackFor = Exception.class)
    public ImportResult importCount(Long id, List<ImportRow> rows, boolean partial) {
        checkImport(id, rows);
        List<ImportResult.Error> errors = rows.stream().filter(ImportRow::hasError)
                .map(r -> new ImportResult.Error(r.rowNo(), String.join("；", r.errors()))).toList();
        if (!errors.isEmpty() && !partial) return new ImportResult(0, errors.size(), errors);
        Map<Long, CountLineDO> lines = lineMapper.selectByDoc(id).stream().collect(Collectors.toMap(CountLineDO::getId, l -> l));
        List<LineInput> inputs = new ArrayList<>();
        for (ImportRow r : rows) {
            if (r.hasError() || !StringUtils.hasText(r.get("countQty"))) continue;
            CountLineDO l = lines.get(Long.valueOf(r.get("id").trim()));
            inputs.add(new LineInput(l.getId(), new BigDecimal(r.get("countQty").trim()), l.getRecountQty(),
                    StringUtils.hasText(r.get("reason")) ? r.get("reason").trim() : l.getReason(),
                    StringUtils.hasText(r.get("remark")) ? r.get("remark").trim() : l.getRemark()));
        }
        input(id, inputs);
        return new ImportResult(inputs.size(), errors.size(), errors);
    }

    public Map<String, Object> printData(Long id) {
        CountDetail d = detail(id);
        CountDO doc = getOrThrow(id);
        List<CountLineRow> rows = sheet(id);
        boolean book = !Boolean.TRUE.equals(doc.getBlindCount());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("docNo", d.docNo());
        data.put("docDate", d.docDate());
        data.put("warehouseNames", d.warehouseNames());
        data.put("scope", d.scopeSummary());
        data.put("blindCount", !book);
        data.put("remark", Objects.toString(d.remark(), ""));
        List<Map<String, Object>> lines = new ArrayList<>();
        for (CountLineRow r : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("lineNo", r.lineNo());
            m.put("warehouseName", r.warehouseName());
            m.put("locationCode", Objects.toString(r.locationCode(), ""));
            m.put("code", r.materialCode());
            m.put("name", r.materialName());
            m.put("spec", Objects.toString(r.materialSpec(), ""));
            m.put("uom", r.baseUom());
            m.put("batchNo", Objects.toString(r.batchNo(), ""));
            m.put("bookQty", book && r.bookQty() != null ? r.bookQty() : "");
            lines.add(m);
        }
        data.put("lines", lines);
        return data;
    }

    private String warehouseNames(CountDO d, Map<Long, WarehouseDO> whs) {
        return ids(d.getWarehouseIds()).stream().map(whs::get).filter(Objects::nonNull).map(WarehouseDO::getName).collect(Collectors.joining("、"));
    }

    private static String scope(CountDO d) {
        if (FULL.equals(d.getCountType())) return "全盘";
        List<String> parts = new ArrayList<>();
        if (!ids(d.getCategoryIds()).isEmpty()) parts.add(ids(d.getCategoryIds()).size() + " 个类别");
        if (!ids(d.getLocationIds()).isEmpty()) parts.add(ids(d.getLocationIds()).size() + " 个库位");
        if (!ids(d.getMaterialIds()).isEmpty()) parts.add(ids(d.getMaterialIds()).size() + " 个物料");
        return parts.isEmpty() ? "抽盘" : "抽盘：" + String.join("、", parts);
    }

    private static String code(Map<Long, MaterialDTO> materials, Long id) {
        return materials.containsKey(id) ? materials.get(id).code() : String.valueOf(id);
    }

    static String csv(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return null;
        return ids.stream().filter(Objects::nonNull).distinct().map(String::valueOf).collect(Collectors.joining(","));
    }

    static List<Long> ids(String csv) {
        if (csv == null || csv.isBlank()) return List.of();
        return Arrays.stream(csv.split(",")).map(String::trim).filter(s -> s.matches("\\d+")).map(Long::valueOf).toList();
    }
}
