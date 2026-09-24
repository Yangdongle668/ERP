package com.erp.it.system;

import com.erp.module.system.api.workflow.ApprovalCompletedEvent;
import com.erp.module.system.api.workflow.StartResult;
import com.erp.module.system.api.workflow.WorkflowApi;
import com.erp.module.system.service.support.SystemCaches;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 审批流（01-08），验收用例 SYS-WF-T01～T14 */
class WorkflowIntegrationTest extends SystemTestSupport {

    private static final AtomicLong BIZ_ID = new AtomicLong(System.currentTimeMillis());

    @Autowired
    private WorkflowApi workflowApi;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private SystemCaches caches;

    /** 部门负责人 L、总经理 G（角色）、发起人 A、会签 B/C */
    private String dept;
    private Long leader;
    private Long gm;
    private Long initiator;
    private String leaderToken;
    private String gmToken;
    private String initiatorToken;
    private String gmRole;
    private String basicRole;

    @BeforeEach
    void setUp() throws Exception {
        ok(doPost("/api/system/workflow/definitions/draft?bizType=" + ItApprovalConfig.BIZ, admin, null));
        // 每个用例前把流程恢复为“无 ACTIVE 版本”
        jdbc.update("UPDATE wf_definition SET deleted = 1 WHERE biz_type = ?", ItApprovalConfig.BIZ);
        jdbc.update("UPDATE wf_instance SET status = 'TERMINATED' WHERE biz_type = ? AND status = 'RUNNING'", ItApprovalConfig.BIZ);
        ItApprovalConfig.EVENTS.clear();
        ItApprovalConfig.FAIL_ON_APPROVE.clear();

        dept = createDept(HQ, "审批部门" + uniq());
        basicRole = createRole("SELF", List.of());
        String basic = basicRole;
        gmRole = createRole("SELF", List.of());
        leader = Long.valueOf(createUser("wl" + uniq(), dept, List.of(basic), false));
        gm = Long.valueOf(createUser("wg" + uniq(), dept, List.of(gmRole), false));
        initiator = Long.valueOf(createUser("wa" + uniq(), dept, List.of(basic), false));
        setLeader(dept, leader);
        leaderToken = loginId(leader);
        gmToken = loginId(gm);
        initiatorToken = loginId(initiator);
    }

    private void setLeader(String deptId, Long userId) {
        jdbc.update("UPDATE sys_org SET leader_user_id = ? WHERE id = ?", userId, Long.valueOf(deptId));
        caches.clear(SystemCaches.ORG);
    }

    private String loginId(Long userId) throws Exception {
        String username = jdbc.queryForObject("SELECT username FROM sys_user WHERE id = ?", String.class, userId);
        return login(username, PASSWORD);
    }

    // ==================== 流程配置辅助 ====================

    private static Map<String, Object> node(String name, String type, Object value, String mode) {
        Map<String, Object> n = new HashMap<>();
        n.put("name", name);
        n.put("approverType", type);
        n.put("approverValue", value);
        n.put("multiMode", mode);
        return n;
    }

    private static Map<String, Object> branch(String name, boolean isDefault, List<Map<String, Object>> conds, List<Map<String, Object>> nodes) {
        Map<String, Object> b = new HashMap<>();
        b.put("name", name);
        b.put("isDefault", isDefault);
        b.put("conditions", conds);
        b.put("nodes", nodes);
        return b;
    }

    private String saveDraft(List<Map<String, Object>> branches, boolean skipInitiator) throws Exception {
        String id = ok(doPost("/api/system/workflow/definitions/draft?bizType=" + ItApprovalConfig.BIZ, admin, null)).at("/id").asText();
        Map<String, Object> body = new HashMap<>();
        body.put("skipInitiator", skipInitiator);
        body.put("skipDuplicate", true);
        body.put("emptyPolicy", "TO_ADMIN");
        body.put("branches", branches);
        ok(doPut("/api/system/workflow/definitions/" + id, admin, body));
        return id;
    }

    private void publish(List<Map<String, Object>> branches, boolean skipInitiator) throws Exception {
        String id = saveDraft(branches, skipInitiator);
        ok(doPost("/api/system/workflow/definitions/" + id + "/publish", admin, Map.of("remark", "测试")));
    }

