package com.erp.module.purchase.apiimpl;

import com.erp.module.purchase.api.receipt.PurchaseReturnApi;
import com.erp.module.purchase.service.receipt.ReturnService;
import org.springframework.stereotype.Service;

@Service
public class PurchaseReturnApiImpl implements PurchaseReturnApi {

    private final ReturnService returnService;

    public PurchaseReturnApiImpl(ReturnService returnService) {
        this.returnService = returnService;
    }

    @Override
    public DraftResult createDraft(DraftRequest request) {
        return returnService.createDraft(request);
    }
}
