package com.erp.module.shipping.service.picking;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.common.exception.BizException;
import com.erp.common.result.PageResult;
import com.erp.common.util.Decimals;
import com.erp.module.crm.api.customer.CustomerDTO;
import com.erp.module.engineering.api.material.MaterialDTO;
import com.erp.module.inventory.api.stock.BatchSuggestion;
import com.erp.module.inventory.api.stock.InventoryQueryApi;
import com.erp.module.shipping.api.ShippingErrorCodes;
import com.erp.module.shipping.config.ShippingModuleConfig;
import com.erp.module.shipping.controller.vo.PickingVOs.BatchOption;
import com.erp.module.shipping.controller.vo.PickingVOs.NoticeLineSum;
import com.erp.module.shipping.controller.vo.PickingVOs.PickLineSave;
import com.erp.module.shipping.controller.vo.PickingVOs.PickingDetail;
import com.erp.module.shipping.controller.vo.PickingVOs.PickingLineVO;
import com.erp.module.shipping.controller.vo.PickingVOs.PickingQuery;
import com.erp.module.shipping.controller.vo.PickingVOs.PickingRow;
import com.erp.module.shipping.dal.dataobject.ShpNoticeDO;
import com.erp.module.shipping.dal.dataobject.ShpNoticeLineDO;
import com.erp.module.shipping.dal.dataobject.ShpPickingDO;
import com.erp.module.shipping.dal.dataobject.ShpPickingLineDO;
import com.erp.module.shipping.dal.mapper.ShpNoticeLineMapper;
import com.erp.module.shipping.dal.mapper.ShpNoticeMapper;
import com.erp.module.shipping.dal.mapper.ShpPickingLineMapper;
import com.erp.module.shipping.dal.mapper.ShpPickingMapper;
import com.erp.module.shipping.service.NoticeStatus;
import com.erp.module.shipping.service.PickingStatus;
import com.erp.module.shipping.service.ShpAction;
import com.erp.module.shipping.service.ShpStateMachines;
import com.erp.module.shipping.service.ShpSupport;
import com.erp.module.shipping.service.notice.NoticeFlow;
import com.erp.module.system.api.user.UserDTO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 拣货单（11-02 第 1～4 节） */
@Service
public class PickingService {

    private static final String BIZ_TYPE = ShippingModuleConfig.PICKING;
    private static final BigDecimal ALL = new BigDecimal("999999999");

    private final ShpPickingMapper mapper;
    private final ShpPickingLineMapper lineMapper;
    private final ShpNoticeMapper noticeMapper;
    private final ShpNoticeLineMapper noticeLineMapper;
    private final InventoryQueryApi inventoryQueryApi;
    private final NoticeFlow flow;
    private final ShpSupport support;

    public PickingService(ShpPickingMapper mapper, ShpPickingLineMapper lineMapper, ShpNoticeMapper noticeMapper, ShpNoticeLineMapper noticeLineMapper,
                          InventoryQueryApi inventoryQueryApi, NoticeFlow flow, ShpSupport support) {
        this.mapper = mapper;
        this.lineMapper = lineMapper;
        this.noticeMapper = noticeMapper;
        this.noticeLineMapper = noticeLineMapper;
        this.inventoryQueryApi = inventoryQueryApi;
        this.flow = flow;
        this.support = support;
    }

    // ==================== 生成 / 取消（由出货通知调用） ====================

