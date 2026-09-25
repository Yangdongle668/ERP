package com.erp.module.purchase.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 订单变更明细（表 pur_order_change_line） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "pur_order_change_line")
public class OrderChangeLineDO extends BaseDO {

    private Long changeId;

    private Integer lineNo;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long orderLineId;

    /** ADD/MODIFY/CANCEL */
    private String changeType;

    private Long materialId;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String uom;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal oldQty;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal newQty;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal oldPrice;

    /** 不含税单价 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal newPrice;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal taxRate;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDate oldRequiredDate;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDate newRequiredDate;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String remark;
}
