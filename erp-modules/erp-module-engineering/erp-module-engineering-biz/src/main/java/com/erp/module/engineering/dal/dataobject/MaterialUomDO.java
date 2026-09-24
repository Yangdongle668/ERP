package com.erp.module.engineering.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/** 物料单位换算：1 uom = rate 基本单位 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("eng_material_uom")
public class MaterialUomDO extends BaseDO {

    private Long materialId;
    private String uom;
    private BigDecimal rate;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String remark;
}
