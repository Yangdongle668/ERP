package com.erp.module.pmc.service;

import com.erp.module.pmc.api.query.PmcQueryApi;
import com.erp.module.pmc.api.query.ShortageDTO;
import com.erp.module.pmc.service.alert.DeliveryAlertService;
import com.erp.module.pmc.service.shortage.ShortageService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** PMC 查询对外实现（生产、销售） */
@Service("pmcQueryApiImpl")
public class PmcQueryApiImpl implements PmcQueryApi {

    private final ShortageService shortageService;
    private final DeliveryAlertService alertService;

    public PmcQueryApiImpl(ShortageService shortageService, DeliveryAlertService alertService) {
        this.shortageService = shortageService;
        this.alertService = alertService;
    }

    @Override
    public List<ShortageDTO> getShortage(Long prodOrderId) {
        return shortageService.shortageOf(prodOrderId);
    }

    @Override
    public Optional<LocalDate> getEstimatedDate(Long orderLineId) {
        return alertService.estimatedDate(orderLineId);
    }
}
