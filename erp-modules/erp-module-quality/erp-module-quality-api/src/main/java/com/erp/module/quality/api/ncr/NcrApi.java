package com.erp.module.quality.api.ncr;

import java.math.BigDecimal;
import java.util.List;

/** NCR（生产不良、库存问题等由其他模块发起） */
public interface NcrApi {

    /**
     * 生成草稿 NCR（来源 PRODUCTION / INVENTORY），返回 NCR 编号。
     *
     * @param qty 不合格数量（基本单位）
     */
    String createNcr(NcrCreateRequest request);

    record NcrCreateRequest(String source, String sourceNo, Long materialId, String batchNo, Long supplierId, BigDecimal qty, String description,
                            String defectCode, List<Long> fileIds) {
    }
}
