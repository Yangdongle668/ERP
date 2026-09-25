package com.erp.module.purchase.service.receipt;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.common.enums.DocStatus;
import com.erp.common.exception.BizException;
import com.erp.common.exception.GlobalErrorCodes;
import com.erp.common.result.PageResult;
import com.erp.common.util.Decimals;
import com.erp.framework.event.DomainEventPublisher;
import com.erp.module.engineering.api.material.MaterialDTO;
import com.erp.module.engineering.api.material.MaterialPurchaseAttr;
import com.erp.module.engineering.api.material.MaterialStockAttr;
import com.erp.module.inventory.api.doc.InventoryDocApi;
import com.erp.module.inventory.api.doc.SourceRef;
import com.erp.module.inventory.api.doc.StockDocEvent;
import com.erp.module.inventory.api.doc.StockInConfirmedEvent;
import com.erp.module.inventory.api.doc.StockInRequest;
import com.erp.module.inventory.api.doc.StockInType;
import com.erp.module.inventory.api.warehouse.WarehouseApi;
import com.erp.module.inventory.api.warehouse.WarehouseDTO;
import com.erp.module.inventory.api.warehouse.WarehouseType;
import com.erp.module.purchase.api.PurchaseErrorCodes;
import com.erp.module.purchase.api.receipt.PurchaseReceiptApprovedEvent;
import com.erp.module.purchase.api.supplier.SupplierStatus;
import com.erp.module.purchase.config.PurchaseModuleConfig;
import com.erp.module.purchase.controller.vo.CommonVOs.DocResult;
import com.erp.module.purchase.controller.vo.CommonVOs.RelatedDoc;
import com.erp.module.purchase.controller.vo.CommonVOs.SaveResult;
import com.erp.module.purchase.controller.vo.ReceiptVOs.ReceiptDetail;
import com.erp.module.purchase.controller.vo.ReceiptVOs.ReceiptLineResp;
import com.erp.module.purchase.controller.vo.ReceiptVOs.ReceiptLineSave;
import com.erp.module.purchase.controller.vo.ReceiptVOs.ReceiptQuery;
import com.erp.module.purchase.controller.vo.ReceiptVOs.ReceiptRow;
import com.erp.module.purchase.controller.vo.ReceiptVOs.ReceiptSave;
import com.erp.module.purchase.controller.vo.ReceiptVOs.ReturnableLine;
import com.erp.module.purchase.controller.vo.ReceiptVOs.ReturnableQuery;
import com.erp.module.purchase.dal.dataobject.IdQtyRow;
import com.erp.module.purchase.dal.dataobject.OrderDO;
import com.erp.module.purchase.dal.dataobject.OrderLineDO;
import com.erp.module.purchase.dal.dataobject.OutsourcingDO;
import com.erp.module.purchase.dal.dataobject.ReceiptDO;
import com.erp.module.purchase.dal.dataobject.ReceiptLineDO;
import com.erp.module.purchase.dal.dataobject.ReturnLineDO;
import com.erp.module.purchase.dal.dataobject.SupplierDO;
import com.erp.module.purchase.dal.mapper.ReceiptLineMapper;
import com.erp.module.purchase.dal.mapper.ReceiptMapper;
import com.erp.module.purchase.dal.mapper.ReturnLineMapper;
import com.erp.module.purchase.dal.mapper.ReturnMapper;
import com.erp.module.purchase.service.PurAction;
import com.erp.module.purchase.service.PurStateMachines;
import com.erp.module.purchase.service.PurSupport;
import com.erp.module.purchase.service.order.OrderService;
import com.erp.module.purchase.service.outsourcing.OutsourcingService;
import com.erp.module.purchase.service.requisition.RequisitionService;
import com.erp.module.purchase.service.supplier.SupplierService;
import com.erp.module.system.api.file.FileApi;
import com.erp.module.system.api.user.UserDTO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
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
 * 到货（需求 07-06）：草稿 → 已审核（生成仓库采购入库单，按入库仓拆单）→ 已完成（所有行已入库且检验处理完毕）。
 * 审核即回写采购订单（委外单）已到货数量；入库确认、IQC 判定、退货回写到货行并同步汇总到订单行。
 */
@Service
public class ReceiptService {

    public static final String BIZ_TYPE = PurchaseModuleConfig.RECEIPT;
    public static final String PURCHASE = "PURCHASE";
    public static final String OUTSOURCE = "OUTSOURCE";
    public static final String SAMPLE = "SAMPLE";
    static final Set<String> TYPES = Set.of(PURCHASE, OUTSOURCE, SAMPLE);
    static final Map<String, String> TYPE_NAMES = Map.of(PURCHASE, "采购到货", OUTSOURCE, "委外收货", SAMPLE, "样品到货");
    public static final String PENDING = "PENDING";
    public static final String NONE = "NONE";

    private final ReceiptMapper mapper;
    private final ReceiptLineMapper lineMapper;
    private final ReturnLineMapper returnLineMapper;
    private final ReturnMapper returnMapper;
    private final OrderService orderService;
    private final OutsourcingService outsourcingService;
    private final SupplierService supplierService;
    private final PurSupport support;
    private final InventoryDocApi inventoryDocApi;
    private final WarehouseApi warehouseApi;
    private final FileApi fileApi;
    private final DomainEventPublisher eventPublisher;

    public ReceiptService(ReceiptMapper mapper, ReceiptLineMapper lineMapper, ReturnLineMapper returnLineMapper, ReturnMapper returnMapper,
                          OrderService orderService, OutsourcingService outsourcingService,
                          SupplierService supplierService, PurSupport support, InventoryDocApi inventoryDocApi, WarehouseApi warehouseApi,
                          FileApi fileApi, DomainEventPublisher eventPublisher) {
        this.mapper = mapper;
        this.lineMapper = lineMapper;
        this.returnLineMapper = returnLineMapper;
        this.returnMapper = returnMapper;
        this.orderService = orderService;
        this.outsourcingService = outsourcingService;
        this.supplierService = supplierService;
        this.support = support;
        this.inventoryDocApi = inventoryDocApi;
        this.warehouseApi = warehouseApi;
        this.fileApi = fileApi;
        this.eventPublisher = eventPublisher;
    }

    // ==================== 查询 ====================

