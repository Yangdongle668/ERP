package com.erp.module.production.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/** 领料单明细（mfg_issue_line） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mfg_issue_line")
public class MfgIssueLineDO extends BaseDO {

    private Long issueId;
    private Integer lineNo;
    private Long materialLineId;
    private Long materialId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long warehouseId;
    private BigDecimal requestQty;
    private BigDecimal issuedQty;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String remark;
}
