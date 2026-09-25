package com.erp.module.purchase.service.outsourcing;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.common.enums.DocStatus;
import com.erp.common.exception.BizException;
import com.erp.common.exception.GlobalErrorCodes;
import com.erp.common.result.PageResult;
import com.erp.common.util.Decimals;
import com.erp.framework.datascope.DataScopes;
import com.erp.module.engineering.api.bom.BomApi;
import com.erp.module.engineering.api.bom.BomDTO;
import com.erp.module.engineering.api.material.MaterialDTO;
import com.erp.module.engineering.api.material.SourceType;
import com.erp.module.inventory.api.doc.InventoryDocApi;
import com.erp.module.inventory.api.doc.SourceRef;
import com.erp.module.inventory.api.doc.StockInRequest;
import com.erp.module.inventory.api.doc.StockInType;
import com.erp.module.inventory.api.doc.StockOutRequest;
import com.erp.module.inventory.api.doc.StockOutType;
import com.erp.module.inventory.api.stock.InventoryQueryApi;
import com.erp.module.inventory.api.warehouse.WarehouseApi;
import com.erp.module.inventory.api.warehouse.WarehouseDTO;
import com.erp.module.inventory.api.warehouse.WarehouseType;
import com.erp.module.purchase.api.PurchaseErrorCodes;
import com.erp.module.purchase.api.outsourcing.MrpOutsourceSuggestion;
import com.erp.module.purchase.config.PurchaseModuleConfig;
import com.erp.module.purchase.controller.vo.CommonVOs.DocResult;
import com.erp.module.purchase.controller.vo.CommonVOs.RelatedDoc;
import com.erp.module.purchase.controller.vo.OutsourcingVOs.BomPreview;
import com.erp.module.purchase.controller.vo.OutsourcingVOs.OsDetail;
import com.erp.module.purchase.controller.vo.OutsourcingVOs.OsMaterialResp;
import com.erp.module.purchase.controller.vo.OutsourcingVOs.OsMaterialSave;
import com.erp.module.purchase.controller.vo.OutsourcingVOs.OsQuery;
import com.erp.module.purchase.controller.vo.OutsourcingVOs.OsReceiptLine;
import com.erp.module.purchase.controller.vo.OutsourcingVOs.OsRow;
import com.erp.module.purchase.controller.vo.OutsourcingVOs.OsSave;
import com.erp.module.purchase.controller.vo.OutsourcingVOs.OsTxn;
import com.erp.module.purchase.controller.vo.OutsourcingVOs.QtyLine;
import com.erp.module.purchase.controller.vo.OutsourcingVOs.SettleLine;
import com.erp.module.purchase.controller.vo.PriceVOs.EffectivePrice;
import com.erp.module.purchase.dal.dataobject.OrderLineAggRow;
import com.erp.module.purchase.dal.dataobject.OutsourcingDO;
import com.erp.module.purchase.dal.dataobject.OutsourcingMaterialDO;
import com.erp.module.purchase.dal.dataobject.OutsourcingTxnDO;
import com.erp.module.purchase.dal.dataobject.ReceiptDO;
import com.erp.module.purchase.dal.dataobject.ReceiptLineDO;
import com.erp.module.purchase.dal.dataobject.SupplierDO;
import com.erp.module.purchase.dal.mapper.OutsourcingMapper;
import com.erp.module.purchase.dal.mapper.OutsourcingMaterialMapper;
import com.erp.module.purchase.dal.mapper.OutsourcingTxnMapper;
import com.erp.module.purchase.dal.mapper.ReceiptLineMapper;
import com.erp.module.purchase.dal.mapper.ReceiptMapper;
import com.erp.module.purchase.service.PurAction;
import com.erp.module.purchase.service.PurStateMachines;
import com.erp.module.purchase.service.PurSupport;
import com.erp.module.purchase.service.price.PriceService;
import com.erp.module.purchase.service.supplier.SupplierService;
import com.erp.module.system.api.currency.CurrencyApi;
import com.erp.module.system.api.file.FileApi;
import com.erp.module.system.api.uom.UomApi;
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
 * 委外加工（需求 07-07）：草稿 → 待审批 → 已审核 → 执行中（发料或收货）→ 已完成（收齐并核销）/ 已关闭。
 * 发料生成委外发料出库单（OUTSOURCE_ISSUE），余料退回生成委外退料入库单（OUTSOURCE_RETURN），收货走到货单（委外收货）。
 * 已发、退回数量来自仓库确认后写入的 pur_outsourcing_txn（反确认时标记），保证幂等。
 */
@Service
public class OutsourcingService {

    public static final String BIZ_TYPE = PurchaseModuleConfig.OUTSOURCING;
    /** 余料退回入库单的来源类型（每次退回独立的来源 ID，便于多次退回） */
    public static final String RETURN_SOURCE = "PUR_OS_RETURN";
    public static final String ISSUE = "ISSUE";
    public static final String RETURN = "RETURN";

    private final OutsourcingMapper mapper;
    private final OutsourcingMaterialMapper materialMapper;
    private final OutsourcingTxnMapper txnMapper;
    private final ReceiptMapper receiptMapper;
    private final ReceiptLineMapper receiptLineMapper;
    private final SupplierService supplierService;
    private final PriceService priceService;
    private final PurSupport support;
    private final BomApi bomApi;
    private final UomApi uomApi;
    private final CurrencyApi currencyApi;
    private final InventoryDocApi inventoryDocApi;
    private final InventoryQueryApi inventoryQueryApi;
    private final WarehouseApi warehouseApi;
    private final WorkflowApi workflowApi;
    private final FileApi fileApi;

