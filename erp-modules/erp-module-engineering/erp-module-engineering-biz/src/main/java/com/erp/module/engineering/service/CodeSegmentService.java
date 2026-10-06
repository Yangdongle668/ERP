package com.erp.module.engineering.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.erp.common.enums.EnableStatus;
import com.erp.common.exception.BizException;
import com.erp.module.engineering.api.EngineeringErrorCodes;
import com.erp.module.engineering.controller.vo.CategoryVOs.CodeScheme;
import com.erp.module.engineering.controller.vo.CategoryVOs.CodeSchemeSave;
import com.erp.module.engineering.controller.vo.CategoryVOs.Segment;
import com.erp.module.engineering.controller.vo.CategoryVOs.SegmentSave;
import com.erp.module.engineering.controller.vo.CategoryVOs.SegmentValue;
import com.erp.module.engineering.controller.vo.CategoryVOs.SegmentValueSave;
import com.erp.module.engineering.dal.dataobject.CodeSegmentDO;
import com.erp.module.engineering.dal.dataobject.CodeSegmentValueDO;
import com.erp.module.engineering.dal.dataobject.MaterialCategoryDO;
import com.erp.module.engineering.dal.mapper.CodeSegmentMapper;
import com.erp.module.engineering.dal.mapper.CodeSegmentValueMapper;
import com.erp.module.engineering.dal.mapper.MaterialMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 物料编码段（需求 05-01 第 8 节，《物料编码原则》）：编码 = 类别前缀 + 各编码段的特征值 + 流水号。
 * 如线材 {@code 12-} + 线材型号 {@code 02} + 颜色 {@code 1} + 5 位流水 = {@code 12-02100001}；不同特征组合独立计数。
 *
 * <p>类别已有物料后（ENG-CAT-R09）：编码段的个数、顺序、位数及已有特征值的代码不能修改，特征值不能删除（只能停用），
 * 保证已发出的编码含义不变、作废编码不被复用。
 */
@Service
public class CodeSegmentService {

    private final CodeSegmentMapper segmentMapper;
    private final CodeSegmentValueMapper valueMapper;
    private final MaterialMapper materialMapper;
    private final MaterialCategoryService categoryService;

    public CodeSegmentService(CodeSegmentMapper segmentMapper, CodeSegmentValueMapper valueMapper, MaterialMapper materialMapper,
                              MaterialCategoryService categoryService) {
        this.segmentMapper = segmentMapper;
        this.valueMapper = valueMapper;
        this.materialMapper = materialMapper;
        this.categoryService = categoryService;
    }

    /** 编码段生成结果：prefix 为类别前缀 + 特征值（编码规则变量 categoryPrefix），description 记录在物料上 */
    public record Composed(String prefix, String description) {
    }

    // ==================== 查询 ====================

    public CodeScheme scheme(Long categoryId) {
        MaterialCategoryDO c = categoryService.getOrThrow(categoryId);
        return new CodeScheme(c.getId(), c.getName(), c.getCodePrefix(), c.getCodeSeqLength(), categoryService.isLeaf(c.getId()),
                materialMapper.countByCategory(c.getId()) > 0, segments(c.getId()));
    }

    private List<Segment> segments(Long categoryId) {
        List<CodeSegmentDO> segs = segmentMapper.selectByCategory(categoryId);
        Map<Long, List<CodeSegmentValueDO>> values = valueMapper.selectBySegments(segs.stream().map(CodeSegmentDO::getId).toList())
                .stream().collect(Collectors.groupingBy(CodeSegmentValueDO::getSegmentId));
        return segs.stream().map(s -> new Segment(s.getId(), s.getName(), s.getSegLength(), s.getSort(), s.getRemark(),
                values.getOrDefault(s.getId(), List.of()).stream()
                        .map(v -> new SegmentValue(v.getId(), v.getValueCode(), v.getValueName(), v.getSort(), v.getStatus().name(), v.getRemark()))
                        .toList())).toList();
    }

    // ==================== 维护 ====================

