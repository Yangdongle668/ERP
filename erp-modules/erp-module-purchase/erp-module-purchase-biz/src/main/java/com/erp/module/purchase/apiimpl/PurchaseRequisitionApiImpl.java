package com.erp.module.purchase.apiimpl;

import com.erp.module.purchase.api.requisition.MrpPurchaseSuggestion;
import com.erp.module.purchase.api.requisition.PurchaseRequisitionApi;
import com.erp.module.purchase.service.requisition.RequisitionService;
import org.springframework.stereotype.Service;

import java.util.List;

/** 采购申请对外接口（PMC 调用） */
@Service
public class PurchaseRequisitionApiImpl implements PurchaseRequisitionApi {

    private final RequisitionService requisitionService;

    public PurchaseRequisitionApiImpl(RequisitionService requisitionService) {
        this.requisitionService = requisitionService;
    }

    @Override
    public List<Long> createFromMrp(List<MrpPurchaseSuggestion> suggestions) {
        return requisitionService.createFromMrp(suggestions);
    }
}
