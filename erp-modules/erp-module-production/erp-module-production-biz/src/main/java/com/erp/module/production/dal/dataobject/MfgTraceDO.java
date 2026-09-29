package com.erp.module.production.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/** 生产追溯关系（mfg_trace） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mfg_trace")
public class MfgTraceDO extends BaseDO {

    private Long prodOrderId;
    private Long productMaterialId;
    private String productBatchNo;
    private Long componentMaterialId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String componentBatchNo;
    /** 领料为正，退料为负 */
    private BigDecimal qty;
    private String sourceDocType;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long sourceDocId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String sourceDocNo;
}
