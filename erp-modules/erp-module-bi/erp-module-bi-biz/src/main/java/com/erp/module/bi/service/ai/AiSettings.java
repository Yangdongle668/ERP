package com.erp.module.bi.service.ai;

import com.erp.module.bi.config.BiModuleConfig;
import com.erp.module.system.api.param.ParamApi;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * AI 参数读取（需求 13-BI与AI README 第 5 节）。供应商 DeepSeek / 通义千问 / 其他 OpenAI 兼容接口，接口地址与模型为空时按供应商默认。
 * API Key 优先取参数，未设置时取环境变量 ERP_AI_API_KEY；接口地址参数为空时可用环境变量 ERP_AI_BASE_URL 覆盖默认地址。
 */
@Component
public class AiSettings {

    public static final String ENV_KEY = "ERP_AI_API_KEY";
    public static final String ENV_BASE_URL = "ERP_AI_BASE_URL";

    static final Map<String, String> DEFAULT_BASE_URL = Map.of(
            BiModuleConfig.VENDOR_DEEPSEEK, "https://api.deepseek.com",
            BiModuleConfig.VENDOR_QWEN, "https://dashscope.aliyuncs.com/compatible-mode/v1");
    static final Map<String, String> DEFAULT_MODEL = Map.of(
            BiModuleConfig.VENDOR_DEEPSEEK, "deepseek-chat",
            BiModuleConfig.VENDOR_QWEN, "qwen-plus");

    /** provider 为供应商（DEEPSEEK / QWEN / OPENAI_COMPATIBLE），调用协议统一为 OpenAI 兼容接口 */
    public record Snapshot(boolean enabled, String provider, String baseUrl, String model, String apiKey, String keySource, boolean mask, int quota) {

        /** 已启用且配置了 API Key、接口地址、模型（AI-R01） */
        public boolean ready() {
            return enabled && notBlank(apiKey) && notBlank(baseUrl) && notBlank(model);
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
        if (!notBlank(key)) {
            key = System.getenv(ENV_KEY);
            source = notBlank(key) ? "ENV" : "NONE";
        }
        String provider = paramApi.getString(BiModuleConfig.P_AI_PROVIDER);
        if (!notBlank(provider)) provider = BiModuleConfig.VENDOR_DEEPSEEK;
        String baseUrl = paramApi.getString(BiModuleConfig.P_AI_BASE_URL);
        if (!notBlank(baseUrl)) baseUrl = System.getenv(ENV_BASE_URL);
        if (!notBlank(baseUrl)) baseUrl = DEFAULT_BASE_URL.get(provider);
        String model = paramApi.getString(BiModuleConfig.P_AI_MODEL);
        if (!notBlank(model)) model = DEFAULT_MODEL.get(provider);
        return new Snapshot(paramApi.getBool(BiModuleConfig.P_AI_ENABLED), provider, baseUrl == null ? null : baseUrl.trim(),
                model == null ? null : model.trim(), key, source, paramApi.getBool(BiModuleConfig.P_AI_MASK), paramApi.getInt(BiModuleConfig.P_AI_QUOTA));
    }

    static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
