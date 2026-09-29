package com.erp.module.shipping.service.notice;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.common.exception.BizException;
import com.erp.common.result.PageResult;
import com.erp.common.util.Decimals;
import com.erp.module.crm.api.credit.CreditApi;
import com.erp.module.crm.api.credit.CreditCheckPoint;
import com.erp.module.crm.api.credit.CreditCheckResult;
import com.erp.module.crm.api.customer.AddressDTO;
import com.erp.module.crm.api.customer.CustomerDTO;
import com.erp.module.crm.api.customer.CustomerStatus;
import com.erp.module.crm.api.customer.CustomerStatusChangedEvent;
import com.erp.module.engineering.api.material.MaterialDTO;
import com.erp.module.inventory.api.stock.InventoryQueryApi;
import com.erp.module.inventory.api.warehouse.WarehouseDTO;
import com.erp.module.inventory.api.warehouse.WarehouseType;
import com.erp.module.pmc.api.shipping.ShippingPlanApi;
import com.erp.module.pmc.api.shipping.ShippingPlanLineDTO;
import com.erp.module.pmc.api.shipping.ShippingPlanPublishedEvent;
import com.erp.module.quality.api.inspection.InspectionApi;
import com.erp.module.sales.api.order.OpenLineFilter;
import com.erp.module.sales.api.order.SalesOrderHeaderDTO;
import com.erp.module.sales.api.order.SalesOrderLineDTO;
import com.erp.module.sales.api.order.SalesOrderQueryApi;
import com.erp.module.shipping.api.ShippingErrorCodes;
import com.erp.module.shipping.config.ShippingModuleConfig;
import com.erp.module.shipping.controller.vo.NoticeVOs.AddressOption;
import com.erp.module.shipping.controller.vo.NoticeVOs.CustomerDefaults;
import com.erp.module.shipping.controller.vo.NoticeVOs.FromOrdersReq;
import com.erp.module.shipping.controller.vo.NoticeVOs.FromPlanReq;
import com.erp.module.shipping.controller.vo.NoticeVOs.LinkedDoc;
import com.erp.module.shipping.controller.vo.NoticeVOs.NoticeDetail;
import com.erp.module.shipping.controller.vo.NoticeVOs.NoticeLineSave;
import com.erp.module.shipping.controller.vo.NoticeVOs.NoticeLineVO;
import com.erp.module.shipping.controller.vo.NoticeVOs.NoticeQuery;
import com.erp.module.shipping.controller.vo.NoticeVOs.NoticeRow;
import com.erp.module.shipping.controller.vo.NoticeVOs.NoticeSave;
import com.erp.module.shipping.controller.vo.NoticeVOs.OrderLineOption;
import com.erp.module.shipping.controller.vo.NoticeVOs.PlanLineOption;
import com.erp.module.shipping.controller.vo.NoticeVOs.SaveResult;
import com.erp.module.shipping.dal.dataobject.ShpForwarderDO;
import com.erp.module.shipping.dal.dataobject.ShpNoticeDO;
import com.erp.module.shipping.dal.dataobject.ShpNoticeLineDO;
import com.erp.module.shipping.dal.dataobject.ShpPickingDO;
import com.erp.module.shipping.dal.dataobject.ShpShipmentDO;
import com.erp.module.shipping.dal.dataobject.ShpShipmentLineDO;
import com.erp.module.shipping.dal.mapper.ShpForwarderMapper;
import com.erp.module.shipping.dal.mapper.ShpNoticeLineMapper;
import com.erp.module.shipping.dal.mapper.ShpNoticeMapper;
import com.erp.module.shipping.dal.mapper.ShpShipmentLineMapper;
import com.erp.module.shipping.dal.mapper.ShpShipmentMapper;
import com.erp.module.shipping.service.NoticeStatus;
import com.erp.module.shipping.service.PickingStatus;
import com.erp.module.shipping.service.ShipmentStatus;
import com.erp.module.shipping.service.ShpAction;
import com.erp.module.shipping.service.ShpSupport;
import com.erp.module.shipping.service.picking.PickingService;
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 出货通知（11-01） */
@Service
public class NoticeService {

    private static final String BIZ_TYPE = ShippingModuleConfig.NOTICE;
    /** 未出货未关闭 */
    public static final List<String> OPEN = List.of(NoticeStatus.DRAFT.name(), NoticeStatus.PENDING.name(), NoticeStatus.APPROVED.name(),
            NoticeStatus.PICKING.name(), NoticeStatus.PACKED.name(), NoticeStatus.OQC.name(), NoticeStatus.READY.name());
    static final List<NoticeStatus> CLOSABLE = List.of(NoticeStatus.APPROVED, NoticeStatus.PICKING, NoticeStatus.PACKED, NoticeStatus.OQC, NoticeStatus.READY);

    private final ShpNoticeMapper mapper;
    private final ShpNoticeLineMapper lineMapper;
    private final ShpShipmentMapper shipmentMapper;
    private final ShpShipmentLineMapper shipmentLineMapper;
    private final ShpForwarderMapper forwarderMapper;
    private final SalesOrderQueryApi orderQueryApi;
    private final ShippingPlanApi shippingPlanApi;
    private final CreditApi creditApi;
    private final InventoryQueryApi inventoryQueryApi;
    private final InspectionApi inspectionApi;
    private final WorkflowApi workflowApi;
    private final PickingService pickingService;
    private final NoticeFlow flow;
    private final ShpSupport support;

