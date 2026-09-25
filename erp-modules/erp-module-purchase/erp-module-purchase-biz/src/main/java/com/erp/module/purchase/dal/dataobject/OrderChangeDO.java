package com.erp.module.purchase.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDocDO;
import java.math.BigDecimal;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 采购订单变更单（表 pur_order_change） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "pur_order_change", autoResultMap = true)
public class OrderChangeDO extends BaseDocDO {

    private Long orderId;

    private String changeReason;

    private Integer newVersion;

    private BigDecimal amountChangeBase;

    /** 变更前订单快照（json） */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String snapshot;
}
