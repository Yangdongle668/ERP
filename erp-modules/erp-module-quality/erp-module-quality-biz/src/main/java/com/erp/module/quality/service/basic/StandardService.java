package com.erp.module.quality.service.basic;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.common.exception.BizException;
import com.erp.common.result.PageResult;
import com.erp.module.engineering.api.category.MaterialCategoryApi;
import com.erp.module.engineering.api.category.MaterialCategoryDTO;
import com.erp.module.engineering.api.material.MaterialDTO;
import com.erp.module.quality.api.QualityErrorCodes;
import com.erp.module.quality.api.inspection.InspectType;
import com.erp.module.quality.config.QualityModuleConfig;
import com.erp.module.quality.controller.vo.BasicVOs.StandardDetail;
import com.erp.module.quality.controller.vo.BasicVOs.StandardItem;
import com.erp.module.quality.controller.vo.BasicVOs.StandardItemSave;
import com.erp.module.quality.controller.vo.BasicVOs.StandardQuery;
import com.erp.module.quality.controller.vo.BasicVOs.StandardRow;
import com.erp.module.quality.controller.vo.BasicVOs.StandardSave;
import com.erp.module.quality.dal.dataobject.QcInspectionDO;
import com.erp.module.quality.dal.dataobject.QcInspectionItemLibDO;
import com.erp.module.quality.dal.dataobject.QcSamplingPlanDO;
import com.erp.module.quality.dal.dataobject.QcStandardDO;
import com.erp.module.quality.dal.dataobject.QcStandardItemDO;
import com.erp.module.quality.dal.mapper.QcInspectionMapper;
import com.erp.module.quality.dal.mapper.QcStandardItemMapper;
import com.erp.module.quality.dal.mapper.QcStandardMapper;
import com.erp.module.quality.service.QcAction;
import com.erp.module.quality.service.QcStateMachines;
import com.erp.module.quality.service.QcSupport;
import com.erp.module.quality.service.StdStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** 检验标准：版本、生效 / 作废、匹配（10-01 第 4、6 节） */
@Service
public class StandardService {

    public static final String MATERIAL = "MATERIAL";
    public static final String CATEGORY = "CATEGORY";

    private final QcStandardMapper mapper;
    private final QcStandardItemMapper itemMapper;
    private final QcInspectionMapper inspectionMapper;
    private final SamplingService samplingService;
    private final BasicDataService basicDataService;
    private final MaterialCategoryApi categoryApi;
    private final QcSupport support;

    public StandardService(QcStandardMapper mapper, QcStandardItemMapper itemMapper, QcInspectionMapper inspectionMapper, SamplingService samplingService,
                           BasicDataService basicDataService, MaterialCategoryApi categoryApi, QcSupport support) {
        this.mapper = mapper;
        this.itemMapper = itemMapper;
        this.inspectionMapper = inspectionMapper;
        this.samplingService = samplingService;
        this.basicDataService = basicDataService;
        this.categoryApi = categoryApi;
        this.support = support;
    }

    // ==================== 查询 ====================

    public PageResult<StandardRow> page(StandardQuery q) {
        List<String> statuses = StringUtils.hasText(q.getStatuses()) ? Arrays.asList(q.getStatuses().split(",")) : List.of();
        LambdaQueryWrapper<QcStandardDO> w = new LambdaQueryWrapper<QcStandardDO>()
                .like(StringUtils.hasText(q.getCode()), QcStandardDO::getCode, q.getCode())
                .like(StringUtils.hasText(q.getName()), QcStandardDO::getName, q.getName())
                .eq(StringUtils.hasText(q.getInspectType()), QcStandardDO::getInspectType, q.getInspectType())
                .eq(q.getMaterialId() != null, QcStandardDO::getMaterialId, q.getMaterialId())
                .eq(q.getCategoryId() != null, QcStandardDO::getCategoryId, q.getCategoryId())
                .in(!statuses.isEmpty(), QcStandardDO::getStdStatus, statuses)
                .orderByAsc(QcStandardDO::getCode).orderByDesc(QcStandardDO::getStdVersion);
        PageResult<QcStandardDO> p = mapper.selectPage(q, w);
        return new PageResult<>(rows(p.list()), p.total());
    }

