package com.erp.it.workbench;

import com.erp.it.AbstractIntegrationTest;
import com.erp.module.system.api.notify.AlertRaisedEvent;
import com.erp.module.system.api.notify.MessageSendEvent;
import com.erp.module.system.api.notify.NotifyApi;
import com.erp.module.system.api.notify.TodoCreatedEvent;
import com.erp.module.system.api.notify.TodoDoneEvent;
import com.erp.module.system.api.workflow.WorkflowApi;
import com.erp.module.system.service.support.SystemCaches;
import com.erp.module.workbench.service.todo.TodoService;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/** 工作台（需求 02）：待办与审批、消息、公告、预警、首页卡片与布局 */
class WorkbenchIntegrationTest extends AbstractIntegrationTest {

    static final String PASSWORD = "Passw0rd!2026";
    static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final AtomicLong BIZ_ID = new AtomicLong(8_800_000);

    @Autowired
    NotifyApi notifyApi;
    @Autowired
    WorkflowApi workflowApi;
    @Autowired
    TodoService todoService;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    SystemCaches caches;

    String admin;

    @BeforeEach
    void setUp() throws Exception {
        admin = loginAsAdmin();
    }

    // ==================== 辅助 ====================

    record User(Long id, String token) {
    }

    private String role(String dataScope, List<String> permissions) throws Exception {
        String code = "R" + uniq();
        String id = ok(doPost("/api/system/roles", admin, Map.of("code", code, "name", "角色" + code, "dataScope", dataScope, "sort", 10))).asText();
        if (!permissions.isEmpty()) ok(doPut("/api/system/roles/" + id + "/permissions", admin, Map.of("permissions", permissions)));
        return id;
    }

    private String dept(String name) throws Exception {
        return ok(doPost("/api/system/orgs", admin, Map.of("parentId", "100", "orgType", "DEPT", "code", "D" + uniq(), "name", name + uniq(), "sort", 10))).asText();
    }

    private User user(String deptId, String dataScope, List<String> permissions) throws Exception {
        String username = "wb" + uniq();
        Map<String, Object> u = new HashMap<>();
        u.put("username", username);
        u.put("realName", "工作台" + username);
        u.put("deptId", deptId);
        u.put("roleIds", List.of(role(dataScope, permissions)));
        u.put("password", PASSWORD);
        u.put("mustChangePassword", false);
        Long id = Long.valueOf(ok(doPost("/api/system/users", admin, u)).at("/id").asText());
        return new User(id, login(username, PASSWORD));
    }

    private User user(List<String> permissions) throws Exception {
        return user("100", "SELF", permissions);
    }

    private long todoCount(User u) throws Exception {
        return ok(doGet("/api/workbench/todos/count", u.token())).asLong();
    }

    private JsonNode todos(User u, String status) throws Exception {
        return ok(doGet("/api/workbench/todos?pageSize=100&status=" + status, u.token())).at("/list");
    }

    private JsonNode findBy(JsonNode list, String field, String value) {
        for (JsonNode n : list) if (value.equals(n.at("/" + field).asText())) return n;
        return null;
    }

    /** 发布一个“指定审批人”的单节点流程 */
    private void publishFlow(Long approver) throws Exception {
        jdbc.update("UPDATE wf_definition SET deleted = 1 WHERE biz_type = ?", ItWorkbenchConfig.BIZ);
        String id = ok(doPost("/api/system/workflow/definitions/draft?bizType=" + ItWorkbenchConfig.BIZ, admin, null)).at("/id").asText();
        Map<String, Object> node = new HashMap<>();
        node.put("name", "主管审批");
        node.put("approverType", "USER");
        node.put("approverValue", List.of(String.valueOf(approver)));
        node.put("multiMode", "ANY");
        Map<String, Object> branch = new HashMap<>();
        branch.put("name", "全部");
        branch.put("isDefault", true);
        branch.put("conditions", List.of());
        branch.put("nodes", List.of(node));
        Map<String, Object> body = new HashMap<>();
        body.put("skipInitiator", false);
        body.put("skipDuplicate", true);
        body.put("emptyPolicy", "TO_ADMIN");
        body.put("branches", List.of(branch));
        ok(doPut("/api/system/workflow/definitions/" + id, admin, body));
        ok(doPost("/api/system/workflow/definitions/" + id + "/publish", admin, Map.of("remark", "测试")));
    }

