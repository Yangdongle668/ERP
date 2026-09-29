package com.erp.module.production.service.material;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.common.enums.DocStatus;
import com.erp.common.exception.BizException;
import com.erp.common.result.PageResult;
import com.erp.framework.datascope.DataScopes;
import com.erp.framework.event.DomainEventPublisher;
import com.erp.module.engineering.api.material.MaterialDTO;
import com.erp.module.inventory.api.doc.InventoryDocApi;
import com.erp.module.inventory.api.doc.SourceRef;
import com.erp.module.inventory.api.doc.StockDocEvent;
import com.erp.module.inventory.api.doc.StockInConfirmedEvent;
import com.erp.module.inventory.api.doc.StockInRequest;
import com.erp.module.inventory.api.doc.StockInType;
import com.erp.module.inventory.api.warehouse.WarehouseDTO;
import com.erp.module.inventory.api.warehouse.WarehouseType;
import com.erp.module.production.api.ProductionErrorCodes;
import com.erp.module.production.api.returns.DefectMaterialReturnedEvent;
import com.erp.module.production.config.ProductionModuleConfig;
import com.erp.module.production.controller.vo.CommonVOs.DocResult;
import com.erp.module.production.controller.vo.MaterialDocVOs.CreateResult;
import com.erp.module.production.controller.vo.MaterialDocVOs.ReturnCandidate;
import com.erp.module.production.controller.vo.MaterialDocVOs.ReturnDetail;
import com.erp.module.production.controller.vo.MaterialDocVOs.ReturnLineResp;
import com.erp.module.production.controller.vo.MaterialDocVOs.ReturnLineSave;
import com.erp.module.production.controller.vo.MaterialDocVOs.ReturnQuery;
import com.erp.module.production.controller.vo.MaterialDocVOs.ReturnRow;
import com.erp.module.production.controller.vo.MaterialDocVOs.ReturnSave;
import com.erp.module.production.dal.dataobject.MfgProdOrderDO;
import com.erp.module.production.dal.dataobject.MfgProdOrderMaterialDO;
import com.erp.module.production.dal.dataobject.MfgReturnDO;
import com.erp.module.production.dal.dataobject.MfgReturnLineDO;
import com.erp.module.production.dal.dataobject.MfgTraceDO;
import com.erp.module.production.dal.mapper.MfgProdOrderMapper;
import com.erp.module.production.dal.mapper.MfgProdOrderMaterialMapper;
import com.erp.module.production.dal.mapper.MfgReturnLineMapper;
import com.erp.module.production.dal.mapper.MfgReturnMapper;
import com.erp.module.production.dal.mapper.MfgTraceMapper;
import com.erp.module.production.service.MfgAction;
import com.erp.module.production.service.MfgStateMachines;
import com.erp.module.production.service.MfgSupport;
import com.erp.module.production.service.order.MaterialPlanner;
import com.erp.module.production.service.order.OrderProgressService;
import com.erp.module.production.service.trace.TraceRecorder;
import com.erp.module.system.api.user.UserDTO;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 退料单（需求 09-03）：良品退料（余料）/ 不良退料（来料不良、制程损坏）。
 * 提交后生成仓库“生产退料入库单”，入库确认回写已退数量、追溯（负数），不良退料发布事件供品质开 NCR。
 */
@Service("mfgReturnService")
public class ReturnService {

    public static final String BIZ_TYPE = ProductionModuleConfig.RETURN;
    public static final String SOURCE_TYPE = "MFG_RETURN";

    private final MfgReturnMapper mapper;
    private final MfgReturnLineMapper lineMapper;
    private final MfgProdOrderMapper orderMapper;
    private final MfgProdOrderMaterialMapper materialMapper;
    private final MfgTraceMapper traceMapper;
    private final OrderProgressService progress;
    private final MaterialPlanner planner;
    private final MfgSupport support;
    private final InventoryDocApi inventoryDocApi;
    private final TraceRecorder trace;
    private final DomainEventPublisher eventPublisher;

