package com.erp.module.production.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/** 生产订单用料（mfg_prod_order_material） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mfg_prod_order_material")
public class MfgProdOrderMaterialDO extends BaseDO {

    private Long prodOrderId;
    private Integer lineNo;
    private Long componentId;
    /** 单位用量（已除以 BOM 基数，不含损耗） */
    private BigDecimal qtyPer;
    private BigDecimal scrapRate;
    private BigDecimal requiredQty;
    /** PICK/BACKFLUSH */
    private String issueMethod;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer operationSeq;
    /** 已领（含超领、倒冲） */
    private BigDecimal issuedQty;
    private BigDecimal overIssuedQty;
    /** 已退（良品 + 不良） */
    private BigDecimal returnedQty;
    private BigDecimal returnedGoodQty;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long substituteOfId;
    private Boolean isAdded;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String remark;
}
