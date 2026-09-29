package com.erp.module.shipping.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 报关资料（shp_customs） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("shp_customs")
public class ShpCustomsDO extends BaseDO {

    private Long shipmentId;
    /** 编码规则 SHP_CUSTOMS */
    private String docCode;
    /** 报关单号（报关后回填） */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String customsNo;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDate declareDate;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String tradeMode;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String declarePort;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String destinationCountry;
    private String currency;
    private BigDecimal totalAmount;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String remark;
    private Boolean invalid;
}
