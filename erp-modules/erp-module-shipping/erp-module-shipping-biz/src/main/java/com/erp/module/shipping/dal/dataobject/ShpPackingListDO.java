package com.erp.module.shipping.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/** Packing List（shp_packing_list） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("shp_packing_list")
public class ShpPackingListDO extends BaseDO {

    private Long shipmentId;
    private String plNo;
    private LocalDate plDate;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String consignee;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String notifyParty;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String shippingMarks;
    /** 行快照 JSON */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String lineData;
    /** 合计 JSON */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String totalData;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String remark;
    /** 出货单反确认 / 作废后失效 */
    private Boolean invalid;
}
