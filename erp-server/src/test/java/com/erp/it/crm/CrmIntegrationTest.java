package com.erp.it.crm;

import com.erp.it.AbstractIntegrationTest;
import com.erp.module.crm.api.credit.CreditApi;
import com.erp.module.crm.api.credit.CreditCheckPoint;
import com.erp.module.crm.api.credit.CreditCheckResult;
import com.erp.module.crm.api.credit.CreditUsage;
import com.erp.module.crm.api.customer.CustomerApi;
import com.erp.module.crm.api.opportunity.OpportunityApi;
import com.erp.module.crm.api.part.CustomerPartApi;
import com.erp.module.crm.service.CreditService;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** CRM（需求 03 各功能点验收用例） */
class CrmIntegrationTest extends AbstractIntegrationTest {

    static final String TERM = "404";
    static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Autowired
    CustomerApi customerApi;
    @Autowired
    CustomerPartApi customerPartApi;
    @Autowired
    CreditApi creditApi;
    @Autowired
    OpportunityApi opportunityApi;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    CreditService creditService;

    private String admin;

    @BeforeEach
    void login() throws Exception {
        admin = loginAsAdmin();
    }

    private Map<String, Object> customer(String name, String country) {
        Map<String, Object> c = new HashMap<>();
        c.put("name", name);
        c.put("nameEn", "CN".equals(country) ? null : name);
        c.put("country", country);
        return c;
    }

    private String create(Map<String, Object> body, String token) throws Exception {
        return ok(doPost("/api/crm/customers", token, body)).at("/id").asText();
    }

    private JsonNode detail(String id) throws Exception {
        return ok(doGet("/api/crm/customers/" + id, admin));
    }

    /** 可以转正式的外销客户：主联系人、默认收货地址、付款条件 */
    private Map<String, Object> fullCustomer(String name) {
        Map<String, Object> c = customer(name, "US");
        c.put("paymentTermId", TERM);
        c.put("contacts", List.of(Map.of("name", "John", "email", "john@example.com", "isPrimary", true)));
        c.put("addresses", List.of(Map.of("addressType", "SHIP_TO", "companyName", name, "country", "US", "addressLine", "1 Main St", "isDefault", true)));
        return c;
    }

    private String activeCustomer(String name) throws Exception {
        String id = create(fullCustomer(name), admin);
        assertThat(ok(doPost("/api/crm/customers/" + id + "/activate", admin, null)).at("/customerStatus").asText()).isEqualTo("ACTIVE");
        return id;
    }

    /** 新建用户（角色数据范围、权限），返回 [userId, token] */
    private String[] user(String dataScope, List<String> permissions) throws Exception {
        String code = "R" + uniq();
        String roleId = ok(doPost("/api/system/roles", admin, Map.of("code", code, "name", "角色" + code, "dataScope", dataScope, "sort", 10))).asText();
        ok(doPut("/api/system/roles/" + roleId + "/permissions", admin, Map.of("permissions", permissions)));
        String username = "crm" + uniq();
        Map<String, Object> u = new HashMap<>();
        u.put("username", username);
        u.put("realName", "业务员" + username);
        u.put("deptId", "100");
        u.put("roleIds", List.of(roleId));
        u.put("password", "Passw0rd!2026");
        u.put("mustChangePassword", false);
        String id = ok(doPost("/api/system/users", admin, u)).at("/id").asText();
        return new String[]{id, login(username, "Passw0rd!2026")};
    }

    // ==================== 客户 ====================

