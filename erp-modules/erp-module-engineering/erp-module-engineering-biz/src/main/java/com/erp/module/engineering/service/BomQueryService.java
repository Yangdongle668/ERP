package com.erp.module.engineering.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.common.enums.DocStatus;
import com.erp.common.exception.BizException;
import com.erp.common.result.PageResult;
import com.erp.common.util.Decimals;
import com.erp.module.engineering.api.EngineeringErrorCodes;
import com.erp.module.engineering.api.bom.BomApi;
import com.erp.module.engineering.api.bom.BomDTO;
import com.erp.module.engineering.api.bom.BomExplodeLine;
import com.erp.module.engineering.api.material.MaterialType;
import com.erp.module.engineering.controller.vo.BomVOs.BomDetail;
import com.erp.module.engineering.controller.vo.BomVOs.BomQuery;
import com.erp.module.engineering.controller.vo.BomVOs.BomRow;
import com.erp.module.engineering.controller.vo.BomVOs.CompareResult;
import com.erp.module.engineering.controller.vo.BomVOs.CompareRow;
import com.erp.module.engineering.controller.vo.BomVOs.CostResult;
import com.erp.module.engineering.controller.vo.BomVOs.ExplodeRow;
import com.erp.module.engineering.controller.vo.BomVOs.LineResp;
import com.erp.module.engineering.controller.vo.BomVOs.Side;
import com.erp.module.engineering.controller.vo.BomVOs.SubstituteResp;
import com.erp.module.engineering.controller.vo.BomVOs.WhereUsedRow;
import com.erp.module.engineering.controller.vo.MaterialVOs.BomBrief;
import com.erp.module.engineering.controller.vo.MaterialVOs.MaterialBoms;
import com.erp.module.engineering.dal.dataobject.BomDO;
import com.erp.module.engineering.dal.dataobject.BomLineDO;
import com.erp.module.engineering.dal.dataobject.BomSubstituteDO;
import com.erp.module.engineering.dal.dataobject.MaterialDO;
import com.erp.module.engineering.dal.mapper.BomLineMapper;
import com.erp.module.engineering.dal.mapper.BomMapper;
import com.erp.module.engineering.dal.mapper.BomSubstituteMapper;
import com.erp.module.engineering.dal.mapper.MaterialMapper;
import com.erp.module.system.api.param.ParamApi;
import com.erp.module.system.api.uom.UomApi;
import com.erp.module.system.api.user.UserApi;
import com.erp.module.system.api.user.UserDTO;
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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * BOM 查询（列表、详情、多级展开、反查、版本比较、成本卷算、低位码），同时是 {@link BomApi} 的实现。
 *
 * <p>需求量统一口径（需求 05-03 第 3 节）：子件需求量 = 父件数量 ÷ 基数 × 用量 × (1 + 损耗率)，逐层按子件单位精度向上取整；
 * 虚拟件在 MRP 口径中透过（不作为需求出现）。
 */
@Service
public class BomQueryService implements BomApi {

    static final String PARAM_MAX_LEVEL = "eng.bom.max-level";
    static final int CALC_SCALE = 10;
    private static final List<DocStatus> LIVE = List.of(DocStatus.DRAFT, DocStatus.PENDING_APPROVAL, DocStatus.APPROVED);

    private final BomMapper bomMapper;
    private final BomLineMapper lineMapper;
    private final BomSubstituteMapper substituteMapper;
    private final MaterialMapper materialMapper;
    private final UomApi uomApi;
    private final ParamApi paramApi;
    private final UserApi userApi;

    public BomQueryService(BomMapper bomMapper, BomLineMapper lineMapper, BomSubstituteMapper substituteMapper, MaterialMapper materialMapper,
                           UomApi uomApi, ParamApi paramApi, UserApi userApi) {
        this.bomMapper = bomMapper;
        this.lineMapper = lineMapper;
        this.substituteMapper = substituteMapper;
        this.materialMapper = materialMapper;
        this.uomApi = uomApi;
        this.paramApi = paramApi;
        this.userApi = userApi;
    }

