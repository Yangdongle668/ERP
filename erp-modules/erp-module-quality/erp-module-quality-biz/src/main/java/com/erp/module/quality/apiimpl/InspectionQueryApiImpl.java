package com.erp.module.quality.apiimpl;

import com.erp.module.quality.api.inspection.InspectionDTO;
import com.erp.module.quality.api.inspection.InspectionQueryApi;
import com.erp.module.quality.service.inspection.InspectionQueryService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class InspectionQueryApiImpl implements InspectionQueryApi {

    private final InspectionQueryService queryService;

    public InspectionQueryApiImpl(InspectionQueryService queryService) {
        this.queryService = queryService;
    }

    @Override
    public List<InspectionDTO> getByBiz(String upstreamType, Long upstreamId) {
        return queryService.getByBiz(upstreamType, upstreamId);
    }

    @Override
    public boolean isOqcPassed(Long noticeId) {
        return queryService.isOqcPassed(noticeId);
    }

    @Override
    public void checkFirstArticle(Long prodOrderId) {
        queryService.checkFirstArticle(prodOrderId);
    }
}
