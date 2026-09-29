package com.erp.module.pmc.service.mps;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.common.exception.BizException;
import com.erp.common.result.PageResult;
import com.erp.framework.datascope.DataScopes;
import com.erp.module.engineering.api.material.MaterialDTO;
import com.erp.module.engineering.api.material.MaterialPlanAttr;
import com.erp.module.engineering.api.routing.RoutingDTO;
import com.erp.module.engineering.api.routing.WorkCenterDTO;
import com.erp.module.pmc.api.PmcErrorCodes;
import com.erp.module.pmc.config.PmcModuleConfig;
import com.erp.module.pmc.controller.vo.MpsVOs.CapCell;
import com.erp.module.pmc.controller.vo.MpsVOs.CapRow;
import com.erp.module.pmc.controller.vo.MpsVOs.Cell;
import com.erp.module.pmc.controller.vo.MpsVOs.CellSave;
import com.erp.module.pmc.controller.vo.MpsVOs.Matrix;
import com.erp.module.pmc.controller.vo.MpsVOs.MatrixRow;
import com.erp.module.pmc.controller.vo.MpsVOs.MpsDetail;
import com.erp.module.pmc.controller.vo.MpsVOs.MpsQuery;
import com.erp.module.pmc.controller.vo.MpsVOs.MpsRow;
import com.erp.module.pmc.controller.vo.MpsVOs.MpsSave;
import com.erp.module.pmc.controller.vo.MpsVOs.RowSave;
import com.erp.module.pmc.dal.dataobject.PmcDemandDO;
import com.erp.module.pmc.dal.dataobject.PmcMpsDO;
import com.erp.module.pmc.dal.dataobject.PmcMpsLineDO;
import com.erp.module.pmc.dal.mapper.PmcMpsLineMapper;
import com.erp.module.pmc.dal.mapper.PmcMpsMapper;
import com.erp.module.pmc.service.PlanStatus;
import com.erp.module.pmc.service.PlanningData;
import com.erp.module.pmc.service.PmcAction;
import com.erp.module.pmc.service.PmcStateMachines;
import com.erp.module.pmc.service.PmcSupport;
import com.erp.module.pmc.service.Weeks;
import com.erp.module.pmc.service.demand.DemandService;
import com.erp.module.pmc.service.schedule.CapacityService;
import com.erp.module.production.api.order.OpenOrderDTO;
import com.erp.module.system.api.user.UserDTO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * MPS 主生产计划（需求 06-02）：按周编制成品 / 关键半成品的计划生产数量；需求、在制完工、预计结存由系统计算，过去的周锁定。
 * 每个周期只能有一张已发布的 MPS：新发布时关闭周期重叠的旧 MPS；已发布的调整复制为新草稿。
 */
@Service("pmcMpsService")
public class MpsService {

    private static final int MAX_WEEKS = 26;

    private final PmcMpsMapper mapper;
    private final PmcMpsLineMapper lineMapper;
    private final DemandService demandService;
    private final CapacityService capacityService;
    private final PlanningData data;
    private final PmcSupport support;

    public MpsService(PmcMpsMapper mapper, PmcMpsLineMapper lineMapper, DemandService demandService, CapacityService capacityService, PlanningData data,
                      PmcSupport support) {
        this.mapper = mapper;
        this.lineMapper = lineMapper;
        this.demandService = demandService;
        this.capacityService = capacityService;
        this.data = data;
        this.support = support;
    }

    // ==================== 单头 ====================

    public PageResult<MpsRow> page(MpsQuery q) {
        LambdaQueryWrapper<PmcMpsDO> w = new LambdaQueryWrapper<PmcMpsDO>().eq(PmcMpsDO::getDeleted, false)
                .like(StringUtils.hasText(q.getDocNo()), PmcMpsDO::getDocNo, q.getDocNo())
                .in(StringUtils.hasText(q.getStatuses()), PmcMpsDO::getMpsStatus, Arrays.asList(String.valueOf(q.getStatuses()).split(",")))
                .le(StringUtils.hasText(q.getWeek()), PmcMpsDO::getStartWeek, q.getWeek())
                .ge(StringUtils.hasText(q.getWeek()), PmcMpsDO::getEndWeek, q.getWeek())
                .orderByDesc(PmcMpsDO::getCreatedAt).orderByDesc(PmcMpsDO::getId);
        IPage<PmcMpsDO> p = mapper.selectScopedPage(new Page<>(q.getPageNo(), q.getPageSize()), w);
        Map<Long, UserDTO> users = support.users(p.getRecords().stream().map(PmcMpsDO::getOwnerId).toList());
        return new PageResult<>(p.getRecords().stream().map(m -> new MpsRow(m.getId(), m.getDocNo(), m.getTitle(), m.getStartWeek(), m.getEndWeek(),
                m.getMpsStatus(), materialCount(m.getId()), m.getPublishedAt(), PmcSupport.name(users, m.getOwnerId()), m.getCreatedAt())).toList(),
                p.getTotal());
    }