    public OutsourcingService(OutsourcingMapper mapper, OutsourcingMaterialMapper materialMapper, OutsourcingTxnMapper txnMapper, ReceiptMapper receiptMapper,
                              ReceiptLineMapper receiptLineMapper, SupplierService supplierService, PriceService priceService, PurSupport support,
                              BomApi bomApi, UomApi uomApi, CurrencyApi currencyApi, InventoryDocApi inventoryDocApi, InventoryQueryApi inventoryQueryApi,
                              WarehouseApi warehouseApi, WorkflowApi workflowApi, FileApi fileApi) {
        this.mapper = mapper;
        this.materialMapper = materialMapper;
        this.txnMapper = txnMapper;
        this.receiptMapper = receiptMapper;
        this.receiptLineMapper = receiptLineMapper;
        this.supplierService = supplierService;
        this.priceService = priceService;
        this.support = support;
        this.bomApi = bomApi;
        this.uomApi = uomApi;
        this.currencyApi = currencyApi;
        this.inventoryDocApi = inventoryDocApi;
        this.inventoryQueryApi = inventoryQueryApi;
        this.warehouseApi = warehouseApi;
        this.workflowApi = workflowApi;
        this.fileApi = fileApi;
    }

    // ==================== 查询 ====================

    public PageResult<OsRow> page(OsQuery q) {
        LambdaQueryWrapper<OutsourcingDO> w = new LambdaQueryWrapper<OutsourcingDO>().eq(OutsourcingDO::getDeleted, false)
                .likeRight(StringUtils.hasText(q.getDocNo()), OutsourcingDO::getDocNo, q.getDocNo() == null ? null : q.getDocNo().trim().toUpperCase())
                .eq(q.getSupplierId() != null, OutsourcingDO::getSupplierId, q.getSupplierId())
                .eq(q.getMaterialId() != null, OutsourcingDO::getMaterialId, q.getMaterialId())
                .ge(q.getRequiredFrom() != null, OutsourcingDO::getRequiredDate, q.getRequiredFrom())
                .le(q.getRequiredTo() != null, OutsourcingDO::getRequiredDate, q.getRequiredTo());
        if (StringUtils.hasText(q.getStatuses())) {
            w.in(OutsourcingDO::getStatus, Arrays.stream(q.getStatuses().split(",")).map(String::trim).map(DocStatus::valueOf).toList());
        }
        w.orderByDesc(OutsourcingDO::getDocDate).orderByDesc(OutsourcingDO::getId);
        IPage<OutsourcingDO> page = mapper.selectScopedPage(Page.of(q.getPageNo(), q.getPageSize()), w);
        List<OutsourcingDO> list = page.getRecords();
        Map<Long, List<OutsourcingMaterialDO>> mats = materialMapper.selectByParents(list.stream().map(OutsourcingDO::getId).toList()).stream()
                .collect(Collectors.groupingBy(OutsourcingMaterialDO::getOutsourcingId));
        Map<Long, MaterialDTO> ms = support.materials(list.stream().map(OutsourcingDO::getMaterialId).toList());
        Map<Long, SupplierDO> ss = supplierService.byIds(list.stream().map(OutsourcingDO::getSupplierId).toList());
        Map<Long, UserDTO> users = support.users(list.stream().map(OutsourcingDO::getOwnerId).toList());
        boolean price = PurSupport.canViewPrice();
        return new PageResult<>(list.stream().map(o -> {
            List<OutsourcingMaterialDO> m = mats.getOrDefault(o.getId(), List.of());
            BigDecimal req = m.stream().map(OutsourcingMaterialDO::getRequiredQty).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal issued = m.stream().map(OutsourcingMaterialDO::getIssuedQty).reduce(BigDecimal.ZERO, BigDecimal::add);
            MaterialDTO md = ms.get(o.getMaterialId());
            SupplierDO s = ss.get(o.getSupplierId());
            return new OsRow(o.getId(), o.getDocNo(), o.getDocDate(), o.getSupplierId(), s == null ? null : s.getShortName(), o.getMaterialId(),
                    md == null ? null : md.code(), md == null ? null : md.name(), md == null ? null : md.baseUom(), o.getQty(),
                    PurSupport.mask(o.getProcessPrice(), price), o.getCurrency(), o.getRequiredDate(),
                    req.signum() == 0 ? BigDecimal.ZERO : issued.multiply(PurSupport.HUNDRED).divide(req, 2, RoundingMode.HALF_UP),
                    o.getReceivedQty(), o.getQualifiedQty(), o.getStatus().name(), PurSupport.name(users, o.getOwnerId()));
        }).toList(), page.getTotal());
    }

