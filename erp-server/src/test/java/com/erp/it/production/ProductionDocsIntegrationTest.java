package com.erp.it.production;

import com.erp.module.production.api.order.MrpSuggestion;
import com.erp.module.production.api.order.ProductionOrderApi;
import com.erp.module.production.api.order.ProductionQueryApi;
import com.erp.module.production.api.order.WipDTO;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 生产单据的状态约束、派工、倒冲缺料、不良退料、批次追溯、MRP 转单与在制、直通率（需求 09-01～09-07） */
class ProductionDocsIntegrationTest extends ProductionTestSupport {

    @Autowired
    ProductionOrderApi orderApi;
    @Autowired
    ProductionQueryApi queryApi;

    /** 两道工序的产品，1 个原材料（领料），第 20 道为检验点 */
    private String[] product(String stockQty) throws Exception {
        String prod = fg("电源");
        String comp = raw("外壳");
        bom(prod, List.of(bomLine(comp, 1, 0, "PICK", 10)));
        String wc = workCenter();
        routing(prod, wc, new Object[]{10, "装配", true}, new Object[]{20, "测试", true, true});
        if (stockQty != null) stock(comp, W_RAW, stockQty, null);
        return new String[]{prod, comp, wc};
    }

    /** PO-T03 撤销下达、暂停 / 恢复；RPT-T05 反审核约束；IPQC 触发 */
    @Test
    void statusConstraints() throws Exception {
        String[] p = product("100");
        String id = releasedOrder(p[0], "10");
        ok(doPost("/api/production/prod-orders/" + id + "/unrelease", admin, null));
        assertThat(order(id).at("/prodStatus").asText()).isEqualTo("PLANNED");
        ok(doPost("/api/production/prod-orders/" + id + "/release", admin, Map.of()));
        for (String i : issueAll(id)) confirmIssue(i, null, null);
        assertError(doPost("/api/production/prod-orders/" + id + "/unrelease", admin, null), "当前状态【生产中】不能撤销下达");

        String r10 = reportOk(report(id, 10, 10, 0, 0));
        String r20 = reportOk(report(id, 20, 5, 0, 0));
        assertThat(ItProductionConfig.IPQC.stream().anyMatch(e -> e.getReportId().toString().equals(r20) && e.getQty().compareTo(new BigDecimal("5")) == 0))
                .isTrue();
        assertThat(ItProductionConfig.IPQC.stream().noneMatch(e -> e.getReportId().toString().equals(r10))).isTrue();
        assertError(doPost("/api/production/reports/" + r10 + "/unapprove", admin, null), "下道工序已报工，不能反审核");

        assertError(doPost("/api/production/prod-orders/" + id + "/suspend", admin, Map.of()), "请填写暂停原因");
        ok(doPost("/api/production/prod-orders/" + id + "/suspend", admin, Map.of("reason", "设备故障")));
        assertThat(order(id).at("/prodStatus").asText()).isEqualTo("SUSPENDED");
        assertError(doPost("/api/production/reports", admin, report(id, 20, 1, 0, 0)), "生产订单已暂停");
        ok(doPost("/api/production/prod-orders/" + id + "/resume", admin, null));
        assertThat(order(id).at("/prodStatus").asText()).isEqualTo("IN_PROGRESS");

        ok(doPost("/api/production/reports/" + r20 + "/unapprove", admin, null));
        assertThat(ok(doGet("/api/production/reports/" + r20, admin)).at("/status").asText()).isEqualTo("DRAFT");
        assertThat(order(id).at("/operations/1/goodQty").decimalValue()).isEqualByComparingTo("0");
        ok(doPost("/api/production/reports/" + r10 + "/unapprove", admin, null));
        ok(doDelete("/api/production/reports/" + r20, admin));

        // 作废：已领料的订单不能作废（非草稿/已计划状态）
        assertThat(doPost("/api/production/prod-orders/" + id + "/void", admin, Map.of("reason", "x")).at("/code").asInt()).isNotZero();
    }