    public ReturnService(MfgReturnMapper mapper, MfgReturnLineMapper lineMapper, MfgProdOrderMapper orderMapper, MfgProdOrderMaterialMapper materialMapper,
                         MfgTraceMapper traceMapper, OrderProgressService progress, MaterialPlanner planner, MfgSupport support,
                         InventoryDocApi inventoryDocApi, TraceRecorder trace, DomainEventPublisher eventPublisher) {
        this.mapper = mapper;
        this.lineMapper = lineMapper;
        this.orderMapper = orderMapper;
        this.materialMapper = materialMapper;
        this.traceMapper = traceMapper;
        this.progress = progress;
        this.planner = planner;
        this.support = support;
        this.inventoryDocApi = inventoryDocApi;
        this.trace = trace;
        this.eventPublisher = eventPublisher;
    }

    public MfgReturnDO getOrThrow(Long id) {
        MfgReturnDO r = id == null ? null : mapper.selectById(id);
        if (r == null) throw new BizException(ProductionErrorCodes.RETURN_NOT_EXISTS);
        return r;
    }

    // ==================== 查询 ====================

    public PageResult<ReturnRow> page(ReturnQuery q) {
        LambdaQueryWrapper<MfgReturnDO> w = new LambdaQueryWrapper<MfgReturnDO>().eq(MfgReturnDO::getDeleted, false);
        if (StringUtils.hasText(q.getDocNo())) w.likeRight(MfgReturnDO::getDocNo, q.getDocNo().trim());
        if (q.getProdOrderId() != null) w.eq(MfgReturnDO::getProdOrderId, q.getProdOrderId());
        if (StringUtils.hasText(q.getProdOrderNo())) w.likeRight(MfgReturnDO::getSourceNo, q.getProdOrderNo().trim());
        if (StringUtils.hasText(q.getReturnType())) w.eq(MfgReturnDO::getReturnType, q.getReturnType());
        if (StringUtils.hasText(q.getStatuses())) w.in(MfgReturnDO::getStatus, Arrays.stream(q.getStatuses().split(",")).map(DocStatus::valueOf).toList());
        if (q.getWarehouseId() != null) w.eq(MfgReturnDO::getWarehouseId, q.getWarehouseId());
        if (q.getMaterialId() != null) {
            List<Long> ids = lineMapper.selectList(new LambdaQueryWrapper<MfgReturnLineDO>().eq(MfgReturnLineDO::getMaterialId, q.getMaterialId()))
                    .stream().map(MfgReturnLineDO::getReturnId).distinct().toList();
            if (ids.isEmpty()) return new PageResult<>(List.of(), 0L);
            w.in(MfgReturnDO::getId, ids);
        }
        if (q.getDateFrom() != null) w.ge(MfgReturnDO::getDocDate, q.getDateFrom());
        if (q.getDateTo() != null) w.le(MfgReturnDO::getDocDate, q.getDateTo());
        w.orderByDesc(MfgReturnDO::getId);
        IPage<MfgReturnDO> p = mapper.selectScopedPage(new Page<>(q.getPageNo(), q.getPageSize()), w);
        List<MfgReturnDO> list = p.getRecords();
        List<Long> orderIds = list.stream().map(MfgReturnDO::getProdOrderId).distinct().toList();
        Map<Long, MfgProdOrderDO> orders = orderIds.isEmpty() ? Map.of()
                : orderMapper.selectBatchIds(orderIds).stream().collect(Collectors.toMap(MfgProdOrderDO::getId, Function.identity()));
        Map<Long, MaterialDTO> ms = support.materials(orders.values().stream().map(MfgProdOrderDO::getMaterialId).toList());
        Map<Long, WarehouseDTO> whs = support.warehouses(list.stream().map(MfgReturnDO::getWarehouseId).toList());
        Map<Long, List<MfgReturnLineDO>> lines = lineMapper.selectByParents(list.stream().map(MfgReturnDO::getId).toList()).stream()
                .collect(Collectors.groupingBy(MfgReturnLineDO::getReturnId));
        Map<Long, UserDTO> users = support.users(list.stream().map(MfgReturnDO::getOwnerId).toList());
        return new PageResult<>(list.stream().map(r -> {
            MfgProdOrderDO o = orders.get(r.getProdOrderId());
            MaterialDTO m = o == null ? null : ms.get(o.getMaterialId());
            WarehouseDTO wh = whs.get(r.getWarehouseId());
            List<MfgReturnLineDO> ls = lines.getOrDefault(r.getId(), List.of());
            return new ReturnRow(r.getId(), r.getDocNo(), r.getReturnType(), r.getProdOrderId(), o == null ? null : o.getDocNo(), m == null ? null : m.code(),
                    m == null ? null : m.name(), r.getWarehouseId(), wh == null ? null : wh.name(), ls.size(),
                    MfgSupport.sum(ls.stream().map(MfgReturnLineDO::getQty).toList()), r.getStatus().name(), r.getStockInNos(),
                    MfgSupport.name(users, r.getOwnerId()), r.getDocDate());
        }).toList(), p.getTotal());
    }

