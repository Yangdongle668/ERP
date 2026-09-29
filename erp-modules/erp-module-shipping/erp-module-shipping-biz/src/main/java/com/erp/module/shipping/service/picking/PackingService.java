package com.erp.module.shipping.service.picking;

import com.erp.common.exception.BizException;
import com.erp.common.util.Decimals;
import com.erp.framework.mybatis.BaseDO;
import com.erp.module.crm.api.customer.CustomerDTO;
import com.erp.module.engineering.api.material.MaterialDTO;
import com.erp.module.engineering.api.material.MaterialStockAttr;
import com.erp.module.quality.api.inspection.InspectionApi;
import com.erp.module.quality.api.inspection.InspectionDTO;
import com.erp.module.quality.api.inspection.InspectionJudgedEvent;
import com.erp.module.quality.api.inspection.InspectionQueryApi;
import com.erp.module.sales.api.order.SalesOrderHeaderDTO;
import com.erp.module.sales.api.order.SalesOrderQueryApi;
import com.erp.module.shipping.api.ShippingErrorCodes;
import com.erp.module.shipping.config.ShippingModuleConfig;
import com.erp.module.shipping.controller.vo.PackingVOs.BatchPackReq;
import com.erp.module.shipping.controller.vo.PackingVOs.CartonLineSave;
import com.erp.module.shipping.controller.vo.PackingVOs.CartonLineVO;
import com.erp.module.shipping.controller.vo.PackingVOs.CartonSave;
import com.erp.module.shipping.controller.vo.PackingVOs.CartonVO;
import com.erp.module.shipping.controller.vo.PackingVOs.PackItem;
import com.erp.module.shipping.controller.vo.PackingVOs.PackingTotals;
import com.erp.module.shipping.controller.vo.PackingVOs.PackingView;
import com.erp.module.shipping.dal.dataobject.ShpCartonDO;
import com.erp.module.shipping.dal.dataobject.ShpCartonLineDO;
import com.erp.module.shipping.dal.dataobject.ShpNoticeDO;
import com.erp.module.shipping.dal.dataobject.ShpNoticeLineDO;
import com.erp.module.shipping.dal.dataobject.ShpPickingDO;
import com.erp.module.shipping.dal.dataobject.ShpPickingLineDO;
import com.erp.module.shipping.dal.dataobject.ShpShipmentDO;
import com.erp.module.shipping.dal.dataobject.ShpShipmentLineDO;
import com.erp.module.shipping.dal.mapper.ShpCartonLineMapper;
import com.erp.module.shipping.dal.mapper.ShpCartonMapper;
import com.erp.module.shipping.dal.mapper.ShpNoticeLineMapper;
import com.erp.module.shipping.dal.mapper.ShpPickingLineMapper;
import com.erp.module.shipping.dal.mapper.ShpShipmentLineMapper;
import com.erp.module.shipping.dal.mapper.ShpShipmentMapper;
import com.erp.module.shipping.service.NoticeStatus;
import com.erp.module.shipping.service.PickingStatus;
import com.erp.module.shipping.service.ShipmentStatus;
import com.erp.module.shipping.service.ShpAction;
import com.erp.module.shipping.service.ShpSupport;
import com.erp.module.shipping.service.notice.NoticeFlow;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 装箱与 OQC（11-02 第 3.2、4 节；11-01 申请 OQC） */
@Service
public class PackingService {

    private static final BigDecimal MILLION = new BigDecimal("1000000");
    public static final String OQC_PENDING = "PENDING";
    public static final String OQC_PASSED = "PASSED";
    public static final String OQC_REJECTED = "REJECTED";

    private final ShpCartonMapper cartonMapper;
    private final ShpCartonLineMapper cartonLineMapper;
    private final ShpNoticeLineMapper noticeLineMapper;
    private final ShpPickingLineMapper pickingLineMapper;
    private final ShpShipmentMapper shipmentMapper;
    private final ShpShipmentLineMapper shipmentLineMapper;
    private final InspectionApi inspectionApi;
    private final InspectionQueryApi inspectionQueryApi;
    private final SalesOrderQueryApi orderQueryApi;
    private final NoticeFlow flow;
    private final ShpSupport support;

