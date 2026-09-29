package com.erp.module.production.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDocDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 领料单（mfg_issue） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "mfg_issue", autoResultMap = true)
public class MfgIssueDO extends BaseDocDO {

    /** NORMAL/OVER/BACKFLUSH */
    private String issueType;
    private Long prodOrderId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long warehouseId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal kitQty;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String overReason;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String overRemark;
    /** 倒冲来源报工单 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long reportId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String stockOutIds;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String stockOutNos;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime submittedAt;
}