    public PageResult<ReceiptRow> page(ReceiptQuery q) {
        LambdaQueryWrapper<ReceiptDO> w = query(q);
        PageResult<ReceiptDO> page = mapper.selectPage(q, w);
        return new PageResult<>(rows(page.list()), page.total());
    }

    private LambdaQueryWrapper<ReceiptDO> query(ReceiptQuery q) {
        LambdaQueryWrapper<ReceiptDO> w = new LambdaQueryWrapper<ReceiptDO>()
                .likeRight(StringUtils.hasText(q.getDocNo()), ReceiptDO::getDocNo, q.getDocNo() == null ? null : q.getDocNo().trim().toUpperCase())
                .eq(q.getSupplierId() != null, ReceiptDO::getSupplierId, q.getSupplierId())
                .like(StringUtils.hasText(q.getDeliveryNoteNo()), ReceiptDO::getDeliveryNoteNo, q.getDeliveryNoteNo())
                .eq(StringUtils.hasText(q.getReceiptType()), ReceiptDO::getReceiptType, q.getReceiptType())
                .ge(q.getDateFrom() != null, ReceiptDO::getArrivalAt, q.getDateFrom() == null ? null : q.getDateFrom().atStartOfDay())
                .lt(q.getDateTo() != null, ReceiptDO::getArrivalAt, q.getDateTo() == null ? null : q.getDateTo().plusDays(1).atStartOfDay());
        if (StringUtils.hasText(q.getStatuses())) {
            w.in(ReceiptDO::getStatus, Arrays.stream(q.getStatuses().split(",")).map(String::trim).map(DocStatus::valueOf).toList());
        }
        StringBuilder cond = new StringBuilder();
        if (q.getMaterialId() != null) cond.append(" AND material_id = ").append(q.getMaterialId().longValue());
        if (StringUtils.hasText(q.getInspectStatus()) && q.getInspectStatus().matches("[A-Z_]+")) {
            cond.append(" AND inspect_status = '").append(q.getInspectStatus()).append("'");
        }
        if (StringUtils.hasText(q.getOrderNo()) && q.getOrderNo().trim().matches("[A-Za-z0-9-]+")) {
            cond.append(" AND order_id IN (SELECT id FROM pur_order WHERE doc_no LIKE '").append(q.getOrderNo().trim().toUpperCase()).append("%'")
                    .append(" UNION SELECT id FROM pur_outsourcing WHERE doc_no LIKE '").append(q.getOrderNo().trim().toUpperCase()).append("%')");
        }
        if (!cond.isEmpty()) w.inSql(ReceiptDO::getId, "SELECT receipt_id FROM pur_receipt_line WHERE deleted = 0" + cond);
        return w.orderByDesc(ReceiptDO::getArrivalAt).orderByDesc(ReceiptDO::getId);
    }

    private List<ReceiptRow> rows(List<ReceiptDO> list) {
        if (list.isEmpty()) return List.of();
        Map<Long, List<ReceiptLineDO>> lines = lineMapper.selectByParents(list.stream().map(ReceiptDO::getId).toList()).stream()
                .collect(Collectors.groupingBy(ReceiptLineDO::getReceiptId));
        Map<Long, MaterialDTO> ms = support.materials(lines.values().stream().flatMap(List::stream).map(ReceiptLineDO::getMaterialId).toList());
        Map<Long, SupplierDO> ss = supplierService.byIds(list.stream().map(ReceiptDO::getSupplierId).toList());
        Map<Long, UserDTO> users = support.users(list.stream().map(ReceiptDO::getReceiverId).toList());
        return list.stream().map(r -> {
            List<ReceiptLineDO> ls = lines.getOrDefault(r.getId(), List.of());
            Map<String, Long> insp = ls.stream().collect(Collectors.groupingBy(ReceiptLineDO::getInspectStatus, LinkedHashMap::new, Collectors.counting()));
            String inspect = insp.entrySet().stream().map(e -> INSPECT_NAMES.getOrDefault(e.getKey(), e.getKey()) + " " + e.getValue())
                    .collect(Collectors.joining(" / "));
            long stocked = ls.stream().filter(l -> l.getStockedQty().compareTo(l.getBaseQty()) >= 0).count();
            String stock = stocked == ls.size() && !ls.isEmpty() ? "ALL" : ls.stream().anyMatch(l -> l.getStockedQty().signum() > 0) ? "PARTIAL" : "NONE";
            SupplierDO s = ss.get(r.getSupplierId());
            return new ReceiptRow(r.getId(), r.getDocNo(), r.getReceiptType(), r.getSupplierId(), s == null ? null : s.getShortName(), r.getDeliveryNoteNo(),
                    r.getArrivalAt(), RequisitionService.summary(ms, ls.stream().map(ReceiptLineDO::getMaterialId).toList()), ls.size(),
                    r.getStatus() == DocStatus.DRAFT ? "" : inspect, stock, PurSupport.name(users, r.getReceiverId()), r.getStatus().name());
        }).toList();
    }

    static final Map<String, String> INSPECT_NAMES = Map.of(NONE, "免检", PENDING, "待检", "QUALIFIED", "合格", "CONCESSION", "特采",
            "REJECTED", "不合格", "PARTIAL", "部分合格");