    // ==================== 列表与详情 ====================

    public PageResult<BomRow> page(BomQuery q) {
        LambdaQueryWrapper<BomDO> w = query(q);
        if (w == null) return PageResult.empty();
        PageResult<BomDO> page = bomMapper.page(q, w);
        return new PageResult<>(toRows(page.list()), page.total());
    }

    /** 导出用：按查询条件或勾选的 ID */
    public List<BomDO> listForExport(BomQuery q, int limit) {
        if (StringUtils.hasText(q.getIds())) {
            List<Long> ids = Arrays.stream(q.getIds().split(",")).map(String::trim).filter(s -> s.matches("\\d+")).map(Long::valueOf).toList();
            return ids.isEmpty() ? List.of() : bomMapper.selectBatchIds(ids);
        }
        LambdaQueryWrapper<BomDO> w = query(q);
        return w == null ? List.of() : bomMapper.selectList(w.last("LIMIT " + limit));
    }

    private LambdaQueryWrapper<BomDO> query(BomQuery q) {
        LambdaQueryWrapper<BomDO> w = new LambdaQueryWrapper<BomDO>()
                .eq(q.getMaterialId() != null, BomDO::getMaterialId, q.getMaterialId())
                .eq(Boolean.TRUE.equals(q.getDefaultOnly()), BomDO::getIsDefault, true);
        if (StringUtils.hasText(q.getStatuses())) {
            w.in(BomDO::getStatus, Arrays.stream(q.getStatuses().split(",")).map(String::trim).filter(StringUtils::hasText).map(DocStatus::valueOf).toList());
        }
        if (StringUtils.hasText(q.getKeyword())) {
            String k = q.getKeyword().trim();
            List<Long> ids = materialMapper.selectList(new LambdaQueryWrapper<MaterialDO>().select(MaterialDO::getId)
                            .and(x -> x.likeRight(MaterialDO::getCode, k.toUpperCase()).or().like(MaterialDO::getName, k)).last("LIMIT 2000"))
                    .stream().map(MaterialDO::getId).toList();
            if (ids.isEmpty()) return null;
            w.in(BomDO::getMaterialId, ids);
        }
        if (q.getComponentId() != null) {
            w.inSql(BomDO::getId, "SELECT bom_id FROM eng_bom_line WHERE deleted = 0 AND component_id = " + q.getComponentId().longValue());
        }
        return w.orderByAsc(BomDO::getDocNo);
    }

    private List<BomRow> toRows(List<BomDO> list) {
        Map<Long, MaterialDO> materials = materials(list.stream().map(BomDO::getMaterialId).toList());
        Map<Long, UserDTO> users = users(list.stream().map(BomDO::getUpdatedBy).toList());
        return list.stream().map(b -> {
            MaterialDO m = materials.get(b.getMaterialId());
            return new BomRow(b.getId(), b.getDocNo(), b.getMaterialId(), m == null ? null : m.getCode(), m == null ? null : m.getName(),
                    m == null ? null : m.getSpec(), m == null ? null : m.getMaterialType(), m == null ? null : m.getBaseUom(), b.getBomVersion(),
                    b.getBaseQty(), Boolean.TRUE.equals(b.getIsDefault()), nz(b.getLineCount()), b.getDescription(), b.getStatus().name(),
                    name(users, b.getUpdatedBy()), b.getUpdatedAt());
        }).toList();
    }

