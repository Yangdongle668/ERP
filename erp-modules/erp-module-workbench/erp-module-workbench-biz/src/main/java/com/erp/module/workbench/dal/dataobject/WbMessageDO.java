package com.erp.module.workbench.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** 站内消息（wb_message） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wb_message")
public class WbMessageDO extends BaseDO {

    private Long userId;
    private String msgType;
    private String title;
    private String content;
    private String route;
    private Boolean readFlag;
    private LocalDateTime readAt;
    private Boolean emailSent;
}
