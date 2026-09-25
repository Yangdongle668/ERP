package com.erp.module.sales.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDocDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/** 销售订单变更单（sal_order_change） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "sal_order_change", autoResultMap = true)
public class SalOrderChangeDO extends BaseDocDO {

    private Long orderId;
    private Integer orderVersionFrom;
    private String changeReason;
    private String reasonRemark;
    private BigDecimal amountBefore;
    private BigDecimal amountAfter;
    private BigDecimal amountChangeBase;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String headerChanges;
}
