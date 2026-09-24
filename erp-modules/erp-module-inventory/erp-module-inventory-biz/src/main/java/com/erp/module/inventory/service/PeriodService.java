package com.erp.module.inventory.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.common.enums.DocStatus;
import com.erp.common.exception.BizException;
import com.erp.framework.event.DomainEventPublisher;
import com.erp.framework.excel.ExcelColumn;
import com.erp.framework.excel.ImportResult;
import com.erp.framework.excel.ImportRow;
import com.erp.framework.security.SecurityUtils;
import com.erp.module.engineering.api.material.MaterialApi;
import com.erp.module.engineering.api.material.MaterialDTO;
import com.erp.module.engineering.api.material.Tracking;
import com.erp.module.inventory.api.InventoryErrorCodes;
import com.erp.module.inventory.api.doc.StockInType;
import com.erp.module.inventory.api.period.FinancePeriodChecker;
import com.erp.module.inventory.api.period.PeriodClosedEvent;
import com.erp.module.inventory.controller.vo.PeriodVOs.CheckItem;
import com.erp.module.inventory.controller.vo.PeriodVOs.CheckResult;
import com.erp.module.inventory.controller.vo.PeriodVOs.OpeningInfo;
import com.erp.module.inventory.controller.vo.PeriodVOs.PeriodRow;
import com.erp.module.inventory.dal.dataobject.CountDO;
import com.erp.module.inventory.dal.dataobject.LocationDO;
import com.erp.module.inventory.dal.dataobject.PeriodBalanceDO;
import com.erp.module.inventory.dal.dataobject.PeriodDO;
import com.erp.module.inventory.dal.dataobject.StockDO;
import com.erp.module.inventory.dal.dataobject.StockInDO;
import com.erp.module.inventory.dal.dataobject.StockInLineDO;
import com.erp.module.inventory.dal.dataobject.StockOutDO;
import com.erp.module.inventory.dal.dataobject.StockTxnDO;
import com.erp.module.inventory.dal.dataobject.TransferDO;
import com.erp.module.inventory.dal.dataobject.TxnSumRow;
import com.erp.module.inventory.dal.dataobject.WarehouseDO;
import com.erp.module.inventory.dal.mapper.CountMapper;
import com.erp.module.inventory.dal.mapper.LocationMapper;
import com.erp.module.inventory.dal.mapper.PeriodBalanceMapper;
import com.erp.module.inventory.dal.mapper.PeriodMapper;
import com.erp.module.inventory.dal.mapper.StockInLineMapper;
import com.erp.module.inventory.dal.mapper.StockInMapper;
import com.erp.module.inventory.dal.mapper.StockMapper;
import com.erp.module.inventory.dal.mapper.StockOutMapper;
import com.erp.module.inventory.dal.mapper.StockTxnMapper;
import com.erp.module.inventory.dal.mapper.TransferMapper;
import com.erp.module.inventory.dal.mapper.WarehouseMapper;
import com.erp.module.inventory.service.doc.DocSupport;
import com.erp.module.inventory.service.doc.StockInService;
import com.erp.module.inventory.service.posting.CountFreeze;
import com.erp.module.inventory.service.posting.PeriodGuard;
import com.erp.module.inventory.service.posting.StockPostingService;
import com.erp.module.system.api.log.DocLogApi;
import com.erp.module.system.api.user.UserDTO;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 期初与月结（需求 08-09）：启用期间、期初导入（期初入库单）、完成期初；月结检查、月结（期末结存 + 下一期间 + PeriodClosedEvent）、反结账。
 */
@Service("invPeriodService")
public class PeriodService {

    public static final String BIZ_TYPE = "INV_PERIOD";
    public static final String OPENING_SOURCE = "INV_OPENING";
    static final DateTimeFormatter YM = DateTimeFormatter.ofPattern("yyyyMM");

    /** 期初导入模板（3.2 节） */
    public static final List<ExcelColumn<Object>> OPENING_COLUMNS = List.of(
            ExcelColumn.input("warehouseCode", "仓库编码", true, null),
            ExcelColumn.input("locationCode", "库位编码", false, "启用库位的仓库必填"),
            ExcelColumn.input("materialCode", "物料编码", true, null),
            ExcelColumn.input("batchNo", "批次号", false, "批次管理物料必填"),
            ExcelColumn.input("supplierBatchNo", "供应商批号", false, null),
            ExcelColumn.<Object>input("productionDate", "生产日期", false, "yyyy-MM-dd").ofType(ExcelColumn.Type.DATE),
            ExcelColumn.<Object>input("expireDate", "到期日期", false, "yyyy-MM-dd").ofType(ExcelColumn.Type.DATE),
            ExcelColumn.<Object>input("qty", "数量", true, "基本单位").ofType(ExcelColumn.Type.NUMBER),
            ExcelColumn.<Object>input("unitCost", "单价", true, "本位币不含税").ofType(ExcelColumn.Type.NUMBER),
            ExcelColumn.input("serialNos", "序列号", false, "序列号物料，多个用逗号分隔"));