    /** CUS-T01 外销默认值；T02 名称 + 国家唯一；R02 外销英文名；R05 联系方式 */
    @Test
    void createDefaultsAndUnique() throws Exception {
        String name = "ABC Inc. " + uniq();
        String id = create(customer(name, "us"), admin);
        JsonNode d = detail(id);
        assertThat(d.at("/isForeign").asBoolean()).isTrue();
        assertThat(d.at("/currency").asText()).isEqualTo("USD");
        assertThat(d.at("/salesTaxRate").decimalValue()).isEqualByComparingTo("0");
        assertThat(d.at("/code").asText()).matches("C\\d{5}");
        assertThat(d.at("/customerStatus").asText()).isEqualTo("PROSPECT");
        assertThat(d.at("/country").asText()).isEqualTo("US");

        assertError(doPost("/api/crm/customers", admin, customer(name.toLowerCase().replace(".", ""), "US")), "该国家已存在名称为「" + name + "」的客户");
        // 不同国家可以同名
        create(customer(name, "DE"), admin);

        Map<String, Object> noEn = customer("XYZ " + uniq(), "JP");
        noEn.put("nameEn", null);
        assertError(doPost("/api/crm/customers", admin, noEn), "外销客户必须填写英文名称");
        Map<String, Object> badContact = customer("内销客户" + uniq(), "CN");
        badContact.put("contacts", List.of(Map.of("name", "张三")));
        assertError(doPost("/api/crm/customers", admin, badContact), "联系人「张三」至少需要填写邮箱、电话、手机中的一项");
        // 内销默认本位币、13%
        JsonNode cn = detail(create(customer("深圳某某电子有限公司" + uniq(), "CN"), admin));
        assertThat(cn.at("/isForeign").asBoolean()).isFalse();
        assertThat(cn.at("/salesTaxRate").decimalValue()).isEqualByComparingTo("0.13");
    }

    /** R01 查重：去掉公司后缀后同名提示 */
    @Test
    void duplicateCheck() throws Exception {
        String core = "Acme" + uniq();
        create(customer(core + " Co., Ltd.", "US"), admin);
        JsonNode dups = ok(doPost("/api/crm/customers/duplicate-check", admin, Map.of("name", core + " Limited", "country", "GB")));
        assertThat(dups.size()).isEqualTo(1);
        assertThat(dups.at("/0/matchedBy").asText()).isEqualTo("NAME");
        JsonNode r = ok(doPost("/api/crm/customers", admin, customer(core + " Limited", "GB")));
        assertThat(r.at("/warnings/0").asText()).startsWith("发现疑似重复客户：");
    }

    /** CUS-T03 转正式缺项；T04 潜在客户不能下单；T07 黑名单不能出货；停用/启用 */
    @Test
    void activateAndStatuses() throws Exception {
        String name = "Beta " + uniq();
        Map<String, Object> body = fullCustomer(name);
        body.remove("addresses");
        String id = create(body, admin);
        String shortName = detail(id).at("/shortName").asText();
        assertError(doPost("/api/crm/customers/" + id + "/activate", admin, null), "转正式客户需要：默认收货地址");
        assertThatThrownBy(() -> customerApi.validateCanOrder(Long.valueOf(id))).hasMessage("客户「" + shortName + "」不是正式客户，不能下单");
        customerApi.validateCanQuote(Long.valueOf(id));

        String a = activeCustomer("Gamma " + uniq());
        assertThat(customerApi.validateCanOrder(Long.valueOf(a)).status().name()).isEqualTo("ACTIVE");
        assertThat(customerApi.getDefaultAddress(Long.valueOf(a), "SHIP_TO")).isPresent();
        assertThat(customerApi.getContacts(Long.valueOf(a)).get(0).primary()).isTrue();

        assertError(doPost("/api/crm/customers/" + a + "/blacklist", admin, Map.of()), "请填写加入黑名单的原因");
        ok(doPost("/api/crm/customers/" + a + "/blacklist", admin, Map.of("reason", "长期拖欠货款")));
        String aShort = detail(a).at("/shortName").asText();
        assertThatThrownBy(() -> customerApi.validateCanShip(Long.valueOf(a))).hasMessage("客户「" + aShort + "」在黑名单中，不能出货");
        ok(doPost("/api/crm/customers/" + a + "/unblacklist", admin, Map.of("reason", "已结清")));
        assertThat(detail(a).at("/customerStatus").asText()).isEqualTo("ACTIVE");
        ok(doPost("/api/crm/customers/" + a + "/disable", admin, Map.of()));
        customerApi.validateCanShip(Long.valueOf(a));
        assertThatThrownBy(() -> customerApi.validateCanOrder(Long.valueOf(a))).hasMessageContaining("已停用");
        ok(doPost("/api/crm/customers/" + a + "/enable", admin, null));
        // 正式客户改名：提示已有单据不会改变
        Map<String, Object> rename = fullCustomer("Gamma Renamed " + uniq());
        rename.put("version", detail(a).at("/version").asInt());
        assertThat(ok(doPut("/api/crm/customers/" + a, admin, rename)).at("/warnings").toString()).contains("已有单据中的客户名称不会改变");
    }

