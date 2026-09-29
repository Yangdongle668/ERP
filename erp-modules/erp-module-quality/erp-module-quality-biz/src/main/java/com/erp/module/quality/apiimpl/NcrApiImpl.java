package com.erp.module.quality.apiimpl;

import com.erp.module.production.api.defect.DefectNcrCreator;
import com.erp.module.quality.api.ncr.NcrApi;
import com.erp.module.quality.service.ncr.NcrService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/** NCR 对外：NcrApi；生产不良“生成 NCR”扩展点 DefectNcrCreator（MFG 06 3.1） */
@Service
public class NcrApiImpl implements NcrApi, DefectNcrCreator {

    private final NcrService ncrService;

    public NcrApiImpl(NcrService ncrService) {
        this.ncrService = ncrService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createNcr(NcrCreateRequest r) {
        return ncrService.createExternal(r.source(), r.sourceNo(), r.materialId(), r.batchNo(), r.supplierId(), null, r.qty(), r.description(),
                StringUtils.hasText(r.defectCode()) ? List.of(r.defectCode()) : List.of(), null, r.supplierId() != null ? "SUPPLIER" : "PROCESS",
                r.fileIds()).getDocNo();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String create(NcrRequest r) {
        String desc = "生产不良：订单 " + r.prodOrderNo() + " 工序 " + r.operationSeq() + (StringUtils.hasText(r.description()) ? "，" + r.description() : "");
        return ncrService.createExternal("PRODUCTION", r.prodOrderNo(), r.materialId(), r.batchNo(), null, null, r.qty(), desc,
                StringUtils.hasText(r.defectCode()) ? List.of(r.defectCode()) : List.of(), null, "PROCESS", r.fileIds()).getDocNo();
    }
}
