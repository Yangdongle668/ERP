package com.erp.module.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.common.exception.BizException;
import com.erp.framework.module.ErpModule;
import com.erp.module.system.api.SystemErrorCodes;
import com.erp.module.system.api.param.ParamApi;
import com.erp.module.system.api.param.ParamDefinition;
import com.erp.module.system.api.param.ParamType;
import com.erp.module.system.controller.vo.ParamVOs.ParamChange;
import com.erp.module.system.controller.vo.ParamVOs.ParamModule;
import com.erp.module.system.controller.vo.ParamVOs.ParamResp;
import com.erp.module.system.dal.dataobject.ParamDO;
import com.erp.module.system.dal.mapper.ParamMapper;
import com.erp.module.system.service.support.SystemCaches;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 系统参数（01-10）：声明同步、页面读写、带缓存读取（{@link ParamApi} 实现）。
 */
@Slf4j
@Service
public class ParamService implements ParamApi {

    private final ParamMapper paramMapper;
    private final SystemCaches caches;
    private final ObjectMapper objectMapper;
    private final Map<String, String> moduleNames;
    private final Map<String, Integer> moduleOrders;

    public ParamService(ParamMapper paramMapper, SystemCaches caches, ObjectMapper objectMapper, List<ErpModule> modules) {
        this.paramMapper = paramMapper;
        this.caches = caches;
        this.objectMapper = objectMapper;
        this.moduleNames = modules.stream().collect(Collectors.toMap(ErpModule::code, ErpModule::name, (a, b) -> a));
        this.moduleOrders = modules.stream().collect(Collectors.toMap(ErpModule::code, ErpModule::order, (a, b) -> a));
    }

    // ==================== 声明同步（SYS-PAR-R04） ====================

    @Transactional(rollbackFor = Exception.class)
    public void sync(List<ParamDefinition> definitions) {
        Map<String, ParamDefinition> byKey = new LinkedHashMap<>();
        for (ParamDefinition d : definitions) {
            if (byKey.put(d.key(), d) != null) {
                throw new IllegalStateException("系统参数重复声明: " + d.key());
            }
        }
        Map<String, ParamDO> existing = paramMapper.selectList(null).stream().collect(Collectors.toMap(ParamDO::getParamKey, p -> p));
        for (ParamDefinition d : byKey.values()) {
            ParamDO p = existing.get(d.key());
            boolean isNew = p == null;
            if (isNew) {
                p = new ParamDO();
                p.setParamKey(d.key());
                p.setValue(d.defaultValue());
            }
            p.setModuleCode(d.moduleCode());
            p.setGroupName(d.groupName());
            p.setName(d.name());
            p.setValueType(d.type());
            p.setOptions(d.options().isEmpty() ? null : toJson(d.options()));
            p.setMinValue(d.minValue());
            p.setMaxValue(d.maxValue());
            p.setDefaultValue(d.defaultValue());
            p.setDescription(d.description());
            p.setSort(d.sort());
            p.setActive(true);
            if (isNew) paramMapper.insert(p);
            else paramMapper.updateByIdOrFail(p);
        }
        for (ParamDO p : existing.values()) {
            if (!byKey.containsKey(p.getParamKey()) && Boolean.TRUE.equals(p.getActive())) {
                p.setActive(false);
                paramMapper.updateByIdOrFail(p);
            }
        }
        caches.clear(SystemCaches.PARAM);
        log.info("[系统参数] 同步 {} 个参数声明", byKey.size());
    }

    // ==================== 页面 ====================

    public List<ParamModule> modules() {
        Map<String, Long> counts = activeParams().stream().collect(Collectors.groupingBy(ParamDO::getModuleCode, Collectors.counting()));
        return counts.entrySet().stream()
                .sorted(Comparator.comparingInt((Map.Entry<String, Long> e) -> moduleOrders.getOrDefault(e.getKey(), 999)).thenComparing(Map.Entry::getKey))
                .map(e -> new ParamModule(e.getKey(), moduleNames.getOrDefault(e.getKey(), e.getKey()), e.getValue().intValue()))
                .toList();
    }

