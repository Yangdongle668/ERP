package com.erp.module.purchase.service.price;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.common.enums.DocStatus;
import com.erp.common.exception.BizException;
import com.erp.common.exception.GlobalErrorCodes;
import com.erp.common.result.PageResult;
import com.erp.common.util.Decimals;
import com.erp.framework.datascope.DataScopes;
import com.erp.framework.excel.ExcelColumn;
import com.erp.framework.excel.ImportResult;
import com.erp.framework.excel.ImportRow;
import com.erp.module.engineering.api.category.MaterialCategoryApi;
import com.erp.module.engineering.api.material.MaterialDTO;
import com.erp.module.purchase.api.PurchaseErrorCodes;
import com.erp.module.purchase.api.supplier.SupplierStatus;
import com.erp.module.purchase.config.PurchaseModuleConfig;
import com.erp.module.purchase.controller.vo.CommonVOs.DocResult;
import com.erp.module.purchase.controller.vo.PriceVOs.AdjustDetail;
import com.erp.module.purchase.controller.vo.PriceVOs.AdjustLineResp;
import com.erp.module.purchase.controller.vo.PriceVOs.AdjustLineSave;
import com.erp.module.purchase.controller.vo.PriceVOs.AdjustQuery;
import com.erp.module.purchase.controller.vo.PriceVOs.AdjustRow;
import com.erp.module.purchase.controller.vo.PriceVOs.AdjustSave;
import com.erp.module.purchase.controller.vo.PriceVOs.EffectivePrice;
import com.erp.module.purchase.controller.vo.PriceVOs.PriceQuery;
import com.erp.module.purchase.controller.vo.PriceVOs.PriceRow;
import com.erp.module.purchase.dal.dataobject.PriceAdjustDO;
import com.erp.module.purchase.dal.dataobject.PriceAdjustLineDO;
import com.erp.module.purchase.dal.dataobject.PriceDO;
import com.erp.module.purchase.dal.dataobject.SupplierDO;
import com.erp.module.purchase.dal.dataobject.SupplierMaterialDO;
import com.erp.module.purchase.dal.mapper.PriceAdjustLineMapper;
import com.erp.module.purchase.dal.mapper.PriceAdjustMapper;
import com.erp.module.purchase.dal.mapper.PriceMapper;
import com.erp.module.purchase.service.PurAction;
import com.erp.module.purchase.service.PurStateMachines;
import com.erp.module.purchase.service.PurSupport;
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
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 采购价格（需求 07-02）：价格只能通过调价单（手工、询价定标、导入）审批后生效。
 * 取价规则（R04）：该日期有效、币别相同，取最近生效的一组阶梯中起始数量 ≤ 数量的最大一档。
 */
@Service
public class PriceService {

    public static final String BIZ_TYPE = PurchaseModuleConfig.PRICE_ADJUST;
    public static final String EFFECTIVE = "EFFECTIVE";
    public static final String EXPIRED = "EXPIRED";
    public static final String REPLACED = "REPLACED";
    /** R05：涨幅超过 10% 标红 */
    static final BigDecimal WARN_INCREASE = new BigDecimal("0.10");
    static final int MAX_FILTER_ROWS = 5000;

    public static final List<ExcelColumn<Object>> IMPORT_COLUMNS = List.of(
            ExcelColumn.input("supplierCode", "供应商编码", true, "已建档的供应商编码"),
            ExcelColumn.input("materialCode", "物料编码", true, "已启用的物料编码"),
            ExcelColumn.input("minQty", "阶梯起始数量", false, "基本单位，第一档为 0，默认 0"),
            ExcelColumn.input("newPrice", "不含税单价", true, "每基本单位，大于 0"),
            ExcelColumn.input("taxRate", "税率", false, "小数，如 0.13；默认供应商税率"),
            ExcelColumn.input("currency", "币别", false, "默认供应商币别"),
            ExcelColumn.<Object>input("effectiveFrom", "生效日期", false, "yyyy-MM-dd，默认今天").ofType(ExcelColumn.Type.DATE),
            ExcelColumn.<Object>input("effectiveTo", "失效日期", false, "yyyy-MM-dd，空表示长期").ofType(ExcelColumn.Type.DATE),
            ExcelColumn.input("remark", "备注", false, null));

    private final PriceMapper priceMapper;
    private final PriceAdjustMapper adjustMapper;
    private final PriceAdjustLineMapper lineMapper;
    private final SupplierService supplierService;
    private final PurSupport support;
    private final CurrencyApi currencyApi;
    private final MaterialCategoryApi categoryApi;
    private final WorkflowApi workflowApi;
    private final FileApi fileApi;

