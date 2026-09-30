package com.erp.module.finance.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDocDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 记账凭证（fin_voucher） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("fin_voucher")
public class FinVoucherDO extends BaseDocDO {

    /** 凭证字 */
    private String voucherWord;
    /** 期间内序号 */
    private Integer voucherSeq;
    private String period;
    /** AUTO/MANUAL */
    private String voucherSource;
    /** 生成的业务类型（科目映射） */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String bizType;
    /** 生成来源单据 ID，逗号分隔 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String sourceIds;
    private Integer attachmentCount;
    private BigDecimal totalDebit;
    private BigDecimal totalCredit;
    /** DRAFT/AUDITED/POSTED */
    private String voucherStatus;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long auditorId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime auditedAt;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long posterId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime postedAt;
    /** 冲回的原凭证（外币重估下月冲回） */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long reversalOfId;
}