    private final PeriodMapper periodMapper;
    private final PeriodBalanceMapper balanceMapper;
    private final StockTxnMapper txnMapper;
    private final StockMapper stockMapper;
    private final StockInMapper stockInMapper;
    private final StockInLineMapper stockInLineMapper;
    private final StockOutMapper stockOutMapper;
    private final TransferMapper transferMapper;
    private final CountMapper countMapper;
    private final WarehouseMapper warehouseMapper;
    private final LocationMapper locationMapper;
    private final PeriodGuard periodGuard;
    private final StockInService stockInService;
    private final DocSupport support;
    private final MaterialApi materialApi;
    private final DocLogApi docLogApi;
    private final DomainEventPublisher eventPublisher;
    private final ObjectProvider<FinancePeriodChecker> financeCheckers;

    public PeriodService(PeriodMapper periodMapper, PeriodBalanceMapper balanceMapper, StockTxnMapper txnMapper, StockMapper stockMapper,
                         StockInMapper stockInMapper, StockInLineMapper stockInLineMapper, StockOutMapper stockOutMapper, TransferMapper transferMapper,
                         CountMapper countMapper, WarehouseMapper warehouseMapper, LocationMapper locationMapper, PeriodGuard periodGuard,
                         StockInService stockInService, DocSupport support, MaterialApi materialApi, DocLogApi docLogApi,
                         DomainEventPublisher eventPublisher, ObjectProvider<FinancePeriodChecker> financeCheckers) {
        this.periodMapper = periodMapper;
        this.balanceMapper = balanceMapper;
        this.txnMapper = txnMapper;
        this.stockMapper = stockMapper;
        this.stockInMapper = stockInMapper;
        this.stockInLineMapper = stockInLineMapper;
        this.stockOutMapper = stockOutMapper;
        this.transferMapper = transferMapper;
        this.countMapper = countMapper;
        this.warehouseMapper = warehouseMapper;
        this.locationMapper = locationMapper;
        this.periodGuard = periodGuard;
        this.stockInService = stockInService;
        this.support = support;
        this.materialApi = materialApi;
        this.docLogApi = docLogApi;
        this.eventPublisher = eventPublisher;
        this.financeCheckers = financeCheckers;
    }

    // ==================== 期间列表 ====================

    public List<PeriodRow> list() {
        List<PeriodDO> all = periodMapper.selectList(new LambdaQueryWrapper<PeriodDO>().orderByDesc(PeriodDO::getPeriod));
        Map<Long, UserDTO> users = support.users(all.stream().map(PeriodDO::getClosedBy).toList());
        String latestClosed = all.stream().filter(p -> PeriodGuard.CLOSED.equals(p.getPeriodStatus())).map(PeriodDO::getPeriod).findFirst().orElse(null);
        String earliestOpen = all.stream().filter(p -> PeriodGuard.OPEN.equals(p.getPeriodStatus())).map(PeriodDO::getPeriod).min(String::compareTo).orElse(null);
        return all.stream().map(p -> new PeriodRow(p.getId(), p.getPeriod(), p.getStartDate(), p.getEndDate(), p.getPeriodStatus(),
                Boolean.TRUE.equals(p.getIsOpening()), DocSupport.name(users, p.getClosedBy()), p.getClosedAt(), financeClosed(p),
                p.getPeriod().equals(earliestOpen), p.getPeriod().equals(latestClosed))).toList();
    }

    /** 设置启用期间（3.1）：只能设置一次 */
    @Transactional(rollbackFor = Exception.class)
    public void init(String period) {
        PeriodDO start = periodGuard.openingPeriod();
        if (start != null) throw BizException.of(InventoryErrorCodes.PERIOD_ALREADY_INIT, start.getPeriod());
        PeriodDO p = newPeriod(YearMonth.parse(period, YM));
        p.setIsOpening(true);
        periodMapper.insert(p);
        docLogApi.record(BIZ_TYPE, p.getId(), period, "INIT", "设置启用期间", null, PeriodGuard.OPEN, null);
    }

