package com.erp.module.purchase.apiimpl;

import com.erp.module.purchase.api.outsourcing.MrpOutsourceSuggestion;
import com.erp.module.purchase.api.outsourcing.OutsourcingApi;
import com.erp.module.purchase.service.outsourcing.OutsourcingService;
import org.springframework.stereotype.Service;

import java.util.List;

/** 委外对外接口（PMC 调用） */
@Service
public class OutsourcingApiImpl implements OutsourcingApi {

    private final OutsourcingService outsourcingService;

    public OutsourcingApiImpl(OutsourcingService outsourcingService) {
        this.outsourcingService = outsourcingService;
    }

    @Override
    public List<Long> createFromMrp(List<MrpOutsourceSuggestion> suggestions) {
        return outsourcingService.createFromMrp(suggestions);
    }
}
