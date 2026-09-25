package com.erp.module.purchase.service.order;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.common.enums.DocStatus;
import com.erp.common.exception.BizException;
import com.erp.common.exception.GlobalErrorCodes;
import com.erp.common.result.PageResult;
import com.erp.common.util.Decimals;
import com.erp.framework.datascope.DataScopes;
import com.erp.framework.event.DomainEventPublisher;
import com.erp.module.engineering.api.material.MaterialDTO;
import com.erp.module.purchase.api.PurchaseErrorCodes;
import com.erp.module.purchase.api.order.PurchaseOrderChangedEvent;
import com.erp.module.purchase.config.PurchaseModuleConfig;
import com.erp.module.purchase.controller.vo.CommonVOs.DocResult;
import com.erp.module.purchase.controller.vo.OrderVOs.ChangeDetail;
import com.erp.module.purchase.controller.vo.OrderVOs.ChangeLineResp;
import com.erp.module.purchase.controller.vo.OrderVOs.ChangeLineSave;
import com.erp.module.purchase.controller.vo.OrderVOs.ChangeQuery;
import com.erp.module.purchase.controller.vo.OrderVOs.ChangeRow;
import com.erp.module.purchase.controller.vo.OrderVOs.ChangeSave;
import com.erp.module.purchase.controller.vo.OrderVOs.OrderLineResp;
import com.erp.module.purchase.dal.dataobject.OrderChangeDO;
import com.erp.module.purchase.dal.dataobject.OrderChangeLineDO;
import com.erp.module.purchase.dal.dataobject.OrderDO;
import com.erp.module.purchase.dal.dataobject.OrderLineDO;
import com.erp.module.purchase.dal.dataobject.SupplierDO;
import com.erp.module.purchase.dal.mapper.OrderChangeLineMapper;
import com.erp.module.purchase.dal.mapper.OrderChangeMapper;
import com.erp.module.purchase.dal.mapper.OrderLineMapper;
import com.erp.module.purchase.dal.mapper.OrderMapper;
import com.erp.module.purchase.service.PurAction;
import com.erp.module.purchase.service.PurStateMachines;
import com.erp.module.purchase.service.PurSupport;
import com.erp.module.purchase.service.requisition.RequisitionService;
import com.erp.module.purchase.service.supplier.SupplierService;
import com.erp.module.system.api.currency.CurrencyApi;
import com.erp.module.system.api.file.FileApi;
import com.erp.module.system.api.user.UserDTO;
import com.erp.module.system.api.workflow.ApprovalCompletedEvent;
import com.erp.module.system.api.workflow.StartResult;
import com.erp.module.system.api.workflow.WorkflowApi;
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
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 采购订单变更单（需求 07-05 第 2 节 pur_order_change）：按订单创建（带出当前行），审批通过后应用到订单，
 * 订单版本 + 1 并保存变更前快照，发布 PurchaseOrderChangedEvent。
 */
@Service
public class OrderChangeService {

    public static final String BIZ_TYPE = PurchaseModuleConfig.ORDER_CHANGE;
    static final Set<String> TYPES = Set.of("ADD", "MODIFY", "CANCEL");

    private final OrderChangeMapper mapper;
    private final OrderChangeLineMapper lineMapper;
    private final OrderMapper orderMapper;
    private final OrderLineMapper orderLineMapper;
    private final OrderService orderService;
    private final RequisitionService requisitionService;
    private final SupplierService supplierService;
    private final PurSupport support;
    private final CurrencyApi currencyApi;
    private final WorkflowApi workflowApi;
    private final FileApi fileApi;
    private final DomainEventPublisher eventPublisher;

    public OrderChangeService(OrderChangeMapper mapper, OrderChangeLineMapper lineMapper, OrderMapper orderMapper, OrderLineMapper orderLineMapper,
                              OrderService orderService, RequisitionService requisitionService, SupplierService supplierService, PurSupport support,
                              CurrencyApi currencyApi, WorkflowApi workflowApi, FileApi fileApi, DomainEventPublisher eventPublisher) {
        this.mapper = mapper;
        this.lineMapper = lineMapper;
        this.orderMapper = orderMapper;
        this.orderLineMapper = orderLineMapper;
        this.orderService = orderService;
        this.requisitionService = requisitionService;
        this.supplierService = supplierService;
        this.support = support;
        this.currencyApi = currencyApi;
        this.workflowApi = workflowApi;
        this.fileApi = fileApi;
        this.eventPublisher = eventPublisher;
    }

