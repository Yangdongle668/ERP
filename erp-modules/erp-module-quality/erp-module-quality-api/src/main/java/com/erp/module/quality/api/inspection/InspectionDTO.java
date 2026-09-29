package com.erp.module.quality.api.inspection;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 检验单对外视图（数量为基本单位）。
 *
 * @param inspStatus PENDING / INSPECTING / WAIT_MRB / JUDGED / HANDLED / CANCELED
 * @param result     QUALIFIED / REJECTED / CONCESSION / SORTED，未判定为空
 */
public record InspectionDTO(Long id, String docNo, InspectType inspectType, String ipqcKind, Long materialId, String batchNo, BigDecimal lotQty,
                            String upstreamType, Long upstreamId, Long upstreamLineId, String upstreamNo, String inspStatus, String result,
                            BigDecimal qualifiedQty, BigDecimal concessionQty, BigDecimal rejectedQty, LocalDateTime judgeAt, Long ncrId) {
}
