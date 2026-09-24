package com.erp.module.inventory.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.common.exception.BizException;
import com.erp.common.result.PageResult;
import com.erp.common.util.Decimals;
import com.erp.module.engineering.api.bom.BomApi;
import com.erp.module.engineering.api.category.MaterialCategoryApi;
import com.erp.module.engineering.api.category.MaterialCategoryDTO;
import com.erp.module.engineering.api.material.MaterialApi;
import com.erp.module.engineering.api.material.MaterialDTO;
import com.erp.module.engineering.api.material.MaterialStockAttr;
import com.erp.module.inventory.api.InventoryErrorCodes;
import com.erp.module.inventory.api.warehouse.WarehouseType;
import com.erp.module.inventory.controller.vo.ReportVOs.AgingQuery;
import com.erp.module.inventory.controller.vo.ReportVOs.AgingResult;
import com.erp.module.inventory.controller.vo.ReportVOs.AgingRow;
import com.erp.module.inventory.controller.vo.ReportVOs.AlertCounts;
import com.erp.module.inventory.controller.vo.ReportVOs.AlertRow;
import com.erp.module.inventory.controller.vo.ReportVOs.Bucket;
import com.erp.module.inventory.controller.vo.ReportVOs.SlowQuery;
import com.erp.module.inventory.controller.vo.ReportVOs.SlowRow;
import com.erp.module.inventory.controller.vo.ReportVOs.StockPage;
import com.erp.module.inventory.controller.vo.ReportVOs.StockQuery;
import com.erp.module.inventory.controller.vo.ReportVOs.StockRow;
import com.erp.module.inventory.controller.vo.ReportVOs.SummaryQuery;
import com.erp.module.inventory.controller.vo.ReportVOs.SummaryResult;
import com.erp.module.inventory.controller.vo.ReportVOs.SummaryRow;
import com.erp.module.inventory.controller.vo.ReportVOs.TxnQuery;
import com.erp.module.inventory.controller.vo.ReportVOs.TxnRow;
import com.erp.module.inventory.dal.dataobject.BatchDO;
import com.erp.module.inventory.dal.dataobject.LocationDO;
import com.erp.module.inventory.dal.dataobject.PeriodBalanceDO;
import com.erp.module.inventory.dal.dataobject.PeriodDO;
import com.erp.module.inventory.dal.dataobject.StockDO;
import com.erp.module.inventory.dal.dataobject.StockTxnDO;
import com.erp.module.inventory.dal.dataobject.TxnBucketRow;
import com.erp.module.inventory.dal.dataobject.TxnSumRow;
import com.erp.module.inventory.dal.dataobject.WarehouseDO;
import com.erp.module.inventory.dal.mapper.BatchMapper;
import com.erp.module.inventory.dal.mapper.PeriodBalanceMapper;
import com.erp.module.inventory.dal.mapper.PeriodMapper;
import com.erp.module.inventory.dal.mapper.StockMapper;
import com.erp.module.inventory.dal.mapper.StockTxnMapper;
import com.erp.module.inventory.dal.mapper.WarehouseMapper;
import com.erp.module.inventory.service.doc.DocSupport;
import com.erp.module.inventory.service.doc.StockInService;
import com.erp.module.inventory.service.posting.PeriodGuard;
import com.erp.module.system.api.param.ParamApi;
import com.erp.module.system.api.user.UserDTO;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 库存查询与报表（需求 08-08）：即时库存、库存流水、收发存汇总、库龄、呆滞料、库存预警。所有查询按仓库数据权限过滤。
 */
@Service
public class StockReportService {

    static final DateTimeFormatter YM = DateTimeFormatter.ofPattern("yyyyMM");
    static final String PARAM_SLOW_DAYS = "inv.alert.slow-moving-days";
    static final String PARAM_EXPIRY_DAYS = "inv.alert.expiry-warn-days";
    static final String PARAM_QC_HOURS = "inv.qc.overdue-hours";

    /** 收发存入库细分（2.3 节） */
    public static final List<Bucket> IN_KEYS = List.of(new Bucket("PURCHASE", "采购"), new Bucket("PRODUCTION", "生产"),
            new Bucket("RETURN", "退料"), new Bucket("SALES_RETURN", "退货"), new Bucket("OTHER", "其他"), new Bucket("COUNT", "盘盈"),
            new Bucket("TRANSFER", "调入"));
    public static final List<Bucket> OUT_KEYS = List.of(new Bucket("ISSUE", "领料"), new Bucket("SALES", "销售"),
            new Bucket("RETURN", "退货"), new Bucket("OTHER", "其他"), new Bucket("COUNT", "盘亏"), new Bucket("TRANSFER", "调出"));

    private final StockMapper stockMapper;
    private final StockTxnMapper txnMapper;
    private final BatchMapper batchMapper;
    private final WarehouseMapper warehouseMapper;
    private final PeriodMapper periodMapper;
    private final PeriodBalanceMapper balanceMapper;
    private final WarehouseService warehouseService;
    private final InventoryQueryService queryService;
    private final DocSupport support;
    private final MaterialApi materialApi;
    private final MaterialCategoryApi categoryApi;
    private final BomApi bomApi;
    private final ParamApi paramApi;

    public StockReportService(StockMapper stockMapper, StockTxnMapper txnMapper, BatchMapper batchMapper, WarehouseMapper warehouseMapper,
                              PeriodMapper periodMapper, PeriodBalanceMapper balanceMapper, WarehouseService warehouseService,
                              InventoryQueryService queryService, DocSupport support, MaterialApi materialApi, MaterialCategoryApi categoryApi,
                              BomApi bomApi, ParamApi paramApi) {
        this.stockMapper = stockMapper;
        this.txnMapper = txnMapper;
        this.batchMapper = batchMapper;
        this.warehouseMapper = warehouseMapper;
        this.periodMapper = periodMapper;
        this.balanceMapper = balanceMapper;
        this.warehouseService = warehouseService;
        this.queryService = queryService;
        this.support = support;
        this.materialApi = materialApi;
        this.categoryApi = categoryApi;
        this.bomApi = bomApi;
        this.paramApi = paramApi;
    }

