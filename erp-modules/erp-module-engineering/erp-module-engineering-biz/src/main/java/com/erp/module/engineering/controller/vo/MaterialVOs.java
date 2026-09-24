package com.erp.module.engineering.controller.vo;

import com.erp.module.engineering.api.material.MaterialStatus;

import java.math.BigDecimal;
import java.util.List;

/** 物料的其他接口参数与结果 */
public final class MaterialVOs {

    private MaterialVOs() {
    }

    /** 查重请求（名称、规格、制造商料号失焦时调用） */
    public record DuplicateCheckReq(Long id, Long categoryId, String name, String spec, String mpn) {
    }

    /** 查重结果；mode 为参数 eng.material.duplicate-check：OFF / WARN / BLOCK */
    public record DuplicateCheckResp(String mode, List<Suspect> suspects) {
    }

    /** @param reason NAME_SPEC 名称+规格相同 / MPN 制造商料号相同 */
    public record Suspect(Long id, String code, String name, String spec, String mpn, MaterialStatus status, String reason) {
    }

    /** 停用前的引用统计（ENG-MAT-R08） */
    public record References(BigDecimal stockQty, int openDocCount, int bomCount) {
    }

    public record BatchReq(List<Long> ids) {
    }

    /** 批量操作结果：失败的逐条给出原因 */
    public record BatchResult(int success, List<Failure> failures) {
    }

    public record Failure(Long id, String code, String message) {
    }

    /** 以本物料为父件 / 子件的 BOM（物料详情“BOM”页签） */
    public record MaterialBoms(List<BomBrief> asParent, List<BomBrief> asComponent) {
    }

    public record BomBrief(Long bomId, String docNo, Long materialId, String materialCode, String materialName, int version,
                           boolean isDefault, String status, BigDecimal qtyPer, String uom) {
    }

    /** 启用前端需要的开关：是否走审批、是否允许手工编码、查重方式 */
    public record Settings(boolean enableApproval, boolean manualCodeAllowed, String duplicateCheck, boolean canViewCost) {
    }
}