    public BomDetail detail(Long id) {
        BomDO b = getOrThrow(id);
        List<BomLineDO> lines = lineMapper.selectByBom(id);
        List<BomSubstituteDO> subs = substituteMapper.selectByBoms(List.of(id));
        Set<Long> ids = new HashSet<>();
        ids.add(b.getMaterialId());
        lines.forEach(l -> ids.add(l.getComponentId()));
        subs.forEach(s -> ids.add(s.getSubstituteId()));
        Map<Long, MaterialDO> materials = materials(ids);
        Map<Long, UserDTO> users = users(List.of(nzId(b.getCreatedBy()), nzId(b.getUpdatedBy())));
        Map<Long, List<BomSubstituteDO>> subsByLine = subs.stream().collect(Collectors.groupingBy(BomSubstituteDO::getBomLineId));
        List<LineResp> lineResps = lines.stream().map(l -> {
            MaterialDO c = materials.get(l.getComponentId());
            List<SubstituteResp> sr = subsByLine.getOrDefault(l.getId(), List.of()).stream().map(s -> {
                MaterialDO sm = materials.get(s.getSubstituteId());
                return new SubstituteResp(s.getSubstituteId(), sm == null ? null : sm.getCode(), sm == null ? null : sm.getName(),
                        sm == null ? null : sm.getSpec(), sm == null ? null : sm.getBaseUom(), sm == null ? null : sm.getStatus(),
                        s.getPriority(), s.getRatio(), s.getRemark());
            }).toList();
            return new LineResp(l.getId(), l.getLineNo(), l.getComponentId(), c == null ? null : c.getCode(), c == null ? null : c.getName(),
                    c == null ? null : c.getSpec(), c == null ? null : c.getMaterialType(), c == null ? null : c.getStatus(), l.getUom(),
                    l.getQtyPer(), l.getScrapRate(), l.getPositionNo(), l.getIssueMethod(), l.getOperationSeq(), Boolean.TRUE.equals(l.getIsKey()),
                    l.getRemark(), sr);
        }).toList();
        MaterialDO m = materials.get(b.getMaterialId());
        String copiedNo = b.getCopiedFromId() == null ? null : Optional.ofNullable(bomMapper.selectById(b.getCopiedFromId())).map(BomDO::getDocNo).orElse(null);
        return new BomDetail(b.getId(), b.getDocNo(), b.getMaterialId(), m == null ? null : m.getCode(), m == null ? null : m.getName(),
                m == null ? null : m.getSpec(), m == null ? null : m.getMaterialType(), m == null ? null : m.getStatus(),
                m == null ? null : m.getBaseUom(), b.getBomVersion(), b.getBaseQty(), Boolean.TRUE.equals(b.getIsDefault()), b.getEffectiveDate(),
                b.getDescription(), b.getRemark(), b.getStatus().name(), b.getCopiedFromId(), copiedNo,
                name(users, b.getCreatedBy()), b.getCreatedAt(), name(users, b.getUpdatedBy()), b.getUpdatedAt(), b.getVersion(), lineResps);
    }

    public BomDO getOrThrow(Long id) {
        BomDO b = id == null ? null : bomMapper.selectById(id);
        if (b == null) throw new BizException(EngineeringErrorCodes.BOM_NOT_EXISTS);
        return b;
    }

    public int nextVersion(Long materialId) {
        return bomMapper.maxVersion(materialId) + 1;
    }

    // ==================== 多级展开 ====================

    /**
     * 展开指定 BOM 版本（详情页“多级展开”“成本”）：第一层为该版本的行，下级使用各子件的默认已审核 BOM；
     * 虚拟件作为一行显示（phantom=true）并继续展开。
     */
    public List<ExplodeRow> explodeTree(Long bomId, BigDecimal qty, int levels) {
        BomDO bom = getOrThrow(bomId);
        Ctx ctx = new Ctx(levels);
        Set<Long> ancestors = new HashSet<>();
        ancestors.add(bom.getMaterialId());
        BigDecimal top = qty == null || qty.signum() <= 0 ? BigDecimal.ONE : qty;
        return expand(bom, BigDecimal.ONE, top, 1, "", ancestors, ctx);
    }

    public CostResult cost(Long bomId) {
        List<ExplodeRow> rows = explodeTree(bomId, BigDecimal.ONE, 0);
        BigDecimal total = BigDecimal.ZERO;
        int missing = 0;
        for (ExplodeRow r : rows) total = total.add(Decimals.nullToZero(r.costAmount()));
        for (ExplodeRow r : flatten(rows)) if (r.costMissing()) missing++;
        return new CostResult(Decimals.price(total), missing, rows);
    }

