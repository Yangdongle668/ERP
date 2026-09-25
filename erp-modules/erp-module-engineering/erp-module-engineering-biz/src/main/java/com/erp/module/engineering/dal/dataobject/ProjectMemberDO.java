package com.erp.module.engineering.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 项目成员 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("eng_project_member")
public class ProjectMemberDO extends BaseDO {

    private Long projectId;
    private Long userId;
    /** 角色 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String memberRole;
}
