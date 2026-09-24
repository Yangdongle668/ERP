package com.erp.module.system.service.file;

import java.io.IOException;
import java.io.InputStream;

/** 附件存储后端（需求 01-12 1.1：本地磁盘 / S3 兼容对象存储，按 erp.file.storage 选择）。 */
public interface FileStorage {

    /** 存储类型标识，写入 sys_file.storage */
    String type();

    /** 保存内容到 path（相对路径） */
    void put(String path, InputStream content) throws IOException;

    InputStream open(String path) throws IOException;

    /** 删除；不存在时忽略 */
    void delete(String path) throws IOException;
}
