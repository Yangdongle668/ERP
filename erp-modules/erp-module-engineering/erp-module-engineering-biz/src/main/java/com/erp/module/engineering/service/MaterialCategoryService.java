package com.erp.module.engineering.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.erp.common.enums.EnableStatus;
import com.erp.common.exception.BizException;
import com.erp.module.engineering.api.EngineeringErrorCodes;
import com.erp.module.engineering.api.category.MaterialCategoryApi;
import com.erp.module.engineering.api.category.MaterialCategoryDTO;
import com.erp.module.engineering.controller.vo.CategoryVOs.CategoryNode;
import com.erp.module.engineering.controller.vo.CategoryVOs.CategorySave;
import com.erp.module.engineering.controller.vo.CategoryVOs.SimpleNode;
import com.erp.module.engineering.dal.dataobject.MaterialCategoryDO;
import com.erp.module.engineering.dal.mapper.MaterialCategoryMapper;
import com.erp.module.engineering.dal.mapper.MaterialMapper;
import com.erp.module.system.api.uom.UomApi;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
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

/** 物料类别（需求 05-01），同时是 {@link MaterialCategoryApi} 的实现。 */
@Service
public class MaterialCategoryService implements MaterialCategoryApi {

    static final int MAX_LEVEL = 5;

    private final MaterialCategoryMapper categoryMapper;
    private final MaterialMapper materialMapper;
    private final UomApi uomApi;

    public MaterialCategoryService(MaterialCategoryMapper categoryMapper, MaterialMapper materialMapper, UomApi uomApi) {
        this.categoryMapper = categoryMapper;
        this.materialMapper = materialMapper;
        this.uomApi = uomApi;
    }

    // ==================== 查询 ====================

    /** 树形表格（含停用）；关键字命中节点及其祖先都显示 */
    public List<CategoryNode> tree(String keyword, String status) {
        List<MaterialCategoryDO> all = categoryMapper.selectList(null);
        Map<Long, MaterialCategoryDO> byId = new HashMap<>();
        all.forEach(c -> byId.put(c.getId(), c));
        String k = StringUtils.hasText(keyword) ? keyword.trim().toLowerCase() : null;
        Set<Long> visible = new HashSet<>();
        for (MaterialCategoryDO c : all) {
            boolean statusOk = !StringUtils.hasText(status) || c.getStatus().name().equals(status);
            boolean keywordOk = k == null || c.getName().toLowerCase().contains(k) || c.getCode().toLowerCase().contains(k);
            if (statusOk && keywordOk) {
                for (Long id : pathIds(c.getPath())) if (byId.containsKey(id)) visible.add(id);
            }
        }
        Map<Long, Long> enabledCounts = materialMapper.countEnabledByCategory();
        Map<Long, Long> anyCounts = materialMapper.countAllByCategory();
        // 含下级的启用物料数：把每个类别的数量累加到其全部祖先
        Map<Long, Integer> totals = new HashMap<>();
        for (MaterialCategoryDO c : all) {
            int n = enabledCounts.getOrDefault(c.getId(), 0L).intValue();
            if (n == 0) continue;
            for (Long id : pathIds(c.getPath())) totals.merge(id, n, Integer::sum);
        }
        return build(all.stream().filter(c -> visible.contains(c.getId())).toList(),
                c -> new CategoryNode(c.getId(), c.getParentId(), c.getCode(), c.getName(), c.getCodePrefix(), c.getDefaultMaterialType(),
                        c.getDefaultBaseUom(), c.getDefaultTracking(), Boolean.TRUE.equals(c.getDefaultIqcRequired()), c.getDefaultShelfLifeDays(),
                        c.getLevel(), c.getSort(), c.getStatus().name(), c.getRemark(), totals.getOrDefault(c.getId(), 0),
                        anyCounts.getOrDefault(c.getId(), 0L) > 0, c.getVersion(), new ArrayList<>()),
                CategoryNode::children);
    }

    /** 启用类别的精简树（选择器、物料列表左侧树） */
    public List<SimpleNode> simpleTree() {
        List<MaterialCategoryDO> enabled = categoryMapper.selectList(null).stream().filter(c -> c.getStatus() == EnableStatus.ENABLED).toList();
        Set<Long> parents = new HashSet<>();
        categoryMapper.selectList(null).forEach(c -> {
            if (c.getParentId() != null) parents.add(c.getParentId());
        });
        return build(enabled, c -> new SimpleNode(c.getId(), c.getParentId(), c.getCode(), c.getName(), c.getCodePrefix(), c.getDefaultMaterialType(),
                        c.getDefaultBaseUom(), c.getDefaultTracking(), Boolean.TRUE.equals(c.getDefaultIqcRequired()), c.getDefaultShelfLifeDays(),
                        !parents.contains(c.getId()), new ArrayList<>()),
                SimpleNode::children);
    }