    @Transactional(rollbackFor = Exception.class)
    public void save(Long categoryId, CodeSchemeSave req) {
        MaterialCategoryDO c = categoryService.getOrThrow(categoryId);
        List<SegmentSave> segs = req.segments() == null ? List.of() : req.segments();
        if (!segs.isEmpty() && !categoryService.isLeaf(c.getId())) throw new BizException(EngineeringErrorCodes.CODE_SEGMENT_NOT_LEAF);
        validate(segs);
        List<CodeSegmentDO> oldSegs = segmentMapper.selectByCategory(c.getId());
        Map<Long, CodeSegmentDO> oldSegById = oldSegs.stream().collect(Collectors.toMap(CodeSegmentDO::getId, Function.identity()));
        Map<Long, CodeSegmentValueDO> oldValById = valueMapper.selectBySegments(oldSegById.keySet()).stream()
                .collect(Collectors.toMap(CodeSegmentValueDO::getId, Function.identity()));
        boolean locked = materialMapper.countByCategory(c.getId()) > 0;
        if (locked) checkLocked(c, segs, oldSegs, oldValById);

        Set<Long> keptSegs = new HashSet<>();
        Set<Long> keptVals = new HashSet<>();
        for (int i = 0; i < segs.size(); i++) {
            SegmentSave s = segs.get(i);
            CodeSegmentDO seg = s.id() == null ? null : oldSegById.get(s.id());
            boolean isNew = seg == null;
            if (isNew) {
                seg = new CodeSegmentDO();
                seg.setId(IdWorker.getId());
                seg.setCategoryId(c.getId());
            }
            seg.setName(s.name().trim());
            seg.setSegLength(s.length());
            seg.setSort((i + 1) * 10);
            seg.setRemark(trim(s.remark()));
            if (isNew) segmentMapper.insert(seg);
            else segmentMapper.updateByIdOrFail(seg);
            keptSegs.add(seg.getId());
            List<SegmentValueSave> values = s.values() == null ? List.of() : s.values();
            for (int j = 0; j < values.size(); j++) {
                SegmentValueSave v = values.get(j);
                CodeSegmentValueDO val = v.id() == null ? null : oldValById.get(v.id());
                boolean newVal = val == null || !Objects.equals(val.getSegmentId(), seg.getId());
                if (newVal) {
                    val = new CodeSegmentValueDO();
                    val.setId(IdWorker.getId());
                    val.setSegmentId(seg.getId());
                }
                val.setValueCode(v.code().trim().toUpperCase(Locale.ROOT));
                val.setValueName(v.name().trim());
                val.setSort((j + 1) * 10);
                val.setStatus("DISABLED".equals(v.status()) ? EnableStatus.DISABLED : EnableStatus.ENABLED);
                val.setRemark(trim(v.remark()));
                if (newVal) valueMapper.insert(val);
                else valueMapper.updateByIdOrFail(val);
                keptVals.add(val.getId());
            }
        }
        // 未锁定时允许删除编码段和特征值
        oldValById.keySet().stream().filter(id -> !keptVals.contains(id)).forEach(valueMapper::deleteById);
        oldSegById.keySet().stream().filter(id -> !keptSegs.contains(id)).forEach(segmentMapper::deleteById);
    }

    private static void validate(List<SegmentSave> segs) {
        Set<String> names = new HashSet<>();
        for (SegmentSave s : segs) {
            String name = s.name().trim();
            if (!names.add(name)) throw BizException.of(EngineeringErrorCodes.CODE_SEGMENT_INVALID, "编码段名称「" + name + "」重复");
            Set<String> codes = new HashSet<>();
            for (SegmentValueSave v : s.values() == null ? List.<SegmentValueSave>of() : s.values()) {
                String code = v.code().trim().toUpperCase(Locale.ROOT);
                if (code.length() != s.length()) {
                    throw BizException.of(EngineeringErrorCodes.CODE_SEGMENT_INVALID,
                            "编码段「" + name + "」为 " + s.length() + " 位，特征值「" + code + "」位数不对");
                }
                if (!codes.add(code)) throw BizException.of(EngineeringErrorCodes.CODE_SEGMENT_INVALID, "编码段「" + name + "」的特征值「" + code + "」重复");
            }
        }
    }