    private final class Ctx {
        final int maxLevels;
        final Map<Long, MaterialDO> materials = new HashMap<>();
        final Map<Long, Optional<BomDO>> defaults = new HashMap<>();
        final Map<Long, List<BomLineDO>> lines = new HashMap<>();

        Ctx(int levels) {
            int limit = paramApi.getInt(PARAM_MAX_LEVEL);
            this.maxLevels = levels <= 0 ? limit : Math.min(levels, limit);
        }

        MaterialDO material(Long id) {
            return materials.computeIfAbsent(id, materialMapper::selectById);
        }

        Optional<BomDO> defaultBom(Long materialId) {
            return defaults.computeIfAbsent(materialId, k -> Optional.ofNullable(bomMapper.selectDefault(k)));
        }

        List<BomLineDO> linesOf(Long bomId) {
            return lines.computeIfAbsent(bomId, lineMapper::selectByBom);
        }
    }

    /**
     * @param parentTotal    上级相对 1 个顶层父件的累计用量
     * @param parentRequired 上级的需求量（虚拟件为未取整值）
     */
    private List<ExplodeRow> expand(BomDO bom, BigDecimal parentTotal, BigDecimal parentRequired, int level, String prefix,
                                    Set<Long> ancestors, Ctx ctx) {
        List<ExplodeRow> rows = new ArrayList<>();
        int i = 0;
        for (BomLineDO l : ctx.linesOf(bom.getId())) {
            i++;
            MaterialDO c = ctx.material(l.getComponentId());
            if (c == null) continue;
            String path = prefix.isEmpty() ? String.valueOf(i) : prefix + "." + i;
            BigDecimal per = l.getQtyPer().divide(bom.getBaseQty(), CALC_SCALE, RoundingMode.HALF_UP);
            BigDecimal factor = per.multiply(BigDecimal.ONE.add(Decimals.nullToZero(l.getScrapRate())));
            BigDecimal total = parentTotal.multiply(factor).setScale(CALC_SCALE, RoundingMode.HALF_UP);
            boolean phantom = c.getMaterialType() == MaterialType.PHANTOM;
            BigDecimal raw = parentRequired.multiply(factor);
            BigDecimal required = phantom ? raw.setScale(CALC_SCALE, RoundingMode.HALF_UP) : roundUp(raw, l.getUom());
            Optional<BomDO> child = ctx.defaultBom(c.getId());
            List<ExplodeRow> children = List.of();
            if (child.isPresent() && level < ctx.maxLevels && !ancestors.contains(c.getId())) {
                ancestors.add(c.getId());
                children = expand(child.get(), total, required, level + 1, path, ancestors, ctx);
                ancestors.remove(c.getId());
            }
            BigDecimal unitCost = null;
            BigDecimal amount;
            boolean missing = false;
            if (children.isEmpty()) {
                unitCost = c.getStandardCost();
                missing = unitCost == null;
                amount = unitCost == null ? null : Decimals.price(total.multiply(unitCost));
            } else {
                amount = children.stream().map(ExplodeRow::costAmount).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
            }
            rows.add(new ExplodeRow(bom.getId() + ":" + l.getId() + ":" + path, level, path, bom.getMaterialId(), c.getId(), c.getCode(),
                    c.getName(), c.getSpec(), c.getMaterialType(), c.getSourceType(), l.getUom(), per.stripTrailingZeros(),
                    l.getScrapRate(), strip(total), required.stripTrailingZeros(), l.getIssueMethod(), phantom,
                    child.map(BomDO::getId).orElse(null), child.map(BomDO::getBomVersion).orElse(null), unitCost, amount, missing, children));
        }
        return rows;
    }

    private BigDecimal roundUp(BigDecimal v, String uom) {
        int precision;
        try {
            precision = uomApi.precision(uom);
        } catch (BizException e) {
            precision = Decimals.QTY_SCALE;
        }
        return v.setScale(precision, RoundingMode.UP);
    }