    private static PeriodDO newPeriod(YearMonth ym) {
        PeriodDO p = new PeriodDO();
        p.setPeriod(ym.format(YM));
        p.setStartDate(ym.atDay(1));
        p.setEndDate(ym.atEndOfMonth());
        p.setPeriodStatus(PeriodGuard.OPEN);
        p.setIsOpening(false);
        p.setOpeningCompleted(false);
        p.setFinanceClosed(false);
        return p;
    }

    // ==================== 期初 ====================

    public OpeningInfo openingInfo() {
        PeriodDO start = periodGuard.openingPeriod();
        if (start == null) return new OpeningInfo(null, null, null, false, 0, 0, null, false);
        List<StockInDO> docs = openingDocs();
        List<StockInLineDO> lines = stockInLineMapper.selectByDocs(docs.stream().map(StockInDO::getId).toList());
        BigDecimal amount = StockInService.canViewCost()
                ? lines.stream().map(StockInLineDO::getAmount).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add) : null;
        boolean completed = Boolean.TRUE.equals(start.getOpeningCompleted());
        return new OpeningInfo(start.getPeriod(), start.getStartDate(), start.getStartDate().minusDays(1), completed, docs.size(), lines.size(),
                amount, !completed && !docs.isEmpty());
    }

    private List<StockInDO> openingDocs() {
        return stockInMapper.selectList(new LambdaQueryWrapper<StockInDO>().eq(StockInDO::getInType, StockInType.OPENING)
                .ne(StockInDO::getStatus, DocStatus.VOIDED).orderByAsc(StockInDO::getId));
    }

    private PeriodDO requireOpeningEditable() {
        PeriodDO start = periodGuard.openingPeriod();
        if (start == null) throw new BizException(InventoryErrorCodes.PERIOD_NOT_INIT);
        if (Boolean.TRUE.equals(start.getOpeningCompleted())) throw new BizException(InventoryErrorCodes.OPENING_COMPLETED);
        return start;
    }

    /** 解析后的一行期初 */
    record OpeningLine(int rowNo, WarehouseDO warehouse, Long locationId, MaterialDTO material, String batchNo, String supplierBatchNo,
                       LocalDate productionDate, LocalDate expireDate, BigDecimal qty, BigDecimal unitCost, List<String> serialNos) {
    }

    /** 校验期初导入，返回每行的动作（错误写入行） */
    public Map<Integer, String> checkOpening(List<ImportRow> rows) {
        Map<Integer, String> actions = new HashMap<>();
        parseOpening(rows).forEach(l -> actions.put(l.rowNo(), "新增"));
        return actions;
    }

    List<OpeningLine> parseOpening(List<ImportRow> rows) {
        requireOpeningEditable();
        Map<String, WarehouseDO> whs = warehouseMapper.selectList(null).stream().collect(Collectors.toMap(w -> w.getCode().toUpperCase(), w -> w, (a, b) -> a));
        Map<String, MaterialDTO> materialCache = new HashMap<>();
        List<OpeningLine> result = new ArrayList<>();
        Set<String> serialSeen = new java.util.HashSet<>();
        for (ImportRow r : rows) {
            WarehouseDO w = r.get("warehouseCode") == null ? null : whs.get(r.get("warehouseCode").trim().toUpperCase());
            if (w == null) r.error("仓库编码「" + r.get("warehouseCode") + "」不存在");
            MaterialDTO m = r.get("materialCode") == null ? null : materialCache.computeIfAbsent(r.get("materialCode").trim(), this::materialByCode);
            if (m == null) r.error("物料编码「" + r.get("materialCode") + "」不存在");
            Long locId = null;
            if (w != null) {
                if (Boolean.TRUE.equals(w.getLocationEnabled())) {
                    if (!StringUtils.hasText(r.get("locationCode"))) {
                        r.error("仓库「" + w.getName() + "」启用了库位管理，请填写库位编码");
                    } else {
                        LocationDO loc = locationMapper.selectOne(new LambdaQueryWrapper<LocationDO>().eq(LocationDO::getWarehouseId, w.getId())
                                .eq(LocationDO::getCode, r.get("locationCode").trim()));
                        if (loc == null) r.error("库位「" + r.get("locationCode") + "」不存在");
                        else locId = loc.getId();
                    }
                }
            }
            BigDecimal qty = decimal(r, "qty", "数量");
            if (qty != null && qty.signum() <= 0) r.error("数量必须大于 0");
            BigDecimal cost = decimal(r, "unitCost", "单价");
            if (cost != null && cost.signum() < 0) r.error("单价不能小于 0");
            LocalDate prod = date(r, "productionDate", "生产日期");
            LocalDate exp = date(r, "expireDate", "到期日期");
            String batch = StringUtils.hasText(r.get("batchNo")) ? r.get("batchNo").trim() : null;
            List<String> sns = DocSupport.serials(r.get("serialNos"));
            if (m != null) {
                Tracking t = m.tracking() == null ? Tracking.NONE : m.tracking();
                if (t == Tracking.BATCH && batch == null) r.error("物料「" + m.code() + "」需要填写批次号");
                if (t == Tracking.SERIAL && qty != null && BigDecimal.valueOf(sns.size()).compareTo(qty) != 0) {
                    r.error("物料「" + m.code() + "」序列号数量 " + sns.size() + " 与数量 " + qty.stripTrailingZeros().toPlainString() + " 不一致");
                }
                for (String sn : sns) {
                    if (!serialSeen.add(m.id() + "|" + sn)) r.error("序列号「" + sn + "」重复");
                }
            }
            if (r.hasError()) continue;
            result.add(new OpeningLine(r.rowNo(), w, locId, m, batch, StringUtils.hasText(r.get("supplierBatchNo")) ? r.get("supplierBatchNo").trim() : null,
                    prod, exp, qty, cost, sns));
        }
        return result;
    }

    private MaterialDTO materialByCode(String code) {
        return materialApi.search(code, null, 20).stream().filter(m -> m.code().equalsIgnoreCase(code)).findFirst().orElse(null);
    }

    private static BigDecimal decimal(ImportRow r, String key, String label) {
        String v = r.get(key);
        if (!StringUtils.hasText(v)) {
            r.error(label + "不能为空");
            return null;
        }
        try {
            return new BigDecimal(v.trim());
        } catch (NumberFormatException e) {
            r.error(label + "格式不正确");
            return null;
        }
    }

    private static LocalDate date(ImportRow r, String key, String label) {
        String v = r.get(key);
        if (!StringUtils.hasText(v)) return null;
        try {
            return LocalDate.parse(v.trim().length() > 10 ? v.trim().substring(0, 10) : v.trim());
        } catch (DateTimeParseException e) {
            r.error(label + "格式应为 yyyy-MM-dd");
            return null;
        }
    }

    /** 期初导入：有任何错误行时整体不导入；按仓库生成期初入库单（日期 = 启用期间前一天）并自动确认 */
    @Transactional(rollbackFor = Exception.class)
    public ImportResult importOpening(List<ImportRow> rows) {
        PeriodDO start = requireOpeningEditable();
        List<OpeningLine> lines = parseOpening(rows);
        List<ImportResult.Error> errors = rows.stream().filter(ImportRow::hasError)
                .map(r -> new ImportResult.Error(r.rowNo(), String.join("；", r.errors()))).toList();
        if (!errors.isEmpty()) return new ImportResult(0, errors.size(), errors);
        if (lines.isEmpty()) throw new BizException(InventoryErrorCodes.NO_LINES);
        Map<Long, List<OpeningLine>> byWarehouse = new LinkedHashMap<>();
        lines.forEach(l -> byWarehouse.computeIfAbsent(l.warehouse().getId(), k -> new ArrayList<>()).add(l));
        LocalDate date = start.getStartDate().minusDays(1);
        for (Map.Entry<Long, List<OpeningLine>> e : byWarehouse.entrySet()) {
            List<StockInLineDO> ins = e.getValue().stream().map(l -> {
                StockInLineDO x = new StockInLineDO();
                x.setMaterialId(l.material().id());
                x.setUom(l.material().baseUom());
                x.setQty(l.qty());
                x.setBaseQty(l.qty());
                x.setLocationId(l.locationId());
                x.setBatchNo(l.batchNo());
                x.setSupplierBatchNo(l.supplierBatchNo());
                x.setProductionDate(l.productionDate());
                x.setExpireDate(l.expireDate());
                x.setSerialNos(DocSupport.serialText(l.serialNos()));
                x.setUnitCost(l.unitCost());
                x.setAmount(DocSupport.amount(l.qty(), l.unitCost()));
                return x;
            }).toList();
            Long id = stockInService.createSystem(StockInType.OPENING, e.getKey(), date, OPENING_SOURCE, null, null, "期初导入", ins);
            stockInService.confirm(id, null, null, false);
        }
        return new ImportResult(lines.size(), 0, List.of());
    }

    /** 清空期初：启用期间还没有其他单据过账前，冲销并作废全部期初入库单 */
    @Transactional(rollbackFor = Exception.class)
    public void clearOpening() {
        PeriodDO start = requireOpeningEditable();
        long others = txnMapper.selectCount(new LambdaQueryWrapper<StockTxnDO>().ne(StockTxnDO::getPeriod, StockPostingService.OPENING_PERIOD));
        if (others > 0) throw new BizException(InventoryErrorCodes.OPENING_CLEAR_BLOCKED);
        for (StockInDO d : openingDocs()) stockInService.reverseSystem(d.getId(), "清空期初");
        docLogApi.record(BIZ_TYPE, start.getId(), start.getPeriod(), "CLEAR_OPENING", "清空期初", null, null, null);
    }

    @Transactional(rollbackFor = Exception.class)
    public void completeOpening() {
        PeriodDO start = requireOpeningEditable();
        start.setOpeningCompleted(true);
        periodMapper.updateByIdOrFail(start);
        docLogApi.record(BIZ_TYPE, start.getId(), start.getPeriod(), "COMPLETE_OPENING", "完成期初", null, null, null);
    }

    // ==================== 月结 ====================

    public CheckResult check(String period) {
        PeriodDO p = getOrThrow(period);
        List<CheckItem> items = new ArrayList<>();
        if (!PeriodGuard.OPEN.equals(p.getPeriodStatus())) throw BizException.of(InventoryErrorCodes.PERIOD_STATUS, period);
        PeriodDO start = periodGuard.openingPeriod();
        if (start != null && !Boolean.TRUE.equals(start.getOpeningCompleted())) {
            items.add(new CheckItem("BLOCK", "请先完成期初库存导入", List.of()));
        }
        PeriodDO prev = periodMapper.selectOne(new LambdaQueryWrapper<PeriodDO>().lt(PeriodDO::getPeriod, period).orderByDesc(PeriodDO::getPeriod).last("LIMIT 1"));
        if (prev != null && !PeriodGuard.CLOSED.equals(prev.getPeriodStatus())) {
            items.add(new CheckItem("BLOCK", "请先结账 " + prev.getPeriod(), List.of()));
        }
        List<String> counts = countMapper.selectList(new LambdaQueryWrapper<CountDO>().in(CountDO::getCountStatus, CountFreeze.ACTIVE)
                .le(CountDO::getDocDate, p.getEndDate())).stream().map(c -> "盘点单 " + c.getDocNo() + " 尚未完成").toList();
        if (!counts.isEmpty()) items.add(new CheckItem("BLOCK", "本期存在盘点中的盘点单", counts));
        List<StockDO> negatives = stockMapper.selectList(new LambdaQueryWrapper<StockDO>().lt(StockDO::getQty, BigDecimal.ZERO).last("LIMIT 50"));
        if (!negatives.isEmpty()) {
            Map<Long, MaterialDTO> ms = support.materials(negatives.stream().map(StockDO::getMaterialId).toList());
            Map<Long, WarehouseDO> ws = support.warehouses().byIds(negatives.stream().map(StockDO::getWarehouseId).toList());
            items.add(new CheckItem("BLOCK", "存在负库存", negatives.stream().map(s -> (ms.containsKey(s.getMaterialId()) ? ms.get(s.getMaterialId()).code() : "")
                    + " @ " + (ws.containsKey(s.getWarehouseId()) ? ws.get(s.getWarehouseId()).getName() : "") + "：" + s.getQty().stripTrailingZeros().toPlainString()).toList()));
        }
        List<DocStatus> open = List.of(DocStatus.DRAFT, DocStatus.PENDING_APPROVAL, DocStatus.APPROVED);
        List<String> pending = new ArrayList<>();
        stockInMapper.selectList(new LambdaQueryWrapper<StockInDO>().in(StockInDO::getStatus, open).between(StockInDO::getDocDate, p.getStartDate(), p.getEndDate())
                .last("LIMIT 50")).forEach(d -> pending.add(d.getDocNo()));
        stockOutMapper.selectList(new LambdaQueryWrapper<StockOutDO>().in(StockOutDO::getStatus, open).between(StockOutDO::getDocDate, p.getStartDate(), p.getEndDate())
                .last("LIMIT 50")).forEach(d -> pending.add(d.getDocNo()));
        transferMapper.selectList(new LambdaQueryWrapper<TransferDO>().eq(TransferDO::getStatus, DocStatus.DRAFT).between(TransferDO::getDocDate, p.getStartDate(), p.getEndDate())
                .last("LIMIT 50")).forEach(d -> pending.add(d.getDocNo()));
        if (!pending.isEmpty()) items.add(new CheckItem("WARN", "本期存在未确认的出入库单、调拨单（确认时业务日期需在下期）", pending));
        boolean passed = items.stream().noneMatch(i -> "BLOCK".equals(i.level()));
        return new CheckResult(period, passed, items);
    }

    @Transactional(rollbackFor = Exception.class)
    public void close(String period) {
        CheckResult r = check(period);
        if (!r.passed()) {
            throw BizException.of(InventoryErrorCodes.PERIOD_CLOSE_BLOCKED, r.items().stream().filter(i -> "BLOCK".equals(i.level()))
                    .map(i -> i.details().isEmpty() ? i.title() : i.details().get(0)).collect(Collectors.joining("；")));
        }
        PeriodDO p = getOrThrow(period);
        balanceMapper.delete(new LambdaQueryWrapper<PeriodBalanceDO>().eq(PeriodBalanceDO::getPeriod, period));
        for (TxnSumRow row : txnMapper.balances(p.getEndDate())) {
            if (row.getQty() == null || row.getQty().signum() == 0) continue;
            PeriodBalanceDO b = new PeriodBalanceDO();
            b.setPeriod(period);
            b.setMaterialId(row.getMaterialId());
            b.setWarehouseId(row.getWarehouseId());
            b.setQty(row.getQty());
            balanceMapper.insert(b);
        }
        p.setPeriodStatus(PeriodGuard.CLOSED);
        p.setClosedBy(SecurityUtils.getLoginUserIdOrNull());
        p.setClosedAt(LocalDateTime.now());
        periodMapper.updateByIdOrFail(p);
        String next = YearMonth.parse(period, YM).plusMonths(1).format(YM);
        if (periodMapper.selectCount(new LambdaQueryWrapper<PeriodDO>().eq(PeriodDO::getPeriod, next)) == 0) {
            periodMapper.insert(newPeriod(YearMonth.parse(next, YM)));
        }
        docLogApi.record(BIZ_TYPE, p.getId(), period, "CLOSE", "月结", PeriodGuard.OPEN, PeriodGuard.CLOSED, null);
        eventPublisher.publish(new PeriodClosedEvent(period));
    }

    @Transactional(rollbackFor = Exception.class)
    public void reopen(String period) {
        PeriodDO p = getOrThrow(period);
        PeriodDO latest = periodMapper.selectOne(new LambdaQueryWrapper<PeriodDO>().eq(PeriodDO::getPeriodStatus, PeriodGuard.CLOSED)
                .orderByDesc(PeriodDO::getPeriod).last("LIMIT 1"));
        if (latest == null || !latest.getPeriod().equals(period)) {
            throw BizException.of(InventoryErrorCodes.PERIOD_REOPEN_LATEST, latest == null ? "-" : latest.getPeriod());
        }
        if (financeClosed(p)) throw new BizException(InventoryErrorCodes.PERIOD_FINANCE_CLOSED);
        balanceMapper.delete(new LambdaQueryWrapper<PeriodBalanceDO>().eq(PeriodBalanceDO::getPeriod, period));
        p.setPeriodStatus(PeriodGuard.OPEN);
        p.setClosedBy(null);
        p.setClosedAt(null);
        periodMapper.updateByIdOrFail(p);
        docLogApi.record(BIZ_TYPE, p.getId(), period, "REOPEN", "反结账", PeriodGuard.CLOSED, PeriodGuard.OPEN, null);
    }

    private boolean financeClosed(PeriodDO p) {
        if (Boolean.TRUE.equals(p.getFinanceClosed())) return true;
        return financeCheckers.orderedStream().anyMatch(c -> c.isClosed(p.getPeriod()));
    }

    private PeriodDO getOrThrow(String period) {
        PeriodDO p = period == null ? null : periodMapper.selectOne(new LambdaQueryWrapper<PeriodDO>().eq(PeriodDO::getPeriod, period));
        if (p == null) throw BizException.of(InventoryErrorCodes.PERIOD_NOT_EXISTS, period);
        return p;
    }
}