    public List<ParamResp> list(String moduleCode, String keyword) {
        return activeParams().stream()
                .filter(p -> moduleCode == null || moduleCode.isEmpty() || moduleCode.equals(p.getModuleCode()))
                .filter(p -> keyword == null || keyword.isBlank() || p.getName().contains(keyword.trim()) || p.getParamKey().contains(keyword.trim()))
                .sorted(Comparator.comparingInt((ParamDO p) -> moduleOrders.getOrDefault(p.getModuleCode(), 999))
                        .thenComparing(ParamDO::getGroupName).thenComparingInt(ParamDO::getSort).thenComparing(ParamDO::getParamKey))
                .map(this::toResp)
                .toList();
    }

    /** 批量保存（只提交修改过的参数），返回变更明细供操作日志记录（SYS-PAR-R02） */
    @Transactional(rollbackFor = Exception.class)
    public List<String> save(List<ParamChange> changes) {
        List<String> diffs = new ArrayList<>();
        for (ParamChange c : changes) {
            ParamDO p = paramMapper.selectByKey(c.key());
            if (p == null || !Boolean.TRUE.equals(p.getActive())) throw BizException.of(SystemErrorCodes.PARAM_NOT_EXISTS, c.key());
            String value = normalize(p, c.value());
            if (Objects.equals(value, p.getValue())) continue;
            diffs.add(p.getGroupName() + " / " + p.getName() + "：" + display(p.getValue()) + " → " + display(value));
            p.setValue(value);
            paramMapper.updateByIdOrFail(p);
        }
        caches.clear(SystemCaches.PARAM);
        if (!diffs.isEmpty()) log.info("[系统参数] 修改 {}", diffs);
        return diffs;
    }

    @Transactional(rollbackFor = Exception.class)
    public void reset(String key) {
        ParamDO p = paramMapper.selectByKey(key);
        if (p == null) throw BizException.of(SystemErrorCodes.PARAM_NOT_EXISTS, key);
        p.setValue(p.getDefaultValue());
        paramMapper.updateByIdOrFail(p);
        caches.clear(SystemCaches.PARAM);
    }

    /** 按类型和范围校验并规范化（SYS-PAR-R01） */
    String normalize(ParamDO p, String raw) {
        String v = raw == null ? null : raw.trim();
        if (v == null || v.isEmpty()) {
            if (p.getValueType() == ParamType.STRING || p.getValueType() == ParamType.USER_LIST) return v == null ? null : "";
            throw BizException.of(SystemErrorCodes.PARAM_TYPE_INVALID, p.getName(), "不能为空");
        }
        switch (p.getValueType()) {
            case INT -> {
                long n;
                try {
                    n = Long.parseLong(v);
                } catch (NumberFormatException e) {
                    throw BizException.of(SystemErrorCodes.PARAM_TYPE_INVALID, p.getName(), "必须是整数");
                }
                checkRange(p, new BigDecimal(n));
                return String.valueOf(n);
            }
            case DECIMAL -> {
                BigDecimal d;
                try {
                    d = new BigDecimal(v);
                } catch (NumberFormatException e) {
                    throw BizException.of(SystemErrorCodes.PARAM_TYPE_INVALID, p.getName(), "必须是数字");
                }
                checkRange(p, d);
                return d.stripTrailingZeros().toPlainString();
            }
            case BOOL -> {
                if (!"true".equalsIgnoreCase(v) && !"false".equalsIgnoreCase(v)) {
                    throw BizException.of(SystemErrorCodes.PARAM_TYPE_INVALID, p.getName(), "必须是 true 或 false");
                }
                return v.toLowerCase();
            }
            case ENUM -> {
                boolean ok = options(p).stream().anyMatch(o -> o.value().equals(v));
                if (!ok) throw BizException.of(SystemErrorCodes.PARAM_TYPE_INVALID, p.getName(), "不是可选值");
                return v;
            }
            case TIME -> {
                try {
                    LocalTime.parse(v);
                } catch (DateTimeParseException e) {
                    throw BizException.of(SystemErrorCodes.PARAM_TYPE_INVALID, p.getName(), "时间格式应为 HH:mm");
                }
                return v;
            }
            case USER_LIST -> {
                for (String s : v.split(",")) {
                    if (!s.trim().matches("\\d+") && !s.trim().isEmpty() && !"admin".equals(s.trim())) {
                        throw BizException.of(SystemErrorCodes.PARAM_TYPE_INVALID, p.getName(), "用户 ID 格式不正确");
                    }
                }
                return v;
            }
            default -> {
                return v;
            }
        }
    }