    /** CUS-T05 数据权限；T06 转移（事件、转移记录）；R08 无转移权限只能是自己 */
    @Test
    void dataScopeAndTransfer() throws Exception {
        List<String> perms = List.of("crm:customer:query", "crm:customer:create", "crm:customer:update");
        String[] a = user("SELF", perms);
        String[] b = user("SELF", perms);
        String ca = create(customer("业务A客户" + uniq(), "CN"), a[1]);
        String cb = create(customer("业务B客户" + uniq(), "CN"), b[1]);
        JsonNode listA = ok(doGet("/api/crm/customers?pageNo=1&pageSize=100", a[1])).at("/list");
        assertThat(listA.findValuesAsText("id")).contains(ca).doesNotContain(cb);
        assertError(doGet("/api/crm/customers/" + cb, a[1]), "客户不存在");
        Map<String, Object> other = customer("代建客户" + uniq(), "CN");
        other.put("ownerId", b[0]);
        assertError(doPost("/api/crm/customers", a[1], other), "没有客户转移权限，负责人只能是自己");

        ItCrmConfig.OWNER_EVENTS.clear();
        ok(doPost("/api/crm/customers/transfer", admin, Map.of("customerIds", List.of(ca), "newOwnerId", b[0], "transferDocs", true, "reason", "A 离职")));
        assertThat(detail(ca).at("/ownerId").asText()).isEqualTo(b[0]);
        assertThat(ItCrmConfig.OWNER_EVENTS).hasSize(1);
        assertThat(ItCrmConfig.OWNER_EVENTS.get(0).isTransferDocs()).isTrue();
        assertThat(ItCrmConfig.OWNER_EVENTS.get(0).getCustomerIds()).containsExactly(Long.valueOf(ca));
        JsonNode logs = ok(doGet("/api/crm/customers/" + ca + "/transfer-logs", admin));
        assertThat(logs.at("/0/toOwnerId").asText()).isEqualTo(b[0]);
        assertThat(logs.at("/0/reason").asText()).isEqualTo("A 离职");
        assertThat(ok(doGet("/api/crm/customers?pageNo=1&pageSize=100", b[1])).at("/list").findValuesAsText("id")).contains(ca, cb);
        // 联系人查询按客户数据范围
        assertThat(ok(doGet("/api/crm/contacts?pageNo=1&pageSize=100", a[1])).at("/total").asInt()).isZero();
    }

    /** CUS-T08 有业务数据不能删除；潜在客户无数据可删 */
    @Test
    void deleteRules() throws Exception {
        String id = create(customer("待删客户" + uniq(), "CN"), admin);
        ItCrmConfig.REFERENCED.add(Long.valueOf(id));
        try {
            assertError(doDelete("/api/crm/customers/" + id, admin), "客户已有业务数据，不能删除");
        } finally {
            ItCrmConfig.REFERENCED.remove(Long.valueOf(id));
        }
        ok(doDelete("/api/crm/customers/" + id, admin));
        assertError(doGet("/api/crm/customers/" + id, admin), "客户不存在");
    }

    // ==================== 客户料号 ====================

