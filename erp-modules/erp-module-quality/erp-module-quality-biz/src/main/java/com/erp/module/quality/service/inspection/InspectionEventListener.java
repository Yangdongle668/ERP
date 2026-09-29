package com.erp.module.quality.service.inspection;

import com.erp.module.inventory.api.batch.BatchApi;
import com.erp.module.inventory.api.batch.BatchDTO;
import com.erp.module.inventory.api.doc.StockDocEvent;
import com.erp.module.inventory.api.doc.StockInConfirmedEvent;
import com.erp.module.inventory.api.doc.StockInType;
import com.erp.module.inventory.api.doc.TransferConfirmedEvent;
import com.erp.module.inventory.api.doc.TransferType;
import com.erp.module.production.api.order.OpenOrderDTO;
import com.erp.module.production.api.order.ProductionQueryApi;
import com.erp.module.production.api.report.IpqcTriggerEvent;
import com.erp.module.quality.api.inspection.InspectType;
import com.erp.module.quality.config.QualityModuleConfig;
import com.erp.module.system.api.workflow.ApprovalCompletedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 检验单触发（README 第 2 节）：入库确认 → IQC / FQC / 退货检验；报工触发 → IPQC；复检送检调拨 → 复检；
 * 入库单反确认 → 阻止或取消；检验调拨确认 → 已处理；重判审批结果。
 */
@Component
public class InspectionEventListener {

    private final InspectionService inspectionService;
    private final BatchApi batchApi;
    private final ProductionQueryApi productionQueryApi;

    public InspectionEventListener(InspectionService inspectionService, BatchApi batchApi, ProductionQueryApi productionQueryApi) {
        this.inspectionService = inspectionService;
        this.batchApi = batchApi;
        this.productionQueryApi = productionQueryApi;
    }

    @EventListener
    public void onStockIn(StockInConfirmedEvent e) {
        InspectType type = typeOf(e.getInType(), e.getWarehouseType());
        if (type == null) return;
        for (StockInConfirmedEvent.Line l : e.getLines()) {
            if (l.baseQty() == null || l.baseQty().signum() <= 0) continue;
            Long supplier = e.getSupplierId();
            if (supplier == null && type == InspectType.IQC && StringUtils.hasText(l.batchNo())) {
                supplier = batchApi.get(l.materialId(), l.batchNo()).map(BatchDTO::supplierId).orElse(null);
            }
            inspectionService.create(new InspectionService.CreateCmd(type, null, l.materialId(), l.batchNo(), l.baseQty(), type == InspectType.IQC ? supplier : null,
                    type == InspectType.RETURN ? e.getCustomerId() : null, InspectionService.SRC_STOCK_IN, e.getStockInId(), l.sourceLineId(), e.getStockInNo(),
                    e.getSource() == null ? null : e.getSource().sourceType(), e.getSource() == null ? null : e.getSource().sourceId(), l.sourceLineId(),
                    e.getSource() == null ? null : e.getSource().sourceNo(), null, null, null, e.getWarehouseId(), null));
        }
    }

    /** 采购 / 委外入库进待检仓 → IQC；生产入库进待检仓 → FQC；销售退货入退货仓（或待检仓）→ 退货检验 */
    static InspectType typeOf(StockInType inType, String warehouseType) {
        if (inType == null) return null;
        return switch (inType) {
            case PURCHASE_IN, OUTSOURCE_IN -> "QC".equals(warehouseType) ? InspectType.IQC : null;
            case PRODUCTION_IN -> "QC".equals(warehouseType) ? InspectType.FQC : null;
            case SALES_RETURN -> "RTN".equals(warehouseType) || "QC".equals(warehouseType) ? InspectType.RETURN : null;
            default -> null;
        };
    }

    @EventListener
    public void onStockDoc(StockDocEvent e) {
        if (!"STOCK_IN".equals(e.getDocType())) return;
        switch (e.getKind()) {
            case IN_REVERSING -> inspectionService.checkSourceReversible(InspectionService.SRC_STOCK_IN, e.getDocId());
            case IN_REVERSED -> inspectionService.cancelBySource(InspectionService.SRC_STOCK_IN, e.getDocId(), "入库单 " + e.getDocNo() + " 反确认");
            default -> {
            }
        }
    }

    @EventListener
    public void onIpqc(IpqcTriggerEvent e) {
        if (e.getQty() == null || e.getQty().signum() <= 0) return;
        String operation = null;
        for (OpenOrderDTO o : productionQueryApi.getOpenOrders(List.of(e.getMaterialId()))) {
            if (!o.id().equals(e.getProdOrderId()) || o.operations() == null) continue;
            operation = o.operations().stream().filter(op -> op.seq() == e.getOperationSeq()).map(OpenOrderDTO.Operation::operation).findFirst().orElse(null);
        }
        inspectionService.create(new InspectionService.CreateCmd(InspectType.IPQC, "REPORT", e.getMaterialId(), e.getBatchNo(), e.getQty(), null, null,
                InspectionService.SRC_REPORT, e.getReportId(), null, e.getReportNo(), InspectionService.SRC_REPORT, e.getReportId(), null, e.getProdOrderNo(),
                e.getProdOrderId(), e.getOperationSeq(), operation, null, null));
    }

    @EventListener
    public void onTransfer(TransferConfirmedEvent e) {
        if (e.getTransferType() == TransferType.INSPECTION && e.getInspectionId() != null) {
            inspectionService.onTransferConfirmed(e.getInspectionId(), e.getTransferId());
        } else if (e.getTransferType() == TransferType.RECHECK) {
            int i = 0;
            for (TransferConfirmedEvent.Line l : e.getLines()) {
                i++;
                Long supplier = StringUtils.hasText(l.batchNo()) ? batchApi.get(l.materialId(), l.batchNo()).map(BatchDTO::supplierId).orElse(null) : null;
                inspectionService.create(new InspectionService.CreateCmd(InspectType.RECHECK, null, l.materialId(), l.batchNo(), l.qty(), supplier, null,
                        InspectionService.SRC_TRANSFER, e.getTransferId(), (long) i, e.getTransferNo(), InspectionService.SRC_TRANSFER, e.getTransferId(),
                        null, e.getTransferNo(), null, null, null, e.getToWarehouseId(), null));
            }
        }
    }

    @EventListener
    public void onApproval(ApprovalCompletedEvent e) {
        if (!QualityModuleConfig.REJUDGE.equals(e.getBizType())) return;
        if (e.getResult() == ApprovalCompletedEvent.Result.APPROVED) inspectionService.doRejudge(e.getBizId());
    }
}