    private List<StandardRow> rows(List<QcStandardDO> list) {
        Map<Long, MaterialDTO> ms = support.materials(list.stream().map(QcStandardDO::getMaterialId).toList());
        Map<Long, QcSamplingPlanDO> plans = samplingService.plans(list.stream().map(QcStandardDO::getSamplingPlanId).toList());
        Map<Long, String> cats = categoryNames();
        List<StandardRow> rows = new ArrayList<>();
        for (QcStandardDO s : list) {
            MaterialDTO m = ms.get(s.getMaterialId());
            QcSamplingPlanDO sp = plans.get(s.getSamplingPlanId());
            long items = itemMapper.selectCount(new LambdaQueryWrapper<QcStandardItemDO>().eq(QcStandardItemDO::getStandardId, s.getId()));
            rows.add(new StandardRow(s.getId(), s.getCode(), s.getName(), s.getInspectType(), s.getScopeType(), s.getMaterialId(), m == null ? null : m.code(),
                    m == null ? null : m.name(), s.getCategoryId(), categoryText(s, cats), s.getOperation(), s.getSamplingPlanId(), sp == null ? null : sp.getName(),
                    s.getStdVersion(), (int) items, s.getStdStatus(), s.getUpdatedAt()));
        }
        return rows;
    }

    private Map<Long, String> categoryNames() {
        return categoryApi.listAll().stream().collect(Collectors.toMap(MaterialCategoryDTO::id, MaterialCategoryDTO::name, (a, b) -> a));
    }

    private static String categoryText(QcStandardDO s, Map<Long, String> cats) {
        if (!CATEGORY.equals(s.getScopeType())) return null;
        return s.getCategoryId() == null ? "通用" : cats.getOrDefault(s.getCategoryId(), String.valueOf(s.getCategoryId()));
    }

    public StandardDetail detail(Long id) {
        QcStandardDO s = get(id);
        List<QcStandardItemDO> items = itemMapper.selectByParent(id);
        Map<Long, QcSamplingPlanDO> plans = samplingService.plans(items.stream().map(QcStandardItemDO::getSamplingPlanId).toList());
        MaterialDTO m = s.getMaterialId() == null ? null : support.materials(List.of(s.getMaterialId())).get(s.getMaterialId());
        QcSamplingPlanDO sp = samplingService.plans(List.of(s.getSamplingPlanId())).get(s.getSamplingPlanId());
        List<StandardItem> itemVos = items.stream().map(i -> new StandardItem(i.getId(), i.getSeq(), i.getLibItemId(), i.getName(), i.getItemType(), i.getMethod(),
                i.getUnit(), i.getSpec(), i.getTarget(), i.getUpperLimit(), i.getLowerLimit(), i.getDefectLevel(), i.getSamplingPlanId(),
                plans.containsKey(i.getSamplingPlanId()) ? plans.get(i.getSamplingPlanId()).getName() : null, Boolean.TRUE.equals(i.getIsKey()))).toList();
        List<QcStandardDO> versions = mapper.selectList(new LambdaQueryWrapper<QcStandardDO>().eq(QcStandardDO::getCode, s.getCode())
                .orderByDesc(QcStandardDO::getStdVersion));
        return new StandardDetail(s.getId(), s.getCode(), s.getName(), s.getInspectType(), s.getScopeType(), s.getMaterialId(), m == null ? null : m.code(),
                m == null ? null : m.name(), s.getCategoryId(), categoryText(s, categoryNames()), s.getOperation(), s.getSamplingPlanId(),
                sp == null ? null : sp.getName(), s.getStdVersion(), s.getStdStatus(), s.getFileId(), s.getRemark(), s.getEffectiveAt(), s.getVersion(),
                itemVos, rows(versions));
    }

    public QcStandardDO get(Long id) {
        QcStandardDO s = id == null ? null : mapper.selectById(id);
        if (s == null) throw BizException.of(QualityErrorCodes.NOT_EXISTS, "检验标准");
        return s;
    }

