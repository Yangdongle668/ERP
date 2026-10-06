package com.erp.module.engineering.api.routing;

import java.util.List;
import java.util.Optional;

/** 工艺路线查询：没有默认工艺路线的物料，生产订单按“单工序”处理 */
public interface RoutingApi {

    Optional<RoutingDTO> getDefaultRouting(Long materialId);

    Optional<RoutingDTO> getRouting(Long routingId);

    /** 物料的全部已审核版本：默认版本在前，其余按版本号倒序（生产订单选择工艺路线版本） */
    List<RoutingDTO> listApprovedVersions(Long materialId);
}
