package com.erp.common.result;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** 分页查询参数基类，查询 VO 继承本类。 */
public class PageParam {

    public static final int MAX_PAGE_SIZE = 500;

    @Min(value = 1, message = "页码最小为 1")
    private int pageNo = 1;

    @Min(value = 1, message = "每页条数最小为 1")
    @Max(value = MAX_PAGE_SIZE, message = "每页条数最大为 " + MAX_PAGE_SIZE)
    private int pageSize = 20;

    public int getPageNo() {
        return pageNo;
    }

    public void setPageNo(int pageNo) {
        this.pageNo = pageNo;
    }

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }
}