    private int materialCount(Long mpsId) {
        return (int) lineMapper.selectByParent(mpsId).stream().map(PmcMpsLineDO::getMaterialId).distinct().count();
    }

    public MpsDetail detail(Long id) {
        PmcMpsDO m = getOrThrow(id);
        String copiedNo = m.getCopiedFromId() == null ? null : mapper.selectById(m.getCopiedFromId()) == null ? null
                : mapper.selectById(m.getCopiedFromId()).getDocNo();
        return new MpsDetail(m.getId(), m.getDocNo(), m.getTitle(), m.getStartWeek(), m.getEndWeek(), m.getMpsStatus(), m.getRemark(), m.getPublishedAt(),
                m.getCopiedFromId(), copiedNo, support.userName(m.getOwnerId()), m.getCreatedAt(), m.getVersion() == null ? 0 : m.getVersion());
    }

    public PmcMpsDO getOrThrow(Long id) {
        PmcMpsDO m = id == null ? null : mapper.selectById(id);
        if (m == null) throw new BizException(PmcErrorCodes.MPS_NOT_EXISTS);
        DataScopes.check(m.getOrgId(), m.getDeptId(), m.getOwnerId(), "MPS");
        return m;
    }

    private static void validateWeeks(String start, String end) {
        LocalDate s = Weeks.monday(start);
        LocalDate e = Weeks.monday(end);
        if (e.isBefore(s) || Weeks.range(start, end).size() > MAX_WEEKS) throw new BizException(PmcErrorCodes.MPS_WEEK_RANGE);
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(MpsSave req) {
        validateWeeks(req.startWeek(), req.endWeek());
        PmcMpsDO m = new PmcMpsDO();
        m.setDocNo(support.nextNo(PmcModuleConfig.MPS));
        m.setDocDate(LocalDate.now());
        m.setTitle(req.title().trim());
        m.setStartWeek(req.startWeek().trim());
        m.setEndWeek(req.endWeek().trim());
        m.setMpsStatus(PlanStatus.DRAFT.name());
        m.setStatus(PlanStatus.DRAFT.docStatus());
        m.setRemark(PmcSupport.trim(req.remark()));
        support.fillOwner(m);
        mapper.insert(m);
        support.log(PmcModuleConfig.MPS, m.getId(), m.getDocNo(), "CREATE", "新建", null, PlanStatus.DRAFT.name(), null);
        return m.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, MpsSave req) {
        PmcMpsDO m = requireDraft(id);
        validateWeeks(req.startWeek(), req.endWeek());
        if (req.version() != null) m.setVersion(req.version());
        m.setTitle(req.title().trim());
        m.setStartWeek(req.startWeek().trim());
        m.setEndWeek(req.endWeek().trim());
        m.setRemark(PmcSupport.trim(req.remark()));
        mapper.updateByIdOrFail(m);
        Set<String> weeks = new LinkedHashSet<>(Weeks.range(m.getStartWeek(), m.getEndWeek()));
        for (PmcMpsLineDO l : lineMapper.selectByParent(id)) if (!weeks.contains(l.getWeek())) lineMapper.deleteById(l.getId());
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        PmcMpsDO m = requireDraft(id);
        lineMapper.deleteByParent(m.getId());
        mapper.deleteById(m.getId());
    }

    private PmcMpsDO requireDraft(Long id) {
        PmcMpsDO m = getOrThrow(id);
        if (!PlanStatus.DRAFT.name().equals(m.getMpsStatus())) throw new BizException(PmcErrorCodes.MPS_NOT_DRAFT);
        return m;
    }

    private void fire(PmcMpsDO m, PmcAction action, String reason) {
        PlanStatus from = PlanStatus.valueOf(m.getMpsStatus());
        PlanStatus to = PmcStateMachines.PLAN.fire(from, action);
        m.setMpsStatus(to.name());
        m.setStatus(to.docStatus());
        mapper.updateByIdOrFail(m);
        support.log(PmcModuleConfig.MPS, m.getId(), m.getDocNo(), action.name(), action.label(), from.name(), to.name(), reason);
    }

    /** 发布：关闭周期重叠的已发布 MPS（R03 复制的新版本发布时替换旧版本） */
    @Transactional(rollbackFor = Exception.class)
    public void publish(Long id) {
        PmcMpsDO m = getOrThrow(id);
        for (PmcMpsDO old : mapper.selectList(new LambdaQueryWrapper<PmcMpsDO>().eq(PmcMpsDO::getMpsStatus, PlanStatus.PUBLISHED.name())
                .le(PmcMpsDO::getStartWeek, m.getEndWeek()).ge(PmcMpsDO::getEndWeek, m.getStartWeek()))) {
            fire(old, PmcAction.CLOSE, "被 " + m.getDocNo() + " 替代");
        }
        refresh(m, lineMapper.selectByParent(id));
        m.setPublishedAt(LocalDateTime.now());
        fire(m, PmcAction.PUBLISH, null);
    }

    @Transactional(rollbackFor = Exception.class)
    public void close(Long id) {
        fire(getOrThrow(id), PmcAction.CLOSE, null);
    }

    /** 复制为新草稿（已发布的 MPS 调整时使用） */
    @Transactional(rollbackFor = Exception.class)
    public Long copy(Long id) {
        PmcMpsDO src = getOrThrow(id);
        Long nid = create(new MpsSave(src.getTitle(), src.getStartWeek(), src.getEndWeek(), src.getRemark(), null));
        PmcMpsDO n = mapper.selectById(nid);
        n.setCopiedFromId(src.getId());
        mapper.updateByIdOrFail(n);
        for (PmcMpsLineDO l : lineMapper.selectByParent(id)) {
            PmcMpsLineDO c = new PmcMpsLineDO();
            c.setMpsId(nid);
            c.setMaterialId(l.getMaterialId());
            c.setWeek(l.getWeek());
            c.setDemandQty(l.getDemandQty());
            c.setWipQty(l.getWipQty());
            c.setPlannedQty(l.getPlannedQty());
            c.setProjectedOnHand(l.getProjectedOnHand());
            c.setRemark(l.getRemark());
            lineMapper.insert(c);
        }
        return nid;
    }

    // ==================== 矩阵 ====================

    /** 物料在各周的需求、在制完工（系统计算）与期初可用 */
    private record Calc(Map<Long, Map<String, BigDecimal>> demand, Map<Long, Map<String, BigDecimal>> wip, Map<Long, BigDecimal> opening,
                        Map<Long, MaterialPlanAttr> attrs) {
    }

    private Calc calc(PmcMpsDO m, Set<Long> materialIds) {
        List<String> weeks = Weeks.range(m.getStartWeek(), m.getEndWeek());
        String first = weeks.get(0);
        LocalDate end = Weeks.sunday(m.getEndWeek());
        Map<Long, Map<String, BigDecimal>> demand = new HashMap<>();
        for (PmcDemandDO d : demandService.openDemands(end)) {
            if (!materialIds.contains(d.getMaterialId())) continue;
            String w = Weeks.of(d.getRequiredDate());
            if (w.compareTo(first) < 0) w = first;
            demand.computeIfAbsent(d.getMaterialId(), k -> new HashMap<>()).merge(w, d.getOpenQty(), BigDecimal::add);
        }
        Map<Long, Map<String, BigDecimal>> wip = new HashMap<>();
        if (!materialIds.isEmpty()) {
            for (OpenOrderDTO o : data.productionQueryApi().getOpenOrders(materialIds)) {
                if (o.remainingQty().signum() <= 0 || o.planEnd().isAfter(end)) continue;
                String w = Weeks.of(o.planEnd());
                if (w.compareTo(first) < 0) w = first;
                wip.computeIfAbsent(o.materialId(), k -> new HashMap<>()).merge(w, o.remainingQty(), BigDecimal::add);
            }
        }
        return new Calc(demand, wip, data.available(materialIds), support.planAttrs(materialIds));
    }

    /** 重算需求、在制完工、预计结存并保存到明细 */
    private void refresh(PmcMpsDO m, List<PmcMpsLineDO> lines) {
        Set<Long> mids = lines.stream().map(PmcMpsLineDO::getMaterialId).collect(Collectors.toSet());
        Calc c = calc(m, mids);
        Map<Long, Map<String, PmcMpsLineDO>> by = new HashMap<>();
        lines.forEach(l -> by.computeIfAbsent(l.getMaterialId(), k -> new HashMap<>()).put(l.getWeek(), l));
        for (Long mid : mids) {
            BigDecimal proj = c.opening().getOrDefault(mid, BigDecimal.ZERO);
            for (String w : Weeks.range(m.getStartWeek(), m.getEndWeek())) {
                PmcMpsLineDO l = by.get(mid).get(w);
                BigDecimal dem = c.demand().getOrDefault(mid, Map.of()).getOrDefault(w, BigDecimal.ZERO);
                BigDecimal wp = c.wip().getOrDefault(mid, Map.of()).getOrDefault(w, BigDecimal.ZERO);
                BigDecimal plan = l == null ? BigDecimal.ZERO : l.getPlannedQty();
                proj = proj.add(plan).add(wp).subtract(dem);
                if (l == null) continue;
                l.setDemandQty(dem);
                l.setWipQty(wp);
                l.setProjectedOnHand(proj);
                lineMapper.updateByIdOrFail(l);
            }
        }
    }

    public Matrix matrix(Long id) {
        PmcMpsDO m = getOrThrow(id);
        List<PmcMpsLineDO> lines = lineMapper.selectByParent(id);
        List<String> weeks = Weeks.range(m.getStartWeek(), m.getEndWeek());
        List<Long> mids = lines.stream().map(PmcMpsLineDO::getMaterialId).distinct().toList();
        Calc c = calc(m, new LinkedHashSet<>(mids));
        Map<Long, MaterialDTO> ms = support.materials(mids);
        Map<String, PmcMpsLineDO> byKey = lines.stream().collect(Collectors.toMap(l -> l.getMaterialId() + "|" + l.getWeek(), l -> l, (a, b) -> a));
        String current = Weeks.current();
        boolean live = PlanStatus.DRAFT.name().equals(m.getMpsStatus());
        List<MatrixRow> rows = new ArrayList<>();
        for (Long mid : mids) {
            MaterialDTO md = ms.get(mid);
            MaterialPlanAttr a = c.attrs().get(mid);
            BigDecimal opening = c.opening().getOrDefault(mid, BigDecimal.ZERO);
            BigDecimal proj = opening;
            List<Cell> cells = new ArrayList<>();
            for (String w : weeks) {
                PmcMpsLineDO l = byKey.get(mid + "|" + w);
                BigDecimal dem = live || l == null ? c.demand().getOrDefault(mid, Map.of()).getOrDefault(w, BigDecimal.ZERO) : l.getDemandQty();
                BigDecimal wp = live || l == null ? c.wip().getOrDefault(mid, Map.of()).getOrDefault(w, BigDecimal.ZERO) : l.getWipQty();
                BigDecimal plan = l == null ? BigDecimal.ZERO : l.getPlannedQty();
                proj = proj.add(plan).add(wp).subtract(dem);
                cells.add(new Cell(w, dem, wp, plan, proj, w.compareTo(current) < 0, l == null ? null : l.getRemark()));
            }
            rows.add(new MatrixRow(mid, md == null ? null : md.code(), md == null ? null : md.name(), md == null ? null : md.spec(),
                    md == null ? null : md.baseUom(), a == null ? BigDecimal.ZERO : PmcSupport.nz(a.safetyStock()), opening, a == null ? null : a.mpq(), cells));
        }
        rows.sort(Comparator.comparing(MatrixRow::materialCode, Comparator.nullsLast(Comparator.naturalOrder())));
        return new Matrix(id, m.getMpsStatus(), weeks, rows);
    }

    /** R01：只能对有已审核 BOM 的自制件编制 */
    private void validateMaterial(Long materialId) {
        MaterialDTO md = support.material(materialId);
        MaterialPlanAttr a = support.materialApi().getPlanAttr(materialId);
        boolean make = a != null && a.sourceType() != null ? !"PURCHASE".equals(a.sourceType().name()) : md.sourceType() == null || !"PURCHASE".equals(md.sourceType().name());
        if (!make || data.bomApi().getDefaultBom(materialId, LocalDate.now()).isEmpty()) throw BizException.of(PmcErrorCodes.MPS_NOT_MAKE, md.code());
    }

    /** 整体保存矩阵：过去的周不能修改（R04） */
    @Transactional(rollbackFor = Exception.class)
    public void saveMatrix(Long id, List<RowSave> rows, Integer version) {
        PmcMpsDO m = requireDraft(id);
        if (version != null) {
            m.setVersion(version);
            mapper.updateByIdOrFail(m);
        }
        List<String> weeks = Weeks.range(m.getStartWeek(), m.getEndWeek());
        String current = Weeks.current();
        Map<String, PmcMpsLineDO> existing = lineMapper.selectByParent(id).stream()
                .collect(Collectors.toMap(l -> l.getMaterialId() + "|" + l.getWeek(), l -> l, (a, b) -> a));
        Set<Long> keep = new LinkedHashSet<>();
        for (RowSave r : rows == null ? List.<RowSave>of() : rows) {
            if (r.materialId() == null || !keep.add(r.materialId())) continue;
            boolean isNew = existing.keySet().stream().noneMatch(k -> k.startsWith(r.materialId() + "|"));
            if (isNew) validateMaterial(r.materialId());
            Map<String, CellSave> cells = new HashMap<>();
            if (r.cells() != null) r.cells().forEach(c -> cells.put(c.week(), c));
            for (String w : weeks) {
                PmcMpsLineDO l = existing.get(r.materialId() + "|" + w);
                CellSave c = cells.get(w);
                BigDecimal plan = c == null || c.plannedQty() == null ? (l == null ? BigDecimal.ZERO : l.getPlannedQty()) : PmcSupport.max0(c.plannedQty());
                BigDecimal old = l == null ? BigDecimal.ZERO : l.getPlannedQty();
                if (w.compareTo(current) < 0 && plan.compareTo(old) != 0) throw BizException.of(PmcErrorCodes.MPS_PAST_WEEK, w);
                if (l == null) {
                    l = new PmcMpsLineDO();
                    l.setMpsId(id);
                    l.setMaterialId(r.materialId());
                    l.setWeek(w);
                    l.setDemandQty(BigDecimal.ZERO);
                    l.setWipQty(BigDecimal.ZERO);
                    l.setProjectedOnHand(BigDecimal.ZERO);
                    l.setPlannedQty(plan);
                    l.setRemark(c == null ? null : PmcSupport.trim(c.remark()));
                    lineMapper.insert(l);
                    existing.put(r.materialId() + "|" + w, l);
                } else {
                    l.setPlannedQty(plan);
                    if (c != null) l.setRemark(PmcSupport.trim(c.remark()));
                    lineMapper.updateByIdOrFail(l);
                }
            }
        }
        for (PmcMpsLineDO l : existing.values()) if (!keep.contains(l.getMaterialId())) lineMapper.deleteById(l.getId());
        refresh(m, lineMapper.selectByParent(id));
    }

    /**
     * 按需求生成：计划 = max(0, 需求 + 安全库存 − 上周结存 − 在制)，按 MPQ 取整；过去的周不变。
     * 没有物料时，加入期间内有需求的自制件（有 BOM）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void generate(Long id, List<Long> materialIds) {
        PmcMpsDO m = requireDraft(id);
        List<String> weeks = Weeks.range(m.getStartWeek(), m.getEndWeek());
        List<PmcMpsLineDO> lines = lineMapper.selectByParent(id);
        Set<Long> mids = new LinkedHashSet<>(lines.stream().map(PmcMpsLineDO::getMaterialId).toList());
        if (materialIds != null) {
            for (Long mid : materialIds) if (!mids.contains(mid)) {
                validateMaterial(mid);
                mids.add(mid);
            }
        }
        if (mids.isEmpty()) {
            LocalDate end = Weeks.sunday(m.getEndWeek());
            for (PmcDemandDO d : demandService.openDemands(end)) {
                if (mids.contains(d.getMaterialId())) continue;
                try {
                    validateMaterial(d.getMaterialId());
                    mids.add(d.getMaterialId());
                } catch (BizException ignored) {
                    // 采购件、没有 BOM 的物料不编入 MPS
                }
            }
        }
        Calc c = calc(m, mids);
        Map<String, PmcMpsLineDO> existing = lines.stream().collect(Collectors.toMap(l -> l.getMaterialId() + "|" + l.getWeek(), l -> l, (a, b) -> a));
        String current = Weeks.current();
        for (Long mid : mids) {
            MaterialPlanAttr a = c.attrs().get(mid);
            BigDecimal ss = a == null ? BigDecimal.ZERO : PmcSupport.nz(a.safetyStock());
            BigDecimal mpq = a == null ? null : a.mpq();
            BigDecimal proj = c.opening().getOrDefault(mid, BigDecimal.ZERO);
            for (String w : weeks) {
                PmcMpsLineDO l = existing.get(mid + "|" + w);
                BigDecimal dem = c.demand().getOrDefault(mid, Map.of()).getOrDefault(w, BigDecimal.ZERO);
                BigDecimal wp = c.wip().getOrDefault(mid, Map.of()).getOrDefault(w, BigDecimal.ZERO);
                BigDecimal plan;
                if (w.compareTo(current) < 0) {
                    plan = l == null ? BigDecimal.ZERO : l.getPlannedQty();
                } else {
                    plan = PmcSupport.max0(dem.add(ss).subtract(proj).subtract(wp));
                    if (plan.signum() > 0 && mpq != null && mpq.signum() > 0) plan = plan.divide(mpq, 0, RoundingMode.CEILING).multiply(mpq);
                }
                proj = proj.add(plan).add(wp).subtract(dem);
                if (l == null) {
                    l = new PmcMpsLineDO();
                    l.setMpsId(id);
                    l.setMaterialId(mid);
                    l.setWeek(w);
                    l.setPlannedQty(plan);
                    l.setDemandQty(dem);
                    l.setWipQty(wp);
                    l.setProjectedOnHand(proj);
                    lineMapper.insert(l);
                } else {
                    l.setPlannedQty(plan);
                    l.setDemandQty(dem);
                    l.setWipQty(wp);
                    l.setProjectedOnHand(proj);
                    lineMapper.updateByIdOrFail(l);
                }
            }
        }
    }

    /** 产能检查：按产品工艺路线换算各工作中心每周负荷（计划 × 标准工时 + 准备），与每周产能比较 */
    public List<CapRow> capacityCheck(Long id) {
        PmcMpsDO m = getOrThrow(id);
        List<String> weeks = Weeks.range(m.getStartWeek(), m.getEndWeek());
        Map<Long, Map<String, BigDecimal>> load = new LinkedHashMap<>();
        Map<Long, RoutingDTO> routings = new HashMap<>();
        for (PmcMpsLineDO l : lineMapper.selectByParent(id)) {
            if (l.getPlannedQty() == null || l.getPlannedQty().signum() <= 0) continue;
            RoutingDTO r = routings.computeIfAbsent(l.getMaterialId(), k -> data.routingApi().getDefaultRouting(k).orElse(null));
            if (r == null) continue;
            for (RoutingDTO.Step s : r.steps()) {
                if (s.workCenterId() == null) continue;
                BigDecimal h = CapacityService.loadHours(l.getPlannedQty(), s.setupMinutes(), s.runSeconds());
                load.computeIfAbsent(s.workCenterId(), k -> new HashMap<>()).merge(l.getWeek(), h, BigDecimal::add);
            }
        }
        CapacityService.Calendar cal = capacityService.calendar(Weeks.monday(m.getStartWeek()), Weeks.sunday(m.getEndWeek()));
        Map<Long, WorkCenterDTO> wcs = cal.workCenters();
        List<CapRow> out = new ArrayList<>();
        load.forEach((wc, byWeek) -> {
            List<CapCell> cells = new ArrayList<>();
            for (String w : weeks) {
                BigDecimal cap = BigDecimal.ZERO;
                for (LocalDate d = Weeks.monday(w); !d.isAfter(Weeks.sunday(w)); d = d.plusDays(1)) cap = cap.add(cal.hours(wc, d));
                BigDecimal h = byWeek.getOrDefault(w, BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
                cells.add(new CapCell(w, h, cap, h.compareTo(cap) > 0));
            }
            WorkCenterDTO w = wcs.get(wc);
            out.add(new CapRow(wc, w == null ? null : w.code(), w == null ? null : w.name(), cells));
        });
        return out;
    }
}
