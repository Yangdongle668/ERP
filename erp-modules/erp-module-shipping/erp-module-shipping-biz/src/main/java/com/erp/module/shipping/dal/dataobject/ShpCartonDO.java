package com.erp.module.shipping.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/** 装箱：箱（shp_carton） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("shp_carton")
public class ShpCartonDO extends BaseDO {

    private Long noticeId;
    private Integer cartonNo;
    /** 字典 shp_carton_spec */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String cartonSpec;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal lengthCm;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal widthCm;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal heightCm;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal grossWeightKg;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal netWeightKg;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal cbm;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String palletNo;
    /** 随哪张出货单出货 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long shipmentId;
}