    public OsDetail detail(Long id) {
        OutsourcingDO o = getOrThrow(id);
        DataScopes.check(o.getOrgId(), o.getDeptId(), o.getOwnerId(), "委外单");
        MaterialDTO m = support.material(o.getMaterialId());
        SupplierDO s = supplierService.getOrThrow(o.getSupplierId());
        BomDTO bom = bomApi.getBom(o.getBomId()).orElse(null);
        List<OutsourcingMaterialDO> mats = materialMapper.selectByParent(id);
        List<ReceiptLineDO> rls = receiptLineMapper.selectList(new LambdaQueryWrapper<ReceiptLineDO>().eq(ReceiptLineDO::getOrderId, id)
                .isNull(ReceiptLineDO::getOrderLineId));
        Map<Long, ReceiptDO> receipts = rls.isEmpty() ? Collections.emptyMap() : receiptMapper.selectBatchIds(rls.stream().map(ReceiptLineDO::getReceiptId)
                .collect(Collectors.toSet())).stream().collect(Collectors.toMap(ReceiptDO::getId, r -> r));
        List<OsReceiptLine> receiptLines = rls.stream().filter(l -> receipts.containsKey(l.getReceiptId())).map(l -> {
            ReceiptDO r = receipts.get(l.getReceiptId());
            return new OsReceiptLine(r.getId(), r.getDocNo(), r.getArrivalAt(), r.getStatus().name(), l.getBaseQty(), l.getStockedQty(),
                    l.getInspectStatus(), l.getQualifiedQty(), l.getRejectedQty());
        }).toList();
        Map<Long, MaterialDTO> cms = support.materials(mats.stream().map(OutsourcingMaterialDO::getMaterialId).toList());
        Map<Long, OutsourcingMaterialDO> matById = mats.stream().collect(Collectors.toMap(OutsourcingMaterialDO::getId, x -> x));
        List<OsTxn> txns = txnMapper.selectByOutsourcing(id).stream().map(t -> {
            OutsourcingMaterialDO om = matById.get(t.getOutsourcingMaterialId());
            MaterialDTO cm = om == null ? null : cms.get(om.getMaterialId());
            return new OsTxn(t.getId(), t.getTxnType(), t.getStockDocId(), t.getStockDocNo(), cm == null ? null : cm.code(), cm == null ? null : cm.name(),
                    t.getQty(), Boolean.TRUE.equals(t.getReversed()), t.getCreatedAt());
        }).toList();
        List<RelatedDoc> related = new ArrayList<>();
        receipts.values().stream().sorted(Comparator.comparing(ReceiptDO::getId)).forEach(r -> related.add(new RelatedDoc("DOWN", "委外收货",
                r.getDocNo(), r.getDocDate(), r.getStatus().name(), r.getStatus().label(), "/purchase/receipt/" + r.getId())));
        boolean price = PurSupport.canViewPrice();
        return new OsDetail(o.getId(), o.getDocNo(), o.getDocDate(), o.getStatus().name(), s.getId(), s.getShortName(), m.id(), m.code(), m.name(), m.spec(),
                m.baseUom(), o.getBomId(), bom == null ? null : bom.docNo(), bom == null ? null : bom.version(), o.getQty(),
                PurSupport.mask(o.getProcessPrice(), price), o.getTaxRate(), o.getCurrency(), o.getExchangeRate(), PurSupport.mask(o.getAmount(), price),
                PurSupport.mask(o.getTaxAmount(), price), PurSupport.mask(o.getTotalAmount(), price), o.getRequiredDate(), o.getReceivedQty(),
                o.getQualifiedQty(), kitQty(mats), o.getMrpResultId(), o.getCloseReason(), o.getRemark(), o.getOwnerId(), support.userName(o.getOwnerId()),
                o.getCreatedAt(), o.getVersion(), price, materialResps(o, mats), receiptLines, txns, related);
    }

    private List<OsMaterialResp> materialResps(OutsourcingDO o, List<OutsourcingMaterialDO> mats) {
        Map<Long, MaterialDTO> ms = support.materials(mats.stream().map(OutsourcingMaterialDO::getMaterialId).toList());
        return mats.stream().map(x -> {
            MaterialDTO m = ms.get(x.getMaterialId());
            BigDecimal consumed = consumed(o.getQualifiedQty(), x);
            BigDecimal loss = x.getIssuedQty().subtract(x.getReturnedQty()).subtract(consumed);
            return new OsMaterialResp(x.getId(), x.getLineNo(), x.getMaterialId(), m == null ? null : m.code(), m == null ? null : m.name(),
                    m == null ? null : m.spec(), x.getUom(), x.getQtyPer(), x.getRequiredQty(), x.getIssuedQty(), x.getReturnedQty(), x.getConsumedQty(),
                    x.getLossQty(), x.getLossReason(), x.getAdjustReason(), inventoryQueryApi.getAvailableQty(x.getMaterialId()), consumed, loss);
        }).toList();
    }

    /** 核销消耗 = 合格收货数量 × 单位用量（按单位精度舍入） */
    private BigDecimal consumed(BigDecimal qualified, OutsourcingMaterialDO x) {
        return uomApi.round(PurSupport.nz(qualified).multiply(x.getQtyPer()), x.getUom());
    }

    /** 已发材料可生产的数量（齐套数）= min((已发 − 退回) ÷ 单位用量) */
    static BigDecimal kitQty(List<OutsourcingMaterialDO> mats) {
        return mats.stream().filter(x -> x.getQtyPer().signum() > 0)
                .map(x -> x.getIssuedQty().subtract(x.getReturnedQty()).divide(x.getQtyPer(), 4, RoundingMode.DOWN))
                .min(Comparator.naturalOrder()).orElse(BigDecimal.ZERO);
    }

    /** 选择加工物料后带出默认 BOM 并计算用料（R01） */
    public BomPreview preview(Long materialId, Long bomId, BigDecimal qty) {
        BomDTO bom = resolveBom(materialId, bomId);
        List<OsMaterialResp> list = new ArrayList<>();
        int no = 0;
        Map<Long, MaterialDTO> ms = support.materials(bom.lines().stream().map(BomDTO.Line::componentId).toList());
        for (BomDTO.Line l : bom.lines()) {
            BigDecimal per = qtyPer(bom, l);
            MaterialDTO m = ms.get(l.componentId());
            String uom = StringUtils.hasText(l.uom()) ? l.uom() : m == null ? null : m.baseUom();
            BigDecimal required = requiredQty(qty, per, uom);
            list.add(new OsMaterialResp(null, ++no, l.componentId(), m == null ? null : m.code(), m == null ? null : m.name(), m == null ? null : m.spec(),
                    uom, per, required, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null, null, null,
                    inventoryQueryApi.getAvailableQty(l.componentId()), null, null));
        }
        return new BomPreview(bom.id(), bom.docNo(), bom.version(), list);
    }