    public ReceiptDetail detail(Long id) {
        ReceiptDO r = getOrThrow(id);
        SupplierDO s = supplierService.getOrThrow(r.getSupplierId());
        List<ReceiptLineDO> lines = lineMapper.selectByParent(id);
        Map<Long, MaterialDTO> ms = support.materials(lines.stream().map(ReceiptLineDO::getMaterialId).toList());
        boolean outsource = OUTSOURCE.equals(r.getReceiptType());
        Map<Long, OrderDO> orders = outsource ? Collections.emptyMap() : orderService.byIds(lines.stream().map(ReceiptLineDO::getOrderId).toList());
        Map<Long, OutsourcingDO> oss = outsource ? outsourcingService.byIds(lines.stream().map(ReceiptLineDO::getOrderId).toList()) : Collections.emptyMap();
        Map<Long, OrderLineDO> ols = orderService.linesByIds(lines.stream().map(ReceiptLineDO::getOrderLineId).toList());
        Map<Long, String> whNames = new HashMap<>();
        lines.stream().map(ReceiptLineDO::getTargetWarehouseId).filter(Objects::nonNull).distinct()
                .forEach(wid -> warehouseApi.get(wid).ifPresent(w -> whNames.put(wid, w.name())));
        List<ReceiptLineResp> resp = lines.stream().map(l -> {
            MaterialDTO m = ms.get(l.getMaterialId());
            OrderLineDO ol = ols.get(l.getOrderLineId());
            String orderNo = outsource ? (oss.containsKey(l.getOrderId()) ? oss.get(l.getOrderId()).getDocNo() : null)
                    : (orders.containsKey(l.getOrderId()) ? orders.get(l.getOrderId()).getDocNo() : null);
            BigDecimal open = ol != null ? OrderService.toUom(ol, ol.getBaseQty().subtract(ol.getReceivedQty()).max(BigDecimal.ZERO))
                    : oss.containsKey(l.getOrderId()) ? oss.get(l.getOrderId()).getQty().subtract(oss.get(l.getOrderId()).getReceivedQty()).max(BigDecimal.ZERO) : null;
            return new ReceiptLineResp(l.getId(), l.getLineNo(), l.getOrderId(), orderNo, l.getOrderLineId(), ol == null ? null : ol.getLineNo(),
                    l.getMaterialId(), m == null ? null : m.code(), m == null ? null : m.name(), m == null ? null : m.spec(), m == null ? null : m.baseUom(),
                    l.getUom(), l.getQty(), l.getBaseQty(), open, l.getSupplierBatchNo(), l.getProductionDate(), Boolean.TRUE.equals(l.getInspectRequired()),
                    l.getTargetWarehouseId(), whNames.get(l.getTargetWarehouseId()), l.getStockInId(), l.getStockInNo(), l.getStockedQty(), l.getStockedDate(),
                    l.getBatchNo(), l.getInspectStatus(), l.getQualifiedQty(), l.getConcessionQty(), l.getRejectedQty(), l.getReturnedQty(),
                    l.getStatementQty(), l.getInspectionNo(), l.getRejectReason(), l.getRemark());
        }).toList();
        List<RelatedDoc> related = new ArrayList<>();
        orders.values().stream().sorted(Comparator.comparing(OrderDO::getId)).forEach(o -> related.add(new RelatedDoc("UP", "采购订单", o.getDocNo(),
                o.getDocDate(), o.getStatus().name(), o.getStatus().label(), "/purchase/order/" + o.getId())));
        oss.values().forEach(o -> related.add(new RelatedDoc("UP", "委外单", o.getDocNo(), o.getDocDate(), o.getStatus().name(), o.getStatus().label(),
                "/purchase/outsourcing/" + o.getId())));
        lines.stream().filter(l -> l.getStockInId() != null).collect(Collectors.toMap(ReceiptLineDO::getStockInId, l -> l, (a, b) -> a, LinkedHashMap::new))
                .values().forEach(l -> related.add(new RelatedDoc("DOWN", "入库单", l.getStockInNo() == null ? String.valueOf(l.getStockInId()) : l.getStockInNo(),
                        l.getStockedDate(), null, null, "/inventory/in/" + l.getStockInId())));
        lines.stream().map(ReceiptLineDO::getInspectionNo).filter(Objects::nonNull).distinct()
                .forEach(no -> related.add(new RelatedDoc("DOWN", "检验单", no, null, null, null, null)));
        if (!lines.isEmpty()) {
            Set<Long> returnIds = returnLineMapper.selectList(new LambdaQueryWrapper<ReturnLineDO>().in(ReturnLineDO::getReceiptLineId,
                    lines.stream().map(ReceiptLineDO::getId).toList())).stream().map(ReturnLineDO::getReturnId).collect(Collectors.toSet());
            if (!returnIds.isEmpty()) {
                returnMapper.selectBatchIds(returnIds).forEach(x -> related.add(new RelatedDoc("DOWN", "采购退货", x.getDocNo(), x.getDocDate(),
                        x.getStatus().name(), x.getStatus().label(), "/purchase/return/" + x.getId())));
            }
        }
        Map<Long, UserDTO> users = support.users(List.of(nz(r.getReceiverId()), nz(r.getCreatedBy())));
        return new ReceiptDetail(r.getId(), r.getDocNo(), r.getDocDate(), r.getStatus().name(), r.getReceiptType(), s.getId(), s.getShortName(),
                s.getSupplierStatus().name(), r.getDeliveryNoteNo(), r.getArrivalAt(), r.getReceiverId(), PurSupport.name(users, r.getReceiverId()),
                r.getRemark(), r.getOwnerId(), PurSupport.name(users, r.getCreatedBy()), r.getCreatedAt(), r.getVersion(), resp, related);
    }

    // ==================== 编辑 ====================

    @Transactional(rollbackFor = Exception.class)
    public SaveResult create(ReceiptSave req) {
        ReceiptDO r = new ReceiptDO();
        r.setDocNo(support.nextNo(BIZ_TYPE));
        r.setStatus(DocStatus.DRAFT);
        support.fillOwner(r, null);
        fillHeader(r, req);
        mapper.insert(r);
        List<String> warnings = saveLines(r, req.lines());
        if (req.fileIds() != null && !req.fileIds().isEmpty()) fileApi.bind(req.fileIds(), BIZ_TYPE, r.getId());
        return new SaveResult(r.getId(), warnings);
    }

    @Transactional(rollbackFor = Exception.class)
    public SaveResult update(Long id, ReceiptSave req) {
        ReceiptDO r = getOrThrow(id);
        PurSupport.requireDraft(r);
        if (req.version() != null) r.setVersion(req.version());
        fillHeader(r, req);
        mapper.updateByIdOrFail(r);
        List<String> warnings = saveLines(r, req.lines());
        if (req.fileIds() != null && !req.fileIds().isEmpty()) fileApi.bind(req.fileIds(), BIZ_TYPE, r.getId());
        return new SaveResult(r.getId(), warnings);
    }

