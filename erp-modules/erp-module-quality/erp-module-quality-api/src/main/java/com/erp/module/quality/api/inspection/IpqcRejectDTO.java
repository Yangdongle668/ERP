package com.erp.module.quality.api.inspection;

import java.time.LocalDateTime;

/** 生产订单某工序最近一次已判定的 IPQC 为拒收（QC-INS-R09：生产订单工序显示“IPQC 不合格”警示） */
public record IpqcRejectDTO(int operationSeq, Long inspectionId, String docNo, LocalDateTime judgeAt, Long ncrId) {
}
