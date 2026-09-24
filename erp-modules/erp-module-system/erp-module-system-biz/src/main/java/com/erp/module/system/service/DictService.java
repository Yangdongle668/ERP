package com.erp.module.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.common.enums.EnableStatus;
import com.erp.common.exception.BizException;
import com.erp.common.result.PageResult;
import com.erp.framework.module.ErpModule;
import com.erp.module.system.api.SystemErrorCodes;
import com.erp.module.system.api.dict.DictApi;
import com.erp.module.system.api.dict.DictDefinition;
import com.erp.module.system.api.dict.DictItemDTO;
import com.erp.module.system.api.dict.DictReferenceChecker;
import com.erp.module.system.controller.vo.DictVOs.BundleItem;
import com.erp.module.system.controller.vo.DictVOs.BundleType;
import com.erp.module.system.controller.vo.DictVOs.DictBundle;
import com.erp.module.system.controller.vo.DictVOs.ItemResp;
import com.erp.module.system.controller.vo.DictVOs.ItemSave;
import com.erp.module.system.controller.vo.DictVOs.TypeQuery;
import com.erp.module.system.controller.vo.DictVOs.TypeResp;
import com.erp.module.system.controller.vo.DictVOs.TypeSave;
import com.erp.module.system.dal.dataobject.DictItemDO;
import com.erp.module.system.dal.dataobject.DictTypeDO;
import com.erp.module.system.dal.mapper.DictItemMapper;
import com.erp.module.system.dal.mapper.DictTypeMapper;
import com.erp.module.system.service.support.SystemCaches;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** 数据字典（01-04）：声明同步、维护、全局缓存、{@link DictApi} 实现。 */
@Slf4j
@Service
public class DictService implements DictApi {

    private static final Set<String> TAG_TYPES = Set.of("DEFAULT", "PRIMARY", "SUCCESS", "WARNING", "DANGER", "INFO");

    private final DictTypeMapper typeMapper;
    private final DictItemMapper itemMapper;
    private final SystemCaches caches;
    private final List<DictReferenceChecker> referenceCheckers;
    private final Map<String, String> moduleNames;

    public DictService(DictTypeMapper typeMapper, DictItemMapper itemMapper, SystemCaches caches,
                       List<DictReferenceChecker> referenceCheckers, List<ErpModule> modules) {
        this.typeMapper = typeMapper;
        this.itemMapper = itemMapper;
        this.caches = caches;
        this.referenceCheckers = referenceCheckers;
        this.moduleNames = modules.stream().collect(Collectors.toMap(ErpModule::code, ErpModule::name, (a, b) -> a));
    }

    // ==================== 声明同步（SYS-DIC-R06） ====================

    @Transactional(rollbackFor = Exception.class)
    public void sync(List<DictDefinition> definitions) {
        Map<String, DictDefinition> byCode = new LinkedHashMap<>();
        for (DictDefinition d : definitions) {
            DictDefinition prev = byCode.put(d.typeCode(), d);
            if (prev != null) {
                throw new IllegalStateException("字典类型重复声明: " + d.typeCode() + "（模块 " + prev.moduleCode() + "、" + d.moduleCode() + "）");
            }
        }
        boolean changed = false;
        for (DictDefinition d : byCode.values()) {
            DictTypeDO type = typeMapper.selectByCode(d.typeCode());
            if (type == null) {
                type = new DictTypeDO();
                type.setCode(d.typeCode());
                type.setName(d.name());
                type.setModuleCode(d.moduleCode());
                type.setBuiltin(true);
                type.setStatus(EnableStatus.ENABLED);
                typeMapper.insert(type);
                changed = true;
            } else if (!Boolean.TRUE.equals(type.getBuiltin()) || !Objects.equals(type.getModuleCode(), d.moduleCode())) {
                type.setBuiltin(true);
                type.setModuleCode(d.moduleCode());
                typeMapper.updateByIdOrFail(type);
                changed = true;
            }
            Map<String, DictItemDO> existing = itemMapper.selectByType(d.typeCode()).stream()
                    .collect(Collectors.toMap(DictItemDO::getValue, i -> i));
            int sort = 10;
            for (DictDefinition.Item item : d.items()) {
                DictItemDO e = existing.get(item.value());
                if (e == null) {
                    DictItemDO n = new DictItemDO();
                    n.setTypeCode(d.typeCode());
                    n.setValue(item.value());
                    n.setLabel(item.label());
                    n.setLabelEn(item.labelEn());
                    n.setTagType(item.tagType().name());
                    n.setSort(sort);
                    n.setIsDefault(item.isDefault());
                    n.setBuiltin(item.builtin());
                    n.setStatus(EnableStatus.ENABLED);
                    itemMapper.insert(n);
                    changed = true;
                } else if (item.builtin() && (!Boolean.TRUE.equals(e.getBuiltin()) || e.getStatus() != EnableStatus.ENABLED)) {
                    // 内置项必须保持内置、启用；标签、颜色、排序以管理员修改为准
                    e.setBuiltin(true);
                    e.setStatus(EnableStatus.ENABLED);
                    itemMapper.updateByIdOrFail(e);
                    changed = true;
                }
                sort += 10;
            }
        }
        if (changed) changed();
        log.info("[数据字典] 同步 {} 个字典声明", byCode.size());
    }

