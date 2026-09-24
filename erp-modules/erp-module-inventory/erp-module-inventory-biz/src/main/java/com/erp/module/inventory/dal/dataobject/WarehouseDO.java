package com.erp.module.inventory.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.common.enums.EnableStatus;
import com.erp.framework.mybatis.BaseDO;
import com.erp.module.inventory.api.warehouse.WarehouseType;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 仓库 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("inv_warehouse")
public class WarehouseDO extends BaseDO {

    private String code;
    private String name;
    private WarehouseType warehouseType;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long orgId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long managerId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String address;
    private Boolean locationEnabled;
    private Boolean allowNegative;
    private Boolean isDefault;
    private EnableStatus status;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String remark;
}