    /** WO-T01 派工不能超过可派；工单报工；有报工的工单不能取消 */
    @Test
    void workOrders() throws Exception {
        String[] p = product(null);
        String id = releasedOrder(p[0], "500");
        JsonNode disp = ok(doGet("/api/production/work-orders/dispatchable?prodOrderId=" + id, admin));
        assertThat(disp.size()).isEqualTo(2);
        assertThat(disp.at("/0/undispatchedQty").decimalValue()).isEqualByComparingTo("500");
        String today = LocalDate.now().toString();
        Map<String, Object> l1 = Map.of("planDate", today, "shift", "DAY", "workCenterId", p[2], "planQty", 300);
        Map<String, Object> l2 = Map.of("planDate", today, "shift", "NIGHT", "workCenterId", p[2], "planQty", 300);
        assertError(doPost("/api/production/work-orders/batch", admin, Map.of("prodOrderId", id, "operationSeq", 10, "lines", List.of(l1, l2))),
                "派工数量超过工序可派数量 500");
        Map<String, Object> l3 = new HashMap<>(l2);
        l3.put("planQty", 200);
        JsonNode wos = ok(doPost("/api/production/work-orders/batch", admin, Map.of("prodOrderId", id, "operationSeq", 10, "lines", List.of(l1, l3))));
        assertThat(wos.at("/ids").size()).isEqualTo(2);
        String wo1 = wos.at("/ids/0").asText();
        String wo2 = wos.at("/ids/1").asText();
        JsonNode rest = ok(doGet("/api/production/work-orders/dispatchable?prodOrderId=" + id, admin));
        assertThat(rest.size()).isEqualTo(1);
        assertThat(rest.at("/0/operationSeq").asInt()).isEqualTo(20);

        String woNo = ok(doGet("/api/production/work-orders?pageNo=1&pageSize=10&prodOrderId=" + id, admin)).at("/list/0/docNo").asText();
        JsonNode ctx = ok(doGet("/api/production/reports/context?barcode=" + woNo, admin));
        assertThat(ctx.at("/prodOrderId").asText()).isEqualTo(id);
        assertThat(ctx.at("/operationSeq").asInt()).isEqualTo(10);

        Map<String, Object> r = report(id, 10, 100, 0, 0);
        r.put("workOrderId", wo1);
        reportOk(r);
        JsonNode row = null;
        for (JsonNode w : ok(doGet("/api/production/work-orders?pageNo=1&pageSize=10&prodOrderId=" + id, admin)).at("/list")) {
            if (w.at("/id").asText().equals(wo1)) row = w;
        }
        assertThat(row).isNotNull();
        assertThat(row.at("/goodQty").decimalValue()).isEqualByComparingTo("100");
        assertThat(row.at("/woStatus").asText()).isEqualTo("RUNNING");
        assertError(doPost("/api/production/work-orders/" + wo1 + "/cancel", admin, null), "工单已有报工，不能取消");
        ok(doPost("/api/production/work-orders/" + wo2 + "/cancel", admin, null));
        ok(doPost("/api/production/work-orders/" + wo1 + "/complete", admin, null));
        ok(doGet("/api/production/work-orders/" + wo1 + "/print-data", admin));
        ok(doGet("/api/production/work-orders/load?workCenterId=" + p[2] + "&date=" + today, admin));
    }

    /** ISS-T06 倒冲物料库存不足时报工失败 */
    @Test
    void backflushShortage() throws Exception {
        String prod = fg("插头");
        String glue = material("焊锡", CAT_AUX, "AUXILIARY", "NONE");
        bom(prod, List.of(bomLine(glue, 1, 0, "BACKFLUSH", null)));
        routing(prod, workCenter(), new Object[]{10, "焊接", true});
        String id = releasedOrder(prod, "10");
        JsonNode r = doPost("/api/production/reports", admin, report(id, 10, 5, 0, 0));
        assertThat(r.at("/code").asInt()).isNotZero();
        assertThat(r.at("/msg").asText()).contains("库存不足，需要 5，可用 0");
        stock(glue, W_AUX, "10", null);
        String rid = reportOk(report(id, 10, 5, 0, 0));
        // 仓库参数未开启来源单据自动确认：倒冲出库单由仓库确认
        String bf = jdbc.queryForObject("select id from mfg_issue where report_id = ? and deleted = 0", Long.class, Long.valueOf(rid)).toString();
        assertThat(materialLine(id, glue).at("/issuedQty").decimalValue()).isEqualByComparingTo("0");
        confirmIssue(bf, null, null);
        assertThat(materialLine(id, glue).at("/issuedQty").decimalValue()).isEqualByComparingTo("5");
    }