    /** R01：物料取得方式为委外且有已审核 BOM */
    private BomDTO resolveBom(Long materialId, Long bomId) {
        MaterialDTO m = support.material(materialId);
        if (m.sourceType() != SourceType.OUTSOURCE) throw BizException.of(PurchaseErrorCodes.OS_MATERIAL, m.code());
        BomDTO bom = bomId != null ? bomApi.getBom(bomId).orElse(null) : bomApi.getDefaultBom(materialId, LocalDate.now()).orElse(null);
        if (bom == null || !bom.materialId().equals(materialId) || bom.status() != DocStatus.APPROVED) throw BizException.of(PurchaseErrorCodes.OS_MATERIAL, m.code());
        return bom;
    }

    /** 单位用量（含损耗）= BOM 用量 ÷ 基数 × (1 + 损耗率) */
    static BigDecimal qtyPer(BomDTO bom, BomDTO.Line l) {
        BigDecimal base = bom.baseQty() == null || bom.baseQty().signum() == 0 ? BigDecimal.ONE : bom.baseQty();
        BigDecimal scrap = l.scrapRate() == null ? BigDecimal.ZERO : l.scrapRate();
        return l.qtyPer().divide(base, 10, RoundingMode.HALF_UP).multiply(BigDecimal.ONE.add(scrap)).setScale(Decimals.QTY_SCALE, RoundingMode.HALF_UP);
    }

    /** 应发数量 = 委外数量 × 单位用量（按单位精度向上取整） */
    private BigDecimal requiredQty(BigDecimal qty, BigDecimal per, String uom) {
        BigDecimal raw = PurSupport.nz(qty).multiply(per);
        int scale = uom == null ? Decimals.QTY_SCALE : Math.min(uomApi.precision(uom), Decimals.QTY_SCALE);
        return raw.setScale(scale, RoundingMode.CEILING).setScale(Decimals.QTY_SCALE, RoundingMode.UNNECESSARY);
    }

    // ==================== 编辑 ====================

    @Transactional(rollbackFor = Exception.class)
    public Long create(OsSave req) {
        OutsourcingDO o = new OutsourcingDO();
        o.setDocNo(support.nextNo(BIZ_TYPE));
        o.setDocDate(LocalDate.now());
        o.setStatus(DocStatus.DRAFT);
        o.setReceivedQty(BigDecimal.ZERO);
        o.setQualifiedQty(BigDecimal.ZERO);
        o.setStatementQty(BigDecimal.ZERO);
        support.fillOwner(o, null);
        BomDTO bom = fill(o, req);
        mapper.insert(o);
        saveMaterials(o, bom, req.materials());
        if (req.fileIds() != null && !req.fileIds().isEmpty()) fileApi.bind(req.fileIds(), BIZ_TYPE, o.getId());
        return o.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, OsSave req) {
        OutsourcingDO o = getOrThrow(id);
        DataScopes.check(o.getOrgId(), o.getDeptId(), o.getOwnerId(), "委外单");
        PurSupport.requireDraft(o);
        if (req.version() != null) o.setVersion(req.version());
        BomDTO bom = fill(o, req);
        mapper.updateByIdOrFail(o);
        saveMaterials(o, bom, req.materials());
        if (req.fileIds() != null && !req.fileIds().isEmpty()) fileApi.bind(req.fileIds(), BIZ_TYPE, o.getId());
    }

    private BomDTO fill(OutsourcingDO o, OsSave req) {
        SupplierDO s = supplierService.validateActive(req.supplierId());
        BomDTO bom = resolveBom(req.materialId(), req.bomId());
        if (req.qty() == null || req.qty().signum() <= 0) throw BizException.of(PurchaseErrorCodes.LINE_QTY_POSITIVE, 1);
        if (req.requiredDate() == null) throw BizException.of(PurchaseErrorCodes.LINE_FIELD_REQUIRED, 1, "要求日期");
        o.setSupplierId(s.getId());
        o.setMaterialId(req.materialId());
        o.setBomId(bom.id());
        o.setBomSnapshot(bom.docNo() + " V" + bom.version());
        o.setQty(Decimals.qty(req.qty()));
        String currency = StringUtils.hasText(req.currency()) ? req.currency().trim().toUpperCase() : s.getCurrency();
        currencyApi.validate(currency);
        o.setCurrency(currency);
        o.setExchangeRate(currency.equals(currencyApi.getBaseCurrency()) ? BigDecimal.ONE
                : req.exchangeRate() != null ? req.exchangeRate() : currencyApi.getRate(currency, o.getDocDate()));
        o.setTaxRate(req.taxRate() != null ? req.taxRate() : s.getPurchaseTaxRate());
        BigDecimal price = req.processPrice();
        if (price == null) {
            EffectivePrice p = priceService.effectiveForUom(s.getId(), req.materialId(), o.getQty(), null, o.getDocDate(), currency);
            price = p == null ? BigDecimal.ZERO : p.price();
        }
        o.setProcessPrice(Decimals.price(price));
        o.setAmount(Decimals.multiplyAmount(o.getQty(), o.getProcessPrice()));
        o.setTaxAmount(Decimals.amount(o.getAmount().multiply(o.getTaxRate())));
        o.setTotalAmount(o.getAmount().add(o.getTaxAmount()));
        o.setRequiredDate(req.requiredDate());
        o.setRemark(PurSupport.trim(req.remark()));
        return bom;
    }

