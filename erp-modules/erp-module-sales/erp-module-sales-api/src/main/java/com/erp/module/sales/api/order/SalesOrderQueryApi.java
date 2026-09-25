package com.erp.module.sales.api.order;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 销售订单查询（出货、PMC、CRM 信用、财务使用），不受数据权限限制。 */
public interface SalesOrderQueryApi {

    /** 已审核 / 执行中订单中未出完（lineStatus = OPEN 且未出货数量 &gt; 0）的行，按交期、单号、行号排序 */
    List<SalesOrderLineDTO> getOpenLines(OpenLineFilter filter);

    Optional<SalesOrderLineDTO> getLine(Long lineId);

    Map<Long, SalesOrderLineDTO> getLines(Collection<Long> lineIds);

    /** 客户未出货订单金额（本位币含税）：已审核、执行中订单未关闭行的未出货数量 × 含税单价 × 汇率；补货订单不计 */
    BigDecimal getOpenAmountByCustomer(Long customerId);

    /** 订单“出货前”付款节点尚未收齐的金额（原币），没有该类节点时为 0（出货通知保存时提示，SAL-PP-R02） */
    BigDecimal getUnpaidBeforeShipment(Long orderId);
}