    static List<ExplodeRow> flatten(List<ExplodeRow> rows) {
        List<ExplodeRow> out = new ArrayList<>();
        for (ExplodeRow r : rows) {
            out.add(r);
            out.addAll(flatten(r.children()));
        }
        return out;
    }

    // ==================== 反查 ====================

    /** 多级反查：直接使用该物料的 BOM（非作废），再向上找这些父件被哪些 BOM 使用，直到顶层 */
    public List<WhereUsedRow> whereUsedTree(Long materialId) {
        return whereUsed(materialId, 1, "", new HashSet<>(Set.of(materialId)));
    }

    private List<WhereUsedRow> whereUsed(Long materialId, int level, String prefix, Set<Long> visiting) {
        if (level > 30) return List.of();
        List<BomLineDO> lines = lineMapper.selectByComponent(materialId);
        if (lines.isEmpty()) return List.of();
        Map<Long, BomDO> boms = bomMapper.selectBatchIds(lines.stream().map(BomLineDO::getBomId).collect(Collectors.toSet())).stream()
                .filter(b -> b.getStatus() != DocStatus.VOIDED).collect(Collectors.toMap(BomDO::getId, b -> b));
        Map<Long, MaterialDO> materials = materials(boms.values().stream().map(BomDO::getMaterialId).toList());
        List<WhereUsedRow> rows = new ArrayList<>();
        List<BomLineDO> sorted = lines.stream().filter(l -> boms.containsKey(l.getBomId()))
                .sorted(Comparator.comparing(l -> boms.get(l.getBomId()).getDocNo())).toList();
        for (BomLineDO l : sorted) {
            BomDO b = boms.get(l.getBomId());
            MaterialDO p = materials.get(b.getMaterialId());
            List<WhereUsedRow> parents = List.of();
            if (!visiting.contains(b.getMaterialId())) {
                visiting.add(b.getMaterialId());
                parents = whereUsed(b.getMaterialId(), level + 1, prefix + b.getId() + "/", visiting);
                visiting.remove(b.getMaterialId());
            }
            rows.add(new WhereUsedRow(prefix + b.getId() + ":" + l.getId(), level, b.getId(), b.getDocNo(), b.getMaterialId(),
                    p == null ? null : p.getCode(), p == null ? null : p.getName(), p == null ? null : p.getSpec(),
                    p == null ? null : p.getMaterialType(), b.getBomVersion(), Boolean.TRUE.equals(b.getIsDefault()), b.getStatus().name(),
                    l.getQtyPer(), l.getUom(), parents.isEmpty(), parents));
        }
        return rows;
    }

    /** 物料详情“BOM”页签：以本物料为父件的版本、直接使用本物料的 BOM */
    public MaterialBoms materialBoms(Long materialId) {
        MaterialDO self = materialMapper.selectById(materialId);
        List<BomBrief> asParent = bomMapper.selectByMaterial(materialId).stream()
                .map(b -> new BomBrief(b.getId(), b.getDocNo(), materialId, self == null ? null : self.getCode(), self == null ? null : self.getName(),
                        b.getBomVersion(), Boolean.TRUE.equals(b.getIsDefault()), b.getStatus().name(), null, null)).toList();
        // 只取直接使用（从第 30 层开始，不再向上递归）
        List<BomBrief> asComponent = whereUsed(materialId, 30, "", new HashSet<>(Set.of(materialId))).stream()
                .map(r -> new BomBrief(r.bomId(), r.docNo(), r.materialId(), r.materialCode(), r.materialName(), r.version(), r.isDefault(),
                        r.status(), r.qtyPer(), r.uom())).toList();
        return new MaterialBoms(asParent, asComponent);
    }

    // ==================== 版本比较 ====================