    /** CP-T01、R01 唯一、R02 找不到返回空、R03 */
    @Test
    void customerPart() throws Exception {
        String cid = activeCustomer("Parts " + uniq());
        String cat = ok(doPost("/api/engineering/categories", admin, Map.of("code", "K" + uniq().substring(0, 6), "name", "成品" + uniq(),
                "codePrefix", "K" + uniq().substring(0, 6), "defaultMaterialType", "FINISHED", "defaultBaseUom", "PCS", "defaultTracking", "NONE",
                "defaultIqcRequired", false, "sort", 10))).asText();
        String fg = ok(doPost("/api/engineering/materials", admin, Map.of("categoryId", cat, "name", "成品" + uniq(), "materialType", "FINISHED",
                "baseUom", "PCS"))).asText();
        Map<String, Object> part = new HashMap<>(Map.of("customerId", cid, "customerPartNo", "X-100", "materialId", fg));
        assertError(doPost("/api/crm/customer-parts", admin, part), "本厂物料必须是启用的半成品或成品");
        ok(doPost("/api/engineering/materials/" + fg + "/enable", admin, null));
        String pid = ok(doPost("/api/crm/customer-parts", admin, part)).asText();
        String code = ok(doGet("/api/engineering/materials/" + fg, admin)).at("/code").asText();
        assertError(doPost("/api/crm/customer-parts", admin, part), "客户料号「X-100」已存在，对应物料 " + code);
        assertThat(customerPartApi.toMaterial(Long.valueOf(cid), "X-100").orElseThrow().materialId()).isEqualTo(Long.valueOf(fg));
        assertThat(customerPartApi.toMaterial(Long.valueOf(cid), "X-200")).isEmpty();
        assertThat(customerPartApi.toCustomerPart(Long.valueOf(cid), Long.valueOf(fg)).orElseThrow().customerPartNo()).isEqualTo("X-100");
        ok(doPost("/api/crm/customer-parts/" + pid + "/disable", admin, null));
        assertThat(customerPartApi.toMaterial(Long.valueOf(cid), "X-100")).isEmpty();
        assertThat(ok(doGet("/api/crm/customer-parts?pageNo=1&pageSize=10&customerId=" + cid, admin)).at("/list/0/materialCode").asText()).isEqualTo(code);
        // 有客户料号的客户不能删除（潜在客户才可删，这里是正式客户，先验证业务数据口径）
        ok(doDelete("/api/crm/customer-parts/" + pid, admin));
    }

    // ==================== 信用 ====================

    private void setCredit(String customerId, String limit, String control) throws Exception {
        ok(doPost("/api/crm/credit-changes", admin, Map.of("customerId", customerId, "newLimit", limit, "newControl", control, "reason", "年度评审")));
    }

    /** CRD-T01 BLOCK 超额；T02 WARN；T03 逾期；T05 刷新占用；R02 未设置额度只查逾期 */
    @Test
    void creditCheck() throws Exception {
        String id = activeCustomer("Credit " + uniq());
        Long cid = Long.valueOf(id);
        CreditCheckResult none = creditApi.check(cid, new BigDecimal("999999999"), CreditCheckPoint.ORDER);
        assertThat(none.pass()).isTrue();
        assertThat(none.limit()).isNull();

        setCredit(id, "1000000", "BLOCK");
        assertThat(detail(id).at("/credit/creditLimit").decimalValue()).isEqualByComparingTo("1000000");
        ItCrmConfig.USAGE.put(cid, new CreditUsage(new BigDecimal("600000"), null, new BigDecimal("300000")));
        creditApi.refresh(List.of(cid));
        CreditCheckResult r = creditApi.check(cid, new BigDecimal("200000"), CreditCheckPoint.ORDER);
        assertThat(r.pass()).isFalse();
        assertThat(r.used()).isEqualByComparingTo("900000");
        assertThat(r.message()).endsWith("超出 100,000.00");
        // 出货检查不重复计算未出货订单：60 万 + 20 万 < 100 万
        assertThat(creditApi.check(cid, new BigDecimal("200000"), CreditCheckPoint.SHIPMENT).pass()).isTrue();

        setCredit(id, "1000000", "WARN");
        CreditCheckResult w = creditApi.check(cid, new BigDecimal("200000"), CreditCheckPoint.ORDER);
        assertThat(w.pass()).isTrue();
        assertThat(w.message()).contains("信用额度不足");

        setCredit(id, "1000000", "BLOCK");
        ItCrmConfig.USAGE.put(cid, new CreditUsage(new BigDecimal("500000"), new BigDecimal("50000"), BigDecimal.ZERO));
        creditApi.refresh(List.of(cid));
        CreditCheckResult o = creditApi.check(cid, new BigDecimal("10"), CreditCheckPoint.ORDER);
        assertThat(o.pass()).isFalse();
        assertThat(o.message()).isEqualTo("客户「" + detail(id).at("/shortName").asText() + "」有逾期应收 50,000.00，请先催收");
        ItCrmConfig.USAGE.remove(cid);

        JsonNode rows = ok(doGet("/api/crm/credits?pageNo=1&pageSize=10&customerId=" + id, admin)).at("/list");
        assertThat(rows.at("/0/overdueAmount").decimalValue()).isEqualByComparingTo("50000");
        assertThat(ok(doGet("/api/crm/credit-changes?customerId=" + id, admin)).size()).isEqualTo(3);
    }

