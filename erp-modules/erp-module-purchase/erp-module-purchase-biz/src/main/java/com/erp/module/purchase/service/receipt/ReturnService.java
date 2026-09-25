package com.erp.module.purchase.service.receipt;

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
import com.erp.module.inventory.api.doc.InventoryDocApi;
import com.erp.module.inventory.api.doc.SourceRef;
import com.erp.module.inventory.api.doc.StockDocEvent;
import com.erp.module.inventory.api.doc.StockOutConfirmedEvent;
import com.erp.module.inventory.api.doc.StockOutRequest;
import com.erp.module.inventory.api.doc.StockOutType;
import com.erp.module.inventory.api.stock.BatchSuggestion;
import com.erp.module.inventory.api.stock.InventoryQueryApi;
import com.erp.module.inventory.api.warehouse.WarehouseApi;
import com.erp.module.inventory.api.warehouse.WarehouseDTO;
import com.erp.module.inventory.api.warehouse.WarehouseType;
import com.erp.module.purchase.api.PurchaseErrorCodes;
import com.erp.module.purchase.api.receipt.PurchaseReturnCompletedEvent;
import com.erp.module.purchase.config.PurchaseModuleConfig;
import com.erp.module.purchase.controller.vo.CommonVOs.DocResult;
import com.erp.module.purchase.controller.vo.CommonVOs.RelatedDoc;
import com.erp.module.purchase.controller.vo.CommonVOs.SaveResult;
import com.erp.module.purchase.controller.vo.ReturnVOs.DefectCandidate;
import com.erp.module.purchase.controller.vo.ReturnVOs.DefectItem;
import com.erp.module.purchase.controller.vo.ReturnVOs.ReturnDetail;
import com.erp.module.purchase.controller.vo.ReturnVOs.ReturnLineResp;
import com.erp.module.purchase.controller.vo.ReturnVOs.ReturnLineSave;
import com.erp.module.purchase.controller.vo.ReturnVOs.ReturnQuery;
import com.erp.module.purchase.controller.vo.ReturnVOs.ReturnRow;
import com.erp.module.purchase.controller.vo.ReturnVOs.ReturnSave;
import com.erp.module.purchase.dal.dataobject.OrderDO;
import com.erp.module.purchase.dal.dataobject.OrderLineDO;
import com.erp.module.purchase.dal.dataobject.OutsourcingDO;
import com.erp.module.purchase.dal.dataobject.ReceiptDO;
import com.erp.module.purchase.dal.dataobject.ReceiptLineDO;
import com.erp.module.purchase.dal.dataobject.ReturnDO;
import com.erp.module.purchase.dal.dataobject.ReturnLineDO;
import com.erp.module.purchase.dal.dataobject.SupplierDO;
import com.erp.module.purchase.dal.mapper.ReceiptLineMapper;
import com.erp.module.purchase.dal.mapper.ReturnLineMapper;
import com.erp.module.purchase.dal.mapper.ReturnMapper;
import com.erp.module.purchase.service.PurAction;
import com.erp.module.purchase.service.PurStateMachines;
import com.erp.module.purchase.service.PurSupport;
import com.erp.module.purchase.service.order.OrderService;
import com.erp.module.purchase.service.outsourcing.OutsourcingService;
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
 * 采购退货（需求 07-08）：草稿 → 待审批 → 已审核（生成采购退货出库单）→ 已完成（出库确认）；已审核且出库未确认可作废。
 * 出库确认后回写到货行已退货；退货退款（REFUND）增加订单行已退货，退货换货（REPLACE）恢复订单行未到货数量。
 */
@Service
public class ReturnService {

    public static final String BIZ_TYPE = PurchaseModuleConfig.RETURN;
    public static final String REFUND = "REFUND";
    public static final String REPLACE = "REPLACE";
    static final Set<String> HANDLINGS = Set.of(REFUND, REPLACE);

    private final ReturnMapper mapper;
    private final ReturnLineMapper lineMapper;
    private final ReceiptLineMapper receiptLineMapper;
    private final ReceiptService receiptService;
    private final OrderService orderService;
    private final OutsourcingService outsourcingService;
    private final SupplierService supplierService;
    private final PurSupport support;
    private final CurrencyApi currencyApi;
    private final InventoryDocApi inventoryDocApi;
    private final InventoryQueryApi inventoryQueryApi;
    private final WarehouseApi warehouseApi;
    private final WorkflowApi workflowApi;
    private final FileApi fileApi;
    private final DomainEventPublisher eventPublisher;

