package com.erp.module.inventory.api.doc;

/** 来源业务单据引用：生成仓库单据时必填，用于回写与幂等。 */
public record SourceRef(String sourceType, Long sourceId, String sourceNo) {
}
