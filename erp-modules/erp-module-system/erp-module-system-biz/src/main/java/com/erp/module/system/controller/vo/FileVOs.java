package com.erp.module.system.controller.vo;

import java.time.LocalDateTime;

/** 附件接口 VO */
public final class FileVOs {

    private FileVOs() {
    }

    public record FileResp(Long id, String bizType, Long bizId, String category, String fileName, long fileSize, String contentType,
                           Long createdBy, String createdByName, LocalDateTime createdAt) {
    }
}
