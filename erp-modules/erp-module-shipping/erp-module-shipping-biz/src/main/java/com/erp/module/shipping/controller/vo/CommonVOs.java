package com.erp.module.shipping.controller.vo;

import java.util.List;

/** 出货通用请求 */
public final class CommonVOs {

    private CommonVOs() {
    }

    public record ReasonReq(String reason) {
    }

    public record IdsReq(List<Long> ids) {
    }
}
