package com.erp.module.workbench.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** 公告阅读记录（wb_notice_read） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wb_notice_read")
public class WbNoticeReadDO extends BaseDO {

    private Long noticeId;
    private Long userId;
    private LocalDateTime readAt;
}