    private void fillHeader(ReceiptDO r, ReceiptSave req) {
        String type = StringUtils.hasText(req.receiptType()) ? req.receiptType() : PURCHASE;
        if (type == null || !TYPES.contains(type)) throw BizException.of(GlobalErrorCodes.BAD_REQUEST, "到货类型");
        SupplierDO s = supplierService.getOrThrow(req.supplierId());
        if (s.getSupplierStatus() == SupplierStatus.ELIMINATED) throw new BizException(PurchaseErrorCodes.RECEIPT_SUPPLIER_ELIMINATED);
        r.setReceiptType(type);
        r.setSupplierId(s.getId());
        r.setDeliveryNoteNo(PurSupport.trim(req.deliveryNoteNo()));
        r.setArrivalAt(req.arrivalAt() != null ? req.arrivalAt() : LocalDateTime.now().withNano(0));
        r.setDocDate(r.getArrivalAt().toLocalDate());
        r.setReceiverId(req.receiverId() != null ? req.receiverId() : support.currentUser());
        r.setRemark(PurSupport.trim(req.remark()));
    }

    private List<String> saveLines(ReceiptDO r, List<ReceiptLineSave> lines) {
        if (lines == null || lines.isEmpty()) throw new BizException(PurchaseErrorCodes.DOC_NO_LINES);
        lineMapper.deleteByParent(r.getId());
        List<String> warnings = new ArrayList<>();
        int no = 0;
        for (ReceiptLineSave l : lines) {
            no++;
            ReceiptLineDO d = new ReceiptLineDO();
            d.setReceiptId(r.getId());
            d.setLineNo(no);
            if (OUTSOURCE.equals(r.getReceiptType())) {
                if (l.orderId() == null) throw BizException.of(PurchaseErrorCodes.LINE_FIELD_REQUIRED, no, "委外单");
                OutsourcingDO o = outsourcingService.getOrThrow(l.orderId());
                d.setOrderId(o.getId());
                d.setMaterialId(o.getMaterialId());
                d.setUom(support.material(o.getMaterialId()).baseUom());
            } else {
                if (l.orderLineId() == null) throw BizException.of(PurchaseErrorCodes.LINE_FIELD_REQUIRED, no, "采购订单行");
                OrderLineDO ol = orderService.linesByIds(List.of(l.orderLineId())).get(l.orderLineId());
                if (ol == null) throw new BizException(PurchaseErrorCodes.ORDER_LINE_NOT_EXISTS);
                d.setOrderId(ol.getOrderId());
                d.setOrderLineId(ol.getId());
                d.setMaterialId(ol.getMaterialId());
                d.setUom(ol.getUom());
            }
            if (l.qty() == null || l.qty().signum() <= 0) throw BizException.of(PurchaseErrorCodes.LINE_QTY_POSITIVE, no);
            d.setQty(Decimals.qty(l.qty()));
            d.setBaseQty(Decimals.qty(support.toBase(d.getMaterialId(), d.getQty(), d.getUom())));
            d.setSupplierBatchNo(PurSupport.trim(l.supplierBatchNo()));
            d.setProductionDate(l.productionDate());
            d.setRemark(PurSupport.trim(l.remark()));
            d.setInspectRequired(support.materialApi().getQualityAttr(d.getMaterialId()).iqcRequired());
            d.setTargetWarehouseId(targetWarehouse(d));
            d.setStockedQty(BigDecimal.ZERO);
            d.setInspectStatus(Boolean.TRUE.equals(d.getInspectRequired()) ? PENDING : NONE);
            d.setQualifiedQty(BigDecimal.ZERO);
            d.setConcessionQty(BigDecimal.ZERO);
            d.setRejectedQty(BigDecimal.ZERO);
            d.setReturnedQty(BigDecimal.ZERO);
            d.setStatementQty(BigDecimal.ZERO);
            checkLine(r, d, warnings);
            lineMapper.insert(d);
        }
        return warnings;
    }

    /** 入库仓：需检 → 待检仓；免检 → 物料默认仓 */
    private Long targetWarehouse(ReceiptLineDO d) {
        WarehouseDTO w = warehouseApi.getDefaultWarehouse(d.getMaterialId(), Boolean.TRUE.equals(d.getInspectRequired()) ? WarehouseType.QC : null);
        return w.id();
    }

