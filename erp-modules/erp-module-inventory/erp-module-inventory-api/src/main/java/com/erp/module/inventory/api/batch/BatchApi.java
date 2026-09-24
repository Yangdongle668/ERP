package com.erp.module.inventory.api.batch;

import java.util.List;
import java.util.Optional;

/** 批次（需求 08-仓库/07）。品质模块冻结批次后，仓库人员不能手工解冻（INV-BAT-R05）。 */
public interface BatchApi {

    Optional<BatchDTO> get(Long materialId, String batchNo);

    /**
     * @param moduleCode 冻结来源模块，如 quality
     * @param sourceNo   来源单号，如 NCR 编号
     */
    void freeze(Long materialId, String batchNo, String reason, String moduleCode, String sourceNo);

    void unfreeze(Long materialId, String batchNo, String reason, String moduleCode);

    /** 批次的全部出入库流水（按时间） */
    List<BatchTxn> trace(Long materialId, String batchNo);
}