    // ==================== 公共过滤 ====================

    /** 当前用户可查询的仓库（按类型、仓库 ID 过滤），null 表示全部 */
    private Map<Long, WarehouseDO> warehouses(String typesCsv, String idsCsv, Long singleId) {
        Set<Long> allowed = warehouseService.accessibleIds();
        Set<WarehouseType> types = parseTypes(typesCsv);
        Set<Long> ids = new HashSet<>(ids(idsCsv));
        if (singleId != null) ids.add(singleId);
        return warehouseMapper.selectList(null).stream()
                .filter(w -> allowed == null || allowed.contains(w.getId()))
                .filter(w -> types.isEmpty() || types.contains(w.getWarehouseType()))
                .filter(w -> ids.isEmpty() || ids.contains(w.getId()))
                .collect(Collectors.toMap(WarehouseDO::getId, w -> w, (a, b) -> a, LinkedHashMap::new));
    }

    static Set<WarehouseType> parseTypes(String csv) {
        if (!StringUtils.hasText(csv)) return EnumSet.noneOf(WarehouseType.class);
        return Arrays.stream(csv.split(",")).map(String::trim).filter(StringUtils::hasText).map(WarehouseType::valueOf)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(WarehouseType.class)));
    }

    static List<Long> ids(String csv) {
        if (!StringUtils.hasText(csv)) return List.of();
        return Arrays.stream(csv.split(",")).map(String::trim).filter(s -> s.matches("\\d+")).map(Long::valueOf).toList();
    }

    /** 关键字 → 物料 ID（编码前缀 / 名称规格模糊），null 表示不过滤 */
    private Set<Long> keywordMaterials(String keyword, Long materialId) {
        if (materialId != null) return Set.of(materialId);
        if (!StringUtils.hasText(keyword)) return null;
        return materialApi.search(keyword.trim(), null, 2000).stream().map(MaterialDTO::id).collect(Collectors.toSet());
    }

    /** 类别（含下级）→ 类别 ID 集合，null 表示不过滤 */
    private Set<Long> categoryScope(Long categoryId) {
        if (categoryId == null) return null;
        Set<Long> s = new HashSet<>(categoryApi.getDescendantIds(categoryId));
        s.add(categoryId);
        return s;
    }

    private Map<Long, BigDecimal> costs(Set<Long> materialIds, boolean cost) {
        Map<Long, BigDecimal> m = new HashMap<>();
        if (!cost) return m;
        for (Long id : materialIds) {
            BigDecimal c = support.refCost(id);
            if (c != null) m.put(id, c);
        }
        return m;
    }

    // ==================== 即时库存 ====================

    public StockPage stocks(StockQuery q) {
        String groupBy = q.getGroupBy() == null ? "WAREHOUSE" : q.getGroupBy();
        Map<Long, WarehouseDO> whs = warehouses(q.getWarehouseTypes(), q.getWarehouseIds(), null);
        if (whs.isEmpty()) return new StockPage(List.of(), 0, null, null);
        Set<Long> kw = keywordMaterials(q.getKeyword(), q.getMaterialId());
        if (kw != null && kw.isEmpty()) return new StockPage(List.of(), 0, null, null);
        LambdaQueryWrapper<StockDO> w = new LambdaQueryWrapper<StockDO>().in(StockDO::getWarehouseId, whs.keySet())
                .in(kw != null, StockDO::getMaterialId, kw)
                .likeRight(StringUtils.hasText(q.getBatchNo()), StockDO::getBatchNo, q.getBatchNo() == null ? null : q.getBatchNo().trim())
                .eq(q.getLocationId() != null, StockDO::getLocationId, q.getLocationId());
        List<StockDO> stocks = stockMapper.selectList(w);
        Map<Long, MaterialDTO> materials = support.materials(stocks.stream().map(StockDO::getMaterialId).collect(Collectors.toSet()));
        Set<Long> cats = categoryScope(q.getCategoryId());
        if (cats != null) {
            stocks = stocks.stream().filter(s -> materials.containsKey(s.getMaterialId()) && cats.contains(materials.get(s.getMaterialId()).categoryId())).toList();
        }
        InventoryQueryService.UsableFilter usable = queryService.usableFilter(stocks);
        Map<Long, String> catNames = categoryApi.listAll().stream().collect(Collectors.toMap(MaterialCategoryDTO::id, MaterialCategoryDTO::name));
        boolean cost = StockInService.canViewCost();
        Map<Long, BigDecimal> costs = costs(stocks.stream().map(StockDO::getMaterialId).collect(Collectors.toSet()), cost);

        // 分组
        Function<StockDO, String> keyFn = switch (groupBy) {
            case "MATERIAL" -> s -> String.valueOf(s.getMaterialId());
            case "BATCH" -> s -> s.getMaterialId() + "|" + s.getWarehouseId() + "|" + s.getLocationId() + "|" + s.getBatchNo();
            default -> s -> s.getMaterialId() + "|" + s.getWarehouseId();
        };
        Map<String, List<StockDO>> groups = new LinkedHashMap<>();
        stocks.stream().sorted(Comparator.comparing((StockDO s) -> materials.containsKey(s.getMaterialId()) ? materials.get(s.getMaterialId()).code() : "")
                        .thenComparing(StockDO::getWarehouseId).thenComparing(StockDO::getLocationId).thenComparing(StockDO::getBatchNo))
                .forEach(s -> groups.computeIfAbsent(keyFn.apply(s), k -> new ArrayList<>()).add(s));
        boolean showZero = Boolean.TRUE.equals(q.getShowZero());
        Map<Long, BigDecimal> reservedByMaterial = "MATERIAL".equals(groupBy)
                ? queryService.reserved(materials.keySet(), null) : Map.of();
        Map<String, BatchDO> batches = new HashMap<>();
        if ("BATCH".equals(groupBy)) {
            Set<Long> mids = stocks.stream().filter(s -> !s.getBatchNo().isEmpty()).map(StockDO::getMaterialId).collect(Collectors.toSet());
            if (!mids.isEmpty()) batchMapper.selectList(new LambdaQueryWrapper<BatchDO>().in(BatchDO::getMaterialId, mids))
                    .forEach(b -> batches.put(b.getMaterialId() + "|" + b.getBatchNo(), b));
        }
        Map<Long, LocationDO> locs = "BATCH".equals(groupBy) ? warehouseService.locationsByIds(stocks.stream().map(StockDO::getLocationId).toList()) : Map.of();
        Map<Long, MaterialStockAttr> attrs = new HashMap<>();
        List<StockRow> rows = new ArrayList<>();
        for (Map.Entry<String, List<StockDO>> e : groups.entrySet()) {
            List<StockDO> g = e.getValue();
            StockDO first = g.get(0);
            MaterialDTO m = materials.get(first.getMaterialId());
            BigDecimal onHand = sum(g, s -> true);
            if (!showZero && onHand.signum() == 0) continue;
            BigDecimal avail = sum(g, usable::usable);
            BigDecimal qc = sum(g, s -> usable.typeOf(s) == WarehouseType.QC);
            BigDecimal ng = sum(g, s -> usable.typeOf(s) == WarehouseType.NG);
            BigDecimal reserved = null;
            BigDecimal safety = null;
            boolean below = false;
            if ("MATERIAL".equals(groupBy)) {
                reserved = reservedByMaterial.getOrDefault(first.getMaterialId(), BigDecimal.ZERO);
                avail = avail.subtract(reserved);
                MaterialStockAttr a = attrs.computeIfAbsent(first.getMaterialId(), materialApi::getStockAttr);
                safety = a.safetyStock();
                below = safety != null && safety.signum() > 0 && avail.compareTo(safety) < 0;
            }
            BigDecimal refCost = costs.get(first.getMaterialId());
            WarehouseDO wh = "MATERIAL".equals(groupBy) ? null : whs.get(first.getWarehouseId());
            BatchDO b = batches.get(first.getMaterialId() + "|" + first.getBatchNo());
            LocationDO loc = locs.get(first.getLocationId());
            LocalDate lastIn = g.stream().map(StockDO::getLastInDate).filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(null);
            LocalDate lastOut = g.stream().map(StockDO::getLastOutDate).filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(null);
            boolean detail = "BATCH".equals(groupBy);
            rows.add(new StockRow(e.getKey(), first.getMaterialId(), m == null ? null : m.code(), m == null ? null : m.name(), m == null ? null : m.spec(),
                    m == null ? null : m.baseUom(), m == null ? null : catNames.get(m.categoryId()), wh == null ? null : wh.getId(),
                    wh == null ? null : wh.getName(), wh == null ? null : wh.getWarehouseType(), detail && first.getLocationId() != 0 ? first.getLocationId() : null,
                    loc == null ? null : loc.getCode(), detail && !first.getBatchNo().isEmpty() ? first.getBatchNo() : null,
                    b == null ? null : b.getProductionDate(), b == null ? null : b.getExpireDate(), b != null && Boolean.TRUE.equals(b.getIsConcession()),
                    b != null && Boolean.TRUE.equals(b.getFrozen()), onHand, avail, reserved, "MATERIAL".equals(groupBy) ? qc : null,
                    "MATERIAL".equals(groupBy) ? ng : null, safety, below, cost ? refCost : null,
                    cost && refCost != null ? Decimals.multiplyAmount(onHand, refCost) : null, lastIn, lastOut));
        }
        Set<String> uoms = rows.stream().map(StockRow::baseUom).filter(Objects::nonNull).collect(Collectors.toSet());
        BigDecimal totalQty = uoms.size() == 1 ? rows.stream().map(StockRow::onHandQty).reduce(BigDecimal.ZERO, BigDecimal::add) : null;
        BigDecimal totalAmount = cost ? rows.stream().map(StockRow::amount).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add) : null;
        int from = (int) Math.min((long) (q.getPageNo() - 1) * q.getPageSize(), rows.size());
        int to = Math.min(from + q.getPageSize(), rows.size());
        return new StockPage(rows.subList(from, to), rows.size(), totalQty, totalAmount);
    }

    /** 导出：全部行 */
    public List<StockRow> stocksForExport(StockQuery q, int limit) {
        q.setPageNo(1);
        q.setPageSize(Math.max(1, limit));
        return stocks(q).list();
    }

    private static BigDecimal sum(List<StockDO> g, java.util.function.Predicate<StockDO> p) {
        return g.stream().filter(p).map(StockDO::getQty).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // ==================== 库存流水 ====================

    public PageResult<TxnRow> txns(TxnQuery q) {
        LambdaQueryWrapper<StockTxnDO> w = txnQuery(q);
        if (w == null) return PageResult.empty();
        PageResult<StockTxnDO> page = txnMapper.selectPage(q, w);
        return new PageResult<>(txnRows(page.list()), page.total());
    }

    public List<TxnRow> txnsForExport(TxnQuery q, int limit) {
        LambdaQueryWrapper<StockTxnDO> w = txnQuery(q);
        return w == null ? List.of() : txnRows(txnMapper.selectList(w.last("LIMIT " + limit)));
    }

    private LambdaQueryWrapper<StockTxnDO> txnQuery(TxnQuery q) {
        LocalDate from = q.getDateFrom() != null ? q.getDateFrom() : LocalDate.now().withDayOfMonth(1);
        LocalDate to = q.getDateTo() != null ? q.getDateTo() : LocalDate.now();
        if (from.plusYears(1).isBefore(to)) throw new BizException(InventoryErrorCodes.QUERY_RANGE_TOO_LARGE);
        Map<Long, WarehouseDO> whs = warehouses(null, null, q.getWarehouseId());
        if (whs.isEmpty()) return null;
        Set<Long> kw = keywordMaterials(q.getKeyword(), q.getMaterialId());
        if (kw != null && kw.isEmpty()) return null;
        LambdaQueryWrapper<StockTxnDO> w = new LambdaQueryWrapper<StockTxnDO>()
                .in(StockTxnDO::getWarehouseId, whs.keySet())
                .in(kw != null, StockTxnDO::getMaterialId, kw)
                .between(StockTxnDO::getBizDate, from, to)
                .eq(StringUtils.hasText(q.getDirection()), StockTxnDO::getDirection, q.getDirection())
                .likeRight(StringUtils.hasText(q.getBatchNo()), StockTxnDO::getBatchNo, q.getBatchNo() == null ? null : q.getBatchNo().trim());
        if (StringUtils.hasText(q.getBizTypes())) w.in(StockTxnDO::getBizType, Arrays.asList(q.getBizTypes().split(",")));
        if (StringUtils.hasText(q.getDocNo())) {
            String no = q.getDocNo().trim();
            w.and(x -> x.likeRight(StockTxnDO::getDocNo, no.toUpperCase()).or().likeRight(StockTxnDO::getSourceNo, no));
        }
        return w.orderByDesc(StockTxnDO::getBizDate).orderByDesc(StockTxnDO::getId);
    }

    private List<TxnRow> txnRows(List<StockTxnDO> list) {
        if (list.isEmpty()) return List.of();
        Map<Long, MaterialDTO> materials = support.materials(list.stream().map(StockTxnDO::getMaterialId).collect(Collectors.toSet()));
        Map<Long, WarehouseDO> whs = warehouseService.byIds(list.stream().map(StockTxnDO::getWarehouseId).toList());
        Map<Long, LocationDO> locs = warehouseService.locationsByIds(list.stream().map(StockTxnDO::getLocationId).toList());
        Map<Long, UserDTO> users = support.users(list.stream().map(StockTxnDO::getOperatorId).toList());
        boolean cost = StockInService.canViewCost();
        return list.stream().map(t -> {
            MaterialDTO m = materials.get(t.getMaterialId());
            WarehouseDO w = whs.get(t.getWarehouseId());
            LocationDO loc = locs.get(t.getLocationId());
            boolean in = "IN".equals(t.getDirection());
            return new TxnRow(t.getId(), t.getBizDate(), t.getDocType(), t.getDocId(), t.getDocNo(), t.getBizType(), t.getSourceType(), t.getSourceId(),
                    t.getSourceNo(), t.getMaterialId(), m == null ? null : m.code(), m == null ? null : m.name(), m == null ? null : m.spec(),
                    m == null ? null : m.baseUom(), w == null ? null : w.getName(), loc == null ? null : loc.getCode(),
                    t.getBatchNo().isEmpty() ? null : t.getBatchNo(), in ? t.getQty() : null, in ? null : t.getQty(), t.getBalanceQty(),
                    cost ? t.getUnitCost() : null, cost ? t.getAmount() : null, Boolean.TRUE.equals(t.getIsReversal()),
                    DocSupport.name(users, t.getOperatorId()), t.getCreatedAt());
        }).toList();
    }

    // ==================== 收发存汇总 ====================

    public SummaryResult inOutSummary(SummaryQuery q) {
        YearMonth now = YearMonth.now();
        YearMonth pf = StringUtils.hasText(q.getPeriodFrom()) ? YearMonth.parse(q.getPeriodFrom(), YM) : now;
        YearMonth pt = StringUtils.hasText(q.getPeriodTo()) ? YearMonth.parse(q.getPeriodTo(), YM) : pf;
        if (pt.isBefore(pf)) pt = pf;
        if (pf.plusMonths(12).isBefore(pt)) throw new BizException(InventoryErrorCodes.QUERY_RANGE_TOO_LARGE);
        LocalDate from = pf.atDay(1);
        LocalDate to = pt.atEndOfMonth();
        boolean byWarehouse = "WAREHOUSE".equals(q.getLevel());
        Map<Long, WarehouseDO> whs = warehouses(null, null, q.getWarehouseId());
        Set<Long> kw = keywordMaterials(q.getKeyword(), null);
        Set<Long> cats = categoryScope(q.getCategoryId());
        boolean cost = StockInService.canViewCost();

        // 成本是否已计算：范围内期间全部已结账且财务已回填期末金额
        List<PeriodDO> periods = periodMapper.selectList(new LambdaQueryWrapper<PeriodDO>().between(PeriodDO::getPeriod, pf.format(YM), pt.format(YM)));
        boolean calculated = !periods.isEmpty() && periods.size() == (int) (ChronoUnit.MONTHS.between(pf, pt) + 1)
                && periods.stream().allMatch(p -> PeriodGuard.CLOSED.equals(p.getPeriodStatus()) && Boolean.TRUE.equals(p.getFinanceClosed()));

        Map<String, Acc> acc = new LinkedHashMap<>();
        Function<Long[], String> key = k -> byWarehouse ? k[0] + "|" + k[1] : String.valueOf(k[0]);
        for (TxnSumRow r : txnMapper.balances(from.minusDays(1))) {
            if (!whs.containsKey(r.getWarehouseId())) continue;
            Acc a = acc.computeIfAbsent(key.apply(new Long[]{r.getMaterialId(), r.getWarehouseId()}), k -> new Acc(r.getMaterialId(), byWarehouse ? r.getWarehouseId() : null));
            a.opening = a.opening.add(r.getQty());
        }
        for (TxnBucketRow r : txnMapper.buckets(from, to)) {
            if (!whs.containsKey(r.getWarehouseId())) continue;
            Acc a = acc.computeIfAbsent(key.apply(new Long[]{r.getMaterialId(), r.getWarehouseId()}), k -> new Acc(r.getMaterialId(), byWarehouse ? r.getWarehouseId() : null));
            // 冲销流水按原方向扣减（反确认的入库计为负入库）
            boolean reversal = Boolean.TRUE.equals(r.getIsReversal());
            boolean in = "IN".equals(r.getDirection()) != reversal;
            BigDecimal qty = reversal ? r.getQty().negate() : r.getQty();
            BigDecimal amount = r.getAmount() == null ? null : reversal ? r.getAmount().negate() : r.getAmount();
            if (in) {
                a.in = a.in.add(qty);
                a.inDetail.merge(inBucket(r.getDocType(), r.getBizType()), qty, BigDecimal::add);
                if (amount != null) a.inAmount = a.inAmount.add(amount);
                if (r.getAmountCount() != null && r.getTxnCount() != null && r.getAmountCount() < r.getTxnCount()) a.inAmountPartial = true;
            } else {
                a.out = a.out.add(qty);
                a.outDetail.merge(outBucket(r.getDocType(), r.getBizType()), qty, BigDecimal::add);
                if (amount != null) a.outAmount = a.outAmount.add(amount);
            }
        }
        // 期初、期末金额：已结账期间取期末结存金额（财务回填）
        String prevPeriod = pf.minusMonths(1).format(YM);
        Map<String, BigDecimal> openingAmounts = balanceAmounts(prevPeriod, byWarehouse);
        Map<String, BigDecimal> closingAmounts = calculated ? balanceAmounts(pt.format(YM), byWarehouse) : Map.of();

        Map<Long, MaterialDTO> materials = support.materials(acc.values().stream().map(x -> x.materialId).collect(Collectors.toSet()));
        boolean showIdle = Boolean.TRUE.equals(q.getShowIdle());
        List<SummaryRow> rows = new ArrayList<>();
        for (Map.Entry<String, Acc> e : acc.entrySet()) {
            Acc a = e.getValue();
            MaterialDTO m = materials.get(a.materialId);
            if (kw != null && !kw.contains(a.materialId)) continue;
            if (cats != null && (m == null || !cats.contains(m.categoryId()))) continue;
            boolean idle = a.in.signum() == 0 && a.out.signum() == 0;
            if (idle && (!showIdle || a.opening.signum() == 0)) continue;
            BigDecimal closing = a.opening.add(a.in).subtract(a.out);
            WarehouseDO w = a.warehouseId == null ? null : whs.get(a.warehouseId);
            rows.add(new SummaryRow(a.materialId, m == null ? null : m.code(), m == null ? null : m.name(), m == null ? null : m.spec(),
                    m == null ? null : m.baseUom(), a.warehouseId, w == null ? null : w.getName(), a.opening,
                    cost ? openingAmounts.get(e.getKey()) : null, a.in, a.inDetail, cost && !a.inAmountPartial ? a.inAmount : null, a.out, a.outDetail,
                    cost && calculated ? a.outAmount : null, closing, cost ? closingAmounts.get(e.getKey()) : null, false));
        }
        rows.sort(Comparator.comparing((SummaryRow r) -> Objects.toString(r.materialCode(), "")).thenComparing(r -> Objects.toString(r.warehouseName(), "")));
        return new SummaryResult(pf.format(YM), pt.format(YM), calculated, IN_KEYS, OUT_KEYS, rows);
    }

    private Map<String, BigDecimal> balanceAmounts(String period, boolean byWarehouse) {
        Map<String, BigDecimal> m = new HashMap<>();
        balanceMapper.selectList(new LambdaQueryWrapper<PeriodBalanceDO>().eq(PeriodBalanceDO::getPeriod, period).isNotNull(PeriodBalanceDO::getAmount))
                .forEach(b -> m.merge(byWarehouse ? b.getMaterialId() + "|" + b.getWarehouseId() : String.valueOf(b.getMaterialId()), b.getAmount(), BigDecimal::add));
        return m;
    }

    static String inBucket(String docType, String bizType) {
        if ("TRANSFER".equals(docType)) return "TRANSFER";
        return switch (bizType) {
            case "PURCHASE_IN", "OUTSOURCE_IN" -> "PURCHASE";
            case "PRODUCTION_IN" -> "PRODUCTION";
            case "PRODUCTION_RETURN", "OUTSOURCE_RETURN" -> "RETURN";
            case "SALES_RETURN" -> "SALES_RETURN";
            case "COUNT_GAIN" -> "COUNT";
            default -> "OTHER";
        };
    }

    static String outBucket(String docType, String bizType) {
        if ("TRANSFER".equals(docType)) return "TRANSFER";
        return switch (bizType) {
            case "PRODUCTION_ISSUE", "OUTSOURCE_ISSUE" -> "ISSUE";
            case "SALES_OUT" -> "SALES";
            case "PURCHASE_RETURN" -> "RETURN";
            case "COUNT_LOSS" -> "COUNT";
            default -> "OTHER";
        };
    }

    private static final class Acc {
        final Long materialId;
        final Long warehouseId;
        BigDecimal opening = BigDecimal.ZERO;
        BigDecimal in = BigDecimal.ZERO;
        BigDecimal out = BigDecimal.ZERO;
        BigDecimal inAmount = BigDecimal.ZERO;
        BigDecimal outAmount = BigDecimal.ZERO;
        boolean inAmountPartial;
        final Map<String, BigDecimal> inDetail = new LinkedHashMap<>();
        final Map<String, BigDecimal> outDetail = new LinkedHashMap<>();

        Acc(Long materialId, Long warehouseId) {
            this.materialId = materialId;
            this.warehouseId = warehouseId;
        }
    }

    // ==================== 库龄 ====================

    public AgingResult aging(AgingQuery q) {
        LocalDate asOf = q.getAsOf() != null ? q.getAsOf() : LocalDate.now();
        List<Integer> limits = StringUtils.hasText(q.getBuckets())
                ? Arrays.stream(q.getBuckets().split(",")).map(String::trim).filter(s -> s.matches("\\d+")).map(Integer::valueOf).sorted().distinct().toList()
                : List.of(30, 90, 180, 365);
        List<String> labels = new ArrayList<>();
        int prev = 0;
        for (int l : limits) {
            labels.add(prev == 0 ? "0～" + l + " 天" : (prev + 1) + "～" + l + " 天");
            prev = l;
        }
        labels.add("> " + prev + " 天");
        String types = StringUtils.hasText(q.getWarehouseTypes()) ? q.getWarehouseTypes()
                : DocSupport.AVAILABLE.stream().map(Enum::name).collect(Collectors.joining(","));
        Map<Long, WarehouseDO> whs = warehouses(types, null, null);
        if (whs.isEmpty()) return new AgingResult(labels, List.of(), zeros(labels.size()));
        List<StockDO> stocks = stockMapper.selectList(new LambdaQueryWrapper<StockDO>().in(StockDO::getWarehouseId, whs.keySet()).gt(StockDO::getQty, 0));
        Map<Long, MaterialDTO> materials = support.materials(stocks.stream().map(StockDO::getMaterialId).collect(Collectors.toSet()));
        Set<Long> cats = categoryScope(q.getCategoryId());
        if (cats != null) stocks = stocks.stream().filter(s -> materials.containsKey(s.getMaterialId()) && cats.contains(materials.get(s.getMaterialId()).categoryId())).toList();
        Map<String, BatchDO> batches = new HashMap<>();
        Set<Long> batchMids = stocks.stream().filter(s -> !s.getBatchNo().isEmpty()).map(StockDO::getMaterialId).collect(Collectors.toSet());
        if (!batchMids.isEmpty()) batchMapper.selectList(new LambdaQueryWrapper<BatchDO>().in(BatchDO::getMaterialId, batchMids))
                .forEach(b -> batches.put(b.getMaterialId() + "|" + b.getBatchNo(), b));
        boolean cost = StockInService.canViewCost();
        Map<Long, BigDecimal> costs = costs(materials.keySet(), cost);
        Map<Long, List<StockDO>> byMaterial = stocks.stream().collect(Collectors.groupingBy(StockDO::getMaterialId, LinkedHashMap::new, Collectors.toList()));
        List<AgingRow> rows = new ArrayList<>();
        List<BigDecimal> totals = zeros(labels.size());
        for (Map.Entry<Long, List<StockDO>> e : byMaterial.entrySet()) {
            BigDecimal[] qty = new BigDecimal[labels.size()];
            Arrays.fill(qty, BigDecimal.ZERO);
            int maxDays = 0;
            // 批次物料按批次首次入库日期
            BigDecimal nonBatch = BigDecimal.ZERO;
            for (StockDO s : e.getValue()) {
                BatchDO b = batches.get(s.getMaterialId() + "|" + s.getBatchNo());
                if (s.getBatchNo().isEmpty() || b == null) {
                    nonBatch = nonBatch.add(s.getQty());
                    continue;
                }
                int days = (int) Math.max(0, ChronoUnit.DAYS.between(b.getFirstInDate(), asOf));
                qty[bucket(days, limits)] = qty[bucket(days, limits)].add(s.getQty());
                maxDays = Math.max(maxDays, days);
            }
            // 非批次物料按先进先出倒推：现存量从最近的入库流水往前分配
            if (nonBatch.signum() > 0) {
                BigDecimal rest = nonBatch;
                List<StockTxnDO> ins = txnMapper.selectList(new LambdaQueryWrapper<StockTxnDO>().eq(StockTxnDO::getMaterialId, e.getKey())
                        .in(StockTxnDO::getWarehouseId, whs.keySet()).eq(StockTxnDO::getDirection, "IN").eq(StockTxnDO::getIsReversal, false)
                        .eq(StockTxnDO::getBatchNo, "").le(StockTxnDO::getBizDate, asOf).orderByDesc(StockTxnDO::getBizDate).last("LIMIT 500"));
                for (StockTxnDO t : ins) {
                    if (rest.signum() <= 0) break;
                    BigDecimal take = t.getQty().min(rest);
                    int days = (int) Math.max(0, ChronoUnit.DAYS.between(t.getBizDate(), asOf));
                    qty[bucket(days, limits)] = qty[bucket(days, limits)].add(take);
                    maxDays = Math.max(maxDays, days);
                    rest = rest.subtract(take);
                }
                if (rest.signum() > 0) qty[labels.size() - 1] = qty[labels.size() - 1].add(rest);
            }
            MaterialDTO m = materials.get(e.getKey());
            BigDecimal c = costs.get(e.getKey());
            List<BigDecimal> q2 = Arrays.asList(qty);
            List<BigDecimal> amounts = cost && c != null ? q2.stream().map(x -> Decimals.multiplyAmount(x, c)).toList() : null;
            if (amounts != null) for (int i = 0; i < amounts.size(); i++) totals.set(i, totals.get(i).add(amounts.get(i)));
            rows.add(new AgingRow(e.getKey(), m == null ? null : m.code(), m == null ? null : m.name(), m == null ? null : m.spec(), m == null ? null : m.baseUom(),
                    q2.stream().reduce(BigDecimal.ZERO, BigDecimal::add), q2, amounts, maxDays));
        }
        rows.sort(Comparator.comparingInt(AgingRow::maxDays).reversed());
        return new AgingResult(labels, rows, cost ? totals : null);
    }

    private static int bucket(int days, List<Integer> limits) {
        for (int i = 0; i < limits.size(); i++) if (days <= limits.get(i)) return i;
        return limits.size();
    }

    private static List<BigDecimal> zeros(int n) {
        List<BigDecimal> l = new ArrayList<>();
        for (int i = 0; i < n; i++) l.add(BigDecimal.ZERO);
        return l;
    }

    // ==================== 呆滞料 ====================

    public List<SlowRow> slowMoving(SlowQuery q) {
        int days = q.getDays() != null && q.getDays() > 0 ? q.getDays() : paramApi.getInt(PARAM_SLOW_DAYS);
        String types = StringUtils.hasText(q.getWarehouseTypes()) ? q.getWarehouseTypes()
                : DocSupport.AVAILABLE.stream().map(Enum::name).collect(Collectors.joining(","));
        Map<Long, WarehouseDO> whs = warehouses(types, null, null);
        if (whs.isEmpty()) return List.of();
        List<StockDO> stocks = stockMapper.selectList(new LambdaQueryWrapper<StockDO>().in(StockDO::getWarehouseId, whs.keySet()).gt(StockDO::getQty, 0));
        Map<Long, MaterialDTO> materials = support.materials(stocks.stream().map(StockDO::getMaterialId).collect(Collectors.toSet()));
        Set<Long> cats = categoryScope(q.getCategoryId());
        boolean cost = StockInService.canViewCost();
        LocalDate today = LocalDate.now();
        List<SlowRow> rows = new ArrayList<>();
        Map<Long, List<StockDO>> byMaterial = stocks.stream().collect(Collectors.groupingBy(StockDO::getMaterialId));
        for (Map.Entry<Long, List<StockDO>> e : byMaterial.entrySet()) {
            MaterialDTO m = materials.get(e.getKey());
            if (cats != null && (m == null || !cats.contains(m.categoryId()))) continue;
            LocalDate lastIn = e.getValue().stream().map(StockDO::getLastInDate).filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(null);
            LocalDate lastOut = e.getValue().stream().map(StockDO::getLastOutDate).filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(null);
            LocalDate since = lastOut != null ? lastOut : lastIn;
            if (since == null) continue;
            int idle = (int) ChronoUnit.DAYS.between(since, today);
            if (idle < days) continue;
            BigDecimal qty = e.getValue().stream().map(StockDO::getQty).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal c = cost ? support.refCost(e.getKey()) : null;
            boolean used = bomApi.whereUsed(e.getKey()).stream().anyMatch(b -> b.isDefault());
            rows.add(new SlowRow(e.getKey(), m == null ? null : m.code(), m == null ? null : m.name(), m == null ? null : m.spec(), m == null ? null : m.baseUom(),
                    qty, c == null ? null : Decimals.multiplyAmount(qty, c), lastIn, lastOut, idle, used));
        }
        rows.sort(Comparator.comparingInt(SlowRow::idleDays).reversed());
        return rows;
    }

    // ==================== 库存预警 ====================

    public List<AlertRow> alerts(String type) {
        return switch (type == null ? "LOW" : type) {
            case "HIGH" -> stockLevelAlerts(false);
            case "EXPIRY" -> expiryAlerts();
            case "QC_OVERDUE" -> qcOverdueAlerts();
            default -> stockLevelAlerts(true);
        };
    }

    public AlertCounts alertCounts() {
        return new AlertCounts(stockLevelAlerts(true).size(), stockLevelAlerts(false).size(), expiryAlerts().size(), qcOverdueAlerts().size());
    }

    /** 低于安全库存（可用量 + 在途 < 安全库存）/ 超过最高库存（现存量 > 最高库存）；在途采购由资材模块提供，未接入时为 0 */
    List<AlertRow> stockLevelAlerts(boolean low) {
        Map<Long, WarehouseDO> whs = warehouses(null, null, null);
        if (whs.isEmpty()) return List.of();
        List<StockDO> stocks = stockMapper.selectList(new LambdaQueryWrapper<StockDO>().in(StockDO::getWarehouseId, whs.keySet()));
        Set<Long> mids = stocks.stream().map(StockDO::getMaterialId).collect(Collectors.toSet());
        Map<Long, MaterialDTO> materials = support.materials(mids);
        InventoryQueryService.UsableFilter usable = queryService.usableFilter(stocks);
        Map<Long, BigDecimal> reserved = queryService.reserved(mids, null);
        Map<Long, List<StockDO>> byMaterial = stocks.stream().collect(Collectors.groupingBy(StockDO::getMaterialId));
        List<AlertRow> rows = new ArrayList<>();
        Map<Long, Long> buyers = new HashMap<>();
        for (Map.Entry<Long, List<StockDO>> e : byMaterial.entrySet()) {
            MaterialStockAttr a = materialApi.getStockAttr(e.getKey());
            MaterialDTO m = materials.get(e.getKey());
            if (m == null) continue;
            BigDecimal onHand = sum(e.getValue(), s -> true);
            BigDecimal avail = sum(e.getValue(), usable::usable).subtract(reserved.getOrDefault(e.getKey(), BigDecimal.ZERO));
            if (low) {
                if (a.safetyStock() == null || a.safetyStock().signum() <= 0) continue;
                BigDecimal inTransit = BigDecimal.ZERO;
                BigDecimal gap = a.safetyStock().subtract(avail.add(inTransit));
                if (gap.signum() <= 0) continue;
                buyers.put(e.getKey(), materialApi.getPurchaseAttr(e.getKey()).buyerId());
                rows.add(row("LOW", m, null, null, onHand, a.safetyStock(), a.maxStock(), avail, inTransit, gap, null, null, null, null, null));
            } else {
                if (a.maxStock() == null || a.maxStock().signum() <= 0 || onHand.compareTo(a.maxStock()) <= 0) continue;
                rows.add(row("HIGH", m, null, null, onHand, a.safetyStock(), a.maxStock(), avail, null, onHand.subtract(a.maxStock()), null, null, null, null, null));
            }
        }
        if (!buyers.isEmpty()) {
            Map<Long, UserDTO> users = support.users(buyers.values().stream().filter(Objects::nonNull).toList());
            rows = rows.stream().map(r -> new AlertRow(r.type(), r.materialId(), r.materialCode(), r.materialName(), r.materialSpec(), r.baseUom(),
                    r.warehouseId(), r.warehouseName(), r.batchNo(), r.qty(), r.safetyStock(), r.maxStock(), r.availableQty(), r.inTransitQty(), r.gap(),
                    r.expireDate(), r.daysLeft(), r.inAt(), r.waitHours(), r.sourceNo(), DocSupport.name(users, buyers.get(r.materialId())))).toList();
        }
        return rows.stream().sorted(Comparator.comparing(AlertRow::materialCode)).toList();
    }

    /** 临期/过期批次：到期日 ≤ 今天 + 临期提醒天数，且有库存 */
    List<AlertRow> expiryAlerts() {
        int warn = paramApi.getInt(PARAM_EXPIRY_DAYS);
        LocalDate today = LocalDate.now();
        List<BatchDO> batches = batchMapper.selectList(new LambdaQueryWrapper<BatchDO>().isNotNull(BatchDO::getExpireDate)
                .le(BatchDO::getExpireDate, today.plusDays(warn)));
        return batchAlerts(batches, "EXPIRY", b -> true, today);
    }

    /** 待检超时：待检仓中首次入库超过阈值小时仍有库存的批次 */
    List<AlertRow> qcOverdueAlerts() {
        int hours = paramApi.getInt(PARAM_QC_HOURS);
        LocalDateTime limit = LocalDateTime.now().minusHours(hours);
        Map<Long, WarehouseDO> qc = warehouses(WarehouseType.QC.name(), null, null);
        if (qc.isEmpty()) return List.of();
        List<StockDO> stocks = stockMapper.selectList(new LambdaQueryWrapper<StockDO>().in(StockDO::getWarehouseId, qc.keySet()).gt(StockDO::getQty, 0));
        if (stocks.isEmpty()) return List.of();
        Map<Long, MaterialDTO> materials = support.materials(stocks.stream().map(StockDO::getMaterialId).collect(Collectors.toSet()));
        Map<String, BatchDO> batches = new HashMap<>();
        batchMapper.selectList(new LambdaQueryWrapper<BatchDO>().in(BatchDO::getMaterialId, materials.keySet()))
                .forEach(b -> batches.put(b.getMaterialId() + "|" + b.getBatchNo(), b));
        List<AlertRow> rows = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        for (StockDO s : stocks) {
            BatchDO b = batches.get(s.getMaterialId() + "|" + s.getBatchNo());
            LocalDateTime inAt = b != null && b.getFirstInAt() != null ? b.getFirstInAt() : s.getLastInDate() == null ? null : s.getLastInDate().atStartOfDay();
            if (inAt == null || inAt.isAfter(limit)) continue;
            MaterialDTO m = materials.get(s.getMaterialId());
            if (m == null) continue;
            WarehouseDO w = qc.get(s.getWarehouseId());
            rows.add(row("QC_OVERDUE", m, w, s.getBatchNo().isEmpty() ? null : s.getBatchNo(), s.getQty(), null, null, null, null, null, null, null, inAt,
                    ChronoUnit.HOURS.between(inAt, now), b == null ? null : b.getSourceNo()));
        }
        rows.sort(Comparator.comparing(AlertRow::waitHours).reversed());
        return rows;
    }

    private List<AlertRow> batchAlerts(List<BatchDO> batches, String type, java.util.function.Predicate<BatchDO> p, LocalDate today) {
        if (batches.isEmpty()) return List.of();
        Map<Long, WarehouseDO> whs = warehouses(null, null, null);
        if (whs.isEmpty()) return List.of();
        Set<Long> mids = batches.stream().map(BatchDO::getMaterialId).collect(Collectors.toSet());
        Map<String, BatchDO> byKey = batches.stream().filter(p).collect(Collectors.toMap(b -> b.getMaterialId() + "|" + b.getBatchNo(), b -> b, (a, b) -> a));
        List<StockDO> stocks = stockMapper.selectList(new LambdaQueryWrapper<StockDO>().in(StockDO::getMaterialId, mids).in(StockDO::getWarehouseId, whs.keySet())
                .gt(StockDO::getQty, 0));
        Map<Long, MaterialDTO> materials = support.materials(mids);
        List<AlertRow> rows = new ArrayList<>();
        for (StockDO s : stocks) {
            BatchDO b = byKey.get(s.getMaterialId() + "|" + s.getBatchNo());
            MaterialDTO m = materials.get(s.getMaterialId());
            if (b == null || m == null) continue;
            rows.add(row(type, m, whs.get(s.getWarehouseId()), b.getBatchNo(), s.getQty(), null, null, null, null, null, b.getExpireDate(),
                    (int) ChronoUnit.DAYS.between(today, b.getExpireDate()), null, null, b.getSourceNo()));
        }
        rows.sort(Comparator.comparing(AlertRow::daysLeft));
        return rows;
    }

    private static AlertRow row(String type, MaterialDTO m, WarehouseDO w, String batchNo, BigDecimal qty, BigDecimal safety, BigDecimal max,
                                BigDecimal avail, BigDecimal inTransit, BigDecimal gap, LocalDate expire, Integer daysLeft, LocalDateTime inAt,
                                Long waitHours, String sourceNo) {
        return new AlertRow(type, m.id(), m.code(), m.name(), m.spec(), m.baseUom(), w == null ? null : w.getId(), w == null ? null : w.getName(), batchNo,
                qty, safety, max, avail, inTransit, gap, expire, daysLeft, inAt, waitHours, sourceNo, null);
    }
}