    /** R01～R03、R08：订单状态与供应商、超收、保质期、样品订单 */
    private void checkLine(ReceiptDO r, ReceiptLineDO d, List<String> warnings) {
        int no = d.getLineNo();
        if (OUTSOURCE.equals(r.getReceiptType())) {
            outsourcingService.checkReceive(d.getOrderId(), r.getSupplierId(), d.getBaseQty(), no, warnings);
        } else {
            OrderDO o = orderService.getOrThrow(d.getOrderId());
            OrderLineDO ol = orderService.linesByIds(List.of(d.getOrderLineId())).get(d.getOrderLineId());
            if (!o.getSupplierId().equals(r.getSupplierId())) throw BizException.of(PurchaseErrorCodes.RECEIPT_ORDER_SUPPLIER, no);
            if (o.getStatus() != DocStatus.APPROVED && o.getStatus() != DocStatus.IN_PROGRESS) throw BizException.of(PurchaseErrorCodes.RECEIPT_ORDER_STATUS, o.getDocNo());
            if (OrderService.CLOSED.equals(ol.getLineStatus())) throw BizException.of(PurchaseErrorCodes.RECEIPT_LINE_CLOSED, o.getDocNo(), ol.getLineNo());
            boolean sampleOrder = "SAMPLE".equals(o.getOrderType());
            if (SAMPLE.equals(r.getReceiptType()) && !sampleOrder) throw new BizException(PurchaseErrorCodes.RECEIPT_SAMPLE_ONLY);
            if (PURCHASE.equals(r.getReceiptType()) && sampleOrder) throw BizException.of(PurchaseErrorCodes.RECEIPT_TYPE_MISMATCH, no);
            // R02：到货数量 ≤ 未到货数量 × (1 + 超收比例)
            MaterialPurchaseAttr a = support.materialApi().getPurchaseAttr(d.getMaterialId());
            BigDecimal pct = a.overReceivePct() != null && a.overReceivePct().signum() > 0 ? a.overReceivePct()
                    : PurSupport.nz(support.params().getDecimal(PurchaseModuleConfig.P_OVER_RECEIVE_PCT)).divide(PurSupport.HUNDRED);
            BigDecimal openBase = ol.getBaseQty().subtract(ol.getReceivedQty()).max(BigDecimal.ZERO);
            BigDecimal maxBase = openBase.multiply(BigDecimal.ONE.add(pct));
            if (d.getBaseQty().compareTo(maxBase) > 0) {
                throw BizException.of(PurchaseErrorCodes.RECEIPT_OVER, no, PurSupport.plain(OrderService.toUom(ol, maxBase)));
            }
        }
        // R03：有保质期的物料生产日期必填且不晚于今天；剩余保质期比例不足时按参数警告或阻止
        MaterialStockAttr st = support.materialApi().getStockAttr(d.getMaterialId());
        if (st.shelfLifeDays() != null && st.shelfLifeDays() > 0) {
            if (d.getProductionDate() == null || d.getProductionDate().isAfter(LocalDate.now())) {
                throw BizException.of(PurchaseErrorCodes.RECEIPT_PRODUCTION_DATE, no);
            }
            if (st.minRemainingLifePct() != null && st.minRemainingLifePct().signum() > 0) {
                long used = ChronoUnit.DAYS.between(d.getProductionDate(), LocalDate.now());
                BigDecimal remain = BigDecimal.valueOf(st.shelfLifeDays() - used).divide(BigDecimal.valueOf(st.shelfLifeDays()), 6, RoundingMode.HALF_UP);
                if (remain.compareTo(st.minRemainingLifePct()) < 0) {
                    String msg = BizException.of(PurchaseErrorCodes.RECEIPT_LIFE, no, PurSupport.pctText(remain), PurSupport.pctText(st.minRemainingLifePct())).getMessage();
                    if ("BLOCK".equals(support.params().getString(PurchaseModuleConfig.P_MIN_LIFE_CHECK))) {
                        throw BizException.of(PurchaseErrorCodes.RECEIPT_LIFE, no, PurSupport.pctText(remain), PurSupport.pctText(st.minRemainingLifePct()));
                    }
                    warnings.add(msg);
                }
            }
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        ReceiptDO r = getOrThrow(id);
        PurSupport.requireDraft(r);
        lineMapper.deleteByParent(id);
        mapper.deleteById(id);
        fileApi.deleteByBiz(BIZ_TYPE, id);
    }

    // ==================== 审核 / 反审核 ====================

    /** 审核：回写订单已到货 → 发布事件 → 按入库仓生成仓库入库单（最后执行：参数允许时仓库会立即自动确认） */
    @Transactional(rollbackFor = Exception.class)
    public DocResult approve(Long id) {
        ReceiptDO r = getOrThrow(id);
        if (r.getStatus() != DocStatus.DRAFT) throw BizException.of(GlobalErrorCodes.ILLEGAL_STATE_TRANSITION, r.getStatus().label(), "审核");
        SupplierDO s = supplierService.getOrThrow(r.getSupplierId());
        List<String> warnings = new ArrayList<>();
        if (s.getSupplierStatus() == SupplierStatus.ELIMINATED) throw new BizException(PurchaseErrorCodes.RECEIPT_SUPPLIER_ELIMINATED);
        if (s.getSupplierStatus() == SupplierStatus.SUSPENDED) warnings.add("供应商「" + s.getName() + "」已暂停合作，已下订单允许到货");
        List<ReceiptLineDO> lines = lineMapper.selectByParent(id);
        if (lines.isEmpty()) throw new BizException(PurchaseErrorCodes.DOC_NO_LINES);
        for (ReceiptLineDO l : lines) {
            checkLine(r, l, warnings);
            l.setTargetWarehouseId(targetWarehouse(l));
            l.setInspectStatus(Boolean.TRUE.equals(l.getInspectRequired()) ? PENDING : NONE);
            l.setRejectReason(null);
            lineMapper.updateByIdOrFail(l);
        }
        support.fire(PurStateMachines.RECEIPT, mapper, r, BIZ_TYPE, PurAction.APPROVE, null);
        refreshUpstream(r, lines);
        eventPublisher.publish(new PurchaseReceiptApprovedEvent(r.getId(), r.getDocNo(), r.getReceiptType(), r.getSupplierId(), true,
                lines.stream().map(l -> new PurchaseReceiptApprovedEvent.Line(l.getId(), l.getOrderId(), l.getOrderLineId(), l.getMaterialId(),
                        l.getBaseQty(), Boolean.TRUE.equals(l.getInspectRequired()))).toList()));
        createStockIns(r, lines);
        return new DocResult(getOrThrow(id).getStatus().name(), warnings);
    }

    /** R05：按入库仓拆分生成采购入库单（委外收货为委外入库） */
    private void createStockIns(ReceiptDO r, List<ReceiptLineDO> lines) {
        StockInType type = OUTSOURCE.equals(r.getReceiptType()) ? StockInType.OUTSOURCE_IN : StockInType.PURCHASE_IN;
        Map<Long, List<ReceiptLineDO>> byWarehouse = lines.stream().collect(Collectors.groupingBy(ReceiptLineDO::getTargetWarehouseId, LinkedHashMap::new, Collectors.toList()));
        SourceRef src = new SourceRef(BIZ_TYPE, r.getId(), r.getDocNo());
        for (Map.Entry<Long, List<ReceiptLineDO>> e : byWarehouse.entrySet()) {
            List<StockInRequest.Line> reqLines = e.getValue().stream().map(l -> new StockInRequest.Line(l.getId(), l.getMaterialId(), l.getUom(), l.getQty(),
                    null, l.getSupplierBatchNo(), l.getProductionDate(), unitCost(r, l), null)).toList();
            List<Long> ids = inventoryDocApi.createStockIn(new StockInRequest(type, src, e.getKey(), LocalDate.now(), r.getSupplierId(), null, reqLines));
            Long stockInId = ids.isEmpty() ? null : ids.get(0);
            for (ReceiptLineDO l : lineMapper.selectBatchIds(e.getValue().stream().map(ReceiptLineDO::getId).toList())) {
                if (l.getStockInId() == null && stockInId != null) {
                    l.setStockInId(stockInId);
                    lineMapper.updateByIdOrFail(l);
                }
            }
        }
    }

    /** 入库单价：本位币、不含税、每基本单位 */
    private BigDecimal unitCost(ReceiptDO r, ReceiptLineDO l) {
        if (OUTSOURCE.equals(r.getReceiptType())) {
            OutsourcingDO o = outsourcingService.getOrThrow(l.getOrderId());
            return Decimals.price(o.getProcessPrice().multiply(o.getExchangeRate()));
        }
        OrderLineDO ol = orderService.linesByIds(List.of(l.getOrderLineId())).get(l.getOrderLineId());
        OrderDO o = orderService.getOrThrow(l.getOrderId());
        if (ol.getBaseQty().signum() == 0) return null;
        return Decimals.price(ol.getPrice().multiply(ol.getQty()).divide(ol.getBaseQty(), 10, RoundingMode.HALF_UP).multiply(o.getExchangeRate()));
    }

    /** R06：生成的入库单全部未确认时作废入库单，扣回订单已到货；有已确认的入库单时阻止 */
    @Transactional(rollbackFor = Exception.class)
    public void unapprove(Long id, String reason) {
        ReceiptDO r = getOrThrow(id);
        String why = PurSupport.requireReason(reason, "反审核");
        List<ReceiptLineDO> lines = lineMapper.selectByParent(id);
        for (ReceiptLineDO l : lines) {
            if (l.getStockedQty().signum() > 0) throw BizException.of(PurchaseErrorCodes.RECEIPT_STOCKED, Objects.toString(l.getStockInNo(), ""));
        }
        if (r.getStatus() != DocStatus.APPROVED) throw BizException.of(GlobalErrorCodes.ILLEGAL_STATE_TRANSITION, r.getStatus().label(), "反审核");
        inventoryDocApi.cancelBySource(BIZ_TYPE, id);
        for (ReceiptLineDO l : lines) {
            l.setStockInId(null);
            l.setStockInNo(null);
            l.setRejectReason(null);
            l.setInspectStatus(Boolean.TRUE.equals(l.getInspectRequired()) ? PENDING : NONE);
            lineMapper.updateByIdOrFail(l);
        }
        support.fire(PurStateMachines.RECEIPT, mapper, r, BIZ_TYPE, PurAction.UNAPPROVE, why);
        refreshUpstream(r, lines);
        eventPublisher.publish(new PurchaseReceiptApprovedEvent(r.getId(), r.getDocNo(), r.getReceiptType(), r.getSupplierId(), false,
                lines.stream().map(l -> new PurchaseReceiptApprovedEvent.Line(l.getId(), l.getOrderId(), l.getOrderLineId(), l.getMaterialId(),
                        l.getBaseQty(), Boolean.TRUE.equals(l.getInspectRequired()))).toList()));
    }

    /** 重新汇总采购订单行 / 委外单 */
    private void refreshUpstream(ReceiptDO r, List<ReceiptLineDO> lines) {
        if (OUTSOURCE.equals(r.getReceiptType())) {
            outsourcingService.refreshReceipts(lines.stream().map(ReceiptLineDO::getOrderId).toList());
        } else {
            orderService.refreshLines(lines.stream().map(ReceiptLineDO::getOrderLineId).toList());
        }
    }

    // ==================== 仓库、品质回写 ====================

    /** 入库确认：回写已入库、批次；免检（入非待检仓）直接合格 */
    @Transactional(rollbackFor = Exception.class)
    public void onStockInConfirmed(StockInConfirmedEvent e) {
        Map<Long, BigDecimal> qty = new HashMap<>();
        Map<Long, String> batch = new HashMap<>();
        for (StockInConfirmedEvent.Line l : e.getLines()) {
            if (l.sourceLineId() == null) continue;
            qty.merge(l.sourceLineId(), l.baseQty(), BigDecimal::add);
            if (l.batchNo() != null) batch.putIfAbsent(l.sourceLineId(), l.batchNo());
        }
        if (qty.isEmpty()) return;
        boolean qc = WarehouseType.QC.name().equals(e.getWarehouseType());
        List<ReceiptLineDO> lines = lineMapper.selectBatchIds(qty.keySet());
        for (ReceiptLineDO l : lines) {
            l.setStockInId(e.getStockInId());
            l.setStockInNo(e.getStockInNo());
            l.setStockedQty(Decimals.qty(qty.get(l.getId())));
            l.setStockedDate(LocalDate.now());
            l.setBatchNo(batch.get(l.getId()));
            l.setRejectReason(null);
            if (qc) {
                if (NONE.equals(l.getInspectStatus())) l.setInspectStatus(PENDING);
            } else {
                l.setInspectStatus(NONE);
                l.setQualifiedQty(l.getStockedQty());
                l.setJudgedDate(LocalDate.now());
            }
            lineMapper.updateByIdOrFail(l);
        }
        afterLineChange(lines);
    }

    /** 反确认前：已检验、已退货或已对账的到货行阻止反确认 */
    public void onStockInReversing(StockDocEvent e) {
        for (ReceiptLineDO l : linesOfStockIn(e.getDocId())) {
            boolean judged = Boolean.TRUE.equals(l.getInspectRequired()) && l.getInspectionNo() != null;
            if (judged || l.getReturnedQty().signum() > 0 || l.getStatementQty().signum() > 0) {
                ReceiptDO r = getOrThrow(l.getReceiptId());
                throw BizException.of(PurchaseErrorCodes.RECEIPT_REVERSE_BLOCKED, r.getDocNo(), l.getLineNo());
            }
        }
    }

    /** 反确认后：扣回已入库、合格 */
    @Transactional(rollbackFor = Exception.class)
    public void onStockInReversed(StockDocEvent e) {
        List<ReceiptLineDO> lines = linesOfStockIn(e.getDocId());
        for (ReceiptLineDO l : lines) {
            l.setStockedQty(BigDecimal.ZERO);
            l.setStockedDate(null);
            l.setBatchNo(null);
            l.setQualifiedQty(BigDecimal.ZERO);
            l.setConcessionQty(BigDecimal.ZERO);
            l.setRejectedQty(BigDecimal.ZERO);
            l.setJudgedDate(null);
            l.setInspectStatus(Boolean.TRUE.equals(l.getInspectRequired()) ? PENDING : NONE);
            lineMapper.updateByIdOrFail(l);
        }
        afterLineChange(lines);
    }

    /** 入库单被仓库退回：到货单保持已审核，行上显示退回原因，通知收货员和采购员 */
    @Transactional(rollbackFor = Exception.class)
    public void onStockInRejected(StockDocEvent e) {
        List<ReceiptLineDO> lines = linesOfStockIn(e.getDocId());
        if (lines.isEmpty()) return;
        for (ReceiptLineDO l : lines) {
            l.setRejectReason(e.getReason() == null ? "入库被退回" : ("入库被退回：" + e.getReason()));
            lineMapper.updateByIdOrFail(l);
        }
        ReceiptDO r = getOrThrow(lines.get(0).getReceiptId());
        List<Long> to = new ArrayList<>();
        to.add(r.getReceiverId());
        to.add(r.getOwnerId());
        if (!OUTSOURCE.equals(r.getReceiptType())) {
            orderService.byIds(lines.stream().map(ReceiptLineDO::getOrderId).toList()).values().forEach(o -> to.add(o.getOwnerId()));
        }
        support.message(to, "入库单被退回", "到货单 " + r.getDocNo() + " 生成的入库单 " + e.getDocNo() + " 被仓库退回：" + Objects.toString(e.getReason(), "")
                + "。请反审核到货单修改后重新审核", "/purchase/receipt/" + r.getId());
        support.log(BIZ_TYPE, r.getId(), r.getDocNo(), "STOCK_IN_REJECTED", "入库被退回", r.getStatus().name(), r.getStatus().name(), e.getReason());
    }

    private List<ReceiptLineDO> linesOfStockIn(Long stockInId) {
        return lineMapper.selectList(new LambdaQueryWrapper<ReceiptLineDO>().eq(ReceiptLineDO::getStockInId, stockInId));
    }

    /**
     * IQC 判定结果回写（品质调用 PurchaseReceiptApi）：合格（含特采）、不合格、检验单号；状态 合格 / 特采 / 不合格 / 部分合格。
     */
    @Transactional(rollbackFor = Exception.class)
    public void applyInspection(Long receiptLineId, String inspectionNo, BigDecimal qualified, BigDecimal concession, BigDecimal rejected) {
        ReceiptLineDO l = lineMapper.selectById(receiptLineId);
        if (l == null) throw new BizException(PurchaseErrorCodes.RECEIPT_LINE_NOT_EXISTS);
        BigDecimal q = PurSupport.nz(qualified);
        BigDecimal c = PurSupport.nz(concession);
        BigDecimal rj = PurSupport.nz(rejected);
        String status;
        if (rj.signum() == 0) status = c.signum() > 0 ? "CONCESSION" : "QUALIFIED";
        else if (q.add(c).signum() == 0) status = "REJECTED";
        else status = "PARTIAL";
        l.setQualifiedQty(Decimals.qty(q.add(c)));
        l.setConcessionQty(Decimals.qty(c));
        l.setRejectedQty(Decimals.qty(rj));
        l.setInspectStatus(status);
        l.setInspectionNo(PurSupport.trim(inspectionNo));
        l.setJudgedDate(LocalDate.now());
        lineMapper.updateByIdOrFail(l);
        afterLineChange(List.of(l));
    }

    /** 到货行变化后：汇总到订单行/委外单，刷新到货单完成状态 */
    private void afterLineChange(List<ReceiptLineDO> lines) {
        if (lines.isEmpty()) return;
        Map<Long, ReceiptDO> receipts = mapper.selectBatchIds(lines.stream().map(ReceiptLineDO::getReceiptId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(ReceiptDO::getId, r -> r));
        List<Long> orderLines = new ArrayList<>();
        List<Long> outsourcings = new ArrayList<>();
        for (ReceiptLineDO l : lines) {
            ReceiptDO r = receipts.get(l.getReceiptId());
            if (r != null && OUTSOURCE.equals(r.getReceiptType())) outsourcings.add(l.getOrderId());
            else orderLines.add(l.getOrderLineId());
        }
        orderService.refreshLines(orderLines);
        outsourcingService.refreshReceipts(outsourcings);
        receipts.keySet().forEach(this::refreshCompletion);
    }

    /** 所有行已入库且检验状态不是待检时，到货单变为已完成；反之恢复为已审核 */
    public void refreshCompletion(Long receiptId) {
        ReceiptDO r = getOrThrow(receiptId);
        if (r.getStatus() != DocStatus.APPROVED && r.getStatus() != DocStatus.COMPLETED) return;
        List<ReceiptLineDO> lines = lineMapper.selectByParent(receiptId);
        boolean done = !lines.isEmpty() && lines.stream().allMatch(l -> l.getStockedQty().compareTo(l.getBaseQty()) >= 0 && !PENDING.equals(l.getInspectStatus()));
        if (r.getStatus() == DocStatus.APPROVED && done) support.fire(PurStateMachines.RECEIPT, mapper, r, BIZ_TYPE, PurAction.COMPLETE, null);
        else if (r.getStatus() == DocStatus.COMPLETED && !done) support.fire(PurStateMachines.RECEIPT, mapper, r, BIZ_TYPE, PurAction.REOPEN, null);
    }

    /** 退货出库确认/反确认后：重新汇总到货行已退货数量 */
    @Transactional(rollbackFor = Exception.class)
    public void refreshReturned(Collection<Long> receiptLineIds) {
        Set<Long> ids = receiptLineIds.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) return;
        Map<Long, BigDecimal> out = returnLineMapper.sumOutByReceiptLines(ids).stream().collect(Collectors.toMap(IdQtyRow::getId, IdQtyRow::getQty));
        for (ReceiptLineDO l : lineMapper.selectBatchIds(ids)) {
            l.setReturnedQty(Decimals.qty(out.getOrDefault(l.getId(), BigDecimal.ZERO)));
            lineMapper.updateByIdOrFail(l);
        }
    }

    // ==================== 退货选单 ====================

    /** 可退数量 = 入库数量 − 已退货 − 未完成退货单占用（PUR-RT-R01） */
    public Map<Long, BigDecimal> returnable(Collection<Long> receiptLineIds) {
        Set<Long> ids = receiptLineIds.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) return Collections.emptyMap();
        Map<Long, BigDecimal> open = returnLineMapper.sumOpenByReceiptLines(ids).stream().collect(Collectors.toMap(IdQtyRow::getId, IdQtyRow::getQty));
        Map<Long, BigDecimal> map = new HashMap<>();
        for (ReceiptLineDO l : lineMapper.selectBatchIds(ids)) {
            map.put(l.getId(), l.getStockedQty().subtract(l.getReturnedQty()).subtract(open.getOrDefault(l.getId(), BigDecimal.ZERO)).max(BigDecimal.ZERO));
        }
        return map;
    }

