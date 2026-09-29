package com.erp.module.shipping.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 货代（shp_forwarder） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("shp_forwarder")
public class ShpForwarderDO extends BaseDO {

    private String code;
    private String name;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String contact;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String phone;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String email;
    /** 运输方式，逗号分隔 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String services;
    /** ENABLED/DISABLED */
    private String forwarderStatus;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String remark;
}
