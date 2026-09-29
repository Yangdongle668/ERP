package com.erp.module.quality.controller.vo;

import java.util.List;

/** 品质通用请求 */
public final class CommonVOs {

    private CommonVOs() {
    }

    public record ReasonReq(String reason) {
    }

    public record IdsReq(List<Long> ids) {
    }

    public record TextReq(String text) {
    }
}
