package com.erp.module.inventory.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.common.enums.EnableStatus;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 库位 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("inv_location")
public class LocationDO extends BaseDO {

    private Long warehouseId;
    private String code;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String name;
    private EnableStatus status;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String remark;
}