    public PageResult<ReturnableLine> returnableLines(ReturnableQuery q) {
        List<ReceiptDO> receipts = mapper.selectList(new LambdaQueryWrapper<ReceiptDO>().in(ReceiptDO::getStatus, DocStatus.APPROVED, DocStatus.COMPLETED)
                .eq(q.getSupplierId() != null, ReceiptDO::getSupplierId, q.getSupplierId())
                .likeRight(StringUtils.hasText(q.getReceiptNo()), ReceiptDO::getDocNo, q.getReceiptNo() == null ? null : q.getReceiptNo().trim().toUpperCase())
                .orderByDesc(ReceiptDO::getArrivalAt).last("LIMIT 1000"));
        if (receipts.isEmpty()) return PageResult.empty();
        Map<Long, ReceiptDO> byId = receipts.stream().collect(Collectors.toMap(ReceiptDO::getId, r -> r));
        List<ReceiptLineDO> lines = lineMapper.selectList(new LambdaQueryWrapper<ReceiptLineDO>().in(ReceiptLineDO::getReceiptId, byId.keySet())
                .gt(ReceiptLineDO::getStockedQty, 0).eq(q.getMaterialId() != null, ReceiptLineDO::getMaterialId, q.getMaterialId()));
        Map<Long, BigDecimal> can = returnable(lines.stream().map(ReceiptLineDO::getId).toList());
        lines = lines.stream().filter(l -> can.getOrDefault(l.getId(), BigDecimal.ZERO).signum() > 0).toList();
        int from = Math.min((q.getPageNo() - 1) * q.getPageSize(), lines.size());
        List<ReceiptLineDO> page = lines.subList(from, Math.min(from + q.getPageSize(), lines.size()));
        return new PageResult<>(page.stream().map(l -> returnableLine(byId.get(l.getReceiptId()), l, can.get(l.getId()))).toList(), lines.size());
    }

