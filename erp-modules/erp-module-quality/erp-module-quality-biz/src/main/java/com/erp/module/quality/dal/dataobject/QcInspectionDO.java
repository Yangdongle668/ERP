package com.erp.module.quality.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDocDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 检验单（qc_inspection） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("qc_inspection")
public class QcInspectionDO extends BaseDocDO {

    /** IQC/IPQC/FQC/OQC/RETURN/RECHECK */
    private String inspectType;
    /** FIRST/PATROL/LAST/REPORT */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String ipqcKind;
    private Long materialId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String batchNo;
    /** 送检批量（基本单位） */
    private BigDecimal lotQty;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long supplierId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long customerId;
    /** 来源单据行（入库单来源行、出货通知行） */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long sourceLineId;
    /** 上游业务单据类型：PUR_RECEIPT / MFG_FINISH / SAL_RETURN / SHP_NOTICE / MFG_REPORT / INV_TRANSFER */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String upstreamType;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long upstreamId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long upstreamLineId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String upstreamNo;
    /** IPQC */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long prodOrderId;
    /** IPQC 工序号 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer operationSeq;
    /** 被检物所在仓库 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long warehouseId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long standardId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String standardCode;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer standardVersion;
    /** 抽样方案与计算结果 JSON */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String samplingSnapshot;
    private Integer sampleQty;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long inspectorId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime startedAt;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime inspectedAt;
    /** PASS/FAIL */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String suggestedResult;
    /** QUALIFIED/REJECTED/CONCESSION/SORTED */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String result;
    private BigDecimal qualifiedQty;
    private BigDecimal concessionQty;
    private BigDecimal rejectedQty;
    private Integer crCount;
    private Integer maCount;
    private Integer miCount;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long judgeBy;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime judgeAt;
    /** 让步判定合格理由等 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String judgeReason;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long ncrId;
    /** PENDING/INSPECTING/WAIT_MRB/JUDGED/HANDLED/CANCELED */
    private String inspStatus;
    /** MRB 处置含挑选：只能按挑选判定 */
    private Boolean mrbSort;
    /** MRB 已定特采数量 */
    private BigDecimal presetConcessionQty;
    /** MRB 已定不合格数量 */
    private BigDecimal presetRejectedQty;
    /** 检验调拨单 ID */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String transferIds;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String confirmedTransferIds;
    private Integer rejudgeCount;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String rejudgeReason;
    private Boolean overdueNotified;
}
