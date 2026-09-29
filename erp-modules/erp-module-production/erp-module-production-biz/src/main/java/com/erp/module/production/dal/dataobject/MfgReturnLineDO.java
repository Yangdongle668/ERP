package com.erp.module.production.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/** 退料单明细（mfg_return_line） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mfg_return_line")
public class MfgReturnLineDO extends BaseDO {

    private Long returnId;
    private Integer lineNo;
    private Long materialLineId;
    private Long materialId;
    private BigDecimal qty;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String batchNo;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String defectDesc;
    private BigDecimal receivedQty;
}