    public ReturnService(ReturnMapper mapper, ReturnLineMapper lineMapper, ReceiptLineMapper receiptLineMapper, ReceiptService receiptService,
                         OrderService orderService, OutsourcingService outsourcingService, SupplierService supplierService, PurSupport support,
                         CurrencyApi currencyApi, InventoryDocApi inventoryDocApi, InventoryQueryApi inventoryQueryApi, WarehouseApi warehouseApi,
                         WorkflowApi workflowApi, FileApi fileApi, DomainEventPublisher eventPublisher) {
        this.mapper = mapper;
        this.lineMapper = lineMapper;
        this.receiptLineMapper = receiptLineMapper;
        this.receiptService = receiptService;
        this.orderService = orderService;
        this.outsourcingService = outsourcingService;
        this.supplierService = supplierService;
        this.support = support;
        this.currencyApi = currencyApi;
        this.inventoryDocApi = inventoryDocApi;
        this.inventoryQueryApi = inventoryQueryApi;
        this.warehouseApi = warehouseApi;
        this.workflowApi = workflowApi;
        this.fileApi = fileApi;
        this.eventPublisher = eventPublisher;
    }

    // ==================== 查询 ====================

    public PageResult<ReturnRow> page(ReturnQuery q) {
        LambdaQueryWrapper<ReturnDO> w = new LambdaQueryWrapper<ReturnDO>().eq(ReturnDO::getDeleted, false)
                .likeRight(StringUtils.hasText(q.getDocNo()), ReturnDO::getDocNo, q.getDocNo() == null ? null : q.getDocNo().trim().toUpperCase())
                .eq(q.getSupplierId() != null, ReturnDO::getSupplierId, q.getSupplierId())
                .eq(StringUtils.hasText(q.getReturnReason()), ReturnDO::getReturnReason, q.getReturnReason())
                .eq(StringUtils.hasText(q.getHandling()), ReturnDO::getHandling, q.getHandling())
                .ge(q.getDateFrom() != null, ReturnDO::getDocDate, q.getDateFrom())
                .le(q.getDateTo() != null, ReturnDO::getDocDate, q.getDateTo());
        if (StringUtils.hasText(q.getStatuses())) {
            w.in(ReturnDO::getStatus, Arrays.stream(q.getStatuses().split(",")).map(String::trim).map(DocStatus::valueOf).toList());
        }
        if (q.getMaterialId() != null) {
            w.inSql(ReturnDO::getId, "SELECT return_id FROM pur_return_line WHERE deleted = 0 AND material_id = " + q.getMaterialId().longValue());
        }
        w.orderByDesc(ReturnDO::getDocDate).orderByDesc(ReturnDO::getId);
        IPage<ReturnDO> page = mapper.selectScopedPage(Page.of(q.getPageNo(), q.getPageSize()), w);
        List<ReturnDO> list = page.getRecords();
        Map<Long, List<ReturnLineDO>> lines = lineMapper.selectByParents(list.stream().map(ReturnDO::getId).toList()).stream()
                .collect(Collectors.groupingBy(ReturnLineDO::getReturnId));
        Map<Long, MaterialDTO> ms = support.materials(lines.values().stream().flatMap(List::stream).map(ReturnLineDO::getMaterialId).toList());
        Map<Long, SupplierDO> ss = supplierService.byIds(list.stream().map(ReturnDO::getSupplierId).toList());
        Map<Long, UserDTO> users = support.users(list.stream().map(ReturnDO::getOwnerId).toList());
        Map<Long, String> whs = warehouseNames(list.stream().map(ReturnDO::getWarehouseId).toList());
        boolean price = PurSupport.canViewPrice();
        return new PageResult<>(list.stream().map(r -> {
            List<ReturnLineDO> ls = lines.getOrDefault(r.getId(), List.of());
            SupplierDO s = ss.get(r.getSupplierId());
            String out = r.getStatus() == DocStatus.COMPLETED ? "DONE" : r.getStatus() == DocStatus.APPROVED ? "PENDING" : "NONE";
            return new ReturnRow(r.getId(), r.getDocNo(), r.getDocDate(), r.getSupplierId(), s == null ? null : s.getShortName(), r.getReturnReason(),
                    r.getHandling(), r.getWarehouseId(), whs.get(r.getWarehouseId()), RequisitionService.summary(ms, ls.stream().map(ReturnLineDO::getMaterialId).toList()),
                    r.getCurrency(), PurSupport.mask(r.getTotalAmount(), price), r.getStatus().name(), out, PurSupport.name(users, r.getOwnerId()));
        }).toList(), page.getTotal());
    }

    private Map<Long, String> warehouseNames(List<Long> ids) {
        Map<Long, String> map = new HashMap<>();
        ids.stream().filter(Objects::nonNull).distinct().forEach(id -> warehouseApi.get(id).ifPresent(w -> map.put(id, w.name())));
        return map;
    }

