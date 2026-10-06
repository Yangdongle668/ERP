package com.erp.module.quality.service.basic;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.common.exception.BizException;
import com.erp.common.result.PageResult;
import com.erp.module.quality.api.QualityErrorCodes;
import com.erp.module.quality.controller.vo.BasicVOs.LevelPlan;
import com.erp.module.quality.controller.vo.BasicVOs.SamplingPreviewReq;
import com.erp.module.quality.controller.vo.BasicVOs.SamplingQuery;
import com.erp.module.quality.controller.vo.BasicVOs.SamplingResult;
import com.erp.module.quality.controller.vo.BasicVOs.SamplingRow;
import com.erp.module.quality.controller.vo.BasicVOs.SamplingSave;
import com.erp.module.quality.config.QualityModuleConfig;
import com.erp.module.quality.dal.dataobject.QcSamplingPlanDO;
import com.erp.module.quality.dal.dataobject.QcScarDO;
import com.erp.module.quality.dal.dataobject.QcStandardDO;
import com.erp.module.quality.dal.dataobject.QcStandardItemDO;
import com.erp.module.quality.dal.mapper.QcSamplingPlanMapper;
import com.erp.module.quality.dal.mapper.QcScarMapper;
import com.erp.module.quality.dal.mapper.QcStandardItemMapper;
import com.erp.module.quality.dal.mapper.QcStandardMapper;
import com.erp.module.quality.service.QcSupport;
import com.erp.module.quality.service.ScarStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** 抽样方案维护与样本量 / Ac、Re 计算（10-01 第 2、3 节） */
@Service
public class SamplingService {

    public static final String GB2828 = "GB2828";
    public static final String FULL = "FULL";
    public static final String FIXED = "FIXED";
    public static final String EXEMPT = "EXEMPT";
    public static final List<String> PLAN_TYPES = List.of(GB2828, FULL, FIXED, EXEMPT);

    private final QcSamplingPlanMapper mapper;
    private final QcStandardMapper standardMapper;
    private final QcStandardItemMapper standardItemMapper;
    private final AqlTableService aqlTable;
    private final QcScarMapper scarMapper;
    private final QcSupport support;

    public SamplingService(QcSamplingPlanMapper mapper, QcStandardMapper standardMapper, QcStandardItemMapper standardItemMapper,
                           AqlTableService aqlTable, QcScarMapper scarMapper, QcSupport support) {
        this.aqlTable = aqlTable;
        this.scarMapper = scarMapper;
        this.support = support;
        this.mapper = mapper;
        this.standardMapper = standardMapper;
        this.standardItemMapper = standardItemMapper;
    }

    public PageResult<SamplingRow> page(SamplingQuery q) {
        LambdaQueryWrapper<QcSamplingPlanDO> w = new LambdaQueryWrapper<QcSamplingPlanDO>()
                .and(StringUtils.hasText(q.getKeyword()), x -> x.like(QcSamplingPlanDO::getCode, q.getKeyword()).or().like(QcSamplingPlanDO::getName, q.getKeyword()))
                .eq(StringUtils.hasText(q.getPlanType()), QcSamplingPlanDO::getPlanType, q.getPlanType())
                .eq(StringUtils.hasText(q.getStatus()), QcSamplingPlanDO::getPlanStatus, q.getStatus())
                .orderByAsc(QcSamplingPlanDO::getCode);
        PageResult<QcSamplingPlanDO> p = mapper.selectPage(q, w);
        return new PageResult<>(p.list().stream().map(SamplingService::row).toList(), p.total());
    }

    public List<SamplingRow> listEnabled() {
        return mapper.selectList(new LambdaQueryWrapper<QcSamplingPlanDO>().eq(QcSamplingPlanDO::getPlanStatus, "ENABLED").orderByAsc(QcSamplingPlanDO::getCode))
                .stream().map(SamplingService::row).toList();
    }

