package com.erp.module.bi.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 指标说明（计算逻辑在代码中注册）（bi_metric） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("bi_metric")
public class BiMetricDO extends BaseDO {

    private String code;
    /** 展示名称（为空用代码注册名称） */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String displayName;
    /** 补充说明 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String description;
    /** 负责人 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String ownerName;
}