    public PackingService(ShpCartonMapper cartonMapper, ShpCartonLineMapper cartonLineMapper, ShpNoticeLineMapper noticeLineMapper,
                          ShpPickingLineMapper pickingLineMapper, ShpShipmentMapper shipmentMapper, ShpShipmentLineMapper shipmentLineMapper,
                          InspectionApi inspectionApi, InspectionQueryApi inspectionQueryApi, SalesOrderQueryApi orderQueryApi, NoticeFlow flow,
                          ShpSupport support) {
        this.cartonMapper = cartonMapper;
        this.cartonLineMapper = cartonLineMapper;
        this.noticeLineMapper = noticeLineMapper;
        this.pickingLineMapper = pickingLineMapper;
        this.shipmentMapper = shipmentMapper;
        this.shipmentLineMapper = shipmentLineMapper;
        this.inspectionApi = inspectionApi;
        this.inspectionQueryApi = inspectionQueryApi;
        this.orderQueryApi = orderQueryApi;
        this.flow = flow;
        this.support = support;
    }

    // ==================== 可装 / 可出货数量 ====================

    /** 通知行 + 批次 → 实拣数量（未启用拣货时取通知有效数量，批次为空） */
    public Map<String, BigDecimal> picked(ShpNoticeDO n) {
        Map<String, BigDecimal> map = new LinkedHashMap<>();
        ShpPickingDO p = flow.activePicking(n.getId());
        if (p != null) {
            if (!PickingStatus.DONE.name().equals(p.getPickingStatus())) return map;
            for (ShpPickingLineDO l : pickingLineMapper.selectByParent(p.getId())) {
                if (ShpSupport.nz(l.getPickedQty()).signum() > 0) map.merge(ShpSupport.batchKey(l.getNoticeLineId(), l.getBatchNo()), l.getPickedQty(), BigDecimal::add);
            }
            return map;
        }
        if (NoticeFlow.status(n) == NoticeStatus.DRAFT || NoticeFlow.status(n) == NoticeStatus.PENDING) return map;
        for (ShpNoticeLineDO l : noticeLineMapper.selectByParent(n.getId())) map.put(ShpSupport.batchKey(l.getId(), null), NoticeFlow.effectiveQty(l));
        return map;
    }

    /** 已装箱数量（通知行 + 批次） */
    private Map<String, BigDecimal> packed(Long noticeId) {
        Map<String, BigDecimal> map = new HashMap<>();
        for (ShpCartonLineDO l : cartonLineMapper.selectByNotice(noticeId)) map.merge(ShpSupport.batchKey(l.getNoticeLineId(), l.getBatchNo()), l.getQty(), BigDecimal::add);
        return map;
    }

    /** 可出货单元：通知行 + 批次 + 数量 + 所在箱（出货单生成时使用） */
    public record Unit(Long noticeLineId, String batchNo, BigDecimal qty, List<Long> cartonIds) {
    }

