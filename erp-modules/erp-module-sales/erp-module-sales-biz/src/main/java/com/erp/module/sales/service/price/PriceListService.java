package com.erp.module.sales.service.price;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.common.enums.DocStatus;
import com.erp.common.exception.BizException;
import com.erp.common.exception.GlobalErrorCodes;
import com.erp.common.result.PageResult;
import com.erp.common.util.Decimals;
import com.erp.framework.excel.ImportResult;
import com.erp.framework.excel.ImportRow;
import com.erp.module.crm.api.customer.CustomerDTO;
import com.erp.module.engineering.api.material.MaterialDTO;
import com.erp.module.sales.api.SalesErrorCodes;
import com.erp.module.sales.config.SalesModuleConfig;
import com.erp.module.sales.controller.vo.CommonVOs.DocResult;
import com.erp.module.sales.controller.vo.PriceListVOs.ItemExportRow;
import com.erp.module.sales.controller.vo.PriceListVOs.ItemResp;
import com.erp.module.sales.controller.vo.PriceListVOs.ItemSave;
import com.erp.module.sales.controller.vo.PriceListVOs.PriceListDetail;
import com.erp.module.sales.controller.vo.PriceListVOs.PriceListQuery;
import com.erp.module.sales.controller.vo.PriceListVOs.PriceListRow;
import com.erp.module.sales.controller.vo.PriceListVOs.PriceListSave;
import com.erp.module.sales.dal.dataobject.SalPriceListDO;
import com.erp.module.sales.dal.dataobject.SalPriceListItemDO;
import com.erp.module.sales.dal.mapper.SalPriceListItemMapper;
import com.erp.module.sales.dal.mapper.SalPriceListMapper;
import com.erp.module.sales.service.CostService;
import com.erp.module.sales.service.SalAction;
import com.erp.module.sales.service.SalStateMachines;
import com.erp.module.sales.service.SalSupport;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** 销售价格表（需求 04-01）：草稿 → 待审批 → 已审核（生效）→ 已关闭 */
@Service("salPriceListService")
public class PriceListService {

    public static final String BIZ_TYPE = SalesModuleConfig.PRICE_LIST;
    static final Set<String> SCOPES = Set.of("CUSTOMER", "LEVEL", "ALL");

    private final SalPriceListMapper mapper;
    private final SalPriceListItemMapper itemMapper;
    private final SalSupport support;
    private final CostService costService;
    private final WorkflowApi workflowApi;

    public PriceListService(SalPriceListMapper mapper, SalPriceListItemMapper itemMapper, SalSupport support, CostService costService,
                            WorkflowApi workflowApi) {
        this.mapper = mapper;
        this.itemMapper = itemMapper;
        this.support = support;
        this.costService = costService;
        this.workflowApi = workflowApi;
    }

    // ==================== 查询 ====================

    public PageResult<PriceListRow> page(PriceListQuery q) {
        Page<SalPriceListDO> page = mapper.selectPage(Page.of(q.getPageNo(), q.getPageSize()), query(q));
        return new PageResult<>(rows(page.getRecords()), page.getTotal());
    }

    private LambdaQueryWrapper<SalPriceListDO> query(PriceListQuery q) {
        LambdaQueryWrapper<SalPriceListDO> w = new LambdaQueryWrapper<SalPriceListDO>()
                .eq(StringUtils.hasText(q.getScope()), SalPriceListDO::getScope, q.getScope())
                .eq(q.getCustomerId() != null, SalPriceListDO::getCustomerId, q.getCustomerId())
                .eq(StringUtils.hasText(q.getCustomerLevel()), SalPriceListDO::getCustomerLevel, q.getCustomerLevel())
                .eq(StringUtils.hasText(q.getCurrency()), SalPriceListDO::getCurrency, q.getCurrency());
        if (StringUtils.hasText(q.getKeyword())) {
            String k = q.getKeyword().trim();
            w.and(x -> x.like(SalPriceListDO::getName, k).or().likeRight(SalPriceListDO::getDocNo, k.toUpperCase()));
        }
        if (StringUtils.hasText(q.getStatuses())) {
            w.in(SalPriceListDO::getStatus, Arrays.stream(q.getStatuses().split(",")).map(String::trim).map(DocStatus::valueOf).toList());
        }
        if (q.getEffectiveOn() != null) {
            w.le(SalPriceListDO::getEffectiveFrom, q.getEffectiveOn())
                    .and(x -> x.isNull(SalPriceListDO::getEffectiveTo).or().ge(SalPriceListDO::getEffectiveTo, q.getEffectiveOn()));
        }
        if (q.getMaterialId() != null) {
            w.inSql(SalPriceListDO::getId, "SELECT price_list_id FROM sal_price_list_item WHERE deleted = 0 AND material_id = " + q.getMaterialId().longValue());
        }
        return w.orderByDesc(SalPriceListDO::getEffectiveFrom).orderByDesc(SalPriceListDO::getId);
    }

