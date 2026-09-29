package com.erp.module.quality.service.basic;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.common.exception.BizException;
import com.erp.common.result.PageResult;
import com.erp.module.quality.api.QualityErrorCodes;
import com.erp.module.quality.controller.vo.BasicVOs.DefectCodeQuery;
import com.erp.module.quality.controller.vo.BasicVOs.DefectCodeRow;
import com.erp.module.quality.controller.vo.BasicVOs.DefectCodeSave;
import com.erp.module.quality.controller.vo.BasicVOs.ItemLibQuery;
import com.erp.module.quality.controller.vo.BasicVOs.ItemLibRow;
import com.erp.module.quality.controller.vo.BasicVOs.ItemLibSave;
import com.erp.module.quality.dal.dataobject.QcDefectCodeDO;
import com.erp.module.quality.dal.dataobject.QcInspectionDefectDO;
import com.erp.module.quality.dal.dataobject.QcInspectionItemLibDO;
import com.erp.module.quality.dal.dataobject.QcStandardItemDO;
import com.erp.module.quality.dal.mapper.QcDefectCodeMapper;
import com.erp.module.quality.dal.mapper.QcInspectionDefectMapper;
import com.erp.module.quality.dal.mapper.QcInspectionItemLibMapper;
import com.erp.module.quality.dal.mapper.QcStandardItemMapper;
import com.erp.module.quality.service.QcSupport;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** 检验项目库、缺陷代码（10-01） */
@Service
public class BasicDataService {

    public static final List<String> LEVELS = List.of("CR", "MA", "MI");
    public static final String QUALITATIVE = "QUALITATIVE";
    public static final String QUANTITATIVE = "QUANTITATIVE";

    private final QcInspectionItemLibMapper itemMapper;
    private final QcDefectCodeMapper defectMapper;
    private final QcStandardItemMapper standardItemMapper;
    private final QcInspectionDefectMapper inspectionDefectMapper;
    private final QcSupport support;

    public BasicDataService(QcInspectionItemLibMapper itemMapper, QcDefectCodeMapper defectMapper, QcStandardItemMapper standardItemMapper,
                            QcInspectionDefectMapper inspectionDefectMapper, QcSupport support) {
        this.itemMapper = itemMapper;
        this.defectMapper = defectMapper;
        this.standardItemMapper = standardItemMapper;
        this.inspectionDefectMapper = inspectionDefectMapper;
        this.support = support;
    }

    // ==================== 项目库 ====================

    public PageResult<ItemLibRow> pageItems(ItemLibQuery q) {
        LambdaQueryWrapper<QcInspectionItemLibDO> w = new LambdaQueryWrapper<QcInspectionItemLibDO>()
                .and(StringUtils.hasText(q.getKeyword()), x -> x.like(QcInspectionItemLibDO::getCode, q.getKeyword()).or().like(QcInspectionItemLibDO::getName, q.getKeyword()))
                .eq(StringUtils.hasText(q.getItemType()), QcInspectionItemLibDO::getItemType, q.getItemType())
                .eq(StringUtils.hasText(q.getStatus()), QcInspectionItemLibDO::getItemStatus, q.getStatus())
                .orderByAsc(QcInspectionItemLibDO::getCode);
        PageResult<QcInspectionItemLibDO> p = itemMapper.selectPage(q, w);
        return new PageResult<>(p.list().stream().map(BasicDataService::itemRow).toList(), p.total());
    }

    static ItemLibRow itemRow(QcInspectionItemLibDO i) {
        return new ItemLibRow(i.getId(), i.getCode(), i.getName(), i.getItemType(), i.getMethod(), i.getUnit(), i.getDefectLevel(), i.getTool(),
                i.getDescription(), i.getItemStatus(), i.getUpdatedAt());
    }