    /** RET-T02 不良退料；TRC-T01/T02 批次反向 / 正向追溯 */
    @Test
    void defectReturnAndTrace() throws Exception {
        String prod = material("主板", CAT_FG, "FINISHED", "BATCH");
        String comp = material("芯片", CAT_RAW, "RAW", "BATCH");
        bom(prod, List.of(bomLine(comp, 1, 0, "PICK", 10)));
        routing(prod, workCenter(), new Object[]{10, "贴片", true});
        String b1 = "B1-" + uniq();
        String b2 = "B2-" + uniq();
        stock(comp, W_RAW, "30", b1);
        stock(comp, W_RAW, "30", b2);
        String lot = "LOT-" + uniq();
        Map<String, Object> body = orderBody(prod, "40");
        body.put("batchNo", lot);
        String id = ok(doPost("/api/production/prod-orders", admin, body)).at("/id").asText();
        ok(doPost("/api/production/prod-orders/" + id + "/submit", admin, null));
        ok(doPost("/api/production/prod-orders/" + id + "/release", admin, Map.of()));

        String line = materialLine(id, comp).at("/id").asText();
        for (String batch : List.of(b1, b2)) {
            String iss = ok(doPost("/api/production/issues", admin, Map.of("prodOrderId", id, "lines", List.of(Map.of("materialLineId", line, "requestQty", 20)))))
                    .at("/ids/0").asText();
            ok(doPost("/api/production/issues/" + iss + "/submit", admin, null));
            confirmIssue(iss, null, Map.of(comp, batch));
        }

        // 不良退料：必须填写不良描述，退到不良品仓，发布事件
        JsonNode cands = ok(doGet("/api/production/returns/candidates?prodOrderId=" + id + "&returnType=DEFECT", admin));
        assertThat(cands.at("/0/returnableQty").decimalValue()).isEqualByComparingTo("40");
        assertThat(cands.at("/0/batchNos").size()).isEqualTo(2);
        String ret = ok(doPost("/api/production/returns", admin, Map.of("prodOrderId", id, "returnType", "DEFECT",
                "lines", List.of(Map.of("materialLineId", line, "qty", 2, "batchNo", b2))))).at("/ids/0").asText();
        assertThat(doPost("/api/production/returns/" + ret + "/submit", admin, null).at("/msg").asText()).contains("不良退料必须填写不良描述");
        int v = ok(doGet("/api/production/returns/" + ret, admin)).at("/version").asInt();
        ok(doPut("/api/production/returns/" + ret, admin, Map.of("prodOrderId", id, "returnType", "DEFECT", "version", v,
                "lines", List.of(Map.of("materialLineId", line, "qty", 2, "batchNo", b2, "defectDesc", "来料引脚氧化")))));
        ok(doPost("/api/production/returns/" + ret + "/submit", admin, null));
        JsonNode rd = ok(doGet("/api/production/returns/" + ret, admin));
        assertThat(rd.at("/warehouseName").asText()).isNotBlank();
        confirmStockIns(rd.at("/stockInIds").asText());
        assertThat(ItProductionConfig.DEFECT_RETURNS.stream().anyMatch(e -> e.getReturnId().toString().equals(ret)
                && e.getLines().get(0).batchNo().equals(b2) && "来料引脚氧化".equals(e.getLines().get(0).defectDesc()))).isTrue();
        assertThat(materialLine(id, comp).at("/returnedQty").decimalValue()).isEqualByComparingTo("2");

        // 报工 38，完工入库（产品批次 = 订单批号）
        reportOk(report(id, 10, 38, 0, 0));
        String fin = ok(doPost("/api/production/prod-orders/" + id + "/finish", admin, Map.of("qty", 38))).asText();
        confirmStockIns(jdbc.queryForObject("select stock_in_ids from mfg_finish where id = ?", String.class, Long.valueOf(fin)));

        JsonNode back = ok(doGet("/api/production/trace/backward?materialId=" + prod + "&batchNo=" + lot, admin));
        assertThat(back.at("/orderCount").asInt()).isEqualTo(1);
        assertThat(back.at("/batchCount").asInt()).isEqualTo(2);
        Map<String, BigDecimal> byBatch = new HashMap<>();
        for (JsonNode c : back.at("/root/children")) byBatch.put(c.at("/batchNo").asText(), c.at("/qty").decimalValue());
        assertThat(byBatch.get(b1)).isEqualByComparingTo("20");
        assertThat(byBatch.get(b2)).isEqualByComparingTo("18");
        assertThat(back.at("/root/prodOrderId").asText()).isEqualTo(id);

        JsonNode fwd = ok(doGet("/api/production/trace/forward?materialId=" + comp + "&batchNo=" + b2, admin));
        assertThat(fwd.at("/orderCount").asInt()).isEqualTo(1);
        assertThat(fwd.at("/root/children/0/batchNo").asText()).isEqualTo(lot);
        assertThat(fwd.at("/root/children/0/qty").decimalValue()).isEqualByComparingTo("18");

        String nonBatch = raw("螺钉");
        assertError(doGet("/api/production/trace/forward?materialId=" + nonBatch + "&batchNo=X", admin), "该物料未启用批次管理，无法精确追溯");
    }