    public ReturnDetail detail(Long id) {
        MfgReturnDO r = getOrThrow(id);
        DataScopes.check(r.getOrgId(), r.getDeptId(), r.getOwnerId(), "退料单");
        MfgProdOrderDO o = progress.getOrThrow(r.getProdOrderId());
        MaterialDTO product = support.material(o.getMaterialId());
        List<MfgReturnLineDO> lines = lineMapper.selectByParent(id);
        Map<Long, ReturnCandidate> cands = candidates(o.getId(), r.getReturnType(), id).stream()
                .collect(Collectors.toMap(ReturnCandidate::materialLineId, Function.identity()));
        Map<Long, MaterialDTO> ms = support.materials(lines.stream().map(MfgReturnLineDO::getMaterialId).toList());
        List<ReturnLineResp> resp = lines.stream().map(l -> {
            MaterialDTO c = ms.get(l.getMaterialId());
            ReturnCandidate cand = cands.get(l.getMaterialLineId());
            return new ReturnLineResp(l.getId(), l.getLineNo(), l.getMaterialLineId(), l.getMaterialId(), c == null ? null : c.code(), c == null ? null : c.name(),
                    c == null ? null : c.spec(), c == null ? null : c.baseUom(), cand == null ? null : cand.returnableQty(), l.getQty(), l.getBatchNo(),
                    l.getDefectDesc(), l.getReceivedQty());
        }).toList();
        return new ReturnDetail(r.getId(), r.getDocNo(), r.getReturnType(), r.getStatus().name(), r.getDocDate(), o.getId(), o.getDocNo(), o.getProdStatus(),
                product.id(), product.code(), product.name(), r.getWarehouseId(), support.warehouseName(r.getWarehouseId()), r.getStockInIds(), r.getStockInNos(),
                r.getRemark(), r.getOwnerId(), support.userName(r.getOwnerId()), r.getCreatedAt(), r.getVersion(), resp);
    }

    /**
     * 可退数量（3.4）：良品 = max(0, 已领 − 已退 − 理论耗用)，不良 = 已领 − 已退；扣除其他未入库退料单占用。
     */
    public List<ReturnCandidate> candidates(Long prodOrderId, String returnType, Long excludeReturnId) {
        MfgProdOrderDO o = progress.getOrThrow(prodOrderId);
        boolean defect = "DEFECT".equals(returnType);
        List<MfgProdOrderMaterialDO> mats = materialMapper.selectByParent(prodOrderId);
        Map<Long, BigDecimal> pending = pendingReturns(prodOrderId, excludeReturnId);
        Map<Long, MaterialDTO> ms = support.materials(mats.stream().map(MfgProdOrderMaterialDO::getComponentId).toList());
        Map<Long, List<String>> batches = issuedBatches(o);
        List<ReturnCandidate> out = new ArrayList<>();
        for (MfgProdOrderMaterialDO m : mats) {
            if (MfgSupport.nz(m.getIssuedQty()).signum() <= 0) continue;
            MaterialDTO c = ms.get(m.getComponentId());
            BigDecimal net = MaterialPlanner.netQty(m);
            BigDecimal theo = planner.theoretical(o, m, c == null ? null : c.baseUom());
            BigDecimal returnable = defect ? net : MfgSupport.max0(net.subtract(theo)).min(net);
            returnable = MfgSupport.max0(returnable.subtract(pending.getOrDefault(m.getId(), BigDecimal.ZERO)));
            WarehouseDTO wh = defaultWarehouse(m.getComponentId(), defect);
            out.add(new ReturnCandidate(m.getId(), m.getComponentId(), c == null ? null : c.code(), c == null ? null : c.name(), c == null ? null : c.spec(),
                    c == null ? null : c.baseUom(), m.getIssuedQty(), m.getReturnedQty(), theo, returnable, wh == null ? null : wh.id(),
                    wh == null ? null : wh.name(), batches.getOrDefault(m.getComponentId(), List.of())));
        }
        return out;
    }

