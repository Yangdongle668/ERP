package com.erp.module.sales.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 订单变更明细（sal_order_change_line） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "sal_order_change_line", autoResultMap = true)
public class SalOrderChangeLineDO extends BaseDO {

    private Long changeId;
    private Integer lineNo;
    private String changeType;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long orderLineId;
    private Long materialId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String uom;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal oldQty;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal newQty;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal oldPrice;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal newPrice;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDate oldRequiredDate;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDate newRequiredDate;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String newCustomerPartNo;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String newDescription;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String remark;
}
