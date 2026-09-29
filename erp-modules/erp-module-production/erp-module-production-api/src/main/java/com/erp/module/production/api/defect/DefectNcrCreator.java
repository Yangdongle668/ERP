package com.erp.module.production.api.defect;

import java.math.BigDecimal;
import java.util.List;

/**
 * 不良生成 NCR 的扩展点（MFG 06 3.1）：品质模块实现，返回 NCR 编号。
 * 未实现时“生成 NCR”按钮不可用。
 */
public interface DefectNcrCreator {

    String create(NcrRequest request);

    /** @param qty 不良数量（基本单位）；fileIds 为不良照片 */
    record NcrRequest(Long defectId, Long prodOrderId, String prodOrderNo, Long materialId, String batchNo, int operationSeq, String defectCode,
                      BigDecimal qty, String description, List<Long> fileIds) {
    }
}