    /** PO-T09 MRP 建议转生产订单；ProductionQueryApi 在制数量 */
    @Test
    void mrpAndWip() throws Exception {
        String[] p = product("100");
        Long prod = Long.valueOf(p[0]);
        List<Long> ids = orderApi.createFromMrp(List.of(new MrpSuggestion(777L, "MRP-777", prod, new BigDecimal("20"), LocalDate.now(),
                LocalDate.now().plusDays(5), null, null, null, "MRP 建议")));
        String id = ids.get(0).toString();
        JsonNode o = order(id);
        assertThat(o.at("/prodStatus").asText()).isEqualTo("PLANNED");
        assertThat(o.at("/sourceNo").asText()).isEqualTo("MRP-777");
        assertThat(queryApi.getWipQty(List.of(prod))).doesNotContainKey(prod);

        ok(doPost("/api/production/prod-orders/" + id + "/release", admin, Map.of()));
        for (String i : issueAll(id)) confirmIssue(i, null, null);
        reportOk(report(id, 10, 20, 0, 0));
        reportOk(report(id, 20, 18, 0, 2));
        WipDTO wip = queryApi.getWipQty(List.of(prod)).get(prod);
        assertThat(wip.wipQty()).isEqualByComparingTo("18");
        assertThat(wip.orders().get(0).prodOrderId().toString()).isEqualTo(id);
        assertThat(queryApi.getAllocatedQty(Long.valueOf(p[1]))).isEqualByComparingTo("0");
    }

