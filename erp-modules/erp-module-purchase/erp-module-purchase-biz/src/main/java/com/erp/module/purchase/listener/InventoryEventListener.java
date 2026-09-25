package com.erp.module.purchase.listener;

import com.erp.module.inventory.api.doc.StockDocEvent;
import com.erp.module.inventory.api.doc.StockInConfirmedEvent;
import com.erp.module.inventory.api.doc.StockInType;
import com.erp.module.inventory.api.doc.StockOutConfirmedEvent;
import com.erp.module.inventory.api.doc.StockOutType;
import com.erp.module.purchase.config.PurchaseModuleConfig;
import com.erp.module.purchase.service.outsourcing.OutsourcingService;
import com.erp.module.purchase.service.receipt.ReceiptService;
import com.erp.module.purchase.service.receipt.ReturnService;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * 监听仓库单据事件（同一事务内，需求 07-资材 README 第 11 节）：
 * <ul>
 *   <li>采购入库、委外入库确认 / 反确认 / 退回 → 到货单；</li>
 *   <li>采购退货出库确认 / 反确认 / 退回 → 采购退货单；</li>
 *   <li>委外发料出库、委外退料入库确认 / 反确认 → 委外单已发、退回数量。</li>
 * </ul>
 */
@Component
public class InventoryEventListener {

    private final ReceiptService receiptService;
    private final ReturnService returnService;
    private final OutsourcingService outsourcingService;

    public InventoryEventListener(ReceiptService receiptService, ReturnService returnService, OutsourcingService outsourcingService) {
        this.receiptService = receiptService;
        this.returnService = returnService;
        this.outsourcingService = outsourcingService;
    }

    @EventListener
    public void onStockIn(StockInConfirmedEvent e) {
        String type = e.getSource() == null ? null : e.getSource().sourceType();
        if (PurchaseModuleConfig.RECEIPT.equals(type)) {
            receiptService.onStockInConfirmed(e);
        } else if (OutsourcingService.RETURN_SOURCE.equals(type) && e.getInType() == StockInType.OUTSOURCE_RETURN) {
            outsourcingService.recordTxn(OutsourcingService.RETURN, e.getStockInId(), e.getStockInNo(), sum(e.getLines().stream()
                    .map(l -> Map.entry(l.sourceLineId(), l.baseQty())).toList()));
        }
    }

    @EventListener
    public void onStockOut(StockOutConfirmedEvent e) {
        String type = e.getSource() == null ? null : e.getSource().sourceType();
        if (PurchaseModuleConfig.RETURN.equals(type) && e.getOutType() == StockOutType.PURCHASE_RETURN) {
            returnService.onStockOutConfirmed(e);
        } else if (PurchaseModuleConfig.OUTSOURCING.equals(type) && e.getOutType() == StockOutType.OUTSOURCE_ISSUE) {
            outsourcingService.recordTxn(OutsourcingService.ISSUE, e.getStockOutId(), e.getStockOutNo(), sum(e.getLines().stream()
                    .map(l -> Map.entry(l.sourceLineId(), l.baseQty())).toList()));
        }
    }

    @EventListener
    public void onStockDoc(StockDocEvent e) {
        String type = e.getSource() == null ? null : e.getSource().sourceType();
        if (type == null) return;
        if (PurchaseModuleConfig.RECEIPT.equals(type)) {
            switch (e.getKind()) {
                case IN_REVERSING -> receiptService.onStockInReversing(e);
                case IN_REVERSED -> receiptService.onStockInReversed(e);
                case REJECTED -> receiptService.onStockInRejected(e);
                default -> {
                }
            }
        } else if (PurchaseModuleConfig.RETURN.equals(type)) {
            switch (e.getKind()) {
                case OUT_REVERSING -> returnService.onStockOutReversing(e);
                case OUT_REVERSED -> returnService.onStockOutReversed(e);
                case REJECTED -> returnService.onStockOutRejected(e);
                default -> {
                }
            }
        } else if ((PurchaseModuleConfig.OUTSOURCING.equals(type) && e.getKind() == StockDocEvent.Kind.OUT_REVERSED)
                || (OutsourcingService.RETURN_SOURCE.equals(type) && e.getKind() == StockDocEvent.Kind.IN_REVERSED)) {
            outsourcingService.reverseTxn(e.getDocId());
        }
    }

    private static Map<Long, BigDecimal> sum(java.util.List<Map.Entry<Long, BigDecimal>> lines) {
        Map<Long, BigDecimal> map = new HashMap<>();
        for (Map.Entry<Long, BigDecimal> l : lines) {
            if (l.getKey() != null) map.merge(l.getKey(), l.getValue(), BigDecimal::add);
        }
        return map;
    }
}