    /** T02 的流程：金额 > 500000 → 部门负责人 → 总经理(角色)；其他 → 部门负责人 */
    private void publishStandard() throws Exception {
        publish(List.of(
                branch("大额订单", false, List.of(Map.of("field", "amount", "op", "GT", "value", 500000)),
                        List.of(node("部门负责人审批", "DEPT_LEADER", null, "ANY"), node("总经理审批", "ROLE", Map.of("roleId", gmRole, "sameCompany", true), "ANY"))),
                branch("其他情况", true, List.of(), List.of(node("部门负责人审批", "DEPT_LEADER", null, "ANY")))), true);
    }

    private StartResult start(long bizId, int amount, Long by) {
        return workflowApi.start(ItApprovalConfig.BIZ, bizId, "IT-" + bizId, null, Map.of("amount", amount, "deptId", Long.valueOf(dept)), Map.of(), by);
    }

    private JsonNode byBiz(long bizId, String token) throws Exception {
        return ok(doGet("/api/system/workflow/instances/by-biz?bizType=" + ItApprovalConfig.BIZ + "&bizId=" + bizId, token));
    }

    private String myTask(long bizId, String token) throws Exception {
        JsonNode r = byBiz(bizId, token);
        assertThat(r.has("myPendingTaskId")).as("当前用户应有待办").isTrue();
        return r.at("/myPendingTaskId").asText();
    }

    private List<ApprovalCompletedEvent.Result> results(long bizId) {
        List<ApprovalCompletedEvent.Result> r = new ArrayList<>();
        ItApprovalConfig.EVENTS.stream().filter(e -> e.getBizId() == bizId).forEach(e -> r.add(e.getResult()));
        return r;
    }

    // ==================== 用例 ====================

    @Test
    void notConfigured_T01() {
        assertThat(start(BIZ_ID.incrementAndGet(), 100, initiator).status()).isEqualTo(StartResult.Status.NOT_REQUIRED);
    }

    @Test
    void bigOrderTwoNodes_T02_andSmallOrderOneNode_T03() throws Exception {
        publishStandard();
        long big = BIZ_ID.incrementAndGet();
        assertThat(start(big, 600000, initiator).isStarted()).isTrue();
        assertThat(workflowApi.isRunning(ItApprovalConfig.BIZ, big)).isTrue();
        assertThat(workflowApi.getRunning(ItApprovalConfig.BIZ, big).orElseThrow().nodeName()).isEqualTo("部门负责人审批");

        ok(doPost("/api/system/workflow/tasks/" + myTask(big, leaderToken) + "/approve", leaderToken, Map.of("comment", "同意")));
        assertThat(results(big)).isEmpty();
        ok(doPost("/api/system/workflow/tasks/" + myTask(big, gmToken) + "/approve", gmToken, Map.of()));
        assertThat(results(big)).containsExactly(ApprovalCompletedEvent.Result.APPROVED);
        JsonNode rec = byBiz(big, initiatorToken).at("/instances/0");
        assertThat(rec.at("/status").asText()).isEqualTo("APPROVED");
        assertThat(rec.at("/tasks")).hasSize(2);
        assertThat(rec.at("/tasks/0/comment").asText()).isEqualTo("同意");

        long small = BIZ_ID.incrementAndGet();
        start(small, 100000, initiator);
        ok(doPost("/api/system/workflow/tasks/" + myTask(small, leaderToken) + "/approve", leaderToken, Map.of()));
        assertThat(results(small)).containsExactly(ApprovalCompletedEvent.Result.APPROVED);
    }

    @Test
    void initiatorIsLeaderAutoPass_T04() throws Exception {
        publishStandard();
        long id = BIZ_ID.incrementAndGet();
        StartResult r = start(id, 100000, leader);
        assertThat(r.status()).isEqualTo(StartResult.Status.AUTO_APPROVED);
        JsonNode task = byBiz(id, leaderToken).at("/instances/0/tasks/0");
        assertThat(task.at("/status").asText()).isEqualTo("AUTO_PASSED");
        assertThat(task.at("/autoReason").asText()).isEqualTo("发起人本人");
    }

