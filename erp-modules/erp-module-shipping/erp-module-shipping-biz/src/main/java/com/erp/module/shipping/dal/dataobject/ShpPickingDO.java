package com.erp.module.shipping.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDocDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** 拣货单（shp_picking） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("shp_picking")
public class ShpPickingDO extends BaseDocDO {

    private Long noticeId;
    private Long warehouseId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long pickerId;
    /** WAITING/PICKING/DONE/CANCELED */
    private String pickingStatus;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime startedAt;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime completedAt;
}
