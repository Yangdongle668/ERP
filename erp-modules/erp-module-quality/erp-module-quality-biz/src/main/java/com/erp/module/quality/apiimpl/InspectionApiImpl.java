package com.erp.module.quality.apiimpl;

import com.erp.module.quality.api.inspection.InspectType;
import com.erp.module.quality.api.inspection.InspectionApi;
import com.erp.module.quality.service.inspection.InspectionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class InspectionApiImpl implements InspectionApi {

    private final InspectionService inspectionService;

    public InspectionApiImpl(InspectionService inspectionService) {
        this.inspectionService = inspectionService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<Long> requestOqc(OqcRequest request) {
        List<Long> ids = new ArrayList<>();
        for (OqcRequest.Line l : request.lines()) {
            if (l.qty() == null || l.qty().signum() <= 0) continue;
            ids.add(inspectionService.create(new InspectionService.CreateCmd(InspectType.OQC, null, l.materialId(), l.batchNo(), l.qty(), null,
                    request.customerId(), InspectionService.SRC_NOTICE, request.noticeId(), l.noticeLineId(), request.noticeNo(), InspectionService.SRC_NOTICE,
                    request.noticeId(), l.noticeLineId(), request.noticeNo(), null, null, null, request.warehouseId(), null)));
        }
        return ids;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelOqc(Long noticeId) {
        inspectionService.cancelBySource(InspectionService.SRC_NOTICE, noticeId, "出货通知取消 / 退回");
    }
}
