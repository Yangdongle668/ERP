package com.erp.module.production.api.finish;

import java.math.BigDecimal;

/**
 * 完工入库 FQC 回写（MFG-FN-R04）：品质模块对来源为完工入库申请（入库单来源类型 MFG_FINISH，来源 ID = 申请 ID）的检验判定后调用。
 * 数量为本次判定的增量（基本单位）；判定撤销时传负数。
 */
public interface ProductionFinishApi {

    String SOURCE_TYPE = "MFG_FINISH";

    void onFqcJudged(Long finishId, BigDecimal qualifiedQty, BigDecimal rejectedQty);
}