    /** SHP-PK-R01：出货仓按 FIFO/FEFO 推荐批次与库位；库存不足时推荐可用部分并标记缺货 */
    public ShpPickingDO createFor(ShpNoticeDO n) {
        ShpPickingDO p = new ShpPickingDO();
        p.setDocNo(support.nextNo(ShippingModuleConfig.PICKING));
        p.setDocDate(LocalDate.now());
        p.setNoticeId(n.getId());
        p.setWarehouseId(n.getWarehouseId());
        p.setPickingStatus(PickingStatus.WAITING.name());
        p.setStatus(PickingStatus.WAITING.docStatus());
        p.setSourceType(ShippingModuleConfig.NOTICE);
        p.setSourceId(n.getId());
        p.setSourceNo(n.getDocNo());
        support.fillOwner(p, n.getOwnerId());
        mapper.insert(p);
        int no = 1;
        for (ShpNoticeLineDO nl : noticeLineMapper.selectByParent(n.getId())) {
            BigDecimal need = NoticeFlow.effectiveQty(nl);
            for (BatchSuggestion s : inventoryQueryApi.suggestBatches(nl.getMaterialId(), n.getWarehouseId(), need)) {
                if (need.signum() <= 0) break;
                BigDecimal q = s.qty().min(need);
                if (q.signum() <= 0) continue;
                lineMapper.insert(line(p.getId(), no++, nl, s.locationId(), s.batchNo(), q, false));
                need = need.subtract(q);
            }
            if (need.signum() > 0) lineMapper.insert(line(p.getId(), no++, nl, null, null, need, true));
        }
        support.log(BIZ_TYPE, p.getId(), p.getDocNo(), ShpAction.CREATE.name(), ShpAction.CREATE.label(), null, p.getPickingStatus(), "出货通知 " + n.getDocNo());
        return p;
    }

    private static ShpPickingLineDO line(Long pickingId, int no, ShpNoticeLineDO nl, Long locationId, String batchNo, BigDecimal qty, boolean shortage) {
        ShpPickingLineDO l = new ShpPickingLineDO();
        l.setPickingId(pickingId);
        l.setLineNo(no);
        l.setNoticeLineId(nl.getId());
        l.setMaterialId(nl.getMaterialId());
        l.setLocationId(locationId);
        l.setBatchNo(StringUtils.hasText(batchNo) ? batchNo : null);
        l.setSuggestedQty(Decimals.qty(qty));
        l.setPickedQty(BigDecimal.ZERO);
        l.setShortage(shortage);
        return l;
    }

    /** SHP-PK-R06：通知关闭 / 反审核时拣货单作废 */
    public void cancelFor(Long noticeId, String reason) {
        ShpPickingDO p = flow.activePicking(noticeId);
        if (p != null) fire(p, ShpAction.CANCEL, reason);
    }

    // ==================== 拣货 ====================

    @Transactional(rollbackFor = Exception.class)
    public void start(Long id) {
        ShpPickingDO p = get(id);
        if (!PickingStatus.WAITING.name().equals(p.getPickingStatus())) {
            throw BizException.of(ShippingErrorCodes.STATUS_NOT_ALLOWED, PickingStatus.valueOf(p.getPickingStatus()).label(), "开始拣货");
        }
        doStart(p);
    }

    private void doStart(ShpPickingDO p) {
        p.setPickerId(support.currentUser());
        p.setStartedAt(LocalDateTime.now());
        fire(p, ShpAction.START_PICK, null);
        ShpNoticeDO n = flow.get(p.getNoticeId());
        if (NoticeFlow.status(n) == NoticeStatus.APPROVED) flow.fire(n, ShpAction.START_PICK, "拣货单 " + p.getDocNo());
    }

