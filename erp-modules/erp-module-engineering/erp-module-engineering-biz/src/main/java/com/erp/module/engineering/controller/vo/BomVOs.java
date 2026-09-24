package com.erp.module.engineering.controller.vo;

import com.erp.common.result.PageParam;
import com.erp.module.engineering.api.bom.IssueMethod;
import com.erp.module.engineering.api.material.MaterialStatus;
import com.erp.module.engineering.api.material.MaterialType;
import com.erp.module.engineering.api.material.SourceType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** BOM 接口的请求/响应（需求 05-03） */
public final class BomVOs {

    private BomVOs() {
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class BomQuery extends PageParam {
        private Long materialId;
        /** 父件编码前缀或名称模糊 */
        private String keyword;
        /** 状态，逗号分隔 */
        private String statuses;
        /** 仅默认版本 */
        private Boolean defaultOnly;
        /** 直接包含此子件 */
        private Long componentId;
        /** 导出：勾选的 BOM（逗号分隔），为空按查询条件 */
        private String ids;
        /** 导出方式：SINGLE 单层 / MULTI 多级展开 */
        private String mode;
    }

    public record BomRow(Long id, String docNo, Long materialId, String materialCode, String materialName, String materialSpec,
                         MaterialType materialType, String uom, int version, BigDecimal baseQty, boolean isDefault, int lineCount,
                         String description, String status, String updatedByName, LocalDateTime updatedAt) {
    }

    public record BomDetail(Long id, String docNo, Long materialId, String materialCode, String materialName, String materialSpec,
                            MaterialType materialType, MaterialStatus materialStatus, String uom, int version, BigDecimal baseQty,
                            boolean isDefault, LocalDate effectiveDate, String description, String remark, String status,
                            Long copiedFromId, String copiedFromNo, String createdByName, LocalDateTime createdAt,
                            String updatedByName, LocalDateTime updatedAt, int rowVersion, List<LineResp> lines) {
    }

    public record LineResp(Long id, int lineNo, Long componentId, String componentCode, String componentName, String componentSpec,
                           MaterialType componentType, MaterialStatus componentStatus, String uom, BigDecimal qtyPer, BigDecimal scrapRate,
                           String positionNo, IssueMethod issueMethod, Integer operationSeq, boolean isKey, String remark,
                           List<SubstituteResp> substitutes) {
    }

    public record SubstituteResp(Long substituteId, String code, String name, String spec, String uom, MaterialStatus status,
                                 int priority, BigDecimal ratio, String remark) {
    }

    /**
     * 新建/修改 BOM。
     *
     * @param rowVersion 修改时回传读取时的乐观锁版本
     */
    public record BomSave(
            @NotNull(message = "请选择父件") Long materialId,
            @NotNull(message = "请输入基数") @DecimalMin(value = "0", inclusive = false, message = "基数必须大于 0") BigDecimal baseQty,
            @Size(max = 256) String description,
            @Size(max = 512) String remark,
            @Valid List<LineSave> lines,
            List<Long> fileIds,
            Integer rowVersion) {
    }

    public record LineSave(
            @NotNull(message = "请选择子件") Long componentId,
            @NotNull(message = "请输入用量") @DecimalMin(value = "0", inclusive = false, message = "用量必须大于 0") BigDecimal qtyPer,
            @DecimalMin(value = "0", message = "损耗率为 0～100%") @DecimalMax(value = "1", message = "损耗率为 0～100%") BigDecimal scrapRate,
            @Size(max = 1024) String positionNo,
            IssueMethod issueMethod,
            Integer operationSeq,
            Boolean isKey,
            @Size(max = 256) String remark,
            @Valid List<SubstituteSave> substitutes) {
    }

    public record SubstituteSave(
            @NotNull(message = "请选择替代料") Long substituteId,
            Integer priority,
            @DecimalMin(value = "0", inclusive = false, message = "替代比例必须大于 0") BigDecimal ratio,
            @Size(max = 128) String remark) {
    }

    /** 保存结果：warnings 为不阻止保存的提示（R05 位号个数） */
    public record SaveResult(Long id, List<String> warnings) {
    }

    /** 新建时的父件信息：下一版本号 */
    public record NextVersion(Long materialId, int version) {
    }

    /**
     * 多级展开行。
     *
     * @param qtyPer      单层用量（每 1 个直接上级）
     * @param totalQtyPer 累计用量（每 1 个顶层父件）
     * @param requiredQty 需求量（父件数量 × 累计用量，逐层按单位精度向上取整）
     */
    public record ExplodeRow(String key, int level, String path, Long parentId, Long componentId, String code, String name, String spec,
                             MaterialType materialType, SourceType sourceType, String uom, BigDecimal qtyPer, BigDecimal scrapRate,
                             BigDecimal totalQtyPer, BigDecimal requiredQty, IssueMethod issueMethod, boolean phantom,
                             Long bomId, Integer bomVersion, BigDecimal unitCost, BigDecimal costAmount, boolean costMissing,
                             List<ExplodeRow> children) {
    }

    /** 反查行：直接或间接使用该物料的 BOM；top 为顶层（没有再被其他 BOM 使用） */
    public record WhereUsedRow(String key, int level, Long bomId, String docNo, Long materialId, String materialCode, String materialName,
                               String materialSpec, MaterialType materialType, int version, boolean isDefault, String status,
                               BigDecimal qtyPer, String uom, boolean top, List<WhereUsedRow> children) {
    }

    /** 版本比较行：change 为 ADDED / REMOVED / CHANGED / SAME；changedFields 为变化的字段 */
    public record CompareRow(Long componentId, String code, String name, String spec, String uom, String change, List<String> changedFields,
                             Side left, Side right) {
    }

    public record Side(BigDecimal qtyPer, BigDecimal scrapRate, String positionNo, IssueMethod issueMethod, boolean isKey, int substituteCount) {
    }

    public record CompareResult(Long leftId, String leftNo, Long rightId, String rightNo, List<CompareRow> rows) {
    }

    /** 成本卷算：total 为末级材料成本合计 */
    public record CostResult(BigDecimal total, int missingCount, List<ExplodeRow> rows) {
    }

    public record ReasonReq(String reason) {
    }
}
