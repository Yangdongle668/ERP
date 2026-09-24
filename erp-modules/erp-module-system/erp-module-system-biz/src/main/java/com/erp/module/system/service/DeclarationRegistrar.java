package com.erp.module.system.service;

import com.erp.module.system.api.dict.DictDefinition;
import com.erp.module.system.api.param.ParamDefinition;
import com.erp.module.system.api.param.ParamDefinitions;
import com.erp.module.system.api.permission.PermissionDefinition;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 声明式注册（01-系统管理 README 第 5 节）：所有单例创建完成后（数据库迁移已执行），
 * 把各模块声明的权限点、字典、参数、编码规则同步到数据库。同一编码被重复声明时启动失败。
 */
@Slf4j
@Component
public class DeclarationRegistrar implements SmartInitializingSingleton {

    private final ObjectProvider<PermissionDefinition> permissions;
    private final ObjectProvider<DictDefinition> dicts;
    private final ObjectProvider<ParamDefinition> params;
    private final ObjectProvider<ParamDefinitions> paramGroups;
    private final PermissionService permissionService;
    private final DictService dictService;
    private final ParamService paramService;
    private final CodeRuleService codeRuleService;

    public DeclarationRegistrar(ObjectProvider<PermissionDefinition> permissions, ObjectProvider<DictDefinition> dicts,
                                ObjectProvider<ParamDefinition> params, ObjectProvider<ParamDefinitions> paramGroups,
                                PermissionService permissionService, DictService dictService, ParamService paramService,
                                CodeRuleService codeRuleService) {
        this.permissions = permissions;
        this.dicts = dicts;
        this.params = params;
        this.paramGroups = paramGroups;
        this.permissionService = permissionService;
        this.dictService = dictService;
        this.paramService = paramService;
        this.codeRuleService = codeRuleService;
    }

    @Override
    public void afterSingletonsInstantiated() {
        permissionService.sync(permissions.orderedStream().toList());
        dictService.sync(dicts.orderedStream().toList());
        List<ParamDefinition> all = new ArrayList<>(params.orderedStream().toList());
        paramGroups.orderedStream().forEach(g -> all.addAll(g.items()));
        paramService.sync(all);
        codeRuleService.sync();
        log.info("[声明式注册] 完成");
    }
}