    private static <N> List<N> build(List<MaterialCategoryDO> list, Function<MaterialCategoryDO, N> mapper, Function<N, List<N>> childrenOf) {
        Set<Long> ids = new HashSet<>();
        list.forEach(c -> ids.add(c.getId()));
        Map<Long, N> nodes = new LinkedHashMap<>();
        List<N> roots = new ArrayList<>();
        list.stream().sorted(Comparator.comparingInt(MaterialCategoryDO::getLevel).thenComparingInt(MaterialCategoryDO::getSort)
                        .thenComparing(MaterialCategoryDO::getCode))
                .forEach(c -> {
                    N n = mapper.apply(c);
                    nodes.put(c.getId(), n);
                    if (c.getParentId() == null || !ids.contains(c.getParentId())) roots.add(n);
                    else childrenOf.apply(nodes.get(c.getParentId())).add(n);
                });
        return roots;
    }

    public MaterialCategoryDO getOrThrow(Long id) {
        MaterialCategoryDO c = id == null ? null : categoryMapper.selectById(id);
        if (c == null) throw new BizException(EngineeringErrorCodes.CATEGORY_NOT_EXISTS);
        return c;
    }

    /** 新增下级时的默认排序：同级最大 + 10 */
    public int nextSort(Long parentId) {
        return categoryMapper.selectChildren(parentId).stream().mapToInt(MaterialCategoryDO::getSort).max().orElse(0) + 10;
    }

    // ==================== 维护 ====================

    @Transactional(rollbackFor = Exception.class)
    public Long create(CategorySave req) {
        MaterialCategoryDO parent = req.parentId() == null ? null : getOrThrow(req.parentId());
        if (parent != null) {
            if (materialMapper.countByCategory(parent.getId()) > 0) throw new BizException(EngineeringErrorCodes.CATEGORY_HAS_MATERIAL);
            if (parent.getLevel() + 1 > MAX_LEVEL) throw new BizException(EngineeringErrorCodes.CATEGORY_TOO_DEEP);
        }
        String code = req.code().trim().toUpperCase();
        checkCodeUnique(code, null);
        checkNameUnique(req.parentId(), req.name().trim(), null);
        MaterialCategoryDO c = new MaterialCategoryDO();
        c.setId(IdWorker.getId());
        c.setParentId(req.parentId());
        c.setCode(code);
        fill(c, req);
        c.setPath((parent == null ? "/" : parent.getPath()) + c.getId() + "/");
        c.setLevel(parent == null ? 1 : parent.getLevel() + 1);
        c.setStatus(EnableStatus.ENABLED);
        try {
            categoryMapper.insert(c);
        } catch (DuplicateKeyException e) {
            throw BizException.of(EngineeringErrorCodes.CATEGORY_CODE_DUPLICATE, code);
        }
        return c.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, CategorySave req) {
        MaterialCategoryDO c = getOrThrow(id);
        String code = req.code().trim().toUpperCase();
        if (!code.equals(c.getCode())) {
            if (materialMapper.countByCategory(id) > 0) throw new BizException(EngineeringErrorCodes.CATEGORY_CODE_LOCKED);
            checkCodeUnique(code, id);
        }
        checkNameUnique(req.parentId(), req.name().trim(), id);
        if (!Objects.equals(c.getParentId(), req.parentId())) move(c, req.parentId());
        c.setCode(code);
        fill(c, req);
        c.setVersion(req.version());
        try {
            categoryMapper.updateByIdOrFail(c);
        } catch (DuplicateKeyException e) {
            throw BizException.of(EngineeringErrorCodes.CATEGORY_CODE_DUPLICATE, code);
        }
    }

    /** 修改上级（R02、R03、R04）：同步更新自身及全部下级的 path、level */
    private void move(MaterialCategoryDO c, Long newParentId) {
        MaterialCategoryDO parent = newParentId == null ? null : getOrThrow(newParentId);
        if (parent != null && parent.getPath().startsWith(c.getPath())) throw new BizException(EngineeringErrorCodes.CATEGORY_PARENT_CYCLE);
        if (parent != null && materialMapper.countByCategory(parent.getId()) > 0) throw new BizException(EngineeringErrorCodes.CATEGORY_HAS_MATERIAL);
        int newLevel = parent == null ? 1 : parent.getLevel() + 1;
        int depth = categoryMapper.selectDescendants(c.getPath()).stream().mapToInt(MaterialCategoryDO::getLevel).max().orElse(c.getLevel()) - c.getLevel();
        if (newLevel + depth > MAX_LEVEL) throw new BizException(EngineeringErrorCodes.CATEGORY_TOO_DEEP);
        String oldPath = c.getPath();
        String newPath = (parent == null ? "/" : parent.getPath()) + c.getId() + "/";
        categoryMapper.updateDescendantPaths(oldPath, oldPath.length(), newPath, newLevel - c.getLevel());
        c.setParentId(newParentId);
        c.setPath(newPath);
        c.setLevel(newLevel);
    }