    /** 录入实拣批次与数量（SHP-PK-R02：批次可用数量校验；未开始时自动开始） */
    @Transactional(rollbackFor = Exception.class)
    public void saveLines(Long id, List<PickLineSave> lines) {
        ShpPickingDO p = get(id);
        if (PickingStatus.WAITING.name().equals(p.getPickingStatus())) {
            doStart(p);
            p = get(id);
        }
        if (!PickingStatus.PICKING.name().equals(p.getPickingStatus())) {
            throw BizException.of(ShippingErrorCodes.STATUS_NOT_ALLOWED, PickingStatus.valueOf(p.getPickingStatus()).label(), "录入拣货");
        }
        Map<Long, ShpNoticeLineDO> nls = noticeLineMapper.selectByParent(p.getNoticeId()).stream().collect(Collectors.toMap(ShpNoticeLineDO::getId, Function.identity()));
        List<PickLineSave> list = lines == null ? List.of() : lines;
        Map<Long, BigDecimal> perLine = new HashMap<>();
        for (PickLineSave s : list) {
            ShpNoticeLineDO nl = nls.get(s.noticeLineId());
            if (nl == null) throw BizException.of(ShippingErrorCodes.NOT_EXISTS, "通知行");
            if (s.pickedQty().signum() < 0) throw BizException.of(ShippingErrorCodes.SN_QTY_INVALID, nl.getLineNo());
            perLine.merge(nl.getId(), s.pickedQty(), BigDecimal::add);
        }
        for (Map.Entry<Long, BigDecimal> e : perLine.entrySet()) {
            ShpNoticeLineDO nl = nls.get(e.getKey());
            BigDecimal max = NoticeFlow.effectiveQty(nl);
            if (e.getValue().compareTo(max) > 0) throw BizException.of(ShippingErrorCodes.PK_PICK_EXCEED, nl.getLineNo(), ShpSupport.plain(max));
        }
        checkBatches(p.getWarehouseId(), list.stream().map(s -> new Pick(nls.get(s.noticeLineId()).getMaterialId(), s.batchNo(), s.pickedQty())).toList());
        lineMapper.deleteByParent(id);
        int no = 1;
        for (PickLineSave s : list) {
            ShpNoticeLineDO nl = nls.get(s.noticeLineId());
            ShpPickingLineDO l = line(id, no++, nl, s.locationId(), ShpSupport.trim(s.batchNo()), ShpSupport.nz(s.suggestedQty()), Boolean.TRUE.equals(s.shortage()));
            l.setPickedQty(Decimals.qty(s.pickedQty()));
            l.setSerialNos(ShpSupport.trim(s.serialNos()));
            lineMapper.insert(l);
        }
        p = get(id);
        mapper.updateByIdOrFail(p);
    }

    private record Pick(Long materialId, String batchNo, BigDecimal qty) {
    }

    /** SHP-PK-R02：批次在出货仓的可用数量（ReservationApi 未上线，仅做校验） */
    private void checkBatches(Long warehouseId, List<Pick> picks) {
        Map<Long, Map<String, BigDecimal>> need = new LinkedHashMap<>();
        for (Pick p : picks) {
            if (p.qty().signum() <= 0) continue;
            need.computeIfAbsent(p.materialId(), k -> new LinkedHashMap<>()).merge(Objects.toString(ShpSupport.trim(p.batchNo()), ""), p.qty(), BigDecimal::add);
        }
        for (Map.Entry<Long, Map<String, BigDecimal>> e : need.entrySet()) {
            Map<String, BigDecimal> avail = available(e.getKey(), warehouseId);
            for (Map.Entry<String, BigDecimal> b : e.getValue().entrySet()) {
                BigDecimal a = avail.getOrDefault(b.getKey(), BigDecimal.ZERO);
                if (b.getValue().compareTo(a) > 0) throw BizException.of(ShippingErrorCodes.PK_BATCH_SHORT, b.getKey(), ShpSupport.plain(a));
            }
        }
    }

    private Map<String, BigDecimal> available(Long materialId, Long warehouseId) {
        Map<String, BigDecimal> map = new HashMap<>();
        for (BatchSuggestion s : inventoryQueryApi.suggestBatches(materialId, warehouseId, ALL)) {
            map.merge(Objects.toString(s.batchNo(), ""), s.qty(), BigDecimal::add);
        }
        return map;
    }

    /** 更换批次：出货仓该物料的可用批次 */
    public List<BatchOption> batchOptions(Long id, Long noticeLineId) {
        ShpPickingDO p = get(id);
        ShpNoticeLineDO nl = noticeLineMapper.selectById(noticeLineId);
        if (nl == null) throw BizException.of(ShippingErrorCodes.NOT_EXISTS, "通知行");
        return inventoryQueryApi.suggestBatches(nl.getMaterialId(), p.getWarehouseId(), ALL).stream()
                .map(s -> new BatchOption(s.batchNo(), s.locationId(), s.qty(), s.productionDate(), s.expireDate())).toList();
    }

