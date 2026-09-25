package com.erp.module.sales.api.returns;

import java.math.BigDecimal;

/** 销售退货（品质模块回写判定结果）。 */
public interface SalesReturnApi {

    /**
     * 退货检验判定回写（覆盖该行的判定数量，基本单位）；全部行判定完且收货完成时退货单完成（SAL-SR-R05）。
     *
     * @throws com.erp.common.exception.BizException 判定合计超过已收货数量
     */
    void recordJudgement(Long returnLineId, BigDecimal goodQty, BigDecimal reworkQty, BigDecimal scrapQty);
}
