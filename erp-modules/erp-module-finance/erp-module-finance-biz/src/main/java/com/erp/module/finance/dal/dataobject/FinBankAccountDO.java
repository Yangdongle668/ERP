package com.erp.module.finance.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 本公司银行账户（fin_bank_account） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("fin_bank_account")
public class FinBankAccountDO extends BaseDO {

    private String code;
    /** 如 中行美元户 */
    private String name;
    private String bankName;
    private String accountNo;
    private String currency;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String swift;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String bankAddress;
    /** 对应会计科目 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String accountCode;
    /** 同币别默认 */
    private Boolean isDefault;
    /** ENABLED/DISABLED */
    private String bankStatus;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String remark;
}
