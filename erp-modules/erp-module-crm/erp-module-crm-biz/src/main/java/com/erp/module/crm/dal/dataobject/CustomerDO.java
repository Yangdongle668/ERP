package com.erp.module.crm.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import com.erp.module.crm.api.customer.CustomerStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;
import java.time.LocalDate;

/** 客户 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "crm_customer", autoResultMap = true)
public class CustomerDO extends BaseDO {

    private String code;
    private String name;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String nameEn;
    private String shortName;
    /** 名称比较键：小写、去空格和标点（R04） */
    private String nameKey;
    /** 查重键：再去掉公司后缀（R01） */
    private String nameCore;
    /** 字典 crm_customer_type */
    private String customerType;
    /** 字典 crm_customer_level */
    private String customerLevel;
    /** PROSPECT/PENDING/ACTIVE/DISABLED/BLACKLIST */
    private CustomerStatus customerStatus;
    /** 加入黑名单前的状态，移出时恢复 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private CustomerStatus statusBeforeBlacklist;
    private Boolean isForeign;
    private String country;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String province;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String city;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String address;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String industry;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String source;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String website;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String websiteDomain;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String phone;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String email;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String taxNo;
    /** 负责业务员 */
    private Long ownerId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long orgId;
    /** 负责部门（数据权限） */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long deptId;
    private String currency;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long paymentTermId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String tradeTerm;
    private BigDecimal salesTaxRate;
    /** 信用额度（本位币），只能通过信用调整修改 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal creditLimit;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer creditDays;
    /** DEFAULT/NONE/WARN/BLOCK */
    private String creditControl;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String blacklistReason;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDate firstOrderDate;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDate lastOrderDate;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String remark;
}