    private long start(Long initiator) {
        long bizId = BIZ_ID.incrementAndGet();
        assertThat(workflowApi.start(ItWorkbenchConfig.BIZ, bizId, "WB-" + bizId, "测试单据 WB-" + bizId, Map.of("amount", 100), Map.of(), initiator)
                .isStarted()).isTrue();
        return bizId;
    }

    // ==================== 待办与审批 ====================

    /** WB-TODO-T01 ~ T04、WB-MSG-T01 */
    @Test
    void approvalTodos() throws Exception {
        User approver = user(List.of());
        User initiator = user(List.of());
        publishFlow(approver.id());

        long doc = start(initiator.id());
        assertThat(todoCount(approver)).isEqualTo(1);
        JsonNode todo = todos(approver, "PENDING").get(0);
        assertThat(todo.at("/category").asText()).isEqualTo("APPROVAL");
        assertThat(todo.at("/bizNo").asText()).isEqualTo("WB-" + doc);
        assertThat(todo.at("/manual").asBoolean()).isFalse();
        String taskId = todo.at("/taskId").asText();
        assertThat(todo.at("/todoKey").asText()).isEqualTo("WF_TASK:" + taskId);
        assertError(doPost("/api/workbench/todos/" + todo.at("/id").asText() + "/done", approver.token(), null), "该待办由业务单据自动完成，请到单据中处理");
        JsonNode summary = ok(doGet("/api/workbench/summary", approver.token()));
        assertThat(summary.at("/approvals").asLong()).isEqualTo(1);

        // T02：审批通过后待办消失，“我已处理”出现
        ok(doPost("/api/system/workflow/tasks/" + taskId + "/approve", approver.token(), Map.of("comment", "同意")));
        assertThat(todoCount(approver)).isZero();
        assertThat(findBy(todos(approver, "DONE"), "taskId", taskId).at("/status").asText()).isEqualTo("DONE");

        // WB-MSG-T01：驳回 → 发起人收到“审批结果”消息
        long rejected = start(initiator.id());
        String rt = todos(approver, "PENDING").get(0).at("/taskId").asText();
        ok(doPost("/api/system/workflow/tasks/" + rt + "/reject", approver.token(), Map.of("comment", "价格太低")));
        JsonNode msgs = ok(doGet("/api/workbench/messages?type=APPROVAL_RESULT&pageSize=50", initiator.token())).at("/list");
        assertThat(msgs.size()).isGreaterThanOrEqualTo(2);
        assertThat(ok(doGet("/api/workbench/messages/unread-count", initiator.token())).at("/byType/APPROVAL_RESULT").asLong()).isGreaterThanOrEqualTo(2);
        assertThat(rejected).isPositive();

        // T03：批量通过（转调审批流批量接口）
        List<String> tasks = new ArrayList<>();
        for (int i = 0; i < 3; i++) start(initiator.id());
        for (JsonNode t : todos(approver, "PENDING")) tasks.add(t.at("/taskId").asText());
        assertThat(tasks).hasSize(3);
        JsonNode batch = ok(doPost("/api/system/workflow/tasks/batch-approve", approver.token(), Map.of("taskIds", tasks, "comment", "批量同意")));
        assertThat(batch.size()).isEqualTo(3);
        assertThat(todoCount(approver)).isZero();

        // T04：发起人撤回 → 审批人待办消失
        long withdrawn = start(initiator.id());
        assertThat(todoCount(approver)).isEqualTo(1);
        workflowApi.withdraw(ItWorkbenchConfig.BIZ, withdrawn, initiator.id());
        assertThat(todoCount(approver)).isZero();
        jdbc.update("UPDATE wf_definition SET deleted = 1 WHERE biz_type = ?", ItWorkbenchConfig.BIZ);
    }