    /**
     * 完成拣货（SHP-PK-R03）：每个通知行实拣合计 = 通知数量；acceptShort 时按实拣完成，差额记为缺货并释放回订单。
     * 未启用装箱时通知直接“已装箱”。
     */
    @Transactional(rollbackFor = Exception.class)
    public void complete(Long id, boolean acceptShort) {
        ShpPickingDO p = get(id);
        if (!PickingStatus.PICKING.name().equals(p.getPickingStatus())) {
            throw BizException.of(ShippingErrorCodes.STATUS_NOT_ALLOWED, PickingStatus.valueOf(p.getPickingStatus()).label(), "完成拣货");
        }
        ShpNoticeDO n = flow.get(p.getNoticeId());
        List<ShpPickingLineDO> lines = lineMapper.selectByParent(id);
        List<ShpNoticeLineDO> nls = noticeLineMapper.selectByParent(n.getId());
        Map<Long, BigDecimal> picked = new HashMap<>();
        for (ShpPickingLineDO l : lines) picked.merge(l.getNoticeLineId(), ShpSupport.nz(l.getPickedQty()), BigDecimal::add);
        if (picked.values().stream().noneMatch(q -> q.signum() > 0)) throw new BizException(ShippingErrorCodes.SH_NOTHING_TO_SHIP);
        Map<ShpNoticeLineDO, BigDecimal> shortages = new LinkedHashMap<>();
        for (ShpNoticeLineDO nl : nls) {
            BigDecimal q = Decimals.qty(picked.getOrDefault(nl.getId(), BigDecimal.ZERO));
            BigDecimal need = NoticeFlow.effectiveQty(nl);
            if (q.compareTo(need) > 0) throw BizException.of(ShippingErrorCodes.PK_PICK_EXCEED, nl.getLineNo(), ShpSupport.plain(need));
            if (q.compareTo(need) < 0) {
                if (!acceptShort) throw BizException.of(ShippingErrorCodes.PK_QTY_MISMATCH, nl.getLineNo(), ShpSupport.plain(q), ShpSupport.plain(need));
                shortages.put(nl, need.subtract(q));
            }
        }
        checkBatches(p.getWarehouseId(), lines.stream().map(l -> new Pick(l.getMaterialId(), l.getBatchNo(), ShpSupport.nz(l.getPickedQty()))).toList());
        boolean packing = support.params().getBool(ShippingModuleConfig.P_PACKING);
        for (ShpNoticeLineDO nl : nls) {
            BigDecimal q = Decimals.qty(picked.getOrDefault(nl.getId(), BigDecimal.ZERO));
            BigDecimal shortage = shortages.getOrDefault(nl, BigDecimal.ZERO);
            nl.setPickedQty(q);
            nl.setShortageQty(Decimals.qty(ShpSupport.nz(nl.getShortageQty()).add(shortage)));
            if (!packing) nl.setPackedQty(q);
            noticeLineMapper.updateByIdOrFail(nl);
        }
        p.setCompletedAt(LocalDateTime.now());
        String reason = shortages.isEmpty() ? null : "按实拣完成，缺货 " + shortages.entrySet().stream()
                .map(e -> "第 " + e.getKey().getLineNo() + " 行 " + ShpSupport.plain(e.getValue())).collect(Collectors.joining("、"));
        fire(p, ShpAction.COMPLETE_PICK, reason);
        n = flow.get(n.getId());
        if (!shortages.isEmpty()) {
            flow.release(n, shortages);
            support.message(List.of(n.getOwnerId()), "出货通知 " + n.getDocNo() + " 拣货缺货", reason + "，差额已释放回订单",
                    "/shipping/notice/" + n.getId());
        }
        if (!packing) {
            n = flow.get(n.getId());
            flow.fire(n, ShpAction.PACK, "未启用装箱，拣货完成即已装箱");
        }
    }

    private void fire(ShpPickingDO p, ShpAction action, String reason) {
        PickingStatus from = PickingStatus.valueOf(p.getPickingStatus());
        PickingStatus to = ShpStateMachines.PICKING.fire(from, action);
        p.setPickingStatus(to.name());
        p.setStatus(to.docStatus());
        mapper.updateByIdOrFail(p);
        support.log(BIZ_TYPE, p.getId(), p.getDocNo(), action.name(), action.label(), from.name(), to.name(), reason);
    }

    // ==================== 查询 ====================

    public ShpPickingDO get(Long id) {
        ShpPickingDO p = id == null ? null : mapper.selectById(id);
        if (p == null) throw BizException.of(ShippingErrorCodes.NOT_EXISTS, "拣货单");
        return p;
    }

