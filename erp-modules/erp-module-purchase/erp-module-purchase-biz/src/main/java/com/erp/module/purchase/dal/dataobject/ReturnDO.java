package com.erp.module.purchase.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDocDO;
import java.math.BigDecimal;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 采购退货单（表 pur_return） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "pur_return", autoResultMap = true)
public class ReturnDO extends BaseDocDO {

    private Long supplierId;

    /** 字典 pur_return_reason */
    private String returnReason;

    /** REFUND/REPLACE */
    private String handling;

    private Long warehouseId;

    private String currency;

    private BigDecimal exchangeRate;

    private BigDecimal totalAmount;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long stockOutId;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String stockOutNo;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String ncrNo;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String voidReason;
}