    public CompareResult compare(Long leftId, Long rightId) {
        BomDO left = getOrThrow(leftId);
        BomDO right = getOrThrow(rightId);
        Map<Long, BomLineDO> l = lineMapper.selectByBom(leftId).stream().collect(Collectors.toMap(BomLineDO::getComponentId, x -> x, (a, b) -> a, LinkedHashMap::new));
        Map<Long, BomLineDO> r = lineMapper.selectByBom(rightId).stream().collect(Collectors.toMap(BomLineDO::getComponentId, x -> x, (a, b) -> a, LinkedHashMap::new));
        Map<Long, Long> subCount = new HashMap<>();
        substituteMapper.selectByBoms(List.of(leftId, rightId)).forEach(s -> subCount.merge(s.getBomLineId(), 1L, Long::sum));
        Set<Long> ids = new java.util.LinkedHashSet<>(l.keySet());
        ids.addAll(r.keySet());
        Map<Long, MaterialDO> materials = materials(ids);
        List<CompareRow> rows = new ArrayList<>();
        for (Long id : ids) {
            BomLineDO a = l.get(id);
            BomLineDO b = r.get(id);
            Side sa = side(a, subCount, left.getBaseQty());
            Side sb = side(b, subCount, right.getBaseQty());
            List<String> changed = new ArrayList<>();
            String change;
            if (a == null) change = "ADDED";
            else if (b == null) change = "REMOVED";
            else {
                if (sa.qtyPer().compareTo(sb.qtyPer()) != 0) changed.add("qtyPer");
                if (Decimals.nullToZero(sa.scrapRate()).compareTo(Decimals.nullToZero(sb.scrapRate())) != 0) changed.add("scrapRate");
                if (!Objects.equals(Objects.toString(sa.positionNo(), ""), Objects.toString(sb.positionNo(), ""))) changed.add("positionNo");
                if (sa.issueMethod() != sb.issueMethod()) changed.add("issueMethod");
                if (sa.isKey() != sb.isKey()) changed.add("isKey");
                if (sa.substituteCount() != sb.substituteCount()) changed.add("substitutes");
                change = changed.isEmpty() ? "SAME" : "CHANGED";
            }
            MaterialDO m = materials.get(id);
            rows.add(new CompareRow(id, m == null ? null : m.getCode(), m == null ? null : m.getName(), m == null ? null : m.getSpec(),
                    m == null ? null : m.getBaseUom(), change, changed, sa, sb));
        }
        return new CompareResult(leftId, left.getDocNo(), rightId, right.getDocNo(), rows);
    }

    /** 比较时把用量折算为每 1 个父件（两个版本的基数可能不同） */
    private static Side side(BomLineDO l, Map<Long, Long> subCount, BigDecimal baseQty) {
        if (l == null) return null;
        BigDecimal per = l.getQtyPer().divide(baseQty, CALC_SCALE, RoundingMode.HALF_UP).stripTrailingZeros();
        return new Side(per, l.getScrapRate(), l.getPositionNo(), l.getIssueMethod(), Boolean.TRUE.equals(l.getIsKey()),
                subCount.getOrDefault(l.getId(), 0L).intValue());
    }

    // ==================== 循环引用与层数 ====================

    /**
     * 循环引用检测（ENG-BOM-R03）：从每个子件出发，沿其全部草稿/待审批/已审核版本向下展开，不能到达父件。
     *
     * @param excludeBomId 正在编辑的 BOM（其行用 componentIds 代替）
     * @return 循环路径的物料 ID（首尾为父件），无循环时为空
     */
    public List<Long> findCycle(Long parentId, Collection<Long> componentIds, Long excludeBomId) {
        Map<Long, Set<Long>> edges = new HashMap<>();
        Set<Long> done = new HashSet<>();
        for (Long c : componentIds) {
            List<Long> path = new ArrayList<>(List.of(parentId, c));
            if (c.equals(parentId)) return path;
            if (dfs(c, parentId, path, edges, done, excludeBomId)) return path;
        }
        return List.of();
    }

    private boolean dfs(Long node, Long target, List<Long> path, Map<Long, Set<Long>> edges, Set<Long> done, Long excludeBomId) {
        if (done.contains(node) || path.size() > 64) return false;
        for (Long next : edges.computeIfAbsent(node, n -> childrenOf(n, excludeBomId))) {
            path.add(next);
            if (next.equals(target)) return true;
            if (dfs(next, target, path, edges, done, excludeBomId)) return true;
            path.remove(path.size() - 1);
        }
        done.add(node);
        return false;
    }