    /** 已有物料：编码段个数、顺序、位数不变；已有特征值不能删除、不能改代码 */
    private static void checkLocked(MaterialCategoryDO c, List<SegmentSave> segs, List<CodeSegmentDO> oldSegs, Map<Long, CodeSegmentValueDO> oldVals) {
        boolean same = segs.size() == oldSegs.size();
        for (int i = 0; same && i < segs.size(); i++) {
            same = Objects.equals(segs.get(i).id(), oldSegs.get(i).getId()) && Objects.equals(segs.get(i).length(), oldSegs.get(i).getSegLength());
        }
        Map<Long, String> newCodes = new HashMap<>();
        segs.forEach(s -> (s.values() == null ? List.<SegmentValueSave>of() : s.values()).forEach(v -> {
            if (v.id() != null) newCodes.put(v.id(), v.code().trim().toUpperCase(Locale.ROOT));
        }));
        for (CodeSegmentValueDO v : oldVals.values()) {
            if (!v.getValueCode().equals(newCodes.get(v.getId()))) same = false;
        }
        if (!same) throw BizException.of(EngineeringErrorCodes.CODE_SEGMENT_LOCKED, c.getName());
    }

    // ==================== 生成 ====================

    /**
     * 按类别编码段组装编码前缀。类别没有编码段时返回 null（按原规则：类别前缀 + 流水号）。
     *
     * @param values   各编码段所选特征值（按顺序）
     * @param required 为 true 时（自动生成编码）每段都必须选择
     */
    public Composed compose(MaterialCategoryDO c, List<String> values, boolean required) {
        List<Segment> segs = segments(c.getId());
        if (segs.isEmpty()) return null;
        List<String> given = values == null ? List.of() : values;
        if (!required && given.stream().noneMatch(StringUtils::hasText)) return null;
        StringBuilder prefix = new StringBuilder(c.getCodePrefix());
        List<String> desc = new ArrayList<>();
        for (int i = 0; i < segs.size(); i++) {
            Segment s = segs.get(i);
            String code = i < given.size() && StringUtils.hasText(given.get(i)) ? given.get(i).trim().toUpperCase(Locale.ROOT) : null;
            if (code == null) throw BizException.of(EngineeringErrorCodes.CODE_VALUE_REQUIRED, s.name());
            SegmentValue v = s.values().stream().filter(x -> x.code().equals(code) && "ENABLED".equals(x.status())).findFirst()
                    .orElseThrow(() -> BizException.of(EngineeringErrorCodes.CODE_VALUE_INVALID, s.name(), code));
            prefix.append(v.code());
            desc.add(s.name() + " " + v.code() + " " + v.name());
        }
        return new Composed(prefix.toString(), String.join("；", desc));
    }

    /**
     * 导入：编码段特征值写在一个单元格里，可用逗号 / 斜杠 / 空格分隔（{@code 02,1}），
     * 也可以直接连写（{@code 021}，按各段位数切分）。
     */
    public List<String> parse(MaterialCategoryDO c, String text) {
        if (!StringUtils.hasText(text)) return List.of();
        String t = text.trim().toUpperCase(Locale.ROOT);
        if (t.matches(".*[,，/\\s].*")) return List.of(t.split("[,，/\\s]+"));
        List<String> parts = new ArrayList<>();
        int pos = 0;
        for (CodeSegmentDO s : segmentMapper.selectByCategory(c.getId())) {
            int end = Math.min(t.length(), pos + s.getSegLength());
            parts.add(t.substring(pos, end));
            pos = end;
        }
        if (pos < t.length()) throw BizException.of(EngineeringErrorCodes.CODE_SEGMENT_INVALID, "编码段特征值「" + text.trim() + "」位数过多");
        return parts;
    }

    public boolean hasSegments(Long categoryId) {
        return !segmentMapper.selectByCategory(categoryId).isEmpty();
    }

    private static String trim(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }
}
