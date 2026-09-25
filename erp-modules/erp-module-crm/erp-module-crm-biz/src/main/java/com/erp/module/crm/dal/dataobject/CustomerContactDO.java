package com.erp.module.crm.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDate;

/** 客户联系人 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_contact")
public class CustomerContactDO extends BaseDO {

    private Long customerId;
    private String name;
    /** MALE/FEMALE/UNKNOWN */
    private String gender;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String title;
    /** 字典 crm_contact_role */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String contactRole;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String email;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String phone;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String mobile;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String im;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDate birthday;
    private Boolean isPrimary;
    /** ACTIVE/LEFT */
    private String contactStatus;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String remark;
}