    /** CRD-T04 临时额度到期恢复 */
    @Test
    void temporaryCredit() throws Exception {
        String id = activeCustomer("Temp " + uniq());
        setCredit(id, "1000000", "WARN");
        ok(doPost("/api/crm/credit-changes", admin, Map.of("customerId", id, "newLimit", "1500000", "expireDate", LocalDate.now().plusDays(3).toString(),
                "reason", "旺季临时")));
        assertThat(detail(id).at("/credit/creditLimit").decimalValue()).isEqualByComparingTo("1500000");
        jdbc.update("UPDATE crm_credit_change SET expire_date = ? WHERE customer_id = ? AND expire_date IS NOT NULL", LocalDate.now().minusDays(1),
                Long.valueOf(id));
        creditService.restoreExpired();
        assertThat(detail(id).at("/credit/creditLimit").decimalValue()).isEqualByComparingTo("1000000");
    }

    // ==================== 跟进 ====================

    /** FU-T02 超过 24 小时不能修改；R01；FU-T01/T03 提醒 */
    @Test
    void followup() throws Exception {
        String cid = create(customer("跟进客户" + uniq(), "CN"), admin);
        Map<String, Object> f = new HashMap<>(Map.of("customerId", cid, "followupType", "PHONE", "followupAt", LocalDateTime.now().minusHours(1).format(DT),
                "subject", "电话沟通", "content", "确认需求", "nextFollowupAt", LocalDate.now().toString()));
        String fid = ok(doPost("/api/crm/followups", admin, f)).asText();
        Map<String, Object> future = new HashMap<>(f);
        future.put("followupAt", LocalDateTime.now().plusDays(1).format(DT));
        assertError(doPost("/api/crm/followups", admin, future), "跟进时间不能晚于当前时间");

        JsonNode rows = ok(doGet("/api/crm/followups?pageNo=1&pageSize=10&customerId=" + cid, admin)).at("/list");
        assertThat(rows.at("/0/editable").asBoolean()).isTrue();
        jdbc.update("UPDATE crm_followup SET created_at = ? WHERE id = ?", LocalDateTime.now().minusHours(25), Long.valueOf(fid));
        assertThat(ok(doGet("/api/crm/followups?pageNo=1&pageSize=10&customerId=" + cid, admin)).at("/list/0/editable").asBoolean()).isFalse();
        assertError(doPut("/api/crm/followups/" + fid, admin, f), "跟进记录超过 24 小时，不能修改");

        // 另一客户：到期未跟进 → 提醒；已有更晚跟进 → 不提醒
        String c2 = create(customer("跟进客户" + uniq(), "CN"), admin);
        Map<String, Object> f2 = new HashMap<>(f);
        f2.put("customerId", c2);
        f2.put("followupAt", LocalDateTime.now().minusHours(3).format(DT));
        String f2id = ok(doPost("/api/crm/followups", admin, f2)).asText();
        Map<String, Object> later = new HashMap<>(f2);
        later.put("followupAt", LocalDateTime.now().minusHours(1).format(DT));
        later.remove("nextFollowupAt");
        ok(doPost("/api/crm/followups", admin, later));
        assertThat(jdbc.queryForObject("SELECT reminded FROM crm_followup WHERE id = ?", Boolean.class, Long.valueOf(f2id))).isTrue();
        assertThat(jdbc.queryForObject("SELECT reminded FROM crm_followup WHERE id = ?", Boolean.class, Long.valueOf(fid))).isFalse();
        JsonNode pending = ok(doGet("/api/crm/followups?pageNo=1&pageSize=50&pendingOnly=true", admin)).at("/list");
        assertThat(pending.findValuesAsText("id")).contains(fid).doesNotContain(f2id);
    }

