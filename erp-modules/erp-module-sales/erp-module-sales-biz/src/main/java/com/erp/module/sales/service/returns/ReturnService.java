package com.erp.module.sales.service.returns;

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
import com.erp.module.crm.api.customer.CustomerDTO;
import com.erp.module.engineering.api.material.MaterialDTO;
import com.erp.module.inventory.api.doc.InventoryDocApi;
import com.erp.module.inventory.api.doc.SourceRef;
import com.erp.module.inventory.api.doc.StockDocEvent;
import com.erp.module.inventory.api.doc.StockInConfirmedEvent;
import com.erp.module.inventory.api.doc.StockInRequest;
import com.erp.module.inventory.api.doc.StockInType;
import com.erp.module.sales.api.SalesErrorCodes;
import com.erp.module.sales.api.returns.SalesReturnApi;
import com.erp.module.sales.api.returns.SalesReturnApprovedEvent;
import com.erp.module.sales.api.returns.SalesReturnReceivedEvent;
import com.erp.module.sales.config.SalesModuleConfig;
import com.erp.module.sales.controller.vo.CommonVOs.DocResult;
import com.erp.module.sales.controller.vo.CommonVOs.RelatedDoc;
import com.erp.module.sales.controller.vo.CommonVOs.SaveResult;
import com.erp.module.sales.controller.vo.ReturnVOs.JudgeReq;
import com.erp.module.sales.controller.vo.ReturnVOs.ReturnDetail;
import com.erp.module.sales.controller.vo.ReturnVOs.ReturnLineResp;
import com.erp.module.sales.controller.vo.ReturnVOs.ReturnLineSave;
import com.erp.module.sales.controller.vo.ReturnVOs.ReturnQuery;
import com.erp.module.sales.controller.vo.ReturnVOs.ReturnRow;
import com.erp.module.sales.controller.vo.ReturnVOs.ReturnSave;
import com.erp.module.sales.controller.vo.ReturnVOs.ShippedLine;
import com.erp.module.sales.dal.dataobject.SalOrderDO;
import com.erp.module.sales.dal.dataobject.SalOrderExecDO;
import com.erp.module.sales.dal.dataobject.SalOrderLineDO;
import com.erp.module.sales.dal.dataobject.SalReturnDO;
import com.erp.module.sales.dal.dataobject.SalReturnLineDO;
import com.erp.module.sales.dal.mapper.SalOrderExecMapper;
import com.erp.module.sales.dal.mapper.SalOrderLineMapper;
import com.erp.module.sales.dal.mapper.SalOrderMapper;
import com.erp.module.sales.dal.mapper.SalReturnLineMapper;
import com.erp.module.sales.dal.mapper.SalReturnMapper;
import com.erp.module.sales.service.SalAction;
import com.erp.module.sales.service.SalStateMachines;
import com.erp.module.sales.service.SalSupport;
import com.erp.module.sales.service.order.OrderExecService;
import com.erp.module.sales.service.order.OrderService;
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
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 销售退货（需求 04-06）：草稿 → 待审批 → 已审核（生成退货仓入库单）→ 已完成（入库并判定完成）；入库单未确认前可作废。
 */
@Service("salReturnService")
public class ReturnService implements SalesReturnApi {

    public static final String BIZ_TYPE = SalesModuleConfig.RETURN;
    public static final String REFUND = "REFUND";
    public static final String REPLACE = "REPLACE";

    private final SalReturnMapper mapper;
    private final SalReturnLineMapper lineMapper;
    private final SalOrderMapper orderMapper;
    private final SalOrderLineMapper orderLineMapper;
    private final SalOrderExecMapper execMapper;
    private final OrderExecService execService;
    private final SalSupport support;
    private final InventoryDocApi inventoryDocApi;
    private final WorkflowApi workflowApi;
    private final FileApi fileApi;
    private final DomainEventPublisher eventPublisher;

    public ReturnService(SalReturnMapper mapper, SalReturnLineMapper lineMapper, SalOrderMapper orderMapper, SalOrderLineMapper orderLineMapper,
                         SalOrderExecMapper execMapper, OrderExecService execService, SalSupport support, InventoryDocApi inventoryDocApi,
                         WorkflowApi workflowApi, FileApi fileApi, DomainEventPublisher eventPublisher) {
        this.mapper = mapper;
        this.lineMapper = lineMapper;
        this.orderMapper = orderMapper;
        this.orderLineMapper = orderLineMapper;
        this.execMapper = execMapper;
        this.execService = execService;
        this.support = support;
        this.inventoryDocApi = inventoryDocApi;
        this.workflowApi = workflowApi;
        this.fileApi = fileApi;
        this.eventPublisher = eventPublisher;
    }

    // ==================== 查询 ====================