    private Set<Long> childrenOf(Long materialId, Long excludeBomId) {
        List<Long> bomIds = bomMapper.selectByMaterials(List.of(materialId), LIVE).stream()
                .map(BomDO::getId).filter(id -> !id.equals(excludeBomId)).toList();
        Set<Long> result = new java.util.LinkedHashSet<>();
        lineMapper.selectByBoms(bomIds).forEach(l -> result.add(l.getComponentId()));
        return result;
    }

    /** 以默认版本逐层计算的展开层数（本 BOM 为第 1 层） */
    public int depth(Collection<Long> componentIds) {
        Map<Long, Integer> memo = new HashMap<>();
        int max = 0;
        for (Long c : componentIds) max = Math.max(max, depthOf(c, memo, new HashSet<>()));
        return 1 + max;
    }

    private int depthOf(Long materialId, Map<Long, Integer> memo, Set<Long> visiting) {
        Integer cached = memo.get(materialId);
        if (cached != null) return cached;
        if (!visiting.add(materialId) || visiting.size() > 64) return 0;
        BomDO bom = bomMapper.selectDefault(materialId);
        int d = 0;
        if (bom != null) {
            int max = 0;
            for (BomLineDO l : lineMapper.selectByBom(bom.getId())) max = Math.max(max, depthOf(l.getComponentId(), memo, visiting));
            d = 1 + max;
        }
        visiting.remove(materialId);
        memo.put(materialId, d);
        return d;
    }

    // ==================== 低位码 ====================

    /** 重新计算全部物料的低位码（ENG-BOM-R11）：按默认已审核 BOM，物料出现的最深层级 */
    @Transactional(rollbackFor = Exception.class)
    public void recomputeLowLevelCodes() {
        Map<Long, Integer> codes = computeLowLevelCodes();
        Map<Long, Integer> current = materialMapper.selectNonZeroLowLevelCodes();
        // 只更新有变化的物料：不再出现在任何默认 BOM 中的归 0
        current.forEach((id, code) -> {
            if (codes.getOrDefault(id, 0) == 0) materialMapper.updateLowLevelCode(id, 0);
        });
        codes.forEach((id, code) -> {
            if (code > 0 && !Integer.valueOf(code).equals(current.get(id))) materialMapper.updateLowLevelCode(id, code);
        });
    }

    Map<Long, Integer> computeLowLevelCodes() {
        List<BomDO> defaults = bomMapper.selectAllDefaults();
        Map<Long, Long> parentOf = new HashMap<>();
        defaults.forEach(b -> parentOf.put(b.getId(), b.getMaterialId()));
        List<long[]> edges = new ArrayList<>();
        lineMapper.selectByBoms(parentOf.keySet()).forEach(l -> edges.add(new long[]{parentOf.get(l.getBomId()), l.getComponentId()}));
        Map<Long, Integer> llc = new HashMap<>();
        for (long[] e : edges) {
            llc.putIfAbsent(e[0], 0);
            llc.putIfAbsent(e[1], 0);
        }
        // 松弛：子件低位码 = max(父件低位码 + 1)；无环时最多迭代“最大层数”次
        for (int iter = 0; iter < 64; iter++) {
            boolean changed = false;
            for (long[] e : edges) {
                int next = llc.get(e[0]) + 1;
                if (llc.get(e[1]) < next) {
                    llc.put(e[1], next);
                    changed = true;
                }
            }
            if (!changed) break;
        }
        return llc;
    }

    // ==================== BomApi ====================

    @Override
    public Optional<BomDTO> getDefaultBom(Long materialId, LocalDate date) {
        return Optional.ofNullable(bomMapper.selectDefault(materialId)).map(this::toDto);
    }

    @Override
    public Optional<BomDTO> getBom(Long bomId) {
        return Optional.ofNullable(bomMapper.selectById(bomId)).map(this::toDto);
    }

