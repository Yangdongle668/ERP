package com.erp.module.finance.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 科目映射（业务类型 → 分录模板）（fin_account_mapping） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("fin_account_mapping")
public class FinAccountMappingDO extends BaseDO {

    /** SALES_AR/SALES_RETURN_AR/RECEIPT/PURCHASE_AP/PAYMENT/… */
    private String bizType;
    /** 条件 JSON，为空表示默认 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String matchCondition;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String conditionDesc;
    private Integer priority;
    /** 分录模板 JSON */
    private String entries;
    /** ENABLED/DISABLED */
    private String mappingStatus;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String remark;
}
