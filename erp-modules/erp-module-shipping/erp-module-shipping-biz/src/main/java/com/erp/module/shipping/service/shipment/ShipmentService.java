package com.erp.module.shipping.service.shipment;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.common.exception.BizException;
import com.erp.common.result.PageResult;
import com.erp.common.util.Decimals;
import com.erp.framework.event.DomainEventPublisher;
import com.erp.framework.mybatis.BaseDO;
import com.erp.module.crm.api.credit.CreditApi;
import com.erp.module.crm.api.credit.CreditCheckPoint;
import com.erp.module.crm.api.credit.CreditCheckResult;
import com.erp.module.crm.api.customer.CustomerDTO;
import com.erp.module.engineering.api.material.MaterialDTO;
import com.erp.module.inventory.api.doc.InventoryDocApi;
import com.erp.module.inventory.api.doc.SourceRef;
import com.erp.module.inventory.api.doc.StockDocEvent;
import com.erp.module.inventory.api.doc.StockOutConfirmedEvent;
import com.erp.module.inventory.api.doc.StockOutRequest;
import com.erp.module.inventory.api.doc.StockOutType;
import com.erp.module.sales.api.order.SalesOrderHeaderDTO;
import com.erp.module.sales.api.order.SalesOrderLineDTO;
import com.erp.module.sales.api.order.SalesOrderQueryApi;
import com.erp.module.sales.api.order.SalesOrderWritebackApi;
import com.erp.module.shipping.api.ShippingErrorCodes;
import com.erp.module.shipping.api.shipment.BillOfLadingReceivedEvent;
import com.erp.module.shipping.api.shipment.ShipmentConfirmedEvent;
import com.erp.module.shipping.api.shipment.ShipmentReversedEvent;
import com.erp.module.shipping.api.shipment.ShipmentSignedEvent;
import com.erp.module.shipping.config.ShippingModuleConfig;
import com.erp.module.shipping.controller.vo.PackingVOs.CartonLineVO;
import com.erp.module.shipping.controller.vo.PackingVOs.CartonVO;
import com.erp.module.shipping.controller.vo.ShipmentVOs.DocRef;
import com.erp.module.shipping.controller.vo.ShipmentVOs.GenerateReq;
import com.erp.module.shipping.controller.vo.ShipmentVOs.LogisticsEventVO;
import com.erp.module.shipping.controller.vo.ShipmentVOs.LogisticsReq;
import com.erp.module.shipping.controller.vo.ShipmentVOs.ShipmentDetail;
import com.erp.module.shipping.controller.vo.ShipmentVOs.ShipmentLineVO;
import com.erp.module.shipping.controller.vo.ShipmentVOs.ShipmentQuery;
import com.erp.module.shipping.controller.vo.ShipmentVOs.ShipmentRow;
import com.erp.module.shipping.controller.vo.ShipmentVOs.ShipmentSave;
import com.erp.module.shipping.controller.vo.ShipmentVOs.SignReq;
import com.erp.module.shipping.controller.vo.ShipmentVOs.SubmitResult;
import com.erp.module.shipping.controller.vo.ShipmentVOs.UnitQty;
import com.erp.module.shipping.dal.dataobject.ShpCartonDO;
import com.erp.module.shipping.dal.dataobject.ShpCartonLineDO;
import com.erp.module.shipping.dal.dataobject.ShpCustomsDO;
import com.erp.module.shipping.dal.dataobject.ShpForwarderDO;
import com.erp.module.shipping.dal.dataobject.ShpInvoiceDO;
import com.erp.module.shipping.dal.dataobject.ShpLogisticsEventDO;
import com.erp.module.shipping.dal.dataobject.ShpNoticeDO;
import com.erp.module.shipping.dal.dataobject.ShpNoticeLineDO;
import com.erp.module.shipping.dal.dataobject.ShpPackingListDO;
import com.erp.module.shipping.dal.dataobject.ShpShipmentDO;
import com.erp.module.shipping.dal.dataobject.ShpShipmentLineDO;
import com.erp.module.shipping.dal.mapper.ShpCartonLineMapper;
import com.erp.module.shipping.dal.mapper.ShpCartonMapper;
import com.erp.module.shipping.dal.mapper.ShpCustomsMapper;
import com.erp.module.shipping.dal.mapper.ShpForwarderMapper;
import com.erp.module.shipping.dal.mapper.ShpInvoiceMapper;
import com.erp.module.shipping.dal.mapper.ShpLogisticsEventMapper;
import com.erp.module.shipping.dal.mapper.ShpNoticeLineMapper;
import com.erp.module.shipping.dal.mapper.ShpPackingListMapper;
import com.erp.module.shipping.dal.mapper.ShpShipmentLineMapper;
import com.erp.module.shipping.dal.mapper.ShpShipmentMapper;
import com.erp.module.shipping.service.NoticeStatus;
import com.erp.module.shipping.service.ShipmentStatus;
import com.erp.module.shipping.service.ShpAction;
import com.erp.module.shipping.service.ShpStateMachines;
import com.erp.module.shipping.service.ShpSupport;
import com.erp.module.shipping.service.notice.NoticeFlow;
import com.erp.module.shipping.service.picking.PackingService;
import com.erp.module.shipping.service.picking.PickingService;
import com.erp.module.shipping.service.picking.PackingService.Unit;
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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 出货单（11-03）：生成、提交出库、出库确认 / 反确认、物流登记与签收 */
@Service
public class ShipmentService {

    private static final String BIZ_TYPE = ShippingModuleConfig.SHIPMENT;
    /** 物流状态顺序（SHP-LOG-R01） */
    public static final List<String> LOGISTICS_ORDER = List.of("BOOKED", "LOADED", "DEPARTED", "ARRIVED", "CLEARED", "DELIVERED");

    private final ShpShipmentMapper mapper;
    private final ShpShipmentLineMapper lineMapper;
    private final ShpNoticeLineMapper noticeLineMapper;
    private final ShpCartonMapper cartonMapper;
    private final ShpCartonLineMapper cartonLineMapper;
    private final ShpForwarderMapper forwarderMapper;
    private final ShpLogisticsEventMapper eventMapper;
    private final ShpPackingListMapper packingListMapper;
    private final ShpInvoiceMapper invoiceMapper;
    private final ShpCustomsMapper customsMapper;
    private final SalesOrderQueryApi orderQueryApi;
    private final SalesOrderWritebackApi writebackApi;
    private final CreditApi creditApi;
    private final InventoryDocApi inventoryDocApi;
    private final WorkflowApi workflowApi;
    private final DomainEventPublisher eventPublisher;
    private final PackingService packingService;
    private final PickingService pickingService;
    private final NoticeFlow flow;
    private final ShpSupport support;

