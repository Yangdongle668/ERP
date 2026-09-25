package com.erp.module.sales.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/** 价格表明细（阶梯）（sal_price_list_item） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "sal_price_list_item", autoResultMap = true)
public class SalPriceListItemDO extends BaseDO {

    private Long priceListId;
    private Integer lineNo;
    private Long materialId;
    private String uom;
    private BigDecimal minQty;
    private BigDecimal price;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String remark;
}