    /** YLD-T01 直通率 = 各报工点一次良率之积；不良柏拉图 */
    @Test
    void yieldFpy() throws Exception {
        String[] p = product(null);
        String id = releasedOrder(p[0], "100");
        Map<String, Object> r10 = report(id, 10, 90, 10, 0);
        r10.put("defects", List.of(Map.of("defectCode", "SCRATCH", "qty", 10)));
        reportOk(r10);
        reportOk(report(id, 20, 81, 0, 9));
        JsonNode y = ok(doGet("/api/production/reports/yield?materialId=" + p[0] + "&groupBy=OPERATION", admin));
        assertThat(y.at("/fpy").decimalValue()).isEqualByComparingTo("0.81");
        assertThat(y.at("/operations").size()).isEqualTo(2);
        assertThat(y.at("/pareto/0/defectCode").asText()).isEqualTo("SCRATCH");

        // 不良处置报废后计入订单报废
        String def = ok(doGet("/api/production/defects?pageNo=1&pageSize=10&prodOrderId=" + id, admin)).at("/list/0/id").asText();
        ok(doPost("/api/production/defects/" + def + "/scrap", admin, Map.of("qty", 10, "scrapReason", "PROCESS")));
        assertThat(order(id).at("/scrappedQty").decimalValue()).isEqualByComparingTo("19");
        // 品质模块实现了 DefectNcrCreator：不良可生成 NCR（来源 PRODUCTION，数量 = 不良数）
        assertThat(ok(doGet("/api/production/defects/ncr-available", admin)).asBoolean()).isTrue();
        String ncrNo = ok(doPost("/api/production/defects/" + def + "/to-ncr", admin, null)).asText();
        JsonNode ncr = ok(doGet("/api/quality/ncrs?docNo=" + ncrNo, admin)).at("/list/0");
        assertThat(ncr.at("/source").asText()).isEqualTo("PRODUCTION");
        assertThat(ncr.at("/ncrQty").decimalValue()).isEqualByComparingTo("10");
    }

