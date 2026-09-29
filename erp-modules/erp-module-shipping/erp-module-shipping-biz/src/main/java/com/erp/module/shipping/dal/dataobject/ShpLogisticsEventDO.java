package com.erp.module.shipping.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** 物流记录（shp_logistics_event） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("shp_logistics_event")
public class ShpLogisticsEventDO extends BaseDO {

    private Long shipmentId;
    private String logisticsStatus;
    private LocalDateTime occurredAt;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String location;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String remark;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long operatorId;
}