    public PriceService(PriceMapper priceMapper, PriceAdjustMapper adjustMapper, PriceAdjustLineMapper lineMapper, SupplierService supplierService,
                        PurSupport support, CurrencyApi currencyApi, MaterialCategoryApi categoryApi, WorkflowApi workflowApi, FileApi fileApi) {
        this.priceMapper = priceMapper;
        this.adjustMapper = adjustMapper;
        this.lineMapper = lineMapper;
        this.supplierService = supplierService;
        this.support = support;
        this.currencyApi = currencyApi;
        this.categoryApi = categoryApi;
        this.workflowApi = workflowApi;
        this.fileApi = fileApi;
    }

    // ==================== 取价 ====================

    /** R04：取价（数量为基本单位） */
    public Optional<PriceDO> effective(Long supplierId, Long materialId, BigDecimal baseQty, LocalDate date, String currency) {
        if (supplierId == null || materialId == null || !StringUtils.hasText(currency)) return Optional.empty();
        LocalDate d = date == null ? LocalDate.now() : date;
        List<PriceDO> valid = priceMapper.selectList(new LambdaQueryWrapper<PriceDO>().eq(PriceDO::getSupplierId, supplierId)
                .eq(PriceDO::getMaterialId, materialId).eq(PriceDO::getCurrency, currency).le(PriceDO::getEffectiveFrom, d)
                .and(x -> x.isNull(PriceDO::getEffectiveTo).or().ge(PriceDO::getEffectiveTo, d)));
        return pickTier(valid, baseQty);
    }

    /** 最近生效的一组阶梯（生效日期最大、同一调价单）中起始数量 ≤ 数量的最大一档 */
    static Optional<PriceDO> pickTier(List<PriceDO> valid, BigDecimal baseQty) {
        if (valid.isEmpty()) return Optional.empty();
        PriceDO newest = valid.stream().max(Comparator.comparing(PriceDO::getEffectiveFrom).thenComparing(PriceDO::getAdjustId)).orElseThrow();
        BigDecimal qty = baseQty == null ? BigDecimal.ZERO : baseQty;
        return valid.stream().filter(p -> p.getEffectiveFrom().equals(newest.getEffectiveFrom()) && p.getAdjustId().equals(newest.getAdjustId()))
                .filter(p -> p.getMinQty().compareTo(qty) <= 0).max(Comparator.comparing(PriceDO::getMinQty));
    }

    /** 按业务单位取价：价格表为每基本单位，换算为每业务单位（数量为空时按 1 个业务单位取第一档） */
    public EffectivePrice effectiveForUom(Long supplierId, Long materialId, BigDecimal qty, String uom, LocalDate date, String currency) {
        MaterialDTO m = support.material(materialId);
        String u = StringUtils.hasText(uom) ? uom : m.baseUom();
        BigDecimal q = qty == null || qty.signum() <= 0 ? BigDecimal.ONE : qty;
        BigDecimal base = support.toBase(materialId, q, u);
        BigDecimal factor = base.divide(q, 10, RoundingMode.HALF_UP);
        Optional<PriceDO> p = effective(supplierId, materialId, qty == null ? BigDecimal.ZERO : base, date, currency);
        return p.map(x -> new EffectivePrice(x.getId(), Decimals.price(x.getPrice().multiply(factor)), Decimals.price(x.getPriceInclTax().multiply(factor)),
                x.getTaxRate(), x.getPrice(), x.getMinQty(), x.getCurrency(), x.getEffectiveFrom(), x.getEffectiveTo())).orElse(null);
    }

    /** 物料最近生效的价格（任意供应商，第一档） */
    public Optional<PriceDO> latest(Long materialId) {
        return priceMapper.selectList(new LambdaQueryWrapper<PriceDO>().eq(PriceDO::getMaterialId, materialId).eq(PriceDO::getPriceStatus, EFFECTIVE)
                        .le(PriceDO::getEffectiveFrom, LocalDate.now()).orderByDesc(PriceDO::getEffectiveFrom).orderByDesc(PriceDO::getId))
                .stream().min(Comparator.comparing(PriceDO::getEffectiveFrom).reversed().thenComparing(PriceDO::getMinQty));
    }

    // ==================== 价格列表 ====================

    public PageResult<PriceRow> page(PriceQuery q) {
        LambdaQueryWrapper<PriceDO> w = priceQuery(q);
        if (w == null) return PageResult.empty();
        if (q.getCategoryId() == null) {
            PageResult<PriceDO> page = priceMapper.selectPage(q, w);
            return new PageResult<>(priceRows(page.list()), page.total());
        }
        List<PriceDO> all = filterCategory(priceMapper.selectList(w.last("LIMIT " + MAX_FILTER_ROWS)), q.getCategoryId());
        int from = Math.min((q.getPageNo() - 1) * q.getPageSize(), all.size());
        return new PageResult<>(priceRows(all.subList(from, Math.min(from + q.getPageSize(), all.size()))), all.size());
    }