    public ReturnDetail detail(Long id) {
        ReturnDO r = getOrThrow(id);
        DataScopes.check(r.getOrgId(), r.getDeptId(), r.getOwnerId(), "采购退货单");
        SupplierDO s = supplierService.getOrThrow(r.getSupplierId());
        List<ReturnLineDO> lines = lineMapper.selectByParent(id);
        Map<Long, ReceiptLineDO> rls = receiptService.linesByIds(lines.stream().map(ReturnLineDO::getReceiptLineId).toList());
        Map<Long, ReceiptDO> receipts = receiptService.byIds(rls.values().stream().map(ReceiptLineDO::getReceiptId).toList());
        Map<Long, OrderDO> orders = orderService.byIds(rls.values().stream().filter(l -> l.getOrderLineId() != null).map(ReceiptLineDO::getOrderId).toList());
        Map<Long, MaterialDTO> ms = support.materials(lines.stream().map(ReturnLineDO::getMaterialId).toList());
        Map<Long, BigDecimal> can = receiptService.returnable(rls.keySet());
        boolean price = PurSupport.canViewPrice();
        List<ReturnLineResp> resp = lines.stream().map(l -> {
            ReceiptLineDO rl = rls.get(l.getReceiptLineId());
            ReceiptDO rc = rl == null ? null : receipts.get(rl.getReceiptId());
            OrderDO o = rl == null ? null : orders.get(rl.getOrderId());
            MaterialDTO m = ms.get(l.getMaterialId());
            BigDecimal returnable = can.getOrDefault(l.getReceiptLineId(), BigDecimal.ZERO)
                    .add(r.getStatus() == DocStatus.DRAFT || r.getStatus() == DocStatus.PENDING_APPROVAL || r.getStatus() == DocStatus.APPROVED ? l.getQty() : BigDecimal.ZERO);
            return new ReturnLineResp(l.getId(), l.getLineNo(), l.getReceiptLineId(), rc == null ? null : rc.getId(), rc == null ? null : rc.getDocNo(),
                    l.getOrderLineId(), o == null ? null : o.getDocNo(), l.getMaterialId(), m == null ? null : m.code(), m == null ? null : m.name(),
                    m == null ? null : m.spec(), m == null ? null : m.baseUom(), l.getBatchNo(), l.getQty(), returnable,
                    PurSupport.mask(l.getPriceInclTax(), price), l.getTaxRate(), PurSupport.mask(l.getTotalAmount(), price), l.getOutQty(), l.getOutDate(),
                    l.getStatementQty(), l.getRemark());
        }).toList();
        List<RelatedDoc> related = new ArrayList<>();
        receipts.values().forEach(x -> related.add(new RelatedDoc("UP", "到货单", x.getDocNo(), x.getDocDate(), x.getStatus().name(), x.getStatus().label(),
                "/purchase/receipt/" + x.getId())));
        orders.values().forEach(x -> related.add(new RelatedDoc("UP", "采购订单", x.getDocNo(), x.getDocDate(), x.getStatus().name(), x.getStatus().label(),
                "/purchase/order/" + x.getId())));
        if (r.getStockOutId() != null) {
            related.add(new RelatedDoc("DOWN", "出库单", Objects.toString(r.getStockOutNo(), String.valueOf(r.getStockOutId())), null, null, null,
                    "/inventory/out/" + r.getStockOutId()));
        }
        if (r.getNcrNo() != null) related.add(new RelatedDoc("UP", "NCR", r.getNcrNo(), null, null, null, null));
        return new ReturnDetail(r.getId(), r.getDocNo(), r.getDocDate(), r.getStatus().name(), s.getId(), s.getShortName(), r.getReturnReason(),
                r.getHandling(), r.getWarehouseId(), warehouseNames(List.of(r.getWarehouseId())).get(r.getWarehouseId()), r.getCurrency(),
                r.getExchangeRate(), PurSupport.mask(r.getTotalAmount(), price), r.getStockOutId(), r.getStockOutNo(), r.getNcrNo(), r.getVoidReason(),
                r.getRemark(), r.getOwnerId(), support.userName(r.getOwnerId()), r.getCreatedAt(), r.getVersion(), price, resp, related);
    }

    // ==================== 编辑 ====================

    @Transactional(rollbackFor = Exception.class)
    public SaveResult create(ReturnSave req) {
        ReturnDO r = new ReturnDO();
        r.setDocNo(support.nextNo(BIZ_TYPE));
        r.setDocDate(LocalDate.now());
        r.setStatus(DocStatus.DRAFT);
        r.setTotalAmount(BigDecimal.ZERO);
        support.fillOwner(r, null);
        fillHeader(r, req);
        r.setCurrency(currencyApi.getBaseCurrency());
        r.setExchangeRate(BigDecimal.ONE);
        mapper.insert(r);
        List<String> warnings = saveLines(r, req.lines());
        mapper.updateByIdOrFail(r);
        if (req.fileIds() != null && !req.fileIds().isEmpty()) fileApi.bind(req.fileIds(), BIZ_TYPE, r.getId());
        return new SaveResult(r.getId(), warnings);
    }

