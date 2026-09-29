package com.erp.module.quality.service.inspection;

import com.erp.common.exception.BizException;
import com.erp.module.inventory.api.batch.BatchApi;
import com.erp.module.inventory.api.batch.BatchDTO;
import com.erp.module.inventory.api.warehouse.WarehouseApi;
import com.erp.module.inventory.api.warehouse.WarehouseType;
import com.erp.module.production.api.order.OpenOrderDTO;
import com.erp.module.production.api.order.ProductionQueryApi;
import com.erp.module.quality.api.QualityErrorCodes;
import com.erp.module.quality.api.inspection.InspectType;
import com.erp.module.quality.controller.vo.InspectionVOs.ManualCreate;
import com.erp.module.quality.service.QcSupport;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/** 手工新建检验单：IPQC 首件 / 巡检 / 末件、复检 */
@Service
public class InspectionManualService {

    private final InspectionService inspectionService;
    private final ProductionQueryApi productionQueryApi;
    private final BatchApi batchApi;
    private final WarehouseApi warehouseApi;

    public InspectionManualService(InspectionService inspectionService, ProductionQueryApi productionQueryApi, BatchApi batchApi, WarehouseApi warehouseApi) {
        this.inspectionService = inspectionService;
        this.productionQueryApi = productionQueryApi;
        this.batchApi = batchApi;
        this.warehouseApi = warehouseApi;
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(ManualCreate req) {
        InspectType type = InspectType.valueOf(req.inspectType());
        if (type == InspectType.IPQC) {
            if (req.ipqcKind() == null || !List.of("FIRST", "PATROL", "LAST").contains(req.ipqcKind())) throw new BizException(QualityErrorCodes.INS_MANUAL_TYPE);
            OpenOrderDTO o = productionQueryApi.getOpenOrders(null).stream().filter(x -> x.id().equals(req.prodOrderId())).findFirst()
                    .orElseThrow(() -> new BizException(QualityErrorCodes.INS_PROD_ORDER));
            String operation = null;
            if (req.operationSeq() != null && o.operations() != null) {
                operation = o.operations().stream().filter(op -> op.seq() == req.operationSeq()).map(OpenOrderDTO.Operation::operation).findFirst().orElse(null);
            }
            return inspectionService.create(new InspectionService.CreateCmd(type, req.ipqcKind(), o.materialId(), QcSupport.trim(req.batchNo()), req.lotQty(),
                    null, null, InspectionService.SRC_MANUAL, null, null, null, "MFG_ORDER", o.id(), null, o.docNo(), o.id(), req.operationSeq(), operation,
                    null, req.remark()));
        }
        if (type == InspectType.RECHECK) {
            if (req.materialId() == null) throw BizException.of(QualityErrorCodes.REASON_REQUIRED, "物料");
            Long supplier = null;
            if (StringUtils.hasText(req.batchNo())) supplier = batchApi.get(req.materialId(), req.batchNo().trim()).map(BatchDTO::supplierId).orElse(null);
            Long wh = req.warehouseId() != null ? req.warehouseId()
                    : warehouseApi.listByType(WarehouseType.QC).stream().filter(w -> w.isDefault()).map(w -> w.id()).findFirst().orElse(null);
            return inspectionService.create(new InspectionService.CreateCmd(type, null, req.materialId(), QcSupport.trim(req.batchNo()), req.lotQty(), supplier,
                    null, InspectionService.SRC_MANUAL, null, null, null, null, null, null, null, null, null, null, wh, req.remark()));
        }
        throw new BizException(QualityErrorCodes.INS_MANUAL_TYPE);
    }
}
