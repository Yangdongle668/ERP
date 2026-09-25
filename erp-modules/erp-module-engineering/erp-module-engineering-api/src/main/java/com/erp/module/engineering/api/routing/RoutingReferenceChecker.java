package com.erp.module.engineering.api.routing;

/**
 * 工艺路线版本、工作中心是否被业务单据使用：生产模块实现。
 * 被生产订单使用的工艺路线不能反审核（ENG-RTG-R05），被使用的工作中心不能删除。
 */
public interface RoutingReferenceChecker {

    boolean isRoutingUsed(Long routingId);

    boolean isWorkCenterUsed(Long workCenterId);
}
