package com.erp.module.finance.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 会计科目（fin_account） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("fin_account")
public class FinAccountDO extends BaseDO {

    private String code;
    private String name;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String parentCode;
    /** ASSET/LIABILITY/EQUITY/COST/PROFIT_LOSS */
    private String accountType;
    /** DEBIT/CREDIT */
    private String direction;
    /** 辅助核算：CUSTOMER、SUPPLIER、DEPT、MATERIAL、PROJECT，逗号分隔 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String auxTypes;
    /** 外币核算 */
    private Boolean currencyAccounting;
    /** 末级科目 */
    private Boolean isLeaf;
    private Integer accountLevel;
    /** ENABLED/DISABLED */
    private String accountStatus;
}