    /** 用料按 BOM 生成；调整应发数量需填写原因 */
    private void saveMaterials(OutsourcingDO o, BomDTO bom, List<OsMaterialSave> overrides) {
        Map<Long, OsMaterialSave> byMaterial = new HashMap<>();
        if (overrides != null) overrides.forEach(x -> byMaterial.put(x.materialId(), x));
        materialMapper.deleteByParent(o.getId());
        Map<Long, MaterialDTO> ms = support.materials(bom.lines().stream().map(BomDTO.Line::componentId).toList());
        int no = 0;
        for (BomDTO.Line l : bom.lines()) {
            MaterialDTO m = ms.get(l.componentId());
            OutsourcingMaterialDO d = new OutsourcingMaterialDO();
            d.setOutsourcingId(o.getId());
            d.setLineNo(++no);
            d.setMaterialId(l.componentId());
            d.setUom(StringUtils.hasText(l.uom()) ? l.uom() : m == null ? null : m.baseUom());
            d.setQtyPer(qtyPer(bom, l));
            BigDecimal computed = requiredQty(o.getQty(), d.getQtyPer(), d.getUom());
            OsMaterialSave ov = byMaterial.get(l.componentId());
            if (ov != null && ov.requiredQty() != null && ov.requiredQty().compareTo(computed) != 0) {
                if (!StringUtils.hasText(ov.adjustReason())) throw BizException.of(PurchaseErrorCodes.OS_ADJUST_REASON, m == null ? "" : m.code());
                d.setRequiredQty(Decimals.qty(ov.requiredQty()));
                d.setAdjustReason(ov.adjustReason().trim());
            } else {
                d.setRequiredQty(computed);
            }
            d.setIssuedQty(BigDecimal.ZERO);
            d.setReturnedQty(BigDecimal.ZERO);
            d.setConsumedQty(BigDecimal.ZERO);
            materialMapper.insert(d);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        OutsourcingDO o = getOrThrow(id);
        DataScopes.check(o.getOrgId(), o.getDeptId(), o.getOwnerId(), "委外单");
        PurSupport.requireDraft(o);
        materialMapper.deleteByParent(id);
        mapper.deleteById(id);
        fileApi.deleteByBiz(BIZ_TYPE, id);
    }

    @Transactional(rollbackFor = Exception.class)
    public void voidDoc(Long id, String reason) {
        OutsourcingDO o = getOrThrow(id);
        DataScopes.check(o.getOrgId(), o.getDeptId(), o.getOwnerId(), "委外单");
        support.fire(PurStateMachines.OUTSOURCING, mapper, o, BIZ_TYPE, PurAction.VOID, PurSupport.trim(reason));
    }

    // ==================== 提交 / 审核 ====================

    @Transactional(rollbackFor = Exception.class)
    public DocResult submit(Long id) {
        OutsourcingDO o = getOrThrow(id);
        DataScopes.check(o.getOrgId(), o.getDeptId(), o.getOwnerId(), "委外单");
        PurSupport.requireDraft(o);
        supplierService.validateActive(o.getSupplierId());
        support.materialApi().validateUsable(o.getMaterialId());
        resolveBom(o.getMaterialId(), o.getBomId());
        support.fire(PurStateMachines.OUTSOURCING, mapper, o, BIZ_TYPE, PurAction.SUBMIT, null);
        Map<String, Object> vars = new HashMap<>();
        vars.put("amountBase", currencyApi.toBase(o.getTotalAmount(), o.getExchangeRate()));
        StartResult r = workflowApi.start(BIZ_TYPE, id, o.getDocNo(), "委外单 " + o.getDocNo(), vars, Map.of(), support.currentUser());
        if (!r.isStarted()) support.fire(PurStateMachines.OUTSOURCING, mapper, o, BIZ_TYPE, PurAction.APPROVE, null);
        return DocResult.of(o.getStatus().name());
    }

    @EventListener
    public void onApproval(ApprovalCompletedEvent e) {
        if (!BIZ_TYPE.equals(e.getBizType())) return;
        OutsourcingDO o = getOrThrow(e.getBizId());
        if (o.getStatus() != DocStatus.PENDING_APPROVAL) return;
        PurAction a = switch (e.getResult()) {
            case APPROVED -> PurAction.APPROVE;
            case WITHDRAWN -> PurAction.WITHDRAW;
            default -> PurAction.REJECT;
        };
        support.fire(PurStateMachines.OUTSOURCING, mapper, o, BIZ_TYPE, a, a == PurAction.REJECT ? e.getComment() : null);
    }

    @Transactional(rollbackFor = Exception.class)
    public void unapprove(Long id, String reason) {
        OutsourcingDO o = getOrThrow(id);
        DataScopes.check(o.getOrgId(), o.getDeptId(), o.getOwnerId(), "委外单");
        String why = PurSupport.requireReason(reason, "反审核");
        boolean executed = !txnMapper.selectByOutsourcing(id).isEmpty() || o.getReceivedQty().signum() > 0
                || receiptLineMapper.selectCount(new LambdaQueryWrapper<ReceiptLineDO>().eq(ReceiptLineDO::getOrderId, id).isNull(ReceiptLineDO::getOrderLineId)) > 0;
        if (executed) throw new BizException(PurchaseErrorCodes.OS_HAS_EXECUTION);
        support.fire(PurStateMachines.OUTSOURCING, mapper, o, BIZ_TYPE, PurAction.UNAPPROVE, why);
    }

    @Transactional(rollbackFor = Exception.class)
    public void close(Long id, String reason) {
        OutsourcingDO o = getOrThrow(id);
        DataScopes.check(o.getOrgId(), o.getDeptId(), o.getOwnerId(), "委外单");
        String why = PurSupport.requireReason(reason, "关闭");
        o.setCloseReason(why);
        support.fire(PurStateMachines.OUTSOURCING, mapper, o, BIZ_TYPE, PurAction.CLOSE, why);
    }

    // ==================== 发料 / 退料 ====================

    /** R02：本次发料 ≤ 应发 × (1 + 超发比例) − 已发；按物料默认仓生成委外发料出库单 */
    @Transactional(rollbackFor = Exception.class)
    public List<Long> issue(Long id, List<QtyLine> lines) {
        OutsourcingDO o = executable(id);
        Map<Long, OutsourcingMaterialDO> mats = materialMapper.selectByParent(id).stream().collect(Collectors.toMap(OutsourcingMaterialDO::getId, x -> x));
        BigDecimal over = BigDecimal.ONE.add(PurSupport.nz(support.params().getDecimal(PurchaseModuleConfig.P_OVER_ISSUE_PCT)).divide(PurSupport.HUNDRED));
        Map<Long, List<StockOutRequest.Line>> byWarehouse = new LinkedHashMap<>();
        Map<Long, MaterialDTO> ms = support.materials(mats.values().stream().map(OutsourcingMaterialDO::getMaterialId).toList());
        for (QtyLine l : positive(lines)) {
            OutsourcingMaterialDO x = mats.get(l.outsourcingMaterialId());
            if (x == null) throw BizException.of(GlobalErrorCodes.DATA_NOT_EXISTS, "委外用料");
            MaterialDTO m = ms.get(x.getMaterialId());
            if (l.qty().compareTo(x.getRequiredQty().multiply(over).subtract(x.getIssuedQty())) > 0) {
                throw BizException.of(PurchaseErrorCodes.OS_ISSUE_OVER, m == null ? "" : m.code());
            }
            Long wid = warehouseApi.getDefaultWarehouse(x.getMaterialId(), null).id();
            byWarehouse.computeIfAbsent(wid, k -> new ArrayList<>()).add(new StockOutRequest.Line(x.getId(), x.getMaterialId(), x.getUom(), l.qty(), null, null));
        }
        List<Long> ids = new ArrayList<>();
        SourceRef src = new SourceRef(BIZ_TYPE, o.getId(), o.getDocNo());
        if (o.getStatus() == DocStatus.APPROVED) support.fire(PurStateMachines.OUTSOURCING, mapper, o, BIZ_TYPE, PurAction.START, null);
        support.log(BIZ_TYPE, o.getId(), o.getDocNo(), "ISSUE", "发料", o.getStatus().name(), o.getStatus().name(), null);
        for (Map.Entry<Long, List<StockOutRequest.Line>> e : byWarehouse.entrySet()) {
            ids.addAll(inventoryDocApi.createStockOut(new StockOutRequest(StockOutType.OUTSOURCE_ISSUE, src, e.getKey(), LocalDate.now(), null, null,
                    o.getSupplierId(), null, e.getValue())));
        }
        return ids;
    }

    /** 余料退回：本次 ≤ 已发 − 已退；良品入物料默认仓，不良入不良品仓（委外退料入库） */
    @Transactional(rollbackFor = Exception.class)
    public List<Long> returnMaterial(Long id, List<QtyLine> lines) {
        OutsourcingDO o = executable(id);
        Map<Long, OutsourcingMaterialDO> mats = materialMapper.selectByParent(id).stream().collect(Collectors.toMap(OutsourcingMaterialDO::getId, x -> x));
        Map<Long, MaterialDTO> ms = support.materials(mats.values().stream().map(OutsourcingMaterialDO::getMaterialId).toList());
        Map<Long, List<StockInRequest.Line>> byWarehouse = new LinkedHashMap<>();
        for (QtyLine l : positive(lines)) {
            OutsourcingMaterialDO x = mats.get(l.outsourcingMaterialId());
            if (x == null) throw BizException.of(GlobalErrorCodes.DATA_NOT_EXISTS, "委外用料");
            MaterialDTO m = ms.get(x.getMaterialId());
            if (l.qty().compareTo(x.getIssuedQty().subtract(x.getReturnedQty())) > 0) throw BizException.of(PurchaseErrorCodes.OS_RETURN_OVER, m == null ? "" : m.code());
            Long wid;
            if (Boolean.TRUE.equals(l.defective())) {
                List<WarehouseDTO> ng = warehouseApi.listByType(WarehouseType.NG);
                wid = ng.stream().filter(WarehouseDTO::isDefault).findFirst().or(() -> ng.stream().findFirst())
                        .map(WarehouseDTO::id).orElseThrow(() -> BizException.of(GlobalErrorCodes.DATA_NOT_EXISTS, "不良品仓"));
            } else {
                wid = warehouseApi.getDefaultWarehouse(x.getMaterialId(), null).id();
            }
            byWarehouse.computeIfAbsent(wid, k -> new ArrayList<>()).add(new StockInRequest.Line(x.getId(), x.getMaterialId(), x.getUom(), l.qty(),
                    null, null, null, null, null));
        }
        List<Long> ids = new ArrayList<>();
        support.log(BIZ_TYPE, o.getId(), o.getDocNo(), "RETURN_MATERIAL", "余料退回", o.getStatus().name(), o.getStatus().name(), null);
        for (Map.Entry<Long, List<StockInRequest.Line>> e : byWarehouse.entrySet()) {
            ids.addAll(inventoryDocApi.createStockIn(new StockInRequest(StockInType.OUTSOURCE_RETURN, new SourceRef(RETURN_SOURCE, IdWorker.getId(), o.getDocNo()),
                    e.getKey(), LocalDate.now(), o.getSupplierId(), null, e.getValue())));
        }
        return ids;
    }

    private OutsourcingDO executable(Long id) {
        OutsourcingDO o = getOrThrow(id);
        DataScopes.check(o.getOrgId(), o.getDeptId(), o.getOwnerId(), "委外单");
        if (o.getStatus() != DocStatus.APPROVED && o.getStatus() != DocStatus.IN_PROGRESS) {
            throw BizException.of(GlobalErrorCodes.ILLEGAL_STATE_TRANSITION, o.getStatus().label(), "发料/退料");
        }
        return o;
    }

    private static List<QtyLine> positive(List<QtyLine> lines) {
        List<QtyLine> list = lines == null ? List.of() : lines.stream().filter(l -> l.qty() != null && l.qty().signum() > 0).toList();
        if (list.isEmpty()) throw new BizException(PurchaseErrorCodes.OS_NOTHING);
        return list;
    }

    /** 仓库确认发料出库 / 退料入库：按单据 + 用料行记录（幂等），重新汇总已发、退回 */
    @Transactional(rollbackFor = Exception.class)
    public void recordTxn(String txnType, Long stockDocId, String stockDocNo, Map<Long, BigDecimal> qtyByMaterialLine) {
        Set<Long> osIds = new java.util.HashSet<>();
        for (Map.Entry<Long, BigDecimal> e : qtyByMaterialLine.entrySet()) {
            OutsourcingMaterialDO x = materialMapper.selectById(e.getKey());
            if (x == null) continue;
            OutsourcingTxnDO t = txnMapper.selectOne(new LambdaQueryWrapper<OutsourcingTxnDO>().eq(OutsourcingTxnDO::getStockDocId, stockDocId)
                    .eq(OutsourcingTxnDO::getOutsourcingMaterialId, x.getId()).eq(OutsourcingTxnDO::getTxnType, txnType));
            if (t == null) {
                t = new OutsourcingTxnDO();
                t.setOutsourcingId(x.getOutsourcingId());
                t.setOutsourcingMaterialId(x.getId());
                t.setTxnType(txnType);
                t.setStockDocId(stockDocId);
                t.setStockDocNo(stockDocNo);
                t.setQty(e.getValue());
                t.setReversed(false);
                txnMapper.insert(t);
            } else {
                t.setQty(e.getValue());
                t.setReversed(false);
                txnMapper.updateByIdOrFail(t);
            }
            osIds.add(x.getOutsourcingId());
        }
        osIds.forEach(this::refreshMaterials);
    }

    /** 仓库反确认：标记该单据的记录，重新汇总 */
    @Transactional(rollbackFor = Exception.class)
    public void reverseTxn(Long stockDocId) {
        List<OutsourcingTxnDO> list = txnMapper.selectByStockDoc(stockDocId);
        for (OutsourcingTxnDO t : list) {
            t.setReversed(true);
            txnMapper.updateByIdOrFail(t);
        }
        list.stream().map(OutsourcingTxnDO::getOutsourcingId).distinct().forEach(this::refreshMaterials);
    }

    private void refreshMaterials(Long outsourcingId) {
        Map<String, Map<Long, BigDecimal>> sums = new HashMap<>();
        for (OutsourcingTxnDO t : txnMapper.selectByOutsourcing(outsourcingId)) {
            if (Boolean.TRUE.equals(t.getReversed())) continue;
            sums.computeIfAbsent(t.getTxnType(), k -> new HashMap<>()).merge(t.getOutsourcingMaterialId(), t.getQty(), BigDecimal::add);
        }
        for (OutsourcingMaterialDO x : materialMapper.selectByParent(outsourcingId)) {
            x.setIssuedQty(Decimals.qty(sums.getOrDefault(ISSUE, Map.of()).getOrDefault(x.getId(), BigDecimal.ZERO)));
            x.setReturnedQty(Decimals.qty(sums.getOrDefault(RETURN, Map.of()).getOrDefault(x.getId(), BigDecimal.ZERO)));
            materialMapper.updateByIdOrFail(x);
        }
    }

    /** 委外用料行 ID 是否属于某委外单（监听仓库事件时识别来源） */
    public boolean isMaterialLine(Long outsourcingMaterialId) {
        return outsourcingMaterialId != null && materialMapper.selectById(outsourcingMaterialId) != null;
    }

    // ==================== 收货（到货单回写） ====================

    /** 重新汇总委外单的收货、合格、已对账数量（到货单审核、入库确认、检验、对账时调用） */
    @Transactional(rollbackFor = Exception.class)
    public void refreshReceipts(Collection<Long> outsourcingIds) {
        Set<Long> ids = outsourcingIds.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) return;
        Map<Long, OrderLineAggRow> sums = receiptLineMapper.sumByOutsourcings(ids).stream().collect(Collectors.toMap(OrderLineAggRow::getId, r -> r));
        for (OutsourcingDO o : mapper.selectBatchIds(ids)) {
            OrderLineAggRow a = sums.get(o.getId());
            o.setReceivedQty(Decimals.qty(a == null ? BigDecimal.ZERO : PurSupport.nz(a.getReceivedQty())));
            o.setQualifiedQty(Decimals.qty(a == null ? BigDecimal.ZERO : PurSupport.nz(a.getQualifiedQty())));
            o.setStatementQty(Decimals.qty(a == null ? BigDecimal.ZERO : PurSupport.nz(a.getStatementQty())));
            mapper.updateByIdOrFail(o);
            if (o.getStatus() == DocStatus.APPROVED && o.getReceivedQty().signum() > 0) {
                support.fire(PurStateMachines.OUTSOURCING, mapper, o, BIZ_TYPE, PurAction.START, null);
            }
        }
    }

