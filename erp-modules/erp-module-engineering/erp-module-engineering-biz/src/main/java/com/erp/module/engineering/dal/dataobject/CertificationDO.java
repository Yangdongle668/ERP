package com.erp.module.engineering.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDate;

/** 认证证书 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("eng_certification")
public class CertificationDO extends BaseDO {

    /** 字典 eng_cert_type */
    private String certType;
    private String certNo;
    private String name;
    private String issuingBody;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String holder;
    private LocalDate issueDate;
    /** 为空表示长期有效 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDate expireDate;
    /** ISO 代码逗号分隔 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String countries;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String scope;
    /** VALID/REVOKED */
    private String certStatus;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String revokeReason;
    /** 已提醒的阈值（天） */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String remindedDays;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String remark;
}
