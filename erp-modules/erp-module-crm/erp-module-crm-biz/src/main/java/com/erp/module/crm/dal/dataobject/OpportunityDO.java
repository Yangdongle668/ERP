package com.erp.module.crm.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 商机 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_opportunity")
public class OpportunityDO extends BaseDO {

    private String code;
    private String name;
    private Long customerId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long contactId;
    /** CONTACT/REQUIREMENT/QUOTATION/NEGOTIATION/WON/LOST */
    private String stage;
    /** OPEN/WON/LOST/SHELVED */
    private String oppStatus;
    private BigDecimal amount;
    private String currency;
    private BigDecimal winRate;
    private LocalDate expectedDate;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String products;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String competitor;
    private Long ownerId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long orgId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long deptId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String lostReason;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String lostRemark;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String wonOrderNo;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime closedAt;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String remark;
}
