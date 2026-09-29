package com.erp.module.production.controller.vo;

import java.time.LocalDate;
import java.util.List;

/** 生产通用返回与请求 */
public final class CommonVOs {

    private CommonVOs() {
    }

    /** 动作结果：新状态 + 提示（不阻止的警告） */
    public record DocResult(String status, List<String> warnings) {

        public static DocResult of(String status) {
            return new DocResult(status, List.of());
        }
    }

    /** 保存结果：单据 ID + 提示 */
    public record SaveResult(Long id, List<String> warnings) {
    }

    public record ReasonReq(String reason) {
    }

    public record IdsReq(List<Long> ids) {
    }

    /** 关联单据（前端 RelatedDocs） */
    public record RelatedDoc(String direction, String docTypeName, String docNo, LocalDate docDate, String status, String statusLabel, String route) {
    }

    /** 批量操作结果：成功数与失败原因 */
    public record BatchResult(int success, List<String> errors) {
    }
}
