package com.erp.module.crm.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 客户信用占用（由各模块回写/定时重算） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_customer_credit")
public class CustomerCreditDO extends BaseDO {

    private Long customerId;
    private BigDecimal receivableBalance;
    private BigDecimal overdueAmount;
    private BigDecimal openOrderAmount;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime refreshedAt;
}