    public PageResult<ChangeRow> page(ChangeQuery q) {
        LambdaQueryWrapper<OrderChangeDO> w = new LambdaQueryWrapper<OrderChangeDO>()
                .eq(q.getOrderId() != null, OrderChangeDO::getOrderId, q.getOrderId())
                .likeRight(StringUtils.hasText(q.getDocNo()), OrderChangeDO::getDocNo, q.getDocNo() == null ? null : q.getDocNo().trim().toUpperCase());
        if (StringUtils.hasText(q.getStatuses())) {
            w.in(OrderChangeDO::getStatus, Arrays.stream(q.getStatuses().split(",")).map(String::trim).map(DocStatus::valueOf).toList());
        }
        PageResult<OrderChangeDO> page = mapper.selectPage(q, w.orderByDesc(OrderChangeDO::getId));
        Map<Long, OrderDO> orders = orderService.byIds(page.list().stream().map(OrderChangeDO::getOrderId).toList());
        Map<Long, UserDTO> users = support.users(page.list().stream().map(OrderChangeDO::getOwnerId).toList());
        boolean price = PurSupport.canViewPrice();
        return new PageResult<>(page.list().stream().map(c -> new ChangeRow(c.getId(), c.getDocNo(), c.getDocDate(), c.getOrderId(),
                orders.containsKey(c.getOrderId()) ? orders.get(c.getOrderId()).getDocNo() : null, c.getChangeReason(), c.getNewVersion(),
                PurSupport.mask(c.getAmountChangeBase(), price), c.getStatus().name(), PurSupport.name(users, c.getOwnerId()), c.getCreatedAt())).toList(),
                page.total());
    }

    /** 新建变更单时带出订单当前行（未关闭） */
    public List<OrderLineResp> template(Long orderId) {
        OrderDO o = orderService.getOrThrow(orderId);
        DataScopes.check(o.getOrgId(), o.getDeptId(), o.getOwnerId(), "采购订单");
        return orderService.lineResps(orderLineMapper.selectByParent(orderId), PurSupport.canViewPrice());
    }

