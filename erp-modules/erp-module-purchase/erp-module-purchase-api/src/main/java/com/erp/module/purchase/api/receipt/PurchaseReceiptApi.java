package com.erp.module.purchase.api.receipt;

import java.math.BigDecimal;

/** 到货（品质 IQC 调用）。 */
public interface PurchaseReceiptApi {

    /**
     * IQC 判定结果回写到货行（需求 07-06 第 4 节）：合格（含特采）、不合格数量、检验单号；
     * 同步回写采购订单行（委外单）的合格数量。同一到货行重复回写时以最后一次为准（幂等）。
     *
     * @param qualifiedQty  合格数量（基本单位，不含特采）
     * @param concessionQty 特采数量
     * @param rejectedQty   不合格数量
     */
    void applyInspection(Long receiptLineId, String inspectionNo, BigDecimal qualifiedQty, BigDecimal concessionQty, BigDecimal rejectedQty);
}