    /** WB-TODO-R02 幂等更新、手工完成、R03 用户停用转交、R04 对账 */
    @Test
    void taskTodos() throws Exception {
        String d = dept("待办部门");
        User leader = user(d, "SELF", List.of());
        User worker = user(d, "SELF", List.of());
        jdbc.update("UPDATE sys_org SET leader_user_id = ? WHERE id = ?", leader.id(), Long.valueOf(d));
        caches.clear(SystemCaches.ORG);

        String key = "PMC_SHORT:" + uniq();
        notifyApi.todo(new TodoCreatedEvent(key, List.of(worker.id()), TodoCreatedEvent.Category.TASK, "PMC_SHORT", 1L, "SHORT-1", "催料：螺丝",
                "/pmc/shortage", TodoCreatedEvent.Priority.NORMAL, LocalDateTime.now().minusHours(1)));
        notifyApi.todo(new TodoCreatedEvent(key, List.of(worker.id()), TodoCreatedEvent.Category.TASK, "PMC_SHORT", 1L, "SHORT-1", "催料：螺丝（更新）",
                "/pmc/shortage", TodoCreatedEvent.Priority.HIGH, LocalDateTime.now().minusHours(1)));
        JsonNode list = todos(worker, "PENDING");
        assertThat(list.size()).isEqualTo(1);
        assertThat(list.get(0).at("/title").asText()).isEqualTo("催料：螺丝（更新）");
        assertThat(list.get(0).at("/priority").asText()).isEqualTo("HIGH");
        assertThat(list.get(0).at("/overdue").asBoolean()).isTrue();
        assertThat(ok(doGet("/api/workbench/todos?overdue=true&keyword=SHORT-1", worker.token())).at("/total").asLong()).isEqualTo(1);
        ok(doPost("/api/workbench/todos/" + list.get(0).at("/id").asText() + "/done", worker.token(), null));
        assertThat(todoCount(worker)).isZero();

        // 事件完成（userIds 为空表示所有处理人）
        String key2 = "QC_INSP:" + uniq();
        notifyApi.todo(new TodoCreatedEvent(key2, List.of(worker.id(), leader.id()), TodoCreatedEvent.Category.TASK, "QC_IQC", 2L, "IQC-1", "待检验",
                "/quality/iqc", null, null));
        assertThat(todoCount(leader)).isEqualTo(1);
        notifyApi.done(new TodoDoneEvent(key2, List.of(), TodoDoneEvent.Result.DONE));
        assertThat(todoCount(worker)).isZero();
        assertThat(todoCount(leader)).isZero();

        // R03：用户停用 → 任务类待办转给部门负责人
        String key3 = "ENG_ECN_TASK:" + uniq();
        notifyApi.todo(new TodoCreatedEvent(key3, List.of(worker.id()), TodoCreatedEvent.Category.TASK, "ENG_ECN", 3L, "ECN-1", "ECN 执行确认",
                "/engineering/ecn", null, null));
        ok(doPost("/api/system/users/" + worker.id() + "/disable", admin, null));
        assertThat(findBy(todos(leader, "PENDING"), "todoKey", key3)).as("转交给部门负责人").isNotNull();

        // R04：审批待办对账（审批任务不存在 → 取消）
        notifyApi.todo(new TodoCreatedEvent("WF_TASK:999" + uniq(), List.of(leader.id()), TodoCreatedEvent.Category.APPROVAL, "IT", 4L, "X-1", "丢失的审批",
                "/", null, null));
        long before = todoCount(leader);
        assertThat(todoService.reconcile()).isGreaterThanOrEqualTo(1);
        assertThat(todoCount(leader)).isEqualTo(before - 1);
    }

