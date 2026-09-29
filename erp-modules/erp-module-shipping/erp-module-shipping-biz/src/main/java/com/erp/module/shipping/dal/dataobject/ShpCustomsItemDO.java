package com.erp.module.shipping.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/** 报关商品（按 HS 编码合并）（shp_customs_item） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("shp_customs_item")
public class ShpCustomsItemDO extends BaseDO {

    private Long customsId;
    private Integer seq;
    private String hsCode;
    private String declareName;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String declareElements;
    private BigDecimal qty;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String uom;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal secondQty;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String secondUom;
    private BigDecimal unitPrice;
    private BigDecimal amount;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String origin;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal netWeight;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal grossWeight;
}
