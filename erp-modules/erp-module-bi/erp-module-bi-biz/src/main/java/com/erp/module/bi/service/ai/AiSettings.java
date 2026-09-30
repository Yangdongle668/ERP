package com.erp.module.bi.service.ai;

import com.erp.module.bi.config.BiModuleConfig;
import com.erp.module.system.api.param.ParamApi;
import org.springframework.stereotype.Component;

/** AI 参数读取（需求 13-BI与AI README 第 5 节）。API Key 优先取参数，未设置时取环境变量 ERP_AI_API_KEY */
@Component
public class AiSettings {

    public static final String ENV_KEY = "ERP_AI_API_KEY";

    public record Snapshot(boolean enabled, String provider, String model, String apiKey, String keySource, boolean mask, int quota) {

        /** 已启用且配置了 API Key（AI-R01） */
        public boolean ready() {
            return enabled && apiKey != null && !apiKey.isBlank();
        }

        /** 页面只显示后 4 位 */
        public String maskedKey() {
            if (apiKey == null || apiKey.isBlank()) return null;
            return "****" + (apiKey.length() <= 4 ? apiKey : apiKey.substring(apiKey.length() - 4));
        }
    }

    private final ParamApi paramApi;

    public AiSettings(ParamApi paramApi) {
        this.paramApi = paramApi;
    }

    public Snapshot get() {
        String key = paramApi.getString(BiModuleConfig.P_AI_KEY);
        String source = "PARAM";
        if (key == null || key.isBlank()) {
            key = System.getenv(ENV_KEY);
            source = key == null || key.isBlank() ? "NONE" : "ENV";
        }
        String model = paramApi.getString(BiModuleConfig.P_AI_MODEL);
        return new Snapshot(paramApi.getBool(BiModuleConfig.P_AI_ENABLED), paramApi.getString(BiModuleConfig.P_AI_PROVIDER),
                model == null || model.isBlank() ? BiModuleConfig.DEFAULT_MODEL : model.trim(), key, source, paramApi.getBool(BiModuleConfig.P_AI_MASK),
                paramApi.getInt(BiModuleConfig.P_AI_QUOTA));
    }
}
