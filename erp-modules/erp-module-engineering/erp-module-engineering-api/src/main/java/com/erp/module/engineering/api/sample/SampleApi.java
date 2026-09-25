package com.erp.module.engineering.api.sample;

/** 样品单回调（ENG-SMP-R03）：样品生产订单完工入库后，生产模块调用，样品单变为“待寄出” */
public interface SampleApi {

    void onProductionCompleted(Long sampleId);
}
