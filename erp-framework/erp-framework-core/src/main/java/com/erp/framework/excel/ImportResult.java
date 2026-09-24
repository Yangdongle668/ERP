package com.erp.framework.excel;

import java.util.List;

/** 导入执行结果：成功条数、失败条数与失败原因 */
public record ImportResult(int success, int failed, List<Error> errors) {

    public record Error(int rowNo, String message) {
    }
}