    public PageResult<PickingRow> page(PickingQuery q) {
        LambdaQueryWrapper<ShpPickingDO> w = query(q);
        if (w == null) return new PageResult<>(List.of(), 0L);
        IPage<ShpPickingDO> page = mapper.selectPage(new Page<>(q.getPageNo(), q.getPageSize()), w);
        return new PageResult<>(rows(page.getRecords()), page.getTotal());
    }

    private LambdaQueryWrapper<ShpPickingDO> query(PickingQuery q) {
        List<String> statuses = new ArrayList<>();
        if (StringUtils.hasText(q.getStatuses())) {
            for (String s : q.getStatuses().split(",")) {
                if ("OPEN".equals(s)) statuses.addAll(List.of(PickingStatus.WAITING.name(), PickingStatus.PICKING.name()));
                else statuses.add(s.trim());
            }
        }
        List<Long> noticeIds = null;
        if (StringUtils.hasText(q.getNoticeNo()) || q.getCustomerId() != null || q.getShipDateFrom() != null || q.getShipDateTo() != null) {
            noticeIds = noticeMapper.selectList(new LambdaQueryWrapper<ShpNoticeDO>()
                            .like(StringUtils.hasText(q.getNoticeNo()), ShpNoticeDO::getDocNo, q.getNoticeNo())
                            .eq(q.getCustomerId() != null, ShpNoticeDO::getCustomerId, q.getCustomerId())
                            .ge(q.getShipDateFrom() != null, ShpNoticeDO::getShipDate, q.getShipDateFrom())
                            .le(q.getShipDateTo() != null, ShpNoticeDO::getShipDate, q.getShipDateTo()))
                    .stream().map(ShpNoticeDO::getId).toList();
            if (noticeIds.isEmpty()) return null;
        }
        return new LambdaQueryWrapper<ShpPickingDO>()
                .like(StringUtils.hasText(q.getDocNo()), ShpPickingDO::getDocNo, q.getDocNo())
                .in(noticeIds != null, ShpPickingDO::getNoticeId, noticeIds)
                .eq(q.getWarehouseId() != null, ShpPickingDO::getWarehouseId, q.getWarehouseId())
                .in(!statuses.isEmpty(), ShpPickingDO::getPickingStatus, statuses)
                .orderByDesc(ShpPickingDO::getId);
    }

    private List<PickingRow> rows(List<ShpPickingDO> list) {
        Map<Long, ShpNoticeDO> notices = byIds(list.stream().map(ShpPickingDO::getNoticeId).toList());
        Map<Long, CustomerDTO> cus = support.customers(notices.values().stream().map(ShpNoticeDO::getCustomerId).toList());
        Map<Long, UserDTO> users = support.users(list.stream().map(ShpPickingDO::getPickerId).toList());
        Map<Long, String> whs = new HashMap<>();
        support.warehouses(list.stream().map(ShpPickingDO::getWarehouseId).toList()).forEach((k, v) -> whs.put(k, v.name()));
        Map<Long, List<ShpPickingLineDO>> lines = lineMapper.selectByParents(list.stream().map(ShpPickingDO::getId).toList()).stream()
                .collect(Collectors.groupingBy(ShpPickingLineDO::getPickingId));
        return list.stream().map(p -> {
            ShpNoticeDO n = notices.get(p.getNoticeId());
            List<ShpPickingLineDO> ls = lines.getOrDefault(p.getId(), List.of());
            return new PickingRow(p.getId(), p.getDocNo(), p.getNoticeId(), n == null ? null : n.getDocNo(), n == null ? null : n.getCustomerId(),
                    n == null ? null : ShpSupport.customerName(cus.get(n.getCustomerId())), n == null ? null : n.getShipDate(), p.getWarehouseId(),
                    whs.get(p.getWarehouseId()), ls.size(), sum(ls, ShpPickingLineDO::getSuggestedQty), sum(ls, ShpPickingLineDO::getPickedQty),
                    p.getPickingStatus(), p.getPickerId(), ShpSupport.name(users, p.getPickerId()), p.getStartedAt(), p.getCompletedAt(), p.getCreatedAt());
        }).toList();
    }

    private Map<Long, ShpNoticeDO> byIds(List<Long> ids) {
        List<Long> set = ids.stream().filter(Objects::nonNull).distinct().toList();
        if (set.isEmpty()) return new HashMap<>();
        return noticeMapper.selectBatchIds(set).stream().collect(Collectors.toMap(ShpNoticeDO::getId, Function.identity()));
    }