    public List<QcStandardItemDO> items(Long standardId) {
        return itemMapper.selectByParent(standardId);
    }

    // ==================== 维护 ====================

    @Transactional(rollbackFor = Exception.class)
    public Long create(StandardSave req) {
        QcStandardDO s = new QcStandardDO();
        s.setCode(support.nextNo(QualityModuleConfig.STANDARD));
        s.setStdVersion(1);
        s.setStdStatus(StdStatus.DRAFT.name());
        fill(s, req);
        mapper.insert(s);
        saveItems(s, req.items());
        support.log(QualityModuleConfig.STANDARD, s.getId(), s.getCode() + " V1", QcAction.CREATE.name(), QcAction.CREATE.label(), null, s.getStdStatus(), null);
        return s.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, StandardSave req) {
        QcStandardDO s = get(id);
        if (!StdStatus.DRAFT.name().equals(s.getStdStatus())) throw new BizException(QualityErrorCodes.STD_EFFECTIVE_LOCKED);
        if (req.version() != null) s.setVersion(req.version());
        fill(s, req);
        mapper.updateByIdOrFail(s);
        saveItems(s, req.items());
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        QcStandardDO s = get(id);
        if (!StdStatus.DRAFT.name().equals(s.getStdStatus())) throw BizException.of(QualityErrorCodes.STD_NOT_DRAFT, "删除");
        if (inspectionMapper.selectCount(new LambdaQueryWrapper<QcInspectionDO>().eq(QcInspectionDO::getStandardId, id)) > 0) {
            throw BizException.of(QualityErrorCodes.IN_USE, "检验标准");
        }
        itemMapper.deleteByParent(id);
        mapper.deleteById(id);
    }

    /** 新版本：复制当前版本为草稿（同一编号只能有一个草稿） */
    @Transactional(rollbackFor = Exception.class)
    public Long newVersion(Long id) {
        QcStandardDO s = get(id);
        List<QcStandardDO> all = mapper.selectList(new LambdaQueryWrapper<QcStandardDO>().eq(QcStandardDO::getCode, s.getCode()));
        all.stream().filter(x -> StdStatus.DRAFT.name().equals(x.getStdStatus())).findFirst()
                .ifPresent(d -> { throw BizException.of(QualityErrorCodes.STD_DRAFT_EXISTS, d.getStdVersion()); });
        int ver = all.stream().mapToInt(QcStandardDO::getStdVersion).max().orElse(0) + 1;
        QcStandardDO n = new QcStandardDO();
        n.setCode(s.getCode());
        n.setName(s.getName());
        n.setInspectType(s.getInspectType());
        n.setScopeType(s.getScopeType());
        n.setMaterialId(s.getMaterialId());
        n.setCategoryId(s.getCategoryId());
        n.setOperation(s.getOperation());
        n.setSamplingPlanId(s.getSamplingPlanId());
        n.setFileId(s.getFileId());
        n.setRemark(s.getRemark());
        n.setStdVersion(ver);
        n.setStdStatus(StdStatus.DRAFT.name());
        mapper.insert(n);
        int seq = 0;
        for (QcStandardItemDO i : itemMapper.selectByParent(id)) {
            QcStandardItemDO c = new QcStandardItemDO();
            c.setStandardId(n.getId());
            c.setSeq(++seq);
            c.setLibItemId(i.getLibItemId());
            c.setName(i.getName());
            c.setItemType(i.getItemType());
            c.setMethod(i.getMethod());
            c.setUnit(i.getUnit());
            c.setSpec(i.getSpec());
            c.setTarget(i.getTarget());
            c.setUpperLimit(i.getUpperLimit());
            c.setLowerLimit(i.getLowerLimit());
            c.setDefectLevel(i.getDefectLevel());
            c.setSamplingPlanId(i.getSamplingPlanId());
            c.setIsKey(i.getIsKey());
            itemMapper.insert(c);
        }
        support.log(QualityModuleConfig.STANDARD, n.getId(), n.getCode() + " V" + ver, QcAction.NEW_VERSION.name(), QcAction.NEW_VERSION.label(), null,
                n.getStdStatus(), "由 V" + s.getStdVersion() + " 复制");
        return n.getId();
    }