    private WarehouseDTO defaultWarehouse(Long materialId, boolean defect) {
        try {
            return support.warehouseApi().getDefaultWarehouse(materialId, defect ? WarehouseType.NG : null);
        } catch (BizException e) {
            return null;
        }
    }

    /** 本订单领用过的批次（按投入物料汇总净数量 > 0） */
    private Map<Long, List<String>> issuedBatches(MfgProdOrderDO o) {
        Map<Long, Map<String, BigDecimal>> sum = new LinkedHashMap<>();
        for (MfgTraceDO t : traceMapper.selectList(new LambdaQueryWrapper<MfgTraceDO>().eq(MfgTraceDO::getProdOrderId, o.getId()).orderByAsc(MfgTraceDO::getId))) {
            if (t.getComponentBatchNo() == null) continue;
            sum.computeIfAbsent(t.getComponentMaterialId(), k -> new LinkedHashMap<>()).merge(t.getComponentBatchNo(), t.getQty(), BigDecimal::add);
        }
        Map<Long, List<String>> out = new HashMap<>();
        sum.forEach((k, v) -> out.put(k, v.entrySet().stream().filter(e -> e.getValue().signum() > 0).map(Map.Entry::getKey).toList()));
        return out;
    }

    private Map<Long, BigDecimal> pendingReturns(Long prodOrderId, Long excludeReturnId) {
        List<MfgReturnDO> list = mapper.selectList(new LambdaQueryWrapper<MfgReturnDO>().eq(MfgReturnDO::getProdOrderId, prodOrderId)
                .in(MfgReturnDO::getStatus, List.of(DocStatus.PENDING_APPROVAL, DocStatus.APPROVED))
                .ne(excludeReturnId != null, MfgReturnDO::getId, excludeReturnId));
        Map<Long, BigDecimal> map = new HashMap<>();
        if (list.isEmpty()) return map;
        for (MfgReturnLineDO l : lineMapper.selectByParents(list.stream().map(MfgReturnDO::getId).toList())) {
            map.merge(l.getMaterialLineId(), MfgSupport.max0(l.getQty().subtract(l.getReceivedQty())), BigDecimal::add);
        }
        return map;
    }

    // ==================== 新建 / 修改 ====================

    @Transactional(rollbackFor = Exception.class)
    public CreateResult create(ReturnSave req) {
        MfgProdOrderDO o = progress.getOrThrow(req.prodOrderId());
        DataScopes.check(o.getOrgId(), o.getDeptId(), o.getOwnerId(), "生产订单");
        OrderProgressService.requireActive(o);
        String type = "DEFECT".equals(req.returnType()) ? "DEFECT" : "GOOD";
        List<ReturnLineSave> lines = validLines(req);
        Map<Long, MfgProdOrderMaterialDO> mats = materials(o.getId());
        Map<Long, List<ReturnLineSave>> byWarehouse = new LinkedHashMap<>();
        int no = 0;
        for (ReturnLineSave l : lines) {
            no++;
            MfgProdOrderMaterialDO m = mats.get(l.materialLineId());
            if (m == null) throw BizException.of(ProductionErrorCodes.ISSUE_LINE_INVALID, no);
            Long wh = req.warehouseId() != null ? req.warehouseId()
                    : support.warehouseApi().getDefaultWarehouse(m.getComponentId(), "DEFECT".equals(type) ? WarehouseType.NG : null).id();
            byWarehouse.computeIfAbsent(wh, k -> new ArrayList<>()).add(l);
        }
        List<Long> ids = new ArrayList<>();
        List<String> nos = new ArrayList<>();
        for (Map.Entry<Long, List<ReturnLineSave>> e : byWarehouse.entrySet()) {
            MfgReturnDO r = new MfgReturnDO();
            r.setDocNo(support.nextNo(BIZ_TYPE));
            r.setDocDate(LocalDate.now());
            r.setStatus(DocStatus.DRAFT);
            r.setReturnType(type);
            r.setProdOrderId(o.getId());
            r.setSourceType(ProductionModuleConfig.PROD_ORDER);
            r.setSourceId(o.getId());
            r.setSourceNo(o.getDocNo());
            r.setWarehouseId(e.getKey());
            r.setRemark(MfgSupport.trim(req.remark()));
            support.fillOwner(r, null, o.getDeptId());
            r.setDeptId(o.getDeptId());
            mapper.insert(r);
            saveLines(r, e.getValue(), mats);
            ids.add(r.getId());
            nos.add(r.getDocNo());
        }
        return new CreateResult(ids, nos, List.of());
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, ReturnSave req) {
        MfgReturnDO r = getOrThrow(id);
        DataScopes.check(r.getOrgId(), r.getDeptId(), r.getOwnerId(), "退料单");
        MfgSupport.requireDraft(r);
        if (req.version() != null) r.setVersion(req.version());
        r.setRemark(MfgSupport.trim(req.remark()));
        if (req.warehouseId() != null) r.setWarehouseId(req.warehouseId());
        mapper.updateByIdOrFail(r);
        List<ReturnLineSave> lines = validLines(req);
        lineMapper.deleteByParent(id);
        saveLines(r, lines, materials(r.getProdOrderId()));
    }