    @Test
    void disassemblyOrderAndBomVersions() throws Exception {
        String prod = fg("拆解主机");
        String c1 = raw("拆解子件A");
        String c2 = raw("拆解子件B");
        String bom1 = bom(prod, List.of(bomLine(c1, 2, 0, "PICK", null), bomLine(c2, 1, 0.05, "PICK", null)));
        String bom2 = bom(prod, List.of(bomLine(c1, 3, 0, "PICK", null)));

        // 编辑页 BOM 版本下拉列出全部已审核版本，默认版本在前
        JsonNode preview = ok(doGet("/api/production/prod-orders/preview-materials?materialId=" + prod, admin));
        assertThat(preview.at("/boms").size()).isEqualTo(2);
        assertThat(preview.at("/boms/0/id").asText()).isEqualTo(bom1);
        assertThat(preview.at("/boms/0/isDefault").asBoolean()).isTrue();
        assertThat(preview.at("/boms/1/id").asText()).isEqualTo(bom2);

        // 拆解订单：投入产品本身，下达时按 BOM 固化产出（不含损耗）
        stock(prod, W_FG, "10", null);
        Map<String, Object> body = orderBody(prod, "5");
        body.put("orderType", "DISASSEMBLY");
        String id = ok(doPost("/api/production/prod-orders", admin, body)).at("/id").asText();
        ok(doPost("/api/production/prod-orders/" + id + "/submit", admin, null));
        ok(doPost("/api/production/prod-orders/" + id + "/release", admin, Map.of("confirmShortage", true)));
        JsonNode d = order(id);
        assertThat(d.at("/materials").size()).isEqualTo(1);
        assertThat(d.at("/materials/0/componentId").asText()).isEqualTo(prod);
        assertThat(d.at("/materials/0/requiredQty").decimalValue()).isEqualByComparingTo("5");
        Map<String, JsonNode> outputs = new HashMap<>();
        for (JsonNode x : d.at("/outputs")) outputs.put(x.at("/componentId").asText(), x);
        assertThat(outputs.get(c1).at("/expectedQty").decimalValue()).isEqualByComparingTo("10");
        assertThat(outputs.get(c2).at("/expectedQty").decimalValue()).isEqualByComparingTo("5");
        assertThat(queryApi.getWipQty(List.of(Long.valueOf(prod)))).doesNotContainKey(Long.valueOf(prod));

        // 没有产品完工入库；未拆解（未领料）时不能入库子件
        assertThat(doPost("/api/production/prod-orders/" + id + "/finish", admin, Map.of("qty", 1)).at("/msg").asText()).contains("拆解订单没有产品完工入库");
        JsonNode cands = ok(doGet("/api/production/returns/candidates?prodOrderId=" + id + "&returnType=OUTPUT", admin));
        assertThat(cands.size()).isEqualTo(2);
        assertThat(cands.at("/0/returnableQty").decimalValue()).isEqualByComparingTo("0");

        // 领出 3 个产品拆解：子件可入库按已拆解折算
        String iss = ok(doPost("/api/production/issues", admin, Map.of("prodOrderId", id, "lines",
                List.of(Map.of("materialLineId", d.at("/materials/0/id").asText(), "requestQty", 3))))).at("/ids/0").asText();
        ok(doPost("/api/production/issues/" + iss + "/submit", admin, null));
        confirmIssue(iss, null, null);
        Map<String, JsonNode> byComp = new HashMap<>();
        for (JsonNode c : ok(doGet("/api/production/returns/candidates?prodOrderId=" + id + "&returnType=OUTPUT", admin))) {
            byComp.put(c.at("/materialId").asText(), c);
        }
        assertThat(byComp.get(c1).at("/returnableQty").decimalValue()).isEqualByComparingTo("6");
        assertThat(byComp.get(c2).at("/returnableQty").decimalValue()).isEqualByComparingTo("3");
        String over = ok(doPost("/api/production/returns", admin, Map.of("prodOrderId", id, "returnType", "OUTPUT",
                "lines", List.of(Map.of("materialLineId", byComp.get(c1).at("/materialLineId").asText(), "qty", 7))))).at("/ids/0").asText();
        assertThat(doPost("/api/production/returns/" + over + "/submit", admin, null).at("/msg").asText()).contains("超过可退数量");
        ok(doDelete("/api/production/returns/" + over, admin));

        String ret = ok(doPost("/api/production/returns", admin, Map.of("prodOrderId", id, "returnType", "OUTPUT",
                "lines", List.of(Map.of("materialLineId", byComp.get(c1).at("/materialLineId").asText(), "qty", 6),
                        Map.of("materialLineId", byComp.get(c2).at("/materialLineId").asText(), "qty", 2))))).at("/ids/0").asText();
        ok(doPost("/api/production/returns/" + ret + "/submit", admin, null));
        confirmStockIns(ok(doGet("/api/production/returns/" + ret, admin)).at("/stockInIds").asText());
        outputs.clear();
        for (JsonNode x : order(id).at("/outputs")) outputs.put(x.at("/componentId").asText(), x);
        assertThat(outputs.get(c1).at("/receivedQty").decimalValue()).isEqualByComparingTo("6");
        assertThat(outputs.get(c2).at("/receivedQty").decimalValue()).isEqualByComparingTo("2");

        // 非拆解订单不能办理拆解入库
        String normal = releasedOrder(prod, "1");
        assertThat(doPost("/api/production/returns", admin, Map.of("prodOrderId", normal, "returnType", "OUTPUT",
                "lines", List.of(Map.of("materialLineId", "1", "qty", 1)))).at("/msg").asText()).contains("只有拆解订单可以办理拆解入库");

        // 报工完成 → 已完工（拆解订单按报工判断）
        reportOk(report(id, 10, 5, 0, 0));
        assertThat(order(id).at("/prodStatus").asText()).isEqualTo("COMPLETED");
    }

    @Autowired
    com.erp.module.production.service.bi.ProductionBiFactProvider biFacts;

    /** BI 生产事实的标准工时 = 合格 × 工序标准秒 ÷ 3600（36 秒 / 件 × 100 = 1 小时） */
    @Test
    void biStandardHours() throws Exception {
        String prod = fg("标准工时产品");
        String comp = raw("标准工时料");
        bom(prod, List.of(bomLine(comp, 1, 0, "BACKFLUSH", null)));
        routing(prod, workCenter(), new Object[]{10, "组装", true});
        stock(comp, W_RAW, "100", null);
        String id = releasedOrder(prod, "100");
        reportOk(report(id, 10, 100, 0, 0));
        BigDecimal std = biFacts.productionFacts(LocalDate.now(), LocalDate.now()).stream()
                .filter(f -> f.materialId().toString().equals(prod) && f.goodQty().signum() > 0)
                .map(f -> f.stdHours()).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(std).isEqualByComparingTo("1");
    }
}