    @Test
    void rejectNeedsComment_T05_T06() throws Exception {
        publishStandard();
        long id = BIZ_ID.incrementAndGet();
        start(id, 100, initiator);
        String task = myTask(id, leaderToken);
        assertError(doPost("/api/system/workflow/tasks/" + task + "/reject", leaderToken, Map.of("comment", " ")), "请填写驳回意见");
        ok(doPost("/api/system/workflow/tasks/" + task + "/reject", leaderToken, Map.of("comment", "价格太低")));
        assertThat(results(id)).containsExactly(ApprovalCompletedEvent.Result.REJECTED);
        JsonNode ins = byBiz(id, initiatorToken).at("/instances/0");
        assertThat(ins.at("/status").asText()).isEqualTo("REJECTED");
        assertThat(ins.at("/resultComment").asText()).isEqualTo("价格太低");
        // 驳回后可重新提交（新实例）
        assertThat(start(id, 100, initiator).isStarted()).isTrue();
        assertThat(byBiz(id, initiatorToken).at("/instances")).hasSize(2);
    }

    @Test
    void withdraw_T07_andDuplicateSubmit_R01() throws Exception {
        publishStandard();
        long id = BIZ_ID.incrementAndGet();
        start(id, 100, initiator);
        assertThatThrownBy(() -> start(id, 100, initiator)).hasMessage("该单据正在审批中，不能重复提交");
        JsonNode r = byBiz(id, initiatorToken);
        assertThat(r.at("/canWithdraw").asBoolean()).isTrue();
        String instanceId = r.at("/runningInstanceId").asText();
        assertError(doPost("/api/system/workflow/instances/" + instanceId + "/withdraw", leaderToken, null), "只有发起人可以撤回");
        ok(doPost("/api/system/workflow/instances/" + instanceId + "/withdraw", initiatorToken, null));
        assertThat(results(id)).containsExactly(ApprovalCompletedEvent.Result.WITHDRAWN);
        assertThat(byBiz(id, leaderToken).has("myPendingTaskId")).isFalse();
        assertError(doPost("/api/system/workflow/instances/" + instanceId + "/withdraw", initiatorToken, null), "审批已结束，不能撤回");
    }

    @Test
    void countersignAndOrSign_T08_T09() throws Exception {
        Long b = Long.valueOf(createUser("wb" + uniq(), dept, List.of(basicRole), false));
        Long c = Long.valueOf(createUser("wc" + uniq(), dept, List.of(basicRole), false));
        String bToken = loginId(b);
        String cToken = loginId(c);
        publish(List.of(
                branch("加急", false, List.of(Map.of("field", "urgent", "op", "EQ", "value", true)),
                        List.of(node("或签", "USER", List.of(String.valueOf(b), String.valueOf(c)), "ANY"))),
                branch("其他情况", true, List.of(), List.of(node("会签", "USER", List.of(String.valueOf(b), String.valueOf(c)), "ALL")))), true);

        long all = BIZ_ID.incrementAndGet();
        start(all, 1, initiator);
        ok(doPost("/api/system/workflow/tasks/" + myTask(all, bToken) + "/approve", bToken, Map.of()));
        assertThat(results(all)).isEmpty();
        ok(doPost("/api/system/workflow/tasks/" + myTask(all, cToken) + "/approve", cToken, Map.of()));
        assertThat(results(all)).containsExactly(ApprovalCompletedEvent.Result.APPROVED);

        long any = BIZ_ID.incrementAndGet();
        workflowApi.start(ItApprovalConfig.BIZ, any, "IT-" + any, null, Map.of("urgent", true), Map.of(), initiator);
        ok(doPost("/api/system/workflow/tasks/" + myTask(any, bToken) + "/approve", bToken, Map.of()));
        assertThat(results(any)).containsExactly(ApprovalCompletedEvent.Result.APPROVED);
        assertThat(byBiz(any, cToken).at("/instances/0/tasks/1/status").asText()).isEqualTo("CANCELED");
    }