    public ReturnableLine returnableLine(ReceiptDO r, ReceiptLineDO l, BigDecimal returnable) {
        MaterialDTO m = support.material(l.getMaterialId());
        String orderNo;
        String currency;
        BigDecimal pit;
        if (OUTSOURCE.equals(r.getReceiptType())) {
            OutsourcingDO o = outsourcingService.getOrThrow(l.getOrderId());
            orderNo = o.getDocNo();
            currency = o.getCurrency();
            pit = Decimals.price(o.getProcessPrice().multiply(BigDecimal.ONE.add(o.getTaxRate())));
        } else {
            OrderDO o = orderService.getOrThrow(l.getOrderId());
            OrderLineDO ol = orderService.linesByIds(List.of(l.getOrderLineId())).get(l.getOrderLineId());
            orderNo = o.getDocNo();
            currency = o.getCurrency();
            pit = basePriceInclTax(ol);
        }
        return new ReturnableLine(l.getId(), r.getId(), r.getDocNo(), r.getArrivalAt().toLocalDate(), l.getOrderId(), orderNo, l.getOrderLineId(),
                l.getMaterialId(), m.code(), m.name(), m.baseUom(), l.getBatchNo(), currency, l.getBaseQty(), l.getStockedQty(), l.getRejectedQty(),
                l.getReturnedQty(), returnable, PurSupport.mask(pit, PurSupport.canViewPrice()), l.getInspectStatus());
    }