    // ==================== 消息 ====================

    @Test
    void messages() throws Exception {
        User u = user(List.of());
        notifyApi.message(new MessageSendEvent(List.of(u.id()), MessageSendEvent.Type.TASK_DONE, "任务完成", "IQC-1 已判定", "/quality/iqc", true));
        notifyApi.message(new MessageSendEvent(List.of(u.id()), MessageSendEvent.Type.SYSTEM, "系统通知", "今晚维护", null, false));
        JsonNode unread = ok(doGet("/api/workbench/messages/unread-count", u.token()));
        assertThat(unread.at("/total").asLong()).isEqualTo(2);
        assertThat(unread.at("/byType/TASK_DONE").asLong()).isEqualTo(1);
        JsonNode list = ok(doGet("/api/workbench/messages?read=false", u.token())).at("/list");
        assertThat(list.size()).isEqualTo(2);
        String first = list.get(0).at("/id").asText();
        ok(doPost("/api/workbench/messages/" + first + "/read", u.token(), null));
        assertThat(ok(doGet("/api/workbench/messages/unread-count", u.token())).at("/total").asLong()).isEqualTo(1);
        ok(doPost("/api/workbench/messages/read-all", u.token(), null));
        assertThat(ok(doGet("/api/workbench/messages/unread-count", u.token())).at("/total").asLong()).isZero();
        assertThat(ok(doDelete("/api/workbench/messages/read", u.token())).at("/count").asInt()).isEqualTo(2);
        assertThat(ok(doGet("/api/workbench/messages", u.token())).at("/total").asLong()).isZero();
        // 别人的消息不能操作
        User other = user(List.of());
        notifyApi.message(new MessageSendEvent(List.of(other.id()), MessageSendEvent.Type.REMIND, "提醒", null, null, false));
        String otherMsg = ok(doGet("/api/workbench/messages", other.token())).at("/list/0/id").asText();
        assertError(doPost("/api/workbench/messages/" + otherMsg + "/read", u.token(), null), "消息不存在");
    }

    // ==================== 公告 ====================