    @Transactional(rollbackFor = Exception.class)
    public SaveResult update(Long id, ReturnSave req) {
        ReturnDO r = getOrThrow(id);
        DataScopes.check(r.getOrgId(), r.getDeptId(), r.getOwnerId(), "采购退货单");
        PurSupport.requireDraft(r);
        if (req.version() != null) r.setVersion(req.version());
        fillHeader(r, req);
        List<String> warnings = saveLines(r, req.lines());
        mapper.updateByIdOrFail(r);
        if (req.fileIds() != null && !req.fileIds().isEmpty()) fileApi.bind(req.fileIds(), BIZ_TYPE, r.getId());
        return new SaveResult(r.getId(), warnings);
    }

    private void fillHeader(ReturnDO r, ReturnSave req) {
        supplierService.getOrThrow(req.supplierId());
        if (!req.returnReason().equals(r.getReturnReason())) support.dict().validate("pur_return_reason", req.returnReason(), "退货原因");
        if (req.handling() == null || !HANDLINGS.contains(req.handling())) throw BizException.of(GlobalErrorCodes.BAD_REQUEST, "处理方式");
        WarehouseDTO w = warehouseApi.get(req.warehouseId()).orElseThrow(() -> BizException.of(GlobalErrorCodes.DATA_NOT_EXISTS, "仓库"));
        r.setSupplierId(req.supplierId());
        r.setReturnReason(req.returnReason());
        r.setHandling(req.handling());
        r.setWarehouseId(w.id());
        r.setNcrNo(PurSupport.trim(req.ncrNo()));
        r.setRemark(PurSupport.trim(req.remark()));
    }

    /** R01 可退数量、R02 批次库存（警告）；单价取原订单行，币别取来源订单 */
    private List<String> saveLines(ReturnDO r, List<ReturnLineSave> lines) {
        if (lines == null || lines.isEmpty()) throw new BizException(PurchaseErrorCodes.DOC_NO_LINES);
        lineMapper.deleteByParent(r.getId());
        Map<Long, ReceiptLineDO> rls = receiptService.linesByIds(lines.stream().map(ReturnLineSave::receiptLineId).toList());
        Map<Long, ReceiptDO> receipts = receiptService.byIds(rls.values().stream().map(ReceiptLineDO::getReceiptId).toList());
        Map<Long, BigDecimal> can = receiptService.returnable(rls.keySet());
        Map<Long, BigDecimal> used = new HashMap<>();
        List<String> warnings = new ArrayList<>();
        String currency = null;
        BigDecimal rate = BigDecimal.ONE;
        BigDecimal total = BigDecimal.ZERO;
        int no = 0;
        for (ReturnLineSave l : lines) {
            no++;
            ReceiptLineDO rl = rls.get(l.receiptLineId());
            ReceiptDO rc = rl == null ? null : receipts.get(rl.getReceiptId());
            if (rl == null || rc == null) throw new BizException(PurchaseErrorCodes.RECEIPT_LINE_NOT_EXISTS);
            if (!rc.getSupplierId().equals(r.getSupplierId())) throw BizException.of(PurchaseErrorCodes.RETURN_SUPPLIER_MISMATCH, no);
            if (l.qty().signum() <= 0) throw BizException.of(PurchaseErrorCodes.LINE_QTY_POSITIVE, no);
            BigDecimal avail = can.getOrDefault(rl.getId(), BigDecimal.ZERO).subtract(used.getOrDefault(rl.getId(), BigDecimal.ZERO));
            if (l.qty().compareTo(avail) > 0) throw BizException.of(PurchaseErrorCodes.RETURN_OVER, no, PurSupport.plain(avail.max(BigDecimal.ZERO)));
            used.merge(rl.getId(), l.qty(), BigDecimal::add);
            Source src = source(rc, rl);
            if (currency == null) {
                currency = src.currency;
                rate = src.rate;
            } else if (!currency.equals(src.currency)) {
                throw BizException.of(PurchaseErrorCodes.RETURN_CURRENCY, no);
            }
            ReturnLineDO d = new ReturnLineDO();
            d.setReturnId(r.getId());
            d.setLineNo(no);
            d.setReceiptLineId(rl.getId());
            d.setOrderLineId(rl.getOrderLineId());
            d.setMaterialId(rl.getMaterialId());
            d.setBatchNo(StringUtils.hasText(l.batchNo()) ? l.batchNo().trim() : rl.getBatchNo());
            d.setQty(Decimals.qty(l.qty()));
            d.setPriceInclTax(src.priceInclTax);
            d.setTaxRate(src.taxRate);
            d.setTotalAmount(Decimals.multiplyAmount(d.getQty(), d.getPriceInclTax()));
            d.setOutQty(BigDecimal.ZERO);
            d.setStatementQty(BigDecimal.ZERO);
            d.setRemark(PurSupport.trim(l.remark()));
            lineMapper.insert(d);
            total = total.add(d.getTotalAmount());
            stockWarning(r, d, no, warnings);
        }
        r.setCurrency(currency);
        r.setExchangeRate(rate);
        r.setTotalAmount(total);
        return warnings;
    }

