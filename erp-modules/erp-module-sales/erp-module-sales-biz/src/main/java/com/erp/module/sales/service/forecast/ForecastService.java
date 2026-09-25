package com.erp.module.sales.service.forecast;

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
import com.erp.framework.excel.ImportResult;
import com.erp.framework.excel.ImportRow;
import com.erp.module.crm.api.customer.CustomerDTO;
import com.erp.module.engineering.api.material.MaterialDTO;
import com.erp.module.engineering.api.material.MaterialType;
import com.erp.module.sales.api.SalesErrorCodes;
import com.erp.module.sales.api.forecast.ForecastApi;
import com.erp.module.sales.api.forecast.ForecastPublishedEvent;
import com.erp.module.sales.api.forecast.NetForecastDTO;
import com.erp.module.sales.config.SalesModuleConfig;
import com.erp.module.sales.controller.vo.ForecastVOs.CellResp;
import com.erp.module.sales.controller.vo.ForecastVOs.CellSave;
import com.erp.module.sales.controller.vo.ForecastVOs.ConsumptionRow;
import com.erp.module.sales.controller.vo.ForecastVOs.ForecastDetail;
import com.erp.module.sales.controller.vo.ForecastVOs.ForecastQuery;
import com.erp.module.sales.controller.vo.ForecastVOs.ForecastRow;
import com.erp.module.sales.controller.vo.ForecastVOs.ForecastSave;
import com.erp.module.sales.controller.vo.ForecastVOs.RowResp;
import com.erp.module.sales.controller.vo.ForecastVOs.RowSave;
import com.erp.module.sales.dal.dataobject.SalForecastConsumptionDO;
import com.erp.module.sales.dal.dataobject.SalForecastDO;
import com.erp.module.sales.dal.dataobject.SalForecastLineDO;
import com.erp.module.sales.dal.dataobject.SalOrderDO;
import com.erp.module.sales.dal.dataobject.SalOrderLineDO;
import com.erp.module.sales.dal.mapper.SalForecastConsumptionMapper;
import com.erp.module.sales.dal.mapper.SalForecastLineMapper;
import com.erp.module.sales.dal.mapper.SalForecastMapper;
import com.erp.module.sales.dal.mapper.SalOrderLineMapper;
import com.erp.module.sales.dal.mapper.SalOrderMapper;
import com.erp.module.sales.service.SalAction;
import com.erp.module.sales.service.SalStateMachines;
import com.erp.module.sales.service.SalSupport;
import com.erp.module.system.api.user.UserDTO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
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
 * 销售预测（需求 04-05）：草稿 → 已发布（APPROVED）→ 已关闭；订单审核后按“客户 + 物料”冲销预测（第 3 节）。
 */
@Service("salForecastService")
public class ForecastService implements ForecastApi {

    public static final String BIZ_TYPE = SalesModuleConfig.FORECAST;
    static final DateTimeFormatter PERIOD = DateTimeFormatter.ofPattern("yyyyMM");
    /** 不冲销预测的订单类型：补货（免费）、样品 */
    static final Set<String> NO_CONSUME_TYPES = Set.of("REPLACEMENT", "SAMPLE");

    private final SalForecastMapper mapper;
    private final SalForecastLineMapper lineMapper;
    private final SalForecastConsumptionMapper consumptionMapper;
    private final SalOrderMapper orderMapper;
    private final SalOrderLineMapper orderLineMapper;
    private final SalSupport support;
    private final DomainEventPublisher eventPublisher;

    public ForecastService(SalForecastMapper mapper, SalForecastLineMapper lineMapper, SalForecastConsumptionMapper consumptionMapper, SalOrderMapper orderMapper,
                           SalOrderLineMapper orderLineMapper, SalSupport support, DomainEventPublisher eventPublisher) {
        this.mapper = mapper;
        this.lineMapper = lineMapper;
        this.consumptionMapper = consumptionMapper;
        this.orderMapper = orderMapper;
        this.orderLineMapper = orderLineMapper;
        this.support = support;
        this.eventPublisher = eventPublisher;
    }

    // ==================== 查询 ====================