    /**
     * 未出货的数量：启用装箱时为未出货箱（cartonIds 为空表示全部未出货箱）；未启用装箱时为实拣数量减去有效出货单已占用的数量。
     */
    public List<Unit> shippable(ShpNoticeDO n, List<Long> cartonIds) {
        boolean packing = support.params().getBool(ShippingModuleConfig.P_PACKING);
        List<ShpCartonDO> cartons = cartonMapper.selectByNotice(n.getId());
        if (packing && !cartons.isEmpty()) {
            Set<Long> wanted = cartonIds == null || cartonIds.isEmpty() ? null : new HashSet<>(cartonIds);
            List<ShpCartonDO> use = cartons.stream().filter(c -> c.getShipmentId() == null && (wanted == null || wanted.contains(c.getId()))).toList();
            if (wanted != null) {
                for (ShpCartonDO c : cartons) {
                    if (wanted.contains(c.getId()) && c.getShipmentId() != null) throw BizException.of(ShippingErrorCodes.PK_CARTON_SHIPPED, c.getCartonNo());
                }
            }
            Map<Long, ShpCartonDO> byId = use.stream().collect(Collectors.toMap(BaseDO::getId, Function.identity()));
            Map<String, Unit> units = new LinkedHashMap<>();
            for (ShpCartonLineDO l : cartonLineMapper.selectByParents(byId.keySet())) {
                String key = ShpSupport.batchKey(l.getNoticeLineId(), l.getBatchNo());
                Unit u = units.get(key);
                List<Long> ids = u == null ? new ArrayList<>() : new ArrayList<>(u.cartonIds());
                if (!ids.contains(l.getCartonId())) ids.add(l.getCartonId());
                units.put(key, new Unit(l.getNoticeLineId(), l.getBatchNo(), (u == null ? BigDecimal.ZERO : u.qty()).add(l.getQty()), ids));
            }
            return new ArrayList<>(units.values());
        }
        Map<String, BigDecimal> used = new HashMap<>();
        List<ShpShipmentDO> ships = shipmentMapper.selectList(new LambdaQueryWrapper<ShpShipmentDO>().eq(ShpShipmentDO::getNoticeId, n.getId())
                .ne(ShpShipmentDO::getShipmentStatus, ShipmentStatus.VOIDED.name()));
        for (ShpShipmentLineDO l : shipmentLineMapper.selectByParents(ships.stream().map(BaseDO::getId).toList())) {
            used.merge(ShpSupport.batchKey(l.getNoticeLineId(), l.getBatchNo()), l.getBaseQty(), BigDecimal::add);
        }
        List<Unit> list = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> e : picked(n).entrySet()) {
            BigDecimal left = e.getValue().subtract(used.getOrDefault(e.getKey(), BigDecimal.ZERO));
            if (left.signum() <= 0) continue;
            String[] k = e.getKey().split("\\|", -1);
            list.add(new Unit(Long.valueOf(k[0]), k[1].isEmpty() ? null : k[1], left, List.of()));
        }
        return list;
    }

    // ==================== 装箱 ====================

    public PackingView view(Long noticeId) {
        ShpNoticeDO n = flow.get(noticeId);
        List<ShpNoticeLineDO> nls = noticeLineMapper.selectByParent(noticeId);
        Map<Long, ShpNoticeLineDO> nlById = nls.stream().collect(Collectors.toMap(BaseDO::getId, Function.identity()));
        Map<Long, MaterialDTO> ms = support.materials(nls.stream().map(ShpNoticeLineDO::getMaterialId).toList());
        Map<String, BigDecimal> picked = picked(n);
        Map<String, BigDecimal> packed = packed(noticeId);
        List<PackItem> items = new ArrayList<>();
        Map<Long, MaterialStockAttr> attrs = new HashMap<>();
        for (Map.Entry<String, BigDecimal> e : picked.entrySet()) {
            String[] k = e.getKey().split("\\|", -1);
            ShpNoticeLineDO nl = nlById.get(Long.valueOf(k[0]));
            if (nl == null) continue;
            MaterialDTO m = ms.get(nl.getMaterialId());
            MaterialStockAttr a = attrs.computeIfAbsent(nl.getMaterialId(), id -> support.materialApi().getStockAttr(id));
            BigDecimal p = packed.getOrDefault(e.getKey(), BigDecimal.ZERO);
            items.add(new PackItem(nl.getId(), nl.getLineNo(), nl.getMaterialId(), m == null ? null : m.code(), m == null ? null : m.name(),
                    nl.getCustomerPartNo(), k[1].isEmpty() ? null : k[1], m == null ? nl.getUom() : m.baseUom(), e.getValue(), p, e.getValue().subtract(p),
                    a == null ? null : a.unitNetWeight(), a == null ? null : a.unitGrossWeight()));
        }
        List<CartonVO> cartons = cartonVOs(cartonMapper.selectByNotice(noticeId), nlById, ms);
        CustomerDTO c = support.customers(List.of(n.getCustomerId())).get(n.getCustomerId());
        boolean complete = !items.isEmpty() && items.stream().allMatch(i -> i.remainingQty().signum() == 0);
        return new PackingView(n.getId(), n.getDocNo(), n.getNoticeStatus(), n.getCustomerId(), ShpSupport.customerName(c), n.getShipDate(),
                Boolean.TRUE.equals(n.getOqcRequired()), n.getOqcResult(), support.params().getBool(ShippingModuleConfig.P_PACKING), editable(n), complete,
                items, cartons, totals(cartons));
    }

    private List<CartonVO> cartonVOs(List<ShpCartonDO> cartons, Map<Long, ShpNoticeLineDO> nlById, Map<Long, MaterialDTO> ms) {
        Map<Long, List<ShpCartonLineDO>> lines = cartonLineMapper.selectByParents(cartons.stream().map(BaseDO::getId).toList()).stream()
                .collect(Collectors.groupingBy(ShpCartonLineDO::getCartonId));
        Map<Long, String> shipNos = new HashMap<>();
        List<Long> sids = cartons.stream().map(ShpCartonDO::getShipmentId).filter(Objects::nonNull).distinct().toList();
        if (!sids.isEmpty()) shipmentMapper.selectBatchIds(sids).forEach(s -> shipNos.put(s.getId(), s.getDocNo()));
        return cartons.stream().map(c -> new CartonVO(c.getId(), c.getCartonNo(), c.getCartonSpec(), c.getLengthCm(), c.getWidthCm(), c.getHeightCm(),
                c.getGrossWeightKg(), c.getNetWeightKg(), c.getCbm(), c.getPalletNo(), c.getShipmentId(), shipNos.get(c.getShipmentId()),
                lines.getOrDefault(c.getId(), List.of()).stream().map(l -> {
                    ShpNoticeLineDO nl = nlById.get(l.getNoticeLineId());
                    MaterialDTO m = ms.get(l.getMaterialId());
                    return new CartonLineVO(l.getId(), l.getNoticeLineId(), nl == null ? null : nl.getLineNo(), l.getMaterialId(), m == null ? null : m.code(),
                            m == null ? null : m.name(), nl == null ? null : nl.getCustomerPartNo(), l.getBatchNo(), l.getQty(), l.getSerialNos());
                }).toList())).toList();
    }

    public static PackingTotals totals(List<CartonVO> cartons) {
        BigDecimal qty = BigDecimal.ZERO;
        BigDecimal gw = BigDecimal.ZERO;
        BigDecimal nw = BigDecimal.ZERO;
        BigDecimal cbm = BigDecimal.ZERO;
        for (CartonVO c : cartons) {
            qty = qty.add(c.lines().stream().map(CartonLineVO::qty).reduce(BigDecimal.ZERO, BigDecimal::add));
            gw = gw.add(ShpSupport.nz(c.grossWeightKg()));
            nw = nw.add(ShpSupport.nz(c.netWeightKg()));
            cbm = cbm.add(ShpSupport.nz(c.cbm()));
        }
        return new PackingTotals(cartons.size(), qty, gw, nw, cbm);
    }

    private boolean editable(ShpNoticeDO n) {
        if (!support.params().getBool(ShippingModuleConfig.P_PACKING)) return false;
        NoticeStatus st = NoticeFlow.status(n);
        if (st == NoticeStatus.PICKING) {
            ShpPickingDO p = flow.activePicking(n.getId());
            return p != null && PickingStatus.DONE.name().equals(p.getPickingStatus());
        }
        return st == NoticeStatus.PACKED || st == NoticeStatus.OQC || st == NoticeStatus.READY;
    }

    /** 装箱变更前：未完成拣货不能装箱；已完成装箱 / 已申请 OQC 的通知退回“拣货中”并取消 OQC（SHP-PK-R05） */
    private ShpNoticeDO beforeChange(Long noticeId) {
        ShpNoticeDO n = flow.get(noticeId);
        if (!editable(n)) {
            NoticeStatus st = NoticeFlow.status(n);
            if (st == NoticeStatus.PICKING || st == NoticeStatus.APPROVED) throw new BizException(ShippingErrorCodes.PK_NOT_PICKED);
            throw BizException.of(ShippingErrorCodes.STATUS_NOT_ALLOWED, st.label(), "修改装箱");
        }
        NoticeStatus st = NoticeFlow.status(n);
        if (st != NoticeStatus.PICKING) {
            if (st == NoticeStatus.OQC || OQC_PASSED.equals(n.getOqcResult())) inspectionApi.cancelOqc(noticeId);
            n.setOqcResult(null);
            n.setOqcInspectionIds(null);
            flow.fire(n, ShpAction.UNPACK, st == NoticeStatus.PICKING ? null : "修改装箱，需重新完成装箱" + (Boolean.TRUE.equals(n.getOqcRequired()) ? "并重新申请 OQC" : ""));
            n = flow.get(noticeId);
        }
        return n;
    }

    /** 按规格批量装箱：自动生成连续箱号，尾箱为余数（SHP-PK-T03） */
    @Transactional(rollbackFor = Exception.class)
    public List<Long> batchPack(Long noticeId, BatchPackReq req) {
        ShpNoticeDO n = beforeChange(noticeId);
        String batch = ShpSupport.trim(req.batchNo());
        String key = ShpSupport.batchKey(req.noticeLineId(), batch);
        BigDecimal remaining = picked(n).getOrDefault(key, BigDecimal.ZERO).subtract(packed(noticeId).getOrDefault(key, BigDecimal.ZERO));
        ShpNoticeLineDO nl = noticeLine(noticeId, req.noticeLineId());
        String label = support.material(nl.getMaterialId()).code() + (batch == null ? "" : " 批次 " + batch);
        BigDecimal total = req.totalQty() == null ? remaining : Decimals.qty(req.totalQty());
        if (req.qtyPerCarton().signum() <= 0 || total.signum() <= 0) throw BizException.of(ShippingErrorCodes.SN_QTY_INVALID, nl.getLineNo());
        if (total.compareTo(remaining) > 0) throw BizException.of(ShippingErrorCodes.PK_PACK_EXCEED, label, ShpSupport.plain(remaining));
        MaterialStockAttr attr = support.materialApi().getStockAttr(nl.getMaterialId());
        int next = nextCartonNo(noticeId);
        List<Long> ids = new ArrayList<>();
        BigDecimal left = total;
        while (left.signum() > 0) {
            BigDecimal q = left.min(req.qtyPerCarton());
            ShpCartonDO c = new ShpCartonDO();
            c.setNoticeId(noticeId);
            c.setCartonNo(next++);
            applySpec(c, req.cartonSpec(), req.lengthCm(), req.widthCm(), req.heightCm(), req.palletNo());
            BigDecimal net = attr == null || attr.unitNetWeight() == null ? null : weight(attr.unitNetWeight().multiply(q));
            c.setNetWeightKg(net);
            c.setGrossWeightKg(gross(net, req.tareWeightKg(), attr == null ? null : attr.unitGrossWeight(), q));
            cartonMapper.insert(c);
            ShpCartonLineDO l = new ShpCartonLineDO();
            l.setCartonId(c.getId());
            l.setNoticeId(noticeId);
            l.setNoticeLineId(nl.getId());
            l.setMaterialId(nl.getMaterialId());
            l.setBatchNo(batch);
            l.setQty(Decimals.qty(q));
            cartonLineMapper.insert(l);
            ids.add(c.getId());
            left = left.subtract(q);
        }
        support.log(ShippingModuleConfig.NOTICE, noticeId, n.getDocNo(), "PACK_BATCH", "批量装箱", null, null,
                label + " " + ShpSupport.plain(total) + "，" + ids.size() + " 箱");
        return ids;
    }

    /** 手工新增箱（可混装） */
    @Transactional(rollbackFor = Exception.class)
    public Long addCarton(Long noticeId, CartonSave req) {
        ShpNoticeDO n = beforeChange(noticeId);
        ShpCartonDO c = new ShpCartonDO();
        c.setNoticeId(noticeId);
        c.setCartonNo(nextCartonNo(noticeId));
        saveCarton(n, c, req, Map.of());
        return c.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateCarton(Long cartonId, CartonSave req) {
        ShpCartonDO c = carton(cartonId);
        if (c.getShipmentId() != null) throw BizException.of(ShippingErrorCodes.PK_CARTON_SHIPPED, c.getCartonNo());
        ShpNoticeDO n = beforeChange(c.getNoticeId());
        Map<String, BigDecimal> old = new HashMap<>();
        for (ShpCartonLineDO l : cartonLineMapper.selectByParent(cartonId)) old.merge(ShpSupport.batchKey(l.getNoticeLineId(), l.getBatchNo()), l.getQty(), BigDecimal::add);
        saveCarton(n, carton(cartonId), req, old);
    }

    private void saveCarton(ShpNoticeDO n, ShpCartonDO c, CartonSave req, Map<String, BigDecimal> old) {
        Map<String, BigDecimal> picked = picked(n);
        Map<String, BigDecimal> packed = packed(n.getId());
        Map<String, BigDecimal> want = new LinkedHashMap<>();
        BigDecimal net = BigDecimal.ZERO;
        boolean netKnown = true;
        List<ShpCartonLineDO> lines = new ArrayList<>();
        for (CartonLineSave s : req.lines()) {
            ShpNoticeLineDO nl = noticeLine(n.getId(), s.noticeLineId());
            if (s.qty().signum() <= 0) throw BizException.of(ShippingErrorCodes.SN_QTY_INVALID, nl.getLineNo());
            String batch = ShpSupport.trim(s.batchNo());
            String key = ShpSupport.batchKey(nl.getId(), batch);
            want.merge(key, s.qty(), BigDecimal::add);
            BigDecimal can = picked.getOrDefault(key, BigDecimal.ZERO).subtract(packed.getOrDefault(key, BigDecimal.ZERO)).add(old.getOrDefault(key, BigDecimal.ZERO));
            if (want.get(key).compareTo(can) > 0) {
                throw BizException.of(ShippingErrorCodes.PK_PACK_EXCEED, support.material(nl.getMaterialId()).code() + (batch == null ? "" : " 批次 " + batch),
                        ShpSupport.plain(can.max(BigDecimal.ZERO)));
            }
            MaterialStockAttr a = support.materialApi().getStockAttr(nl.getMaterialId());
            if (a == null || a.unitNetWeight() == null) netKnown = false;
            else net = net.add(a.unitNetWeight().multiply(s.qty()));
            ShpCartonLineDO l = new ShpCartonLineDO();
            l.setNoticeId(n.getId());
            l.setNoticeLineId(nl.getId());
            l.setMaterialId(nl.getMaterialId());
            l.setBatchNo(batch);
            l.setQty(Decimals.qty(s.qty()));
            l.setSerialNos(ShpSupport.trim(s.serialNos()));
            lines.add(l);
        }
        applySpec(c, req.cartonSpec(), req.lengthCm(), req.widthCm(), req.heightCm(), req.palletNo());
        BigDecimal netW = req.netWeightKg() != null ? weight(req.netWeightKg()) : netKnown ? weight(net) : null;
        c.setNetWeightKg(netW);
        c.setGrossWeightKg(req.grossWeightKg() != null ? weight(req.grossWeightKg()) : gross(netW, req.tareWeightKg(), null, null));
        if (c.getId() == null) cartonMapper.insert(c);
        else {
            cartonMapper.updateByIdOrFail(c);
            cartonLineMapper.deleteByParent(c.getId());
        }
        for (ShpCartonLineDO l : lines) {
            l.setCartonId(c.getId());
            cartonLineMapper.insert(l);
        }
    }

    /** 删除箱；没有已出货箱时箱号重新连续编号 */
    @Transactional(rollbackFor = Exception.class)
    public void deleteCarton(Long cartonId) {
        ShpCartonDO c = carton(cartonId);
        if (c.getShipmentId() != null) throw BizException.of(ShippingErrorCodes.PK_CARTON_SHIPPED, c.getCartonNo());
        beforeChange(c.getNoticeId());
        cartonLineMapper.deleteByParent(cartonId);
        cartonMapper.deleteById(cartonId);
        List<ShpCartonDO> rest = cartonMapper.selectByNotice(c.getNoticeId());
        if (rest.stream().noneMatch(x -> x.getShipmentId() != null)) {
            int no = 1;
            for (ShpCartonDO x : rest) {
                if (x.getCartonNo() != no) {
                    x.setCartonNo(no);
                    cartonMapper.updateByIdOrFail(x);
                }
                no++;
            }
        }
    }

    /** 清空未出货的箱 */
    @Transactional(rollbackFor = Exception.class)
    public void clearCartons(Long noticeId) {
        beforeChange(noticeId);
        for (ShpCartonDO c : cartonMapper.selectByNotice(noticeId)) {
            if (c.getShipmentId() != null) continue;
            cartonLineMapper.deleteByParent(c.getId());
            cartonMapper.deleteById(c.getId());
        }
    }

    /** SHP-PK-R04：每个物料 + 批次的装箱数量 = 实拣数量 → 已装箱；需要 OQC 时可一并申请 */
    @Transactional(rollbackFor = Exception.class)
    public void packComplete(Long noticeId, boolean requestOqc) {
        ShpNoticeDO n = flow.get(noticeId);
        if (NoticeFlow.status(n) != NoticeStatus.PICKING || !editable(n)) {
            NoticeStatus st = NoticeFlow.status(n);
            if (st == NoticeStatus.PICKING || st == NoticeStatus.APPROVED) throw new BizException(ShippingErrorCodes.PK_NOT_PICKED);
            throw BizException.of(ShippingErrorCodes.STATUS_NOT_ALLOWED, st.label(), "完成装箱");
        }
        Map<String, BigDecimal> picked = picked(n);
        Map<String, BigDecimal> packed = packed(noticeId);
        Set<String> keys = new HashSet<>(picked.keySet());
        keys.addAll(packed.keySet());
        List<ShpNoticeLineDO> nls = noticeLineMapper.selectByParent(noticeId);
        Map<Long, ShpNoticeLineDO> nlById = nls.stream().collect(Collectors.toMap(BaseDO::getId, Function.identity()));
        for (String k : keys) {
            BigDecimal a = picked.getOrDefault(k, BigDecimal.ZERO);
            BigDecimal b = packed.getOrDefault(k, BigDecimal.ZERO);
            if (a.compareTo(b) != 0) {
                String[] p = k.split("\\|", -1);
                ShpNoticeLineDO nl = nlById.get(Long.valueOf(p[0]));
                String code = nl == null ? p[0] : support.material(nl.getMaterialId()).code();
                throw BizException.of(ShippingErrorCodes.PK_PACK_MISMATCH, code + (p[1].isEmpty() ? "" : " 批次 " + p[1]) + "（装箱 " + ShpSupport.plain(b)
                        + " / 拣货 " + ShpSupport.plain(a) + "）");
            }
        }
        Map<Long, BigDecimal> perLine = new HashMap<>();
        packed.forEach((k, v) -> perLine.merge(Long.valueOf(k.split("\\|", -1)[0]), v, BigDecimal::add));
        for (ShpNoticeLineDO nl : nls) {
            nl.setPackedQty(Decimals.qty(perLine.getOrDefault(nl.getId(), BigDecimal.ZERO)));
            noticeLineMapper.updateByIdOrFail(nl);
        }
        flow.fire(flow.get(noticeId), ShpAction.PACK, cartonMapper.selectByNotice(noticeId).size() + " 箱");
        if (requestOqc && Boolean.TRUE.equals(n.getOqcRequired())) requestOqc(noticeId);
    }

    // ==================== OQC ====================

    /** 申请 OQC：按通知行 + 批次生成出货检验单（未出货的数量） */
    @Transactional(rollbackFor = Exception.class)
    public void requestOqc(Long noticeId) {
        ShpNoticeDO n = flow.get(noticeId);
        if (!Boolean.TRUE.equals(n.getOqcRequired())) throw new BizException(ShippingErrorCodes.PK_OQC_NOT_REQUIRED);
        NoticeStatus st = NoticeFlow.status(n);
        if (st != NoticeStatus.PACKED) throw BizException.of(ShippingErrorCodes.STATUS_NOT_ALLOWED, st.label(), "申请 OQC");
        Map<Long, ShpNoticeLineDO> nls = noticeLineMapper.selectByParent(noticeId).stream().collect(Collectors.toMap(BaseDO::getId, Function.identity()));
        List<InspectionApi.OqcRequest.Line> lines = new ArrayList<>();
        for (Unit u : shippable(n, null)) {
            ShpNoticeLineDO nl = nls.get(u.noticeLineId());
            if (nl == null || !Boolean.TRUE.equals(nl.getOqcRequired())) continue;
            lines.add(new InspectionApi.OqcRequest.Line(nl.getId(), nl.getMaterialId(), u.batchNo(), u.qty()));
        }
        if (lines.isEmpty()) throw new BizException(ShippingErrorCodes.SH_NOTHING_TO_SHIP);
        List<Long> ids = inspectionApi.requestOqc(new InspectionApi.OqcRequest(n.getId(), n.getDocNo(), n.getCustomerId(), n.getWarehouseId(), lines));
        n = flow.get(noticeId);
        n.setOqcInspectionIds(ShpSupport.idText(ids));
        n.setOqcResult(OQC_PENDING);
        flow.fire(n, ShpAction.REQUEST_OQC, ids.size() + " 张检验单");
    }

    /** OQC 判定：本轮检验全部判定后，合格（含特采）→ 待出货；任一不合格 → 退回已装箱，提醒船务 */
    @EventListener
    public void onInspectionJudged(InspectionJudgedEvent e) {
        if (!"OQC".equals(e.getInspectType()) || !ShippingModuleConfig.NOTICE.equals(e.getUpstreamType()) || e.getUpstreamId() == null) return;
        ShpNoticeDO n = flow.find(e.getUpstreamId());
        if (n == null || NoticeFlow.status(n) != NoticeStatus.OQC) return;
        List<Long> ids = ShpSupport.ids(n.getOqcInspectionIds());
        if (!ids.contains(e.getInspectionId())) return;
        List<InspectionDTO> list = inspectionQueryApi.getByBiz(ShippingModuleConfig.NOTICE, n.getId()).stream().filter(d -> ids.contains(d.id())).toList();
        if (list.stream().anyMatch(d -> d.result() == null)) return;
        boolean rejected = list.stream().anyMatch(d -> OQC_REJECTED.equals(d.result()));
        n.setOqcResult(rejected ? OQC_REJECTED : OQC_PASSED);
        String docs = list.stream().map(InspectionDTO::docNo).collect(Collectors.joining("、"));
        flow.fire(n, rejected ? ShpAction.OQC_REJECT : ShpAction.OQC_PASS, docs);
        support.message(List.of(n.getOwnerId()), "出货通知 " + n.getDocNo() + (rejected ? " OQC 不合格" : " OQC 合格"),
                rejected ? "检验单 " + docs + " 判定不合格，请处理后重新装箱 / 申请 OQC" : "可生成出货单", "/shipping/notice/" + n.getId());
    }

    // ==================== 箱唛 ====================

    /** 箱唛：每箱一张（客户、PO、料号、数量、毛净重、箱号 n/N） */
    public Map<String, Object> labels(Long noticeId) {
        ShpNoticeDO n = flow.get(noticeId);
        List<ShpNoticeLineDO> nls = noticeLineMapper.selectByParent(noticeId);
        Map<Long, ShpNoticeLineDO> nlById = nls.stream().collect(Collectors.toMap(BaseDO::getId, Function.identity()));
        Map<Long, MaterialDTO> ms = support.materials(nls.stream().map(ShpNoticeLineDO::getMaterialId).toList());
        Map<Long, SalesOrderHeaderDTO> heads = orderQueryApi.getOrderHeaders(nls.stream().map(ShpNoticeLineDO::getOrderId).distinct().toList());
        List<CartonVO> cartons = cartonVOs(cartonMapper.selectByNotice(noticeId), nlById, ms);
        CustomerDTO c = support.customer(n.getCustomerId());
        String customer = StringUtils.hasText(c.nameEn()) ? c.nameEn() : c.name();
        int total = cartons.size();
        List<Map<String, Object>> list = new ArrayList<>();
        for (CartonVO cv : cartons) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("customerName", customer);
            m.put("poNo", cv.lines().stream().map(l -> nlById.get(l.noticeLineId())).filter(Objects::nonNull)
                    .map(nl -> heads.get(nl.getOrderId())).filter(Objects::nonNull).map(h -> Objects.toString(h.customerPoNo(), h.orderNo())).distinct()
                    .collect(Collectors.joining(" / ")));
            m.put("partNo", cv.lines().stream().map(l -> StringUtils.hasText(l.customerPartNo()) ? l.customerPartNo() : l.materialCode()).distinct()
                    .collect(Collectors.joining(" / ")));
            m.put("description", cv.lines().stream().map(l -> {
                ShpNoticeLineDO nl = nlById.get(l.noticeLineId());
                MaterialDTO md = ms.get(l.materialId());
                return nl != null && StringUtils.hasText(nl.getDescription()) ? nl.getDescription() : md == null ? "" : Objects.toString(md.nameEn(), md.name());
            }).distinct().collect(Collectors.joining(" / ")));
            m.put("qty", cv.lines().stream().map(CartonLineVO::qty).reduce(BigDecimal.ZERO, BigDecimal::add));
            m.put("batchNo", cv.lines().stream().map(CartonLineVO::batchNo).filter(Objects::nonNull).distinct().collect(Collectors.joining(" / ")));
            m.put("grossWeight", cv.grossWeightKg());
            m.put("netWeight", cv.netWeightKg());
            m.put("cartonText", cv.cartonNo() + "/" + total);
            m.put("origin", "MADE IN CHINA");
            list.add(m);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("noticeNo", n.getDocNo());
        data.put("cartons", list);
        return data;
    }

    // ==================== 工具 ====================

    private ShpCartonDO carton(Long id) {
        ShpCartonDO c = id == null ? null : cartonMapper.selectById(id);
        if (c == null) throw BizException.of(ShippingErrorCodes.NOT_EXISTS, "箱");
        return c;
    }

    private ShpNoticeLineDO noticeLine(Long noticeId, Long lineId) {
        ShpNoticeLineDO l = lineId == null ? null : noticeLineMapper.selectById(lineId);
        if (l == null || !l.getNoticeId().equals(noticeId)) throw BizException.of(ShippingErrorCodes.NOT_EXISTS, "通知行");
        return l;
    }

    private int nextCartonNo(Long noticeId) {
        return cartonMapper.selectByNotice(noticeId).stream().mapToInt(ShpCartonDO::getCartonNo).max().orElse(0) + 1;
    }

    private static void applySpec(ShpCartonDO c, String spec, BigDecimal l, BigDecimal w, BigDecimal h, String pallet) {
        c.setCartonSpec(ShpSupport.trim(spec));
        c.setLengthCm(l);
        c.setWidthCm(w);
        c.setHeightCm(h);
        c.setCbm(l == null || w == null || h == null ? null : l.multiply(w).multiply(h).divide(MILLION, 4, RoundingMode.HALF_UP));
        c.setPalletNo(ShpSupport.limit(pallet, 16));
    }

    private static BigDecimal weight(BigDecimal v) {
        return v == null ? null : v.setScale(3, RoundingMode.HALF_UP);
    }

    /** 毛重 = 净重 + 箱皮重；没有皮重时按物料单位毛重，否则同净重 */
    private static BigDecimal gross(BigDecimal net, BigDecimal tare, BigDecimal unitGross, BigDecimal qty) {
        if (net != null && tare != null) return weight(net.add(tare));
        if (unitGross != null && qty != null) return weight(unitGross.multiply(qty));
        return net;
    }
}