    /** WB-MSG-T02 重要公告弹窗、WB-MSG-T03 脚本过滤、按部门发布 */
    @Test
    void notices() throws Exception {
        String d = dept("公告部门");
        User inDept = user(d, "SELF", List.of());
        User outside = user(List.of());
        User normal = user(List.of("wb:notice:manage"));
        assertError(doGet("/api/workbench/notices", inDept.token()), "没有该操作权限");

        Map<String, Object> body = new HashMap<>();
        body.put("title", "国庆放假安排");
        body.put("content", "<p onclick=\"alert(1)\">10 月 1 日至 7 日放假</p><script>alert('x')</script><iframe src=\"http://evil\"></iframe>");
        body.put("scope", "ALL");
        body.put("important", true);
        String id = ok(doPost("/api/workbench/notices", normal.token(), body)).asText();
        JsonNode detail = ok(doGet("/api/workbench/notices/" + id, normal.token()));
        String content = detail.at("/content").asText();
        assertThat(content).contains("10 月 1 日至 7 日放假").doesNotContain("<script").doesNotContain("onclick").doesNotContain("iframe");
        assertThat(detail.at("/header/status").asText()).isEqualTo("DRAFT");
        // 草稿不显示
        assertThat(findBy(ok(doGet("/api/workbench/notices/active", inDept.token())), "id", id)).isNull();
        ok(doPost("/api/workbench/notices/" + id + "/publish", normal.token(), null));
        JsonNode popup = ok(doGet("/api/workbench/notices/active?importantUnread=true", inDept.token()));
        assertThat(findBy(popup, "id", id)).as("重要公告登录弹窗").isNotNull();
        ok(doPost("/api/workbench/notices/" + id + "/read", inDept.token(), null));
        assertThat(findBy(ok(doGet("/api/workbench/notices/active?importantUnread=true", inDept.token())), "id", id)).as("已读后不再弹出").isNull();
        assertThat(findBy(ok(doGet("/api/workbench/notices/active", inDept.token())), "id", id).at("/read").asBoolean()).isTrue();
        JsonNode readers = ok(doGet("/api/workbench/notices/" + id + "/readers", normal.token()));
        assertThat(readers.findValuesAsText("userId")).contains(String.valueOf(inDept.id()));
        JsonNode row = findBy(ok(doGet("/api/workbench/notices?keyword=国庆放假安排&pageSize=50", normal.token())).at("/list"), "id", id);
        assertThat(row.at("/readCount").asLong()).isGreaterThanOrEqualTo(1);
        assertThat(row.at("/targetCount").asLong()).isGreaterThan(row.at("/readCount").asLong());
        assertError(doDelete("/api/workbench/notices/" + id, normal.token()), "当前状态【已发布】不能删除");
        ok(doPost("/api/workbench/notices/" + id + "/withdraw", normal.token(), null));
        assertThat(findBy(ok(doGet("/api/workbench/notices/active", inDept.token())), "id", id)).isNull();

        // 按部门发布：部门外用户看不到
        body.put("title", "部门通知");
        body.put("important", false);
        body.put("scope", "DEPT");
        body.put("deptIds", List.of());
        assertError(doPost("/api/workbench/notices", normal.token(), body), "按部门发布时请选择部门");
        body.put("deptIds", List.of(d));
        body.put("expireAt", LocalDateTime.now().minusDays(1).format(DT));
        body.put("publishAt", LocalDateTime.now().minusDays(2).format(DT));
        String deptNotice = ok(doPost("/api/workbench/notices", normal.token(), body)).asText();
        ok(doPost("/api/workbench/notices/" + deptNotice + "/publish", normal.token(), null));
        assertThat(findBy(ok(doGet("/api/workbench/notices/active", inDept.token())), "id", deptNotice)).as("已过期").isNull();
        body.remove("expireAt");
        ok(doPut("/api/workbench/notices/" + deptNotice, normal.token(), body));
        assertThat(findBy(ok(doGet("/api/workbench/notices/active", inDept.token())), "id", deptNotice)).isNotNull();
        assertThat(findBy(ok(doGet("/api/workbench/notices/active", outside.token())), "id", deptNotice)).isNull();
        assertThat(findBy(ok(doGet("/api/workbench/notices?pageSize=50", normal.token())).at("/list"), "id", deptNotice).at("/targetCount").asLong()).isEqualTo(1);
    }

    // ==================== 预警 ====================

