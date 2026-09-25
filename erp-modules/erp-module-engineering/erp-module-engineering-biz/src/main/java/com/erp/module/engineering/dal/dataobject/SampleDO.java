package com.erp.module.engineering.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDocDO;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;
import java.time.LocalDate;

/** 样品单 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("eng_sample")
public class SampleDO extends BaseDocDO {

    /** CUSTOMER/ENGINEERING/CERTIFICATION */
    private String sampleType;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long customerId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long contactId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long projectId;
    private Long materialId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String customerPartNo;
    private BigDecimal qty;
    /** 要求寄出日期 */
    private LocalDate requiredDate;
    /** PRODUCE/FROM_STOCK */
    private String makeMethod;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long prodOrderId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String prodOrderNo;
    private String purpose;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String requirements;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDate shipDate;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String courier;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String trackingNo;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String shipAddress;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long stockOutId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String stockOutNo;
    /** 出库单已确认 */
    private Boolean stockOutDone;
    /** APPROVED/CONDITIONAL/REJECTED */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String feedbackResult;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDate feedbackDate;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String feedbackContent;
    /** DRAFT/PENDING/APPROVED/MAKING/READY/SHIPPED/FEEDBACK/CLOSED/VOIDED */
    private String sampleStatus;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String closeReason;
}
