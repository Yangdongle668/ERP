package com.erp.module.production.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDocDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 退料单（mfg_return） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "mfg_return", autoResultMap = true)
public class MfgReturnDO extends BaseDocDO {

    private Long prodOrderId;
    /** GOOD/DEFECT */
    private String returnType;
    private Long warehouseId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String stockInIds;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String stockInNos;
}
