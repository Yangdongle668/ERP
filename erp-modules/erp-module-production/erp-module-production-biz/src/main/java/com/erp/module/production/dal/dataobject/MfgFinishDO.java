package com.erp.module.production.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDocDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/** 完工入库申请（mfg_finish） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "mfg_finish", autoResultMap = true)
public class MfgFinishDO extends BaseDocDO {

    private Long prodOrderId;
    private Long materialId;
    private BigDecimal qty;
    private String batchNo;
    private Boolean fqcRequired;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long warehouseId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String serialNos;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String stockInIds;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String stockInNos;
    private BigDecimal stockedQty;
    private BigDecimal qualifiedQty;
    private BigDecimal rejectedQty;
    /** SUBMITTED/STOCKED/JUDGED/CANCELED */
    private String finishStatus;
}