    /** R02：出库仓中该批次库存足够（提示） */
    private void stockWarning(ReturnDO r, ReturnLineDO d, int no, List<String> warnings) {
        List<BatchSuggestion> batches = inventoryQueryApi.suggestBatches(d.getMaterialId(), r.getWarehouseId(), new BigDecimal("999999999"));
        BigDecimal have = batches.stream().filter(b -> Objects.equals(b.batchNo(), d.getBatchNo()) || d.getBatchNo() == null)
                .map(BatchSuggestion::qty).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (have.compareTo(d.getQty()) < 0) {
            String wh = warehouseApi.get(r.getWarehouseId()).map(WarehouseDTO::name).orElse("");
            warnings.add("第 " + no + " 行：仓库「" + wh + "」批次「" + Objects.toString(d.getBatchNo(), "-") + "」库存 " + PurSupport.plain(have)
                    + "，不足退货数量");
        }
    }

    record Source(String currency, BigDecimal rate, BigDecimal priceInclTax, BigDecimal taxRate, OrderLineDO orderLine) {
    }

    /** 来源订单（委外单）的币别、汇率、每基本单位含税单价 */
    Source source(ReceiptDO rc, ReceiptLineDO rl) {
        if (ReceiptService.OUTSOURCE.equals(rc.getReceiptType())) {
            OutsourcingDO o = outsourcingService.getOrThrow(rl.getOrderId());
            return new Source(o.getCurrency(), o.getExchangeRate(), Decimals.price(o.getProcessPrice().multiply(BigDecimal.ONE.add(o.getTaxRate()))),
                    o.getTaxRate(), null);
        }
        OrderDO o = orderService.getOrThrow(rl.getOrderId());
        OrderLineDO ol = orderService.linesByIds(List.of(rl.getOrderLineId())).get(rl.getOrderLineId());
        return new Source(o.getCurrency(), o.getExchangeRate(), ReceiptService.basePriceInclTax(ol), ol.getTaxRate(), ol);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        ReturnDO r = getOrThrow(id);
        DataScopes.check(r.getOrgId(), r.getDeptId(), r.getOwnerId(), "采购退货单");
        PurSupport.requireDraft(r);
        lineMapper.deleteByParent(id);
        mapper.deleteById(id);
        fileApi.deleteByBiz(BIZ_TYPE, id);
    }

    // ==================== 提交 / 审核 / 作废 ====================

    @Transactional(rollbackFor = Exception.class)
    public DocResult submit(Long id) {
        ReturnDO r = getOrThrow(id);
        DataScopes.check(r.getOrgId(), r.getDeptId(), r.getOwnerId(), "采购退货单");
        PurSupport.requireDraft(r);
        List<ReturnLineDO> lines = lineMapper.selectByParent(id);
        if (lines.isEmpty()) throw new BizException(PurchaseErrorCodes.DOC_NO_LINES);
        Map<Long, BigDecimal> can = receiptService.returnable(lines.stream().map(ReturnLineDO::getReceiptLineId).toList());
        Map<Long, BigDecimal> own = new HashMap<>();
        lines.forEach(l -> own.merge(l.getReceiptLineId(), l.getQty(), BigDecimal::add));
        for (ReturnLineDO l : lines) {
            BigDecimal avail = can.getOrDefault(l.getReceiptLineId(), BigDecimal.ZERO).add(own.get(l.getReceiptLineId()));
            if (own.get(l.getReceiptLineId()).compareTo(avail) > 0) throw BizException.of(PurchaseErrorCodes.RETURN_OVER, l.getLineNo(), PurSupport.plain(avail));
        }
        List<String> warnings = new ArrayList<>();
        if (REPLACE.equals(r.getHandling())) {
            Map<Long, OrderLineDO> ols = orderService.linesByIds(lines.stream().map(ReturnLineDO::getOrderLineId).toList());
            if (ols.values().stream().anyMatch(ol -> OrderService.CLOSED.equals(ol.getLineStatus()))) warnings.add("订单已关闭，建议改为退货退款");
        }
        support.fire(PurStateMachines.RETURN, mapper, r, BIZ_TYPE, PurAction.SUBMIT, null);
        Map<String, Object> vars = new HashMap<>();
        vars.put("amountBase", currencyApi.toBase(r.getTotalAmount(), r.getExchangeRate()));
        vars.put("returnReason", r.getReturnReason());
        StartResult res = workflowApi.start(BIZ_TYPE, id, r.getDocNo(), "采购退货 " + r.getDocNo(), vars, Map.of(), support.currentUser());
        if (!res.isStarted()) approve(r);
        return new DocResult(getOrThrow(id).getStatus().name(), warnings);
    }

