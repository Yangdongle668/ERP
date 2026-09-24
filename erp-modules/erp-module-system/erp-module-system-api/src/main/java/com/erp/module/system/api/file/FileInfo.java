package com.erp.module.system.api.file;

import java.time.LocalDateTime;

/**
 * 附件信息（需求 01-系统管理/12 第 1 节）。
 *
 * @param category 业务自定义分类（如 DRAWING 图纸、CERT 证书），空为普通附件
 * @param size     字节数
 */
public record FileInfo(Long id, String bizType, Long bizId, String category, String fileName, String ext,
                       String contentType, long size, Long createdBy, LocalDateTime createdAt) {
}
