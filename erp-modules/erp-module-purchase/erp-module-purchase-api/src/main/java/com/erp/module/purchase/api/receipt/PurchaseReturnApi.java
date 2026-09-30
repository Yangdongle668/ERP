package com.erp.module.purchase.api.receipt;

import java.math.BigDecimal;

/** 采购退货（品质 NCR 处置“退供应商”生成退货草稿） */
public interface PurchaseReturnApi {

    /**
     * @param receiptLineId 来源到货行（IQC 不合格时已知）；为空时按供应商 + 物料 + 批次找最近一条仍可退货的到货行
     * @param reason        退货原因（字典 pur_return_reason），如 IQC_REJECT、STOCK_DEFECT
     * @param qty           退货数量（基本单位），超过可退数量时按可退数量
     */
    record DraftRequest(Long supplierId, Long receiptLineId, Long materialId, String batchNo, BigDecimal qty, String reason, String ncrNo, String remark) {
    }

    record DraftResult(Long returnId, String docNo, BigDecimal qty) {
    }

    /** 生成草稿退货单（出库仓取该批次所在的不良品仓）；找不到可退的到货记录或不良品仓时抛出业务异常 */
    DraftResult createDraft(DraftRequest request);
}