    private static <T> BigDecimal sum(List<T> list, Function<T, BigDecimal> f) {
        return list.stream().map(f).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public PickingDetail detail(Long id) {
        ShpPickingDO p = get(id);
        ShpNoticeDO n = flow.get(p.getNoticeId());
        List<ShpPickingLineDO> lines = lineMapper.selectByParent(id);
        List<ShpNoticeLineDO> nls = noticeLineMapper.selectByParent(n.getId());
        Map<Long, ShpNoticeLineDO> nlById = nls.stream().collect(Collectors.toMap(ShpNoticeLineDO::getId, Function.identity()));
        Map<Long, MaterialDTO> ms = support.materials(nls.stream().map(ShpNoticeLineDO::getMaterialId).toList());
        List<PickingLineVO> lvs = lines.stream().map(l -> {
            MaterialDTO m = ms.get(l.getMaterialId());
            ShpNoticeLineDO nl = nlById.get(l.getNoticeLineId());
            return new PickingLineVO(l.getId(), l.getLineNo(), l.getNoticeLineId(), nl == null ? null : nl.getLineNo(), l.getMaterialId(),
                    m == null ? null : m.code(), m == null ? null : m.name(), m == null ? null : m.spec(), m == null ? null : m.baseUom(), l.getLocationId(),
                    l.getBatchNo(), l.getSuggestedQty(), l.getPickedQty(), l.getSerialNos(), Boolean.TRUE.equals(l.getShortage()));
        }).toList();
        Map<Long, BigDecimal> picked = new HashMap<>();
        for (ShpPickingLineDO l : lines) picked.merge(l.getNoticeLineId(), ShpSupport.nz(l.getPickedQty()), BigDecimal::add);
        List<NoticeLineSum> summary = nls.stream().map(nl -> {
            MaterialDTO m = ms.get(nl.getMaterialId());
            return new NoticeLineSum(nl.getId(), nl.getLineNo(), nl.getMaterialId(), m == null ? null : m.code(), m == null ? null : m.name(),
                    m == null ? null : m.baseUom(), nl.getBaseQty(), picked.getOrDefault(nl.getId(), BigDecimal.ZERO), nl.getShortageQty());
        }).toList();
        CustomerDTO c = support.customers(List.of(n.getCustomerId())).get(n.getCustomerId());
        return new PickingDetail(p.getId(), p.getDocNo(), p.getStatus().name(), p.getPickingStatus(), n.getId(), n.getDocNo(), n.getNoticeStatus(),
                n.getCustomerId(), ShpSupport.customerName(c), n.getShipDate(), p.getWarehouseId(), support.warehouseName(p.getWarehouseId()), p.getPickerId(),
                support.userName(p.getPickerId()), p.getStartedAt(), p.getCompletedAt(), p.getRemark(), lvs, summary, p.getCreatedAt());
    }

    public List<ShpPickingDO> byNotice(Long noticeId) {
        return mapper.selectList(new LambdaQueryWrapper<ShpPickingDO>().eq(ShpPickingDO::getNoticeId, noticeId).orderByAsc(ShpPickingDO::getId));
    }

    /** 打印：按库位排序的拣货清单 */
    public Map<String, Object> printData(Long id) {
        PickingDetail d = detail(id);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("docNo", d.docNo());
        m.put("noticeNo", d.noticeNo());
        m.put("customerName", d.customerName());
        m.put("warehouseName", d.warehouseName());
        m.put("shipDate", d.shipDate());
        m.put("lines", d.lines().stream().sorted(Comparator.comparing((PickingLineVO l) -> l.locationId() == null ? Long.MAX_VALUE : l.locationId())
                .thenComparing(PickingLineVO::lineNo)).map(l -> {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("locationCode", l.locationId() == null ? "" : String.valueOf(l.locationId()));
            r.put("materialCode", l.materialCode());
            r.put("materialName", l.materialName());
            r.put("batchNo", Objects.toString(l.batchNo(), ""));
            r.put("suggestedQty", l.suggestedQty());
            r.put("pickedQty", l.pickedQty());
            return r;
        }).toList());
        return m;
    }
}