    /**
     * 到货单委外收货的校验（R03、R04）：收货数量 ≤ 委外数量 − 已收货（含其他未审核到货单占用由审核时再校验）；
     * 累计收货超过已发材料齐套数时警告。
     */
    public void checkReceive(Long outsourcingId, Long supplierId, BigDecimal baseQty, int lineNo, List<String> warnings) {
        OutsourcingDO o = getOrThrow(outsourcingId);
        if (!o.getSupplierId().equals(supplierId)) throw BizException.of(PurchaseErrorCodes.RECEIPT_ORDER_SUPPLIER, lineNo);
        if (o.getStatus() != DocStatus.APPROVED && o.getStatus() != DocStatus.IN_PROGRESS) {
            throw BizException.of(PurchaseErrorCodes.RECEIPT_ORDER_STATUS, o.getDocNo());
        }
        if (baseQty.compareTo(o.getQty().subtract(o.getReceivedQty())) > 0) throw new BizException(PurchaseErrorCodes.RECEIPT_OUTSOURCE_OVER);
        BigDecimal kit = kitQty(materialMapper.selectByParent(outsourcingId));
        if (o.getReceivedQty().add(baseQty).compareTo(kit) > 0) {
            warnings.add("第 " + lineNo + " 行收货数量超过已发材料可生产的数量（" + PurSupport.plain(kit) + "）");
        }
    }