    @Test
    void runningInstanceKeepsOldVersion_T10() throws Exception {
        publishStandard();
        long old = BIZ_ID.incrementAndGet();
        start(old, 600000, initiator);
        // 新版本去掉总经理节点
        publish(List.of(branch("其他情况", true, List.of(), List.of(node("部门负责人审批", "DEPT_LEADER", null, "ANY")))), true);
        ok(doPost("/api/system/workflow/tasks/" + myTask(old, leaderToken) + "/approve", leaderToken, Map.of()));
        assertThat(results(old)).isEmpty();
        assertThat(byBiz(old, gmToken).has("myPendingTaskId")).isTrue();

        long fresh = BIZ_ID.incrementAndGet();
        start(fresh, 600000, initiator);
        ok(doPost("/api/system/workflow/tasks/" + myTask(fresh, leaderToken) + "/approve", leaderToken, Map.of()));
        assertThat(results(fresh)).containsExactly(ApprovalCompletedEvent.Result.APPROVED);
        JsonNode history = ok(doGet("/api/system/workflow/definitions/history?bizType=" + ItApprovalConfig.BIZ, admin));
        assertThat(history.at("/0/status").asText()).isEqualTo("ACTIVE");
        assertThat(history.at("/1/status").asText()).isEqualTo("ARCHIVED");
    }

    @Test
    void businessFailureRollsBack_T11() throws Exception {
        publishStandard();
        long id = BIZ_ID.incrementAndGet();
        start(id, 100, initiator);
        ItApprovalConfig.FAIL_ON_APPROVE.add(id);
        String task = myTask(id, leaderToken);
        assertError(doPost("/api/system/workflow/tasks/" + task + "/approve", leaderToken, Map.of()), "库存不足");
        // 任务仍待处理，实例仍审批中
        assertThat(myTask(id, leaderToken)).isEqualTo(task);
        assertThat(workflowApi.isRunning(ItApprovalConfig.BIZ, id)).isTrue();
        ItApprovalConfig.FAIL_ON_APPROVE.remove(id);
        ok(doPost("/api/system/workflow/tasks/" + task + "/approve", leaderToken, Map.of()));
        assertThat(results(id)).containsExactly(ApprovalCompletedEvent.Result.APPROVED);
    }

    @Test
    void disabledApproverTransferredToLeader_T12() throws Exception {
        Long b = Long.valueOf(createUser("wd" + uniq(), dept, List.of(basicRole), false));
        publish(List.of(branch("其他情况", true, List.of(), List.of(node("指定人审批", "USER", List.of(String.valueOf(b)), "ANY")))), true);
        long id = BIZ_ID.incrementAndGet();
        start(id, 1, initiator);
        ok(doPost("/api/system/users/" + b + "/disable", admin, null));
        JsonNode r = byBiz(id, leaderToken);
        assertThat(r.has("myPendingTaskId")).isTrue();
        JsonNode first = r.at("/instances/0/tasks/0");
        assertThat(first.at("/status").asText()).isEqualTo("TRANSFERRED");
        assertThat(first.at("/autoReason").asText()).isEqualTo("系统转交：原处理人已停用");
    }

    @Test
    void doubleSubmitOnlyFirstWins_T13() throws Exception {
        publishStandard();
        long id = BIZ_ID.incrementAndGet();
        start(id, 1, initiator);
        String task = myTask(id, leaderToken);
        ok(doPost("/api/system/workflow/tasks/" + task + "/approve", leaderToken, Map.of()));
        assertError(doPost("/api/system/workflow/tasks/" + task + "/approve", leaderToken, Map.of()), "你不是该任务的处理人或任务已处理");
        // 别人不能处理我的任务（R04）
        long id2 = BIZ_ID.incrementAndGet();
        start(id2, 1, initiator);
        assertError(doPost("/api/system/workflow/tasks/" + myTask(id2, leaderToken) + "/approve", gmToken, Map.of()), "你不是该任务的处理人或任务已处理");
    }

