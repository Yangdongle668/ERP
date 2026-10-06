package com.erp.module.backup.config;

import com.erp.framework.module.ErpModule;
import com.erp.module.system.api.param.ParamDefinition;
import com.erp.module.system.api.param.ParamDefinitions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 系统备份模块的声明式注册（需求 14-系统备份）。只有超级管理员（拥有全部权限 {@code *}）可以访问，
 * 因此不声明权限点，接口与菜单都要求 {@code *}。
 */
@Configuration
public class BackupModuleConfig {

    public static final String MODULE = "backup";

    public static final String MANUAL = "MANUAL";
    public static final String AUTO = "AUTO";
    public static final String PRE_RESTORE = "PRE_RESTORE";
    public static final String UPLOAD = "UPLOAD";

    public static final String P_AUTO_ENABLED = "bak.auto.enabled";
    public static final String P_AUTO_INCLUDE_FILES = "bak.auto.include-files";
    public static final String P_AUTO_KEEP = "bak.auto.keep";

    @Bean
    public ErpModule backupModule() {
        return new ErpModule(MODULE, "系统备份", 500);
    }

    @Bean
    public ParamDefinitions backupParams() {
        return ParamDefinitions.of(
                ParamDefinition.bool(P_AUTO_ENABLED, MODULE, "自动备份", "开启每日自动备份", false, "定时任务 BAK_AUTO，默认每天 03:15").sort(10),
                ParamDefinition.bool(P_AUTO_INCLUDE_FILES, MODULE, "自动备份", "自动备份包含附件", true, "附件为本地存储时有效").sort(20),
                ParamDefinition.integer(P_AUTO_KEEP, MODULE, "自动备份", "保留份数", 7, 1, 100, "自动备份与恢复前备份各保留最近的份数，手工与上传的备份不自动删除").sort(30));
    }
}
