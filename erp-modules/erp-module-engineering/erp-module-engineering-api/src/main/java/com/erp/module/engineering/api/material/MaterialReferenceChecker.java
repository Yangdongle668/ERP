package com.erp.module.engineering.api.material;

/**
 * 物料被引用情况的扩展点（ENG-MAT-R08、R09、R10）：仓库、资材、销售、生产等模块各实现一个 Bean，
 * 研发工程在停用提示、删除、修改库存管理方式时汇总各模块的结果。BOM 的引用由研发工程自己统计。
 *
 * <pre>{@code
 * @Bean
 * public MaterialReferenceChecker inventoryMaterialChecker(StockMapper m) {
 *     return materialId -> new MaterialUsage(m.sumQty(materialId), m.countOpenDocs(materialId), m.everUsed(materialId), ...);
 * }
 * }</pre>
 */
@FunctionalInterface
public interface MaterialReferenceChecker {

    MaterialUsage usage(Long materialId);
}