    /** WB-ALT-R01 ~ R04、T02 ~ T04 */
    @Test
    void alerts() throws Exception {
        User planner = user(List.of());
        User pmcManager = user(List.of());
        User stranger = user(List.of());
        String key = "DELIVERY_DELAY:" + uniq();
        raise(key, AlertRaisedEvent.Level.WARNING, List.of(planner.id()), "SO-1 预计延期 5 天");
        raise(key, AlertRaisedEvent.Level.WARNING, List.of(planner.id()), "SO-1 预计延期 6 天");
        JsonNode list = ok(doGet("/api/workbench/alerts", planner.token())).at("/list");
        JsonNode a = findBy(list, "alertKey", key);
        assertThat(a.at("/title").asText()).isEqualTo("SO-1 预计延期 6 天");
        assertThat(ok(doGet("/api/workbench/alerts", planner.token())).at("/total").asLong()).isEqualTo(1);
        assertThat(ok(doGet("/api/workbench/alerts/stats", planner.token())).at("/warning").asLong()).isEqualTo(1);
        assertThat(ok(doGet("/api/workbench/alerts", stranger.token())).at("/total").asLong()).as("R04 只看给自己的").isZero();

        // T03：级别升为严重，新增接收人 PMC 主管，并发站内消息
        raise(key, AlertRaisedEvent.Level.CRITICAL, List.of(planner.id(), pmcManager.id()), "SO-1 预计延期 9 天");
        a = findBy(ok(doGet("/api/workbench/alerts", pmcManager.token())).at("/list"), "alertKey", key);
        assertThat(a.at("/level").asText()).isEqualTo("CRITICAL");
        JsonNode msg = ok(doGet("/api/workbench/messages?type=REMIND", pmcManager.token())).at("/list/0");
        assertThat(msg.at("/title").asText()).isEqualTo("【严重预警】SO-1 预计延期 9 天");

        // T02：条件消除 → 已消除
        notifyApi.resolve(key);
        assertThat(ok(doGet("/api/workbench/alerts", planner.token())).at("/total").asLong()).isZero();
        assertThat(findBy(ok(doGet("/api/workbench/alerts?statuses=RESOLVED", planner.token())).at("/list"), "alertKey", key)).isNotNull();

        // 再次发生 → 重新打开；T04：忽略后同级别不再提醒，级别升高重新打开
        raise(key, AlertRaisedEvent.Level.WARNING, List.of(planner.id()), "SO-1 预计延期 5 天");
        String id = findBy(ok(doGet("/api/workbench/alerts", planner.token())).at("/list"), "alertKey", key).at("/id").asText();
        assertError(doPost("/api/workbench/alerts/" + id + "/ignore", planner.token(), Map.of("remark", " ")), "请填写忽略原因");
        assertError(doPost("/api/workbench/alerts/" + id + "/ignore", stranger.token(), Map.of("remark", "x")), "预警不存在");
        ok(doPost("/api/workbench/alerts/" + id + "/ignore", planner.token(), Map.of("remark", "客户同意延期")));
        raise(key, AlertRaisedEvent.Level.WARNING, List.of(planner.id()), "SO-1 预计延期 5 天");
        assertThat(ok(doGet("/api/workbench/alerts", planner.token())).at("/total").asLong()).as("忽略期内同级别不提醒").isZero();
        raise(key, AlertRaisedEvent.Level.CRITICAL, List.of(planner.id()), "SO-1 预计延期 12 天");
        a = findBy(ok(doGet("/api/workbench/alerts", planner.token())).at("/list"), "alertKey", key);
        assertThat(a.at("/status").asText()).isEqualTo("OPEN");
        ok(doPost("/api/workbench/alerts/" + a.at("/id").asText() + "/handle", planner.token(), Map.of("remark", "已安排加班")));
        JsonNode handled = findBy(ok(doGet("/api/workbench/alerts?statuses=HANDLED", planner.token())).at("/list"), "alertKey", key);
        assertThat(handled.at("/handleRemark").asText()).isEqualTo("已安排加班");
        assertThat(handled.at("/handledByName").asText()).isNotBlank();

        // 按权限解析接收人
        String permKey = "STOCK_LOW:" + uniq();
        User buyer = user(List.of("pur:order:query"));
        notifyApi.alert(new AlertRaisedEvent(permKey, "STOCK_LOW", AlertRaisedEvent.Level.WARNING, List.of(), "pur:order:query", "MATERIAL", 1L,
                "螺丝低于安全库存", "可用 700 < 安全库存 800", "/inventory/stock"));
        assertThat(findBy(ok(doGet("/api/workbench/alerts", buyer.token())).at("/list"), "alertKey", permKey)).isNotNull();
    }

    private void raise(String key, AlertRaisedEvent.Level level, List<Long> users, String title) {
        notifyApi.alert(new AlertRaisedEvent(key, "DELIVERY_DELAY", level, users, null, "SAL_ORDER", 1L, title, title, "/pmc/alert"));
    }

    // ==================== 首页 ====================

