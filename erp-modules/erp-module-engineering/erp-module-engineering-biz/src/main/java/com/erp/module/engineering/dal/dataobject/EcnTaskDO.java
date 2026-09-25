package com.erp.module.engineering.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;

/** ECN 执行确认 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("eng_ecn_task")
public class EcnTaskDO extends BaseDO {

    private Long ecnId;
    /** PURCHASE/WAREHOUSE/PRODUCTION/QUALITY/PMC/CERT */
    private String deptRole;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long assigneeId;
    private String content;
    /** PENDING/DONE */
    private String taskStatus;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String doneRemark;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long doneBy;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime doneAt;
}