    public NoticeService(ShpNoticeMapper mapper, ShpNoticeLineMapper lineMapper, ShpShipmentMapper shipmentMapper, ShpShipmentLineMapper shipmentLineMapper,
                         ShpForwarderMapper forwarderMapper, SalesOrderQueryApi orderQueryApi, ShippingPlanApi shippingPlanApi, CreditApi creditApi,
                         InventoryQueryApi inventoryQueryApi, InspectionApi inspectionApi, WorkflowApi workflowApi, PickingService pickingService,
                         NoticeFlow flow, ShpSupport support) {
        this.mapper = mapper;
        this.lineMapper = lineMapper;
        this.shipmentMapper = shipmentMapper;
        this.shipmentLineMapper = shipmentLineMapper;
        this.forwarderMapper = forwarderMapper;
        this.orderQueryApi = orderQueryApi;
        this.shippingPlanApi = shippingPlanApi;
        this.creditApi = creditApi;
        this.inventoryQueryApi = inventoryQueryApi;
        this.inspectionApi = inspectionApi;
        this.workflowApi = workflowApi;
        this.pickingService = pickingService;
        this.flow = flow;
        this.support = support;
    }

    // ==================== 保存 ====================

    @Transactional(rollbackFor = Exception.class)
    public SaveResult create(NoticeSave req) {
        ShpNoticeDO n = new ShpNoticeDO();
        n.setDocNo(support.nextNo(ShippingModuleConfig.NOTICE));
        n.setDocDate(LocalDate.now());
        n.setNoticeStatus(NoticeStatus.DRAFT.name());
        n.setStatus(NoticeStatus.DRAFT.docStatus());
        n.setCreditWarning(false);
        n.setPrepaymentUnpaid(false);
        List<String> warnings = fill(n, req, List.of());
        support.log(BIZ_TYPE, n.getId(), n.getDocNo(), ShpAction.CREATE.name(), ShpAction.CREATE.label(), null, n.getNoticeStatus(), null);
        support.bindFiles(req.fileIds(), BIZ_TYPE, n.getId());
        return new SaveResult(n.getId(), warnings);
    }

    @Transactional(rollbackFor = Exception.class)
    public SaveResult update(Long id, NoticeSave req) {
        ShpNoticeDO n = flow.get(id);
        requireDraft(n);
        List<String> warnings = fill(n, req, lineMapper.selectByParent(id));
        support.log(BIZ_TYPE, n.getId(), n.getDocNo(), ShpAction.SAVE.name(), ShpAction.SAVE.label(), null, null, null);
        support.bindFiles(req.fileIds(), BIZ_TYPE, n.getId());
        return new SaveResult(n.getId(), warnings);
    }

