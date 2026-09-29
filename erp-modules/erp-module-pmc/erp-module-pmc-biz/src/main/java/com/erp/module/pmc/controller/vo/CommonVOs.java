package com.erp.module.pmc.controller.vo;

import java.util.List;

/** PMC 通用请求 / 返回 */
public final class CommonVOs {

    private CommonVOs() {
    }

    public record ReasonReq(String reason) {
    }

    public record IdsReq(List<Long> ids) {
    }

    /** 批量操作结果：成功数与逐条失败原因 */
    public record BatchResult(int success, List<String> errors, List<String> docNos) {
    }
}
