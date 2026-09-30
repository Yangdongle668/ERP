package com.erp.module.bi.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** AI 消息（ai_message） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_message")
public class AiMessageDO extends BaseDO {

    private Long conversationId;
    /** USER/ASSISTANT/TOOL */
    private String role;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String content;
    /** 工具调用与结果（json） */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String toolCall;
    /** 返回给用户的数据表（json，真实数值） */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String resultJson;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer tokens;
    /** UP/DOWN */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String feedback;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String feedbackRemark;
}
