package com.erp.module.system.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.common.enums.EnableStatus;
import com.erp.framework.mybatis.BaseDO;
import com.erp.module.system.enums.OrgType;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_org")
public class OrgDO extends BaseDO {

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long parentId;
    private String code;
    private String name;
    private String shortName;
    private OrgType orgType;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long leaderUserId;
    private String phone;
    private String address;
    private String nameEn;
    private String addressEn;
    private String taxNo;
    private Long logoFileId;
    /** 祖先路径（含自身），如 /100/105/ */
    private String path;
    @TableField("org_level")
    private Integer level;
    private Integer sort;
    private EnableStatus status;
    private String remark;
}
