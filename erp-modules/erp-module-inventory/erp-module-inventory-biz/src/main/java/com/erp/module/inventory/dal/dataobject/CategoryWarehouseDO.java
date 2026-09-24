package com.erp.module.inventory.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 物料类别默认仓 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("inv_category_warehouse")
public class CategoryWarehouseDO extends BaseDO {

    private Long categoryId;
    private Long warehouseId;
}
