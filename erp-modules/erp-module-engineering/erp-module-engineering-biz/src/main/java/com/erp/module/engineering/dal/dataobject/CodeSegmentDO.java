package com.erp.module.engineering.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 物料编码段（需求 05-01 第 8 节）：末级类别下按顺序排列，生成编码时依次拼接所选特征值 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("eng_code_segment")
public class CodeSegmentDO extends BaseDO {

    private Long categoryId;
    private String name;
    private Integer segLength;
    private Integer sort;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String remark;
}