    public PageResult<ForecastRow> page(ForecastQuery q) {
        LambdaQueryWrapper<SalForecastDO> w = new LambdaQueryWrapper<SalForecastDO>().eq(SalForecastDO::getDeleted, false)
                .eq(q.getOwnerId() != null, SalForecastDO::getOwnerId, q.getOwnerId());
        if (StringUtils.hasText(q.getKeyword())) {
            String k = q.getKeyword().trim();
            w.and(x -> x.like(SalForecastDO::getTitle, k).or().likeRight(SalForecastDO::getDocNo, k.toUpperCase()));
        }
        if (StringUtils.hasText(q.getStatuses())) {
            w.in(SalForecastDO::getStatus, Arrays.stream(q.getStatuses().split(",")).map(String::trim).map(DocStatus::valueOf).toList());
        } else {
            w.in(SalForecastDO::getStatus, DocStatus.DRAFT, DocStatus.APPROVED);
        }
        if (StringUtils.hasText(q.getPeriod())) w.le(SalForecastDO::getStartPeriod, q.getPeriod()).ge(SalForecastDO::getEndPeriod, q.getPeriod());
        if (q.getMaterialId() != null) {
            w.inSql(SalForecastDO::getId, "SELECT forecast_id FROM sal_forecast_line WHERE deleted = 0 AND material_id = " + q.getMaterialId().longValue());
        }
        w.orderByDesc(SalForecastDO::getId);
        IPage<SalForecastDO> page = mapper.selectScopedPage(Page.of(q.getPageNo(), q.getPageSize()), w);
        List<SalForecastDO> list = page.getRecords();
        Map<Long, List<SalForecastLineDO>> lines = list.isEmpty() ? Map.of()
                : lineMapper.selectByParents(list.stream().map(SalForecastDO::getId).toList()).stream().collect(Collectors.groupingBy(SalForecastLineDO::getForecastId));
        Map<Long, UserDTO> users = support.users(list.stream().map(SalForecastDO::getOwnerId).toList());
        return new PageResult<>(list.stream().map(f -> {
            List<SalForecastLineDO> ls = lines.getOrDefault(f.getId(), List.of());
            BigDecimal total = SalSupport.sum(ls.stream().map(SalForecastLineDO::getQty).toList());
            BigDecimal consumed = SalSupport.sum(ls.stream().map(SalForecastLineDO::getConsumedQty).toList());
            BigDecimal rate = total.signum() == 0 ? BigDecimal.ZERO : consumed.divide(total, 4, RoundingMode.HALF_UP);
            return new ForecastRow(f.getId(), f.getDocNo(), f.getTitle(), f.getStartPeriod(), f.getEndPeriod(), ls.size(), total, consumed, rate,
                    f.getStatus().name(), f.getOwnerId(), SalSupport.name(users, f.getOwnerId()), f.getPublishedAt(), f.getCreatedAt());
        }).toList(), page.getTotal());
    }

    public ForecastDetail detail(Long id) {
        SalForecastDO f = getOrThrow(id);
        DataScopes.check(f.getOrgId(), f.getDeptId(), f.getOwnerId(), "销售预测");
        List<SalForecastLineDO> lines = lineMapper.selectByParent(id);
        List<String> periods = periods(f.getStartPeriod(), f.getEndPeriod());
        Map<Long, MaterialDTO> ms = support.materials(lines.stream().map(SalForecastLineDO::getMaterialId).toList());
        Map<Long, CustomerDTO> cs = support.customers(lines.stream().map(SalForecastLineDO::getCustomerId).toList());
        Map<String, List<SalForecastLineDO>> byRow = new LinkedHashMap<>();
        lines.stream().sorted(Comparator.comparing(SalForecastLineDO::getId))
                .forEach(l -> byRow.computeIfAbsent(l.getCustomerId() + "|" + l.getMaterialId(), k -> new ArrayList<>()).add(l));
        List<RowResp> rows = new ArrayList<>();
        for (List<SalForecastLineDO> ls : byRow.values()) {
            SalForecastLineDO first = ls.get(0);
            MaterialDTO m = ms.get(first.getMaterialId());
            List<CellResp> cells = ls.stream().sorted(Comparator.comparing(SalForecastLineDO::getPeriod))
                    .map(l -> new CellResp(l.getId(), l.getPeriod(), l.getQty(), l.getConsumedQty())).toList();
            rows.add(new RowResp(first.getCustomerId(), SalSupport.shortName(cs, first.getCustomerId()), first.getMaterialId(),
                    m == null ? null : m.code(), m == null ? null : m.name(), m == null ? null : m.spec(), m == null ? null : m.baseUom(),
                    first.getRemark(), cells, SalSupport.sum(ls.stream().map(SalForecastLineDO::getQty).toList()),
                    SalSupport.sum(ls.stream().map(SalForecastLineDO::getConsumedQty).toList())));
        }
        SalForecastDO from = f.getRevisedFromId() == null ? null : mapper.selectById(f.getRevisedFromId());
        return new ForecastDetail(f.getId(), f.getDocNo(), f.getTitle(), f.getStartPeriod(), f.getEndPeriod(), periods, f.getStatus().name(),
                f.getPublishedAt(), f.getCloseReason(), f.getRevisedFromId(), from == null ? null : from.getDocNo(), f.getRemark(), f.getOwnerId(),
                support.userName(f.getOwnerId()), f.getCreatedAt(), f.getVersion(), rows);
    }