    @Override
    public List<BomExplodeLine> explode(Long materialId, BigDecimal qty, LocalDate date, int levels) {
        BomDO bom = bomMapper.selectDefault(materialId);
        if (bom == null) return List.of();
        List<BomExplodeLine> out = new ArrayList<>();
        passThrough(explodeTree(bom.getId(), qty, levels), bom.getMaterialId(), 0, out);
        return out;
    }

    /** 虚拟件透过：不输出虚拟件行，其子件挂到上一层 */
    private static void passThrough(List<ExplodeRow> rows, Long parentId, int levelShift, List<BomExplodeLine> out) {
        for (ExplodeRow r : rows) {
            if (r.phantom()) {
                passThrough(r.children(), parentId, levelShift + 1, out);
                continue;
            }
            out.add(new BomExplodeLine(r.level() - levelShift, r.path(), parentId, r.componentId(), r.materialType(), r.sourceType(), r.uom(),
                    r.qtyPer(), r.scrapRate(), r.totalQtyPer(), r.requiredQty(), r.issueMethod(), null, r.bomId(), r.bomVersion()));
            passThrough(r.children(), r.componentId(), levelShift, out);
        }
    }

    @Override
    public List<BomDTO> whereUsed(Long componentId) {
        Set<Long> bomIds = lineMapper.selectByComponent(componentId).stream().map(BomLineDO::getBomId).collect(Collectors.toSet());
        if (bomIds.isEmpty()) return List.of();
        return bomMapper.selectBatchIds(bomIds).stream().filter(b -> b.getStatus() == DocStatus.APPROVED).map(this::toDto).toList();
    }

    @Override
    public Map<Long, Integer> getLowLevelCodes() {
        Map<Long, Integer> result = new HashMap<>();
        computeLowLevelCodes().forEach((k, v) -> {
            if (v > 0) result.put(k, v);
        });
        return result;
    }

    private BomDTO toDto(BomDO b) {
        List<BomLineDO> lines = lineMapper.selectByBom(b.getId());
        Map<Long, List<BomSubstituteDO>> subs = substituteMapper.selectByBoms(List.of(b.getId())).stream()
                .collect(Collectors.groupingBy(BomSubstituteDO::getBomLineId));
        return new BomDTO(b.getId(), b.getDocNo(), b.getMaterialId(), b.getBomVersion(), b.getBaseQty(), Boolean.TRUE.equals(b.getIsDefault()),
                b.getEffectiveDate(), b.getStatus(), lines.stream().map(l -> new BomDTO.Line(l.getId(), l.getLineNo(), l.getComponentId(),
                        l.getQtyPer(), l.getUom(), l.getScrapRate(), l.getPositionNo(), l.getIssueMethod(), l.getOperationSeq(),
                        Boolean.TRUE.equals(l.getIsKey()), subs.getOrDefault(l.getId(), List.of()).stream()
                        .map(s -> new BomDTO.Substitute(s.getSubstituteId(), s.getPriority(), s.getRatio())).toList())).toList());
    }

    // ==================== 工具 ====================

    Map<Long, MaterialDO> materials(Collection<Long> ids) {
        Set<Long> set = ids.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (set.isEmpty()) return Map.of();
        return materialMapper.selectBatchIds(set).stream().collect(Collectors.toMap(MaterialDO::getId, Function.identity()));
    }

    private Map<Long, UserDTO> users(Collection<Long> ids) {
        Set<Long> set = ids.stream().filter(Objects::nonNull).filter(id -> id > 0).collect(Collectors.toSet());
        return set.isEmpty() ? Map.of() : userApi.list(set);
    }

    private static String name(Map<Long, UserDTO> users, Long id) {
        return id == null || !users.containsKey(id) ? null : users.get(id).realName();
    }

    private static Long nzId(Long id) {
        return id == null ? 0L : id;
    }

    private static int nz(Integer v) {
        return v == null ? 0 : v;
    }

    private static BigDecimal strip(BigDecimal v) {
        return v.setScale(8, RoundingMode.HALF_UP).stripTrailingZeros();
    }
}
