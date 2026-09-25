package com.erp.module.purchase.apiimpl;

import com.erp.module.purchase.api.receipt.PurchaseReceiptApi;
import com.erp.module.purchase.service.receipt.ReceiptService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/** 到货对外接口（品质 IQC 回写判定结果） */
@Service
public class PurchaseReceiptApiImpl implements PurchaseReceiptApi {

    private final ReceiptService receiptService;

    public PurchaseReceiptApiImpl(ReceiptService receiptService) {
        this.receiptService = receiptService;
    }

    @Override
    public void applyInspection(Long receiptLineId, String inspectionNo, BigDecimal qualifiedQty, BigDecimal concessionQty, BigDecimal rejectedQty) {
        receiptService.applyInspection(receiptLineId, inspectionNo, qualifiedQty, concessionQty, rejectedQty);
    }
}
