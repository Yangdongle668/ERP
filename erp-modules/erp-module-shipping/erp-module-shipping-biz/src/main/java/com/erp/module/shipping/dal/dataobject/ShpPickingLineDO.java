package com.erp.module.shipping.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/** 拣货明细（一行通知可对应多个批次 / 库位）（shp_picking_line） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("shp_picking_line")
public class ShpPickingLineDO extends BaseDO {

    private Long pickingId;
    private Integer lineNo;
    private Long noticeLineId;
    private Long materialId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long locationId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String batchNo;
    private BigDecimal suggestedQty;
    private BigDecimal pickedQty;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String serialNos;
    /** 推荐时库存不足 */
    private Boolean shortage;
}