    private static List<ReturnLineSave> validLines(ReturnSave req) {
        List<ReturnLineSave> lines = req.lines() == null ? List.of() : req.lines().stream().filter(l -> l.qty() != null && l.qty().signum() > 0).toList();
        if (lines.isEmpty()) throw new BizException(ProductionErrorCodes.DOC_NO_LINES);
        return lines;
    }

    private Map<Long, MfgProdOrderMaterialDO> materials(Long orderId) {
        return materialMapper.selectByParent(orderId).stream().collect(Collectors.toMap(MfgProdOrderMaterialDO::getId, Function.identity()));
    }

    private void saveLines(MfgReturnDO r, List<ReturnLineSave> lines, Map<Long, MfgProdOrderMaterialDO> mats) {
        int no = 0;
        for (ReturnLineSave l : lines) {
            no++;
            MfgProdOrderMaterialDO m = mats.get(l.materialLineId());
            if (m == null) throw BizException.of(ProductionErrorCodes.ISSUE_LINE_INVALID, no);
            MfgReturnLineDO line = new MfgReturnLineDO();
            line.setReturnId(r.getId());
            line.setLineNo(no);
            line.setMaterialLineId(m.getId());
            line.setMaterialId(m.getComponentId());
            line.setQty(l.qty());
            line.setBatchNo(MfgSupport.trim(l.batchNo()));
            line.setDefectDesc(MfgSupport.trim(l.defectDesc()));
            line.setReceivedQty(BigDecimal.ZERO);
            lineMapper.insert(line);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        MfgReturnDO r = getOrThrow(id);
        DataScopes.check(r.getOrgId(), r.getDeptId(), r.getOwnerId(), "退料单");
        MfgSupport.requireDraft(r);
        lineMapper.deleteByParent(id);
        mapper.deleteById(id);
    }

    // ==================== 提交 / 撤回 ====================

    /** 提交（RET-R01）：退料数量 ≤ 可退数量；不良退料必须填写不良描述；生成生产退料入库单 */
    @Transactional(rollbackFor = Exception.class)
    public DocResult submit(Long id) {
        MfgReturnDO r = getOrThrow(id);
        DataScopes.check(r.getOrgId(), r.getDeptId(), r.getOwnerId(), "退料单");
        MfgSupport.requireDraft(r);
        MfgProdOrderDO o = progress.getOrThrow(r.getProdOrderId());
        OrderProgressService.requireActive(o);
        List<MfgReturnLineDO> lines = lineMapper.selectByParent(id);
        if (lines.isEmpty()) throw new BizException(ProductionErrorCodes.DOC_NO_LINES);
        Map<Long, ReturnCandidate> cands = candidates(o.getId(), r.getReturnType(), id).stream()
                .collect(Collectors.toMap(ReturnCandidate::materialLineId, Function.identity()));
        Map<Long, BigDecimal> qty = new HashMap<>();
        for (MfgReturnLineDO l : lines) {
            qty.merge(l.getMaterialLineId(), l.getQty(), BigDecimal::add);
            if ("DEFECT".equals(r.getReturnType()) && !StringUtils.hasText(l.getDefectDesc())) {
                ReturnCandidate c = cands.get(l.getMaterialLineId());
                throw BizException.of(ProductionErrorCodes.RETURN_DEFECT_DESC, c == null ? String.valueOf(l.getMaterialId()) : c.code());
            }
        }
        for (Map.Entry<Long, BigDecimal> e : qty.entrySet()) {
            ReturnCandidate c = cands.get(e.getKey());
            BigDecimal limit = c == null ? BigDecimal.ZERO : c.returnableQty();
            if (e.getValue().compareTo(limit) > 0) throw BizException.of(ProductionErrorCodes.RETURN_OVER, c == null ? "" : c.code(), MfgSupport.plain(limit));
        }
        support.fire(MfgStateMachines.MATERIAL_DOC, mapper, r, BIZ_TYPE, MfgAction.SUBMIT, null);
        support.fire(MfgStateMachines.MATERIAL_DOC, mapper, r, BIZ_TYPE, MfgAction.APPROVE, null);
        Map<Long, MaterialDTO> ms = support.materials(lines.stream().map(MfgReturnLineDO::getMaterialId).toList());
        List<StockInRequest.Line> inLines = lines.stream().map(l -> new StockInRequest.Line(l.getId(), l.getMaterialId(),
                ms.containsKey(l.getMaterialId()) ? ms.get(l.getMaterialId()).baseUom() : null, l.getQty(), l.getBatchNo(), null, null, null, null)).toList();
        List<Long> inIds = inventoryDocApi.createStockIn(new StockInRequest(StockInType.PRODUCTION_RETURN, new SourceRef(SOURCE_TYPE, r.getId(), r.getDocNo()),
                r.getWarehouseId(), LocalDate.now(), null, null, inLines));
        MfgReturnDO fresh = getOrThrow(id);
        fresh.setStockInIds(inIds.stream().map(String::valueOf).collect(Collectors.joining(",")));
        mapper.updateByIdOrFail(fresh);
        return DocResult.of(fresh.getStatus().name());
    }

    @Transactional(rollbackFor = Exception.class)
    public void withdraw(Long id) {
        MfgReturnDO r = getOrThrow(id);
        DataScopes.check(r.getOrgId(), r.getDeptId(), r.getOwnerId(), "退料单");
        if (r.getStatus() != DocStatus.APPROVED) throw BizException.of(ProductionErrorCodes.STATUS_NOT_ALLOWED, r.getStatus().label(), "撤回");
        try {
            inventoryDocApi.cancelBySource(SOURCE_TYPE, id);
        } catch (BizException e) {
            throw new BizException(ProductionErrorCodes.ISSUE_WITHDRAW_CONFIRMED);
        }
        r.setStockInIds(null);
        support.fire(MfgStateMachines.MATERIAL_DOC, mapper, r, BIZ_TYPE, MfgAction.WITHDRAW, null);
    }

    // ==================== 仓库回写 ====================

    /** 入库确认（RET-R02）：回写已收、用料已退（良品另记）、追溯（负数）；不良退料发布事件 */
    @EventListener
    public void onStockIn(StockInConfirmedEvent e) {
        if (e.getSource() == null || !SOURCE_TYPE.equals(e.getSource().sourceType())) return;
        MfgReturnDO r = mapper.selectById(e.getSource().sourceId());
        if (r == null || r.getStatus() != DocStatus.APPROVED) return;
        MfgProdOrderDO o = progress.getOrThrow(r.getProdOrderId());
        Map<Long, MfgReturnLineDO> lines = lineMapper.selectByParent(r.getId()).stream().collect(Collectors.toMap(MfgReturnLineDO::getId, Function.identity()));
        Map<Long, MfgProdOrderMaterialDO> mats = new HashMap<>();
        List<DefectMaterialReturnedEvent.Line> defects = new ArrayList<>();
        boolean good = "GOOD".equals(r.getReturnType());
        for (StockInConfirmedEvent.Line l : e.getLines()) {
            MfgReturnLineDO rl = lines.get(l.sourceLineId());
            if (rl == null) continue;
            rl.setReceivedQty(rl.getReceivedQty().add(l.baseQty()));
            MfgProdOrderMaterialDO m = mats.computeIfAbsent(rl.getMaterialLineId(), materialMapper::selectById);
            if (m != null) {
                m.setReturnedQty(MfgSupport.nz(m.getReturnedQty()).add(l.baseQty()));
                if (good) m.setReturnedGoodQty(MfgSupport.nz(m.getReturnedGoodQty()).add(l.baseQty()));
            }
            trace.record(o, l.materialId(), l.batchNo(), l.baseQty().negate(), TraceRecorder.STOCK_IN, e.getStockInId(), e.getStockInNo());
            if (!good) defects.add(new DefectMaterialReturnedEvent.Line(l.materialId(), l.batchNo(), l.baseQty(), rl.getDefectDesc()));
        }
        lines.values().forEach(lineMapper::updateByIdOrFail);
        mats.values().stream().filter(Objects::nonNull).forEach(materialMapper::updateByIdOrFail);
        r.setStockInNos(IssueService.append(r.getStockInNos(), e.getStockInNo()));
        support.fire(MfgStateMachines.MATERIAL_DOC, mapper, r, BIZ_TYPE, MfgAction.COMPLETE, e.getStockInNo());
        if (!defects.isEmpty()) eventPublisher.publish(new DefectMaterialReturnedEvent(r.getId(), r.getDocNo(), o.getId(), o.getDocNo(), defects));
    }

    @EventListener
    public void onStockDoc(StockDocEvent e) {
        if (!"STOCK_IN".equals(e.getDocType()) || e.getSource() == null || !SOURCE_TYPE.equals(e.getSource().sourceType())) return;
        MfgReturnDO r = mapper.selectById(e.getSource().sourceId());
        if (r == null) return;
        if (e.getKind() == StockDocEvent.Kind.IN_REVERSED && r.getStatus() == DocStatus.COMPLETED) {
            boolean good = "GOOD".equals(r.getReturnType());
            for (MfgReturnLineDO rl : lineMapper.selectByParent(r.getId())) {
                MfgProdOrderMaterialDO m = materialMapper.selectById(rl.getMaterialLineId());
                if (m != null) {
                    m.setReturnedQty(MfgSupport.nz(m.getReturnedQty()).subtract(rl.getReceivedQty()));
                    if (good) m.setReturnedGoodQty(MfgSupport.nz(m.getReturnedGoodQty()).subtract(rl.getReceivedQty()));
                    materialMapper.updateByIdOrFail(m);
                }
                rl.setReceivedQty(BigDecimal.ZERO);
                lineMapper.updateByIdOrFail(rl);
            }
            trace.reverse(TraceRecorder.STOCK_IN, e.getDocId());
            support.fire(MfgStateMachines.MATERIAL_DOC, mapper, r, BIZ_TYPE, MfgAction.REOPEN, "入库单 " + e.getDocNo() + " 反确认");
        } else if (e.getKind() == StockDocEvent.Kind.REJECTED && r.getStatus() == DocStatus.APPROVED) {
            support.fire(MfgStateMachines.MATERIAL_DOC, mapper, r, BIZ_TYPE, MfgAction.VOID, "仓库退回：" + (e.getReason() == null ? "" : e.getReason()));
            support.message(List.of(r.getOwnerId()), "退料单被仓库退回", "退料单 " + r.getDocNo() + " 的入库单被仓库退回：" + (e.getReason() == null ? "" : e.getReason()),
                    "/production/return/" + r.getId());
        }
    }

    // ==================== 打印 ====================

    public Map<String, Object> printData(Long id) {
        ReturnDetail d = detail(id);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("docNo", d.docNo());
        data.put("returnTypeName", "DEFECT".equals(d.returnType()) ? "不良退料" : "良品退料");
        data.put("docDate", d.docDate());
        data.put("prodOrderNo", d.prodOrderNo());
        data.put("productCode", d.productCode());
        data.put("warehouseName", d.warehouseName());
        data.put("remark", d.remark());
        data.put("ownerName", d.ownerName());
        data.put("lines", d.lines().stream().map(l -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("lineNo", l.lineNo());
            m.put("materialCode", l.code());
            m.put("materialName", l.name());
            m.put("uom", l.uom());
            m.put("qty", l.qty());
            m.put("batchNo", l.batchNo());
            m.put("defectDesc", l.defectDesc());
            return m;
        }).toList());
        return data;
    }
}