    /** WB-HOME-T01 ~ T04、R01 */
    @Test
    void homeCards() throws Exception {
        User sales = user(List.of("sales:order:query", "shp:shipment:query", "fin:receivable:query"));
        List<String> codes = ok(doGet("/api/workbench/cards", sales.token())).findValuesAsText("code");
        assertThat(codes).contains("SAL_ORDER_MONTH", "SHP_SHIPMENT_MONTH", "FIN_RECEIVED_MONTH", "FIN_AR_OVERDUE", "SAL_ORDER_OPEN")
                .doesNotContain("INV_IN_PENDING", "QC_IQC_PENDING");
        // R01：业务员（仅本人数据）的本月接单额只含自己的订单
        assertThat(ok(doGet("/api/workbench/cards/SAL_ORDER_MONTH/data", sales.token())).at("/value").decimalValue()).isEqualByComparingTo("0");

        User keeper = user(List.of("inv:in:query", "inv:out:query"));
        List<String> kc = ok(doGet("/api/workbench/cards", keeper.token())).findValuesAsText("code");
        assertThat(kc).contains("INV_IN_PENDING", "INV_OUT_PENDING").doesNotContain("SAL_ORDER_MONTH", "SHP_SHIPMENT_MONTH");
        assertError(doGet("/api/workbench/cards/SAL_ORDER_MONTH/data", keeper.token()), "卡片「SAL_ORDER_MONTH」不存在或无权限");

        // T04：管理员全部卡片加载，故障卡片单独失败
        for (JsonNode c : ok(doGet("/api/workbench/cards", admin))) {
            String code = c.at("/code").asText();
            JsonNode r = doGet("/api/workbench/cards/" + code + "/data?refresh=true", admin);
            if (ItWorkbenchConfig.BROKEN_CARD.equals(code)) assertThat(r.at("/msg").asText()).isEqualTo("卡片数据加载失败：测试故障卡片");
            else assertThat(r.at("/code").asInt()).as("卡片 %s：%s", code, r).isZero();
        }
        JsonNode trend = ok(doGet("/api/workbench/cards/SHP_SHIPMENT_TREND/data", admin));
        assertThat(trend.at("/series").size()).isEqualTo(6);

        // T03：隐藏“在手订单”并拖到最后，刷新后保持
        List<Map<String, Object>> items = new ArrayList<>();
        for (String code : codes) if (!"SAL_ORDER_OPEN".equals(code)) items.add(Map.of("code", code, "visible", true));
        items.add(Map.of("code", "SAL_ORDER_OPEN", "visible", false));
        ok(doPut("/api/workbench/layout", sales.token(), Map.of("items", items)));
        JsonNode cards = ok(doGet("/api/workbench/cards", sales.token()));
        JsonNode last = cards.get(cards.size() - 1);
        assertThat(last.at("/code").asText()).isEqualTo("SAL_ORDER_OPEN");
        assertThat(last.at("/visible").asBoolean()).isFalse();
        ok(doPost("/api/workbench/layout/reset", sales.token(), null));
        assertThat(findBy(ok(doGet("/api/workbench/cards", sales.token())), "code", "SAL_ORDER_OPEN").at("/visible").asBoolean()).isTrue();

        // 快捷入口
        ok(doPut("/api/workbench/shortcuts", sales.token(), Map.of("routes", List.of("/sales/order", "/inventory/stock"))));
        assertThat(ok(doGet("/api/workbench/shortcuts", sales.token())).get(1).asText()).isEqualTo("/inventory/stock");
        List<String> many = new ArrayList<>();
        for (int i = 0; i < 13; i++) many.add("/r" + i);
        assertError(doPut("/api/workbench/shortcuts", sales.token(), Map.of("routes", many)), "快捷入口最多 12 个");

        JsonNode summary = ok(doGet("/api/workbench/summary", sales.token()));
        assertThat(summary.at("/pollSeconds").asInt()).isEqualTo(60);
    }
}
