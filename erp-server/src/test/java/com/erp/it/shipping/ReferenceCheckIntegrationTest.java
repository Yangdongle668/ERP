package com.erp.it.shipping;

import com.erp.module.crm.api.customer.CustomerReferenceChecker;
import com.erp.module.engineering.api.ecn.EcnImpact;
import com.erp.module.engineering.api.ecn.EcnImpactProvider;
import com.erp.module.engineering.api.material.MaterialReferenceChecker;
import com.erp.module.engineering.api.material.MaterialUsage;
import com.erp.module.system.api.currency.CurrencyReferenceChecker;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 跨模块引用检查扩展点：销售订单反审核（SAL-SO-R08）、本位币修改（SYS-CUR-R03）、组织删除（SYS-ORG-R07）、
 * 客户删除（CRM R09）、物料引用（ENG-MAT-R08 / R09）。
 */
class ReferenceCheckIntegrationTest extends ShippingTestSupport {

    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    List<CustomerReferenceChecker> customerCheckers;
    @Autowired
    List<MaterialReferenceChecker> materialCheckers;
    @Autowired
    List<CurrencyReferenceChecker> currencyCheckers;

    /** 由订单生成的生产订单（草稿即算）阻止订单反审核；出货通知引用客户与物料 */
    @Test
    void productionOrderBlocksOrderUnapprove() throws Exception {
        String m = fg("引用检查成品", Map.of());
        String raw = material("引用检查原料", CAT_RAW, "RAW", Map.of());
        String bom = ok(doPost("/api/engineering/boms", admin, Map.of("materialId", m, "baseQty", 1,
                "lines", List.of(Map.of("componentId", raw, "qtyPer", 1, "issueMethod", "PICK"))))).at("/id").asText();
        ok(doPost("/api/engineering/boms/" + bom + "/submit", admin, null));
        String c = customer("引用检查客户", false);
        String o = approvedOrder(c, List.of(orderLine(m, "10", "100", null)));
        Map<String, Object> mo = new java.util.HashMap<>();
        mo.put("materialId", m);
        mo.put("qty", "10");
        mo.put("planStart", java.time.LocalDate.now().toString());
        mo.put("planEnd", java.time.LocalDate.now().plusDays(7).toString());
        mo.put("salesOrderLineId", orderLineId(o, 0));
        String prod = ok(doPost("/api/production/prod-orders", admin, mo)).at("/id").asText();
        // 订单仍为已审核（没有通知 / 出货 / 收款），生产模块的引用检查阻止反审核
        assertThat(ok(doGet("/api/sales/orders/" + o, admin)).at("/status").asText()).isEqualTo("APPROVED");
        assertError(doPost("/api/sales/orders/" + o + "/unapprove", admin, Map.of("reason", "改单")),
                "订单已有出货通知/收款/生产订单，不能反审核，请使用订单变更");
        // 删除草稿生产订单后可以反审核
        ok(doDelete("/api/production/prod-orders/" + prod, admin));
        ok(doPost("/api/sales/orders/" + o + "/unapprove", admin, Map.of("reason", "改单")));
        assertThat(ok(doGet("/api/sales/orders/" + o, admin)).at("/status").asText()).isEqualTo("DRAFT");

        // 出货通知引用客户与物料
        String o2 = approvedOrder(c, List.of(orderLine(m, "5", "100", null)));
        notice(c, orderLineId(o2, 0), "5");
        Long customerId = Long.valueOf(c);
        assertThat(customerCheckers.stream().anyMatch(ch -> ch.hasBusinessData(customerId))).isTrue();
        MaterialUsage usage = materialCheckers.stream().map(ch -> ch.usage(Long.valueOf(m))).reduce(MaterialUsage.NONE, MaterialUsage::plus);
        assertThat(usage.used()).isTrue();
        assertThat(usage.openDocCount()).isGreaterThanOrEqualTo(2); // 未完成的销售订单 + 出货通知
    }

    /** 已有销售订单等金额数据时不能修改本位币 */
    @Test
    void baseCurrencyLockedWhenAmountDataExists() throws Exception {
        String c = customer("本位币检查客户", false);
        approvedOrder(c, List.of(orderLine(fg("本位币检查成品", Map.of()), "1", "10", null)));
        assertThat(currencyCheckers).isNotEmpty();
        assertThat(currencyCheckers.stream().anyMatch(CurrencyReferenceChecker::hasAmountData)).isTrue();
        String usd = null;
        for (JsonNode cur : ok(doGet("/api/system/currencies", admin))) {
            if ("USD".equals(cur.at("/code").asText())) usd = cur.at("/id").asText();
        }
        assertThat(usd).isNotNull();
        assertError(doPost("/api/system/currencies/" + usd + "/set-base", admin, null), "系统中已有业务数据，不能修改本位币");
    }

    /** 被客户（负责部门）引用的部门不能删除；没有引用的部门可以删除 */
    @Test
    void referencedDeptCannotBeDeleted() throws Exception {
        String used = ok(doPost("/api/system/orgs", admin, Map.of("parentId", "100", "orgType", "DEPT", "code", "RD" + uniq(), "name", "引用部门" + uniq(),
                "sort", 10))).asText();
        String free = ok(doPost("/api/system/orgs", admin, Map.of("parentId", "100", "orgType", "DEPT", "code", "FD" + uniq(), "name", "空闲部门" + uniq(),
                "sort", 10))).asText();
        String c = customer("部门引用客户", false);
        jdbc.update("UPDATE crm_customer SET dept_id = ? WHERE id = ?", Long.valueOf(used), Long.valueOf(c));
        assertError(doDelete("/api/system/orgs/" + used, admin), "该组织已被业务数据使用，只能停用");
        ok(doDelete("/api/system/orgs/" + free, admin));
    }

    @Autowired
    List<EcnImpactProvider> ecnImpactProviders;

    /** ECN 影响分析：被变更父件的未完成销售订单（销售模块实现 EcnImpactProvider） */
    @Test
    void ecnImpactIncludesOpenSalesOrders() throws Exception {
        String m = fg("ECN 影响成品", Map.of());
        String o = approvedOrder(customer("ECN 影响客户", false), List.of(orderLine(m, "30", "10", null)));
        String no = ok(doGet("/api/sales/orders/" + o, admin)).at("/docNo").asText();
        List<EcnImpact> impacts = ecnImpactProviders.stream().flatMap(p -> p.impacts(List.of(), List.of(Long.valueOf(m))).stream())
                .filter(i -> "SALES".equals(i.impactType())).toList();
        assertThat(impacts).hasSize(1);
        assertThat(impacts.get(0).docNo()).startsWith(no);
        assertThat(impacts.get(0).qty()).isEqualByComparingTo("30");
    }
}
