package com.erp.module.bi.controller.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** AI 分析接口的请求 */
public final class AiVOs {

    private AiVOs() {
    }

    public record ConversationSave(@Size(max = 128) String title) {
    }

    public record Ask(@NotBlank(message = "请输入问题") @Size(max = 2000) String question) {
    }

    /** feedback：UP / DOWN，为空表示取消 */
    public record Feedback(String feedback, @Size(max = 500) String remark) {
    }

    /** AI 设置（只读展示；修改在系统参数页面） */
    public record Settings(boolean enabled, String provider, String baseUrl, String model, String maskedKey, String keySource, boolean mask, int quota) {
    }
}