    private List<PriceListRow> rows(List<SalPriceListDO> list) {
        if (list.isEmpty()) return List.of();
        Map<Long, Long> counts = itemMapper.selectByParents(list.stream().map(SalPriceListDO::getId).toList()).stream()
                .collect(Collectors.groupingBy(SalPriceListItemDO::getPriceListId, Collectors.counting()));
        Map<Long, CustomerDTO> cs = support.customers(list.stream().map(SalPriceListDO::getCustomerId).toList());
        Map<Long, UserDTO> users = support.users(list.stream().map(SalPriceListDO::getOwnerId).toList());
        return list.stream().map(p -> new PriceListRow(p.getId(), p.getDocNo(), p.getName(), p.getScope(), p.getCustomerId(),
                SalSupport.shortName(cs, p.getCustomerId()), p.getCustomerLevel(), p.getCurrency(), Boolean.TRUE.equals(p.getTaxIncluded()),
                p.getEffectiveFrom(), p.getEffectiveTo(), counts.getOrDefault(p.getId(), 0L).intValue(), p.getStatus().name(),
                SalSupport.name(users, p.getOwnerId()), p.getCreatedAt())).toList();
    }

    public PriceListDetail detail(Long id) {
        SalPriceListDO p = getOrThrow(id);
        List<SalPriceListItemDO> items = itemMapper.selectByParent(id);
        boolean cost = SalSupport.canViewOrderCost();
        Map<Long, MaterialDTO> ms = support.materials(items.stream().map(SalPriceListItemDO::getMaterialId).toList());
        Map<Long, CostService.UnitCost> costs = cost ? costService.unitCosts(ms.keySet()) : Map.of();
        BigDecimal minMargin = costService.minMarginRate();
        BigDecimal rate = p.getCurrency().equals(support.baseCurrency()) ? BigDecimal.ONE : safeRate(p.getCurrency());
        BigDecimal taxRate = taxRateOf(p);
        List<ItemResp> resps = new ArrayList<>();
        for (SalPriceListItemDO i : items) {
            MaterialDTO m = ms.get(i.getMaterialId());
            CostService.Margin mg = new CostService.Margin(null, null, false);
            CostService.UnitCost c = costs.get(i.getMaterialId());
            if (c != null && rate != null) {
                BigDecimal excl = Boolean.TRUE.equals(p.getTaxIncluded()) ? i.getPrice().divide(BigDecimal.ONE.add(taxRate), 10, RoundingMode.HALF_UP) : i.getPrice();
                mg = costService.margin(excl, rate, basePerUom(i.getMaterialId(), i.getUom()), c.cost(), minMargin);
            }
            resps.add(new ItemResp(i.getId(), i.getLineNo(), i.getMaterialId(), m == null ? null : m.code(), m == null ? null : m.name(),
                    m == null ? null : m.spec(), m == null ? null : m.baseUom(), i.getUom(), i.getMinQty(), i.getPrice(),
                    SalSupport.mask(mg.cost(), cost), SalSupport.mask(mg.marginRate(), cost), cost && mg.belowFloor(), i.getRemark()));
        }
        CustomerDTO c = p.getCustomerId() == null ? null : support.customerApi().getCustomer(p.getCustomerId()).orElse(null);
        return new PriceListDetail(p.getId(), p.getDocNo(), p.getName(), p.getScope(), p.getCustomerId(), c == null ? null : c.shortName(),
                p.getCustomerLevel(), p.getCurrency(), Boolean.TRUE.equals(p.getTaxIncluded()), p.getEffectiveFrom(), p.getEffectiveTo(),
                p.getStatus().name(), p.getCloseReason(), p.getRemark(), cost, support.userName(p.getOwnerId()), p.getCreatedAt(), p.getVersion(), resps);
    }

