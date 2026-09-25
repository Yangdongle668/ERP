package com.erp.module.purchase.controller.vo;

import java.util.List;

/** 资材通用返回与请求 */
public final class CommonVOs {

    private CommonVOs() {
    }

    /** 提交、审核等动作的结果：新状态 + 提示（不阻止的警告） */
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
    public record RelatedDoc(String direction, String docTypeName, String docNo, java.time.LocalDate docDate, String status, String statusLabel, String route) {
    }
}
