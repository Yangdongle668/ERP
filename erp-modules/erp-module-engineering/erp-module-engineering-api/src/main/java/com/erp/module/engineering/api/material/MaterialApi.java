package com.erp.module.engineering.api.material;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** 物料查询与校验，供其他模块调用（需求 05-研发工程 README 第 10 节）。 */
public interface MaterialApi {

    Optional<MaterialDTO> getMaterial(Long id);

    /** 批量查询，结果顺序不保证；不存在的 ID 不会出现在结果中。 */
    List<MaterialDTO> getMaterials(Collection<Long> ids);

    /**
     * 校验物料存在且已启用（新建业务单据时调用）。
     *
     * @throws com.erp.common.exception.BizException 不存在或未启用
     */
    MaterialDTO validateUsable(Long id);

    MaterialPlanAttr getPlanAttr(Long id);

    MaterialPurchaseAttr getPurchaseAttr(Long id);

    MaterialStockAttr getStockAttr(Long id);

    MaterialQualityAttr getQualityAttr(Long id);

    /**
     * 换算为基本单位数量（ENG-MAT-T07）：物料换算优先，其次通用换算；都没有时抛出业务异常。
     * 结果按基本单位精度舍入。
     */
    BigDecimal convertToBase(Long materialId, BigDecimal qty, String uom);

    /** 按编码前缀或名称/规格模糊搜索启用物料；types 为空表示全部类型；最多 limit 条（≤50） */
    List<MaterialDTO> search(String keyword, Collection<MaterialType> types, int limit);
}
