package com.erp.module.engineering.api.routing;

import java.util.Optional;

/** 工艺路线查询：没有默认工艺路线的物料，生产订单按“单工序”处理 */
public interface RoutingApi {

    Optional<RoutingDTO> getDefaultRouting(Long materialId);

    Optional<RoutingDTO> getRouting(Long routingId);
}
