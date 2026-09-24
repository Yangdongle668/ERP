package com.erp.module.system.dal.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import com.erp.framework.mybatis.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 附件（sys_file） */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_file")
public class FileDO extends BaseDO {

    public static final String STORAGE_LOCAL = "LOCAL";

    private String bizType;
    private Long bizId;
    private String category;
    private String fileName;
    private String ext;
    private String contentType;
    private Long fileSize;
    private String sha256;
    private String storage;
    private String path;
    private Boolean bound;
}
