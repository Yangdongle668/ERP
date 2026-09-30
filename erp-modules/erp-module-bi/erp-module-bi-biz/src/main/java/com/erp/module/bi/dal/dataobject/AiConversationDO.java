package com.erp.module.bi.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** AI 会话（ai_conversation） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_conversation")
public class AiConversationDO extends BaseDO {

    private Long userId;
    private String title;
}