    /** 冲销明细：冲销该预测行的订单行 */
    public List<ConsumptionRow> consumptions(Long forecastId, Long lineId) {
        SalForecastLineDO l = lineMapper.selectById(lineId);
        if (l == null || !l.getForecastId().equals(forecastId)) throw BizException.of(GlobalErrorCodes.DATA_NOT_EXISTS, "预测行");
        List<SalForecastConsumptionDO> cs = consumptionMapper.selectList(new LambdaQueryWrapper<SalForecastConsumptionDO>()
                .eq(SalForecastConsumptionDO::getForecastLineId, lineId).orderByAsc(SalForecastConsumptionDO::getId));
        if (cs.isEmpty()) return List.of();
        Map<Long, SalOrderLineDO> ols = orderLineMapper.selectBatchIds(cs.stream().map(SalForecastConsumptionDO::getOrderLineId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(SalOrderLineDO::getId, x -> x));
        Map<Long, SalOrderDO> orders = ols.isEmpty() ? Map.of() : orderMapper.selectBatchIds(ols.values().stream().map(SalOrderLineDO::getOrderId)
                .collect(Collectors.toSet())).stream().collect(Collectors.toMap(SalOrderDO::getId, x -> x));
        Map<Long, CustomerDTO> customers = support.customers(orders.values().stream().map(SalOrderDO::getCustomerId).toList());
        return cs.stream().map(c -> {
            SalOrderLineDO ol = ols.get(c.getOrderLineId());
            SalOrderDO o = ol == null ? null : orders.get(ol.getOrderId());
            return new ConsumptionRow(c.getId(), o == null ? null : o.getId(), o == null ? null : o.getDocNo(), c.getOrderLineId(),
                    ol == null ? null : ol.getLineNo(), o == null ? null : SalSupport.shortName(customers, o.getCustomerId()), c.getQty(), c.getCreatedAt());
        }).toList();
    }

    // ==================== 编辑 ====================

    @Transactional(rollbackFor = Exception.class)
    public Long create(ForecastSave req) {
        SalForecastDO f = new SalForecastDO();
        f.setDocNo(support.nextNo(BIZ_TYPE));
        f.setDocDate(LocalDate.now());
        f.setStatus(DocStatus.DRAFT);
        support.fillOwner(f, null);
        fillHeader(f, req);
        mapper.insert(f);
        saveRows(f, req.rows());
        return f.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, ForecastSave req) {
        SalForecastDO f = getOrThrow(id);
        DataScopes.check(f.getOrgId(), f.getDeptId(), f.getOwnerId(), "销售预测");
        requireDraft(f, "修改");
        if (req.version() != null) f.setVersion(req.version());
        fillHeader(f, req);
        mapper.updateByIdOrFail(f);
        saveRows(f, req.rows());
    }

    private static void requireDraft(SalForecastDO f, String action) {
        if (f.getStatus() != DocStatus.DRAFT) throw BizException.of(SalesErrorCodes.FORECAST_STATUS, statusLabel(f.getStatus()), action);
    }

    static String statusLabel(DocStatus s) {
        return s == DocStatus.APPROVED ? "已发布" : s.label();
    }

    /** R01：月份跨度 ≤ 12 个月；不能早于当前月 */
    private void fillHeader(SalForecastDO f, ForecastSave req) {
        YearMonth start = parsePeriod(req.startPeriod());
        YearMonth end = parsePeriod(req.endPeriod());
        if (end.isBefore(start)) throw BizException.of(GlobalErrorCodes.BAD_REQUEST, "结束月份不能早于开始月份");
        if (start.plusMonths(11).isBefore(end)) throw new BizException(SalesErrorCodes.FORECAST_PERIOD_RANGE);
        if (start.isBefore(YearMonth.now())) throw new BizException(SalesErrorCodes.FORECAST_PERIOD_PAST);
        f.setTitle(req.title().trim());
        f.setStartPeriod(start.format(PERIOD));
        f.setEndPeriod(end.format(PERIOD));
        f.setRemark(SalSupport.trim(req.remark()));
    }

    static YearMonth parsePeriod(String p) {
        try {
            return YearMonth.parse(p == null ? "" : p.trim().replace("-", ""), PERIOD);
        } catch (DateTimeParseException e) {
            throw BizException.of(SalesErrorCodes.FORECAST_PERIOD_FORMAT, p);
        }
    }

    static List<String> periods(String from, String to) {
        List<String> list = new ArrayList<>();
        for (YearMonth m = parsePeriod(from); !m.isAfter(parsePeriod(to)); m = m.plusMonths(1)) list.add(m.format(PERIOD));
        return list;
    }

    /** 明细整体替换：数量为空或 0 的单元格不保存 */
    private void saveRows(SalForecastDO f, List<RowSave> rows) {
        if (rows == null) return;
        Map<Long, MaterialDTO> ms = support.materials(rows.stream().map(RowSave::materialId).toList());
        Set<String> range = new HashSet<>(periods(f.getStartPeriod(), f.getEndPeriod()));
        Set<String> keys = new HashSet<>();
        lineMapper.deleteByParent(f.getId());
        for (RowSave r : rows) {
            MaterialDTO m = ms.get(r.materialId());
            if (m == null) throw BizException.of(GlobalErrorCodes.DATA_NOT_EXISTS, "物料");
            if (m.materialType() != MaterialType.FINISHED && m.materialType() != MaterialType.SEMI_FINISHED) {
                throw BizException.of(GlobalErrorCodes.BAD_REQUEST, "预测物料「" + m.code() + "」必须是成品或半成品");
            }
            if (r.customerId() != null) support.customer(r.customerId());
            for (CellSave c : r.cells() == null ? List.<CellSave>of() : r.cells()) {
                if (c.qty() == null || c.qty().signum() == 0) continue;
                if (c.qty().signum() < 0) throw BizException.of(GlobalErrorCodes.BAD_REQUEST, "物料「" + m.code() + "」的预测数量不能为负数");
                String period = parsePeriod(c.period()).format(PERIOD);
                if (!range.contains(period)) throw BizException.of(SalesErrorCodes.FORECAST_LINE_OUT_OF_RANGE, m.code(), period);
                if (!keys.add(r.customerId() + "|" + m.id() + "|" + period)) {
                    throw BizException.of(SalesErrorCodes.FORECAST_LINE_DUPLICATE, m.code(), period);
                }
                SalForecastLineDO l = new SalForecastLineDO();
                l.setForecastId(f.getId());
                l.setCustomerId(r.customerId());
                l.setMaterialId(m.id());
                l.setPeriod(period);
                l.setQty(Decimals.qty(c.qty()));
                l.setConsumedQty(BigDecimal.ZERO);
                l.setRemark(SalSupport.trim(r.remark()));
                lineMapper.insert(l);
            }
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        SalForecastDO f = getOrThrow(id);
        DataScopes.check(f.getOrgId(), f.getDeptId(), f.getOwnerId(), "销售预测");
        requireDraft(f, "删除");
        lineMapper.deleteByParent(id);
        mapper.deleteById(id);
    }

    /** 复制为新草稿；revise=true 时为修订（发布新版时关闭旧版并迁移冲销记录，R03） */
    @Transactional(rollbackFor = Exception.class)
    public Long copy(Long id, boolean revise) {
        SalForecastDO src = getOrThrow(id);
        if (revise && src.getStatus() != DocStatus.APPROVED) throw BizException.of(SalesErrorCodes.FORECAST_STATUS, statusLabel(src.getStatus()), "修订");
        YearMonth now = YearMonth.now();
        YearMonth start = parsePeriod(src.getStartPeriod()).isBefore(now) ? now : parsePeriod(src.getStartPeriod());
        YearMonth end = parsePeriod(src.getEndPeriod()).isBefore(start) ? start : parsePeriod(src.getEndPeriod());
        String startP = start.format(PERIOD);
        Map<String, RowSave> rows = new LinkedHashMap<>();
        for (SalForecastLineDO l : lineMapper.selectByParent(id)) {
            if (l.getPeriod().compareTo(startP) < 0) continue;
            RowSave r = rows.computeIfAbsent(l.getCustomerId() + "|" + l.getMaterialId(),
                    k -> new RowSave(l.getCustomerId(), l.getMaterialId(), l.getRemark(), new ArrayList<>()));
            r.cells().add(new CellSave(l.getPeriod(), l.getQty()));
        }
        Long newId = create(new ForecastSave(src.getTitle(), startP, end.format(PERIOD), src.getRemark(), new ArrayList<>(rows.values()), null));
        if (revise) {
            SalForecastDO f = getOrThrow(newId);
            f.setRevisedFromId(src.getId());
            mapper.updateByIdOrFail(f);
        }
        return newId;
    }

    /** “从上期复制”：同一业务员上一张预测（发布或关闭）的明细 */
    public List<RowResp> previousRows(Long id) {
        SalForecastDO f = getOrThrow(id);
        SalForecastDO prev = mapper.selectOne(new LambdaQueryWrapper<SalForecastDO>().eq(SalForecastDO::getOwnerId, f.getOwnerId())
                .ne(SalForecastDO::getId, id).in(SalForecastDO::getStatus, DocStatus.APPROVED, DocStatus.CLOSED)
                .orderByDesc(SalForecastDO::getId).last("LIMIT 1"));
        return prev == null ? List.of() : detail(prev.getId()).rows();
    }

    // ==================== 发布 / 关闭 ====================

    /** R02：同一物料（+ 客户）同一月份只能出现在一张已发布预测中（被修订的旧版除外，发布时自动关闭并迁移冲销） */
    @Transactional(rollbackFor = Exception.class)
    public void publish(Long id) {
        SalForecastDO f = getOrThrow(id);
        DataScopes.check(f.getOrgId(), f.getDeptId(), f.getOwnerId(), "销售预测");
        requireDraft(f, "发布");
        List<SalForecastLineDO> lines = lineMapper.selectByParent(id);
        if (lines.isEmpty()) throw new BizException(SalesErrorCodes.DOC_NO_LINES);
        if (parsePeriod(f.getStartPeriod()).isBefore(YearMonth.now())) throw new BizException(SalesErrorCodes.FORECAST_PERIOD_PAST);
        List<SalForecastDO> published = mapper.selectList(new LambdaQueryWrapper<SalForecastDO>().eq(SalForecastDO::getStatus, DocStatus.APPROVED)
                .ne(SalForecastDO::getId, id).ne(f.getRevisedFromId() != null, SalForecastDO::getId, f.getRevisedFromId()));
        if (!published.isEmpty()) {
            Map<Long, SalForecastDO> byId = published.stream().collect(Collectors.toMap(SalForecastDO::getId, x -> x));
            Map<String, SalForecastLineDO> others = new HashMap<>();
            for (SalForecastLineDO o : lineMapper.selectByParents(byId.keySet())) others.put(key(o), o);
            Map<Long, MaterialDTO> ms = support.materials(lines.stream().map(SalForecastLineDO::getMaterialId).toList());
            for (SalForecastLineDO l : lines) {
                SalForecastLineDO o = others.get(key(l));
                if (o != null) {
                    MaterialDTO m = ms.get(l.getMaterialId());
                    throw BizException.of(SalesErrorCodes.FORECAST_CONFLICT, m == null ? l.getMaterialId() : m.code(),
                            l.getPeriod().substring(0, 4) + "-" + l.getPeriod().substring(4), byId.get(o.getForecastId()).getDocNo());
                }
            }
        }
        f.setPublishedAt(LocalDateTime.now());
        support.fire(SalStateMachines.FORECAST, mapper, f, BIZ_TYPE, SalAction.PUBLISH, null);
        Set<Long> materials = lines.stream().map(SalForecastLineDO::getMaterialId).collect(Collectors.toSet());
        if (f.getRevisedFromId() != null) {
            SalForecastDO old = mapper.selectById(f.getRevisedFromId());
            if (old != null && old.getStatus() == DocStatus.APPROVED) {
                migrateConsumption(old, lines);
                old.setCloseReason("被修订版 " + f.getDocNo() + " 替代");
                support.fire(SalStateMachines.FORECAST, mapper, old, BIZ_TYPE, SalAction.CLOSE, old.getCloseReason());
                materials.addAll(lineMapper.selectByParent(old.getId()).stream().map(SalForecastLineDO::getMaterialId).toList());
                eventPublisher.publish(new ForecastPublishedEvent(old.getId(), old.getDocNo(), ForecastPublishedEvent.CLOSED,
                        lineMapper.selectByParent(old.getId()).stream().map(SalForecastLineDO::getMaterialId).distinct().toList()));
            }
        }
        eventPublisher.publish(new ForecastPublishedEvent(f.getId(), f.getDocNo(), ForecastPublishedEvent.PUBLISHED, List.copyOf(materials)));
    }

    private static String key(SalForecastLineDO l) {
        return l.getCustomerId() + "|" + l.getMaterialId() + "|" + l.getPeriod();
    }

    /** R03：旧版冲销记录迁移到新版相同“客户 + 物料 + 月份”的行 */
    private void migrateConsumption(SalForecastDO old, List<SalForecastLineDO> newLines) {
        Map<String, SalForecastLineDO> byKey = newLines.stream().collect(Collectors.toMap(ForecastService::key, x -> x));
        for (SalForecastLineDO ol : lineMapper.selectByParent(old.getId())) {
            SalForecastLineDO nl = byKey.get(key(ol));
            if (nl == null || ol.getConsumedQty().signum() == 0) continue;
            List<SalForecastConsumptionDO> cs = consumptionMapper.selectList(new LambdaQueryWrapper<SalForecastConsumptionDO>()
                    .eq(SalForecastConsumptionDO::getForecastLineId, ol.getId()).orderByAsc(SalForecastConsumptionDO::getId));
            for (SalForecastConsumptionDO c : cs) {
                c.setForecastLineId(nl.getId());
                consumptionMapper.updateByIdOrFail(c);
            }
            refreshConsumed(List.of(ol.getId(), nl.getId()));
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void close(Long id, String reason) {
        SalForecastDO f = getOrThrow(id);
        DataScopes.check(f.getOrgId(), f.getDeptId(), f.getOwnerId(), "销售预测");
        String why = SalSupport.requireReason(reason, "关闭");
        f.setCloseReason(why.length() > 256 ? why.substring(0, 256) : why);
        support.fire(SalStateMachines.FORECAST, mapper, f, BIZ_TYPE, SalAction.CLOSE, why);
        eventPublisher.publish(new ForecastPublishedEvent(f.getId(), f.getDocNo(), ForecastPublishedEvent.CLOSED,
                lineMapper.selectByParent(id).stream().map(SalForecastLineDO::getMaterialId).distinct().toList()));
    }

    // ==================== 冲销（第 3 节） ====================

    /**
     * 使订单行的冲销数量与有效数量一致：有效数量减少时先回退最晚的冲销记录；增加时按规则冲销（先要求交期所在月，再按窗口依次冲销邻近月份；
     * 同月先冲指定客户的预测，再冲不区分客户的）。
     *
     * @param effectiveQty 订单行应冲销的数量（基本单位）：审核时 = 订单数量；关闭时 = 已出货数量；反审核时 = 0
     */
    public void sync(SalOrderDO o, SalOrderLineDO line, BigDecimal effectiveQty, Set<Long> changedMaterials) {
        List<SalForecastConsumptionDO> existing = consumptionMapper.selectList(new LambdaQueryWrapper<SalForecastConsumptionDO>()
                .eq(SalForecastConsumptionDO::getOrderLineId, line.getId()).orderByAsc(SalForecastConsumptionDO::getId));
        BigDecimal current = SalSupport.sum(existing.stream().map(SalForecastConsumptionDO::getQty).toList());
        BigDecimal target = NO_CONSUME_TYPES.contains(o.getOrderType()) ? BigDecimal.ZERO : effectiveQty.max(BigDecimal.ZERO);
        int cmp = target.compareTo(current);
        if (cmp == 0) return;
        Set<Long> touched = new HashSet<>();
        if (cmp < 0) {
            BigDecimal toRelease = current.subtract(target);
            for (int i = existing.size() - 1; i >= 0 && toRelease.signum() > 0; i--) {
                SalForecastConsumptionDO c = existing.get(i);
                BigDecimal take = c.getQty().min(toRelease);
                if (take.compareTo(c.getQty()) == 0) consumptionMapper.deleteById(c.getId());
                else {
                    c.setQty(c.getQty().subtract(take));
                    consumptionMapper.updateByIdOrFail(c);
                }
                toRelease = toRelease.subtract(take);
                touched.add(c.getForecastLineId());
            }
        } else {
            BigDecimal need = target.subtract(current);
            for (SalForecastLineDO fl : candidates(o.getCustomerId(), line.getMaterialId(), line.getRequiredDate())) {
                if (need.signum() <= 0) break;
                BigDecimal net = fl.getQty().subtract(fl.getConsumedQty());
                if (net.signum() <= 0) continue;
                BigDecimal take = net.min(need);
                SalForecastConsumptionDO c = new SalForecastConsumptionDO();
                c.setForecastLineId(fl.getId());
                c.setOrderLineId(line.getId());
                c.setQty(take);
                consumptionMapper.insert(c);
                fl.setConsumedQty(fl.getConsumedQty().add(take));
                need = need.subtract(take);
                touched.add(fl.getId());
            }
        }
        if (!touched.isEmpty()) {
            refreshConsumed(touched);
            changedMaterials.add(line.getMaterialId());
        }
    }

    /** 要求交期变化等需要重新分配时：先全部回退再冲销 */
    public void resync(SalOrderDO o, SalOrderLineDO line, BigDecimal effectiveQty, Set<Long> changedMaterials) {
        sync(o, line, BigDecimal.ZERO, changedMaterials);
        sync(o, line, effectiveQty, changedMaterials);
    }

    /** 冲销完成后发布事件（PMC 更新预测需求） */
    public void publishConsumed(Collection<Long> materialIds) {
        if (materialIds.isEmpty()) return;
        eventPublisher.publish(new ForecastPublishedEvent(null, null, ForecastPublishedEvent.CONSUMED, List.copyOf(materialIds)));
    }

    /** 可冲销的预测行，按冲销顺序排列 */
    private List<SalForecastLineDO> candidates(Long customerId, Long materialId, LocalDate requiredDate) {
        YearMonth target = YearMonth.from(requiredDate);
        int[] window = window();
        List<String> order = new ArrayList<>();
        order.add(target.format(PERIOD));
        for (int d = 1; d <= Math.max(window[0], window[1]); d++) {
            if (d <= window[0]) order.add(target.minusMonths(d).format(PERIOD));
            if (d <= window[1]) order.add(target.plusMonths(d).format(PERIOD));
        }
        String current = YearMonth.now().format(PERIOD);
        List<Long> published = mapper.selectList(new LambdaQueryWrapper<SalForecastDO>().eq(SalForecastDO::getStatus, DocStatus.APPROVED))
                .stream().map(SalForecastDO::getId).toList();
        if (published.isEmpty()) return List.of();
        List<SalForecastLineDO> lines = lineMapper.selectList(new LambdaQueryWrapper<SalForecastLineDO>().in(SalForecastLineDO::getForecastId, published)
                .eq(SalForecastLineDO::getMaterialId, materialId).in(SalForecastLineDO::getPeriod, order).ge(SalForecastLineDO::getPeriod, current)
                .and(w -> w.isNull(SalForecastLineDO::getCustomerId).or().eq(SalForecastLineDO::getCustomerId, customerId)));
        return lines.stream().sorted(Comparator.<SalForecastLineDO>comparingInt(l -> order.indexOf(l.getPeriod()))
                .thenComparing(l -> l.getCustomerId() == null ? 1 : 0).thenComparing(SalForecastLineDO::getId)).toList();
    }

    /** 参数 sal.forecast.consume-window：“向前 N,向后 M” */
    int[] window() {
        String v = support.params().getString(SalesModuleConfig.P_CONSUME_WINDOW);
        int[] w = {0, 1};
        if (StringUtils.hasText(v)) {
            String[] parts = v.split(",");
            try {
                w[0] = Math.max(0, Integer.parseInt(parts[0].trim()));
                if (parts.length > 1) w[1] = Math.max(0, Integer.parseInt(parts[1].trim()));
            } catch (NumberFormatException ignored) {
                // 参数格式错误时使用默认值
            }
        }
        return w;
    }

    private void refreshConsumed(Collection<Long> forecastLineIds) {
        for (Long id : new HashSet<>(forecastLineIds)) {
            SalForecastLineDO fl = lineMapper.selectById(id);
            if (fl == null) continue;
            BigDecimal sum = SalSupport.sum(consumptionMapper.selectList(new LambdaQueryWrapper<SalForecastConsumptionDO>()
                    .eq(SalForecastConsumptionDO::getForecastLineId, id)).stream().map(SalForecastConsumptionDO::getQty).toList());
            if (sum.compareTo(fl.getConsumedQty()) != 0) {
                fl.setConsumedQty(sum);
                lineMapper.updateByIdOrFail(fl);
            }
        }
    }

    // ==================== ForecastApi ====================

    @Override
    public List<NetForecastDTO> getNetForecast(String fromPeriod, String toPeriod) {
        String current = YearMonth.now().format(PERIOD);
        String from = fromPeriod == null || fromPeriod.compareTo(current) < 0 ? current : parsePeriod(fromPeriod).format(PERIOD);
        List<SalForecastDO> published = mapper.selectList(new LambdaQueryWrapper<SalForecastDO>().eq(SalForecastDO::getStatus, DocStatus.APPROVED));
        if (published.isEmpty()) return List.of();
        Map<Long, SalForecastDO> byId = published.stream().collect(Collectors.toMap(SalForecastDO::getId, x -> x));
        LambdaQueryWrapper<SalForecastLineDO> w = new LambdaQueryWrapper<SalForecastLineDO>().in(SalForecastLineDO::getForecastId, byId.keySet())
                .ge(SalForecastLineDO::getPeriod, from);
        if (toPeriod != null) w.le(SalForecastLineDO::getPeriod, parsePeriod(toPeriod).format(PERIOD));
        return lineMapper.selectList(w.orderByAsc(SalForecastLineDO::getPeriod).orderByAsc(SalForecastLineDO::getId)).stream()
                .filter(l -> l.getQty().compareTo(l.getConsumedQty()) > 0)
                .map(l -> new NetForecastDTO(l.getForecastId(), byId.get(l.getForecastId()).getDocNo(), l.getId(), l.getCustomerId(), l.getMaterialId(),
                        l.getPeriod(), l.getQty(), l.getConsumedQty(), l.getQty().subtract(l.getConsumedQty()))).toList();
    }

    // ==================== 导入（到草稿预测） ====================

    public void checkImport(Long id, List<ImportRow> rows) {
        SalForecastDO f = getOrThrow(id);
        requireDraft(f, "导入");
        Set<String> range = new HashSet<>(periods(f.getStartPeriod(), f.getEndPeriod()));
        Set<String> keys = new HashSet<>();
        for (ImportRow r : rows) {
            Long customerId = null;
            if (r.get("customerCode") != null) {
                CustomerDTO c = customerByCode(r.get("customerCode"));
                if (c == null) r.error("客户编码「" + r.get("customerCode") + "」不存在");
                else customerId = c.id();
            }
            MaterialDTO m = materialByCode(r.get("materialCode"));
            if (r.get("materialCode") == null) r.error("物料编码不能为空");
            else if (m == null) r.error("物料「" + r.get("materialCode") + "」不存在或未启用");
            String period = null;
            try {
                period = parsePeriod(r.get("period")).format(PERIOD);
                if (!range.contains(period)) r.error("月份不在预测起止月份内");
            } catch (BizException e) {
                r.error("月份格式应为 yyyyMM");
            }
            try {
                BigDecimal q = new BigDecimal(Objects.toString(r.get("qty"), "").replace(",", ""));
                if (q.signum() < 0) r.error("数量不能为负数");
            } catch (NumberFormatException e) {
                r.error("数量不是数字");
            }
            if (m != null && period != null && !keys.add(customerId + "|" + m.id() + "|" + period)) r.error("文件中重复");
        }
    }

    /** 导入行覆盖相同“客户 + 物料 + 月份”的单元格 */
    @Transactional(rollbackFor = Exception.class)
    public ImportResult doImport(Long id, List<ImportRow> rows) {
        SalForecastDO f = getOrThrow(id);
        requireDraft(f, "导入");
        Map<String, RowSave> merged = new LinkedHashMap<>();
        for (RowResp r : detail(id).rows()) {
            merged.put(r.customerId() + "|" + r.materialId(), new RowSave(r.customerId(), r.materialId(), r.remark(),
                    new ArrayList<>(r.cells().stream().map(c -> new CellSave(c.period(), c.qty())).toList())));
        }
        for (ImportRow r : rows) {
            Long customerId = r.get("customerCode") == null ? null : customerByCode(r.get("customerCode")).id();
            MaterialDTO m = materialByCode(r.get("materialCode"));
            String period = parsePeriod(r.get("period")).format(PERIOD);
            RowSave row = merged.computeIfAbsent(customerId + "|" + m.id(), k -> new RowSave(customerId, m.id(), null, new ArrayList<>()));
            row.cells().removeIf(c -> c.period().equals(period));
            row.cells().add(new CellSave(period, new BigDecimal(r.get("qty").replace(",", ""))));
        }
        saveRows(f, new ArrayList<>(merged.values()));
        mapper.updateByIdOrFail(f);
        return new ImportResult(rows.size(), 0, List.of());
    }

    private CustomerDTO customerByCode(String code) {
        return support.customerApi().search(code, null, 20).stream().filter(c -> c.code().equalsIgnoreCase(code)).findFirst().orElse(null);
    }

    private MaterialDTO materialByCode(String code) {
        if (code == null) return null;
        return support.materialApi().search(code, null, 5).stream().filter(x -> x.code().equals(code)).findFirst().orElse(null);
    }

    public SalForecastDO getOrThrow(Long id) {
        SalForecastDO f = id == null ? null : mapper.selectById(id);
        if (f == null) throw new BizException(SalesErrorCodes.FORECAST_NOT_EXISTS);
        return f;
    }
}
