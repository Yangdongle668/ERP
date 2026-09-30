package com.erp.module.workbench.dal.dataobject;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** 公告（wb_notice） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wb_notice")
public class WbNoticeDO extends BaseDO {

    private String title;
    /** 富文本（已过滤） */
    private String content;
    /** ALL/DEPT */
    private String scope;
    /** 逗号分隔 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String deptIds;
    private Boolean important;
    private LocalDateTime publishAt;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime expireAt;
    /** DRAFT/PUBLISHED/WITHDRAWN */
    private String noticeStatus;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long publisherId;
}