    // ==================== 核销（R05） ====================

    /** 收齐后计算消耗与超耗；超耗 > 0 的行必须填写原因；核销后委外单完成 */
    @Transactional(rollbackFor = Exception.class)
    public void settle(Long id, List<SettleLine> lines) {
        OutsourcingDO o = executable(id);
        if (o.getReceivedQty().compareTo(o.getQty()) < 0) throw new BizException(PurchaseErrorCodes.OS_NOT_RECEIVED);
        Map<Long, String> reasons = new HashMap<>();
        if (lines != null) lines.forEach(l -> reasons.put(l.outsourcingMaterialId(), PurSupport.trim(l.lossReason())));
        List<OutsourcingMaterialDO> mats = materialMapper.selectByParent(id);
        Map<Long, MaterialDTO> ms = support.materials(mats.stream().map(OutsourcingMaterialDO::getMaterialId).toList());
        for (OutsourcingMaterialDO x : mats) {
            BigDecimal consumed = consumed(o.getQualifiedQty(), x);
            BigDecimal loss = x.getIssuedQty().subtract(x.getReturnedQty()).subtract(consumed);
            String reason = reasons.get(x.getId());
            if (loss.signum() > 0 && reason == null) {
                MaterialDTO m = ms.get(x.getMaterialId());
                throw BizException.of(PurchaseErrorCodes.OS_LOSS_REASON, m == null ? "" : m.code(), PurSupport.plain(loss));
            }
            x.setConsumedQty(consumed);
            x.setLossQty(Decimals.qty(loss));
            x.setLossReason(reason);
            materialMapper.updateByIdOrFail(x);
        }
        if (o.getStatus() == DocStatus.APPROVED) support.fire(PurStateMachines.OUTSOURCING, mapper, o, BIZ_TYPE, PurAction.START, null);
        support.fire(PurStateMachines.OUTSOURCING, mapper, o, BIZ_TYPE, PurAction.COMPLETE, "核销");
    }

