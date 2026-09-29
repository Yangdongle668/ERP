package com.erp.module.production.service.trace;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.module.production.dal.dataobject.MfgProdOrderDO;
import com.erp.module.production.dal.dataobject.MfgTraceDO;
import com.erp.module.production.dal.mapper.MfgTraceMapper;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/** 追溯关系写入（09-07 第 2 节）：领料 / 倒冲出库确认为正，退料入库确认为负；仓库反确认时写入反向记录（只增不改） */
@Component("mfgTraceRecorder")
public class TraceRecorder {

    public static final String STOCK_OUT = "STOCK_OUT";
    public static final String STOCK_IN = "STOCK_IN";

    private final MfgTraceMapper mapper;

    public TraceRecorder(MfgTraceMapper mapper) {
        this.mapper = mapper;
    }

    public void record(MfgProdOrderDO o, Long componentId, String batchNo, BigDecimal qty, String docType, Long docId, String docNo) {
        MfgTraceDO t = new MfgTraceDO();
        t.setProdOrderId(o.getId());
        t.setProductMaterialId(o.getMaterialId());
        t.setProductBatchNo(o.getBatchNo());
        t.setComponentMaterialId(componentId);
        t.setComponentBatchNo(batchNo == null || batchNo.isBlank() ? null : batchNo);
        t.setQty(qty);
        t.setSourceDocType(docType);
        t.setSourceDocId(docId);
        t.setSourceDocNo(docNo);
        mapper.insert(t);
    }

    /** 仓库单据反确认：为该单据此前写入的记录追加反向记录 */
    public void reverse(String docType, Long docId) {
        for (MfgTraceDO t : mapper.selectList(new LambdaQueryWrapper<MfgTraceDO>().eq(MfgTraceDO::getSourceDocType, docType)
                .eq(MfgTraceDO::getSourceDocId, docId))) {
            MfgTraceDO r = new MfgTraceDO();
            r.setProdOrderId(t.getProdOrderId());
            r.setProductMaterialId(t.getProductMaterialId());
            r.setProductBatchNo(t.getProductBatchNo());
            r.setComponentMaterialId(t.getComponentMaterialId());
            r.setComponentBatchNo(t.getComponentBatchNo());
            r.setQty(t.getQty().negate());
            r.setSourceDocType(docType + "_REVERSE");
            r.setSourceDocId(docId);
            r.setSourceDocNo(t.getSourceDocNo());
            mapper.insert(r);
        }
    }
}
