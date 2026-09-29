package com.erp.module.pmc.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDocDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** 出货计划（pmc_shipping_plan） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "pmc_shipping_plan", autoResultMap = true)
public class PmcShippingPlanDO extends BaseDocDO {

    private String planWeek;
    /** DRAFT/PUBLISHED/CLOSED */
    private String planStatus;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime publishedAt;
}
