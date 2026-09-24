package com.erp.module.engineering.api.bom;

import com.erp.module.engineering.api.material.MaterialType;
import com.erp.module.engineering.api.material.SourceType;

import java.math.BigDecimal;

/**
 * 多级展开的一行（需求 05-03 第 3 节统一口径）。
 *
 * @param level       层级，从 1 开始
 * @param path        层级编号，如 1、1.2、1.2.1
 * @param parentId    直接上级物料
 * @param qtyPer      单层用量（每 1 个直接上级，已除以基数）
 * @param totalQtyPer 累计用量（每 1 个顶层父件，含各层损耗）
 * @param requiredQty 需求量 = 顶层数量 × 累计用量，按子件单位精度向上取整
 * @param bomId       该子件自身使用的 BOM（没有已审核默认 BOM 时为空，即末级）
 */
public record BomExplodeLine(int level, String path, Long parentId, Long componentId, MaterialType materialType, SourceType sourceType,
                             String uom, BigDecimal qtyPer, BigDecimal scrapRate, BigDecimal totalQtyPer, BigDecimal requiredQty,
                             IssueMethod issueMethod, Integer operationSeq, Long bomId, Integer bomVersion) {
}
