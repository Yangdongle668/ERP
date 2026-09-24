package com.erp.module.inventory.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 序列号 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("inv_serial")
public class SerialDO extends BaseDO {

    private Long materialId;
    private String serialNo;
    private String batchNo;
    private String serialStatus;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long warehouseId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long locationId;
    private Long lastTxnId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long customerId;
}