    @EventListener
    public void onApproval(ApprovalCompletedEvent e) {
        if (!BIZ_TYPE.equals(e.getBizType())) return;
        ReturnDO r = getOrThrow(e.getBizId());
        if (r.getStatus() != DocStatus.PENDING_APPROVAL) return;
        switch (e.getResult()) {
            case APPROVED -> approve(r);
            case WITHDRAWN -> support.fire(PurStateMachines.RETURN, mapper, r, BIZ_TYPE, PurAction.WITHDRAW, null);
            default -> support.fire(PurStateMachines.RETURN, mapper, r, BIZ_TYPE, PurAction.REJECT, e.getComment());
        }
    }

    /** R03：审核生成采购退货出库单（带批次）；仓库确认后回写 */
    private void approve(ReturnDO r) {
        support.fire(PurStateMachines.RETURN, mapper, r, BIZ_TYPE, PurAction.APPROVE, null);
        List<ReturnLineDO> lines = lineMapper.selectByParent(r.getId());
        Map<Long, MaterialDTO> ms = support.materials(lines.stream().map(ReturnLineDO::getMaterialId).toList());
        List<Long> ids = inventoryDocApi.createStockOut(new StockOutRequest(StockOutType.PURCHASE_RETURN, new SourceRef(BIZ_TYPE, r.getId(), r.getDocNo()),
                r.getWarehouseId(), LocalDate.now(), null, null, r.getSupplierId(), null, lines.stream().map(l -> new StockOutRequest.Line(l.getId(),
                l.getMaterialId(), ms.get(l.getMaterialId()).baseUom(), l.getQty(), l.getBatchNo(), null)).toList()));
        ReturnDO fresh = getOrThrow(r.getId());
        if (fresh.getStockOutId() == null && !ids.isEmpty()) {
            fresh.setStockOutId(ids.get(0));
            mapper.updateByIdOrFail(fresh);
        }
    }

    /** 作废：已审核且出库单未确认（原因必填）；已对账的不能作废（R06） */
    @Transactional(rollbackFor = Exception.class)
    public void voidDoc(Long id, String reason) {
        ReturnDO r = getOrThrow(id);
        DataScopes.check(r.getOrgId(), r.getDeptId(), r.getOwnerId(), "采购退货单");
        String why = PurSupport.requireReason(reason, "作废");
        List<ReturnLineDO> lines = lineMapper.selectByParent(id);
        if (lines.stream().anyMatch(l -> l.getStatementQty().signum() > 0)) throw new BizException(PurchaseErrorCodes.RETURN_STATEMENT);
        if (r.getStatus() == DocStatus.COMPLETED || lines.stream().anyMatch(l -> l.getOutQty().signum() > 0)) {
            throw BizException.of(PurchaseErrorCodes.RETURN_OUT_CONFIRMED, Objects.toString(r.getStockOutNo(), ""));
        }
        if (r.getStatus() == DocStatus.APPROVED) inventoryDocApi.cancelBySource(BIZ_TYPE, id);
        r.setVoidReason(why);
        support.fire(PurStateMachines.RETURN, mapper, r, BIZ_TYPE, PurAction.VOID, why);
    }

    // ==================== 仓库回写（R04、R05） ====================

    @Transactional(rollbackFor = Exception.class)
    public void onStockOutConfirmed(StockOutConfirmedEvent e) {
        ReturnDO r = getOrThrow(e.getSource().sourceId());
        Map<Long, BigDecimal> qty = new HashMap<>();
        e.getLines().forEach(l -> {
            if (l.sourceLineId() != null) qty.merge(l.sourceLineId(), l.baseQty(), BigDecimal::add);
        });
        List<ReturnLineDO> lines = lineMapper.selectByParent(r.getId());
        for (ReturnLineDO l : lines) {
            l.setOutQty(Decimals.qty(qty.getOrDefault(l.getId(), BigDecimal.ZERO)));
            l.setOutDate(LocalDate.now());
            lineMapper.updateByIdOrFail(l);
        }
        r.setStockOutId(e.getStockOutId());
        r.setStockOutNo(e.getStockOutNo());
        mapper.updateByIdOrFail(r);
        support.fire(PurStateMachines.RETURN, mapper, r, BIZ_TYPE, PurAction.COMPLETE, null);
        afterOut(r, lines, true);
    }