    // ==================== 商机 ====================

    private String opp(String customerId, String stage, String amount) throws Exception {
        return ok(doPost("/api/crm/opportunities", admin, Map.of("name", "储能项目" + uniq(), "customerId", customerId, "stage", stage,
                "amount", amount, "currency", "CNY", "expectedDate", LocalDate.now().plusMonths(2).toString()))).asText();
    }

    /** OPP-T01 报价自动推进；T02 订单赢单；T03 输单原因；T04 漏斗；R02 已结束不能修改 */
    @Test
    void opportunity() throws Exception {
        String cid = create(customer("商机客户" + uniq(), "CN"), admin);
        String o1 = opp(cid, "REQUIREMENT", "100000");
        JsonNode d = ok(doGet("/api/crm/opportunities/" + o1, admin));
        assertThat(d.at("/winRate").decimalValue()).isEqualByComparingTo("0.30");
        opportunityApi.onQuotationCreated(Long.valueOf(o1));
        d = ok(doGet("/api/crm/opportunities/" + o1, admin));
        assertThat(d.at("/stage").asText()).isEqualTo("QUOTATION");
        assertThat(d.at("/winRate").decimalValue()).isEqualByComparingTo("0.50");

        String o2 = opp(cid, "NEGOTIATION", "200000");
        String o3 = opp(cid, "CONTACT", "50000");
        JsonNode funnel = ok(doGet("/api/crm/opportunities/funnel?customerId=" + cid, admin));
        assertThat(funnel.at("/totalCount").asInt()).isEqualTo(3);
        assertThat(funnel.at("/totalAmountBase").decimalValue()).isEqualByComparingTo("350000");
        // 加权：10 万 × 50% + 20 万 × 70% + 5 万 × 10% = 19.5 万
        assertThat(funnel.at("/totalWeightedBase").decimalValue()).isEqualByComparingTo("195000");
        assertThat(funnel.at("/stages/2/count").asInt()).isEqualTo(1);

        opportunityApi.onOrderApproved(Long.valueOf(o1), "SO-T-001");
        d = ok(doGet("/api/crm/opportunities/" + o1, admin));
        assertThat(d.at("/status").asText()).isEqualTo("WON");
        assertThat(d.at("/wonOrderNo").asText()).isEqualTo("SO-T-001");
        assertError(doPost("/api/crm/opportunities/" + o1 + "/stage", admin, Map.of("stage", "CONTACT")), "商机已结束，不能修改");

        assertError(doPost("/api/crm/opportunities/" + o2 + "/lose", admin, Map.of()), "请选择输单原因");
        ok(doPost("/api/crm/opportunities/" + o2 + "/lose", admin, Map.of("lostReason", "PRICE", "remark", "对手低 8%")));
        ok(doPost("/api/crm/opportunities/" + o3 + "/shelve", admin, Map.of()));
        ok(doPost("/api/crm/opportunities/" + o3 + "/resume", admin, null));
        assertThat(ok(doGet("/api/crm/opportunities/" + o3, admin)).at("/stage").asText()).isEqualTo("CONTACT");
        // 有商机的潜在客户不能删除
        assertError(doDelete("/api/crm/customers/" + cid, admin), "客户已有业务数据，不能删除");
    }

}