    public PageResult<ReturnRow> page(ReturnQuery q) {
        LambdaQueryWrapper<SalReturnDO> w = new LambdaQueryWrapper<SalReturnDO>().eq(SalReturnDO::getDeleted, false)
                .likeRight(StringUtils.hasText(q.getDocNo()), SalReturnDO::getDocNo, q.getDocNo() == null ? null : q.getDocNo().trim().toUpperCase())
                .eq(q.getCustomerId() != null, SalReturnDO::getCustomerId, q.getCustomerId())
                .likeRight(StringUtils.hasText(q.getRmaNo()), SalReturnDO::getRmaNo, q.getRmaNo())
                .eq(StringUtils.hasText(q.getReturnReason()), SalReturnDO::getReturnReason, q.getReturnReason())
                .eq(StringUtils.hasText(q.getHandling()), SalReturnDO::getHandling, q.getHandling())
                .ge(q.getDateFrom() != null, SalReturnDO::getDocDate, q.getDateFrom())
                .le(q.getDateTo() != null, SalReturnDO::getDocDate, q.getDateTo());
        if (StringUtils.hasText(q.getStatuses())) {
            w.in(SalReturnDO::getStatus, Arrays.stream(q.getStatuses().split(",")).map(String::trim).map(DocStatus::valueOf).toList());
        }
        if (q.getMaterialId() != null) {
            w.inSql(SalReturnDO::getId, "SELECT return_id FROM sal_return_line WHERE deleted = 0 AND material_id = " + q.getMaterialId().longValue());
        }
        w.orderByDesc(SalReturnDO::getDocDate).orderByDesc(SalReturnDO::getId);
        IPage<SalReturnDO> page = mapper.selectScopedPage(Page.of(q.getPageNo(), q.getPageSize()), w);
        List<SalReturnDO> list = page.getRecords();
        Map<Long, List<SalReturnLineDO>> lines = list.isEmpty() ? Map.of()
                : lineMapper.selectByParents(list.stream().map(SalReturnDO::getId).toList()).stream().collect(Collectors.groupingBy(SalReturnLineDO::getReturnId));
        Map<Long, MaterialDTO> ms = support.materials(lines.values().stream().flatMap(List::stream).map(SalReturnLineDO::getMaterialId).toList());
        Map<Long, CustomerDTO> cs = support.customers(list.stream().map(SalReturnDO::getCustomerId).toList());
        Map<Long, UserDTO> users = support.users(list.stream().map(SalReturnDO::getOwnerId).toList());
        return new PageResult<>(list.stream().map(r -> {
            List<SalReturnLineDO> ls = lines.getOrDefault(r.getId(), List.of());
            BigDecimal qty = SalSupport.sum(ls.stream().map(SalReturnLineDO::getQty).toList());
            BigDecimal received = SalSupport.sum(ls.stream().map(SalReturnLineDO::getReceivedQty).toList());
            BigDecimal judged = SalSupport.sum(ls.stream().map(ReturnService::judged).toList());
            String summary = ls.stream().map(l -> ms.get(l.getMaterialId())).filter(Objects::nonNull).map(MaterialDTO::code).distinct().limit(3)
                    .collect(Collectors.joining("、")) + (ls.size() > 3 ? " 等" : "");
            return new ReturnRow(r.getId(), r.getDocNo(), r.getCustomerId(), SalSupport.shortName(cs, r.getCustomerId()), r.getRmaNo(), r.getReturnReason(),
                    r.getHandling(), summary, qty, r.getCurrency(), r.getTotalAmount(), progress(received, qty), progress(judged, received.signum() == 0 ? qty : received),
                    r.getStatus().name(), r.getOwnerId(), SalSupport.name(users, r.getOwnerId()), r.getDocDate());
        }).toList(), page.getTotal());
    }

    static String progress(BigDecimal done, BigDecimal total) {
        if (done.signum() == 0) return "NONE";
        return done.compareTo(total) >= 0 ? "DONE" : "PARTIAL";
    }

    static BigDecimal judged(SalReturnLineDO l) {
        return l.getGoodQty().add(l.getReworkQty()).add(l.getScrapQty());
    }