    public ChangeDetail detail(Long id) {
        OrderChangeDO c = getOrThrow(id);
        OrderDO o = orderService.getOrThrow(c.getOrderId());
        DataScopes.check(o.getOrgId(), o.getDeptId(), o.getOwnerId(), "采购订单");
        List<OrderChangeLineDO> lines = lineMapper.selectByParent(id);
        Map<Long, MaterialDTO> ms = support.materials(lines.stream().map(OrderChangeLineDO::getMaterialId).toList());
        Map<Long, OrderLineDO> ols = orderService.linesByIds(lines.stream().map(OrderChangeLineDO::getOrderLineId).toList());
        boolean price = PurSupport.canViewPrice();
        return new ChangeDetail(c.getId(), c.getDocNo(), c.getDocDate(), c.getStatus().name(), o.getId(), o.getDocNo(), o.getOrderVersion(),
                c.getChangeReason(), c.getNewVersion(), PurSupport.mask(c.getAmountChangeBase(), price), support.userName(c.getOwnerId()), c.getCreatedAt(),
                c.getVersion(), price, lines.stream().map(l -> {
                    MaterialDTO m = ms.get(l.getMaterialId());
                    OrderLineDO ol = ols.get(l.getOrderLineId());
                    return new ChangeLineResp(l.getId(), l.getLineNo(), l.getOrderLineId(), ol == null ? null : ol.getLineNo(), l.getChangeType(),
                            l.getMaterialId(), m == null ? null : m.code(), m == null ? null : m.name(), l.getUom(), l.getOldQty(), l.getNewQty(),
                            PurSupport.mask(l.getOldPrice(), price), PurSupport.mask(l.getNewPrice(), price), l.getTaxRate(), l.getOldRequiredDate(),
                            l.getNewRequiredDate(), ol == null ? null : OrderService.toUom(ol, ol.getReceivedQty()), l.getRemark());
                }).toList());
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(ChangeSave req) {
        OrderDO o = orderService.getOrThrow(req.orderId());
        DataScopes.check(o.getOrgId(), o.getDeptId(), o.getOwnerId(), "采购订单");
        if (!OrderService.ACTIVE.contains(o.getStatus())) throw BizException.of(GlobalErrorCodes.ILLEGAL_STATE_TRANSITION, o.getStatus().label(), "变更");
        orderService.checkNoRunningChange(o.getId());
        OrderChangeDO c = new OrderChangeDO();
        c.setDocNo(support.nextNo(BIZ_TYPE));
        c.setDocDate(LocalDate.now());
        c.setStatus(DocStatus.DRAFT);
        c.setOrderId(o.getId());
        c.setSourceType(PurchaseModuleConfig.ORDER);
        c.setSourceId(o.getId());
        c.setSourceNo(o.getDocNo());
        c.setChangeReason(req.changeReason().trim());
        c.setNewVersion(o.getOrderVersion() + 1);
        c.setAmountChangeBase(BigDecimal.ZERO);
        support.fillOwner(c, o.getOwnerId());
        mapper.insert(c);
        saveLines(c, o, req.lines());
        mapper.updateByIdOrFail(c);
        if (req.fileIds() != null && !req.fileIds().isEmpty()) fileApi.bind(req.fileIds(), BIZ_TYPE, c.getId());
        return c.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, ChangeSave req) {
        OrderChangeDO c = getOrThrow(id);
        PurSupport.requireDraft(c);
        OrderDO o = orderService.getOrThrow(c.getOrderId());
        DataScopes.check(o.getOrgId(), o.getDeptId(), o.getOwnerId(), "采购订单");
        if (req.version() != null) c.setVersion(req.version());
        c.setChangeReason(req.changeReason().trim());
        saveLines(c, o, req.lines());
        mapper.updateByIdOrFail(c);
        if (req.fileIds() != null && !req.fileIds().isEmpty()) fileApi.bind(req.fileIds(), BIZ_TYPE, c.getId());
    }

    /** 只保存有变化的行；校验 R07；计算金额变化（本位币） */
    private void saveLines(OrderChangeDO c, OrderDO o, List<ChangeLineSave> lines) {
        Map<Long, OrderLineDO> current = orderLineMapper.selectByParent(o.getId()).stream().collect(Collectors.toMap(OrderLineDO::getId, l -> l));
        SupplierDO s = supplierService.getOrThrow(o.getSupplierId());
        lineMapper.deleteByParent(c.getId());
        BigDecimal delta = BigDecimal.ZERO;
        int no = 0;
        for (ChangeLineSave l : lines == null ? List.<ChangeLineSave>of() : lines) {
            if (l.changeType() == null || !TYPES.contains(l.changeType())) throw BizException.of(GlobalErrorCodes.BAD_REQUEST, "变更类型");
            OrderChangeLineDO d = new OrderChangeLineDO();
            d.setChangeId(c.getId());
            d.setChangeType(l.changeType());
            d.setRemark(PurSupport.trim(l.remark()));
            if ("ADD".equals(l.changeType())) {
                if (l.materialId() == null) throw BizException.of(PurchaseErrorCodes.LINE_FIELD_REQUIRED, no + 1, "物料");
                if (l.newQty() == null || l.newQty().signum() <= 0) throw BizException.of(PurchaseErrorCodes.LINE_QTY_POSITIVE, no + 1);
                if (l.newRequiredDate() == null) throw BizException.of(PurchaseErrorCodes.LINE_FIELD_REQUIRED, no + 1, "要求到货日期");
                MaterialDTO m = support.materialApi().validateUsable(l.materialId());
                d.setMaterialId(m.id());
                d.setUom(StringUtils.hasText(l.uom()) ? l.uom() : support.materialApi().getPurchaseAttr(m.id()).purchaseUom());
                d.setNewQty(Decimals.qty(l.newQty()));
                d.setNewPrice(l.newPrice() == null ? BigDecimal.ZERO : Decimals.price(l.newPrice()));
                d.setTaxRate(l.taxRate() != null ? l.taxRate() : s.getPurchaseTaxRate());
                d.setNewRequiredDate(l.newRequiredDate());
                delta = delta.add(total(d.getNewQty(), d.getNewPrice(), d.getTaxRate()));
            } else {
                OrderLineDO ol = l.orderLineId() == null ? null : current.get(l.orderLineId());
                if (ol == null) throw new BizException(PurchaseErrorCodes.ORDER_LINE_NOT_EXISTS);
                d.setOrderLineId(ol.getId());
                d.setMaterialId(ol.getMaterialId());
                d.setUom(ol.getUom());
                d.setOldQty(ol.getQty());
                d.setOldPrice(ol.getPrice());
                d.setTaxRate(ol.getTaxRate());
                d.setOldRequiredDate(ol.getRequiredDate());
                if ("CANCEL".equals(l.changeType())) {
                    if (ol.getReceivedQty().signum() > 0) throw BizException.of(PurchaseErrorCodes.ORDER_LINE_RECEIVED, ol.getLineNo());
                    d.setNewQty(BigDecimal.ZERO);
                    delta = delta.subtract(ol.getTotalAmount());
                } else {
                    d.setNewQty(l.newQty() == null ? ol.getQty() : Decimals.qty(l.newQty()));
                    d.setNewPrice(l.newPrice() == null ? ol.getPrice() : Decimals.price(l.newPrice()));
                    d.setNewRequiredDate(l.newRequiredDate() == null ? ol.getRequiredDate() : l.newRequiredDate());
                    boolean changed = d.getNewQty().compareTo(ol.getQty()) != 0 || d.getNewPrice().compareTo(ol.getPrice()) != 0
                            || !d.getNewRequiredDate().equals(ol.getRequiredDate());
                    if (!changed) continue;
                    checkModify(ol, d);
                    delta = delta.add(total(d.getNewQty(), d.getNewPrice(), d.getTaxRate())).subtract(ol.getTotalAmount());
                }
            }
            d.setLineNo(++no);
            lineMapper.insert(d);
        }
        if (no == 0) throw new BizException(PurchaseErrorCodes.ORDER_CHANGE_NOTHING);
        c.setAmountChangeBase(currencyApi.toBase(delta, o.getExchangeRate()));
    }

    /** R07：新数量 ≥ 已到货数量；已到货完的行不能修改单价；已对账的行任何字段不能改 */
    private void checkModify(OrderLineDO ol, OrderChangeLineDO d) {
        if (ol.getStatementQty().signum() > 0) throw BizException.of(PurchaseErrorCodes.ORDER_CHANGE_STATEMENT_LOCKED, ol.getLineNo());
        BigDecimal received = OrderService.toUom(ol, ol.getReceivedQty());
        if (d.getNewQty().compareTo(received) < 0) {
            throw BizException.of(PurchaseErrorCodes.ORDER_CHANGE_QTY_LT_RECEIVED, ol.getLineNo(), PurSupport.plain(received));
        }
        if (d.getNewQty().signum() <= 0) throw BizException.of(PurchaseErrorCodes.LINE_QTY_POSITIVE, ol.getLineNo());
        if (ol.getReceivedQty().compareTo(ol.getBaseQty()) >= 0 && d.getNewPrice().compareTo(ol.getPrice()) != 0) {
            throw BizException.of(PurchaseErrorCodes.ORDER_CHANGE_PRICE_LOCKED, ol.getLineNo());
        }
    }

    private static BigDecimal total(BigDecimal qty, BigDecimal price, BigDecimal rate) {
        BigDecimal amount = Decimals.multiplyAmount(qty, price);
        return amount.add(Decimals.amount(amount.multiply(rate)));
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        OrderChangeDO c = getOrThrow(id);
        PurSupport.requireDraft(c);
        lineMapper.deleteByParent(id);
        mapper.deleteById(id);
        fileApi.deleteByBiz(BIZ_TYPE, id);
    }

    @Transactional(rollbackFor = Exception.class)
    public DocResult submit(Long id) {
        OrderChangeDO c = getOrThrow(id);
        PurSupport.requireDraft(c);
        OrderDO o = orderService.getOrThrow(c.getOrderId());
        DataScopes.check(o.getOrgId(), o.getDeptId(), o.getOwnerId(), "采购订单");
        if (!OrderService.ACTIVE.contains(o.getStatus())) throw BizException.of(GlobalErrorCodes.ILLEGAL_STATE_TRANSITION, o.getStatus().label(), "变更");
        Map<Long, OrderLineDO> current = orderLineMapper.selectByParent(o.getId()).stream().collect(Collectors.toMap(OrderLineDO::getId, l -> l));
        for (OrderChangeLineDO l : lineMapper.selectByParent(id)) {
            OrderLineDO ol = l.getOrderLineId() == null ? null : current.get(l.getOrderLineId());
            if ("MODIFY".equals(l.getChangeType()) && ol != null) checkModify(ol, l);
            if ("CANCEL".equals(l.getChangeType()) && ol != null && ol.getReceivedQty().signum() > 0) {
                throw BizException.of(PurchaseErrorCodes.ORDER_LINE_RECEIVED, ol.getLineNo());
            }
        }
        support.fire(PurStateMachines.SIMPLE, mapper, c, BIZ_TYPE, PurAction.SUBMIT, null);
        Map<String, Object> vars = new HashMap<>();
        vars.put("amountChangeBase", c.getAmountChangeBase());
        Map<String, Long> users = new HashMap<>();
        users.put("buyerId", o.getOwnerId());
        StartResult r = workflowApi.start(BIZ_TYPE, id, c.getDocNo(), "订单变更 " + o.getDocNo() + " → V" + c.getNewVersion(), vars, users, support.currentUser());
        if (!r.isStarted()) approve(c);
        return DocResult.of(c.getStatus().name());
    }

    @EventListener
    public void onApproval(ApprovalCompletedEvent e) {
        if (!BIZ_TYPE.equals(e.getBizType())) return;
        OrderChangeDO c = getOrThrow(e.getBizId());
        if (c.getStatus() != DocStatus.PENDING_APPROVAL) return;
        switch (e.getResult()) {
            case APPROVED -> approve(c);
            case WITHDRAWN -> support.fire(PurStateMachines.SIMPLE, mapper, c, BIZ_TYPE, PurAction.WITHDRAW, null);
            default -> support.fire(PurStateMachines.SIMPLE, mapper, c, BIZ_TYPE, PurAction.REJECT, e.getComment());
        }
    }

    /** 审批通过：保存变更前快照 → 应用到订单 → 版本 + 1 → 重新汇总 → 发布 PurchaseOrderChangedEvent */
    private void approve(OrderChangeDO c) {
        OrderDO o = orderService.getOrThrow(c.getOrderId());
        List<OrderLineDO> before = orderLineMapper.selectByParent(o.getId());
        Map<String, Object> snap = new LinkedHashMap<>();
        snap.put("orderVersion", o.getOrderVersion());
        snap.put("totalAmount", o.getTotalAmount());
        snap.put("lines", before.stream().map(l -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("lineNo", l.getLineNo());
            m.put("materialId", String.valueOf(l.getMaterialId()));
            m.put("uom", l.getUom());
            m.put("qty", l.getQty());
            m.put("price", l.getPrice());
            m.put("priceInclTax", l.getPriceInclTax());
            m.put("totalAmount", l.getTotalAmount());
            m.put("requiredDate", String.valueOf(l.getRequiredDate()));
            return m;
        }).toList());
        c.setSnapshot(orderService.json(snap));
        support.fire(PurStateMachines.SIMPLE, mapper, c, BIZ_TYPE, PurAction.APPROVE, null);

        Map<Long, OrderLineDO> current = before.stream().collect(Collectors.toMap(OrderLineDO::getId, l -> l));
        int maxNo = before.stream().mapToInt(OrderLineDO::getLineNo).max().orElse(0);
        SupplierDO s = supplierService.getOrThrow(o.getSupplierId());
        List<Long> touched = new ArrayList<>();
        List<Long> reqLines = new ArrayList<>();
        for (OrderChangeLineDO l : lineMapper.selectByParent(c.getId())) {
            switch (l.getChangeType()) {
                case "ADD" -> {
                    OrderLineDO d = new OrderLineDO();
                    d.setOrderId(o.getId());
                    d.setLineNo(++maxNo);
                    d.setMaterialId(l.getMaterialId());
                    d.setUom(l.getUom());
                    d.setQty(l.getNewQty());
                    d.setBaseQty(Decimals.qty(support.toBase(l.getMaterialId(), l.getNewQty(), l.getUom())));
                    d.setTaxRate(l.getTaxRate());
                    d.setRequiredDate(l.getNewRequiredDate());
                    d.setSupplierPartNo(supplierService.supplierMaterial(s.getId(), l.getMaterialId()).map(x -> x.getSupplierPartNo()).orElse(null));
                    d.setRemark(l.getRemark());
                    OrderService.initQty(d);
                    orderService.priceLine(o, s, d, support.material(l.getMaterialId()), l.getNewPrice(), d.getLineNo(), new ArrayList<>());
                    // 新增行按不含税单价录入
                    OrderService.computeAmounts(d, false, l.getNewPrice());
                    BigDecimal tol = support.params().getDecimal(PurchaseModuleConfig.P_PRICE_OVERRUN_PCT);
                    d.setPriceOverrun(d.getListPrice() != null
                            && d.getPrice().compareTo(d.getListPrice().multiply(BigDecimal.ONE.add(PurSupport.nz(tol).divide(PurSupport.HUNDRED)))) > 0);
                    orderLineMapper.insert(d);
                }
                case "CANCEL" -> {
                    OrderLineDO d = current.get(l.getOrderLineId());
                    if (d == null) continue;
                    if (d.getRequisitionLineId() != null) reqLines.add(d.getRequisitionLineId());
                    orderLineMapper.deleteById(d.getId());
                }
                default -> {
                    OrderLineDO d = current.get(l.getOrderLineId());
                    if (d == null) continue;
                    d.setQty(l.getNewQty());
                    d.setBaseQty(Decimals.qty(support.toBase(d.getMaterialId(), l.getNewQty(), d.getUom())));
                    d.setRequiredDate(l.getNewRequiredDate());
                    OrderService.computeAmounts(d, false, l.getNewPrice());
                    BigDecimal tol = support.params().getDecimal(PurchaseModuleConfig.P_PRICE_OVERRUN_PCT);
                    d.setPriceOverrun(d.getListPrice() != null
                            && d.getPrice().compareTo(d.getListPrice().multiply(BigDecimal.ONE.add(PurSupport.nz(tol).divide(PurSupport.HUNDRED)))) > 0);
                    orderLineMapper.updateByIdOrFail(d);
                    touched.add(d.getId());
                    if (d.getRequisitionLineId() != null) reqLines.add(d.getRequisitionLineId());
                }
            }
        }
        o = orderService.getOrThrow(o.getId());
        o.setOrderVersion(c.getNewVersion());
        orderService.recalcTotals(o);
        orderMapper.updateByIdOrFail(o);
        support.log(OrderService.BIZ_TYPE, o.getId(), o.getDocNo(), "CHANGE", "变更生效", o.getStatus().name(), o.getStatus().name(),
                c.getDocNo() + "：" + c.getChangeReason());
        orderService.refreshLines(orderLineMapper.selectByParent(o.getId()).stream().map(OrderLineDO::getId).toList());
        orderService.refreshStatus(o.getId());
        requisitionService.refreshOrdered(reqLines.stream().filter(Objects::nonNull).toList());
        eventPublisher.publish(new PurchaseOrderChangedEvent(o.getId(), o.getDocNo(), c.getNewVersion(), "CHANGE",
                orderLineMapper.selectByParent(o.getId()).stream().map(OrderLineDO::getMaterialId).distinct().toList()));
    }

    public OrderChangeDO getOrThrow(Long id) {
        OrderChangeDO c = id == null ? null : mapper.selectById(id);
        if (c == null) throw new BizException(PurchaseErrorCodes.ORDER_CHANGE_NOT_EXISTS);
        return c;
    }
}
