package com.erp.module.engineering.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 工装适用物料 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("eng_tooling_material")
public class ToolingMaterialDO extends BaseDO {

    private Long toolingId;
    private Long materialId;
}