    static SamplingRow row(QcSamplingPlanDO p) {
        return new SamplingRow(p.getId(), p.getCode(), p.getName(), p.getPlanType(), p.getInspectionLevel(), p.getAqlCr(), p.getAqlMa(), p.getAqlMi(),
                p.getFixedQty(), p.getPlanStatus(), p.getRemark(), p.getUpdatedAt());
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(SamplingSave req) {
        if (mapper.selectCount(new LambdaQueryWrapper<QcSamplingPlanDO>().eq(QcSamplingPlanDO::getCode, req.code().trim())) > 0) {
            throw BizException.of(QualityErrorCodes.CODE_DUPLICATE, req.code().trim());
        }
        QcSamplingPlanDO p = new QcSamplingPlanDO();
        p.setCode(req.code().trim());
        fill(p, req);
        mapper.insert(p);
        return p.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, SamplingSave req) {
        QcSamplingPlanDO p = get(id);
        if (!p.getCode().equals(req.code().trim())
                && mapper.selectCount(new LambdaQueryWrapper<QcSamplingPlanDO>().eq(QcSamplingPlanDO::getCode, req.code().trim())) > 0) {
            throw BizException.of(QualityErrorCodes.CODE_DUPLICATE, req.code().trim());
        }
        p.setCode(req.code().trim());
        if (req.version() != null) p.setVersion(req.version());
        fill(p, req);
        mapper.updateByIdOrFail(p);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        QcSamplingPlanDO p = get(id);
        boolean used = standardMapper.selectCount(new LambdaQueryWrapper<QcStandardDO>().eq(QcStandardDO::getSamplingPlanId, id)) > 0
                || standardItemMapper.selectCount(new LambdaQueryWrapper<QcStandardItemDO>().eq(QcStandardItemDO::getSamplingPlanId, id)) > 0;
        if (used) throw BizException.of(QualityErrorCodes.IN_USE, "抽样方案「" + p.getName() + "」");
        mapper.deleteById(id);
    }

    private void fill(QcSamplingPlanDO p, SamplingSave req) {
        validate(req.planType(), req.inspectionLevel(), req.aqlCr(), req.aqlMa(), req.aqlMi(), req.fixedQty());
        p.setName(req.name().trim());
        p.setPlanType(req.planType());
        boolean gb = GB2828.equals(req.planType());
        p.setInspectionLevel(gb ? req.inspectionLevel() : null);
        p.setAqlCr(gb ? QcSupport.trim(req.aqlCr()) : null);
        p.setAqlMa(gb ? QcSupport.trim(req.aqlMa()) : null);
        p.setAqlMi(gb ? QcSupport.trim(req.aqlMi()) : null);
        p.setFixedQty(FIXED.equals(req.planType()) ? req.fixedQty() : null);
        p.setPlanStatus(StringUtils.hasText(req.status()) ? req.status() : "ENABLED");
        p.setRemark(QcSupport.trim(req.remark()));
    }

    /** QC-STD-R04 */
    void validate(String planType, String level, String cr, String ma, String mi, Integer fixedQty) {
        if (planType == null || !PLAN_TYPES.contains(planType)) throw BizException.of(QualityErrorCodes.NOT_EXISTS, "方案类型 " + planType);
        if (GB2828.equals(planType)) {
            if (level == null || !AqlTableService.LEVELS.contains(level) || (!StringUtils.hasText(cr) && !StringUtils.hasText(ma) && !StringUtils.hasText(mi))) {
                throw new BizException(QualityErrorCodes.SAMPLING_AQL_REQUIRED);
            }
            for (String a : new String[]{cr, ma, mi}) {
                if (StringUtils.hasText(a) && !aqlTable.validAql(a)) throw BizException.of(QualityErrorCodes.SAMPLING_AQL_INVALID, a);
            }
        }
        if (FIXED.equals(planType) && (fixedQty == null || fixedQty < 1)) throw new BizException(QualityErrorCodes.SAMPLING_FIXED_QTY);
    }

    public QcSamplingPlanDO get(Long id) {
        QcSamplingPlanDO p = id == null ? null : mapper.selectById(id);
        if (p == null) throw BizException.of(QualityErrorCodes.NOT_EXISTS, "抽样方案");
        return p;
    }

    public Map<Long, QcSamplingPlanDO> plans(Collection<Long> ids) {
        Set<Long> set = ids.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, QcSamplingPlanDO> map = new HashMap<>();
        if (!set.isEmpty()) mapper.selectBatchIds(set).forEach(p -> map.put(p.getId(), p));
        return map;
    }

    // ==================== 计算 ====================

    public SamplingResult preview(SamplingPreviewReq req) {
        if (req.lotQty() == null || req.lotQty().signum() <= 0) throw new BizException(QualityErrorCodes.LOT_QTY_INVALID);
        if (req.planId() != null) return compute(get(req.planId()), req.lotQty());
        validate(req.planType(), req.inspectionLevel(), req.aqlCr(), req.aqlMa(), req.aqlMi(), req.fixedQty());
        QcSamplingPlanDO p = new QcSamplingPlanDO();
        p.setPlanType(req.planType());
        p.setInspectionLevel(req.inspectionLevel());
        p.setAqlCr(QcSupport.trim(req.aqlCr()));
        p.setAqlMa(QcSupport.trim(req.aqlMa()));
        p.setAqlMi(QcSupport.trim(req.aqlMi()));
        p.setFixedQty(req.fixedQty());
        return compute(p, req.lotQty());
    }

    /**
     * 样本量取各等级 n 的最大值，样本量 ≥ 批量时全检（n = 批量）；全检 / 固定数量方案按零缺陷判定（Ac0 / Re1）；免检样本量为 0。
     */
    public SamplingResult compute(QcSamplingPlanDO p, BigDecimal lotQty) {
        return compute(p, lotQty, false);
    }

    /** @param tightened 加严检验（QC-SCAR-R04）：GB2828 方案的检验水平提高一级 */
    public SamplingResult compute(QcSamplingPlanDO p, BigDecimal lotQty, boolean tightened) {
        String level = tightened ? AqlTableService.tighten(p.getInspectionLevel()) : p.getInspectionLevel();
        long lot = Math.max(1, lotQty.setScale(0, RoundingMode.CEILING).longValue());
        List<LevelPlan> levels = new ArrayList<>();
        String letter = null;
        int n;
        switch (p.getPlanType()) {
            case EXEMPT -> n = 0;
            case FULL -> {
                n = (int) Math.min(lot, Integer.MAX_VALUE);
                for (String lv : List.of("CR", "MA", "MI")) levels.add(new LevelPlan(lv, "0", n, 0, 1));
            }
            case FIXED -> {
                n = (int) Math.min(lot, p.getFixedQty() == null ? 1 : p.getFixedQty());
                for (String lv : List.of("CR", "MA", "MI")) levels.add(new LevelPlan(lv, "0", n, 0, 1));
            }
            default -> {
                letter = aqlTable.codeLetter(lot, level);
                int max = 0;
                String[][] aqls = {{"CR", p.getAqlCr()}, {"MA", p.getAqlMa()}, {"MI", p.getAqlMi()}};
                for (String[] a : aqls) {
                    if (!StringUtils.hasText(a[1])) continue;
                    AqlTableService.Plan plan = aqlTable.lookup(lot, level, a[1]);
                    int ln = (int) Math.min(plan.n(), lot);
                    levels.add(new LevelPlan(a[0], a[1], ln, plan.ac(), plan.re()));
                    max = Math.max(max, ln);
                }
                n = max;
            }
        }
        boolean full = n > 0 && n >= lot;
        return new SamplingResult(p.getId(), p.getCode(), p.getName(), p.getPlanType(), level, letter, lotQty, n, full, levels,
                text(p, level, levels, n, full) + (tightened && GB2828.equals(p.getPlanType()) ? "（加严）" : ""));
    }

    private static String text(QcSamplingPlanDO p, String level, List<LevelPlan> levels, int n, boolean full) {
        return switch (p.getPlanType()) {
            case EXEMPT -> "免检";
            case FULL -> "全检 " + n;
            case FIXED -> "固定抽样 " + n + (full ? "（全检）" : "");
            default -> {
                StringBuilder sb = new StringBuilder(level + " 级");
                for (LevelPlan l : levels) {
                    sb.append("，").append(l.level()).append(" ").append(l.aql());
                    sb.append(" Ac").append(l.ac()).append("/Re").append(l.re());
                }
                if (full) sb.append("（全检）");
                yield sb.toString();
            }
        };
    }

    /** 项目级抽样：覆盖方案的样本量（不超过批量） */
    public int itemSampleQty(QcSamplingPlanDO override, BigDecimal lotQty, int defaultQty, boolean tightened) {
        if (override == null) return defaultQty;
        return compute(override, lotQty, tightened).sampleQty();
    }

    /** QC-SCAR-R04：该供应商该物料有验证中的 SCAR 且参数开启时，IQC 加严抽样 */
    public boolean scarTightened(Long supplierId, Long materialId) {
        if (supplierId == null || materialId == null || !support.params().getBool(QualityModuleConfig.P_SCAR_TIGHTENED)) return false;
        return scarMapper.selectCount(new LambdaQueryWrapper<QcScarDO>().eq(QcScarDO::getSupplierId, supplierId).eq(QcScarDO::getMaterialId, materialId)
                .eq(QcScarDO::getScarStatus, ScarStatus.VERIFYING.name())) > 0;
    }
}
