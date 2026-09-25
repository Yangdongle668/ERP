package com.erp.module.sales.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 订单版本快照（sal_order_snapshot） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "sal_order_snapshot", autoResultMap = true)
public class SalOrderSnapshotDO extends BaseDO {

    private Long orderId;
    private Integer orderVersion;
    private String content;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long changeId;
}
