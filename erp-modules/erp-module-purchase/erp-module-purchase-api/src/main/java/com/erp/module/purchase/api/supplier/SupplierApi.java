package com.erp.module.purchase.api.supplier;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** 供应商查询与校验（需求 07-资材 README 第 11 节），供品质、财务、研发工程、PMC 使用。 */
public interface SupplierApi {

    Optional<SupplierDTO> getSupplier(Long id);

    /** 校验供应商为合格供应商且资质有效（PUR-SUP-R04）。 */
    SupplierDTO validateQualified(Long id);

    /** 按编码前缀或名称/简称模糊搜索；statuses 为空表示全部状态；最多 limit 条（≤50） */
    List<SupplierDTO> search(String keyword, Collection<SupplierStatus> statuses, int limit);

    /** 物料的默认供应商（可供物料中标记为默认、非停用的供应商） */
    Optional<SupplierDTO> getDefaultSupplier(Long materialId);
}