    public ReturnDetail detail(Long id) {
        SalReturnDO r = getOrThrow(id);
        DataScopes.check(r.getOrgId(), r.getDeptId(), r.getOwnerId(), "销售退货单");
        List<SalReturnLineDO> lines = lineMapper.selectByParent(id);
        Map<Long, SalOrderLineDO> ols = orderLineMapper.selectBatchIds(lines.isEmpty() ? List.of(0L) : lines.stream().map(SalReturnLineDO::getOrderLineId).toList())
                .stream().collect(Collectors.toMap(SalOrderLineDO::getId, l -> l));
        Map<Long, SalOrderDO> orders = lines.isEmpty() ? Map.of() : orderMapper.selectBatchIds(lines.stream().map(SalReturnLineDO::getOrderId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(SalOrderDO::getId, o -> o));
        Map<Long, MaterialDTO> ms = support.materials(lines.stream().map(SalReturnLineDO::getMaterialId).toList());
        Map<Long, BigDecimal> occupied = occupied(ols.keySet(), id);
        List<RelatedDoc> related = new ArrayList<>();
        orders.values().stream().sorted(Comparator.comparing(SalOrderDO::getId)).forEach(o -> related.add(new RelatedDoc("UP", "销售订单", o.getDocNo(),
                o.getDocDate(), o.getStatus().name(), o.getStatus().label(), "/sales/order/" + o.getId())));
        return new ReturnDetail(r.getId(), r.getDocNo(), r.getDocDate(), r.getStatus().name(), r.getCustomerId(), support.customer(r.getCustomerId()).shortName(),
                r.getRmaNo(), r.getReturnReason(), r.getHandling(), r.getCurrency(), r.getExchangeRate(), r.getTotalAmount(), r.getTotalAmountBase(),
                r.getComplaintNo(), r.getStockInId(), r.getExpectedArrivalDate(), r.getVoidReason(), r.getRemark(), r.getOwnerId(),
                support.userName(r.getOwnerId()), r.getCreatedAt(), r.getVersion(), lines.stream().map(l -> {
                    SalOrderLineDO ol = ols.get(l.getOrderLineId());
                    SalOrderDO o = orders.get(l.getOrderId());
                    MaterialDTO m = ms.get(l.getMaterialId());
                    BigDecimal returnable = ol == null ? BigDecimal.ZERO : ol.getShippedQty().subtract(occupied.getOrDefault(ol.getId(), BigDecimal.ZERO)).max(BigDecimal.ZERO);
                    return new ReturnLineResp(l.getId(), l.getLineNo(), l.getOrderId(), o == null ? null : o.getDocNo(), l.getOrderLineId(),
                            ol == null ? null : ol.getLineNo(), l.getMaterialId(), m == null ? null : m.code(), m == null ? null : m.name(),
                            m == null ? null : m.spec(), m == null ? null : m.baseUom(), l.getBatchNo(), l.getSerialNos(), l.getQty(), returnable,
                            l.getPriceInclTax(), l.getTaxRate(), l.getTotalAmount(), l.getReceivedQty(), l.getGoodQty(), l.getReworkQty(), l.getScrapQty(),
                            l.getRemark());
                }).toList(), related);
    }

    /**
     * 订单行被其他未作废退货单占用的数量（R01）：退款类全部计入（已退货但已出货不减少）；换货类只计未收货部分（收货后已出货数量已扣减）。
     */
    Map<Long, BigDecimal> occupied(Collection<Long> orderLineIds, Long excludeReturnId) {
        if (orderLineIds.isEmpty()) return Map.of();
        List<SalReturnLineDO> lines = lineMapper.selectList(new LambdaQueryWrapper<SalReturnLineDO>().in(SalReturnLineDO::getOrderLineId, orderLineIds)
                .ne(excludeReturnId != null, SalReturnLineDO::getReturnId, excludeReturnId));
        if (lines.isEmpty()) return Map.of();
        Map<Long, SalReturnDO> heads = mapper.selectBatchIds(lines.stream().map(SalReturnLineDO::getReturnId).collect(Collectors.toSet())).stream()
                .filter(r -> r.getStatus() != DocStatus.VOIDED).collect(Collectors.toMap(SalReturnDO::getId, r -> r));
        Map<Long, BigDecimal> map = new HashMap<>();
        for (SalReturnLineDO l : lines) {
            SalReturnDO r = heads.get(l.getReturnId());
            if (r == null) continue;
            BigDecimal q = REPLACE.equals(r.getHandling()) ? l.getQty().subtract(l.getReceivedQty()).max(BigDecimal.ZERO) : l.getQty();
            map.merge(l.getOrderLineId(), q, BigDecimal::add);
        }
        return map;
    }

    /** 退货选单：该客户已出货的订单行 */
    public List<ShippedLine> shippedLines(Long customerId, Long materialId) {
        List<SalOrderDO> orders = orderMapper.selectList(new LambdaQueryWrapper<SalOrderDO>().eq(SalOrderDO::getCustomerId, customerId)
                .in(SalOrderDO::getStatus, DocStatus.APPROVED, DocStatus.IN_PROGRESS, DocStatus.COMPLETED, DocStatus.CLOSED));
        if (orders.isEmpty()) return List.of();
        Map<Long, SalOrderDO> byId = orders.stream().collect(Collectors.toMap(SalOrderDO::getId, o -> o));
        List<SalOrderLineDO> lines = orderLineMapper.selectList(new LambdaQueryWrapper<SalOrderLineDO>().in(SalOrderLineDO::getOrderId, byId.keySet())
                .gt(SalOrderLineDO::getShippedQty, 0).eq(materialId != null, SalOrderLineDO::getMaterialId, materialId));
        Map<Long, BigDecimal> occupied = occupied(lines.stream().map(SalOrderLineDO::getId).toList(), null);
        Map<Long, MaterialDTO> ms = support.materials(lines.stream().map(SalOrderLineDO::getMaterialId).toList());
        Map<Long, LocalDate> lastShip = execMapper.selectList(new LambdaQueryWrapper<SalOrderExecDO>().in(SalOrderExecDO::getOrderId, byId.keySet())
                .eq(SalOrderExecDO::getExecType, OrderExecService.SHIP)).stream().filter(e -> e.getOrderLineId() != null && e.getExecDate() != null)
                .collect(Collectors.toMap(SalOrderExecDO::getOrderLineId, SalOrderExecDO::getExecDate, (a, b) -> a.isAfter(b) ? a : b));
        return lines.stream().map(l -> {
            SalOrderDO o = byId.get(l.getOrderId());
            MaterialDTO m = ms.get(l.getMaterialId());
            BigDecimal occ = occupied.getOrDefault(l.getId(), BigDecimal.ZERO);
            return new ShippedLine(l.getId(), o.getId(), o.getDocNo(), l.getLineNo(), l.getMaterialId(), m == null ? null : m.code(),
                    m == null ? null : m.name(), m == null ? null : m.spec(), m == null ? null : m.baseUom(), l.getShippedQty(), occ,
                    l.getShippedQty().subtract(occ).max(BigDecimal.ZERO), o.getCurrency(), basePrice(l), lastShip.get(l.getId()));
        }).filter(x -> x.returnableQty().signum() > 0).sorted(Comparator.comparing(ShippedLine::orderNo).reversed().thenComparing(ShippedLine::lineNo)).toList();
    }

    /** 含税单价折算为每基本单位 */
    static BigDecimal basePrice(SalOrderLineDO l) {
        return l.getBaseQty().signum() == 0 ? l.getPriceInclTax()
                : l.getPriceInclTax().multiply(l.getQty()).divide(l.getBaseQty(), 6, RoundingMode.HALF_UP);
    }

    // ==================== 编辑 ====================

    @Transactional(rollbackFor = Exception.class)
    public SaveResult create(ReturnSave req) {
        SalReturnDO r = new SalReturnDO();
        r.setDocNo(support.nextNo(BIZ_TYPE));
        r.setDocDate(LocalDate.now());
        r.setStatus(DocStatus.DRAFT);
        r.setTotalAmount(BigDecimal.ZERO);
        r.setTotalAmountBase(BigDecimal.ZERO);
        CustomerDTO c = fillHeader(r, req);
        support.fillOwner(r, c.ownerId());
        r.setCurrency(support.baseCurrency());
        r.setExchangeRate(BigDecimal.ONE);
        mapper.insert(r);
        saveLines(r, req.lines());
        mapper.updateByIdOrFail(r);
        if (req.fileIds() != null && !req.fileIds().isEmpty()) fileApi.bind(req.fileIds(), BIZ_TYPE, r.getId());
        return new SaveResult(r.getId(), List.of());
    }

    @Transactional(rollbackFor = Exception.class)
    public SaveResult update(Long id, ReturnSave req) {
        SalReturnDO r = getOrThrow(id);
        DataScopes.check(r.getOrgId(), r.getDeptId(), r.getOwnerId(), "销售退货单");
        SalSupport.requireDraft(r);
        if (req.version() != null) r.setVersion(req.version());
        fillHeader(r, req);
        saveLines(r, req.lines());
        mapper.updateByIdOrFail(r);
        if (req.fileIds() != null && !req.fileIds().isEmpty()) fileApi.bind(req.fileIds(), BIZ_TYPE, r.getId());
        return new SaveResult(r.getId(), List.of());
    }

    private CustomerDTO fillHeader(SalReturnDO r, ReturnSave req) {
        CustomerDTO c = support.customer(req.customerId());
        if (!Set.of(REFUND, REPLACE).contains(req.handling())) throw BizException.of(GlobalErrorCodes.BAD_REQUEST, "处理方式");
        support.dict().validate("sal_return_reason", req.returnReason(), "退货原因");
        r.setCustomerId(c.id());
        r.setRmaNo(SalSupport.trim(req.rmaNo()));
        r.setReturnReason(req.returnReason());
        r.setHandling(req.handling());
        r.setComplaintNo(SalSupport.trim(req.complaintNo()));
        r.setExpectedArrivalDate(req.expectedArrivalDate());
        r.setRemark(SalSupport.trim(req.remark()));
        return c;
    }

    /** R01：退货数量 ≤ 原出货数量 − 已退货（含其他未完成退货单占用）；币别取原订单（同一退货单只能是同一币别） */
    private void saveLines(SalReturnDO r, List<ReturnLineSave> lines) {
        if (lines == null || lines.isEmpty()) throw new BizException(SalesErrorCodes.DOC_NO_LINES);
        Map<Long, SalOrderLineDO> ols = orderLineMapper.selectBatchIds(lines.stream().map(ReturnLineSave::orderLineId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(SalOrderLineDO::getId, l -> l));
        Map<Long, SalOrderDO> orders = ols.isEmpty() ? Map.of() : orderMapper.selectBatchIds(ols.values().stream().map(SalOrderLineDO::getOrderId)
                .collect(Collectors.toSet())).stream().collect(Collectors.toMap(SalOrderDO::getId, o -> o));
        Map<Long, BigDecimal> occupied = occupied(ols.keySet(), r.getId());
        Map<Long, BigDecimal> used = new HashMap<>();
        lineMapper.deleteByParent(r.getId());
        String currency = null;
        BigDecimal rate = BigDecimal.ONE;
        BigDecimal total = BigDecimal.ZERO;
        int no = 0;
        for (ReturnLineSave s : lines) {
            no++;
            SalOrderLineDO ol = ols.get(s.orderLineId());
            if (ol == null) throw new BizException(SalesErrorCodes.ORDER_LINE_NOT_EXISTS);
            SalOrderDO o = orders.get(ol.getOrderId());
            if (!o.getCustomerId().equals(r.getCustomerId())) throw BizException.of(SalesErrorCodes.RETURN_CUSTOMER_MISMATCH, no);
            if (currency == null) {
                currency = o.getCurrency();
                rate = o.getExchangeRate();
            } else if (!currency.equals(o.getCurrency())) {
                throw BizException.of(GlobalErrorCodes.BAD_REQUEST, "同一退货单只能退同一币别的订单");
            }
            if (s.qty().signum() <= 0) throw BizException.of(SalesErrorCodes.LINE_QTY_POSITIVE, no);
            BigDecimal qty = Decimals.qty(s.qty());
            BigDecimal returnable = ol.getShippedQty().subtract(occupied.getOrDefault(ol.getId(), BigDecimal.ZERO)).subtract(used.getOrDefault(ol.getId(), BigDecimal.ZERO));
            if (qty.compareTo(returnable) > 0) throw BizException.of(SalesErrorCodes.RETURN_OVER_QTY, no, SalSupport.plain(returnable.max(BigDecimal.ZERO)));
            used.merge(ol.getId(), qty, BigDecimal::add);
            SalReturnLineDO d = new SalReturnLineDO();
            d.setReturnId(r.getId());
            d.setLineNo(no);
            d.setOrderId(o.getId());
            d.setOrderLineId(ol.getId());
            d.setShipmentLineId(s.shipmentLineId());
            d.setMaterialId(ol.getMaterialId());
            d.setBatchNo(SalSupport.trim(s.batchNo()));
            d.setSerialNos(SalSupport.trim(s.serialNos()));
            d.setQty(qty);
            d.setPriceInclTax(basePrice(ol));
            d.setTaxRate(ol.getTaxRate());
            d.setTotalAmount(Decimals.multiplyAmount(qty, d.getPriceInclTax()));
            d.setReceivedQty(BigDecimal.ZERO);
            d.setGoodQty(BigDecimal.ZERO);
            d.setReworkQty(BigDecimal.ZERO);
            d.setScrapQty(BigDecimal.ZERO);
            d.setRemark(SalSupport.trim(s.remark()));
            lineMapper.insert(d);
            total = total.add(d.getTotalAmount());
        }
        r.setCurrency(currency);
        r.setExchangeRate(rate);
        r.setTotalAmount(total);
        r.setTotalAmountBase(support.currencyApi().toBase(total, rate));
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        SalReturnDO r = getOrThrow(id);
        DataScopes.check(r.getOrgId(), r.getDeptId(), r.getOwnerId(), "销售退货单");
        SalSupport.requireDraft(r);
        lineMapper.deleteByParent(id);
        mapper.deleteById(id);
        fileApi.deleteByBiz(BIZ_TYPE, id);
    }

    // ==================== 提交 / 审核 / 作废 ====================

    @Transactional(rollbackFor = Exception.class)
    public DocResult submit(Long id) {
        SalReturnDO r = getOrThrow(id);
        DataScopes.check(r.getOrgId(), r.getDeptId(), r.getOwnerId(), "销售退货单");
        SalSupport.requireDraft(r);
        List<SalReturnLineDO> lines = lineMapper.selectByParent(id);
        if (lines.isEmpty()) throw new BizException(SalesErrorCodes.DOC_NO_LINES);
        // 再次校验可退数量
        Map<Long, SalOrderLineDO> ols = orderLineMapper.selectBatchIds(lines.stream().map(SalReturnLineDO::getOrderLineId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(SalOrderLineDO::getId, l -> l));
        Map<Long, BigDecimal> occupied = occupied(ols.keySet(), id);
        Map<Long, BigDecimal> used = new HashMap<>();
        for (SalReturnLineDO l : lines) {
            SalOrderLineDO ol = ols.get(l.getOrderLineId());
            BigDecimal returnable = ol.getShippedQty().subtract(occupied.getOrDefault(ol.getId(), BigDecimal.ZERO)).subtract(used.getOrDefault(ol.getId(), BigDecimal.ZERO));
            if (l.getQty().compareTo(returnable) > 0) throw BizException.of(SalesErrorCodes.RETURN_OVER_QTY, l.getLineNo(), SalSupport.plain(returnable.max(BigDecimal.ZERO)));
            used.merge(ol.getId(), l.getQty(), BigDecimal::add);
        }
        support.fire(SalStateMachines.RETURN, mapper, r, BIZ_TYPE, SalAction.SUBMIT, null);
        Map<String, Object> vars = new HashMap<>();
        vars.put("amountBase", r.getTotalAmountBase());
        vars.put("returnReason", r.getReturnReason());
        Map<String, Long> users = new HashMap<>();
        users.put("ownerId", r.getOwnerId());
        StartResult res = workflowApi.start(BIZ_TYPE, id, r.getDocNo(), "销售退货 " + r.getDocNo() + " " + support.customer(r.getCustomerId()).shortName(),
                vars, users, support.currentUser());
        if (!res.isStarted()) approve(r);
        return DocResult.of(r.getStatus().name());
    }

    @EventListener
    public void onApproval(ApprovalCompletedEvent e) {
        if (!BIZ_TYPE.equals(e.getBizType())) return;
        SalReturnDO r = getOrThrow(e.getBizId());
        if (r.getStatus() != DocStatus.PENDING_APPROVAL) return;
        switch (e.getResult()) {
            case APPROVED -> approve(r);
            case WITHDRAWN -> support.fire(SalStateMachines.RETURN, mapper, r, BIZ_TYPE, SalAction.WITHDRAW, null);
            default -> support.fire(SalStateMachines.RETURN, mapper, r, BIZ_TYPE, SalAction.REJECT, e.getComment());
        }
    }

    /** R02：审核生成退货入库单（仓库模块按入库类型放入退货仓） */
    private void approve(SalReturnDO r) {
        List<SalReturnLineDO> lines = lineMapper.selectByParent(r.getId());
        Map<Long, MaterialDTO> ms = support.materials(lines.stream().map(SalReturnLineDO::getMaterialId).toList());
        List<StockInRequest.Line> reqLines = lines.stream().map(l -> new StockInRequest.Line(l.getId(), l.getMaterialId(), ms.get(l.getMaterialId()).baseUom(),
                l.getQty(), l.getBatchNo(), null, null, null, serials(l.getSerialNos()))).toList();
        List<Long> ids = inventoryDocApi.createStockIn(new StockInRequest(StockInType.SALES_RETURN, new SourceRef(BIZ_TYPE, r.getId(), r.getDocNo()), null,
                LocalDate.now(), null, r.getCustomerId(), reqLines));
        r.setStockInId(ids.isEmpty() ? null : ids.get(0));
        support.fire(SalStateMachines.RETURN, mapper, r, BIZ_TYPE, SalAction.APPROVE, null);
        eventPublisher.publish(new SalesReturnApprovedEvent(r.getId(), r.getDocNo(), r.getCustomerId(), r.getHandling()));
    }

    static List<String> serials(String s) {
        if (!StringUtils.hasText(s)) return List.of();
        return Arrays.stream(s.split("[,，;；\\s]+")).map(String::trim).filter(StringUtils::hasText).toList();
    }

    /** R06：已审核且入库单未确认时作废（同时作废入库单）；已入库不能作废 */
    @Transactional(rollbackFor = Exception.class)
    public void voidDoc(Long id, String reason) {
        SalReturnDO r = getOrThrow(id);
        DataScopes.check(r.getOrgId(), r.getDeptId(), r.getOwnerId(), "销售退货单");
        String why = SalSupport.requireReason(reason, "作废");
        if (r.getStatus() == DocStatus.APPROVED) {
            if (lineMapper.selectByParent(id).stream().anyMatch(l -> l.getReceivedQty().signum() > 0)) throw new BizException(SalesErrorCodes.RETURN_STOCKED);
            try {
                inventoryDocApi.cancelBySource(BIZ_TYPE, id);
            } catch (BizException e) {
                throw new BizException(SalesErrorCodes.RETURN_STOCKED);
            }
        }
        r.setVoidReason(why.length() > 256 ? why.substring(0, 256) : why);
        support.fire(SalStateMachines.RETURN, mapper, r, BIZ_TYPE, SalAction.VOID, why);
    }

    // ==================== 入库 / 判定回写 ====================

    /** R03：退货入库确认 → 已收货；REFUND 订单行已退货增加、REPLACE 已出货减少；发布 SalesReturnReceivedEvent（财务红字应收） */
    @EventListener
    public void onStockIn(StockInConfirmedEvent e) {
        if (e.getSource() == null || !BIZ_TYPE.equals(e.getSource().sourceType())) return;
        if (execMapper.selectCount(new LambdaQueryWrapper<SalOrderExecDO>().eq(SalOrderExecDO::getExecType, OrderExecService.RETURN)
                .eq(SalOrderExecDO::getDocId, e.getStockInId())) > 0) {
            return;
        }
        SalReturnDO r = getOrThrow(e.getSource().sourceId());
        Map<Long, BigDecimal> qty = new LinkedHashMap<>();
        e.getLines().forEach(l -> {
            if (l.sourceLineId() != null) qty.merge(l.sourceLineId(), l.baseQty(), BigDecimal::add);
        });
        applyReceipt(r, qty, e.getStockInId(), e.getStockInNo(), false);
    }

    /** 入库反确认：扣回已收货与订单行数量 */
    @EventListener
    public void onStockDoc(StockDocEvent e) {
        if (e.getSource() == null || !BIZ_TYPE.equals(e.getSource().sourceType())) return;
        SalReturnDO r = mapper.selectById(e.getSource().sourceId());
        if (r == null) return;
        if (e.getKind() == StockDocEvent.Kind.IN_REVERSED) {
            List<SalOrderExecDO> execs = execMapper.selectList(new LambdaQueryWrapper<SalOrderExecDO>().eq(SalOrderExecDO::getExecType, OrderExecService.RETURN)
                    .eq(SalOrderExecDO::getDocId, e.getDocId()));
            BigDecimal net = SalSupport.sum(execs.stream().map(SalOrderExecDO::getQty).toList());
            if (net.signum() <= 0) return;
            Map<Long, Long> lineByOrderLine = lineMapper.selectByParent(r.getId()).stream()
                    .collect(Collectors.toMap(SalReturnLineDO::getOrderLineId, SalReturnLineDO::getId, (a, b) -> a));
            Map<Long, BigDecimal> qty = new LinkedHashMap<>();
            execs.forEach(x -> qty.merge(lineByOrderLine.get(x.getOrderLineId()), x.getQty(), BigDecimal::add));
            applyReceipt(r, qty, e.getDocId(), e.getDocNo(), true);
        } else if (e.getKind() == StockDocEvent.Kind.REJECTED) {
            support.message(List.of(r.getOwnerId()), "退货入库单被退回", "销售退货 " + r.getDocNo() + " 的入库单 " + e.getDocNo() + " 被仓库退回："
                    + Objects.toString(e.getReason(), ""), "/sales/return/" + r.getId());
        }
    }

    private void applyReceipt(SalReturnDO r, Map<Long, BigDecimal> qtyByLine, Long stockInId, String stockInNo, boolean reversed) {
        Map<Long, SalReturnLineDO> lines = lineMapper.selectByParent(r.getId()).stream().collect(Collectors.toMap(SalReturnLineDO::getId, l -> l));
        boolean replace = REPLACE.equals(r.getHandling());
        List<SalesReturnReceivedEvent.Line> evLines = new ArrayList<>();
        for (Map.Entry<Long, BigDecimal> en : qtyByLine.entrySet()) {
            SalReturnLineDO l = lines.get(en.getKey());
            if (l == null) continue;
            BigDecimal q = reversed ? en.getValue().negate() : en.getValue();
            l.setReceivedQty(Decimals.qty(l.getReceivedQty().add(q)).max(BigDecimal.ZERO));
            lineMapper.updateByIdOrFail(l);
            execService.onReturned(l.getOrderLineId(), q, replace, stockInId, stockInNo);
            BigDecimal amount = Decimals.multiplyAmount(en.getValue(), l.getPriceInclTax());
            evLines.add(new SalesReturnReceivedEvent.Line(l.getId(), l.getOrderId(), l.getOrderLineId(), l.getMaterialId(), en.getValue(),
                    l.getPriceInclTax(), l.getTaxRate(), amount));
        }
        support.log(BIZ_TYPE, r.getId(), r.getDocNo(), reversed ? "UNRECEIVE" : "RECEIVE", reversed ? "入库反确认" : "退货入库",
                r.getStatus().name(), r.getStatus().name(), stockInNo);
        eventPublisher.publish(new SalesReturnReceivedEvent(r.getId(), r.getDocNo(), r.getCustomerId(), r.getHandling(), r.getCurrency(),
                r.getExchangeRate(), stockInId, reversed, evLines));
        refreshStatus(getOrThrow(r.getId()));
    }

    /** SalesReturnApi：品质判定回写（覆盖该行的判定数量） */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recordJudgement(Long returnLineId, BigDecimal goodQty, BigDecimal reworkQty, BigDecimal scrapQty) {
        SalReturnLineDO l = lineMapper.selectById(returnLineId);
        if (l == null) throw new BizException(SalesErrorCodes.RETURN_LINE_NOT_EXISTS);
        SalReturnDO r = getOrThrow(l.getReturnId());
        if (r.getStatus() != DocStatus.APPROVED && r.getStatus() != DocStatus.COMPLETED) {
            throw BizException.of(SalesErrorCodes.RETURN_STATUS, r.getStatus().label(), "登记判定");
        }
        BigDecimal g = Decimals.qty(SalSupport.nz(goodQty));
        BigDecimal w = Decimals.qty(SalSupport.nz(reworkQty));
        BigDecimal s = Decimals.qty(SalSupport.nz(scrapQty));
        if (g.signum() < 0 || w.signum() < 0 || s.signum() < 0) throw BizException.of(GlobalErrorCodes.BAD_REQUEST, "判定数量不能为负数");
        if (g.add(w).add(s).compareTo(l.getReceivedQty()) > 0) throw BizException.of(SalesErrorCodes.RETURN_JUDGE_OVER, l.getLineNo(), SalSupport.plain(l.getReceivedQty()));
        l.setGoodQty(g);
        l.setReworkQty(w);
        l.setScrapQty(s);
        lineMapper.updateByIdOrFail(l);
        support.log(BIZ_TYPE, r.getId(), r.getDocNo(), "JUDGE", "判定", r.getStatus().name(), r.getStatus().name(),
                "第 " + l.getLineNo() + " 行 良品 " + SalSupport.plain(g) + "、返工 " + SalSupport.plain(w) + "、不良 " + SalSupport.plain(s));
        refreshStatus(r);
    }

    /** 页面登记判定（没有品质模块时由业务录入） */
    @Transactional(rollbackFor = Exception.class)
    public void judge(Long id, List<JudgeReq> reqs) {
        SalReturnDO r = getOrThrow(id);
        DataScopes.check(r.getOrgId(), r.getDeptId(), r.getOwnerId(), "销售退货单");
        for (JudgeReq j : reqs == null ? List.<JudgeReq>of() : reqs) {
            SalReturnLineDO l = lineMapper.selectById(j.lineId());
            if (l == null || !l.getReturnId().equals(id)) throw new BizException(SalesErrorCodes.RETURN_LINE_NOT_EXISTS);
            recordJudgement(j.lineId(), j.goodQty(), j.reworkQty(), j.scrapQty());
        }
    }

    /** R05：全部行收货完成且判定数量 = 已收货时完成；反确认后恢复 */
    private void refreshStatus(SalReturnDO r) {
        List<SalReturnLineDO> lines = lineMapper.selectByParent(r.getId());
        boolean done = !lines.isEmpty() && lines.stream().allMatch(l -> l.getReceivedQty().compareTo(l.getQty()) >= 0
                && judged(l).compareTo(l.getReceivedQty()) == 0);
        if (r.getStatus() == DocStatus.APPROVED && done) support.fire(SalStateMachines.RETURN, mapper, r, BIZ_TYPE, SalAction.COMPLETE, null);
        else if (r.getStatus() == DocStatus.COMPLETED && !done) support.fire(SalStateMachines.RETURN, mapper, r, BIZ_TYPE, SalAction.REOPEN, null);
    }

    // ==================== 打印 ====================

    public Map<String, Object> printData(Long id) {
        ReturnDetail d = detail(id);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("docNo", d.docNo());
        data.put("docDate", d.docDate());
        data.put("status", d.status());
        data.put("customerName", support.customer(d.customerId()).name());
        data.put("rmaNo", Objects.toString(d.rmaNo(), ""));
        data.put("reasonName", Objects.toString(support.dict().label("sal_return_reason", d.returnReason()), d.returnReason()));
        data.put("handlingName", REFUND.equals(d.handling()) ? "退货退款" : "退货换货");
        data.put("currency", d.currency());
        data.put("totalAmount", d.totalAmount());
        data.put("remark", Objects.toString(d.remark(), ""));
        List<Map<String, Object>> lines = new ArrayList<>();
        for (ReturnLineResp l : d.lines()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("lineNo", l.lineNo());
            m.put("orderNo", l.orderNo());
            m.put("materialCode", l.materialCode());
            m.put("materialName", l.materialName());
            m.put("batchNo", Objects.toString(l.batchNo(), ""));
            m.put("qty", l.qty());
            m.put("priceInclTax", l.priceInclTax());
            m.put("totalAmount", l.totalAmount());
            lines.add(m);
        }
        data.put("lines", lines);
        return data;
    }

    public SalReturnDO getOrThrow(Long id) {
        SalReturnDO r = id == null ? null : mapper.selectById(id);
        if (r == null) throw new BizException(SalesErrorCodes.RETURN_NOT_EXISTS);
        return r;
    }
}
