package com.erp.module.engineering.api.tooling;

import java.util.List;
import java.util.Optional;

/** 工装（生产模块报工、派工时使用） */
public interface ToolingApi {

    Optional<ToolingDTO> get(Long toolingId);

    /** 某物料适用的可用工装 */
    List<ToolingDTO> listUsable(Long materialId);

    /** 校验工装可用（ENG-TL-R03、R05），不可用时抛出 BizException */
    void validateUsable(Long toolingId);

    /**
     * 报工累加使用次数（ENG-TL-R02）：count 为 ceil((合格数 + 不良数) ÷ 模穴数)，由调用方按 {@link #usageOf} 计算。
     * 报工反审核时传负数扣回。
     */
    void addUsage(Long toolingId, int count, String sourceDocNo);

    /** 按产出数量和模穴数计算使用次数 */
    int usageOf(Long toolingId, java.math.BigDecimal outputQty);
}