    // ==================== 字典类型 ====================

    public PageResult<TypeResp> pageTypes(TypeQuery q) {
        LambdaQueryWrapper<DictTypeDO> w = new LambdaQueryWrapper<DictTypeDO>()
                .eq(StringUtils.hasText(q.getModuleCode()), DictTypeDO::getModuleCode, q.getModuleCode())
                .and(StringUtils.hasText(q.getKeyword()), x -> x.like(DictTypeDO::getCode, q.getKeyword().trim())
                        .or().like(DictTypeDO::getName, q.getKeyword().trim()))
                .orderByAsc(DictTypeDO::getModuleCode).orderByAsc(DictTypeDO::getCode);
        return typeMapper.selectPage(q, w).map(this::toTypeResp);
    }

    @Transactional(rollbackFor = Exception.class)
    public Long createType(TypeSave req) {
        if (typeMapper.selectByCode(req.code()) != null) throw BizException.of(SystemErrorCodes.DICT_TYPE_DUPLICATE, req.code());
        DictTypeDO t = new DictTypeDO();
        t.setCode(req.code());
        t.setName(req.name().trim());
        t.setModuleCode(DictTypeDO.CUSTOM_MODULE);
        t.setBuiltin(false);
        t.setStatus(parseStatus(req.status()));
        t.setRemark(req.remark());
        typeMapper.insert(t);
        changed();
        return t.getId();
    }

