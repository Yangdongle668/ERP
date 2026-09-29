package com.erp.module.shipping.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/** 装箱：箱内明细（shp_carton_line） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("shp_carton_line")
public class ShpCartonLineDO extends BaseDO {

    private Long cartonId;
    private Long noticeId;
    private Long noticeLineId;
    private Long materialId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String batchNo;
    /** 基本单位 */
    private BigDecimal qty;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String serialNos;
}
