package com.erp.module.sales.api.cost;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;

/**
 * 单位标准成本扩展点（报价、订单毛利与底价，成本核算的材料单价）：由研发工程（物料标准成本）或财务（成本卷算）实现。
 * 未实现或某物料没有成本时，销售按“最新采购价”兜底，仍没有则视为无成本（按参数 sal.price.no-cost-policy 处理）。
 */
public interface SalesCostProvider {

    /** @return 物料 ID → 单位成本（本位币、每基本单位、不含税）；没有成本的物料不返回 */
    Map<Long, BigDecimal> unitCosts(Collection<Long> materialIds);
}