    public List<PriceRow> listForExport(PriceQuery q, int limit) {
        LambdaQueryWrapper<PriceDO> w = priceQuery(q);
        if (w == null) return List.of();
        List<PriceDO> list = priceMapper.selectList(w.last("LIMIT " + (q.getCategoryId() == null ? limit : MAX_FILTER_ROWS)));
        if (q.getCategoryId() != null) list = filterCategory(list, q.getCategoryId());
        return priceRows(list.size() > limit ? list.subList(0, limit) : list);
    }

    private List<PriceDO> filterCategory(List<PriceDO> list, Long categoryId) {
        Set<Long> cats = new HashSet<>(categoryApi.getDescendantIds(categoryId));
        Map<Long, MaterialDTO> ms = support.materials(list.stream().map(PriceDO::getMaterialId).toList());
        return list.stream().filter(p -> ms.containsKey(p.getMaterialId()) && cats.contains(ms.get(p.getMaterialId()).categoryId())).toList();
    }

    private LambdaQueryWrapper<PriceDO> priceQuery(PriceQuery q) {
        LambdaQueryWrapper<PriceDO> w = new LambdaQueryWrapper<PriceDO>()
                .eq(q.getSupplierId() != null, PriceDO::getSupplierId, q.getSupplierId())
                .eq(q.getMaterialId() != null, PriceDO::getMaterialId, q.getMaterialId())
                .ge(q.getDateFrom() != null, PriceDO::getEffectiveFrom, q.getDateFrom())
                .le(q.getDateTo() != null, PriceDO::getEffectiveFrom, q.getDateTo());
        List<String> statuses = StringUtils.hasText(q.getStatuses()) ? Arrays.stream(q.getStatuses().split(",")).map(String::trim).toList() : List.of(EFFECTIVE);
        w.in(PriceDO::getPriceStatus, statuses);
        if (StringUtils.hasText(q.getKeyword())) {
            List<Long> ids = support.materialApi().search(q.getKeyword().trim(), null, 50).stream().map(MaterialDTO::id).toList();
            if (ids.isEmpty()) return null;
            w.in(PriceDO::getMaterialId, ids);
        }
        return w.orderByAsc(PriceDO::getSupplierId).orderByAsc(PriceDO::getMaterialId).orderByDesc(PriceDO::getEffectiveFrom).orderByAsc(PriceDO::getMinQty);
    }