    @Test
    void publishValidation_T14_R05() throws Exception {
        String id = saveDraft(List.of(
                branch("大额", false, List.of(Map.of("field", "amount", "op", "GT", "value", 1)), List.of()),
                branch("其他情况", true, List.of(), List.of(node("部门负责人审批", "DEPT_LEADER", null, "ANY")))), true);
        assertError(doPost("/api/system/workflow/definitions/" + id + "/publish", admin, Map.of()), "分支「大额」至少需要一个审批节点");

        saveDraft(List.of(
                branch("大额", false, List.of(Map.of("field", "amount", "op", "GT")), List.of(node("部门负责人审批", "DEPT_LEADER", null, "ANY"))),
                branch("其他情况", true, List.of(), List.of(node("部门负责人审批", "DEPT_LEADER", null, "ANY")))), true);
        assertError(doPost("/api/system/workflow/definitions/" + id + "/publish", admin, Map.of()), "分支「大额」的条件不完整");

        Long b = Long.valueOf(createUser("we" + uniq(), dept, List.of(basicRole), false));
        ok(doPost("/api/system/users/" + b + "/disable", admin, null));
        saveDraft(List.of(branch("其他情况", true, List.of(), List.of(node("财务审核", "USER", List.of(String.valueOf(b)), "ANY")))), true);
        assertThat(doPost("/api/system/workflow/definitions/" + id + "/publish", admin, Map.of()).at("/msg").asText())
                .startsWith("节点「财务审核」的审批人「").endsWith("」已停用");

        // 放弃草稿
        ok(doDelete("/api/system/workflow/definitions/" + id, admin));
        assertThat(ok(doGet("/api/system/workflow/definitions?bizType=" + ItApprovalConfig.BIZ, admin)).has("draft")).isFalse();
    }

    @Test
    void transferTerminateMonitorAndBizTypes() throws Exception {
        publishStandard();
        long id = BIZ_ID.incrementAndGet();
        start(id, 1, initiator);
        String task = myTask(id, leaderToken);
        assertError(doPost("/api/system/workflow/tasks/" + task + "/transfer", leaderToken, Map.of("toUserId", leader)), "不能转交给自己");
        ok(doPost("/api/system/workflow/tasks/" + task + "/transfer", leaderToken, Map.of("toUserId", gm, "comment", "请代审")));
        String gmTask = myTask(id, gmToken);

        JsonNode page = ok(doGet("/api/system/workflow/instances?status=RUNNING&bizType=" + ItApprovalConfig.BIZ + "&bizNo=IT-" + id + "&pageNo=1&pageSize=10", admin));
        assertThat(page.at("/list/0/assigneeNames/0").asText()).startsWith("用户wg");
        assertThat(page.at("/list/0/detailRoute").asText()).isEqualTo("/it/order/" + id);

        JsonNode my = ok(doGet("/api/system/workflow/tasks/my?status=PENDING", gmToken));
        assertThat(my.at("/list/0/taskId").asText()).isEqualTo(gmTask);

        String instanceId = page.at("/list/0/id").asText();
        assertError(doPost("/api/system/workflow/instances/" + instanceId + "/terminate", admin, Map.of("reason", "")), "请填写终止原因");
        ok(doPost("/api/system/workflow/instances/" + instanceId + "/terminate", admin, Map.of("reason", "客户取消")));
        assertThat(results(id)).containsExactly(ApprovalCompletedEvent.Result.TERMINATED);

        JsonNode types = ok(doGet("/api/system/workflow/biz-types", admin));
        JsonNode it = null;
        for (JsonNode t : types) if (t.at("/bizType").asText().equals(ItApprovalConfig.BIZ)) it = t;
        assertThat(it).isNotNull();
        assertThat(it.at("/configStatus").asText()).isEqualTo("ENABLED");
        assertThat(it.at("/fields")).hasSize(5);

        // 关闭审批 → 提交即通过
        ok(doPost("/api/system/workflow/biz-types/" + ItApprovalConfig.BIZ + "/enabled", admin, Map.of("enabled", false)));
        assertThat(start(BIZ_ID.incrementAndGet(), 1, initiator).status()).isEqualTo(StartResult.Status.NOT_REQUIRED);
    }

    @Test
    void batchApprove() throws Exception {
        publishStandard();
        long a = BIZ_ID.incrementAndGet();
        long b = BIZ_ID.incrementAndGet();
        start(a, 1, initiator);
        start(b, 1, initiator);
        ItApprovalConfig.FAIL_ON_APPROVE.add(b);
        JsonNode res = ok(doPost("/api/system/workflow/tasks/batch-approve", leaderToken,
                Map.of("taskIds", List.of(myTask(a, leaderToken), myTask(b, leaderToken)), "comment", "批量")));
        assertThat(res.at("/0/success").asBoolean()).isTrue();
        assertThat(res.at("/1/success").asBoolean()).isFalse();
        assertThat(res.at("/1/message").asText()).isEqualTo("库存不足");
    }
}
