package com.erp.module.system.api.file;

import java.util.Collection;
import java.util.List;

/**
 * 附件（需求 01-系统管理/12 第 1 节）。
 *
 * <p>新建单据时前端先上传（未绑定），保存单据时业务模块在同一事务中调用 {@link #bind} 绑定；
 * 超过 24 小时未绑定的文件由定时任务清理。
 */
public interface FileApi {

    /** 把上传后未绑定的文件绑定到单据；只能绑定当前用户上传的未绑定文件，已绑定到同一单据的忽略。 */
    void bind(Collection<Long> fileIds, String bizType, Long bizId);

    /** 单据的全部附件（按上传时间）。 */
    List<FileInfo> list(String bizType, Long bizId);

    /** 单据删除时一并删除附件（逻辑删除，物理文件 30 天后清理）。 */
    void deleteByBiz(String bizType, Long bizId);

    /**
     * 保存系统生成的文件（导出结果、导入错误报告等），直接绑定到 bizType + bizId，返回文件 ID。
     * 不做扩展名与大小限制。
     */
    Long saveGenerated(String bizType, Long bizId, String fileName, String contentType, byte[] content);
}
