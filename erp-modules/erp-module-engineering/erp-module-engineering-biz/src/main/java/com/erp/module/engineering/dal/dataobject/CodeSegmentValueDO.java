package com.erp.module.engineering.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.common.enums.EnableStatus;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 编码段特征值，如线材型号 02 = UL3302 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("eng_code_segment_value")
public class CodeSegmentValueDO extends BaseDO {

    private Long segmentId;
    private String valueCode;
    private String valueName;
    private Integer sort;
    private EnableStatus status;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String remark;
}