    public ShipmentService(ShpShipmentMapper mapper, ShpShipmentLineMapper lineMapper, ShpNoticeLineMapper noticeLineMapper, ShpCartonMapper cartonMapper,
                           ShpCartonLineMapper cartonLineMapper, ShpForwarderMapper forwarderMapper, ShpLogisticsEventMapper eventMapper,
                           ShpPackingListMapper packingListMapper, ShpInvoiceMapper invoiceMapper, ShpCustomsMapper customsMapper,
                           SalesOrderQueryApi orderQueryApi, SalesOrderWritebackApi writebackApi, CreditApi creditApi, InventoryDocApi inventoryDocApi,
                           WorkflowApi workflowApi, DomainEventPublisher eventPublisher, PackingService packingService, PickingService pickingService,
                           NoticeFlow flow, ShpSupport support) {
        this.mapper = mapper;
        this.lineMapper = lineMapper;
        this.noticeLineMapper = noticeLineMapper;
        this.cartonMapper = cartonMapper;
        this.cartonLineMapper = cartonLineMapper;
        this.forwarderMapper = forwarderMapper;
        this.eventMapper = eventMapper;
        this.packingListMapper = packingListMapper;
        this.invoiceMapper = invoiceMapper;
        this.customsMapper = customsMapper;
        this.orderQueryApi = orderQueryApi;
        this.writebackApi = writebackApi;
        this.creditApi = creditApi;
        this.inventoryDocApi = inventoryDocApi;
        this.workflowApi = workflowApi;
        this.eventPublisher = eventPublisher;
        this.packingService = packingService;
        this.pickingService = pickingService;
        this.flow = flow;
        this.support = support;
    }

    // ==================== 生成 ====================

    /** SHP-SH-R01：通知已装箱（免 OQC）或待出货（OQC 合格）；默认带入全部已装箱未出货的数量，可按箱选择分批出货 */
    @Transactional(rollbackFor = Exception.class)
    public Long generate(Long noticeId, GenerateReq req) {
        ShpNoticeDO n = flow.get(noticeId);
        NoticeStatus st = NoticeFlow.status(n);
        boolean ready = st == NoticeStatus.READY || (st == NoticeStatus.PACKED && !Boolean.TRUE.equals(n.getOqcRequired()));
        if (!ready) throw new BizException(ShippingErrorCodes.SH_NOTICE_NOT_READY);
        GenerateReq r = req == null ? new GenerateReq(null, null, null) : req;
        List<Unit> units = packingService.shippable(n, r.cartonIds());
        if (r.units() != null && !r.units().isEmpty() && units.stream().allMatch(u -> u.cartonIds().isEmpty())) {
            Map<String, Unit> byKey = units.stream().collect(Collectors.toMap(u -> ShpSupport.batchKey(u.noticeLineId(), u.batchNo()), Function.identity()));
            List<Unit> picked = new ArrayList<>();
            for (UnitQty q : r.units()) {
                if (q.qty() == null || q.qty().signum() <= 0) continue;
                Unit u = byKey.get(ShpSupport.batchKey(q.noticeLineId(), ShpSupport.trim(q.batchNo())));
                if (u == null) continue;
                if (q.qty().compareTo(u.qty()) > 0) throw new BizException(ShippingErrorCodes.SH_NOTHING_TO_SHIP);
                picked.add(new Unit(u.noticeLineId(), u.batchNo(), Decimals.qty(q.qty()), List.of()));
            }
            units = picked;
        }
        units = units.stream().filter(u -> u.qty().signum() > 0).toList();
        if (units.isEmpty()) throw new BizException(ShippingErrorCodes.SH_NOTHING_TO_SHIP);
        Map<Long, ShpNoticeLineDO> nls = noticeLineMapper.selectByParent(noticeId).stream().collect(Collectors.toMap(BaseDO::getId, Function.identity()));
        ShpShipmentDO s = new ShpShipmentDO();
        s.setDocNo(support.nextNo(ShippingModuleConfig.SHIPMENT));
        s.setDocDate(LocalDate.now());
        s.setNoticeId(n.getId());
        s.setNoticeNo(n.getDocNo());
        s.setCustomerId(n.getCustomerId());
        s.setCurrency(n.getCurrency());
        s.setShipDate(r.shipDate() != null ? r.shipDate() : LocalDate.now());
        s.setExchangeRate(rate(n.getCurrency(), s.getShipDate()));
        s.setTransportMode(n.getTransportMode());
        s.setTradeTerm(n.getTradeTerm());
        s.setPortOfLoading(n.getPortOfLoading());
        s.setPortOfDestination(n.getPortOfDestination());
        s.setShipToSnapshot(n.getShipToSnapshot());
        s.setWarehouseId(n.getWarehouseId());
        s.setForwarderId(n.getForwarderId());
        s.setCreditWarning(false);
        s.setPrepaymentUnpaid(false);
        s.setEtaReminded(false);
        s.setShipmentStatus(ShipmentStatus.DRAFT.name());
        s.setStatus(ShipmentStatus.DRAFT.docStatus());
        s.setSourceType(ShippingModuleConfig.NOTICE);
        s.setSourceId(n.getId());
        s.setSourceNo(n.getDocNo());
        s.setRemark(n.getRemark());
        support.fillOwner(s, n.getOwnerId());
        mapper.insert(s);
        int no = 1;
        List<ShpShipmentLineDO> lines = new ArrayList<>();
        for (Unit u : units) {
            ShpNoticeLineDO nl = nls.get(u.noticeLineId());
            ShpShipmentLineDO l = new ShpShipmentLineDO();
            l.setShipmentId(s.getId());
            l.setLineNo(no++);
            l.setNoticeLineId(nl.getId());
            l.setOrderId(nl.getOrderId());
            l.setOrderNo(nl.getOrderNo());
            l.setOrderLineId(nl.getOrderLineId());
            l.setMaterialId(nl.getMaterialId());
            l.setCustomerPartNo(nl.getCustomerPartNo());
            l.setDescription(nl.getDescription());
            l.setUom(nl.getUom());
            l.setBaseQty(Decimals.qty(u.qty()));
            l.setQty(orderQty(nl, u.qty()));
            l.setBatchNo(u.batchNo());
            l.setSerialNos(serials(u.cartonIds(), nl.getId(), u.batchNo()));
            price(l, nl.getPriceInclTax(), nl.getTaxRate());
            l.setOutQty(BigDecimal.ZERO);
            lineMapper.insert(l);
            lines.add(l);
        }
        List<Long> cartonIds = units.stream().flatMap(u -> u.cartonIds().stream()).distinct().toList();
        for (Long cid : cartonIds) {
            ShpCartonDO c = cartonMapper.selectById(cid);
            c.setShipmentId(s.getId());
            cartonMapper.updateByIdOrFail(c);
        }
        s = get(s.getId());
        totals(s, lines);
        mapper.updateByIdOrFail(s);
        support.log(BIZ_TYPE, s.getId(), s.getDocNo(), ShpAction.CREATE.name(), ShpAction.CREATE.label(), null, s.getShipmentStatus(),
                "出货通知 " + n.getDocNo() + (cartonIds.isEmpty() ? "" : "，" + cartonIds.size() + " 箱"));
        return s.getId();
    }

    private String serials(List<Long> cartonIds, Long noticeLineId, String batchNo) {
        if (cartonIds.isEmpty()) return null;
        String text = cartonLineMapper.selectByParents(cartonIds).stream()
                .filter(l -> l.getNoticeLineId().equals(noticeLineId) && Objects.equals(l.getBatchNo(), batchNo) && StringUtils.hasText(l.getSerialNos()))
                .map(ShpCartonLineDO::getSerialNos).collect(Collectors.joining(","));
        return text.isEmpty() ? null : text;
    }

