package com.erp.module.purchase.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDocDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 调价单（表 pur_price_adjust） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "pur_price_adjust", autoResultMap = true)
public class PriceAdjustDO extends BaseDocDO {

    private Long supplierId;

    private String currency;

    private String adjustReason;

    /** MANUAL/RFQ/IMPORT */
    private String adjustSource;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long rfqId;
}