    /** 生效（QC-STD-R01）：同编号的旧版本、同适用范围的其他生效标准自动作废 */
    @Transactional(rollbackFor = Exception.class)
    public void activate(Long id) {
        QcStandardDO s = get(id);
        if (itemMapper.selectByParent(id).isEmpty()) throw new BizException(QualityErrorCodes.STD_NO_ITEMS);
        List<QcStandardDO> effective = mapper.selectList(new LambdaQueryWrapper<QcStandardDO>().eq(QcStandardDO::getStdStatus, StdStatus.EFFECTIVE.name())
                .eq(QcStandardDO::getInspectType, s.getInspectType()).ne(QcStandardDO::getId, id));
        for (QcStandardDO o : effective) {
            if (o.getCode().equals(s.getCode()) || sameScope(o, s)) fire(o, QcAction.OBSOLETE, "新版本 " + s.getCode() + " V" + s.getStdVersion() + " 生效");
        }
        s.setEffectiveAt(LocalDateTime.now());
        fire(s, QcAction.ACTIVATE, null);
    }

    @Transactional(rollbackFor = Exception.class)
    public void obsolete(Long id) {
        fire(get(id), QcAction.OBSOLETE, null);
    }

    private static boolean sameScope(QcStandardDO a, QcStandardDO b) {
        return a.getScopeType().equals(b.getScopeType()) && Objects.equals(a.getMaterialId(), b.getMaterialId())
                && Objects.equals(a.getCategoryId(), b.getCategoryId()) && Objects.equals(a.getOperation(), b.getOperation());
    }

    private void fire(QcStandardDO s, QcAction action, String reason) {
        StdStatus from = StdStatus.valueOf(s.getStdStatus());
        s.setStdStatus(QcStateMachines.STANDARD.fire(from, action).name());
        mapper.updateByIdOrFail(s);
        support.log(QualityModuleConfig.STANDARD, s.getId(), s.getCode() + " V" + s.getStdVersion(), action.name(), action.label(), from.name(), s.getStdStatus(), reason);
    }

    private void fill(QcStandardDO s, StandardSave req) {
        InspectType type = InspectType.valueOf(req.inspectType());
        if (type == InspectType.RECHECK) throw BizException.of(QualityErrorCodes.NOT_EXISTS, "检验类型 RECHECK（复检使用 IQC 标准）");
        if (MATERIAL.equals(req.scopeType())) {
            if (req.materialId() == null) throw new BizException(QualityErrorCodes.STD_SCOPE_REQUIRED);
            support.material(req.materialId());
        } else if (!CATEGORY.equals(req.scopeType())) {
            throw new BizException(QualityErrorCodes.STD_SCOPE_REQUIRED);
        }
        QcSamplingPlanDO plan = samplingService.get(req.samplingPlanId());
        if (!"ENABLED".equals(plan.getPlanStatus())) throw BizException.of(QualityErrorCodes.SAMPLING_DISABLED, plan.getName());
        s.setName(req.name().trim());
        s.setInspectType(type.name());
        s.setScopeType(req.scopeType());
        s.setMaterialId(MATERIAL.equals(req.scopeType()) ? req.materialId() : null);
        s.setCategoryId(CATEGORY.equals(req.scopeType()) ? req.categoryId() : null);
        s.setOperation(type == InspectType.IPQC ? QcSupport.trim(req.operation()) : null);
        s.setSamplingPlanId(plan.getId());
        s.setFileId(req.fileId());
        s.setRemark(QcSupport.trim(req.remark()));
    }

