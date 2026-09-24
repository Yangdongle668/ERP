package com.erp.module.engineering.api.bom;

import com.erp.common.enums.DocStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * BOM 版本对外视图。
 *
 * @param version 版本号（显示为 V{version}）
 * @param baseQty 基数：用量是生产 baseQty 个父件所需的子件数量
 */
public record BomDTO(Long id, String docNo, Long materialId, int version, BigDecimal baseQty, boolean isDefault,
                     LocalDate effectiveDate, DocStatus status, List<Line> lines) {

    /**
     * @param scrapRate 损耗率（小数，0.02 表示 2%）
     */
    public record Line(Long id, int lineNo, Long componentId, BigDecimal qtyPer, String uom, BigDecimal scrapRate,
                       String positionNo, IssueMethod issueMethod, Integer operationSeq, boolean key, List<Substitute> substitutes) {
    }

    /** @param ratio 1 个主料 = ratio 个替代料 */
    public record Substitute(Long substituteId, int priority, BigDecimal ratio) {
    }
}