    /** 订单单位数量 = 基本单位数量 × 通知数量 ÷ 通知基本数量 */
    static BigDecimal orderQty(ShpNoticeLineDO nl, BigDecimal baseQty) {
        if (nl.getBaseQty().signum() == 0 || nl.getQty().compareTo(nl.getBaseQty()) == 0) return Decimals.qty(baseQty);
        return baseQty.multiply(nl.getQty()).divide(nl.getBaseQty(), Decimals.QTY_SCALE, RoundingMode.HALF_UP);
    }

    /** SHP-SH-R05：金额 = 订单含税单价 × 出货数量；不含税金额 = 价税合计 ÷ (1 + 税率) */
    static void price(ShpShipmentLineDO l, BigDecimal priceInclTax, BigDecimal taxRate) {
        BigDecimal rate = ShpSupport.nz(taxRate);
        BigDecimal total = Decimals.multiplyAmount(l.getQty(), ShpSupport.nz(priceInclTax));
        BigDecimal amount = total.divide(BigDecimal.ONE.add(rate), Decimals.AMOUNT_SCALE, RoundingMode.HALF_UP);
        l.setPriceInclTax(priceInclTax);
        l.setTaxRate(taxRate);
        l.setTotalAmount(total);
        l.setAmount(amount);
        l.setTaxAmount(total.subtract(amount));
    }

    private BigDecimal rate(String currency, LocalDate date) {
        return currency == null ? BigDecimal.ONE : support.currencyApi().getRate(currency, date);
    }

