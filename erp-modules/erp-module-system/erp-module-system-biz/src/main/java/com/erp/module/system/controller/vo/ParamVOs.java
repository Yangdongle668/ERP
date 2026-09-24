package com.erp.module.system.controller.vo;

import com.erp.module.system.api.param.ParamDefinition;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

/** 系统参数页面 VO */
public final class ParamVOs {

    private ParamVOs() {
    }

    /** 左侧模块导航 */
    public record ParamModule(String moduleCode, String moduleName, int count) {
    }

    /** @param modified 当前值 ≠ 默认值 */
    public record ParamResp(String key, String moduleCode, String groupName, String name, String valueType,
                            List<ParamDefinition.Option> options, String minValue, String maxValue,
                            String value, String defaultValue, String description, boolean modified) {
    }

    public record ParamChange(@NotBlank String key, String value) {
    }

    /** 登录页等不需要登录即可读取的公开参数 */
    public record PublicParams(String systemName, int captchaAfterFails, int idleTimeoutMinutes) {
    }
}