    /** 出库反确认前：已对账的退货阻止 */
    public void onStockOutReversing(StockDocEvent e) {
        ReturnDO r = getOrThrow(e.getSource().sourceId());
        if (lineMapper.selectByParent(r.getId()).stream().anyMatch(l -> l.getStatementQty().signum() > 0)) {
            throw BizException.of(PurchaseErrorCodes.RETURN_REVERSE_BLOCKED, r.getDocNo());
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void onStockOutReversed(StockDocEvent e) {
        ReturnDO r = getOrThrow(e.getSource().sourceId());
        List<ReturnLineDO> lines = lineMapper.selectByParent(r.getId());
        for (ReturnLineDO l : lines) {
            l.setOutQty(BigDecimal.ZERO);
            l.setOutDate(null);
            lineMapper.updateByIdOrFail(l);
        }
        if (r.getStatus() == DocStatus.COMPLETED) support.fire(PurStateMachines.RETURN, mapper, r, BIZ_TYPE, PurAction.REOPEN, e.getReason());
        afterOut(r, lines, false);
    }

    /** 出库单被仓库退回：通知经办人（可作废退货单后重新处理） */
    public void onStockOutRejected(StockDocEvent e) {
        ReturnDO r = getOrThrow(e.getSource().sourceId());
        support.message(List.of(r.getOwnerId()), "退货出库单被退回", "退货单 " + r.getDocNo() + " 的出库单 " + e.getDocNo() + " 被仓库退回："
                + Objects.toString(e.getReason(), ""), "/purchase/return/" + r.getId());
        support.log(BIZ_TYPE, r.getId(), r.getDocNo(), "STOCK_OUT_REJECTED", "出库被退回", r.getStatus().name(), r.getStatus().name(), e.getReason());
    }

    private void afterOut(ReturnDO r, List<ReturnLineDO> lines, boolean completed) {
        receiptService.refreshReturned(lines.stream().map(ReturnLineDO::getReceiptLineId).toList());
        orderService.refreshLines(lines.stream().map(ReturnLineDO::getOrderLineId).filter(Objects::nonNull).toList());
        lines.stream().map(ReturnLineDO::getReceiptLineId).map(id -> receiptService.linesByIds(List.of(id)).get(id)).filter(Objects::nonNull)
                .map(ReceiptLineDO::getReceiptId).distinct().forEach(receiptService::refreshCompletion);
        eventPublisher.publish(new PurchaseReturnCompletedEvent(r.getId(), r.getDocNo(), r.getSupplierId(), r.getHandling(), completed,
                lines.stream().map(l -> new PurchaseReturnCompletedEvent.Line(l.getId(), l.getReceiptLineId(), l.getOrderLineId(), l.getMaterialId(),
                        l.getBatchNo(), completed ? l.getOutQty() : l.getQty())).toList()));
    }

    // ==================== 从不良品生成（3.3） ====================

    /** 不良品仓中有库存、且能追溯到采购到货（判定不合格）的批次 */
    public List<DefectCandidate> defectCandidates(Long supplierId) {
        List<WarehouseDTO> ngs = warehouseApi.listByType(WarehouseType.NG);
        if (ngs.isEmpty()) return List.of();
        List<ReceiptLineDO> lines = receiptLineMapper.selectList(new LambdaQueryWrapper<ReceiptLineDO>().gt(ReceiptLineDO::getRejectedQty, 0)
                .orderByDesc(ReceiptLineDO::getId).last("LIMIT 500"));
        if (lines.isEmpty()) return List.of();
        Map<Long, ReceiptDO> receipts = receiptService.byIds(lines.stream().map(ReceiptLineDO::getReceiptId).toList());
        Map<Long, BigDecimal> can = receiptService.returnable(lines.stream().map(ReceiptLineDO::getId).toList());
        Map<Long, SupplierDO> ss = supplierService.byIds(receipts.values().stream().map(ReceiptDO::getSupplierId).toList());
        Map<Long, MaterialDTO> ms = support.materials(lines.stream().map(ReceiptLineDO::getMaterialId).toList());
        List<DefectCandidate> list = new ArrayList<>();
        for (ReceiptLineDO l : lines) {
            ReceiptDO rc = receipts.get(l.getReceiptId());
            if (rc == null || (supplierId != null && !supplierId.equals(rc.getSupplierId()))) continue;
            BigDecimal rejectedLeft = l.getRejectedQty().min(can.getOrDefault(l.getId(), BigDecimal.ZERO));
            if (rejectedLeft.signum() <= 0) continue;
            for (WarehouseDTO w : ngs) {
                BigDecimal ng = inventoryQueryApi.suggestBatches(l.getMaterialId(), w.id(), new BigDecimal("999999999")).stream()
                        .filter(b -> Objects.equals(b.batchNo(), l.getBatchNo())).map(BatchSuggestion::qty).reduce(BigDecimal.ZERO, BigDecimal::add);
                if (ng.signum() <= 0) continue;
                MaterialDTO m = ms.get(l.getMaterialId());
                SupplierDO s = ss.get(rc.getSupplierId());
                String orderNo = l.getOrderLineId() == null ? outsourcingService.getOrThrow(l.getOrderId()).getDocNo() : orderService.getOrThrow(l.getOrderId()).getDocNo();
                list.add(new DefectCandidate(l.getId(), rc.getSupplierId(), s == null ? null : s.getShortName(), rc.getId(), rc.getDocNo(), orderNo,
                        l.getMaterialId(), m == null ? null : m.code(), m == null ? null : m.name(), m == null ? null : m.baseUom(), l.getBatchNo(), w.id(),
                        w.name(), ng, l.getRejectedQty(), rejectedLeft.min(ng), l.getInspectStatus(), l.getInspectionNo()));
            }
        }
        return list;
    }

    /** 勾选后按供应商（+ 出库仓）生成草稿退货单：原因 IQC_REJECT，处理方式默认 REPLACE */
    @Transactional(rollbackFor = Exception.class)
    public List<Long> fromDefects(List<DefectItem> items) {
        if (items == null || items.isEmpty()) throw new BizException(PurchaseErrorCodes.DOC_NO_LINES);
        Map<Long, ReceiptLineDO> rls = receiptService.linesByIds(items.stream().map(DefectItem::receiptLineId).toList());
        Map<Long, ReceiptDO> receipts = receiptService.byIds(rls.values().stream().map(ReceiptLineDO::getReceiptId).toList());
        Map<Long, BigDecimal> can = receiptService.returnable(rls.keySet());
        Map<String, List<ReturnLineSave>> groups = new LinkedHashMap<>();
        for (DefectItem i : items) {
            ReceiptLineDO l = rls.get(i.receiptLineId());
            if (l == null) throw new BizException(PurchaseErrorCodes.RECEIPT_LINE_NOT_EXISTS);
            ReceiptDO rc = receipts.get(l.getReceiptId());
            BigDecimal qty = i.qty() != null ? i.qty() : l.getRejectedQty().min(can.getOrDefault(l.getId(), BigDecimal.ZERO));
            groups.computeIfAbsent(rc.getSupplierId() + "|" + i.warehouseId(), k -> new ArrayList<>())
                    .add(new ReturnLineSave(l.getId(), l.getBatchNo(), qty, null));
        }
        List<Long> ids = new ArrayList<>();
        for (Map.Entry<String, List<ReturnLineSave>> e : groups.entrySet()) {
            String[] k = e.getKey().split("\\|");
            ids.add(create(new ReturnSave(Long.valueOf(k[0]), "IQC_REJECT", REPLACE, Long.valueOf(k[1]), null, null, e.getValue(), null, null)).id());
        }
        return ids;
    }

    // ==================== 打印 ====================

    public Map<String, Object> printData(Long id) {
        ReturnDetail d = detail(id);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("docNo", d.docNo());
        data.put("docDate", d.docDate());
        data.put("status", d.status());
        data.put("supplierName", d.supplierName());
        data.put("reasonName", support.dict().label("pur_return_reason", d.returnReason()));
        data.put("handlingName", REFUND.equals(d.handling()) ? "退货退款" : "退货换货");
        data.put("warehouseName", Objects.toString(d.warehouseName(), ""));
        data.put("currency", d.currency());
        data.put("totalAmount", d.totalAmount());
        data.put("remark", Objects.toString(d.remark(), ""));
        data.put("lines", d.lines().stream().map(l -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("lineNo", l.lineNo());
            m.put("receiptNo", Objects.toString(l.receiptNo(), ""));
            m.put("materialCode", l.materialCode());
            m.put("materialName", l.materialName());
            m.put("batchNo", Objects.toString(l.batchNo(), ""));
            m.put("uom", l.baseUom());
            m.put("qty", l.qty());
            m.put("priceInclTax", l.priceInclTax());
            m.put("totalAmount", l.totalAmount());
            return m;
        }).toList());
        return data;
    }

    public ReturnDO getOrThrow(Long id) {
        ReturnDO r = id == null ? null : mapper.selectById(id);
        if (r == null) throw new BizException(PurchaseErrorCodes.RETURN_NOT_EXISTS);
        return r;
    }
}