    /** SHP-SN-R01～R03：客户、币别、可通知数量；保存后回写订单已通知数量 */
    private List<String> fill(ShpNoticeDO n, NoticeSave req, List<ShpNoticeLineDO> oldLines) {
        CustomerDTO c = support.customerApi().validateCanShip(req.customerId());
        List<String> warnings = new ArrayList<>();
        Map<Long, SalesOrderLineDTO> ols = orderQueryApi.getLines(req.lines().stream().map(NoticeLineSave::orderLineId).toList());
        Map<Long, BigDecimal> oldByOrderLine = new HashMap<>();
        for (ShpNoticeLineDO l : oldLines) oldByOrderLine.merge(l.getOrderLineId(), l.getBaseQty(), BigDecimal::add);
        Map<Long, BigDecimal> newByOrderLine = new LinkedHashMap<>();
        String currency = null;
        int no = 1;
        List<ShpNoticeLineDO> lines = new ArrayList<>();
        boolean oqcDefault = support.params().getBool(ShippingModuleConfig.P_OQC_DEFAULT);
        for (NoticeLineSave s : req.lines()) {
            int lineNo = no++;
            SalesOrderLineDTO ol = ols.get(s.orderLineId());
            if (ol == null || "CLOSED".equals(ol.lineStatus())) throw BizException.of(ShippingErrorCodes.SN_ORDER_INVALID, lineNo);
            if (!Objects.equals(ol.customerId(), c.id())) throw BizException.of(ShippingErrorCodes.SN_CUSTOMER_MISMATCH, lineNo, ShpSupport.customerName(c));
            if (currency == null) currency = ol.currency();
            else if (!currency.equals(ol.currency())) throw new BizException(ShippingErrorCodes.SN_CURRENCY_MIXED);
            if (s.qty() == null || s.qty().signum() <= 0) throw BizException.of(ShippingErrorCodes.SN_QTY_INVALID, lineNo);
            BigDecimal qty = Decimals.qty(s.qty());
            BigDecimal baseQty = toBase(ol, qty);
            BigDecimal already = newByOrderLine.getOrDefault(ol.lineId(), BigDecimal.ZERO);
            BigDecimal allowed = ShpSupport.nz(ol.noticeableQty()).add(oldByOrderLine.getOrDefault(ol.lineId(), BigDecimal.ZERO)).subtract(already);
            if (baseQty.compareTo(allowed) > 0) throw BizException.of(ShippingErrorCodes.SN_QTY_EXCEED, lineNo, ShpSupport.plain(fromBase(ol, allowed.max(BigDecimal.ZERO))));
            newByOrderLine.merge(ol.lineId(), baseQty, BigDecimal::add);
            BigDecimal available = inventoryQueryApi.getAvailableQty(ol.materialId(), req.warehouseId());
            if (available.compareTo(baseQty) < 0) warnings.add("第 " + lineNo + " 行出货仓可用库存 " + ShpSupport.plain(available) + " 不足");
            ShpNoticeLineDO l = new ShpNoticeLineDO();
            l.setLineNo(lineNo);
            l.setOrderId(ol.orderId());
            l.setOrderNo(ol.orderNo());
            l.setOrderLineId(ol.lineId());
            l.setOrderLineNo(ol.lineNo());
            l.setMaterialId(ol.materialId());
            l.setCustomerPartNo(ol.customerPartNo());
            l.setDescription(StringUtils.hasText(s.description()) ? ShpSupport.limit(s.description(), 512) : ol.description());
            l.setUom(ol.uom());
            l.setQty(qty);
            l.setBaseQty(baseQty);
            l.setPriceInclTax(ol.priceInclTax());
            l.setBasePriceInclTax(ol.basePriceInclTax());
            l.setTaxRate(ol.taxRate());
            l.setTotalAmount(Decimals.multiplyAmount(qty, ol.priceInclTax()));
            l.setOqcRequired(oqcDefault || support.materialApi().getQualityAttr(ol.materialId()).oqcRequired());
            l.setPickedQty(BigDecimal.ZERO);
            l.setPackedQty(BigDecimal.ZERO);
            l.setShippedQty(BigDecimal.ZERO);
            l.setShortageQty(BigDecimal.ZERO);
            l.setShippingPlanLineId(s.shippingPlanLineId());
            l.setRemark(ShpSupport.limit(s.remark(), 256));
            lines.add(l);
        }
        AddressDTO addr = support.customerApi().getAddresses(c.id(), "SHIP_TO").stream().filter(a -> a.id().equals(req.shipToAddressId())).findFirst()
                .orElseThrow(() -> BizException.of(ShippingErrorCodes.NOT_EXISTS, "收货地址"));
        WarehouseDTO wh = support.warehouseApi().get(req.warehouseId()).orElseThrow(() -> BizException.of(ShippingErrorCodes.NOT_EXISTS, "出货仓"));
        n.setCustomerId(c.id());
        n.setCurrency(currency);
        n.setShipDate(req.shipDate());
        n.setTransportMode(req.transportMode());
        n.setTradeTerm(ShpSupport.trim(req.tradeTerm()));
        n.setPortOfLoading(ShpSupport.limit(req.portOfLoading(), 64));
        n.setPortOfDestination(ShpSupport.limit(req.portOfDestination(), 64));
        n.setShipToAddressId(addr.id());
        n.setShipToSnapshot(support.toJson(addr));
        n.setNotifyParty(ShpSupport.limit(req.notifyParty(), 512));
        n.setForwarderId(req.forwarderId());
        n.setWarehouseId(wh.id());
        n.setOqcRequired(lines.stream().anyMatch(l -> Boolean.TRUE.equals(l.getOqcRequired())));
        n.setRemark(ShpSupport.limit(req.remark(), 1000));
        BigDecimal total = lines.stream().map(ShpNoticeLineDO::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        n.setTotalAmount(Decimals.amount(total));
        n.setTotalAmountBase(toBaseAmount(total, currency, req.shipDate()));
        support.fillOwner(n, req.ownerId() != null ? req.ownerId() : n.getOwnerId());
        if (n.getId() == null) mapper.insert(n);
        else mapper.updateByIdOrFail(n);
        lineMapper.deleteByParent(n.getId());
        for (ShpNoticeLineDO l : lines) {
            l.setNoticeId(n.getId());
            lineMapper.insert(l);
        }
        Map<Long, BigDecimal> deltas = new LinkedHashMap<>();
        oldByOrderLine.forEach((k, v) -> deltas.merge(k, v.negate(), BigDecimal::add));
        newByOrderLine.forEach((k, v) -> deltas.merge(k, v, BigDecimal::add));
        Map<Long, BigDecimal> plans = new LinkedHashMap<>();
        for (ShpNoticeLineDO l : oldLines) if (l.getShippingPlanLineId() != null) plans.merge(l.getShippingPlanLineId(), l.getBaseQty().negate(), BigDecimal::add);
        for (ShpNoticeLineDO l : lines) if (l.getShippingPlanLineId() != null) plans.merge(l.getShippingPlanLineId(), l.getBaseQty(), BigDecimal::add);
        // 先释放后占用，避免同一订单行调整数量时误报超出可通知数量
        Map<Long, BigDecimal> releases = new LinkedHashMap<>();
        Map<Long, BigDecimal> occupies = new LinkedHashMap<>();
        deltas.forEach((k, v) -> (v.signum() < 0 ? releases : occupies).put(k, v));
        flow.writeBack(n, releases, Map.of());
        flow.writeBack(n, occupies, plans);
        return warnings;
    }

    static BigDecimal toBase(SalesOrderLineDTO ol, BigDecimal qty) {
        if (ol.qty() == null || ol.qty().signum() == 0 || ol.qty().compareTo(ol.baseQty()) == 0) return Decimals.qty(qty);
        return qty.multiply(ol.baseQty()).divide(ol.qty(), Decimals.QTY_SCALE, RoundingMode.HALF_UP);
    }

    static BigDecimal fromBase(SalesOrderLineDTO ol, BigDecimal baseQty) {
        if (ol.baseQty() == null || ol.baseQty().signum() == 0 || ol.qty().compareTo(ol.baseQty()) == 0) return Decimals.qty(baseQty);
        return baseQty.multiply(ol.qty()).divide(ol.baseQty(), Decimals.QTY_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal toBaseAmount(BigDecimal amount, String currency, LocalDate date) {
        if (currency == null) return Decimals.amount(amount);
        BigDecimal rate = support.currencyApi().getRate(currency, date == null ? LocalDate.now() : date);
        return Decimals.amount(support.currencyApi().toBase(amount, rate));
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        ShpNoticeDO n = flow.get(id);
        requireDraft(n);
        List<ShpNoticeLineDO> lines = lineMapper.selectByParent(id);
        Map<ShpNoticeLineDO, BigDecimal> rel = new LinkedHashMap<>();
        for (ShpNoticeLineDO l : lines) rel.put(l, l.getBaseQty());
        flow.release(n, rel);
        lineMapper.deleteByParent(id);
        mapper.deleteById(id);
        support.log(BIZ_TYPE, id, n.getDocNo(), "DELETE", "删除", n.getNoticeStatus(), null, null);
    }

    private static void requireDraft(ShpNoticeDO n) {
        NoticeStatus st = NoticeFlow.status(n);
        if (st == NoticeStatus.PENDING) throw new BizException(ShippingErrorCodes.DOC_PENDING);
        if (st != NoticeStatus.DRAFT) throw BizException.of(ShippingErrorCodes.STATUS_NOT_ALLOWED, st.label(), "修改");
    }

    // ==================== 从订单 / 出货计划生成 ====================

    /** 从订单行直接生成草稿：通知数量默认 = min(可通知, 出货仓可用库存)，库存为 0 时取可通知 */
    @Transactional(rollbackFor = Exception.class)
    public SaveResult fromOrders(FromOrdersReq req) {
        Map<Long, SalesOrderLineDTO> ols = orderQueryApi.getLines(req.orderLineIds());
        List<SalesOrderLineDTO> list = req.orderLineIds().stream().map(ols::get).filter(Objects::nonNull).toList();
        if (list.isEmpty()) throw new BizException(ShippingErrorCodes.NO_LINES);
        SalesOrderLineDTO first = list.get(0);
        CustomerDefaults d = customerDefaults(first.customerId(), first.orderId());
        Long warehouseId = req.warehouseId() != null ? req.warehouseId() : d.warehouseId();
        List<NoticeLineSave> lines = new ArrayList<>();
        for (SalesOrderLineDTO ol : list) {
            BigDecimal noticeable = ShpSupport.nz(ol.noticeableQty());
            BigDecimal avail = warehouseId == null ? BigDecimal.ZERO : inventoryQueryApi.getAvailableQty(ol.materialId(), warehouseId);
            BigDecimal base = avail.signum() > 0 ? noticeable.min(avail) : noticeable;
            lines.add(new NoticeLineSave(null, ol.lineId(), fromBase(ol, base), null, null, null));
        }
        CustomerDTO c = support.customer(first.customerId());
        return create(new NoticeSave(first.customerId(), req.shipDate() != null ? req.shipDate() : LocalDate.now(),
                StringUtils.hasText(req.transportMode()) ? req.transportMode() : (c.foreign() ? "SEA" : "LAND"), d.tradeTerm(), d.portOfLoading(),
                d.portOfDestination(), d.shipToAddressId(), null, null, warehouseId, null, null, lines, null));
    }

    /** SHP-SN-R08：从已发布出货计划按客户（及币别）分组生成通知草稿，回写计划已通知数量 */
    @Transactional(rollbackFor = Exception.class)
    public List<Long> fromPlan(FromPlanReq req) {
        List<ShippingPlanLineDTO> pls = shippingPlanApi.getPlanLines(req.planWeek()).stream()
                .filter(p -> req.planLineIds() == null || req.planLineIds().isEmpty() || req.planLineIds().contains(p.id()))
                .filter(p -> !"CLOSED".equals(p.lineStatus()) && ShpSupport.nz(p.planQty()).compareTo(ShpSupport.nz(p.noticedQty())) > 0).toList();
        Map<Long, SalesOrderLineDTO> ols = orderQueryApi.getLines(pls.stream().map(ShippingPlanLineDTO::orderLineId).toList());
        Map<String, List<ShippingPlanLineDTO>> groups = new LinkedHashMap<>();
        for (ShippingPlanLineDTO p : pls) {
            SalesOrderLineDTO ol = ols.get(p.orderLineId());
            if (ol == null || ShpSupport.nz(ol.noticeableQty()).signum() <= 0) continue;
            groups.computeIfAbsent(ol.customerId() + "|" + ol.currency(), k -> new ArrayList<>()).add(p);
        }
        if (groups.isEmpty()) throw new BizException(ShippingErrorCodes.SN_NO_PLAN_LINES);
        List<Long> ids = new ArrayList<>();
        for (List<ShippingPlanLineDTO> g : groups.values()) {
            SalesOrderLineDTO first = ols.get(g.get(0).orderLineId());
            CustomerDefaults d = customerDefaults(first.customerId(), first.orderId());
            List<NoticeLineSave> lines = g.stream().map(p -> {
                SalesOrderLineDTO ol = ols.get(p.orderLineId());
                BigDecimal base = ShpSupport.nz(p.planQty()).subtract(ShpSupport.nz(p.noticedQty())).min(ShpSupport.nz(ol.noticeableQty()));
                return new NoticeLineSave(null, ol.lineId(), fromBase(ol, base), null, p.remark(), p.id());
            }).toList();
            LocalDate shipDate = g.stream().map(ShippingPlanLineDTO::planShipDate).filter(Objects::nonNull).min(Comparator.naturalOrder()).orElse(LocalDate.now());
            String mode = g.stream().map(ShippingPlanLineDTO::transportMode).filter(StringUtils::hasText).findFirst().orElse("SEA");
            Long warehouseId = req.warehouseId() != null ? req.warehouseId() : d.warehouseId();
            ids.add(create(new NoticeSave(first.customerId(), shipDate, mode, d.tradeTerm(), d.portOfLoading(), d.portOfDestination(), d.shipToAddressId(),
                    null, null, warehouseId, null, "出货计划 " + g.get(0).planNo(), lines, null)).id());
        }
        return ids;
    }

    // ==================== 提交 / 审批 ====================

    /** SHP-SN-R04：信用检查（参数）、出货前款项检查 → 审批条件字段 */
    @Transactional(rollbackFor = Exception.class)
    public SaveResult submit(Long id) {
        ShpNoticeDO n = flow.get(id);
        requireDraft(n);
        support.customerApi().validateCanShip(n.getCustomerId());
        List<ShpNoticeLineDO> lines = lineMapper.selectByParent(id);
        if (lines.isEmpty()) throw new BizException(ShippingErrorCodes.NO_LINES);
        List<String> warnings = new ArrayList<>();
        n.setCreditWarning(false);
        if (support.params().getBool(ShippingModuleConfig.P_CREDIT_CHECK)) {
            CreditCheckResult r = creditApi.check(n.getCustomerId(), n.getTotalAmountBase(), CreditCheckPoint.SHIPMENT);
            if (!r.pass()) {
                if ("BLOCK".equals(r.mode())) throw BizException.of(ShippingErrorCodes.SN_CREDIT_BLOCK, r.message());
                n.setCreditWarning(true);
                warnings.add(r.message());
            }
        }
        List<String> unpaid = unpaidOrders(lines);
        n.setPrepaymentUnpaid(!unpaid.isEmpty());
        warnings.addAll(unpaid);
        mapper.updateByIdOrFail(n);
        flow.fire(n, ShpAction.SUBMIT, warnings.isEmpty() ? null : String.join("；", warnings));
        Map<String, Object> vars = new HashMap<>();
        vars.put("amountBase", ShpSupport.nz(n.getTotalAmountBase()));
        vars.put("creditWarning", Boolean.TRUE.equals(n.getCreditWarning()));
        vars.put("prepaymentUnpaid", Boolean.TRUE.equals(n.getPrepaymentUnpaid()));
        Map<String, Long> users = new HashMap<>();
        if (n.getOwnerId() != null) users.put("ownerId", n.getOwnerId());
        CustomerDTO c = support.customer(n.getCustomerId());
        StartResult r = workflowApi.start(BIZ_TYPE, id, n.getDocNo(), "出货通知 " + n.getDocNo() + "（" + ShpSupport.customerName(c) + "）", vars, users,
                support.currentUser());
        if (!r.isStarted()) approve(flow.get(id));
        return new SaveResult(id, warnings);
    }

    /** 出货前款项未收齐的订单提示（参数 NONE 时不检查） */
    List<String> unpaidOrders(List<ShpNoticeLineDO> lines) {
        List<String> list = new ArrayList<>();
        if ("NONE".equals(support.params().getString(ShippingModuleConfig.P_PREPAYMENT))) return list;
        Map<Long, String> orders = new LinkedHashMap<>();
        for (ShpNoticeLineDO l : lines) orders.putIfAbsent(l.getOrderId(), l.getOrderNo());
        for (Map.Entry<Long, String> e : orders.entrySet()) {
            BigDecimal unpaid = ShpSupport.nz(orderQueryApi.getUnpaidBeforeShipment(e.getKey()));
            if (unpaid.signum() > 0) list.add("订单「" + e.getValue() + "」出货前款项未收齐（" + ShpSupport.plain(unpaid) + "）");
        }
        return list;
    }

    @EventListener
    public void onApproval(ApprovalCompletedEvent e) {
        if (!BIZ_TYPE.equals(e.getBizType())) return;
        ShpNoticeDO n = mapper.selectById(e.getBizId());
        if (n == null || NoticeFlow.status(n) != NoticeStatus.PENDING) return;
        switch (e.getResult()) {
            case APPROVED -> approve(n);
            case WITHDRAWN -> flow.fire(n, ShpAction.WITHDRAW, null);
            default -> flow.fire(n, ShpAction.REJECT, e.getComment());
        }
    }

    /** SHP-SN-R05：审核生成拣货单；未启用拣货时直接“已装箱” */
    private void approve(ShpNoticeDO n) {
        n.setApprovedAt(LocalDateTime.now());
        flow.fire(n, ShpAction.APPROVE, null);
        if (support.params().getBool(ShippingModuleConfig.P_PICKING)) {
            ShpPickingDO p = pickingService.createFor(flow.get(n.getId()));
            support.message(support.usersWithPermission("shp:picking:pick"), "拣货任务 " + p.getDocNo(),
                    "出货通知 " + n.getDocNo() + " 已审核，出货日期 " + n.getShipDate(), "/shipping/picking/" + p.getId());
            return;
        }
        for (ShpNoticeLineDO l : lineMapper.selectByParent(n.getId())) {
            l.setPickedQty(l.getBaseQty());
            l.setPackedQty(l.getBaseQty());
            lineMapper.updateByIdOrFail(l);
        }
        flow.fire(flow.get(n.getId()), ShpAction.PACK, "未启用拣货");
    }

    /** SHP-SN-R06：拣货单未开始时可反审核（作废拣货单） */
    @Transactional(rollbackFor = Exception.class)
    public void unapprove(Long id) {
        ShpNoticeDO n = flow.get(id);
        NoticeStatus st = NoticeFlow.status(n);
        if (st != NoticeStatus.APPROVED) throw BizException.of(ShippingErrorCodes.STATUS_NOT_ALLOWED, st.label(), "反审核");
        ShpPickingDO p = flow.activePicking(id);
        if (p != null && !PickingStatus.WAITING.name().equals(p.getPickingStatus())) throw new BizException(ShippingErrorCodes.SN_PICKING_STARTED);
        pickingService.cancelFor(id, "出货通知反审核");
        n = flow.get(id);
        n.setApprovedAt(null);
        flow.fire(n, ShpAction.UNAPPROVE, null);
    }

    /** SHP-SN-R07：关闭（原因必填），拣货单作废、未完成的 OQC 取消，未出货数量释放回订单 */
    @Transactional(rollbackFor = Exception.class)
    public void close(Long id, String reason) {
        ShpNoticeDO n = flow.get(id);
        String r = ShpSupport.requireText(reason, "关闭原因");
        NoticeStatus st = NoticeFlow.status(n);
        if (!CLOSABLE.contains(st)) throw BizException.of(ShippingErrorCodes.STATUS_NOT_ALLOWED, st.label(), "关闭");
        shipmentMapper.selectList(new LambdaQueryWrapper<ShpShipmentDO>().eq(ShpShipmentDO::getNoticeId, id)
                        .in(ShpShipmentDO::getShipmentStatus, List.of(ShipmentStatus.DRAFT.name(), ShipmentStatus.PENDING.name(), ShipmentStatus.SUBMITTED.name())))
                .stream().findFirst().ifPresent(s -> {
                    throw BizException.of(ShippingErrorCodes.SN_SHIPMENT_OPEN, s.getDocNo());
                });
        pickingService.cancelFor(id, "出货通知关闭");
        if (Boolean.TRUE.equals(n.getOqcRequired())) inspectionApi.cancelOqc(id);
        Map<ShpNoticeLineDO, BigDecimal> rel = new LinkedHashMap<>();
        for (ShpNoticeLineDO l : lineMapper.selectByParent(id)) rel.put(l, NoticeFlow.effectiveQty(l).subtract(ShpSupport.nz(l.getShippedQty())));
        n = flow.get(id);
        n.setCloseReason(ShpSupport.limit(r, 256));
        flow.fire(n, ShpAction.CLOSE, r);
        flow.release(flow.get(id), rel);
    }

    // ==================== 查询 ====================

    public PageResult<NoticeRow> page(NoticeQuery q) {
        LambdaQueryWrapper<ShpNoticeDO> w = query(q);
        if (w == null) return new PageResult<>(List.of(), 0L);
        IPage<ShpNoticeDO> p = mapper.selectScopedPage(new Page<>(q.getPageNo(), q.getPageSize()), w);
        return new PageResult<>(rows(p.getRecords()), p.getTotal());
    }

    public List<NoticeRow> list(NoticeQuery q) {
        LambdaQueryWrapper<ShpNoticeDO> w = query(q);
        return w == null ? List.of() : rows(mapper.selectScopedList(w));
    }

    private LambdaQueryWrapper<ShpNoticeDO> query(NoticeQuery q) {
        List<String> statuses = new ArrayList<>();
        if (StringUtils.hasText(q.getStatuses())) {
            for (String s : q.getStatuses().split(",")) {
                if ("OPEN".equals(s)) statuses.addAll(OPEN);
                else statuses.add(s.trim());
            }
        }
        List<Long> byOrder = null;
        if (StringUtils.hasText(q.getOrderNo())) {
            byOrder = lineMapper.selectList(new LambdaQueryWrapper<ShpNoticeLineDO>().like(ShpNoticeLineDO::getOrderNo, q.getOrderNo().trim())).stream()
                    .map(ShpNoticeLineDO::getNoticeId).distinct().toList();
            if (byOrder.isEmpty()) return null;
        }
        return new LambdaQueryWrapper<ShpNoticeDO>().eq(ShpNoticeDO::getDeleted, false)
                .like(StringUtils.hasText(q.getDocNo()), ShpNoticeDO::getDocNo, q.getDocNo())
                .eq(q.getCustomerId() != null, ShpNoticeDO::getCustomerId, q.getCustomerId())
                .in(byOrder != null, ShpNoticeDO::getId, byOrder)
                .in(!statuses.isEmpty(), ShpNoticeDO::getNoticeStatus, statuses)
                .ge(q.getShipDateFrom() != null, ShpNoticeDO::getShipDate, q.getShipDateFrom())
                .le(q.getShipDateTo() != null, ShpNoticeDO::getShipDate, q.getShipDateTo())
                .eq(StringUtils.hasText(q.getTransportMode()), ShpNoticeDO::getTransportMode, q.getTransportMode())
                .eq(q.getOwnerId() != null, ShpNoticeDO::getOwnerId, q.getOwnerId())
                .orderByDesc(ShpNoticeDO::getShipDate).orderByDesc(ShpNoticeDO::getId);
    }

    private List<NoticeRow> rows(List<ShpNoticeDO> list) {
        Map<Long, CustomerDTO> cus = support.customers(list.stream().map(ShpNoticeDO::getCustomerId).toList());
        Map<Long, UserDTO> users = support.users(list.stream().map(ShpNoticeDO::getOwnerId).toList());
        Map<Long, WarehouseDTO> whs = support.warehouses(list.stream().map(ShpNoticeDO::getWarehouseId).toList());
        Map<Long, List<ShpNoticeLineDO>> lines = lineMapper.selectByParents(list.stream().map(ShpNoticeDO::getId).toList()).stream()
                .collect(Collectors.groupingBy(ShpNoticeLineDO::getNoticeId));
        boolean price = support.canSeePrice();
        LocalDate today = LocalDate.now();
        return list.stream().map(n -> {
            List<ShpNoticeLineDO> ls = lines.getOrDefault(n.getId(), List.of());
            boolean open = OPEN.contains(n.getNoticeStatus());
            return new NoticeRow(n.getId(), n.getDocNo(), n.getCustomerId(), ShpSupport.customerName(cus.get(n.getCustomerId())), n.getShipDate(),
                    open && n.getShipDate() != null && n.getShipDate().isBefore(today), n.getTransportMode(), n.getPortOfDestination(), ls.size(),
                    sum(ls, ShpNoticeLineDO::getBaseQty), n.getCurrency(), price ? n.getTotalAmount() : null, sum(ls, ShpNoticeLineDO::getPickedQty),
                    sum(ls, ShpNoticeLineDO::getPackedQty), sum(ls, ShpNoticeLineDO::getShippedQty), Boolean.TRUE.equals(n.getOqcRequired()),
                    n.getOqcResult(), n.getNoticeStatus(), n.getOwnerId(), ShpSupport.name(users, n.getOwnerId()),
                    whs.containsKey(n.getWarehouseId()) ? whs.get(n.getWarehouseId()).name() : null, n.getCreatedAt());
        }).toList();
    }

    static <T> BigDecimal sum(List<T> list, Function<T, BigDecimal> f) {
        return list.stream().map(f).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public NoticeDetail detail(Long id) {
        ShpNoticeDO n = flow.get(id);
        List<ShpNoticeLineDO> lines = lineMapper.selectByParent(id);
        Map<Long, MaterialDTO> ms = support.materials(lines.stream().map(ShpNoticeLineDO::getMaterialId).toList());
        Map<Long, SalesOrderLineDTO> ols = orderQueryApi.getLines(lines.stream().map(ShpNoticeLineDO::getOrderLineId).toList());
        Map<Long, SalesOrderHeaderDTO> heads = orderQueryApi.getOrderHeaders(lines.stream().map(ShpNoticeLineDO::getOrderId).distinct().toList());
        boolean price = support.canSeePrice();
        boolean draft = NoticeFlow.status(n) == NoticeStatus.DRAFT;
        List<NoticeLineVO> lvs = lines.stream().map(l -> {
            MaterialDTO m = ms.get(l.getMaterialId());
            SalesOrderLineDTO ol = ols.get(l.getOrderLineId());
            SalesOrderHeaderDTO h = heads.get(l.getOrderId());
            BigDecimal noticeable = ol == null ? null : draft ? ShpSupport.nz(ol.noticeableQty()).add(l.getBaseQty()) : ol.noticeableQty();
            BigDecimal avail = draft ? inventoryQueryApi.getAvailableQty(l.getMaterialId(), n.getWarehouseId()) : null;
            return new NoticeLineVO(l.getId(), l.getLineNo(), l.getOrderId(), l.getOrderNo(), l.getOrderLineId(), l.getOrderLineNo(),
                    h == null ? null : h.customerPoNo(), l.getMaterialId(), m == null ? null : m.code(), m == null ? null : m.name(), m == null ? null : m.spec(),
                    l.getCustomerPartNo(), l.getDescription(), l.getUom(), l.getQty(), l.getBaseQty(), m == null ? null : m.baseUom(),
                    price ? l.getPriceInclTax() : null, l.getTaxRate(), price ? l.getTotalAmount() : null, Boolean.TRUE.equals(l.getOqcRequired()),
                    l.getPickedQty(), l.getPackedQty(), l.getShippedQty(), l.getShortageQty(), noticeable, avail, l.getShippingPlanLineId(), l.getRemark());
        }).toList();
        List<ShpPickingDO> pickings = pickingService.byNotice(id);
        List<LinkedDoc> pks = pickings.stream().map(p -> new LinkedDoc(p.getId(), p.getDocNo(), p.getPickingStatus(), p.getDocDate(), null)).toList();
        List<ShpShipmentDO> ships = shipmentMapper.selectList(new LambdaQueryWrapper<ShpShipmentDO>().eq(ShpShipmentDO::getNoticeId, id).orderByAsc(ShpShipmentDO::getId));
        List<LinkedDoc> shs = ships.stream().map(s -> new LinkedDoc(s.getId(), s.getDocNo(), s.getShipmentStatus(), s.getShipDate(), s.getTotalQty())).toList();
        ShpPickingDO active = pickings.stream().filter(p -> !PickingStatus.CANCELED.name().equals(p.getPickingStatus())).reduce((a, b) -> b).orElse(null);
        boolean pickingEnabled = support.params().getBool(ShippingModuleConfig.P_PICKING);
        boolean packingEnabled = support.params().getBool(ShippingModuleConfig.P_PACKING);
        NoticeStatus st = NoticeFlow.status(n);
        boolean canShip = (st == NoticeStatus.PACKED && !Boolean.TRUE.equals(n.getOqcRequired())) || st == NoticeStatus.READY;
        ShpForwarderDO fw = n.getForwarderId() == null ? null : forwarderMapper.selectById(n.getForwarderId());
        CustomerDTO c = support.customers(List.of(n.getCustomerId())).get(n.getCustomerId());
        return new NoticeDetail(n.getId(), n.getDocNo(), n.getDocDate(), n.getStatus().name(), n.getNoticeStatus(), n.getCustomerId(), ShpSupport.customerName(c),
                n.getCurrency(), n.getShipDate(), n.getTransportMode(), n.getTradeTerm(), n.getPortOfLoading(), n.getPortOfDestination(), n.getShipToAddressId(),
                support.addressText(n.getShipToSnapshot()), n.getNotifyParty(), n.getForwarderId(), fw == null ? null : fw.getName(), n.getWarehouseId(),
                support.warehouseName(n.getWarehouseId()), Boolean.TRUE.equals(n.getOqcRequired()), n.getOqcResult(), price ? n.getTotalAmount() : null,
                price ? n.getTotalAmountBase() : null, Boolean.TRUE.equals(n.getCreditWarning()), Boolean.TRUE.equals(n.getPrepaymentUnpaid()),
                n.getApprovedAt(), n.getCloseReason(), n.getOwnerId(), support.userName(n.getOwnerId()), n.getRemark(), pickingEnabled, packingEnabled,
                active != null && !PickingStatus.WAITING.name().equals(active.getPickingStatus()),
                active != null && PickingStatus.DONE.name().equals(active.getPickingStatus()), canShip, lvs, pks, shs, n.getCreatedBy(),
                support.userName(n.getCreatedBy()), n.getCreatedAt(), price);
    }

    /** 选择订单行：该客户已审核 / 执行中、可通知数量 > 0 的订单行 */
    public List<OrderLineOption> orderLineOptions(Long customerId, String orderNo, Long materialId, Long warehouseId) {
        List<SalesOrderLineDTO> ls = orderQueryApi.getOpenLines(new OpenLineFilter(customerId, materialId, null, null, null)).stream()
                .filter(l -> ShpSupport.nz(l.noticeableQty()).signum() > 0)
                .filter(l -> !StringUtils.hasText(orderNo) || l.orderNo().contains(orderNo.trim())).limit(500).toList();
        Map<Long, MaterialDTO> ms = support.materials(ls.stream().map(SalesOrderLineDTO::materialId).toList());
        Map<Long, SalesOrderHeaderDTO> heads = orderQueryApi.getOrderHeaders(ls.stream().map(SalesOrderLineDTO::orderId).distinct().toList());
        Map<Long, BigDecimal> avail = new HashMap<>();
        boolean price = support.canSeePrice();
        return ls.stream().map(l -> {
            MaterialDTO m = ms.get(l.materialId());
            BigDecimal a = avail.computeIfAbsent(l.materialId(), k -> warehouseId == null ? inventoryQueryApi.getAvailableQty(k)
                    : inventoryQueryApi.getAvailableQty(k, warehouseId));
            SalesOrderHeaderDTO h = heads.get(l.orderId());
            return new OrderLineOption(l.lineId(), l.orderId(), l.orderNo(), l.lineNo(), h == null ? null : h.customerPoNo(), l.materialId(),
                    m == null ? null : m.code(), m == null ? null : m.name(), l.customerPartNo(), l.description(), l.uom(), l.qty(), l.noticedQty(),
                    fromBase(l, l.noticeableQty()), l.dueDate(), a, l.currency(), price ? l.priceInclTax() : null);
        }).toList();
    }

    /** 选择客户后的默认值：收货地址、贸易条款、港口（取最近订单）、默认成品仓 */
    public CustomerDefaults customerDefaults(Long customerId, Long orderId) {
        CustomerDTO c = support.customer(customerId);
        List<AddressDTO> addrs = support.customerApi().getAddresses(customerId, "SHIP_TO");
        Long oid = orderId != null ? orderId
                : orderQueryApi.getOpenLines(new OpenLineFilter(customerId, null, null, null, null)).stream().map(SalesOrderLineDTO::orderId).findFirst().orElse(null);
        SalesOrderHeaderDTO h = oid == null ? null : orderQueryApi.getOrderHeaders(List.of(oid)).get(oid);
        Long shipTo = h != null && h.shipToAddressId() != null && addrs.stream().anyMatch(a -> a.id().equals(h.shipToAddressId())) ? h.shipToAddressId()
                : addrs.stream().filter(AddressDTO::isDefault).map(AddressDTO::id).findFirst().orElse(addrs.isEmpty() ? null : addrs.get(0).id());
        List<WarehouseDTO> fgs = support.warehouseApi().listByType(WarehouseType.FG);
        Long wh = fgs.stream().filter(WarehouseDTO::isDefault).map(WarehouseDTO::id).findFirst().orElse(fgs.isEmpty() ? null : fgs.get(0).id());
        return new CustomerDefaults(customerId, h != null ? h.currency() : c.currency(), h != null && h.tradeTerm() != null ? h.tradeTerm() : c.tradeTerm(), wh,
                addrs.stream().map(a -> new AddressOption(a.id(), ShpSupport.addressText(a), a.isDefault())).toList(), shipTo,
                h == null ? null : h.portOfLoading(), h == null ? null : h.portOfDestination());
    }

    /** 从出货计划生成：本周计划中未通知完的行 */
    public List<PlanLineOption> planLineOptions(String week) {
        List<ShippingPlanLineDTO> pls = shippingPlanApi.getPlanLines(week).stream()
                .filter(p -> !"CLOSED".equals(p.lineStatus()) && ShpSupport.nz(p.planQty()).compareTo(ShpSupport.nz(p.noticedQty())) > 0).toList();
        Map<Long, SalesOrderLineDTO> ols = orderQueryApi.getLines(pls.stream().map(ShippingPlanLineDTO::orderLineId).toList());
        Map<Long, MaterialDTO> ms = support.materials(pls.stream().map(ShippingPlanLineDTO::materialId).toList());
        Map<Long, CustomerDTO> cus = support.customers(pls.stream().map(ShippingPlanLineDTO::customerId).toList());
        return pls.stream().map(p -> {
            SalesOrderLineDTO ol = ols.get(p.orderLineId());
            MaterialDTO m = ms.get(p.materialId());
            return new PlanLineOption(p.id(), p.planNo(), p.planWeek(), p.customerId(), ShpSupport.customerName(cus.get(p.customerId())), p.orderLineId(),
                    ol == null ? null : ol.orderNo(), ol == null ? null : ol.lineNo(), p.materialId(), m == null ? null : m.code(), m == null ? null : m.name(),
                    p.planQty(), p.noticedQty(), p.planShipDate(), p.transportMode());
        }).toList();
    }

    public Map<String, Object> printData(Long id) {
        NoticeDetail d = detail(id);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("docNo", d.docNo());
        m.put("status", NoticeStatus.valueOf(d.noticeStatus()).label());
        m.put("customerName", d.customerName());
        m.put("shipDate", d.shipDate());
        m.put("transportModeName", support.dictLabel("shp_transport_mode", d.transportMode()));
        m.put("portOfDestination", Objects.toString(d.portOfDestination(), ""));
        m.put("shipToText", Objects.toString(d.shipToText(), ""));
        m.put("warehouseName", d.warehouseName());
        m.put("remark", Objects.toString(d.remark(), ""));
        m.put("lines", d.lines().stream().map(l -> {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("lineNo", l.lineNo());
            r.put("orderNo", l.orderNo());
            r.put("materialCode", l.materialCode());
            r.put("materialName", l.materialName());
            r.put("customerPartNo", Objects.toString(l.customerPartNo(), ""));
            r.put("qty", l.qty());
            r.put("uom", l.uom());
            r.put("remark", Objects.toString(l.remark(), ""));
            return r;
        }).toList());
        return m;
    }

    /** 出货单生成时取通知行（出货服务使用） */
    public List<ShpNoticeLineDO> lines(Long noticeId) {
        return lineMapper.selectByParent(noticeId);
    }

    public List<ShpShipmentLineDO> shipmentLines(Long shipmentId) {
        return shipmentLineMapper.selectByParent(shipmentId);
    }

    // ==================== 事件 ====================

    /** CRM 客户加入黑名单：提醒未完成出货通知的船务 */
    @EventListener
    public void onCustomerStatus(CustomerStatusChangedEvent e) {
        if (e.getToStatus() != CustomerStatus.BLACKLIST) return;
        List<ShpNoticeDO> list = mapper.selectList(new LambdaQueryWrapper<ShpNoticeDO>().eq(ShpNoticeDO::getCustomerId, e.getCustomerId())
                .in(ShpNoticeDO::getNoticeStatus, OPEN));
        for (ShpNoticeDO n : list) {
            support.message(List.of(n.getOwnerId()), "客户 " + e.getCustomerCode() + " 已加入黑名单",
                    "出货通知 " + n.getDocNo() + " 的客户已加入黑名单，出货单提交将被阻止", "/shipping/notice/" + n.getId());
        }
    }

    /** PMC 出货计划发布：提醒船务生成出货通知 */
    @EventListener
    public void onPlanPublished(ShippingPlanPublishedEvent e) {
        support.message(support.usersWithPermission("shp:notice:create"), "出货计划 " + e.getPlanNo() + " 已发布",
                e.getPlanWeek() + " 共 " + e.getOrderLineIds().size() + " 个订单行，可在出货通知中[从出货计划生成]", "/shipping/notice");
    }
}