    private void totals(ShpShipmentDO s, List<ShpShipmentLineDO> lines) {
        s.setTotalQty(lines.stream().map(ShpShipmentLineDO::getBaseQty).reduce(BigDecimal.ZERO, BigDecimal::add));
        BigDecimal total = lines.stream().map(ShpShipmentLineDO::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        s.setTotalAmount(Decimals.amount(total));
        s.setTotalAmountBase(Decimals.amount(support.currencyApi().toBase(total, s.getExchangeRate())));
        List<ShpCartonDO> cartons = cartonMapper.selectByShipment(s.getId());
        s.setCartonCount(cartons.isEmpty() ? null : cartons.size());
        s.setGrossWeight(cartons.isEmpty() ? null : sum(cartons, ShpCartonDO::getGrossWeightKg));
        s.setNetWeight(cartons.isEmpty() ? null : sum(cartons, ShpCartonDO::getNetWeightKg));
        s.setCbm(cartons.isEmpty() ? null : sum(cartons, ShpCartonDO::getCbm));
    }

    private static <T> BigDecimal sum(List<T> list, Function<T, BigDecimal> f) {
        return list.stream().map(f).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // ==================== 编辑 / 删除 / 作废 ====================

    /** 单头可编辑：出货日期（汇率随之更新）、货代、柜号、封条号、备注 */
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, ShipmentSave req) {
        ShpShipmentDO s = get(id);
        requireDraft(s);
        s.setShipDate(req.shipDate());
        s.setExchangeRate(rate(s.getCurrency(), req.shipDate()));
        s.setForwarderId(req.forwarderId());
        s.setContainerNo(ShpSupport.limit(req.containerNo(), 32));
        s.setSealNo(ShpSupport.limit(req.sealNo(), 32));
        s.setRemark(ShpSupport.limit(req.remark(), 1000));
        totals(s, lineMapper.selectByParent(id));
        mapper.updateByIdOrFail(s);
        support.bindFiles(req.fileIds(), BIZ_TYPE, id);
        support.log(BIZ_TYPE, id, s.getDocNo(), ShpAction.SAVE.name(), ShpAction.SAVE.label(), null, null, null);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        ShpShipmentDO s = get(id);
        requireDraft(s);
        releaseCartons(id);
        lineMapper.deleteByParent(id);
        mapper.deleteById(id);
        support.log(BIZ_TYPE, id, s.getDocNo(), "DELETE", "删除", s.getShipmentStatus(), null, null);
    }

    @Transactional(rollbackFor = Exception.class)
    public void voidShipment(Long id, String reason) {
        ShpShipmentDO s = get(id);
        requireDraft(s);
        String r = ShpSupport.requireText(reason, "作废原因");
        releaseCartons(id);
        s.setVoidReason(ShpSupport.limit(r, 256));
        fire(s, ShpAction.VOID, r);
    }

    private void releaseCartons(Long shipmentId) {
        cartonMapper.update(null, new LambdaUpdateWrapper<ShpCartonDO>().set(ShpCartonDO::getShipmentId, null).eq(ShpCartonDO::getShipmentId, shipmentId));
    }

    private static void requireDraft(ShpShipmentDO s) {
        ShipmentStatus st = ShipmentStatus.valueOf(s.getShipmentStatus());
        if (st == ShipmentStatus.PENDING) throw new BizException(ShippingErrorCodes.DOC_PENDING);
        if (st != ShipmentStatus.DRAFT) throw BizException.of(ShippingErrorCodes.STATUS_NOT_ALLOWED, st.label(), "修改");
    }

    // ==================== 提交 / 放行 / 撤回 ====================

    /** SHP-SH-R02～R04：黑名单、订单行关闭、超出货、信用、出货前款项、OQC；通过后生成销售出库单（有审批流时先待审批） */
    @Transactional(rollbackFor = Exception.class)
    public SubmitResult submit(Long id) {
        ShpShipmentDO s = get(id);
        requireDraft(s);
        support.customerApi().validateCanShip(s.getCustomerId());
        List<ShpShipmentLineDO> lines = lineMapper.selectByParent(id);
        if (lines.isEmpty()) throw new BizException(ShippingErrorCodes.NO_LINES);
        Map<Long, SalesOrderLineDTO> ols = orderQueryApi.getLines(lines.stream().map(ShpShipmentLineDO::getOrderLineId).toList());
        Map<Long, BigDecimal> perOrderLine = new LinkedHashMap<>();
        for (ShpShipmentLineDO l : lines) perOrderLine.merge(l.getOrderLineId(), l.getBaseQty(), BigDecimal::add);
        for (Map.Entry<Long, BigDecimal> e : perOrderLine.entrySet()) {
            SalesOrderLineDTO ol = ols.get(e.getKey());
            if (ol == null) throw BizException.of(ShippingErrorCodes.NOT_EXISTS, "订单行");
            if ("CLOSED".equals(ol.lineStatus()) || "CLOSED".equals(ol.orderStatus()) || "VOIDED".equals(ol.orderStatus())) {
                throw BizException.of(ShippingErrorCodes.SH_ORDER_CLOSED, ol.orderNo(), ol.lineNo());
            }
            // 允许出货上限 = 订单数量 × (1 + 超出货比例) − 已出货 = 可通知 + 已通知 − 已出货
            BigDecimal allowed = ShpSupport.nz(ol.noticeableQty()).add(ShpSupport.nz(ol.noticedQty())).subtract(ShpSupport.nz(ol.shippedQty()));
            if (e.getValue().compareTo(allowed) > 0) throw BizException.of(ShippingErrorCodes.SH_QTY_EXCEED, ol.orderNo(), ol.lineNo(), ShpSupport.plain(allowed));
        }
        ShpNoticeDO n = flow.get(s.getNoticeId());
        Map<Long, ShpNoticeLineDO> nls = noticeLineMapper.selectByParent(n.getId()).stream().collect(Collectors.toMap(BaseDO::getId, Function.identity()));
        for (ShpShipmentLineDO l : lines) {
            ShpNoticeLineDO nl = nls.get(l.getNoticeLineId());
            if (nl != null && Boolean.TRUE.equals(nl.getOqcRequired()) && !PackingService.OQC_PASSED.equals(n.getOqcResult())) {
                throw BizException.of(ShippingErrorCodes.SH_OQC_NOT_PASSED, support.material(l.getMaterialId()).code());
            }
        }
        List<String> warnings = new ArrayList<>();
        s.setCreditWarning(false);
        CreditCheckResult cr = creditApi.check(s.getCustomerId(), s.getTotalAmountBase(), CreditCheckPoint.SHIPMENT);
        if (!cr.pass()) {
            if ("BLOCK".equals(cr.mode())) throw BizException.of(ShippingErrorCodes.SN_CREDIT_BLOCK, cr.message());
            s.setCreditWarning(true);
            warnings.add(cr.message());
        }
        String mode = support.params().getString(ShippingModuleConfig.P_PREPAYMENT);
        boolean unpaid = false;
        if (!"NONE".equals(mode)) {
            Map<Long, String> orders = new LinkedHashMap<>();
            for (ShpShipmentLineDO l : lines) orders.putIfAbsent(l.getOrderId(), l.getOrderNo());
            for (Map.Entry<Long, String> e : orders.entrySet()) {
                BigDecimal amt = ShpSupport.nz(orderQueryApi.getUnpaidBeforeShipment(e.getKey()));
                if (amt.signum() <= 0) continue;
                if ("BLOCK".equals(mode)) throw BizException.of(ShippingErrorCodes.SH_PREPAYMENT_BLOCK, e.getValue(), ShpSupport.plain(amt));
                unpaid = true;
                warnings.add("订单「" + e.getValue() + "」出货前款项未收齐（" + ShpSupport.plain(amt) + "）");
            }
        }
        s.setPrepaymentUnpaid(unpaid);
        mapper.updateByIdOrFail(s);
        fire(s, ShpAction.SUBMIT, warnings.isEmpty() ? null : String.join("；", warnings));
        Map<String, Object> vars = new HashMap<>();
        vars.put("amountBase", ShpSupport.nz(s.getTotalAmountBase()));
        vars.put("creditWarning", Boolean.TRUE.equals(s.getCreditWarning()));
        vars.put("prepaymentUnpaid", unpaid);
        Map<String, Long> users = new HashMap<>();
        if (s.getOwnerId() != null) users.put("ownerId", s.getOwnerId());
        CustomerDTO c = support.customer(s.getCustomerId());
        StartResult r = workflowApi.start(BIZ_TYPE, id, s.getDocNo(), "出货单 " + s.getDocNo() + "（" + ShpSupport.customerName(c) + "）", vars, users,
                support.currentUser());
        if (!r.isStarted()) release(get(id));
        return new SubmitResult(id, warnings);
    }

    @EventListener
    public void onApproval(ApprovalCompletedEvent e) {
        if (!BIZ_TYPE.equals(e.getBizType())) return;
        ShpShipmentDO s = mapper.selectById(e.getBizId());
        if (s == null || !ShipmentStatus.PENDING.name().equals(s.getShipmentStatus())) return;
        switch (e.getResult()) {
            case APPROVED -> release(s);
            case WITHDRAWN -> fire(s, ShpAction.WITHDRAW, null);
            default -> fire(s, ShpAction.REJECT, e.getComment());
        }
    }

    /** 放行：生成销售出库单（行带批次；参数自动确认时直接出库） */
    private void release(ShpShipmentDO s) {
        fire(s, ShpAction.APPROVE, null);
        List<ShpShipmentLineDO> lines = lineMapper.selectByParent(s.getId());
        Map<Long, MaterialDTO> ms = support.materials(lines.stream().map(ShpShipmentLineDO::getMaterialId).toList());
        List<StockOutRequest.Line> out = lines.stream().map(l -> new StockOutRequest.Line(l.getId(), l.getMaterialId(),
                ms.containsKey(l.getMaterialId()) ? ms.get(l.getMaterialId()).baseUom() : null, l.getBaseQty(), l.getBatchNo(),
                StringUtils.hasText(l.getSerialNos()) ? Arrays.stream(l.getSerialNos().split(",")).map(String::trim).filter(StringUtils::hasText).toList() : null)).toList();
        List<Long> ids = inventoryDocApi.createStockOut(new StockOutRequest(StockOutType.SALES_OUT, new SourceRef(BIZ_TYPE, s.getId(), s.getDocNo()),
                s.getWarehouseId(), s.getShipDate(), null, null, null, s.getCustomerId(), out));
        ShpShipmentDO fresh = get(s.getId());
        if (fresh.getStockOutId() == null && !ids.isEmpty()) {
            fresh.setStockOutId(ids.get(0));
            mapper.updateByIdOrFail(fresh);
        }
        if (ShipmentStatus.SUBMITTED.name().equals(fresh.getShipmentStatus())) {
            support.message(support.usersWithPermission("inv:stock-out:confirm"), "销售出库待确认：" + fresh.getDocNo(),
                    "出货单 " + fresh.getDocNo() + " 已提交，请确认出库", "/inventory/stock-out");
        }
    }

    /** 撤回：出库单未确认时作废出库单，回到草稿 */
    @Transactional(rollbackFor = Exception.class)
    public void withdraw(Long id) {
        ShpShipmentDO s = get(id);
        ShipmentStatus st = ShipmentStatus.valueOf(s.getShipmentStatus());
        if (st == ShipmentStatus.SHIPPED || st == ShipmentStatus.COMPLETED) throw new BizException(ShippingErrorCodes.SH_STOCK_OUT_DONE);
        if (st != ShipmentStatus.SUBMITTED) throw BizException.of(ShippingErrorCodes.STATUS_NOT_ALLOWED, st.label(), "撤回");
        inventoryDocApi.cancelBySource(BIZ_TYPE, id);
        s = get(id);
        s.setStockOutId(null);
        fire(s, ShpAction.WITHDRAW, null);
    }

    // ==================== 仓库事件 ====================

    /** 仓库确认销售出库 → 已出货：回写出库数量、通知已出货、订单已出货，发布 ShipmentConfirmedEvent */
    @EventListener
    public void onStockOut(StockOutConfirmedEvent e) {
        if (e.getSource() == null || !BIZ_TYPE.equals(e.getSource().sourceType())) return;
        ShpShipmentDO s = mapper.selectById(e.getSource().sourceId());
        if (s == null || !ShipmentStatus.SUBMITTED.name().equals(s.getShipmentStatus())) return;
        Map<Long, BigDecimal> outByLine = new HashMap<>();
        Map<Long, List<String>> batches = new HashMap<>();
        for (StockOutConfirmedEvent.Line l : e.getLines()) {
            outByLine.merge(l.sourceLineId(), l.baseQty(), BigDecimal::add);
            if (StringUtils.hasText(l.batchNo())) batches.computeIfAbsent(l.sourceLineId(), k -> new ArrayList<>()).add(l.batchNo());
        }
        List<ShpShipmentLineDO> lines = lineMapper.selectByParent(s.getId());
        Map<Long, BigDecimal> perNoticeLine = new HashMap<>();
        List<SalesOrderWritebackApi.Line> sales = new ArrayList<>();
        List<ShipmentConfirmedEvent.Line> evLines = new ArrayList<>();
        for (ShpShipmentLineDO l : lines) {
            BigDecimal out = Decimals.qty(outByLine.getOrDefault(l.getId(), BigDecimal.ZERO));
            l.setOutQty(out);
            if (!StringUtils.hasText(l.getBatchNo()) && batches.containsKey(l.getId())) {
                l.setBatchNo(ShpSupport.limit(batches.get(l.getId()).stream().distinct().collect(Collectors.joining(",")), 64));
            }
            lineMapper.updateByIdOrFail(l);
            perNoticeLine.merge(l.getNoticeLineId(), out, BigDecimal::add);
            if (out.signum() > 0) sales.add(new SalesOrderWritebackApi.Line(l.getOrderLineId(), out));
            evLines.add(new ShipmentConfirmedEvent.Line(l.getId(), l.getOrderId(), l.getOrderLineId(), l.getMaterialId(), l.getBatchNo(), out,
                    l.getPriceInclTax(), l.getTaxRate(), l.getTotalAmount()));
        }
        perNoticeLine.forEach((nlId, q) -> {
            ShpNoticeLineDO nl = noticeLineMapper.selectById(nlId);
            nl.setShippedQty(Decimals.qty(ShpSupport.nz(nl.getShippedQty()).add(q)));
            noticeLineMapper.updateByIdOrFail(nl);
        });
        s = get(s.getId());
        s.setShipDate(LocalDate.now());
        s.setShippedAt(LocalDateTime.now());
        if (s.getStockOutId() == null) s.setStockOutId(e.getStockOutId());
        fire(s, ShpAction.CONFIRM_OUT, "出库单 " + e.getStockOutNo());
        writebackApi.onShipped(new SalesOrderWritebackApi.ShipmentRecord(s.getId(), s.getDocNo(), s.getShipDate(), sales));
        eventPublisher.publish(new ShipmentConfirmedEvent(s.getId(), s.getDocNo(), s.getCustomerId(), s.getCurrency(), s.getExchangeRate(), s.getShipDate(),
                s.getTotalAmount(), s.getTotalAmountBase(), evLines));
        flow.refreshShipped(s.getNoticeId());
        pickingService.syncReservation(s.getNoticeId());
    }

    /** 仓库反确认（已出货 → 待出库，冲回各方数据、单证失效）；出库单退回（待出库 → 草稿） */
    @EventListener
    public void onStockDoc(StockDocEvent e) {
        if (!"STOCK_OUT".equals(e.getDocType()) || e.getSource() == null || !BIZ_TYPE.equals(e.getSource().sourceType())) return;
        ShpShipmentDO s = mapper.selectById(e.getSource().sourceId());
        if (s == null) return;
        ShipmentStatus st = ShipmentStatus.valueOf(s.getShipmentStatus());
        if (e.getKind() == StockDocEvent.Kind.OUT_REVERSED && (st == ShipmentStatus.SHIPPED || st == ShipmentStatus.COMPLETED)) {
            for (ShpShipmentLineDO l : lineMapper.selectByParent(s.getId())) {
                ShpNoticeLineDO nl = noticeLineMapper.selectById(l.getNoticeLineId());
                nl.setShippedQty(Decimals.qty(ShpSupport.nz(nl.getShippedQty()).subtract(ShpSupport.nz(l.getOutQty()))).max(BigDecimal.ZERO));
                noticeLineMapper.updateByIdOrFail(nl);
                l.setOutQty(BigDecimal.ZERO);
                lineMapper.updateByIdOrFail(l);
            }
            s.setShippedAt(null);
            s.setSignedAt(null);
            fire(s, ShpAction.REVERSE, "出库单 " + e.getDocNo() + " 反确认" + (e.getReason() == null ? "" : "：" + e.getReason()));
            invalidateDocs(s.getId());
            writebackApi.onShipmentReversed(s.getId());
            eventPublisher.publish(new ShipmentReversedEvent(s.getId(), s.getDocNo(), s.getCustomerId(), e.getReason()));
            flow.refreshShipped(s.getNoticeId());
            pickingService.syncReservation(s.getNoticeId());
            support.message(List.of(s.getOwnerId()), "出货单 " + s.getDocNo() + " 已反确认出库", Objects.toString(e.getReason(), ""), "/shipping/shipment/" + s.getId());
        } else if (e.getKind() == StockDocEvent.Kind.REJECTED && st == ShipmentStatus.SUBMITTED) {
            s.setStockOutId(null);
            fire(s, ShpAction.OUT_REJECTED, e.getReason());
            support.message(List.of(s.getOwnerId()), "出货单 " + s.getDocNo() + " 的出库单被仓库退回", Objects.toString(e.getReason(), ""),
                    "/shipping/shipment/" + s.getId());
        }
    }

    /** SHP-DOC-R03：出货单反确认 / 作废时单证标记“已失效” */
    private void invalidateDocs(Long shipmentId) {
        ShpShipmentDO self = mapper.selectById(shipmentId);
        packingListMapper.update(null, new LambdaUpdateWrapper<ShpPackingListDO>().set(ShpPackingListDO::getInvalid, true).and(w -> {
            w.eq(ShpPackingListDO::getShipmentId, shipmentId);
            // 合并的 Packing List：其中任一出货单反确认 / 作废都使之失效
            if (self != null && self.getPackingListId() != null) w.or().eq(ShpPackingListDO::getId, self.getPackingListId());
        }));
        invoiceMapper.update(null, new LambdaUpdateWrapper<ShpInvoiceDO>().set(ShpInvoiceDO::getInvalid, true).eq(ShpInvoiceDO::getShipmentId, shipmentId));
        customsMapper.update(null, new LambdaUpdateWrapper<ShpCustomsDO>().set(ShpCustomsDO::getInvalid, true).eq(ShpCustomsDO::getShipmentId, shipmentId));
    }

    // ==================== 物流：提单、签收 ====================

    /** 登记物流（SHP-SH-R07 提单日期不早于出货日期）；首次 / 变更提单日期时回写回款计划；外销以提单为完成 */
    @Transactional(rollbackFor = Exception.class)
    public void logistics(Long id, LogisticsReq req) {
        ShpShipmentDO s = get(id);
        ShipmentStatus st = ShipmentStatus.valueOf(s.getShipmentStatus());
        if (st != ShipmentStatus.SHIPPED && st != ShipmentStatus.COMPLETED) throw new BizException(ShippingErrorCodes.LOG_NOT_SHIPPED);
        if (req.blDate() != null && s.getShipDate() != null && req.blDate().isBefore(s.getShipDate())) throw new BizException(ShippingErrorCodes.SH_BL_DATE);
        boolean blChanged = req.blDate() != null && !req.blDate().equals(s.getBlDate());
        s.setBlNo(ShpSupport.limit(req.blNo(), 64));
        s.setBlDate(req.blDate());
        if (!Objects.equals(req.eta(), s.getEta())) s.setEtaReminded(false);
        s.setEtd(req.etd());
        s.setEta(req.eta());
        if (req.forwarderId() != null) s.setForwarderId(req.forwarderId());
        if (req.containerNo() != null) s.setContainerNo(ShpSupport.limit(req.containerNo(), 32));
        if (req.sealNo() != null) s.setSealNo(ShpSupport.limit(req.sealNo(), 32));
        mapper.updateByIdOrFail(s);
        support.log(BIZ_TYPE, id, s.getDocNo(), "LOGISTICS", "登记物流", null, null,
                "提单 " + Objects.toString(s.getBlNo(), "-") + " / " + Objects.toString(s.getBlDate(), "-"));
        if (blChanged) {
            writebackApi.onBillOfLading(id, req.blDate());
            eventPublisher.publish(new BillOfLadingReceivedEvent(id, s.getDocNo(), s.getCustomerId(), s.getBlNo(), req.blDate()));
            CustomerDTO c = support.customer(s.getCustomerId());
            if (c.foreign() && st == ShipmentStatus.SHIPPED) complete(get(id), req.blDate().atStartOfDay(), null, "外销以提单为完成");
        }
    }

    /** 登记签收 → 已完成，发布 ShipmentSignedEvent */
    @Transactional(rollbackFor = Exception.class)
    public void sign(Long id, SignReq req) {
        ShpShipmentDO s = get(id);
        if (!ShipmentStatus.SHIPPED.name().equals(s.getShipmentStatus())) {
            if (ShipmentStatus.COMPLETED.name().equals(s.getShipmentStatus()) && s.getSignedAt() == null) {
                s.setSignedAt(req.signedAt());
                s.setSignedBy(ShpSupport.limit(req.signedBy(), 64));
                mapper.updateByIdOrFail(s);
                support.bindFiles(req.fileIds(), BIZ_TYPE, id);
                return;
            }
            throw new BizException(ShippingErrorCodes.LOG_NOT_SHIPPED);
        }
        support.bindFiles(req.fileIds(), BIZ_TYPE, id);
        addEvent(s, "DELIVERED", req.signedAt(), null, req.remark());
        complete(get(id), req.signedAt(), req.signedBy(), req.remark());
    }

    /** 物流状态为“已签收”时出货单自动完成（SHP-LOG-R02） */
    void complete(ShpShipmentDO s, LocalDateTime signedAt, String signedBy, String reason) {
        s.setSignedAt(signedAt);
        if (signedBy != null) s.setSignedBy(ShpSupport.limit(signedBy, 64));
        fire(s, ShpAction.COMPLETE, reason);
        eventPublisher.publish(new ShipmentSignedEvent(s.getId(), s.getDocNo(), s.getCustomerId(), signedAt));
    }

    /** 新增物流记录并刷新出货单最新物流状态（SHP-LOG-R01：不可倒退，倒退需说明原因） */
    @Transactional(rollbackFor = Exception.class)
    public void addLogisticsEvent(Long id, String status, LocalDateTime occurredAt, String location, String remark) {
        ShpShipmentDO s = get(id);
        ShipmentStatus st = ShipmentStatus.valueOf(s.getShipmentStatus());
        if (st != ShipmentStatus.SHIPPED && st != ShipmentStatus.COMPLETED) throw new BizException(ShippingErrorCodes.LOG_NOT_SHIPPED);
        int to = LOGISTICS_ORDER.indexOf(status);
        if (to < 0) throw BizException.of(ShippingErrorCodes.NOT_EXISTS, "物流状态");
        int from = s.getLogisticsStatus() == null ? -1 : LOGISTICS_ORDER.indexOf(s.getLogisticsStatus());
        if (to < from && !StringUtils.hasText(remark)) throw new BizException(ShippingErrorCodes.LOG_BACKWARD);
        addEvent(s, status, occurredAt, location, remark);
        if ("DELIVERED".equals(status) && st == ShipmentStatus.SHIPPED) complete(get(id), occurredAt, null, "物流已签收");
    }

    private void addEvent(ShpShipmentDO s, String status, LocalDateTime occurredAt, String location, String remark) {
        ShpLogisticsEventDO ev = new ShpLogisticsEventDO();
        ev.setShipmentId(s.getId());
        ev.setLogisticsStatus(status);
        ev.setOccurredAt(occurredAt == null ? LocalDateTime.now() : occurredAt);
        ev.setLocation(ShpSupport.limit(location, 128));
        ev.setRemark(ShpSupport.limit(remark, 256));
        ev.setOperatorId(support.currentUser());
        eventMapper.insert(ev);
        ShpShipmentDO fresh = get(s.getId());
        fresh.setLogisticsStatus(status);
        fresh.setLogisticsUpdatedAt(LocalDateTime.now());
        mapper.updateByIdOrFail(fresh);
        support.log(BIZ_TYPE, s.getId(), s.getDocNo(), "LOGISTICS_EVENT", "物流状态", s.getLogisticsStatus(), status, remark);
    }

    private void fire(ShpShipmentDO s, ShpAction action, String reason) {
        ShipmentStatus from = ShipmentStatus.valueOf(s.getShipmentStatus());
        ShipmentStatus to = ShpStateMachines.SHIPMENT.next(from, action)
                .orElseThrow(() -> BizException.of(ShippingErrorCodes.STATUS_NOT_ALLOWED, from.label(), action.label()));
        s.setShipmentStatus(to.name());
        s.setStatus(to.docStatus());
        mapper.updateByIdOrFail(s);
        support.log(BIZ_TYPE, s.getId(), s.getDocNo(), action.name(), action.label(), from.name(), to.name(), reason);
    }

    // ==================== 查询 ====================

    public ShpShipmentDO get(Long id) {
        ShpShipmentDO s = id == null ? null : mapper.selectById(id);
        if (s == null) throw BizException.of(ShippingErrorCodes.NOT_EXISTS, "出货单");
        return s;
    }

    public PageResult<ShipmentRow> page(ShipmentQuery q) {
        LambdaQueryWrapper<ShpShipmentDO> w = query(q);
        if (w == null) return new PageResult<>(List.of(), 0L);
        IPage<ShpShipmentDO> p = mapper.selectScopedPage(new Page<>(q.getPageNo(), q.getPageSize()), w);
        return new PageResult<>(rows(p.getRecords()), p.getTotal());
    }

    public List<ShipmentRow> list(ShipmentQuery q) {
        LambdaQueryWrapper<ShpShipmentDO> w = query(q);
        return w == null ? List.of() : rows(mapper.selectScopedList(w));
    }

    private LambdaQueryWrapper<ShpShipmentDO> query(ShipmentQuery q) {
        List<String> statuses = new ArrayList<>();
        if (StringUtils.hasText(q.getStatuses())) {
            for (String x : q.getStatuses().split(",")) {
                if ("UNSIGNED".equals(x)) statuses.add(ShipmentStatus.SHIPPED.name());
                else statuses.add(x.trim());
            }
        }
        List<String> logistics = new ArrayList<>();
        boolean noneLogistics = false;
        if (StringUtils.hasText(q.getLogisticsStatuses())) {
            for (String x : q.getLogisticsStatuses().split(",")) {
                if ("NONE".equals(x)) noneLogistics = true;
                else logistics.add(x.trim());
            }
        }
        List<Long> byOrder = null;
        if (StringUtils.hasText(q.getOrderNo())) {
            byOrder = lineMapper.selectList(new LambdaQueryWrapper<ShpShipmentLineDO>().like(ShpShipmentLineDO::getOrderNo, q.getOrderNo().trim())).stream()
                    .map(ShpShipmentLineDO::getShipmentId).distinct().toList();
            if (byOrder.isEmpty()) return null;
        }
        boolean none = noneLogistics;
        LambdaQueryWrapper<ShpShipmentDO> w = new LambdaQueryWrapper<ShpShipmentDO>().eq(ShpShipmentDO::getDeleted, false)
                .like(StringUtils.hasText(q.getDocNo()), ShpShipmentDO::getDocNo, q.getDocNo())
                .eq(q.getCustomerId() != null, ShpShipmentDO::getCustomerId, q.getCustomerId())
                .in(byOrder != null, ShpShipmentDO::getId, byOrder)
                .like(StringUtils.hasText(q.getNoticeNo()), ShpShipmentDO::getNoticeNo, q.getNoticeNo())
                .like(StringUtils.hasText(q.getBlNo()), ShpShipmentDO::getBlNo, q.getBlNo())
                .in(!statuses.isEmpty(), ShpShipmentDO::getShipmentStatus, statuses)
                .ge(q.getShipDateFrom() != null, ShpShipmentDO::getShipDate, q.getShipDateFrom())
                .le(q.getShipDateTo() != null, ShpShipmentDO::getShipDate, q.getShipDateTo())
                .eq(StringUtils.hasText(q.getTransportMode()), ShpShipmentDO::getTransportMode, q.getTransportMode())
                .eq(q.getForwarderId() != null, ShpShipmentDO::getForwarderId, q.getForwarderId())
                .ge(q.getEtaFrom() != null, ShpShipmentDO::getEta, q.getEtaFrom())
                .le(q.getEtaTo() != null, ShpShipmentDO::getEta, q.getEtaTo());
        if (!logistics.isEmpty() || none) {
            w.and(x -> {
                if (!logistics.isEmpty()) x.in(ShpShipmentDO::getLogisticsStatus, logistics);
                if (none) {
                    if (!logistics.isEmpty()) x.or();
                    x.isNull(ShpShipmentDO::getLogisticsStatus);
                }
            });
        }
        return w.orderByDesc(ShpShipmentDO::getShipDate).orderByDesc(ShpShipmentDO::getId);
    }

    private List<ShipmentRow> rows(List<ShpShipmentDO> list) {
        Map<Long, CustomerDTO> cus = support.customers(list.stream().map(ShpShipmentDO::getCustomerId).toList());
        Map<Long, UserDTO> users = support.users(list.stream().map(ShpShipmentDO::getOwnerId).toList());
        Map<Long, String> fws = forwarderNames(list.stream().map(ShpShipmentDO::getForwarderId).toList());
        boolean price = support.canSeePrice();
        LocalDate today = LocalDate.now();
        return list.stream().map(s -> new ShipmentRow(s.getId(), s.getDocNo(), s.getNoticeId(), s.getNoticeNo(), s.getCustomerId(),
                ShpSupport.customerName(cus.get(s.getCustomerId())), s.getShipDate(), s.getTransportMode(), s.getPortOfDestination(), s.getTotalQty(),
                s.getCurrency(), price ? s.getTotalAmount() : null, s.getCartonCount(), s.getBlNo(), s.getEtd(), s.getEta(), etaOverdue(s, today),
                s.getForwarderId(), fws.get(s.getForwarderId()), s.getContainerNo(), s.getLogisticsStatus(), s.getLogisticsUpdatedAt(), s.getShipmentStatus(),
                s.getOwnerId(), ShpSupport.name(users, s.getOwnerId()), s.getCreatedAt())).toList();
    }

    /** 已过 ETA 未到港 */
    static boolean etaOverdue(ShpShipmentDO s, LocalDate today) {
        if (s.getEta() == null || !s.getEta().isBefore(today) || !ShipmentStatus.SHIPPED.name().equals(s.getShipmentStatus())) return false;
        int idx = s.getLogisticsStatus() == null ? -1 : LOGISTICS_ORDER.indexOf(s.getLogisticsStatus());
        return idx < LOGISTICS_ORDER.indexOf("ARRIVED");
    }

    private Map<Long, String> forwarderNames(List<Long> ids) {
        List<Long> set = ids.stream().filter(Objects::nonNull).distinct().toList();
        Map<Long, String> map = new HashMap<>();
        if (!set.isEmpty()) forwarderMapper.selectBatchIds(set).forEach(f -> map.put(f.getId(), f.getName()));
        return map;
    }

    public ShipmentDetail detail(Long id) {
        ShpShipmentDO s = get(id);
        List<ShpShipmentLineDO> lines = lineMapper.selectByParent(id);
        Map<Long, MaterialDTO> ms = support.materials(lines.stream().map(ShpShipmentLineDO::getMaterialId).toList());
        Map<Long, SalesOrderHeaderDTO> heads = orderQueryApi.getOrderHeaders(lines.stream().map(ShpShipmentLineDO::getOrderId).distinct().toList());
        boolean price = support.canSeePrice();
        List<ShipmentLineVO> lvs = lines.stream().map(l -> {
            MaterialDTO m = ms.get(l.getMaterialId());
            SalesOrderHeaderDTO h = heads.get(l.getOrderId());
            return new ShipmentLineVO(l.getId(), l.getLineNo(), l.getNoticeLineId(), l.getOrderId(), l.getOrderNo(), l.getOrderLineId(),
                    h == null ? null : h.customerPoNo(), l.getMaterialId(), m == null ? null : m.code(), m == null ? null : m.name(), m == null ? null : m.spec(),
                    l.getCustomerPartNo(), l.getDescription(), l.getUom(), l.getQty(), l.getBaseQty(), m == null ? null : m.baseUom(), l.getBatchNo(),
                    l.getSerialNos(), price ? l.getPriceInclTax() : null, l.getTaxRate(), price ? l.getAmount() : null, price ? l.getTaxAmount() : null,
                    price ? l.getTotalAmount() : null, l.getOutQty());
        }).toList();
        List<CartonVO> cartons = cartons(id);
        Map<Long, UserDTO> ops = new HashMap<>();
        List<ShpLogisticsEventDO> evs = eventMapper.selectByParent(id);
        ops.putAll(support.users(evs.stream().map(ShpLogisticsEventDO::getOperatorId).toList()));
        List<LogisticsEventVO> events = evs.stream().map(ev -> new LogisticsEventVO(ev.getId(), ev.getLogisticsStatus(), ev.getOccurredAt(), ev.getLocation(),
                ev.getRemark(), ev.getOperatorId(), ShpSupport.name(ops, ev.getOperatorId()))).toList();
        ShpPackingListDO pl = s.getPackingListId() != null ? packingListMapper.selectById(s.getPackingListId())
                : packingListMapper.selectOne(new LambdaQueryWrapper<ShpPackingListDO>().eq(ShpPackingListDO::getShipmentId, id).last("LIMIT 1"));
        ShpInvoiceDO inv = invoiceMapper.selectOne(new LambdaQueryWrapper<ShpInvoiceDO>().eq(ShpInvoiceDO::getShipmentId, id).last("LIMIT 1"));
        ShpCustomsDO cd = customsMapper.selectOne(new LambdaQueryWrapper<ShpCustomsDO>().eq(ShpCustomsDO::getShipmentId, id).last("LIMIT 1"));
        ShpForwarderDO fw = s.getForwarderId() == null ? null : forwarderMapper.selectById(s.getForwarderId());
        CustomerDTO c = support.customer(s.getCustomerId());
        return new ShipmentDetail(s.getId(), s.getDocNo(), s.getDocDate(), s.getStatus().name(), s.getShipmentStatus(), s.getNoticeId(), s.getNoticeNo(),
                s.getCustomerId(), ShpSupport.customerName(c), c.foreign(), s.getCurrency(), s.getExchangeRate(), s.getShipDate(), s.getTransportMode(),
                s.getTradeTerm(), s.getPortOfLoading(), s.getPortOfDestination(), support.addressText(s.getShipToSnapshot()), s.getWarehouseId(),
                support.warehouseName(s.getWarehouseId()), s.getTotalQty(), price ? s.getTotalAmount() : null, price ? s.getTotalAmountBase() : null,
                s.getCartonCount(), s.getGrossWeight(), s.getNetWeight(), s.getCbm(), s.getStockOutId(),
                pl == null ? null : new DocRef(pl.getId(), pl.getPlNo(), Boolean.TRUE.equals(pl.getInvalid())),
                inv == null ? null : new DocRef(inv.getId(), inv.getInvoiceNo(), Boolean.TRUE.equals(inv.getInvalid())),
                cd == null ? null : new DocRef(cd.getId(), cd.getDocCode(), Boolean.TRUE.equals(cd.getInvalid())), s.getForwarderId(),
                fw == null ? null : fw.getName(), s.getContainerNo(), s.getSealNo(), s.getBlNo(), s.getBlDate(), s.getEtd(), s.getEta(), s.getLogisticsStatus(),
                s.getLogisticsUpdatedAt(), s.getSignedAt(), s.getSignedBy(), Boolean.TRUE.equals(s.getCreditWarning()), Boolean.TRUE.equals(s.getPrepaymentUnpaid()),
                s.getShippedAt(), s.getVoidReason(), s.getOwnerId(), support.userName(s.getOwnerId()), s.getRemark(), lvs, cartons, PackingService.totals(cartons), events,
                s.getCreatedBy(), support.userName(s.getCreatedBy()), s.getCreatedAt(), price);
    }

    /** 出货单的箱（启用装箱时） */
    public List<CartonVO> cartons(Long shipmentId) {
        List<ShpCartonDO> cs = cartonMapper.selectByShipment(shipmentId);
        Map<Long, List<ShpCartonLineDO>> ls = cartonLineMapper.selectByParents(cs.stream().map(BaseDO::getId).toList()).stream()
                .collect(Collectors.groupingBy(ShpCartonLineDO::getCartonId));
        List<ShpNoticeLineDO> nls = cs.isEmpty() ? List.of() : noticeLineMapper.selectByParent(cs.get(0).getNoticeId());
        Map<Long, ShpNoticeLineDO> nlById = nls.stream().collect(Collectors.toMap(BaseDO::getId, Function.identity()));
        Map<Long, MaterialDTO> ms = support.materials(nls.stream().map(ShpNoticeLineDO::getMaterialId).toList());
        return cs.stream().map(c -> new CartonVO(c.getId(), c.getCartonNo(), c.getCartonSpec(), c.getLengthCm(), c.getWidthCm(), c.getHeightCm(),
                c.getGrossWeightKg(), c.getNetWeightKg(), c.getCbm(), c.getPalletNo(), c.getShipmentId(), null,
                ls.getOrDefault(c.getId(), List.of()).stream().map(l -> {
                    ShpNoticeLineDO nl = nlById.get(l.getNoticeLineId());
                    MaterialDTO m = ms.get(l.getMaterialId());
                    return new CartonLineVO(l.getId(), l.getNoticeLineId(), nl == null ? null : nl.getLineNo(), l.getMaterialId(), m == null ? null : m.code(),
                            m == null ? null : m.name(), nl == null ? null : nl.getCustomerPartNo(), l.getBatchNo(), l.getQty(), l.getSerialNos());
                }).toList())).toList();
    }

    public Map<String, Object> printData(Long id) {
        ShipmentDetail d = detail(id);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("docNo", d.docNo());
        m.put("status", ShipmentStatus.valueOf(d.shipmentStatus()).label());
        m.put("customerName", d.customerName());
        // 针式送货单只打印客户编号
        m.put("customerCode", d.customerId() == null ? "" : support.customerApi().getCustomer(d.customerId()).map(c -> Objects.toString(c.code(), "")).orElse(""));
        m.put("shipDate", d.shipDate());
        m.put("shipToText", Objects.toString(d.shipToText(), ""));
        m.put("transportModeName", support.dictLabel("shp_transport_mode", d.transportMode()));
        m.put("blNo", Objects.toString(d.blNo(), ""));
        m.put("cartonCount", d.cartonCount());
        m.put("docDate", d.docDate());
        m.put("statusName", ShipmentStatus.valueOf(d.shipmentStatus()).label());
        m.put("noticeNo", Objects.toString(d.noticeNo(), ""));
        m.put("warehouseName", Objects.toString(d.warehouseName(), ""));
        m.put("totalQty", d.totalQty());
        m.put("forwarderName", Objects.toString(d.forwarderName(), ""));
        support.putPrintSignature(m, mapper.selectById(id));
        m.put("lines", d.lines().stream().map(l -> {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("lineNo", l.lineNo());
            r.put("orderNo", l.orderNo());
            r.put("materialCode", l.materialCode());
            r.put("materialName", l.materialName());
            var mat = support.material(l.materialId());
            r.put("materialSpec", mat == null ? "" : Objects.toString(mat.spec(), ""));
            r.put("batchNo", Objects.toString(l.batchNo(), ""));
            r.put("qty", l.qty());
            r.put("uom", l.uom());
            return r;
        }).toList());
        return m;
    }

    /** ETA 已过 N 天仍未到港的出货（SHP-LOG-R03） */
    public List<ShpShipmentDO> etaOverdueList(LocalDate before) {
        return mapper.selectList(new LambdaQueryWrapper<ShpShipmentDO>().eq(ShpShipmentDO::getShipmentStatus, ShipmentStatus.SHIPPED.name())
                .lt(ShpShipmentDO::getEta, before).eq(ShpShipmentDO::getEtaReminded, false)).stream()
                .filter(s -> etaOverdue(s, before)).toList();
    }

    public void markEtaReminded(ShpShipmentDO s) {
        s.setEtaReminded(true);
        mapper.updateByIdOrFail(s);
    }
}
