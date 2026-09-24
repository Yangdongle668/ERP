package com.erp.module.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.common.exception.BizException;
import com.erp.framework.module.ErpModule;
import com.erp.framework.security.LoginUser;
import com.erp.module.system.api.SystemErrorCodes;
import com.erp.module.system.api.permission.PermissionDefinition;
import com.erp.module.system.controller.vo.PermissionVOs.GroupNode;
import com.erp.module.system.controller.vo.PermissionVOs.ModuleNode;
import com.erp.module.system.controller.vo.PermissionVOs.PermissionNode;
import com.erp.module.system.dal.dataobject.PermissionDO;
import com.erp.module.system.dal.mapper.PermissionMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** 权限点目录（01-03 1.1）：启动时由模块声明同步，授权界面按模块 → 分组 → 权限点展示。 */
@Slf4j
@Service
public class PermissionService {

    private final PermissionMapper permissionMapper;
    private final Map<String, String> moduleNames;
    private final Map<String, Integer> moduleOrders;

    public PermissionService(PermissionMapper permissionMapper, List<ErpModule> modules) {
        this.permissionMapper = permissionMapper;
        this.moduleNames = modules.stream().collect(Collectors.toMap(ErpModule::code, ErpModule::name, (a, b) -> a));
        this.moduleOrders = modules.stream().collect(Collectors.toMap(ErpModule::code, ErpModule::order, (a, b) -> a));
    }

    /** 同步声明：新增/更新名称；代码中删除的权限点标记为失效（已有授权记录保留） */
    @Transactional(rollbackFor = Exception.class)
    public void sync(List<PermissionDefinition> definitions) {
        Map<String, PermissionDO> declared = new LinkedHashMap<>();
        Map<String, String> owner = new HashMap<>();
        for (PermissionDefinition d : definitions) {
            for (PermissionDefinition.Item item : d.items()) {
                String where = d.moduleCode() + "/" + d.groupCode();
                String prev = owner.put(item.code(), where);
                if (prev != null) {
                    throw new IllegalStateException("权限点重复声明: " + item.code() + "（" + prev + "、" + where + "）");
                }
                PermissionDO p = new PermissionDO();
                p.setCode(item.code());
                p.setModuleCode(d.moduleCode());
                p.setGroupCode(d.groupCode());
                p.setGroupName(d.groupName());
                p.setGroupSort(d.groupSort());
                p.setName(item.name());
                p.setPermType(item.type().name());
                p.setDependsOn(String.join(",", item.dependsOn()));
                p.setSort(item.sort());
                p.setActive(true);
                p.setUpdatedAt(LocalDateTime.now());
                declared.put(item.code(), p);
            }
        }
        Map<String, PermissionDO> existing = permissionMapper.selectList(null).stream()
                .collect(Collectors.toMap(PermissionDO::getCode, p -> p));
        for (PermissionDO p : declared.values()) {
            if (existing.containsKey(p.getCode())) permissionMapper.updateById(p);
            else permissionMapper.insert(p);
        }
        for (PermissionDO p : existing.values()) {
            if (!declared.containsKey(p.getCode()) && Boolean.TRUE.equals(p.getActive())) {
                p.setActive(false);
                p.setUpdatedAt(LocalDateTime.now());
                permissionMapper.updateById(p);
            }
        }
        log.info("[权限点] 同步 {} 个权限点", declared.size());
    }

    /** 权限点树（仅有效的） */
    public List<ModuleNode> tree() {
        List<PermissionDO> all = permissionMapper.selectList(new LambdaQueryWrapper<PermissionDO>().eq(PermissionDO::getActive, true));
        Map<String, Map<String, List<PermissionDO>>> byModule = all.stream().collect(Collectors.groupingBy(PermissionDO::getModuleCode,
                Collectors.groupingBy(PermissionDO::getGroupCode)));
        List<ModuleNode> modules = new ArrayList<>();
        byModule.entrySet().stream()
                .sorted(Comparator.comparingInt((Map.Entry<String, ?> e) -> moduleOrders.getOrDefault(e.getKey(), 999)).thenComparing(Map.Entry::getKey))
                .forEach(me -> {
                    List<GroupNode> groups = me.getValue().values().stream()
                            .sorted(Comparator.comparingInt((List<PermissionDO> g) -> g.get(0).getGroupSort()).thenComparing(g -> g.get(0).getGroupCode()))
                            .map(g -> new GroupNode(g.get(0).getGroupCode(), g.get(0).getGroupName(), g.stream()
                                    .sorted(Comparator.comparingInt((PermissionDO p) -> "MENU".equals(p.getPermType()) ? 0 : 1).thenComparingInt(PermissionDO::getSort))
                                    .map(p -> new PermissionNode(p.getCode(), p.getName(), p.getPermType(), splitDeps(p.getDependsOn())))
                                    .toList()))
                            .toList();
                    modules.add(new ModuleNode(me.getKey(), moduleNames.getOrDefault(me.getKey(), me.getKey()), groups));
                });
        return modules;
    }

    /**
     * 校验授权的权限点（SYS-ROL-R06），并自动补齐依赖；返回最终权限集合。
     * {@code *} 只能属于内置超级管理员。
     */
    public Set<String> resolveGrant(Collection<String> codes) {
        Map<String, PermissionDO> active = permissionMapper.selectList(new LambdaQueryWrapper<PermissionDO>().eq(PermissionDO::getActive, true))
                .stream().collect(Collectors.toMap(PermissionDO::getCode, p -> p));
        Set<String> result = new java.util.TreeSet<>();
        for (String code : codes) {
            if (LoginUser.ALL_PERMISSION.equals(code) || !active.containsKey(code)) {
                throw BizException.of(SystemErrorCodes.ROLE_PERMISSION_NOT_EXISTS, code);
            }
            addWithDeps(code, active, result);
        }
        return result;
    }

    private void addWithDeps(String code, Map<String, PermissionDO> active, Set<String> result) {
        if (!result.add(code)) return;
        PermissionDO p = active.get(code);
        if (p == null) return;
        for (String dep : splitDeps(p.getDependsOn())) {
            if (active.containsKey(dep)) addWithDeps(dep, active, result);
        }
    }

    private static List<String> splitDeps(String s) {
        return s == null || s.isBlank() ? List.of() : Arrays.stream(s.split(",")).map(String::trim).filter(x -> !x.isEmpty()).toList();
    }
}