    @Transactional(rollbackFor = Exception.class)
    public Long createItem(ItemLibSave req) {
        if (itemMapper.selectCount(new LambdaQueryWrapper<QcInspectionItemLibDO>().eq(QcInspectionItemLibDO::getCode, req.code().trim())) > 0) {
            throw BizException.of(QualityErrorCodes.CODE_DUPLICATE, req.code().trim());
        }
        QcInspectionItemLibDO i = new QcInspectionItemLibDO();
        i.setCode(req.code().trim());
        fillItem(i, req);
        itemMapper.insert(i);
        return i.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateItem(Long id, ItemLibSave req) {
        QcInspectionItemLibDO i = item(id);
        if (!i.getCode().equals(req.code().trim())
                && itemMapper.selectCount(new LambdaQueryWrapper<QcInspectionItemLibDO>().eq(QcInspectionItemLibDO::getCode, req.code().trim())) > 0) {
            throw BizException.of(QualityErrorCodes.CODE_DUPLICATE, req.code().trim());
        }
        i.setCode(req.code().trim());
        if (req.version() != null) i.setVersion(req.version());
        fillItem(i, req);
        itemMapper.updateByIdOrFail(i);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteItem(Long id) {
        QcInspectionItemLibDO i = item(id);
        if (standardItemMapper.selectCount(new LambdaQueryWrapper<QcStandardItemDO>().eq(QcStandardItemDO::getLibItemId, id)) > 0) {
            throw BizException.of(QualityErrorCodes.IN_USE, "检验项目「" + i.getName() + "」");
        }
        itemMapper.deleteById(id);
    }

    private void fillItem(QcInspectionItemLibDO i, ItemLibSave req) {
        if (req.itemType() == null || !List.of(QUALITATIVE, QUANTITATIVE).contains(req.itemType())) throw BizException.of(QualityErrorCodes.NOT_EXISTS, "项目类型 " + req.itemType());
        level(req.defectLevel());
        support.dictApi().validate("qc_inspection_method", req.method(), "检验方法");
        i.setName(req.name().trim());
        i.setItemType(req.itemType());
        i.setMethod(req.method());
        i.setUnit(QcSupport.trim(req.unit()));
        i.setDefectLevel(req.defectLevel());
        i.setTool(QcSupport.trim(req.tool()));
        i.setDescription(QcSupport.trim(req.description()));
        i.setItemStatus(StringUtils.hasText(req.status()) ? req.status() : "ENABLED");
    }

    public QcInspectionItemLibDO item(Long id) {
        QcInspectionItemLibDO i = id == null ? null : itemMapper.selectById(id);
        if (i == null) throw BizException.of(QualityErrorCodes.NOT_EXISTS, "检验项目");
        return i;
    }

    public Map<Long, QcInspectionItemLibDO> items(Collection<Long> ids) {
        Set<Long> set = ids.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, QcInspectionItemLibDO> map = new HashMap<>();
        if (!set.isEmpty()) itemMapper.selectBatchIds(set).forEach(i -> map.put(i.getId(), i));
        return map;
    }

    public static String level(String level) {
        if (level == null || !LEVELS.contains(level)) throw BizException.of(QualityErrorCodes.NOT_EXISTS, "缺陷等级 " + level);
        return level;
    }

    // ==================== 缺陷代码 ====================

    public PageResult<DefectCodeRow> pageDefects(DefectCodeQuery q) {
        LambdaQueryWrapper<QcDefectCodeDO> w = new LambdaQueryWrapper<QcDefectCodeDO>()
                .and(StringUtils.hasText(q.getKeyword()), x -> x.like(QcDefectCodeDO::getCode, q.getKeyword()).or().like(QcDefectCodeDO::getName, q.getKeyword()))
                .eq(StringUtils.hasText(q.getCategory()), QcDefectCodeDO::getCategory, q.getCategory())
                .eq(StringUtils.hasText(q.getStatus()), QcDefectCodeDO::getCodeStatus, q.getStatus())
                .orderByAsc(QcDefectCodeDO::getCode);
        PageResult<QcDefectCodeDO> p = defectMapper.selectPage(q, w);
        return new PageResult<>(p.list().stream().map(BasicDataService::defectRow).toList(), p.total());
    }

    public List<DefectCodeRow> listDefects() {
        return defectMapper.selectList(new LambdaQueryWrapper<QcDefectCodeDO>().eq(QcDefectCodeDO::getCodeStatus, "ENABLED").orderByAsc(QcDefectCodeDO::getCode))
                .stream().map(BasicDataService::defectRow).toList();
    }

    static DefectCodeRow defectRow(QcDefectCodeDO d) {
        return new DefectCodeRow(d.getId(), d.getCode(), d.getName(), d.getCategory(), d.getDefaultLevel(), d.getCodeStatus(), d.getUpdatedAt());
    }

    @Transactional(rollbackFor = Exception.class)
    public Long createDefect(DefectCodeSave req) {
        if (defectMapper.selectCount(new LambdaQueryWrapper<QcDefectCodeDO>().eq(QcDefectCodeDO::getCode, req.code().trim())) > 0) {
            throw BizException.of(QualityErrorCodes.CODE_DUPLICATE, req.code().trim());
        }
        QcDefectCodeDO d = new QcDefectCodeDO();
        d.setCode(req.code().trim());
        fillDefect(d, req);
        defectMapper.insert(d);
        return d.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateDefect(Long id, DefectCodeSave req) {
        QcDefectCodeDO d = defect(id);
        if (!d.getCode().equals(req.code().trim())) {
            if (inspectionDefectMapper.selectCount(new LambdaQueryWrapper<QcInspectionDefectDO>().eq(QcInspectionDefectDO::getDefectCode, d.getCode())) > 0) {
                throw BizException.of(QualityErrorCodes.IN_USE, "缺陷代码「" + d.getCode() + "」");
            }
            if (defectMapper.selectCount(new LambdaQueryWrapper<QcDefectCodeDO>().eq(QcDefectCodeDO::getCode, req.code().trim())) > 0) {
                throw BizException.of(QualityErrorCodes.CODE_DUPLICATE, req.code().trim());
            }
        }
        d.setCode(req.code().trim());
        if (req.version() != null) d.setVersion(req.version());
        fillDefect(d, req);
        defectMapper.updateByIdOrFail(d);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteDefect(Long id) {
        QcDefectCodeDO d = defect(id);
        if (inspectionDefectMapper.selectCount(new LambdaQueryWrapper<QcInspectionDefectDO>().eq(QcInspectionDefectDO::getDefectCode, d.getCode())) > 0) {
            throw BizException.of(QualityErrorCodes.IN_USE, "缺陷代码「" + d.getCode() + "」");
        }
        defectMapper.deleteById(id);
    }

    private void fillDefect(QcDefectCodeDO d, DefectCodeSave req) {
        support.dictApi().validate("qc_defect_category", req.category(), "缺陷分类");
        d.setName(req.name().trim());
        d.setCategory(req.category());
        d.setDefaultLevel(level(req.defaultLevel()));
        d.setCodeStatus(StringUtils.hasText(req.status()) ? req.status() : "ENABLED");
    }

    public QcDefectCodeDO defect(Long id) {
        QcDefectCodeDO d = id == null ? null : defectMapper.selectById(id);
        if (d == null) throw BizException.of(QualityErrorCodes.NOT_EXISTS, "缺陷代码");
        return d;
    }

    /** 按编码取缺陷代码 */
    public Map<String, QcDefectCodeDO> defectsByCode(Collection<String> codes) {
        Set<String> set = codes.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (set.isEmpty()) return Map.of();
        return defectMapper.selectList(new LambdaQueryWrapper<QcDefectCodeDO>().in(QcDefectCodeDO::getCode, set)).stream()
                .collect(Collectors.toMap(QcDefectCodeDO::getCode, d -> d, (a, b) -> a));
    }
}
