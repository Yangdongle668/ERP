package com.erp.module.system.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 单据操作日志（只增不改的技术表，永久保留） */
@Data
@TableName("sys_doc_log")
public class DocLogDO {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String bizType;
    private Long bizId;
    private String bizNo;
    private String action;
    private String actionName;
    private String fromStatus;
    private String toStatus;
    private String reason;
    private Long operatorId;
    private String operatorName;
    private LocalDateTime createdAt;
}