    @Transactional(rollbackFor = Exception.class)
    public void enable(Long id) {
        MaterialCategoryDO c = getOrThrow(id);
        if (c.getStatus() == EnableStatus.ENABLED) return;
        c.setStatus(EnableStatus.ENABLED);
        categoryMapper.updateByIdOrFail(c);
    }

    /** 停用（R06）：有启用的下级时不能停用；已有物料不受影响 */
    @Transactional(rollbackFor = Exception.class)
    public void disable(Long id) {
        MaterialCategoryDO c = getOrThrow(id);
        if (c.getStatus() == EnableStatus.DISABLED) return;
        if (categoryMapper.selectChildren(id).stream().anyMatch(x -> x.getStatus() == EnableStatus.ENABLED)) {
            throw new BizException(EngineeringErrorCodes.CATEGORY_HAS_ENABLED_CHILD);
        }
        c.setStatus(EnableStatus.DISABLED);
        categoryMapper.updateByIdOrFail(c);
    }

    /** 删除（R05）：有下级或有物料（含停用）时不能删除 */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        getOrThrow(id);
        if (!categoryMapper.selectChildren(id).isEmpty() || materialMapper.countByCategory(id) > 0) {
            throw new BizException(EngineeringErrorCodes.CATEGORY_NOT_DELETABLE);
        }
        categoryMapper.deleteById(id);
    }

    private void fill(MaterialCategoryDO c, CategorySave req) {
        c.setName(req.name().trim());
        c.setCodePrefix(req.codePrefix().trim().toUpperCase());
        c.setDefaultMaterialType(req.defaultMaterialType());
        c.setDefaultBaseUom(StringUtils.hasText(req.defaultBaseUom()) ? uomApi.validate(req.defaultBaseUom().trim()).code() : null);
        c.setDefaultTracking(req.defaultTracking());
        c.setDefaultIqcRequired(req.defaultIqcRequired());
        c.setDefaultShelfLifeDays(req.defaultShelfLifeDays());
        c.setSort(req.sort());
        c.setRemark(StringUtils.hasText(req.remark()) ? req.remark().trim() : null);
    }

    private void checkCodeUnique(String code, Long excludeId) {
        MaterialCategoryDO exists = categoryMapper.selectByCode(code);
        if (exists != null && !exists.getId().equals(excludeId)) throw BizException.of(EngineeringErrorCodes.CATEGORY_CODE_DUPLICATE, code);
    }

    private void checkNameUnique(Long parentId, String name, Long excludeId) {
        boolean dup = categoryMapper.selectChildren(parentId).stream()
                .anyMatch(c -> c.getName().equals(name) && !c.getId().equals(excludeId));
        if (dup) throw BizException.of(EngineeringErrorCodes.CATEGORY_NAME_DUPLICATE, name);
    }

    static List<Long> pathIds(String path) {
        return Arrays.stream(path.split("/")).filter(StringUtils::hasText).map(Long::valueOf).toList();
    }

    // ==================== MaterialCategoryApi ====================

    @Override
    public Optional<MaterialCategoryDTO> get(Long id) {
        return Optional.ofNullable(id == null ? null : categoryMapper.selectById(id)).map(MaterialCategoryService::toDto);
    }

    @Override
    public List<Long> getDescendantIds(Long id) {
        MaterialCategoryDO c = id == null ? null : categoryMapper.selectById(id);
        if (c == null) return List.of();
        List<Long> ids = new ArrayList<>();
        ids.add(c.getId());
        categoryMapper.selectDescendants(c.getPath()).forEach(x -> ids.add(x.getId()));
        return ids;
    }

    @Override
    public List<MaterialCategoryDTO> listAll() {
        return categoryMapper.selectList(null).stream()
                .sorted(Comparator.comparingInt(MaterialCategoryDO::getLevel).thenComparingInt(MaterialCategoryDO::getSort))
                .map(MaterialCategoryService::toDto).toList();
    }

    static MaterialCategoryDTO toDto(MaterialCategoryDO c) {
        return new MaterialCategoryDTO(c.getId(), c.getParentId(), c.getCode(), c.getName(), c.getCodePrefix(), c.getDefaultMaterialType(),
                c.getDefaultBaseUom(), c.getDefaultTracking(), Boolean.TRUE.equals(c.getDefaultIqcRequired()), c.getDefaultShelfLifeDays(),
                c.getLevel(), c.getStatus() == EnableStatus.ENABLED);
    }

    public Map<Long, MaterialCategoryDO> byIds(java.util.Collection<Long> ids) {
        Map<Long, MaterialCategoryDO> map = new HashMap<>();
        if (ids == null || ids.isEmpty()) return map;
        categoryMapper.selectBatchIds(ids).forEach(c -> map.put(c.getId(), c));
        return map;
    }

    /** 末级：没有下级类别（含停用的下级） */
    public boolean isLeaf(Long id) {
        return categoryMapper.selectChildren(id).isEmpty();
    }
}
