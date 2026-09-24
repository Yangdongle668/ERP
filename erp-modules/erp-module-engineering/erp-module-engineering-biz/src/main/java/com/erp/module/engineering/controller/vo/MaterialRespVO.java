package com.erp.module.engineering.controller.vo;

import com.erp.module.engineering.api.material.IssueRule;
import com.erp.module.engineering.api.material.MaterialStatus;
import com.erp.module.engineering.api.material.MaterialType;
import com.erp.module.engineering.api.material.OrderPolicy;
import com.erp.module.engineering.api.material.SourceType;
import com.erp.module.engineering.api.material.Tracking;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 物料（列表与详情共用）。standardCost 在没有字段权限 eng:material:cost 时为 null（ENG-MAT-R13）；
 * uoms 只在详情中返回。
 */
public record MaterialRespVO(
        Long id, String code, String name, String nameEn, String spec, MaterialType materialType, Long categoryId,
        String categoryCode, String categoryName, String baseUom, String drawingNo, String revision, String brand, String manufacturer,
        String mpn, String hsCode, BigDecimal unitNetWeight, BigDecimal unitGrossWeight, Long imageFileId, MaterialStatus status, String remark,
        SourceType sourceType, Integer leadTimeDays, BigDecimal safetyStock, BigDecimal maxStock, OrderPolicy orderPolicy,
        BigDecimal fixedLotQty, Integer periodDays, BigDecimal moq, BigDecimal mpq, Long plannerId, String plannerName, Integer lowLevelCode,
        Long buyerId, String buyerName, String purchaseUom, BigDecimal overReceivePct,
        Tracking tracking, IssueRule issueRule, Integer shelfLifeDays, BigDecimal minRemainingLifePct,
        Boolean iqcRequired, Boolean fqcRequired, Boolean oqcRequired,
        BigDecimal standardCost, String salesUom, BigDecimal purchaseTaxRate, BigDecimal salesTaxRate,
        Integer version, Long createdBy, String createdByName, LocalDateTime createdAt, LocalDateTime updatedAt,
        List<UomRow> uoms) {

    /** @param used 已被单据使用（不能删除、不能改换算率） */
    public record UomRow(Long id, String uom, BigDecimal rate, String remark, boolean used) {
    }
}
