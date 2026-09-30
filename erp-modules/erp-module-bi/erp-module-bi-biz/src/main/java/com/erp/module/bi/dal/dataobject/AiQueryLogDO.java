package com.erp.module.bi.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** AI 问答日志（ai_query_log） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_query_log")
public class AiQueryLogDO extends BaseDO {

    private Long userId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long messageId;
    private String question;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String toolCalls;
    private Integer resultRows;
    private Long latencyMs;
    private Integer tokens;
    private Boolean success;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String error;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String feedback;
}