    private List<PriceRow> priceRows(List<PriceDO> list) {
        if (list.isEmpty()) return List.of();
        Map<Long, MaterialDTO> ms = support.materials(list.stream().map(PriceDO::getMaterialId).toList());
        Map<Long, SupplierDO> ss = supplierService.byIds(list.stream().map(PriceDO::getSupplierId).toList());
        Map<Long, String> adjustNos = adjustMapper.selectBatchIds(list.stream().map(PriceDO::getAdjustId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(PriceAdjustDO::getId, PriceAdjustDO::getDocNo));
        return list.stream().map(p -> {
            MaterialDTO m = ms.get(p.getMaterialId());
            SupplierDO s = ss.get(p.getSupplierId());
            return new PriceRow(p.getId(), p.getSupplierId(), s == null ? null : s.getCode(), s == null ? null : s.getShortName(), p.getMaterialId(),
                    m == null ? null : m.code(), m == null ? null : m.name(), m == null ? null : m.spec(), m == null ? null : m.baseUom(),
                    p.getCurrency(), p.getMinQty(), p.getPrice(), p.getTaxRate(), p.getPriceInclTax(), p.getEffectiveFrom(), p.getEffectiveTo(),
                    p.getPriceStatus(), p.getAdjustId(), adjustNos.get(p.getAdjustId()), p.getUpdatedAt());
        }).toList();
    }

    /** 历史价格：该供应商该物料的全部价格记录 */
    public List<PriceRow> history(Long supplierId, Long materialId) {
        return priceRows(priceMapper.selectList(new LambdaQueryWrapper<PriceDO>().eq(PriceDO::getSupplierId, supplierId).eq(PriceDO::getMaterialId, materialId)
                .orderByDesc(PriceDO::getEffectiveFrom).orderByDesc(PriceDO::getId).orderByAsc(PriceDO::getMinQty)));
    }

    public boolean hasPrices(Long supplierId) {
        return priceMapper.selectCount(new LambdaQueryWrapper<PriceDO>().eq(PriceDO::getSupplierId, supplierId)) > 0;
    }

    // ==================== 调价单 ====================

    public PageResult<AdjustRow> adjustPage(AdjustQuery q) {
        LambdaQueryWrapper<PriceAdjustDO> w = new LambdaQueryWrapper<PriceAdjustDO>().eq(PriceAdjustDO::getDeleted, false)
                .likeRight(StringUtils.hasText(q.getDocNo()), PriceAdjustDO::getDocNo, q.getDocNo() == null ? null : q.getDocNo().trim().toUpperCase())
                .eq(q.getSupplierId() != null, PriceAdjustDO::getSupplierId, q.getSupplierId())
                .ge(q.getDateFrom() != null, PriceAdjustDO::getDocDate, q.getDateFrom())
                .le(q.getDateTo() != null, PriceAdjustDO::getDocDate, q.getDateTo());
        if (StringUtils.hasText(q.getStatuses())) {
            w.in(PriceAdjustDO::getStatus, Arrays.stream(q.getStatuses().split(",")).map(String::trim).map(DocStatus::valueOf).toList());
        }
        if (q.getMaterialId() != null) {
            w.inSql(PriceAdjustDO::getId, "SELECT adjust_id FROM pur_price_adjust_line WHERE deleted = 0 AND material_id = " + q.getMaterialId().longValue());
        }
        w.orderByDesc(PriceAdjustDO::getDocDate).orderByDesc(PriceAdjustDO::getId);
        IPage<PriceAdjustDO> page = adjustMapper.selectScopedPage(Page.of(q.getPageNo(), q.getPageSize()), w);
        List<PriceAdjustDO> list = page.getRecords();
        Map<Long, List<PriceAdjustLineDO>> lines = lineMapper.selectByParents(list.stream().map(PriceAdjustDO::getId).toList()).stream()
                .collect(Collectors.groupingBy(PriceAdjustLineDO::getAdjustId));
        Map<Long, SupplierDO> ss = supplierService.byIds(list.stream().map(PriceAdjustDO::getSupplierId).toList());
        Map<Long, UserDTO> users = support.users(list.stream().map(PriceAdjustDO::getOwnerId).toList());
        return new PageResult<>(list.stream().map(a -> {
            List<PriceAdjustLineDO> ls = lines.getOrDefault(a.getId(), List.of());
            BigDecimal max = ls.stream().map(PriceAdjustLineDO::getChangePct).filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(null);
            SupplierDO s = ss.get(a.getSupplierId());
            return new AdjustRow(a.getId(), a.getDocNo(), a.getDocDate(), a.getSupplierId(), s == null ? null : s.getShortName(), a.getCurrency(),
                    a.getAdjustReason(), a.getAdjustSource(), ls.size(), max, a.getStatus().name(), PurSupport.name(users, a.getOwnerId()), a.getCreatedAt());
        }).toList(), page.getTotal());
    }

    public AdjustDetail adjustDetail(Long id) {
        PriceAdjustDO a = getOrThrow(id);
        DataScopes.check(a.getOrgId(), a.getDeptId(), a.getOwnerId(), "调价单");
        SupplierDO s = supplierService.getOrThrow(a.getSupplierId());
        List<PriceAdjustLineDO> lines = lineMapper.selectByParent(id);
        Map<Long, MaterialDTO> ms = support.materials(lines.stream().map(PriceAdjustLineDO::getMaterialId).toList());
        String rfqNo = a.getRfqId() == null ? null : a.getSourceNo();
        return new AdjustDetail(a.getId(), a.getDocNo(), a.getDocDate(), a.getStatus().name(), a.getSupplierId(), s.getShortName(),
                s.getSupplierStatus().name(), a.getCurrency(), a.getAdjustReason(), a.getAdjustSource(), a.getRfqId(), rfqNo, a.getRemark(),
                support.userName(a.getOwnerId()), a.getCreatedAt(), a.getVersion(), lines.stream().map(l -> {
                    MaterialDTO m = ms.get(l.getMaterialId());
                    return new AdjustLineResp(l.getId(), l.getLineNo(), l.getMaterialId(), m == null ? null : m.code(), m == null ? null : m.name(),
                            m == null ? null : m.spec(), m == null ? null : m.baseUom(), l.getMinQty(), l.getOldPrice(), l.getNewPrice(), l.getTaxRate(),
                            l.getChangePct(), l.getEffectiveFrom(), l.getEffectiveTo(), l.getRemark(),
                            l.getChangePct() != null && l.getChangePct().compareTo(WARN_INCREASE) > 0);
                }).toList());
    }

    @Transactional(rollbackFor = Exception.class)
    public Long createAdjust(AdjustSave req) {
        return createAdjust(req, "MANUAL", null, null);
    }

    @Transactional(rollbackFor = Exception.class)
    public Long createAdjust(AdjustSave req, String source, Long rfqId, String rfqNo) {
        SupplierDO s = checkSupplier(req.supplierId());
        PriceAdjustDO a = new PriceAdjustDO();
        a.setDocNo(support.nextNo(BIZ_TYPE));
        a.setDocDate(LocalDate.now());
        a.setStatus(DocStatus.DRAFT);
        a.setAdjustSource(source);
        a.setRfqId(rfqId);
        if (rfqId != null) {
            a.setSourceType(PurchaseModuleConfig.RFQ);
            a.setSourceId(rfqId);
            a.setSourceNo(rfqNo);
        }
        support.fillOwner(a, null);
        fillHeader(a, s, req);
        adjustMapper.insert(a);
        saveLines(a, s, req.lines());
        if (req.fileIds() != null && !req.fileIds().isEmpty()) fileApi.bind(req.fileIds(), BIZ_TYPE, a.getId());
        return a.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateAdjust(Long id, AdjustSave req) {
        PriceAdjustDO a = getOrThrow(id);
        DataScopes.check(a.getOrgId(), a.getDeptId(), a.getOwnerId(), "调价单");
        PurSupport.requireDraft(a);
        if (req.version() != null) a.setVersion(req.version());
        SupplierDO s = checkSupplier(req.supplierId());
        fillHeader(a, s, req);
        adjustMapper.updateByIdOrFail(a);
        saveLines(a, s, req.lines());
        if (req.fileIds() != null && !req.fileIds().isEmpty()) fileApi.bind(req.fileIds(), BIZ_TYPE, a.getId());
    }

    private SupplierDO checkSupplier(Long supplierId) {
        SupplierDO s = supplierService.getOrThrow(supplierId);
        if (s.getSupplierStatus() == SupplierStatus.SUSPENDED || s.getSupplierStatus() == SupplierStatus.ELIMINATED) {
            throw new BizException(PurchaseErrorCodes.PRICE_SUPPLIER_STATUS);
        }
        return s;
    }

    private void fillHeader(PriceAdjustDO a, SupplierDO s, AdjustSave req) {
        String currency = StringUtils.hasText(req.currency()) ? req.currency().trim().toUpperCase() : s.getCurrency();
        currencyApi.validate(currency);
        a.setSupplierId(s.getId());
        a.setCurrency(currency);
        a.setAdjustReason(req.adjustReason().trim());
        a.setRemark(PurSupport.trim(req.remark()));
    }

    private void saveLines(PriceAdjustDO a, SupplierDO s, List<AdjustLineSave> lines) {
        if (lines == null || lines.isEmpty()) throw new BizException(PurchaseErrorCodes.DOC_NO_LINES);
        Map<Long, MaterialDTO> ms = support.materials(lines.stream().map(AdjustLineSave::materialId).toList());
        Map<Long, SupplierMaterialDO> supplied = new HashMap<>();
        lineMapper.deleteByParent(a.getId());
        boolean allowOther = support.params().getBool(PurchaseModuleConfig.P_PRICE_OTHER_MATERIAL) || !"MANUAL".equals(a.getAdjustSource());
        int no = 0;
        for (AdjustLineSave l : lines) {
            no++;
            MaterialDTO m = ms.get(l.materialId());
            if (m == null) throw BizException.of(GlobalErrorCodes.DATA_NOT_EXISTS, "物料");
            SupplierMaterialDO sm = supplied.computeIfAbsent(m.id(), k -> supplierService.supplierMaterial(s.getId(), k).orElse(null));
            if (sm == null || "DISABLED".equals(sm.getSupplyStatus())) {
                if (!allowOther || sm != null) throw BizException.of(PurchaseErrorCodes.PRICE_MATERIAL_NOT_SUPPLIED, m.code(), s.getShortName());
                supplierService.ensureMaterial(s.getId(), m.id());
            }
            if (l.newPrice() == null || l.newPrice().signum() <= 0) throw BizException.of(PurchaseErrorCodes.PRICE_NOT_POSITIVE, no);
            LocalDate from = l.effectiveFrom() != null ? l.effectiveFrom() : LocalDate.now();
            if (l.effectiveTo() != null && l.effectiveTo().isBefore(from)) throw BizException.of(PurchaseErrorCodes.PRICE_EFFECTIVE_RANGE, no);
            PriceAdjustLineDO d = new PriceAdjustLineDO();
            d.setAdjustId(a.getId());
            d.setLineNo(no);
            d.setMaterialId(m.id());
            d.setMinQty(Decimals.qty(l.minQty() == null ? BigDecimal.ZERO : l.minQty()));
            d.setNewPrice(Decimals.price(l.newPrice()));
            d.setTaxRate(l.taxRate() != null ? l.taxRate() : s.getPurchaseTaxRate());
            d.setEffectiveFrom(from);
            d.setEffectiveTo(l.effectiveTo());
            d.setRemark(PurSupport.trim(l.remark()));
            // 原价：当前该阶梯的有效价（系统带出）
            BigDecimal old = effective(s.getId(), m.id(), d.getMinQty(), LocalDate.now(), a.getCurrency()).map(PriceDO::getPrice).orElse(null);
            d.setOldPrice(old);
            d.setChangePct(old == null || old.signum() == 0 ? null : d.getNewPrice().subtract(old).divide(old, 4, RoundingMode.HALF_UP));
            lineMapper.insert(d);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteAdjust(Long id) {
        PriceAdjustDO a = getOrThrow(id);
        DataScopes.check(a.getOrgId(), a.getDeptId(), a.getOwnerId(), "调价单");
        PurSupport.requireDraft(a);
        lineMapper.deleteByParent(id);
        adjustMapper.deleteById(id);
        fileApi.deleteByBiz(BIZ_TYPE, id);
    }

    @Transactional(rollbackFor = Exception.class)
    public void voidAdjust(Long id, String reason) {
        PriceAdjustDO a = getOrThrow(id);
        DataScopes.check(a.getOrgId(), a.getDeptId(), a.getOwnerId(), "调价单");
        support.fire(PurStateMachines.SIMPLE, adjustMapper, a, BIZ_TYPE, PurAction.VOID, PurSupport.trim(reason));
    }

    /** 提交：R01 阶梯校验、生效日期不早于今天；审批变量 maxIncreasePct；未配置审批流直接生效 */
    @Transactional(rollbackFor = Exception.class)
    public DocResult submit(Long id) {
        PriceAdjustDO a = getOrThrow(id);
        DataScopes.check(a.getOrgId(), a.getDeptId(), a.getOwnerId(), "调价单");
        PurSupport.requireDraft(a);
        List<PriceAdjustLineDO> lines = lineMapper.selectByParent(id);
        if (lines.isEmpty()) throw new BizException(PurchaseErrorCodes.DOC_NO_LINES);
        Map<Long, MaterialDTO> ms = support.materials(lines.stream().map(PriceAdjustLineDO::getMaterialId).toList());
        Map<Long, List<PriceAdjustLineDO>> byMaterial = lines.stream().collect(Collectors.groupingBy(PriceAdjustLineDO::getMaterialId, LinkedHashMap::new, Collectors.toList()));
        LocalDate today = LocalDate.now();
        for (PriceAdjustLineDO l : lines) {
            if (l.getEffectiveFrom().isBefore(today)) throw BizException.of(PurchaseErrorCodes.PRICE_EFFECTIVE_PAST, l.getLineNo());
        }
        for (Map.Entry<Long, List<PriceAdjustLineDO>> e : byMaterial.entrySet()) {
            String code = ms.containsKey(e.getKey()) ? ms.get(e.getKey()).code() : String.valueOf(e.getKey());
            Set<BigDecimal> tiers = new HashSet<>();
            for (PriceAdjustLineDO l : e.getValue()) {
                if (!tiers.add(l.getMinQty().stripTrailingZeros())) throw BizException.of(PurchaseErrorCodes.PRICE_TIER_DUPLICATE, code, PurSupport.plain(l.getMinQty()));
            }
            if (e.getValue().stream().noneMatch(l -> l.getMinQty().signum() == 0)) throw BizException.of(PurchaseErrorCodes.PRICE_ZERO_TIER, code);
        }
        List<String> warnings = new ArrayList<>();
        for (PriceAdjustLineDO l : lines) {
            if (l.getChangePct() != null && l.getChangePct().compareTo(WARN_INCREASE) > 0) {
                warnings.add("第 " + l.getLineNo() + " 行涨幅 " + PurSupport.pctText(l.getChangePct()) + "%，超过 10%");
            }
        }
        BigDecimal maxPct = lines.stream().map(PriceAdjustLineDO::getChangePct).filter(Objects::nonNull).max(Comparator.naturalOrder())
                .orElse(BigDecimal.ZERO).multiply(PurSupport.HUNDRED).setScale(2, RoundingMode.HALF_UP);
        support.fire(PurStateMachines.SIMPLE, adjustMapper, a, BIZ_TYPE, PurAction.SUBMIT, null);
        Map<String, Object> vars = new HashMap<>();
        vars.put("maxIncreasePct", maxPct);
        vars.put("amountImpactBase", BigDecimal.ZERO);
        SupplierDO s = supplierService.getOrThrow(a.getSupplierId());
        StartResult r = workflowApi.start(BIZ_TYPE, id, a.getDocNo(), "调价单 " + a.getDocNo() + " " + s.getShortName(), vars, Map.of(), support.currentUser());
        if (!r.isStarted()) approve(a);
        return new DocResult(a.getStatus().name(), warnings);
    }

    @EventListener
    public void onApproval(ApprovalCompletedEvent e) {
        if (!BIZ_TYPE.equals(e.getBizType())) return;
        PriceAdjustDO a = getOrThrow(e.getBizId());
        if (a.getStatus() != DocStatus.PENDING_APPROVAL) return;
        switch (e.getResult()) {
            case APPROVED -> approve(a);
            case WITHDRAWN -> support.fire(PurStateMachines.SIMPLE, adjustMapper, a, BIZ_TYPE, PurAction.WITHDRAW, null);
            default -> support.fire(PurStateMachines.SIMPLE, adjustMapper, a, BIZ_TYPE, PurAction.REJECT, e.getComment());
        }
    }

    /** R02：审核生效。生效日期 ≤ 今天的新价格立即替代同一供应商 + 物料 + 币别的原有效价格 */
    private void approve(PriceAdjustDO a) {
        support.fire(PurStateMachines.SIMPLE, adjustMapper, a, BIZ_TYPE, PurAction.APPROVE, null);
        List<PriceAdjustLineDO> lines = lineMapper.selectByParent(a.getId());
        Set<Long> newIds = new HashSet<>();
        for (PriceAdjustLineDO l : lines) {
            PriceDO p = new PriceDO();
            p.setSupplierId(a.getSupplierId());
            p.setMaterialId(l.getMaterialId());
            p.setCurrency(a.getCurrency());
            p.setMinQty(l.getMinQty());
            p.setPrice(l.getNewPrice());
            p.setTaxRate(l.getTaxRate());
            p.setPriceInclTax(Decimals.price(l.getNewPrice().multiply(BigDecimal.ONE.add(l.getTaxRate()))));
            p.setEffectiveFrom(l.getEffectiveFrom());
            p.setEffectiveTo(l.getEffectiveTo());
            p.setPriceStatus(EFFECTIVE);
            p.setAdjustId(a.getId());
            p.setAdjustLineId(l.getId());
            priceMapper.insert(p);
            newIds.add(p.getId());
        }
        LocalDate today = LocalDate.now();
        Map<Long, LocalDate> fromByMaterial = new HashMap<>();
        lines.forEach(l -> fromByMaterial.merge(l.getMaterialId(), l.getEffectiveFrom(), (x, y) -> x.isBefore(y) ? x : y));
        fromByMaterial.forEach((materialId, from) -> {
            if (!from.isAfter(today)) replaceOld(a.getSupplierId(), materialId, a.getCurrency(), from, newIds);
        });
    }

    private void replaceOld(Long supplierId, Long materialId, String currency, LocalDate newFrom, Set<Long> keepIds) {
        for (PriceDO old : priceMapper.selectList(new LambdaQueryWrapper<PriceDO>().eq(PriceDO::getSupplierId, supplierId)
                .eq(PriceDO::getMaterialId, materialId).eq(PriceDO::getCurrency, currency).eq(PriceDO::getPriceStatus, EFFECTIVE)
                .le(PriceDO::getEffectiveFrom, newFrom))) {
            if (keepIds.contains(old.getId())) continue;
            old.setEffectiveTo(newFrom.minusDays(1));
            old.setPriceStatus(REPLACED);
            priceMapper.updateByIdOrFail(old);
        }
    }

    /**
     * R03 定时任务：失效日期早于今天的价格置为过期；到达生效日的新价格替代旧价格。
     *
     * @return 处理说明
     */
    @Transactional(rollbackFor = Exception.class)
    public String refreshDaily() {
        LocalDate today = LocalDate.now();
        int expired = 0;
        for (PriceDO p : priceMapper.selectList(new LambdaQueryWrapper<PriceDO>().eq(PriceDO::getPriceStatus, EFFECTIVE).lt(PriceDO::getEffectiveTo, today))) {
            p.setPriceStatus(EXPIRED);
            priceMapper.updateByIdOrFail(p);
            expired++;
        }
        List<PriceDO> effective = priceMapper.selectList(new LambdaQueryWrapper<PriceDO>().eq(PriceDO::getPriceStatus, EFFECTIVE).le(PriceDO::getEffectiveFrom, today));
        Map<String, List<PriceDO>> groups = effective.stream().collect(Collectors.groupingBy(p -> p.getSupplierId() + "|" + p.getMaterialId() + "|" + p.getCurrency()));
        int replaced = 0;
        for (List<PriceDO> g : groups.values()) {
            PriceDO newest = g.stream().max(Comparator.comparing(PriceDO::getEffectiveFrom).thenComparing(PriceDO::getAdjustId)).orElseThrow();
            for (PriceDO p : g) {
                if (p.getAdjustId().equals(newest.getAdjustId())) continue;
                p.setEffectiveTo(newest.getEffectiveFrom().minusDays(1));
                p.setPriceStatus(REPLACED);
                priceMapper.updateByIdOrFail(p);
                replaced++;
            }
        }
        return "过期 " + expired + " 条，被替代 " + replaced + " 条";
    }

    // ==================== 导入 ====================

    /** 导入价格：校验每行，返回每行动作说明（生成的调价单分组） */
    public Map<Integer, String> checkImport(List<ImportRow> rows) {
        Map<Integer, String> actions = new HashMap<>();
        Map<String, SupplierDO> suppliers = new HashMap<>();
        Map<String, MaterialDTO> materials = new HashMap<>();
        for (ImportRow r : rows) {
            SupplierDO s = r.get("supplierCode") == null ? null : suppliers.computeIfAbsent(r.get("supplierCode").toUpperCase(), this::supplierByCode);
            if (s == null) r.error("供应商编码「" + Objects.toString(r.get("supplierCode"), "") + "」不存在");
            else if (s.getSupplierStatus() == SupplierStatus.SUSPENDED || s.getSupplierStatus() == SupplierStatus.ELIMINATED) r.error("供应商已暂停或淘汰");
            MaterialDTO m = r.get("materialCode") == null ? null : materials.computeIfAbsent(r.get("materialCode").toUpperCase(), this::materialByCode);
            if (m == null) r.error("物料编码「" + Objects.toString(r.get("materialCode"), "") + "」不存在或未启用");
            BigDecimal price = decimal(r, "newPrice");
            if (price == null || price.signum() <= 0) r.error("不含税单价必须大于 0");
            BigDecimal min = decimal(r, "minQty");
            if (min != null && min.signum() < 0) r.error("阶梯起始数量不能为负数");
            BigDecimal rate = decimal(r, "taxRate");
            if (rate != null && (rate.signum() < 0 || rate.compareTo(BigDecimal.ONE) >= 0)) r.error("税率应为 0～1 之间的小数");
            LocalDate from = date(r, "effectiveFrom");
            LocalDate to = date(r, "effectiveTo");
            if (from != null && from.isBefore(LocalDate.now())) r.error("生效日期不能早于今天");
            if (from != null && to != null && to.isBefore(from)) r.error("失效日期不能早于生效日期");
            if (r.get("currency") != null && currencyApi.get(r.get("currency").toUpperCase()).isEmpty()) r.error("币别不存在");
            if (!r.hasError() && s != null) actions.put(r.rowNo(), "调价单：" + s.getShortName());
        }
        return actions;
    }

    /** 导入：按供应商 + 币别生成草稿调价单（来源 IMPORT） */
    @Transactional(rollbackFor = Exception.class)
    public ImportResult doImport(List<ImportRow> rows) {
        Map<String, List<ImportRow>> groups = new LinkedHashMap<>();
        Map<String, SupplierDO> suppliers = new HashMap<>();
        for (ImportRow r : rows) {
            SupplierDO s = suppliers.computeIfAbsent(r.get("supplierCode").toUpperCase(), this::supplierByCode);
            String currency = r.get("currency") != null ? r.get("currency").toUpperCase() : s.getCurrency();
            groups.computeIfAbsent(s.getId() + "|" + currency, k -> new ArrayList<>()).add(r);
        }
        int n = 0;
        for (Map.Entry<String, List<ImportRow>> e : groups.entrySet()) {
            String[] key = e.getKey().split("\\|");
            List<AdjustLineSave> lines = e.getValue().stream().map(r -> new AdjustLineSave(materialByCode(r.get("materialCode").toUpperCase()).id(),
                    decimal(r, "minQty"), decimal(r, "newPrice"), decimal(r, "taxRate"), date(r, "effectiveFrom"), date(r, "effectiveTo"), r.get("remark"))).toList();
            createAdjust(new AdjustSave(Long.valueOf(key[0]), key[1], "导入价格", null, lines, null, null), "IMPORT", null, null);
            n += e.getValue().size();
        }
        return new ImportResult(n, 0, List.of());
    }

    private SupplierDO supplierByCode(String code) {
        return supplierService.searchDto(code, null, 50).stream().filter(d -> d.code().equalsIgnoreCase(code)).findFirst()
                .map(d -> supplierService.getOrThrow(d.id())).orElse(null);
    }

    private MaterialDTO materialByCode(String code) {
        return support.materialApi().search(code, null, 50).stream().filter(m -> m.code().equalsIgnoreCase(code)).findFirst().orElse(null);
    }

    private static BigDecimal decimal(ImportRow r, String key) {
        String v = r.get(key);
        if (v == null) return null;
        try {
            return new BigDecimal(v.replace(",", ""));
        } catch (NumberFormatException e) {
            r.error(key + " 不是数字");
            return null;
        }
    }

    private static LocalDate date(ImportRow r, String key) {
        String v = r.get(key);
        if (v == null) return null;
        try {
            return LocalDate.parse(v.length() > 10 ? v.substring(0, 10) : v);
        } catch (RuntimeException e) {
            r.error("日期格式应为 yyyy-MM-dd");
            return null;
        }
    }

    public PriceAdjustDO getOrThrow(Long id) {
        PriceAdjustDO a = id == null ? null : adjustMapper.selectById(id);
        if (a == null) throw new BizException(PurchaseErrorCodes.PRICE_ADJUST_NOT_EXISTS);
        return a;
    }
}