    // ==================== 打印、MRP ====================

    public Map<String, Object> printData(Long id) {
        OsDetail d = detail(id);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("docNo", d.docNo());
        data.put("docDate", d.docDate());
        data.put("status", d.status());
        data.put("supplierName", d.supplierName());
        data.put("materialCode", d.materialCode());
        data.put("materialName", d.materialName());
        data.put("materialSpec", Objects.toString(d.materialSpec(), ""));
        data.put("qty", d.qty());
        data.put("uom", d.uom());
        data.put("processPrice", d.processPrice());
        data.put("currency", d.currency());
        data.put("totalAmount", d.totalAmount());
        data.put("requiredDate", d.requiredDate());
        data.put("bomNo", Objects.toString(d.bomNo(), ""));
        data.put("remark", Objects.toString(d.remark(), ""));
        data.put("materials", d.materials().stream().map(x -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("materialCode", x.materialCode());
            m.put("materialName", x.materialName());
            m.put("spec", Objects.toString(x.materialSpec(), ""));
            m.put("qtyPer", x.qtyPer());
            m.put("requiredQty", x.requiredQty());
            m.put("uom", x.uom());
            return m;
        }).toList());
        return data;
    }

    /** MRP 委外建议：每条建议一张草稿委外单 */
    @Transactional(rollbackFor = Exception.class)
    public List<Long> createFromMrp(List<MrpOutsourceSuggestion> suggestions) {
        List<Long> ids = new ArrayList<>();
        for (MrpOutsourceSuggestion s : suggestions == null ? List.<MrpOutsourceSuggestion>of() : suggestions) {
            Long supplierId = s.supplierId() != null ? s.supplierId()
                    : supplierService.defaultSupplier(s.materialId()).map(SupplierDO::getId)
                    .orElseThrow(() -> BizException.of(PurchaseErrorCodes.ORDER_SUPPLIER_REQUIRED, "MRP", 1));
            Long id = create(new OsSave(supplierId, s.materialId(), null, s.qty(), null, null, null, null,
                    s.requiredDate() == null ? LocalDate.now() : s.requiredDate(), "MRP 委外建议", null, null, null));
            OutsourcingDO o = getOrThrow(id);
            o.setMrpResultId(s.mrpResultId());
            mapper.updateByIdOrFail(o);
            ids.add(id);
        }
        return ids;
    }

    public OutsourcingDO getOrThrow(Long id) {
        OutsourcingDO o = id == null ? null : mapper.selectById(id);
        if (o == null) throw new BizException(PurchaseErrorCodes.OS_NOT_EXISTS);
        return o;
    }

    public Map<Long, OutsourcingDO> byIds(Collection<Long> ids) {
        Set<Long> set = ids.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (set.isEmpty()) return Collections.emptyMap();
        return mapper.selectBatchIds(set).stream().collect(Collectors.toMap(OutsourcingDO::getId, o -> o));
    }
}
