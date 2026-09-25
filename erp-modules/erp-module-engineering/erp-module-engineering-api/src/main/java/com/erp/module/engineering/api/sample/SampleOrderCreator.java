package com.erp.module.engineering.api.sample;

/**
 * 样品生产订单扩展点：生产模块实现，创建“样品”类型的生产订单并返回订单 ID 与单号。
 * 生产订单完工入库后调用 {@link SampleApi#onProductionCompleted(Long)}。
 * 未实现时样品单只能选择“从库存领取”。
 */
public interface SampleOrderCreator {

    Ref create(SampleOrderRequest request);

    record Ref(Long orderId, String orderNo) {
    }
}