    /** 明细整体替换；QC-STD-R02 定量项目上下限 */
    private void saveItems(QcStandardDO s, List<StandardItemSave> items) {
        if (items == null || items.isEmpty()) throw new BizException(QualityErrorCodes.STD_NO_ITEMS);
        Map<Long, QcInspectionItemLibDO> libs = basicDataService.items(items.stream().map(StandardItemSave::libItemId).toList());
        itemMapper.deleteByParent(s.getId());
        int seq = 0;
        for (StandardItemSave r : items) {
            QcInspectionItemLibDO lib = libs.get(r.libItemId());
            if (lib == null) throw BizException.of(QualityErrorCodes.NOT_EXISTS, "检验项目");
            String name = StringUtils.hasText(r.name()) ? r.name().trim() : lib.getName();
            if (BasicDataService.QUANTITATIVE.equals(lib.getItemType())) {
                if (r.upperLimit() == null && r.lowerLimit() == null) throw BizException.of(QualityErrorCodes.STD_ITEM_LIMIT_REQUIRED, name);
                if (r.upperLimit() != null && r.lowerLimit() != null && r.upperLimit().compareTo(r.lowerLimit()) < 0) {
                    throw BizException.of(QualityErrorCodes.STD_ITEM_LIMIT_INVALID, name);
                }
            }
            if (r.samplingPlanId() != null) samplingService.get(r.samplingPlanId());
            QcStandardItemDO i = new QcStandardItemDO();
            i.setStandardId(s.getId());
            i.setSeq(++seq);
            i.setLibItemId(lib.getId());
            i.setName(name);
            i.setItemType(lib.getItemType());
            i.setMethod(lib.getMethod());
            i.setUnit(lib.getUnit());
            i.setSpec(QcSupport.trim(r.spec()));
            boolean quant = BasicDataService.QUANTITATIVE.equals(lib.getItemType());
            i.setTarget(quant ? r.target() : null);
            i.setUpperLimit(quant ? r.upperLimit() : null);
            i.setLowerLimit(quant ? r.lowerLimit() : null);
            i.setDefectLevel(BasicDataService.level(StringUtils.hasText(r.defectLevel()) ? r.defectLevel() : lib.getDefectLevel()));
            i.setSamplingPlanId(r.samplingPlanId());
            i.setIsKey(Boolean.TRUE.equals(r.isKey()));
            itemMapper.insert(i);
        }
    }

    // ==================== 匹配 ====================

    /**
     * 匹配生效标准：物料 + 类型（IPQC 先按工序，再取不限工序）→ 物料类别逐级向上 → 通用标准（类别为空）。复检使用 IQC 标准。
     */
    public QcStandardDO match(Long materialId, InspectType type, String operation) {
        String t = (type == InspectType.RECHECK ? InspectType.IQC : type).name();
        List<QcStandardDO> all = mapper.selectList(new LambdaQueryWrapper<QcStandardDO>().eq(QcStandardDO::getStdStatus, StdStatus.EFFECTIVE.name())
                .eq(QcStandardDO::getInspectType, t));
        QcStandardDO hit = pick(all.stream().filter(s -> MATERIAL.equals(s.getScopeType()) && Objects.equals(s.getMaterialId(), materialId)).toList(), operation);
        if (hit != null) return hit;
        MaterialDTO m = materialId == null ? null : support.materials(List.of(materialId)).get(materialId);
        Long cat = m == null ? null : m.categoryId();
        Set<Long> seen = new HashSet<>();
        while (cat != null && seen.add(cat)) {
            Long c = cat;
            hit = pick(all.stream().filter(s -> CATEGORY.equals(s.getScopeType()) && Objects.equals(s.getCategoryId(), c)).toList(), operation);
            if (hit != null) return hit;
            cat = categoryApi.get(cat).map(MaterialCategoryDTO::parentId).orElse(null);
        }
        return pick(all.stream().filter(s -> CATEGORY.equals(s.getScopeType()) && s.getCategoryId() == null).toList(), operation);
    }

    private static QcStandardDO pick(List<QcStandardDO> list, String operation) {
        if (list.isEmpty()) return null;
        if (StringUtils.hasText(operation)) {
            for (QcStandardDO s : list) if (operation.equals(s.getOperation())) return s;
        }
        for (QcStandardDO s : list) if (!StringUtils.hasText(s.getOperation())) return s;
        return StringUtils.hasText(operation) ? null : list.get(0);
    }
}
