package com.erp.module.system.dal.dataobject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 打印记录（只增的技术表） */
@Data
@TableName("sys_print_log")
public class PrintLogDO {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String bizType;
    private Long bizId;
    private Long templateId;
    private Long printedBy;
    private LocalDateTime printedAt;
}