    /** 内置类型只能改名称、备注 */
    @Transactional(rollbackFor = Exception.class)
    public void updateType(Long id, TypeSave req) {
        DictTypeDO t = getType(id);
        t.setName(req.name().trim());
        t.setRemark(req.remark());
        if (!Boolean.TRUE.equals(t.getBuiltin())) {
            if (req.status() != null) t.setStatus(parseStatus(req.status()));
        }
        t.setVersion(req.version());
        typeMapper.updateByIdOrFail(t);
        changed();
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteType(Long id) {
        DictTypeDO t = getType(id);
        if (Boolean.TRUE.equals(t.getBuiltin())) throw new BizException(SystemErrorCodes.DICT_TYPE_BUILTIN);
        if (itemMapper.countByType(t.getCode()) > 0) throw new BizException(SystemErrorCodes.DICT_TYPE_HAS_ITEMS);
        typeMapper.deleteById(id);
        changed();
    }

    // ==================== 字典项 ====================

    public List<ItemResp> listItems(String typeCode) {
        return itemMapper.selectByType(typeCode).stream().map(this::toItemResp).toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public Long createItem(ItemSave req) {
        DictTypeDO type = typeMapper.selectByCode(req.typeCode());
        if (type == null) throw new BizException(SystemErrorCodes.DICT_TYPE_NOT_EXISTS);
        String value = req.value().trim().toUpperCase();
        if (itemMapper.selectByValue(req.typeCode(), value) != null) throw BizException.of(SystemErrorCodes.DICT_VALUE_DUPLICATE, value);
        checkLabelUnique(req.typeCode(), req.label().trim(), null);
        DictItemDO i = new DictItemDO();
        i.setTypeCode(req.typeCode());
        i.setValue(value);
        fill(i, req);
        i.setBuiltin(false);
        i.setStatus(EnableStatus.ENABLED);
        itemMapper.insert(i);
        if (Boolean.TRUE.equals(i.getIsDefault())) clearOtherDefaults(i);
        changed();
        return i.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateItem(Long id, ItemSave req) {
        DictItemDO i = getItem(id);
        String value = req.value().trim().toUpperCase();
        if (!value.equals(i.getValue())) {
            if (Boolean.TRUE.equals(i.getBuiltin())) throw new BizException(SystemErrorCodes.DICT_ITEM_BUILTIN);
            if (itemMapper.selectByValue(i.getTypeCode(), value) != null) throw BizException.of(SystemErrorCodes.DICT_VALUE_DUPLICATE, value);
            i.setValue(value);
        }
        checkLabelUnique(i.getTypeCode(), req.label().trim(), id);
        fill(i, req);
        i.setVersion(req.version());
        itemMapper.updateByIdOrFail(i);
        if (Boolean.TRUE.equals(i.getIsDefault())) clearOtherDefaults(i);
        changed();
    }

    @Transactional(rollbackFor = Exception.class)
    public void changeItemStatus(Long id, EnableStatus status) {
        DictItemDO i = getItem(id);
        if (Boolean.TRUE.equals(i.getBuiltin())) throw new BizException(SystemErrorCodes.DICT_ITEM_BUILTIN);
        if (i.getStatus() == status) return;
        i.setStatus(status);
        if (status == EnableStatus.DISABLED) i.setIsDefault(false);
        itemMapper.updateByIdOrFail(i);
        changed();
    }

    /** SYS-DIC-R05：内置项不能删除；被使用的不能删除；没有检查器声明支持的字典视为已使用 */
    @Transactional(rollbackFor = Exception.class)
    public void deleteItem(Long id) {
        DictItemDO i = getItem(id);
        if (Boolean.TRUE.equals(i.getBuiltin())) throw new BizException(SystemErrorCodes.DICT_ITEM_BUILTIN);
        DictTypeDO type = typeMapper.selectByCode(i.getTypeCode());
        boolean custom = type != null && DictTypeDO.CUSTOM_MODULE.equals(type.getModuleCode());
        List<DictReferenceChecker> checkers = referenceCheckers.stream().filter(c -> c.supports(i.getTypeCode())).toList();
        boolean referenced = checkers.isEmpty() ? !custom : checkers.stream().anyMatch(c -> c.isReferenced(i.getTypeCode(), i.getValue()));
        if (referenced) throw new BizException(SystemErrorCodes.DICT_ITEM_REFERENCED);
        itemMapper.deleteById(id);
        changed();
    }

    private void checkLabelUnique(String typeCode, String label, Long excludeId) {
        boolean dup = itemMapper.selectByType(typeCode).stream()
                .anyMatch(x -> x.getLabel().equals(label) && !x.getId().equals(excludeId));
        if (dup) throw BizException.of(SystemErrorCodes.DICT_LABEL_DUPLICATE, label);
    }

    private void fill(DictItemDO i, ItemSave req) {
        i.setLabel(req.label().trim());
        i.setLabelEn(StringUtils.hasText(req.labelEn()) ? req.labelEn().trim() : null);
        i.setTagType(TAG_TYPES.contains(req.tagType()) ? req.tagType() : "DEFAULT");
        i.setSort(req.sort());
        i.setIsDefault(req.isDefault());
        i.setRemark(req.remark());
    }

    /** 每个类型最多一个默认项 */
    private void clearOtherDefaults(DictItemDO keep) {
        for (DictItemDO other : itemMapper.selectByType(keep.getTypeCode())) {
            if (!other.getId().equals(keep.getId()) && Boolean.TRUE.equals(other.getIsDefault())) {
                other.setIsDefault(false);
                itemMapper.updateByIdOrFail(other);
            }
        }
    }

    // ==================== 前端全局缓存 ====================

    public DictBundle bundle() {
        return caches.get(SystemCaches.DICT, "bundle", () -> {
            Map<String, List<DictItemDO>> items = itemMapper.selectList(null).stream()
                    .collect(Collectors.groupingBy(DictItemDO::getTypeCode));
            List<BundleType> types = new ArrayList<>();
            for (DictTypeDO t : typeMapper.selectList(new LambdaQueryWrapper<DictTypeDO>().eq(DictTypeDO::getStatus, EnableStatus.ENABLED))) {
                List<BundleItem> list = items.getOrDefault(t.getCode(), List.of()).stream()
                        .sorted(Comparator.comparingInt(DictItemDO::getSort).thenComparing(DictItemDO::getId))
                        .map(i -> new BundleItem(i.getValue(), i.getLabel(), i.getLabelEn(), i.getTagType(), i.getSort(),
                                Boolean.TRUE.equals(i.getIsDefault()), i.getStatus() == EnableStatus.ENABLED))
                        .toList();
                types.add(new BundleType(t.getCode(), t.getName(), list));
            }
            return new DictBundle(version(), types);
        });
    }

    public long version() {
        Long v = typeMapper.selectVersion();
        return v == null ? 0 : v;
    }

    public void refreshCache() {
        typeMapper.increaseVersion();
        caches.clear(SystemCaches.DICT);
    }

    private void changed() {
        typeMapper.increaseVersion();
        caches.clear(SystemCaches.DICT);
    }

    // ==================== DictApi ====================

    private Map<String, BundleItem> index(String typeCode) {
        Map<String, BundleItem> m = new HashMap<>();
        for (BundleType t : bundle().types()) {
            if (t.code().equals(typeCode)) t.items().forEach(i -> m.put(i.value(), i));
        }
        return m;
    }

    @Override
    public List<DictItemDTO> getItems(String typeCode) {
        return bundle().types().stream().filter(t -> t.code().equals(typeCode)).flatMap(t -> t.items().stream())
                .filter(BundleItem::enabled)
                .map(i -> new DictItemDTO(i.value(), i.label(), i.labelEn(), i.tagType(), i.sort(), i.isDefault(), true))
                .toList();
    }

    @Override
    public boolean isValid(String typeCode, String value) {
        BundleItem i = index(typeCode).get(value);
        return i != null && i.enabled();
    }

    @Override
    public void validate(String typeCode, String value, String fieldName) {
        if (value == null || value.isEmpty()) return;
        if (!isValid(typeCode, value)) throw BizException.of(SystemErrorCodes.DICT_VALUE_INVALID, fieldName, value);
    }

    @Override
    public String label(String typeCode, String value) {
        if (value == null || value.isEmpty()) return "";
        BundleItem i = index(typeCode).get(value);
        return i == null ? value : i.label();
    }

    // ==================== 工具 ====================

    private DictTypeDO getType(Long id) {
        DictTypeDO t = typeMapper.selectById(id);
        if (t == null) throw new BizException(SystemErrorCodes.DICT_TYPE_NOT_EXISTS);
        return t;
    }

    private DictItemDO getItem(Long id) {
        DictItemDO i = itemMapper.selectById(id);
        if (i == null) throw new BizException(SystemErrorCodes.DICT_ITEM_NOT_EXISTS);
        return i;
    }

    private static EnableStatus parseStatus(String s) {
        return "DISABLED".equals(s) ? EnableStatus.DISABLED : EnableStatus.ENABLED;
    }

    private TypeResp toTypeResp(DictTypeDO t) {
        String moduleName = DictTypeDO.CUSTOM_MODULE.equals(t.getModuleCode()) ? "自定义" : moduleNames.getOrDefault(t.getModuleCode(), t.getModuleCode());
        return new TypeResp(t.getId(), t.getCode(), t.getName(), t.getModuleCode(), moduleName, Boolean.TRUE.equals(t.getBuiltin()),
                t.getStatus().name(), t.getRemark(), t.getVersion(), t.getUpdatedAt());
    }

    private ItemResp toItemResp(DictItemDO i) {
        return new ItemResp(i.getId(), i.getTypeCode(), i.getValue(), i.getLabel(), i.getLabelEn(), i.getTagType(), i.getSort(),
                Boolean.TRUE.equals(i.getIsDefault()), Boolean.TRUE.equals(i.getBuiltin()), i.getStatus().name(), i.getRemark(), i.getVersion());
    }
}
