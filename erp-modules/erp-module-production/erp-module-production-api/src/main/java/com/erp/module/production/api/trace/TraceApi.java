package com.erp.module.production.api.trace;

import java.util.List;

/** 生产追溯（品质、BI）。最多展开 10 层（MFG-TRC-R01） */
public interface TraceApi {

    /** 正向：原材料批次 → 用在哪些生产订单 / 产品批次（多级向上） */
    List<TraceNode> forward(Long materialId, String batchNo);

    /** 反向：产品批次 → 用了哪些物料批次（半成品批次继续向下展开） */
    List<TraceNode> backward(Long materialId, String batchNo);
}