    private static void checkRange(ParamDO p, BigDecimal n) {
        BigDecimal min = p.getMinValue() == null ? null : new BigDecimal(p.getMinValue());
        BigDecimal max = p.getMaxValue() == null ? null : new BigDecimal(p.getMaxValue());
        if ((min != null && n.compareTo(min) < 0) || (max != null && n.compareTo(max) > 0)) {
            throw BizException.of(SystemErrorCodes.PARAM_OUT_OF_RANGE, p.getName(),
                    min == null ? "-∞" : min.toPlainString(), max == null ? "+∞" : max.toPlainString());
        }
    }

    private static String display(String v) {
        if (v == null || v.isEmpty()) return "（空）";
        if ("true".equals(v)) return "是";
        if ("false".equals(v)) return "否";
        return v;
    }

    private List<ParamDO> activeParams() {
        return paramMapper.selectList(new LambdaQueryWrapper<ParamDO>().eq(ParamDO::getActive, true));
    }

    private ParamResp toResp(ParamDO p) {
        return new ParamResp(p.getParamKey(), p.getModuleCode(), p.getGroupName(), p.getName(), p.getValueType().name(),
                options(p), p.getMinValue(), p.getMaxValue(), p.getValue(), p.getDefaultValue(), p.getDescription(),
                !Objects.equals(p.getValue(), p.getDefaultValue()));
    }

    private List<ParamDefinition.Option> options(ParamDO p) {
        if (p.getOptions() == null || p.getOptions().isEmpty()) return List.of();
        try {
            return objectMapper.readValue(p.getOptions(), new TypeReference<List<ParamDefinition.Option>>() {
            });
        } catch (Exception e) {
            return List.of();
        }
    }

    private String toJson(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    // ==================== ParamApi（带缓存） ====================

    /** 全部参数值快照（缓存） */
    private HashMap<String, String> values() {
        return caches.get(SystemCaches.PARAM, "all", () -> {
            HashMap<String, String> m = new HashMap<>();
            for (ParamDO p : paramMapper.selectList(null)) m.put(p.getParamKey(), p.getValue());
            return m;
        });
    }

    private String raw(String key) {
        HashMap<String, String> all = values();
        if (!all.containsKey(key)) throw BizException.of(SystemErrorCodes.PARAM_NOT_EXISTS, key);
        return all.get(key);
    }

    @Override
    public String getString(String key) {
        return raw(key);
    }

    @Override
    public int getInt(String key) {
        String v = raw(key);
        return v == null || v.isEmpty() ? 0 : Integer.parseInt(v);
    }

    @Override
    public BigDecimal getDecimal(String key) {
        String v = raw(key);
        return v == null || v.isEmpty() ? BigDecimal.ZERO : new BigDecimal(v);
    }

    @Override
    public boolean getBool(String key) {
        return Boolean.parseBoolean(raw(key));
    }

    @Override
    public List<Long> getUserIds(String key) {
        String v = raw(key);
        if (v == null || v.isBlank()) return List.of();
        return Arrays.stream(v.split(",")).map(String::trim).filter(s -> s.matches("\\d+")).map(Long::valueOf).toList();
    }
}