    /** 订单行含税单价换算为每基本单位 */
    public static BigDecimal basePriceInclTax(OrderLineDO ol) {
        if (ol.getBaseQty().signum() == 0) return ol.getPriceInclTax();
        return Decimals.price(ol.getPriceInclTax().multiply(ol.getQty()).divide(ol.getBaseQty(), 10, RoundingMode.HALF_UP));
    }

    // ==================== 打印 ====================

    public Map<String, Object> printData(Long id) {
        ReceiptDetail d = detail(id);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("docNo", d.docNo());
        data.put("status", d.status());
        data.put("typeName", TYPE_NAMES.get(d.receiptType()));
        data.put("supplierName", d.supplierName());
        data.put("deliveryNoteNo", Objects.toString(d.deliveryNoteNo(), ""));
        data.put("arrivalAt", d.arrivalAt());
        data.put("receiverName", Objects.toString(d.receiverName(), ""));
        data.put("remark", Objects.toString(d.remark(), ""));
        data.put("lines", d.lines().stream().map(l -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("lineNo", l.lineNo());
            m.put("orderNo", Objects.toString(l.orderNo(), ""));
            m.put("materialCode", l.materialCode());
            m.put("materialName", l.materialName());
            m.put("spec", Objects.toString(l.materialSpec(), ""));
            m.put("uom", l.uom());
            m.put("qty", l.qty());
            m.put("supplierBatchNo", Objects.toString(l.supplierBatchNo(), ""));
            m.put("inspect", l.inspectRequired() ? "是" : "否");
            m.put("warehouseName", Objects.toString(l.targetWarehouseName(), ""));
            return m;
        }).toList());
        return data;
    }

    // ==================== 工具 ====================

    public ReceiptDO getOrThrow(Long id) {
        ReceiptDO r = id == null ? null : mapper.selectById(id);
        if (r == null) throw new BizException(PurchaseErrorCodes.RECEIPT_NOT_EXISTS);
        return r;
    }

    public Map<Long, ReceiptLineDO> linesByIds(Collection<Long> ids) {
        Set<Long> set = ids.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (set.isEmpty()) return Collections.emptyMap();
        return lineMapper.selectBatchIds(set).stream().collect(Collectors.toMap(ReceiptLineDO::getId, l -> l));
    }

    public Map<Long, ReceiptDO> byIds(Collection<Long> ids) {
        Set<Long> set = ids.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (set.isEmpty()) return Collections.emptyMap();
        return mapper.selectBatchIds(set).stream().collect(Collectors.toMap(ReceiptDO::getId, r -> r));
    }

    private static Long nz(Long v) {
        return v == null ? 0L : v;
    }
}
