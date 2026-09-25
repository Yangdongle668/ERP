package com.erp.module.sales.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDocDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 客户询价 RFQ（sal_rfq） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "sal_rfq", autoResultMap = true)
public class SalRfqDO extends BaseDocDO {

    private Long customerId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long contactId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long opportunityId;
    private String currency;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String tradeTerm;
    private LocalDate replyDueDate;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long engineerId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long costEngineerId;
    private String rfqStatus;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String closeReason;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime remindedAt;
}