    private BigDecimal safeRate(String currency) {
        try {
            return support.currencyApi().getRate(currency, LocalDate.now());
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** 客户价格表取客户税率，其他取默认销项税率 */
    BigDecimal taxRateOf(SalPriceListDO p) {
        if (p.getCustomerId() != null) {
            CustomerDTO c = support.customerApi().getCustomer(p.getCustomerId()).orElse(null);
            if (c != null && c.salesTaxRate() != null) return c.salesTaxRate();
        }
        return defaultTaxRate();
    }

    public BigDecimal defaultTaxRate() {
        return SalSupport.nz(support.params().getDecimal(SalesModuleConfig.P_DEFAULT_TAX_RATE)).divide(SalSupport.HUNDRED, 4, RoundingMode.HALF_UP);
    }

    BigDecimal basePerUom(Long materialId, String uom) {
        try {
            return support.toBase(materialId, BigDecimal.ONE, uom);
        } catch (RuntimeException e) {
            return BigDecimal.ONE;
        }
    }

    public List<ItemExportRow> exportItems(PriceListQuery q, int limit) {
        List<SalPriceListDO> lists = mapper.selectList(query(q).last("LIMIT " + Math.min(limit, 5000)));
        if (lists.isEmpty()) return List.of();
        Map<Long, SalPriceListDO> byId = lists.stream().collect(Collectors.toMap(SalPriceListDO::getId, x -> x));
        List<SalPriceListItemDO> items = itemMapper.selectByParents(byId.keySet());
        Map<Long, MaterialDTO> ms = support.materials(items.stream().map(SalPriceListItemDO::getMaterialId).toList());
        Map<Long, CustomerDTO> cs = support.customers(lists.stream().map(SalPriceListDO::getCustomerId).toList());
        List<ItemExportRow> rows = new ArrayList<>();
        for (SalPriceListItemDO i : items) {
            if (rows.size() >= limit) break;
            SalPriceListDO p = byId.get(i.getPriceListId());
            MaterialDTO m = ms.get(i.getMaterialId());
            rows.add(new ItemExportRow(p.getDocNo(), p.getName(), p.getScope(), SalSupport.shortName(cs, p.getCustomerId()), p.getCurrency(),
                    Boolean.TRUE.equals(p.getTaxIncluded()), p.getEffectiveFrom(), p.getEffectiveTo(), p.getStatus().label(),
                    m == null ? null : m.code(), m == null ? null : m.name(), i.getUom(), i.getMinQty(), i.getPrice()));
        }
        return rows;
    }

    // ==================== 编辑 ====================

    @Transactional(rollbackFor = Exception.class)
    public Long create(PriceListSave req) {
        SalPriceListDO p = new SalPriceListDO();
        p.setDocNo(support.nextNo(BIZ_TYPE));
        p.setDocDate(LocalDate.now());
        p.setStatus(DocStatus.DRAFT);
        support.fillOwner(p, null);
        fillHeader(p, req);
        mapper.insert(p);
        saveItems(p, req.items());
        return p.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, PriceListSave req) {
        SalPriceListDO p = getOrThrow(id);
        requireEditable(p);
        if (req.version() != null) p.setVersion(req.version());
        fillHeader(p, req);
        mapper.updateByIdOrFail(p);
        saveItems(p, req.items());
    }

    private void requireEditable(SalPriceListDO p) {
        if (p.getStatus() == DocStatus.APPROVED || p.getStatus() == DocStatus.CLOSED) throw new BizException(SalesErrorCodes.PRICE_LIST_APPROVED);
        SalSupport.requireDraft(p);
    }

    private void fillHeader(SalPriceListDO p, PriceListSave req) {
        if (!SCOPES.contains(req.scope())) throw BizException.of(GlobalErrorCodes.BAD_REQUEST, "适用范围");
        p.setName(req.name().trim());
        p.setScope(req.scope());
        p.setCustomerId(null);
        p.setCustomerLevel(null);
        if ("CUSTOMER".equals(req.scope())) {
            if (req.customerId() == null) throw BizException.of(SalesErrorCodes.PRICE_LIST_SCOPE, "指定客户", "客户");
            support.customer(req.customerId());
            p.setCustomerId(req.customerId());
        } else if ("LEVEL".equals(req.scope())) {
            if (!StringUtils.hasText(req.customerLevel())) throw BizException.of(SalesErrorCodes.PRICE_LIST_SCOPE, "客户等级", "客户等级");
            support.dict().validate("crm_customer_level", req.customerLevel(), "客户等级");
            p.setCustomerLevel(req.customerLevel());
        }
        String currency = req.currency().trim().toUpperCase();
        support.currencyApi().validate(currency);
        p.setCurrency(currency);
        p.setTaxIncluded(req.taxIncluded() == null || req.taxIncluded());
        if (req.effectiveTo() != null && req.effectiveTo().isBefore(req.effectiveFrom())) throw new BizException(SalesErrorCodes.PRICE_LIST_DATE_RANGE);
        p.setEffectiveFrom(req.effectiveFrom());
        p.setEffectiveTo(req.effectiveTo());
        p.setRemark(SalSupport.trim(req.remark()));
    }

    private void saveItems(SalPriceListDO p, List<ItemSave> items) {
        if (items == null) return;
        Map<Long, MaterialDTO> ms = support.materials(items.stream().map(ItemSave::materialId).toList());
        itemMapper.deleteByParent(p.getId());
        Set<String> keys = new HashSet<>();
        int no = 0;
        for (ItemSave s : items) {
            no++;
            MaterialDTO m = ms.get(s.materialId());
            if (m == null) throw BizException.of(GlobalErrorCodes.DATA_NOT_EXISTS, "物料");
            BigDecimal min = Decimals.qty(SalSupport.nz(s.minQty()));
            if (min.signum() < 0) throw BizException.of(GlobalErrorCodes.BAD_REQUEST, "第 " + no + " 行起始数量不能为负数");
            if (s.price().signum() < 0) throw BizException.of(SalesErrorCodes.LINE_PRICE_NEGATIVE, no);
            String uom = s.uom().trim();
            if (!keys.add(m.id() + "|" + uom + "|" + min.stripTrailingZeros().toPlainString())) {
                throw BizException.of(SalesErrorCodes.PRICE_TIER_DUPLICATE, m.code(), uom, SalSupport.plain(min));
            }
            support.toBase(m.id(), BigDecimal.ONE, uom);
            SalPriceListItemDO d = new SalPriceListItemDO();
            d.setPriceListId(p.getId());
            d.setLineNo(no);
            d.setMaterialId(m.id());
            d.setUom(uom);
            d.setMinQty(min);
            d.setPrice(Decimals.price(s.price()));
            d.setRemark(SalSupport.trim(s.remark()));
            itemMapper.insert(d);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        SalPriceListDO p = getOrThrow(id);
        requireEditable(p);
        itemMapper.deleteByParent(id);
        mapper.deleteById(id);
    }

    /** 复制为新草稿（生效日期取今天，其余不变） */
    @Transactional(rollbackFor = Exception.class)
    public Long copy(Long id) {
        SalPriceListDO src = getOrThrow(id);
        List<ItemSave> items = itemMapper.selectByParent(id).stream()
                .map(i -> new ItemSave(i.getMaterialId(), i.getUom(), i.getMinQty(), i.getPrice(), i.getRemark())).toList();
        LocalDate from = src.getEffectiveFrom().isAfter(LocalDate.now()) ? src.getEffectiveFrom() : LocalDate.now();
        LocalDate to = src.getEffectiveTo() != null && src.getEffectiveTo().isBefore(from) ? null : src.getEffectiveTo();
        return create(new PriceListSave(src.getName(), src.getScope(), src.getCustomerId(), src.getCustomerLevel(), src.getCurrency(),
                src.getTaxIncluded(), from, to, src.getRemark(), items, null));
    }

    // ==================== 提交 / 审核 / 关闭 ====================

    /** R01：同一物料 + 单位必须有起始数量为 0 的档 */
    @Transactional(rollbackFor = Exception.class)
    public DocResult submit(Long id) {
        SalPriceListDO p = getOrThrow(id);
        SalSupport.requireDraft(p);
        List<SalPriceListItemDO> items = itemMapper.selectByParent(id);
        if (items.isEmpty()) throw new BizException(SalesErrorCodes.DOC_NO_LINES);
        Map<Long, MaterialDTO> ms = support.materials(items.stream().map(SalPriceListItemDO::getMaterialId).toList());
        Map<String, Boolean> zero = new HashMap<>();
        for (SalPriceListItemDO i : items) {
            zero.merge(i.getMaterialId() + "|" + i.getUom(), i.getMinQty().signum() == 0, Boolean::logicalOr);
        }
        for (Map.Entry<String, Boolean> e : zero.entrySet()) {
            if (!e.getValue()) {
                MaterialDTO m = ms.get(Long.valueOf(e.getKey().split("\\|")[0]));
                throw BizException.of(SalesErrorCodes.PRICE_ZERO_TIER, m == null ? e.getKey() : m.code());
            }
        }
        for (SalPriceListItemDO i : items) support.materialApi().validateUsable(i.getMaterialId());
        support.fire(SalStateMachines.PRICE_LIST, mapper, p, BIZ_TYPE, SalAction.SUBMIT, null);
        StartResult r = workflowApi.start(BIZ_TYPE, id, p.getDocNo(), "销售价格表 " + p.getDocNo() + " " + p.getName(), Map.of(), Map.of(),
                support.currentUser());
        if (!r.isStarted()) support.fire(SalStateMachines.PRICE_LIST, mapper, p, BIZ_TYPE, SalAction.APPROVE, null);
        return DocResult.of(p.getStatus().name());
    }

    @EventListener
    public void onApproval(ApprovalCompletedEvent e) {
        if (!BIZ_TYPE.equals(e.getBizType())) return;
        SalPriceListDO p = getOrThrow(e.getBizId());
        if (p.getStatus() != DocStatus.PENDING_APPROVAL) return;
        switch (e.getResult()) {
            case APPROVED -> support.fire(SalStateMachines.PRICE_LIST, mapper, p, BIZ_TYPE, SalAction.APPROVE, null);
            case WITHDRAWN -> support.fire(SalStateMachines.PRICE_LIST, mapper, p, BIZ_TYPE, SalAction.WITHDRAW, null);
            default -> support.fire(SalStateMachines.PRICE_LIST, mapper, p, BIZ_TYPE, SalAction.REJECT, e.getComment());
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void close(Long id, String reason) {
        SalPriceListDO p = getOrThrow(id);
        String why = SalSupport.requireReason(reason, "关闭");
        p.setCloseReason(why.length() > 256 ? why.substring(0, 256) : why);
        support.fire(SalStateMachines.PRICE_LIST, mapper, p, BIZ_TYPE, SalAction.CLOSE, why);
    }

    // ==================== 导入（到草稿价格表） ====================

    public void checkImport(Long id, List<ImportRow> rows) {
        SalPriceListDO p = getOrThrow(id);
        requireEditable(p);
        Set<String> keys = new HashSet<>();
        for (ImportRow r : rows) {
            String code = r.get("materialCode");
            MaterialDTO m = code == null ? null : support.materialApi().search(code, null, 5).stream().filter(x -> x.code().equals(code)).findFirst().orElse(null);
            if (code == null) r.error("物料编码不能为空");
            else if (m == null) r.error("物料「" + code + "」不存在或未启用");
            String uom = r.get("uom");
            if (uom == null) r.error("单位不能为空");
            BigDecimal min = num(r, "minQty", "起始数量");
            BigDecimal price = num(r, "price", "单价");
            if (price == null && !r.hasError()) r.error("单价不能为空");
            if (price != null && price.signum() < 0) r.error("单价不能为负数");
            if (m != null && uom != null) {
                try {
                    support.toBase(m.id(), BigDecimal.ONE, uom);
                } catch (BizException e) {
                    r.error(e.getMessage());
                }
                if (!keys.add(m.id() + "|" + uom + "|" + SalSupport.plain(SalSupport.nz(min)))) r.error("文件中阶梯重复");
            }
        }
    }

    private static BigDecimal num(ImportRow r, String key, String label) {
        String v = r.get(key);
        if (v == null) return null;
        try {
            return new BigDecimal(v.replace(",", ""));
        } catch (NumberFormatException e) {
            r.error(label + "不是数字");
            return null;
        }
    }

    /** 导入行追加到明细（相同物料 + 单位 + 起始数量的覆盖单价） */
    @Transactional(rollbackFor = Exception.class)
    public ImportResult doImport(Long id, List<ImportRow> rows) {
        SalPriceListDO p = getOrThrow(id);
        requireEditable(p);
        List<ItemSave> items = new ArrayList<>(itemMapper.selectByParent(id).stream()
                .map(i -> new ItemSave(i.getMaterialId(), i.getUom(), i.getMinQty(), i.getPrice(), i.getRemark())).toList());
        int ok = 0;
        for (ImportRow r : rows) {
            String code = r.get("materialCode");
            MaterialDTO m = support.materialApi().search(code, null, 5).stream().filter(x -> x.code().equals(code)).findFirst().orElseThrow();
            BigDecimal min = Decimals.qty(r.get("minQty") == null ? BigDecimal.ZERO : new BigDecimal(r.get("minQty").replace(",", "")));
            BigDecimal price = new BigDecimal(r.get("price").replace(",", ""));
            items.removeIf(i -> i.materialId().equals(m.id()) && i.uom().equals(r.get("uom")) && Decimals.qty(SalSupport.nz(i.minQty())).compareTo(min) == 0);
            items.add(new ItemSave(m.id(), r.get("uom"), min, price, r.get("remark")));
            ok++;
        }
        saveItems(p, items);
        mapper.updateByIdOrFail(p);
        return new ImportResult(ok, 0, List.of());
    }

    public SalPriceListDO getOrThrow(Long id) {
        SalPriceListDO p = id == null ? null : mapper.selectById(id);
        if (p == null) throw new BizException(SalesErrorCodes.PRICE_LIST_NOT_EXISTS);
        return p;
    }
}
