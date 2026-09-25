package com.erp.module.purchase.service.order;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.common.enums.DocStatus;
import com.erp.common.exception.BizException;
import com.erp.common.exception.GlobalErrorCodes;
import com.erp.common.result.PageResult;
import com.erp.common.util.Decimals;
import com.erp.framework.datascope.DataScopes;
import com.erp.framework.event.DomainEventPublisher;
import com.erp.module.engineering.api.material.MaterialDTO;
import com.erp.module.engineering.api.material.MaterialPurchaseAttr;
import com.erp.module.engineering.api.material.MaterialStockAttr;
import com.erp.module.purchase.api.PurchaseErrorCodes;
import com.erp.module.purchase.api.order.PurchaseDeliveryDateChangedEvent;
import com.erp.module.purchase.api.order.PurchaseOrderApprovedEvent;
import com.erp.module.purchase.api.order.PurchaseOrderChangedEvent;
import com.erp.module.purchase.api.supplier.SupplierStatus;
import com.erp.module.purchase.config.PurchaseModuleConfig;
import com.erp.module.purchase.controller.vo.CommonVOs.DocResult;
import com.erp.module.purchase.controller.vo.CommonVOs.RelatedDoc;
import com.erp.module.purchase.controller.vo.OrderVOs.ConfirmDate;
import com.erp.module.purchase.controller.vo.OrderVOs.FromRequisitionLine;
import com.erp.module.purchase.controller.vo.OrderVOs.FromRequisitionResult;
import com.erp.module.purchase.controller.vo.OrderVOs.OpenLine;
import com.erp.module.purchase.controller.vo.OrderVOs.OpenLineQuery;
import com.erp.module.purchase.controller.vo.OrderVOs.OrderDetail;
import com.erp.module.purchase.controller.vo.OrderVOs.OrderLineResp;
import com.erp.module.purchase.controller.vo.OrderVOs.OrderLineSave;
import com.erp.module.purchase.controller.vo.OrderVOs.OrderQuery;
import com.erp.module.purchase.controller.vo.OrderVOs.OrderRow;
import com.erp.module.purchase.controller.vo.OrderVOs.OrderSave;
import com.erp.module.purchase.controller.vo.OrderVOs.OrderSaveResult;
import com.erp.module.purchase.controller.vo.PriceVOs.EffectivePrice;
import com.erp.module.purchase.dal.dataobject.OrderChangeDO;
import com.erp.module.purchase.dal.dataobject.OrderDO;
import com.erp.module.purchase.dal.dataobject.OrderLineAggRow;
import com.erp.module.purchase.dal.dataobject.OrderLineDO;
import com.erp.module.purchase.dal.dataobject.ReceiptDO;
import com.erp.module.purchase.dal.dataobject.ReceiptLineDO;
import com.erp.module.purchase.dal.dataobject.RequisitionDO;
import com.erp.module.purchase.dal.dataobject.RequisitionLineDO;
import com.erp.module.purchase.dal.dataobject.ReturnAggRow;
import com.erp.module.purchase.dal.dataobject.SupplierContactDO;
import com.erp.module.purchase.dal.dataobject.SupplierDO;
import com.erp.module.purchase.dal.dataobject.SupplierMaterialDO;
import com.erp.module.purchase.dal.mapper.OrderChangeMapper;
import com.erp.module.purchase.dal.mapper.OrderLineMapper;
import com.erp.module.purchase.dal.mapper.OrderMapper;
import com.erp.module.purchase.dal.mapper.ReceiptLineMapper;
import com.erp.module.purchase.dal.mapper.ReceiptMapper;
import com.erp.module.purchase.dal.mapper.ReturnLineMapper;
import com.erp.module.purchase.service.PurAction;
import com.erp.module.purchase.service.PurStateMachines;
import com.erp.module.purchase.service.PurSupport;
import com.erp.module.purchase.service.price.PriceService;
import com.erp.module.purchase.service.requisition.RequisitionService;
import com.erp.module.purchase.service.supplier.SupplierService;
import com.erp.module.system.api.currency.CurrencyApi;
import com.erp.module.system.api.file.FileApi;
import com.erp.module.system.api.org.OrgDTO;
import com.erp.module.system.api.paymentterm.PaymentTermApi;
import com.erp.module.system.api.paymentterm.PaymentTermDTO;
import com.erp.module.system.api.user.UserDTO;
import com.erp.module.system.api.workflow.ApprovalCompletedEvent;
import com.erp.module.system.api.workflow.StartResult;
import com.erp.module.system.api.workflow.WorkflowApi;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
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
 * 采购订单（需求 07-05）：草稿 → 待审批 → 已审核 → 执行中（第一次到货审核）→ 已完成（所有行收齐或关闭）/ 已关闭。
 * 订单行的到货、入库、合格、退货、对账数量由到货单、退货单、对账单变化时重新汇总（{@link #refreshLines}）。
 */
@Service
public class OrderService {

    public static final String BIZ_TYPE = PurchaseModuleConfig.ORDER;
    public static final String OPEN = "OPEN";
    public static final String RECEIVED = "RECEIVED";
    public static final String CLOSED = "CLOSED";
    static final Set<String> TYPES = Set.of("STANDARD", "SAMPLE");
    static final List<DocStatus> ACTIVE = List.of(DocStatus.APPROVED, DocStatus.IN_PROGRESS);
    static final int MAX_ROWS = 2000;

    private final OrderMapper mapper;
    private final OrderLineMapper lineMapper;
    private final OrderChangeMapper changeMapper;
    private final ReceiptMapper receiptMapper;
    private final ReceiptLineMapper receiptLineMapper;
    private final ReturnLineMapper returnLineMapper;
    private final RequisitionService requisitionService;
    private final SupplierService supplierService;
    private final PriceService priceService;
    private final PurSupport support;
    private final CurrencyApi currencyApi;
    private final PaymentTermApi paymentTermApi;
    private final WorkflowApi workflowApi;
    private final FileApi fileApi;
    private final DomainEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    public OrderService(OrderMapper mapper, OrderLineMapper lineMapper, OrderChangeMapper changeMapper, ReceiptMapper receiptMapper,
                        ReceiptLineMapper receiptLineMapper, ReturnLineMapper returnLineMapper, RequisitionService requisitionService,
                        SupplierService supplierService, PriceService priceService, PurSupport support, CurrencyApi currencyApi,
                        PaymentTermApi paymentTermApi, WorkflowApi workflowApi, FileApi fileApi, DomainEventPublisher eventPublisher,
                        ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.lineMapper = lineMapper;
        this.changeMapper = changeMapper;
        this.receiptMapper = receiptMapper;
        this.receiptLineMapper = receiptLineMapper;
        this.returnLineMapper = returnLineMapper;
        this.requisitionService = requisitionService;
        this.supplierService = supplierService;
        this.priceService = priceService;
        this.support = support;
        this.currencyApi = currencyApi;
        this.paymentTermApi = paymentTermApi;
        this.workflowApi = workflowApi;
        this.fileApi = fileApi;
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
    }

    // ==================== 查询 ====================

    public PageResult<OrderRow> page(OrderQuery q) {
        LambdaQueryWrapper<OrderDO> w = query(q);
        IPage<OrderDO> page = mapper.selectScopedPage(Page.of(q.getPageNo(), q.getPageSize()), w);
        return new PageResult<>(rows(page.getRecords()), page.getTotal());
    }

    public List<OrderRow> listForExport(OrderQuery q, int limit) {
        return rows(mapper.selectScopedList(query(q).last("LIMIT " + limit)));
    }

    private LambdaQueryWrapper<OrderDO> query(OrderQuery q) {
        LambdaQueryWrapper<OrderDO> w = new LambdaQueryWrapper<OrderDO>().eq(OrderDO::getDeleted, false)
                .likeRight(StringUtils.hasText(q.getDocNo()), OrderDO::getDocNo, q.getDocNo() == null ? null : q.getDocNo().trim().toUpperCase())
                .eq(q.getSupplierId() != null, OrderDO::getSupplierId, q.getSupplierId())
                .eq(q.getOwnerId() != null, OrderDO::getOwnerId, q.getOwnerId())
                .eq(StringUtils.hasText(q.getOrderType()), OrderDO::getOrderType, q.getOrderType())
                .ge(q.getDateFrom() != null, OrderDO::getDocDate, q.getDateFrom())
                .le(q.getDateTo() != null, OrderDO::getDocDate, q.getDateTo());
        if (StringUtils.hasText(q.getStatuses())) {
            w.in(OrderDO::getStatus, Arrays.stream(q.getStatuses().split(",")).map(String::trim).map(DocStatus::valueOf).toList());
        }
        StringBuilder cond = new StringBuilder();
        if (q.getMaterialId() != null) cond.append(" AND material_id = ").append(q.getMaterialId().longValue());
        if (q.getRequiredFrom() != null) cond.append(" AND required_date >= '").append(q.getRequiredFrom()).append("'");
        if (q.getRequiredTo() != null) cond.append(" AND required_date <= '").append(q.getRequiredTo()).append("'");
        if (Boolean.TRUE.equals(q.getOverdue())) {
            cond.append(" AND line_status = 'OPEN' AND COALESCE(confirmed_date, required_date) < '").append(LocalDate.now()).append("'");
            w.in(OrderDO::getStatus, ACTIVE);
        }
        if (!cond.isEmpty()) w.inSql(OrderDO::getId, "SELECT order_id FROM pur_order_line WHERE deleted = 0" + cond);
        return w.orderByDesc(OrderDO::getDocDate).orderByDesc(OrderDO::getId);
    }

    private List<OrderRow> rows(List<OrderDO> list) {
        if (list.isEmpty()) return List.of();
        Map<Long, List<OrderLineDO>> lines = lineMapper.selectByParents(list.stream().map(OrderDO::getId).toList()).stream()
                .collect(Collectors.groupingBy(OrderLineDO::getOrderId));
        Map<Long, SupplierDO> ss = supplierService.byIds(list.stream().map(OrderDO::getSupplierId).toList());
        Map<Long, UserDTO> users = support.users(list.stream().map(OrderDO::getOwnerId).toList());
        boolean price = PurSupport.canViewPrice();
        LocalDate today = LocalDate.now();
        return list.stream().map(o -> {
            List<OrderLineDO> ls = lines.getOrDefault(o.getId(), List.of());
            boolean active = ACTIVE.contains(o.getStatus());
            int overdue = active ? (int) ls.stream().filter(l -> OPEN.equals(l.getLineStatus()) && dueDate(l).isBefore(today)).count() : 0;
            int delayed = (int) ls.stream().filter(OrderService::delayed).count();
            SupplierDO s = ss.get(o.getSupplierId());
            return new OrderRow(o.getId(), o.getDocNo(), o.getDocDate(), o.getOrderType(), o.getSupplierId(), s == null ? null : s.getShortName(),
                    o.getOwnerId(), PurSupport.name(users, o.getOwnerId()), o.getCurrency(), PurSupport.mask(o.getTotalAmount(), price),
                    ls.stream().map(OrderService::dueDate).min(Comparator.naturalOrder()).orElse(null),
                    ls.stream().map(OrderLineDO::getBaseQty).reduce(BigDecimal.ZERO, BigDecimal::add),
                    ls.stream().map(OrderLineDO::getReceivedQty).reduce(BigDecimal.ZERO, BigDecimal::add), overdue, delayed, o.getSentAt(),
                    o.getOrderVersion(), Boolean.TRUE.equals(o.getHasPriceOverrun()), o.getStatus().name());
        }).toList();
    }

    public static LocalDate dueDate(OrderLineDO l) {
        return l.getConfirmedDate() != null ? l.getConfirmedDate() : l.getRequiredDate();
    }

    static boolean delayed(OrderLineDO l) {
        return l.getConfirmedDate() != null && l.getConfirmedDate().isAfter(l.getRequiredDate()) && !CLOSED.equals(l.getLineStatus());
    }

    public OrderDetail detail(Long id) {
        OrderDO o = getOrThrow(id);
        DataScopes.check(o.getOrgId(), o.getDeptId(), o.getOwnerId(), "采购订单");
        SupplierDO s = supplierService.getOrThrow(o.getSupplierId());
        List<OrderLineDO> lines = lineMapper.selectByParent(id);
        boolean price = PurSupport.canViewPrice();
        SupplierContactDO contact = o.getSupplierContactId() == null ? null
                : supplierService.contacts(s.getId()).stream().filter(c -> c.getId().equals(o.getSupplierContactId())).findFirst().orElse(null);
        PaymentTermDTO term = supplierService.paymentTerm(o.getPaymentTermId());
        OrderChangeDO running = changeMapper.selectOne(new LambdaQueryWrapper<OrderChangeDO>().eq(OrderChangeDO::getOrderId, id)
                .in(OrderChangeDO::getStatus, DocStatus.DRAFT, DocStatus.PENDING_APPROVAL).last("LIMIT 1"));
        boolean hasReceipt = receiptLineMapper.selectCount(new LambdaQueryWrapper<ReceiptLineDO>().eq(ReceiptLineDO::getOrderId, id)) > 0;
        Map<Long, UserDTO> users = support.users(List.of(nz(o.getOwnerId()), nz(o.getCreatedBy())));
        return new OrderDetail(o.getId(), o.getDocNo(), o.getDocDate(), o.getStatus().name(), o.getOrderType(), s.getId(), s.getCode(), s.getShortName(),
                s.getSupplierLevel(), o.getSupplierContactId(), contact == null ? null : contact.getName(), o.getCurrency(), o.getExchangeRate(),
                o.getPaymentTermId(), term == null ? null : term.name(), o.getTradeTerm(), Boolean.TRUE.equals(o.getTaxIncluded()), o.getDeliveryAddress(),
                PurSupport.mask(o.getAmount(), price), PurSupport.mask(o.getTaxAmount(), price), PurSupport.mask(o.getTotalAmount(), price),
                PurSupport.mask(o.getTotalAmountBase(), price), o.getOrderVersion(), Boolean.TRUE.equals(o.getHasPriceOverrun()), o.getSentAt(),
                o.getCloseReason(), o.getOwnerId(), PurSupport.name(users, o.getOwnerId()), o.getRemark(), price, hasReceipt,
                running == null ? null : running.getId(), PurSupport.name(users, o.getCreatedBy()), o.getCreatedAt(), o.getVersion(),
                lineResps(lines, price), related(o, lines));
    }

    List<OrderLineResp> lineResps(List<OrderLineDO> lines, boolean price) {
        Map<Long, MaterialDTO> ms = support.materials(lines.stream().map(OrderLineDO::getMaterialId).toList());
        Map<Long, RequisitionLineDO> rls = requisitionService.lines(lines.stream().map(OrderLineDO::getRequisitionLineId).toList());
        Map<Long, RequisitionDO> rs = requisitionService.heads(rls.values().stream().map(RequisitionLineDO::getRequisitionId).toList());
        LocalDate today = LocalDate.now();
        return lines.stream().map(l -> {
            MaterialDTO m = ms.get(l.getMaterialId());
            RequisitionLineDO rl = rls.get(l.getRequisitionLineId());
            RequisitionDO r = rl == null ? null : rs.get(rl.getRequisitionId());
            BigDecimal open = l.getBaseQty().subtract(l.getReceivedQty()).max(BigDecimal.ZERO);
            return new OrderLineResp(l.getId(), l.getLineNo(), l.getMaterialId(), m == null ? null : m.code(), m == null ? null : m.name(),
                    m == null ? null : m.nameEn(), m == null ? null : m.spec(), m == null ? null : m.baseUom(), l.getSupplierPartNo(), l.getUom(),
                    l.getQty(), l.getBaseQty(), PurSupport.mask(l.getPrice(), price), PurSupport.mask(l.getPriceInclTax(), price), l.getTaxRate(),
                    PurSupport.mask(l.getAmount(), price), PurSupport.mask(l.getTaxAmount(), price), PurSupport.mask(l.getTotalAmount(), price),
                    PurSupport.mask(l.getListPrice(), price), Boolean.TRUE.equals(l.getPriceOverrun()), l.getRequiredDate(), l.getConfirmedDate(),
                    l.getReceivedQty(), l.getStockedQty(), l.getQualifiedQty(), l.getReturnedQty(), l.getReplaceQty(), l.getStatementQty(), open,
                    l.getFirstReceivedDate(), l.getRequisitionLineId(), r == null ? null : r.getDocNo(), r == null ? null : r.getId(), l.getLineStatus(),
                    delayed(l), OPEN.equals(l.getLineStatus()) && dueDate(l).isBefore(today), l.getLastFollowUp(), l.getFollowUpAt(), l.getRemark());
        }).toList();
    }

    private List<RelatedDoc> related(OrderDO o, List<OrderLineDO> lines) {
        List<RelatedDoc> list = new ArrayList<>();
        Map<Long, RequisitionDO> reqs = requisitionService.heads(requisitionService.lines(lines.stream().map(OrderLineDO::getRequisitionLineId).toList())
                .values().stream().map(RequisitionLineDO::getRequisitionId).toList());
        reqs.values().forEach(r -> list.add(new RelatedDoc("UP", "采购申请", r.getDocNo(), r.getDocDate(), r.getStatus().name(), r.getStatus().label(),
                "/purchase/requisition/" + r.getId())));
        Set<Long> receiptIds = receiptLineMapper.selectList(new LambdaQueryWrapper<ReceiptLineDO>().eq(ReceiptLineDO::getOrderId, o.getId())).stream()
                .map(ReceiptLineDO::getReceiptId).collect(Collectors.toSet());
        if (!receiptIds.isEmpty()) {
            receiptMapper.selectBatchIds(receiptIds).stream().sorted(Comparator.comparing(ReceiptDO::getId)).forEach(r -> list.add(new RelatedDoc("DOWN",
                    "到货单", r.getDocNo(), r.getDocDate(), r.getStatus().name(), r.getStatus().label(), "/purchase/receipt/" + r.getId())));
        }
        changeMapper.selectList(new LambdaQueryWrapper<OrderChangeDO>().eq(OrderChangeDO::getOrderId, o.getId()).orderByAsc(OrderChangeDO::getId))
                .forEach(c -> list.add(new RelatedDoc("DOWN", "订单变更单", c.getDocNo(), c.getDocDate(), c.getStatus().name(), c.getStatus().label(),
                        "/purchase/order-change/" + c.getId())));
        return list;
    }

    // ==================== 编辑 ====================

    @Transactional(rollbackFor = Exception.class)
    public OrderSaveResult create(OrderSave req) {
        requirePriceView();
        OrderDO o = new OrderDO();
        o.setDocNo(support.nextNo(BIZ_TYPE));
        o.setDocDate(LocalDate.now());
        o.setStatus(DocStatus.DRAFT);
        o.setOrderVersion(1);
        o.setAmount(BigDecimal.ZERO);
        o.setTaxAmount(BigDecimal.ZERO);
        o.setTotalAmount(BigDecimal.ZERO);
        o.setTotalAmountBase(BigDecimal.ZERO);
        o.setHasPriceOverrun(false);
        SupplierDO s = fillHeader(o, req);
        mapper.insert(o);
        List<String> warnings = saveLines(o, s, req.lines());
        mapper.updateByIdOrFail(o);
        if (req.fileIds() != null && !req.fileIds().isEmpty()) fileApi.bind(req.fileIds(), BIZ_TYPE, o.getId());
        return new OrderSaveResult(o.getId(), warnings);
    }

    @Transactional(rollbackFor = Exception.class)
    public OrderSaveResult update(Long id, OrderSave req) {
        requirePriceView();
        OrderDO o = getOrThrow(id);
        DataScopes.check(o.getOrgId(), o.getDeptId(), o.getOwnerId(), "采购订单");
        PurSupport.requireDraft(o);
        if (req.version() != null) o.setVersion(req.version());
        SupplierDO s = fillHeader(o, req);
        List<String> warnings = saveLines(o, s, req.lines());
        mapper.updateByIdOrFail(o);
        if (req.fileIds() != null && !req.fileIds().isEmpty()) fileApi.bind(req.fileIds(), BIZ_TYPE, o.getId());
        return new OrderSaveResult(o.getId(), warnings);
    }

    private void requirePriceView() {
        if (!PurSupport.canViewPrice()) throw BizException.of(PurchaseErrorCodes.NO_PRICE_VIEW, "采购订单");
    }

    private SupplierDO fillHeader(OrderDO o, OrderSave req) {
        String type = StringUtils.hasText(req.orderType()) ? req.orderType() : "STANDARD";
        if (type == null || !TYPES.contains(type)) throw BizException.of(GlobalErrorCodes.BAD_REQUEST, "订单类型");
        SupplierDO s = supplierService.getOrThrow(req.supplierId());
        if (s.getSupplierStatus() == SupplierStatus.ELIMINATED) throw BizException.of(PurchaseErrorCodes.SUPPLIER_ELIMINATED, s.getName());
        o.setOrderType(type);
        o.setSupplierId(s.getId());
        Long contactId = req.supplierContactId();
        if (contactId == null) {
            contactId = supplierService.contacts(s.getId()).stream().filter(c -> Boolean.TRUE.equals(c.getIsPrimary())).map(SupplierContactDO::getId)
                    .findFirst().orElse(null);
        }
        o.setSupplierContactId(contactId);
        String currency = StringUtils.hasText(req.currency()) ? req.currency().trim().toUpperCase() : s.getCurrency();
        currencyApi.validate(currency);
        o.setCurrency(currency);
        BigDecimal rate = currency.equals(currencyApi.getBaseCurrency()) ? BigDecimal.ONE
                : req.exchangeRate() != null ? req.exchangeRate() : currencyApi.getRate(currency, o.getDocDate());
        if (rate.signum() <= 0) throw BizException.of(GlobalErrorCodes.BAD_REQUEST, "汇率必须大于 0");
        o.setExchangeRate(rate);
        Long termId = req.paymentTermId() != null ? req.paymentTermId() : s.getPaymentTermId();
        PaymentTermDTO term = paymentTermApi.validate(termId, "PURCHASE");
        o.setPaymentTermId(termId);
        o.setPaymentTermSnapshot(json(term));
        String trade = req.tradeTerm() != null ? PurSupport.trim(req.tradeTerm()) : s.getTradeTerm();
        if (trade != null && !trade.equals(o.getTradeTerm())) support.dict().validate("sys_trade_term", trade, "贸易条款");
        o.setTradeTerm(trade);
        o.setTaxIncluded(req.taxIncluded() == null || req.taxIncluded());
        o.setDeliveryAddress(PurSupport.trim(req.deliveryAddress()));
        support.fillOwner(o, req.ownerId() != null ? req.ownerId() : (o.getOwnerId() != null ? o.getOwnerId() : s.getBuyerId()));
        o.setRemark(PurSupport.trim(req.remark()));
        return s;
    }

    /** 整体替换明细：取价、换算、金额（R04～R06）；返回提示 */
    private List<String> saveLines(OrderDO o, SupplierDO s, List<OrderLineSave> lines) {
        if (lines == null || lines.isEmpty()) throw new BizException(PurchaseErrorCodes.DOC_NO_LINES);
        Map<Long, MaterialDTO> ms = support.materials(lines.stream().map(OrderLineSave::materialId).toList());
        Map<Long, RequisitionLineDO> reqLines = requisitionService.lines(lines.stream().map(OrderLineSave::requisitionLineId).toList());
        lineMapper.deleteByParent(o.getId());
        List<String> warnings = new ArrayList<>();
        int no = 0;
        for (OrderLineSave l : lines) {
            no++;
            MaterialDTO m = ms.get(l.materialId());
            if (m == null) throw BizException.of(GlobalErrorCodes.DATA_NOT_EXISTS, "物料");
            if (l.qty() == null || l.qty().signum() <= 0) throw BizException.of(PurchaseErrorCodes.LINE_QTY_POSITIVE, no);
            if (l.requiredDate() == null) throw BizException.of(PurchaseErrorCodes.LINE_FIELD_REQUIRED, no, "要求到货日期");
            if (l.requisitionLineId() != null) {
                RequisitionLineDO rl = reqLines.get(l.requisitionLineId());
                if (rl == null || !rl.getMaterialId().equals(m.id())) throw BizException.of(GlobalErrorCodes.DATA_NOT_EXISTS, "来源申请行");
            }
            OrderLineDO d = new OrderLineDO();
            d.setOrderId(o.getId());
            d.setLineNo(no);
            d.setMaterialId(m.id());
            d.setUom(StringUtils.hasText(l.uom()) ? l.uom() : support.materialApi().getPurchaseAttr(m.id()).purchaseUom());
            d.setQty(Decimals.qty(l.qty()));
            d.setBaseQty(Decimals.qty(support.toBase(m.id(), d.getQty(), d.getUom())));
            d.setSupplierPartNo(supplierService.supplierMaterial(s.getId(), m.id()).map(SupplierMaterialDO::getSupplierPartNo).orElse(null));
            d.setTaxRate(l.taxRate() != null ? l.taxRate() : s.getPurchaseTaxRate());
            d.setRequiredDate(l.requiredDate());
            d.setRequisitionLineId(l.requisitionLineId());
            d.setRemark(PurSupport.trim(l.remark()));
            initQty(d);
            BigDecimal input = Boolean.TRUE.equals(o.getTaxIncluded()) ? l.priceInclTax() : l.price();
            priceLine(o, s, d, m, input, no, warnings);
            moqWarnings(s, d, m, no, warnings);
            lineMapper.insert(d);
        }
        recalcTotals(o);
        return warnings;
    }

    static void initQty(OrderLineDO d) {
        d.setReceivedQty(BigDecimal.ZERO);
        d.setStockedQty(BigDecimal.ZERO);
        d.setQualifiedQty(BigDecimal.ZERO);
        d.setReturnedQty(BigDecimal.ZERO);
        d.setReplaceQty(BigDecimal.ZERO);
        d.setStatementQty(BigDecimal.ZERO);
        d.setLineStatus(OPEN);
    }

    /**
     * 取价与金额（R04～R06）：录入价为空时取价格表价；价格表价用于判断超价（单价 > 价格表价 × (1 + 容差)）。
     *
     * @param input 按单头“单价含税”开关录入的单价（每订单单位）
     */
    void priceLine(OrderDO o, SupplierDO s, OrderLineDO d, MaterialDTO m, BigDecimal input, int no, List<String> warnings) {
        EffectivePrice list = priceService.effectiveForUom(s.getId(), m.id(), d.getQty(), d.getUom(), o.getDocDate(), o.getCurrency());
        d.setListPrice(list == null ? null : list.price());
        boolean incl = Boolean.TRUE.equals(o.getTaxIncluded());
        BigDecimal price = input;
        if (price == null && list != null) {
            price = incl ? Decimals.price(list.price().multiply(BigDecimal.ONE.add(d.getTaxRate()))) : list.price();
        }
        if (price == null) price = BigDecimal.ZERO;
        if (price.signum() < 0) throw BizException.of(GlobalErrorCodes.BAD_REQUEST, "第 " + no + " 行单价不能为负数");
        if (list == null && !"NONE".equals(support.params().getString(PurchaseModuleConfig.P_REQUIRE_PRICE))) {
            warnings.add("第 " + no + " 行物料「" + m.code() + "」无有效采购价格");
        }
        computeAmounts(d, incl, price);
        BigDecimal tol = support.params().getDecimal(PurchaseModuleConfig.P_PRICE_OVERRUN_PCT);
        d.setPriceOverrun(list != null && d.getPrice().compareTo(list.price().multiply(BigDecimal.ONE.add(PurSupport.nz(tol).divide(PurSupport.HUNDRED)))) > 0);
    }

    /** R06：含税录入时 price = ROUND(含税价 ÷ (1 + 税率), 6)，价税合计 = ROUND(数量 × 含税价, 2)，不含税金额 = ROUND(价税合计 ÷ (1 + 税率), 2)；不含税录入反向计算 */
    public static void computeAmounts(OrderLineDO d, boolean taxIncluded, BigDecimal inputPrice) {
        BigDecimal rate = d.getTaxRate();
        BigDecimal onePlus = BigDecimal.ONE.add(rate);
        if (taxIncluded) {
            d.setPriceInclTax(Decimals.price(inputPrice));
            d.setPrice(Decimals.price(inputPrice.divide(onePlus, 10, RoundingMode.HALF_UP)));
            d.setTotalAmount(Decimals.multiplyAmount(d.getQty(), d.getPriceInclTax()));
            d.setAmount(Decimals.amount(d.getTotalAmount().divide(onePlus, 10, RoundingMode.HALF_UP)));
            d.setTaxAmount(d.getTotalAmount().subtract(d.getAmount()));
        } else {
            d.setPrice(Decimals.price(inputPrice));
            d.setPriceInclTax(Decimals.price(inputPrice.multiply(onePlus)));
            d.setAmount(Decimals.multiplyAmount(d.getQty(), d.getPrice()));
            d.setTaxAmount(Decimals.amount(d.getAmount().multiply(rate)));
            d.setTotalAmount(d.getAmount().add(d.getTaxAmount()));
        }
    }

    /** R03：数量 < MOQ 警告；不是 MPQ 整数倍时提示向上取整 */
    private void moqWarnings(SupplierDO s, OrderLineDO d, MaterialDTO m, int no, List<String> warnings) {
        BigDecimal[] mm = moqMpq(s.getId(), m.id());
        if (mm[0] != null && mm[0].signum() > 0 && d.getBaseQty().compareTo(mm[0]) < 0) {
            warnings.add("第 " + no + " 行数量小于 MOQ " + PurSupport.plain(mm[0]) + " " + m.baseUom());
        }
        if (mm[1] != null && mm[1].signum() > 0 && d.getBaseQty().remainder(mm[1]).signum() != 0) {
            BigDecimal up = d.getBaseQty().divide(mm[1], 0, RoundingMode.CEILING).multiply(mm[1]);
            warnings.add("第 " + no + " 行数量不是 MPQ 的整数倍，是否向上取整到 " + PurSupport.plain(up) + " " + m.baseUom());
        }
    }

    /** 供应商可供物料的 MOQ/MPQ 优先，其次物料采购属性（基本单位） */
    BigDecimal[] moqMpq(Long supplierId, Long materialId) {
        SupplierMaterialDO sm = supplierService.supplierMaterial(supplierId, materialId).orElse(null);
        MaterialPurchaseAttr a = support.materialApi().getPurchaseAttr(materialId);
        BigDecimal moq = sm != null && sm.getMoq() != null ? sm.getMoq() : a.moq();
        BigDecimal mpq = sm != null && sm.getMpq() != null ? sm.getMpq() : a.mpq();
        return new BigDecimal[]{moq, mpq};
    }

    /** 单头合计 = 行合计之和；本位币 = 价税合计 × 汇率；是否有超价行 */
    void recalcTotals(OrderDO o) {
        List<OrderLineDO> lines = lineMapper.selectByParent(o.getId());
        o.setAmount(lines.stream().map(OrderLineDO::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
        o.setTaxAmount(lines.stream().map(OrderLineDO::getTaxAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
        o.setTotalAmount(lines.stream().map(OrderLineDO::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
        o.setTotalAmountBase(currencyApi.toBase(o.getTotalAmount(), o.getExchangeRate()));
        o.setHasPriceOverrun(lines.stream().anyMatch(l -> Boolean.TRUE.equals(l.getPriceOverrun())));
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        OrderDO o = getOrThrow(id);
        DataScopes.check(o.getOrgId(), o.getDeptId(), o.getOwnerId(), "采购订单");
        PurSupport.requireDraft(o);
        lineMapper.deleteByParent(id);
        mapper.deleteById(id);
        fileApi.deleteByBiz(BIZ_TYPE, id);
    }

    @Transactional(rollbackFor = Exception.class)
    public void voidDoc(Long id, String reason) {
        OrderDO o = getOrThrow(id);
        DataScopes.check(o.getOrgId(), o.getDeptId(), o.getOwnerId(), "采购订单");
        support.fire(PurStateMachines.EXECUTABLE, mapper, o, BIZ_TYPE, PurAction.VOID, PurSupport.requireReason(reason, "作废"));
    }

    // ==================== 提交 / 审核 ====================

    @Transactional(rollbackFor = Exception.class)
    public DocResult submit(Long id) {
        OrderDO o = getOrThrow(id);
        DataScopes.check(o.getOrgId(), o.getDeptId(), o.getOwnerId(), "采购订单");
        PurSupport.requireDraft(o);
        List<OrderLineDO> lines = lineMapper.selectByParent(id);
        if (lines.isEmpty()) throw new BizException(PurchaseErrorCodes.DOC_NO_LINES);
        boolean sample = "SAMPLE".equals(o.getOrderType());
        // R01：标准订单要求合格供应商；样品订单只要求非暂停/淘汰
        SupplierDO s = sample ? supplierService.validateActive(o.getSupplierId()) : supplierService.validateQualified(o.getSupplierId());
        boolean requireApproved = support.params().getBool(PurchaseModuleConfig.P_REQUIRE_SUPPLIER_MATERIAL);
        String requirePrice = support.params().getString(PurchaseModuleConfig.P_REQUIRE_PRICE);
        List<String> warnings = new ArrayList<>();
        for (OrderLineDO l : lines) {
            MaterialDTO m = support.materialApi().validateUsable(l.getMaterialId());
            SupplierMaterialDO sm = supplierService.supplierMaterial(s.getId(), m.id()).orElse(null);
            // 供应商 R08：试用状态的物料只能下样品订单
            if (!sample && sm != null && "TRIAL".equals(sm.getSupplyStatus())) throw BizException.of(PurchaseErrorCodes.SUPPLIER_MATERIAL_TRIAL, m.code());
            if (requireApproved && (sm == null || "DISABLED".equals(sm.getSupplyStatus()))) {
                throw BizException.of(PurchaseErrorCodes.ORDER_MATERIAL_NOT_APPROVED, m.code(), s.getName());
            }
            if (l.getListPrice() == null) {
                if ("BLOCK".equals(requirePrice)) throw BizException.of(PurchaseErrorCodes.ORDER_NO_PRICE, m.code());
                if ("WARN".equals(requirePrice)) warnings.add("第 " + l.getLineNo() + " 行物料「" + m.code() + "」无有效采购价格");
            }
        }
        List<Long> reqLineIds = lines.stream().map(OrderLineDO::getRequisitionLineId).filter(Objects::nonNull).toList();
        if (!reqLineIds.isEmpty()) requisitionService.pendingLines(reqLineIds);
        support.fire(PurStateMachines.EXECUTABLE, mapper, o, BIZ_TYPE, PurAction.SUBMIT, null);
        Map<String, Object> vars = new HashMap<>();
        vars.put("amountBase", o.getTotalAmountBase());
        vars.put("supplierLevel", s.getSupplierLevel());
        vars.put("hasPriceOverrun", Boolean.TRUE.equals(o.getHasPriceOverrun()));
        Map<String, Long> users = new HashMap<>();
        users.put("buyerId", o.getOwnerId());
        StartResult r = workflowApi.start(BIZ_TYPE, id, o.getDocNo(), "采购订单 " + o.getDocNo() + " " + s.getShortName(), vars, users, support.currentUser());
        if (!r.isStarted()) approve(o);
        return new DocResult(o.getStatus().name(), warnings);
    }

    @EventListener
    public void onApproval(ApprovalCompletedEvent e) {
        if (!BIZ_TYPE.equals(e.getBizType())) return;
        OrderDO o = getOrThrow(e.getBizId());
        if (o.getStatus() != DocStatus.PENDING_APPROVAL) return;
        switch (e.getResult()) {
            case APPROVED -> approve(o);
            case WITHDRAWN -> support.fire(PurStateMachines.EXECUTABLE, mapper, o, BIZ_TYPE, PurAction.WITHDRAW, null);
            default -> support.fire(PurStateMachines.EXECUTABLE, mapper, o, BIZ_TYPE, PurAction.REJECT, e.getComment());
        }
    }

    /** 审核：回写申请已转数量；发布 PurchaseOrderApprovedEvent（PMC 在途量） */
    private void approve(OrderDO o) {
        support.fire(PurStateMachines.EXECUTABLE, mapper, o, BIZ_TYPE, PurAction.APPROVE, null);
        List<OrderLineDO> lines = lineMapper.selectByParent(o.getId());
        requisitionService.refreshOrdered(lines.stream().map(OrderLineDO::getRequisitionLineId).filter(Objects::nonNull).toList());
        eventPublisher.publish(new PurchaseOrderApprovedEvent(o.getId(), o.getDocNo(), o.getSupplierId(), true,
                lines.stream().map(OrderLineDO::getMaterialId).distinct().toList()));
    }

    /** R09：有到货记录不能反审核；扣回申请已转数量 */
    @Transactional(rollbackFor = Exception.class)
    public void unapprove(Long id, String reason) {
        OrderDO o = getOrThrow(id);
        DataScopes.check(o.getOrgId(), o.getDeptId(), o.getOwnerId(), "采购订单");
        String why = PurSupport.requireReason(reason, "反审核");
        if (o.getStatus() == DocStatus.APPROVED && receiptLineMapper.selectCount(new LambdaQueryWrapper<ReceiptLineDO>().eq(ReceiptLineDO::getOrderId, id)) > 0) {
            throw new BizException(PurchaseErrorCodes.ORDER_HAS_RECEIPT);
        }
        if (o.getStatus() == DocStatus.IN_PROGRESS) throw new BizException(PurchaseErrorCodes.ORDER_HAS_RECEIPT);
        checkNoRunningChange(id);
        support.fire(PurStateMachines.EXECUTABLE, mapper, o, BIZ_TYPE, PurAction.UNAPPROVE, why);
        List<OrderLineDO> lines = lineMapper.selectByParent(id);
        requisitionService.refreshOrdered(lines.stream().map(OrderLineDO::getRequisitionLineId).filter(Objects::nonNull).toList());
        eventPublisher.publish(new PurchaseOrderApprovedEvent(o.getId(), o.getDocNo(), o.getSupplierId(), false,
                lines.stream().map(OrderLineDO::getMaterialId).distinct().toList()));
    }

    void checkNoRunningChange(Long orderId) {
        OrderChangeDO c = changeMapper.selectOne(new LambdaQueryWrapper<OrderChangeDO>().eq(OrderChangeDO::getOrderId, orderId)
                .in(OrderChangeDO::getStatus, DocStatus.DRAFT, DocStatus.PENDING_APPROVAL).last("LIMIT 1"));
        if (c != null) throw BizException.of(PurchaseErrorCodes.ORDER_CHANGE_RUNNING, c.getDocNo());
    }

    /** R08：原因必填；有未审核的到货单时不能关闭；未收齐的行关闭，不再计入在途 */
    @Transactional(rollbackFor = Exception.class)
    public void close(Long id, String reason) {
        OrderDO o = getOrThrow(id);
        DataScopes.check(o.getOrgId(), o.getDeptId(), o.getOwnerId(), "采购订单");
        String why = PurSupport.requireReason(reason, "关闭");
        List<Long> draftReceipts = receiptLineMapper.selectList(new LambdaQueryWrapper<ReceiptLineDO>().eq(ReceiptLineDO::getOrderId, id)).stream()
                .map(ReceiptLineDO::getReceiptId).distinct().toList();
        if (!draftReceipts.isEmpty()) {
            ReceiptDO draft = receiptMapper.selectOne(new LambdaQueryWrapper<ReceiptDO>().in(ReceiptDO::getId, draftReceipts)
                    .eq(ReceiptDO::getStatus, DocStatus.DRAFT).last("LIMIT 1"));
            if (draft != null) throw BizException.of(PurchaseErrorCodes.ORDER_HAS_PENDING_RECEIPT, draft.getDocNo());
        }
        checkNoRunningChange(id);
        List<OrderLineDO> lines = lineMapper.selectByParent(id);
        for (OrderLineDO l : lines) {
            if (OPEN.equals(l.getLineStatus())) {
                l.setLineStatus(CLOSED);
                lineMapper.updateByIdOrFail(l);
            }
        }
        o.setCloseReason(why);
        support.fire(PurStateMachines.EXECUTABLE, mapper, o, BIZ_TYPE, PurAction.CLOSE, why);
        eventPublisher.publish(new PurchaseOrderChangedEvent(o.getId(), o.getDocNo(), o.getOrderVersion(), "CLOSE",
                lines.stream().map(OrderLineDO::getMaterialId).distinct().toList()));
    }

    /** 发送给供应商：记录发送时间 */
    @Transactional(rollbackFor = Exception.class)
    public void markSent(Long id) {
        OrderDO o = getOrThrow(id);
        DataScopes.check(o.getOrgId(), o.getDeptId(), o.getOwnerId(), "采购订单");
        if (!ACTIVE.contains(o.getStatus())) throw BizException.of(GlobalErrorCodes.ILLEGAL_STATE_TRANSITION, o.getStatus().label(), "发送给供应商");
        o.setSentAt(LocalDateTime.now());
        mapper.updateByIdOrFail(o);
        support.log(BIZ_TYPE, o.getId(), o.getDocNo(), "SEND", "发送给供应商", o.getStatus().name(), o.getStatus().name(), null);
    }

    /** 回复交期：确认交期晚于要求日期的行显示交期延误，并通知 PMC */
    @Transactional(rollbackFor = Exception.class)
    public void confirmDates(Long id, List<ConfirmDate> dates) {
        OrderDO o = getOrThrow(id);
        DataScopes.check(o.getOrgId(), o.getDeptId(), o.getOwnerId(), "采购订单");
        if (!ACTIVE.contains(o.getStatus())) throw BizException.of(GlobalErrorCodes.ILLEGAL_STATE_TRANSITION, o.getStatus().label(), "回复交期");
        Map<Long, OrderLineDO> lines = lineMapper.selectByParent(id).stream().collect(Collectors.toMap(OrderLineDO::getId, l -> l));
        List<PurchaseDeliveryDateChangedEvent.Line> changed = new ArrayList<>();
        for (ConfirmDate c : dates == null ? List.<ConfirmDate>of() : dates) {
            OrderLineDO l = lines.get(c.lineId());
            if (l == null) throw new BizException(PurchaseErrorCodes.ORDER_LINE_NOT_EXISTS);
            if (Objects.equals(l.getConfirmedDate(), c.confirmedDate())) continue;
            l.setConfirmedDate(c.confirmedDate());
            lineMapper.updateByIdOrFail(l);
            changed.add(new PurchaseDeliveryDateChangedEvent.Line(l.getId(), l.getMaterialId(), l.getRequiredDate(), l.getConfirmedDate(), delayed(l)));
        }
        if (changed.isEmpty()) return;
        support.log(BIZ_TYPE, o.getId(), o.getDocNo(), "CONFIRM_DATE", "回复交期", o.getStatus().name(), o.getStatus().name(),
                changed.size() + " 行" + (changed.stream().anyMatch(PurchaseDeliveryDateChangedEvent.Line::delayed) ? "，有交期延误" : ""));
        eventPublisher.publish(new PurchaseDeliveryDateChangedEvent(o.getId(), o.getDocNo(), changed));
    }

    /** 记录跟催（11-采购报表 2.1）：更新承诺日期（确认交期）并记入订单操作日志 */
    @Transactional(rollbackFor = Exception.class)
    public void followUp(Long lineId, String content, LocalDate newDate) {
        OrderLineDO l = lineMapper.selectById(lineId);
        if (l == null) throw new BizException(PurchaseErrorCodes.ORDER_LINE_NOT_EXISTS);
        OrderDO o = getOrThrow(l.getOrderId());
        DataScopes.check(o.getOrgId(), o.getDeptId(), o.getOwnerId(), "采购订单");
        String text = PurSupport.requireReason(content, "跟催内容的");
        l.setLastFollowUp(text.length() > 500 ? text.substring(0, 500) : text);
        l.setFollowUpAt(LocalDateTime.now());
        boolean dateChanged = newDate != null && !newDate.equals(l.getConfirmedDate());
        if (dateChanged) l.setConfirmedDate(newDate);
        lineMapper.updateByIdOrFail(l);
        support.log(BIZ_TYPE, o.getId(), o.getDocNo(), "FOLLOW_UP", "跟催", o.getStatus().name(), o.getStatus().name(),
                "第 " + l.getLineNo() + " 行：" + text + (dateChanged ? "（新承诺日期 " + newDate + "）" : ""));
        if (dateChanged) {
            eventPublisher.publish(new PurchaseDeliveryDateChangedEvent(o.getId(), o.getDocNo(), List.of(
                    new PurchaseDeliveryDateChangedEvent.Line(l.getId(), l.getMaterialId(), l.getRequiredDate(), l.getConfirmedDate(), delayed(l)))));
        }
    }

    // ==================== 从申请生成（3.2） ====================

    /** 按供应商分组，每个供应商生成一张草稿订单；数量按供应商 MOQ/MPQ 调整 */
    @Transactional(rollbackFor = Exception.class)
    public FromRequisitionResult fromRequisitions(List<FromRequisitionLine> reqLines) {
        requirePriceView();
        if (reqLines == null || reqLines.isEmpty()) throw new BizException(PurchaseErrorCodes.DOC_NO_LINES);
        List<RequisitionLineDO> lines = requisitionService.pendingLines(reqLines.stream().map(FromRequisitionLine::requisitionLineId).toList());
        Map<Long, RequisitionLineDO> byId = lines.stream().collect(Collectors.toMap(RequisitionLineDO::getId, l -> l));
        Map<Long, RequisitionDO> heads = requisitionService.heads(lines.stream().map(RequisitionLineDO::getRequisitionId).toList());
        Map<Long, List<FromRequisitionLine>> bySupplier = new LinkedHashMap<>();
        for (FromRequisitionLine r : reqLines) {
            RequisitionLineDO l = byId.get(r.requisitionLineId());
            Long sid = r.supplierId() != null ? r.supplierId() : l.getSuggestedSupplierId();
            if (sid == null) throw BizException.of(PurchaseErrorCodes.ORDER_SUPPLIER_REQUIRED, heads.get(l.getRequisitionId()).getDocNo(), l.getLineNo());
            bySupplier.computeIfAbsent(sid, k -> new ArrayList<>()).add(r);
        }
        List<Long> ids = new ArrayList<>();
        List<String> messages = new ArrayList<>();
        LocalDate today = LocalDate.now();
        for (Map.Entry<Long, List<FromRequisitionLine>> e : bySupplier.entrySet()) {
            SupplierDO s = supplierService.getOrThrow(e.getKey());
            List<OrderLineSave> saves = new ArrayList<>();
            for (FromRequisitionLine r : e.getValue()) {
                RequisitionLineDO l = byId.get(r.requisitionLineId());
                MaterialDTO m = support.material(l.getMaterialId());
                BigDecimal pendingBase = l.getBaseQty().subtract(l.getOrderedQty());
                BigDecimal base = r.qty() != null ? support.toBase(m.id(), r.qty(), l.getUom()) : pendingBase;
                BigDecimal adjusted = adjustByMoq(s.getId(), m.id(), base);
                if (adjusted.compareTo(base) != 0) {
                    messages.add("申请 " + heads.get(l.getRequisitionId()).getDocNo() + " 第 " + l.getLineNo() + " 行物料「" + m.code() + "」已按 MOQ/MPQ 取整为 "
                            + PurSupport.plain(adjusted) + " " + m.baseUom());
                }
                LocalDate required = l.getRequiredDate().isBefore(today) ? today : l.getRequiredDate();
                saves.add(new OrderLineSave(null, m.id(), m.baseUom(), adjusted, null, null, null, required, l.getId(), null));
            }
            OrderSaveResult res = create(new OrderSave("STANDARD", s.getId(), null, s.getCurrency(), null, s.getPaymentTermId(), s.getTradeTerm(), true,
                    null, s.getBuyerId(), null, saves, null, null));
            ids.add(res.id());
            messages.addAll(res.warnings());
        }
        return new FromRequisitionResult(ids, messages);
    }

    /** 数量 < MOQ 时取 MOQ；按 MPQ 向上取整（基本单位） */
    BigDecimal adjustByMoq(Long supplierId, Long materialId, BigDecimal baseQty) {
        BigDecimal[] mm = moqMpq(supplierId, materialId);
        BigDecimal q = baseQty;
        if (mm[0] != null && mm[0].signum() > 0 && q.compareTo(mm[0]) < 0) q = mm[0];
        if (mm[1] != null && mm[1].signum() > 0 && q.remainder(mm[1]).signum() != 0) q = q.divide(mm[1], 0, RoundingMode.CEILING).multiply(mm[1]);
        return Decimals.qty(q);
    }

    // ==================== 到货选单 ====================

    public PageResult<OpenLine> openLines(OpenLineQuery q) {
        List<OrderDO> orders = mapper.selectList(new LambdaQueryWrapper<OrderDO>().in(OrderDO::getStatus, ACTIVE)
                .eq(q.getSupplierId() != null, OrderDO::getSupplierId, q.getSupplierId())
                .eq(StringUtils.hasText(q.getOrderType()), OrderDO::getOrderType, q.getOrderType())
                .likeRight(StringUtils.hasText(q.getOrderNo()), OrderDO::getDocNo, q.getOrderNo() == null ? null : q.getOrderNo().trim().toUpperCase())
                .orderByAsc(OrderDO::getDocDate).orderByAsc(OrderDO::getId).last("LIMIT " + MAX_ROWS));
        if (orders.isEmpty()) return PageResult.empty();
        Map<Long, OrderDO> byId = orders.stream().collect(Collectors.toMap(OrderDO::getId, o -> o));
        List<OrderLineDO> lines = lineMapper.selectList(new LambdaQueryWrapper<OrderLineDO>().in(OrderLineDO::getOrderId, byId.keySet())
                .eq(OrderLineDO::getLineStatus, OPEN).eq(q.getMaterialId() != null, OrderLineDO::getMaterialId, q.getMaterialId())
                .orderByAsc(OrderLineDO::getOrderId).orderByAsc(OrderLineDO::getLineNo)).stream()
                .filter(l -> l.getBaseQty().compareTo(l.getReceivedQty()) > 0).toList();
        int from = Math.min((q.getPageNo() - 1) * q.getPageSize(), lines.size());
        List<OrderLineDO> page = lines.subList(from, Math.min(from + q.getPageSize(), lines.size()));
        Map<Long, MaterialDTO> ms = support.materials(page.stream().map(OrderLineDO::getMaterialId).toList());
        return new PageResult<>(page.stream().map(l -> {
            OrderDO o = byId.get(l.getOrderId());
            MaterialDTO m = ms.get(l.getMaterialId());
            BigDecimal openBase = l.getBaseQty().subtract(l.getReceivedQty());
            MaterialStockAttr st = support.materialApi().getStockAttr(l.getMaterialId());
            return new OpenLine(l.getId(), o.getId(), o.getDocNo(), l.getLineNo(), o.getOrderType(), o.getSupplierId(), l.getMaterialId(),
                    m == null ? null : m.code(), m == null ? null : m.name(), m == null ? null : m.spec(), m == null ? null : m.baseUom(), l.getUom(),
                    l.getQty(), l.getReceivedQty(), toUom(l, openBase), openBase, l.getRequiredDate(), l.getConfirmedDate(),
                    support.materialApi().getQualityAttr(l.getMaterialId()).iqcRequired(), st.shelfLifeDays());
        }).toList(), lines.size());
    }

    /** 基本单位数量换算为订单单位（按订单行的换算比例） */
    public static BigDecimal toUom(OrderLineDO l, BigDecimal baseQty) {
        if (l.getBaseQty().signum() == 0 || l.getBaseQty().compareTo(l.getQty()) == 0) return Decimals.qty(baseQty);
        return baseQty.multiply(l.getQty()).divide(l.getBaseQty(), Decimals.QTY_SCALE, RoundingMode.HALF_UP);
    }

    // ==================== 数量回写 ====================

    /**
     * 重新汇总订单行的到货、入库、合格、退货、对账数量与行状态（到货单审核/反审核、入库确认、检验、退货出库、对账审核时调用），
     * 并刷新订单状态（开始执行、完成、恢复执行）。
     * 已到货 = 已审核到货单数量 − 换货退回数量（R04：换货恢复未到货数量）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void refreshLines(Collection<Long> orderLineIds) {
        Set<Long> ids = orderLineIds.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) return;
        Map<Long, OrderLineAggRow> rc = receiptLineMapper.sumByOrderLines(ids).stream().collect(Collectors.toMap(OrderLineAggRow::getId, r -> r));
        Map<Long, ReturnAggRow> rt = returnLineMapper.sumByOrderLines(ids).stream().collect(Collectors.toMap(ReturnAggRow::getId, r -> r));
        Set<Long> orders = new HashSet<>();
        for (OrderLineDO l : lineMapper.selectBatchIds(ids)) {
            OrderLineAggRow a = rc.get(l.getId());
            ReturnAggRow r = rt.get(l.getId());
            BigDecimal replace = r == null ? BigDecimal.ZERO : PurSupport.nz(r.getReplaceQty());
            l.setReceivedQty(Decimals.qty((a == null ? BigDecimal.ZERO : PurSupport.nz(a.getReceivedQty())).subtract(replace)));
            l.setStockedQty(Decimals.qty(a == null ? BigDecimal.ZERO : PurSupport.nz(a.getStockedQty())));
            l.setQualifiedQty(Decimals.qty(a == null ? BigDecimal.ZERO : PurSupport.nz(a.getQualifiedQty())));
            l.setStatementQty(Decimals.qty(a == null ? BigDecimal.ZERO : PurSupport.nz(a.getStatementQty())));
            l.setReturnedQty(Decimals.qty(r == null ? BigDecimal.ZERO : PurSupport.nz(r.getRefundQty())));
            l.setReplaceQty(Decimals.qty(replace));
            l.setFirstReceivedDate(a == null || a.getFirstArrival() == null ? null : a.getFirstArrival().toLocalDate());
            if (!CLOSED.equals(l.getLineStatus())) l.setLineStatus(l.getReceivedQty().compareTo(l.getBaseQty()) >= 0 ? RECEIVED : OPEN);
            lineMapper.updateByIdOrFail(l);
            orders.add(l.getOrderId());
        }
        for (Long oid : orders) refreshStatus(oid);
    }

    /** 已审核 → 执行中（第一次到货）→ 已完成（所有行收齐或关闭）；到货撤销时恢复 */
    public void refreshStatus(Long orderId) {
        OrderDO o = getOrThrow(orderId);
        if (o.getStatus() != DocStatus.APPROVED && o.getStatus() != DocStatus.IN_PROGRESS && o.getStatus() != DocStatus.COMPLETED) return;
        List<OrderLineDO> lines = lineMapper.selectByParent(orderId);
        boolean any = lines.stream().anyMatch(l -> l.getReceivedQty().signum() > 0 || l.getReplaceQty().signum() > 0)
                || receiptLineMapper.sumByOrderLines(lines.stream().map(OrderLineDO::getId).toList()).stream().anyMatch(a -> PurSupport.nz(a.getReceivedQty()).signum() > 0);
        boolean done = !lines.isEmpty() && lines.stream().allMatch(l -> !OPEN.equals(l.getLineStatus()));
        if (o.getStatus() == DocStatus.COMPLETED && !done) support.fire(PurStateMachines.EXECUTABLE, mapper, o, BIZ_TYPE, PurAction.REOPEN, null);
        if (o.getStatus() == DocStatus.APPROVED && any) support.fire(PurStateMachines.EXECUTABLE, mapper, o, BIZ_TYPE, PurAction.START, null);
        if (o.getStatus() == DocStatus.IN_PROGRESS && done) support.fire(PurStateMachines.EXECUTABLE, mapper, o, BIZ_TYPE, PurAction.COMPLETE, null);
        else if (o.getStatus() == DocStatus.IN_PROGRESS && !any) support.fire(PurStateMachines.EXECUTABLE, mapper, o, BIZ_TYPE, PurAction.REOPEN, null);
    }

    // ==================== 打印 ====================

    public Map<String, Object> printData(Long id, String lang) {
        OrderDetail d = detail(id);
        boolean en = "en".equalsIgnoreCase(lang) || "en-US".equalsIgnoreCase(lang);
        SupplierDO s = supplierService.getOrThrow(d.supplierId());
        SupplierContactDO c = d.supplierContactId() == null ? null
                : supplierService.contacts(s.getId()).stream().filter(x -> x.getId().equals(d.supplierContactId())).findFirst().orElse(null);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("docNo", d.docNo());
        data.put("docDate", d.docDate());
        data.put("status", d.status());
        data.put("orderTypeName", "SAMPLE".equals(d.orderType()) ? (en ? "Sample" : "样品") : (en ? "Standard" : "标准"));
        data.put("orderVersion", d.orderVersion());
        OrgDTO company = support.orgs(List.of(nz(getOrThrow(id).getOrgId()))).values().stream().findFirst().orElse(null);
        data.put("companyName", company == null ? "" : (en && company.nameEn() != null ? company.nameEn() : company.name()));
        data.put("companyAddress", company == null ? "" : Objects.toString(en && company.addressEn() != null ? company.addressEn() : company.address(), ""));
        data.put("supplierName", en && s.getNameEn() != null ? s.getNameEn() : s.getName());
        data.put("supplierNameEn", Objects.toString(s.getNameEn(), s.getName()));
        data.put("supplierAddress", Objects.toString(s.getAddress(), ""));
        data.put("contactName", c == null ? "" : c.getName());
        data.put("contactPhone", c == null ? "" : Objects.toString(c.getMobile(), Objects.toString(c.getPhone(), "")));
        data.put("currency", d.currency());
        data.put("paymentTermName", Objects.toString(d.paymentTermName(), ""));
        data.put("tradeTerm", Objects.toString(d.tradeTerm(), ""));
        data.put("deliveryAddress", Objects.toString(d.deliveryAddress(), company == null ? "" : Objects.toString(company.address(), "")));
        data.put("buyerName", Objects.toString(d.ownerName(), ""));
        data.put("remark", Objects.toString(d.remark(), ""));
        data.put("amount", d.amount());
        data.put("taxAmount", d.taxAmount());
        data.put("totalAmount", d.totalAmount());
        List<Map<String, Object>> lines = new ArrayList<>();
        for (OrderLineResp l : d.lines()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("lineNo", l.lineNo());
            m.put("materialCode", l.materialCode());
            m.put("materialName", en && l.materialNameEn() != null ? l.materialNameEn() : l.materialName());
            m.put("spec", Objects.toString(l.materialSpec(), ""));
            m.put("supplierPartNo", Objects.toString(l.supplierPartNo(), ""));
            m.put("uom", l.uom());
            m.put("qty", l.qty());
            m.put("price", l.price());
            m.put("priceInclTax", l.priceInclTax());
            m.put("taxRate", PurSupport.pctText(l.taxRate()) + "%");
            m.put("amount", l.amount());
            m.put("totalAmount", l.totalAmount());
            m.put("requiredDate", l.requiredDate());
            m.put("confirmedDate", l.confirmedDate());
            m.put("remark", Objects.toString(l.remark(), ""));
            lines.add(m);
        }
        data.put("lines", lines);
        return data;
    }

    // ==================== 提醒（R10） ====================

    /** 确认交期（没有则要求日期）前 N 天仍未到货的行提醒采购员；已逾期的每天提醒一次 */
    public int remindOverdue() {
        int days = support.params().getInt(PurchaseModuleConfig.P_OVERDUE_REMIND_DAYS);
        LocalDate today = LocalDate.now();
        List<OrderDO> orders = mapper.selectList(new LambdaQueryWrapper<OrderDO>().in(OrderDO::getStatus, ACTIVE));
        if (orders.isEmpty()) return 0;
        Map<Long, OrderDO> byId = orders.stream().collect(Collectors.toMap(OrderDO::getId, o -> o));
        Map<Long, int[]> byOwner = new HashMap<>();
        for (OrderLineDO l : lineMapper.selectList(new LambdaQueryWrapper<OrderLineDO>().in(OrderLineDO::getOrderId, byId.keySet())
                .eq(OrderLineDO::getLineStatus, OPEN))) {
            LocalDate due = dueDate(l);
            if (due.isAfter(today.plusDays(days))) continue;
            int[] c = byOwner.computeIfAbsent(byId.get(l.getOrderId()).getOwnerId(), k -> new int[2]);
            if (due.isBefore(today)) c[1]++;
            else c[0]++;
        }
        byOwner.forEach((owner, c) -> support.message(List.of(owner), "采购交期提醒",
                (c[1] > 0 ? "已逾期未到货 " + c[1] + " 行；" : "") + (c[0] > 0 ? days + " 天内到期未到货 " + c[0] + " 行；" : "") + "请及时跟催",
                "/purchase/report"));
        return byOwner.size();
    }

    // ==================== 工具 ====================

    String json(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    public OrderDO getOrThrow(Long id) {
        OrderDO o = id == null ? null : mapper.selectById(id);
        if (o == null) throw new BizException(PurchaseErrorCodes.ORDER_NOT_EXISTS);
        return o;
    }

    public Map<Long, OrderDO> byIds(Collection<Long> ids) {
        Set<Long> set = ids.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (set.isEmpty()) return Collections.emptyMap();
        return mapper.selectBatchIds(set).stream().collect(Collectors.toMap(OrderDO::getId, o -> o));
    }

    public Map<Long, OrderLineDO> linesByIds(Collection<Long> ids) {
        Set<Long> set = ids.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (set.isEmpty()) return Collections.emptyMap();
        return lineMapper.selectBatchIds(set).stream().collect(Collectors.toMap(OrderLineDO::getId, l -> l));
    }

    private static Long nz(Long v) {
        return v == null ? 0L : v;
    }
}
