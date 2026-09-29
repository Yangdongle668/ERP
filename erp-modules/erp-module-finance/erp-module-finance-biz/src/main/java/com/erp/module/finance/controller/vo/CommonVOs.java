package com.erp.module.finance.controller.vo;

import java.util.List;

/** 财务通用请求 */
public final class CommonVOs {

    private CommonVOs() {
    }

    public record ReasonReq(String reason) {
    }

    public record IdsReq(List<Long> ids) {
    }

    /** 批量操作结果：失败的单据及原因 */
    public record BatchResult(int success, List<String> errors) {
    }
}
