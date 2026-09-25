package com.erp.module.purchase.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDocDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 采购申请（表 pur_requisition） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "pur_requisition", autoResultMap = true)
public class RequisitionDO extends BaseDocDO {

    private String requisitionType;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long requestDeptId;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long mrpRunId;

    private Boolean urgent;
}
