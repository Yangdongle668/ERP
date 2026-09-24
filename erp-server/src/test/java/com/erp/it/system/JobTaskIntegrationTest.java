package com.erp.it.system;

import com.erp.common.exception.BizException;
import com.erp.framework.security.LoginUser;
import com.erp.framework.security.UserDataScope;
import com.erp.module.system.api.SystemErrorCodes;
import com.erp.module.system.api.task.AsyncTaskApi;
import com.erp.module.system.service.support.SystemCaches;
import com.erp.module.system.service.task.AsyncTaskService;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;

/** 定时任务（01-12 第 3 节）与任务中心（01-12 第 2 节） */
class JobTaskIntegrationTest extends SystemTestSupport {

    @Autowired
    private AsyncTaskApi asyncTaskApi;
    @Autowired
    private AsyncTaskService taskService;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private SystemCaches caches;

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    // ==================== 定时任务 ====================

    @Test
    void jobsRegisteredFromErpJob() throws Exception {
        JsonNode jobs = ok(doGet("/api/system/jobs", admin));
        List<String> codes = new ArrayList<>();
        jobs.forEach(j -> codes.add(j.at("/code").asText()));
        assertThat(codes).contains("SYS_LOG_CLEANUP", "SYS_FILE_CLEANUP", "SYS_TASK_CLEANUP", "SYS_RATE_REMIND");
        JsonNode logCleanup = find(jobs, j -> j.at("/code").asText().equals("SYS_LOG_CLEANUP"));
        assertThat(logCleanup.at("/cron").asText()).isEqualTo("0 0 2 * * ?");
        assertThat(logCleanup.at("/moduleName").asText()).isEqualTo("系统管理");
        assertThat(logCleanup.at("/nextRunAt").asText()).endsWith("02:00:00");
    }

    @Test
    void cronPreviewAndValidation_T01_R01() throws Exception {
        JsonNode times = ok(doGet("/api/system/jobs/cron-preview?cron=0 0 3 * * ?", admin));
        assertThat(times).hasSize(5);
        times.forEach(t -> assertThat(t.asText()).endsWith("03:00:00"));
        assertError(doGet("/api/system/jobs/cron-preview?cron=abc", admin), "Cron 表达式不正确");
        assertError(doPut("/api/system/jobs/SYS_FILE_CLEANUP/cron", admin, Map.of("cron", "0 0 25 * * ?")), "Cron 表达式不正确");
    }

    @Test
    void updateCronEnableAndReset() throws Exception {
        ok(doPut("/api/system/jobs/SYS_TASK_CLEANUP/cron", admin, Map.of("cron", "0 15 4 * * ?")));
        JsonNode j = job("SYS_TASK_CLEANUP");
        assertThat(j.at("/cron").asText()).isEqualTo("0 15 4 * * ?");
        assertThat(j.at("/nextRunAt").asText()).endsWith("04:15:00");

        ok(doPost("/api/system/jobs/SYS_TASK_CLEANUP/disable", admin, null));
        assertThat(job("SYS_TASK_CLEANUP").at("/enabled").asBoolean()).isFalse();
        assertThat(job("SYS_TASK_CLEANUP").has("nextRunAt")).isFalse();
        ok(doPost("/api/system/jobs/SYS_TASK_CLEANUP/enable", admin, null));

        ok(doPost("/api/system/jobs/SYS_TASK_CLEANUP/reset-cron", admin, null));
        assertThat(job("SYS_TASK_CLEANUP").at("/cron").asText()).isEqualTo("0 40 3 * * ?");
    }

    @Test
    void runNowWritesLog_andRejectsWhileRunning_R03() throws Exception {
        ok(doPost("/api/system/jobs/SYS_FILE_CLEANUP/run", admin, null));
        JsonNode entry = null;
        for (int i = 0; i < 50 && entry == null; i++) {
            JsonNode logs = ok(doGet("/api/system/jobs/SYS_FILE_CLEANUP/logs?pageNo=1&pageSize=5", admin));
            if (logs.at("/list").size() > 0 && !"RUNNING".equals(logs.at("/list/0/result").asText())) entry = logs.at("/list/0");
            else Thread.sleep(100);
        }
        assertThat(entry).isNotNull();
        assertThat(entry.at("/result").asText()).isEqualTo("SUCCESS");
        assertThat(entry.at("/triggerType").asText()).isEqualTo("MANUAL");
        assertThat(entry.at("/message").asText()).startsWith("清理附件");
        assertThat(entry.at("/operatorName").asText()).isNotBlank();
        assertThat(job("SYS_FILE_CLEANUP").at("/lastResult").asText()).isEqualTo("SUCCESS");

        // 另一实例持有锁
        jdbc.update("UPDATE sys_job SET locked_until = ? WHERE code = 'SYS_FILE_CLEANUP'", LocalDateTime.now().plusMinutes(5));
        try {
            assertThat(job("SYS_FILE_CLEANUP").at("/running").asBoolean()).isTrue();
            assertError(doPost("/api/system/jobs/SYS_FILE_CLEANUP/run", admin, null), "任务正在执行中");
        } finally {
            jdbc.update("UPDATE sys_job SET locked_until = NULL WHERE code = 'SYS_FILE_CLEANUP'");
        }
    }

    // ==================== 任务中心 ====================

    private void actAsAdmin() {
        LoginUser u = new LoginUser(1L, "admin", "超级管理员", 100L, 100L, Set.of(LoginUser.ALL_PERMISSION), 0, false, UserDataScope.ALL);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(u, null, List.of()));
    }

    @Test
    void taskSuccessWithResultFile() throws Exception {
        actAsAdmin();
        Long id = asyncTaskApi.submit(AsyncTaskApi.TYPE_EXPORT, "导出测试", "system", ctx -> {
            // 后台线程以提交人身份执行
            assertThat(com.erp.framework.security.SecurityUtils.getLoginUserIdOrNull()).isEqualTo(1L);
            ctx.progress(30);
            ctx.resultFile("结果.xlsx", null, new byte[]{1, 2, 3});
            ctx.resultMessage("成功导出 3 行");
        });
        JsonNode t = waitTask(id, s -> !s.equals("WAITING") && !s.equals("RUNNING"));
        assertThat(t.at("/status").asText()).isEqualTo("SUCCESS");
        assertThat(t.at("/progress").asInt()).isEqualTo(100);
        assertThat(t.at("/resultMessage").asText()).isEqualTo("成功导出 3 行");
        String fileId = t.at("/resultFileId").asText();
        assertThat(mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/system/files/" + fileId + "/download")
                .header("Authorization", admin)).andReturn().getResponse().getContentAsByteArray()).containsExactly(1, 2, 3);

        // 结果文件过期（R03）
        jdbc.update("UPDATE sys_async_task SET finished_at = ? WHERE id = ?", LocalDateTime.now().minusDays(8), id);
        taskService.cleanupExpiredResults();
        JsonNode expired = ok(doGet("/api/system/tasks/" + id, admin));
        assertThat(expired.at("/resultExpired").asBoolean()).isTrue();
        assertThat(expired.has("resultFileId")).isFalse();
    }

    @Test
    void taskFailureMessage() throws Exception {
        actAsAdmin();
        Long id = asyncTaskApi.submit("IMPORT", "导入测试", "system", ctx -> {
            throw BizException.of(SystemErrorCodes.FILE_EMPTY);
        });
        JsonNode t = waitTask(id, s -> s.equals("FAILED"));
        assertThat(t.at("/errorMessage").asText()).isEqualTo("不能上传空文件");
    }

    @Test
    void perUserLimitQueueAndCancel_R01() throws Exception {
        actAsAdmin();
        CountDownLatch release = new CountDownLatch(1);
        List<Long> ids = new ArrayList<>();
        try {
            for (int i = 0; i < 4; i++) {
                ids.add(asyncTaskApi.submit("MRP", "运算" + i, "system", ctx -> release.await(10, TimeUnit.SECONDS)));
            }
            waitTask(ids.get(2), s -> s.equals("RUNNING"));
            assertThat(ok(doGet("/api/system/tasks/" + ids.get(3), admin)).at("/status").asText()).isEqualTo("WAITING");
            assertError(doPost("/api/system/tasks/" + ids.get(0) + "/cancel", admin, null), "只能取消等待中的任务");
            ok(doPost("/api/system/tasks/" + ids.get(3) + "/cancel", admin, null));
            assertThat(ok(doGet("/api/system/tasks/" + ids.get(3), admin)).at("/status").asText()).isEqualTo("CANCELED");
        } finally {
            release.countDown();
        }
        for (int i = 0; i < 3; i++) waitTask(ids.get(i), s -> s.equals("SUCCESS"));
    }

    @Test
    void restartMarksInterrupted_T02() throws Exception {
        actAsAdmin();
        CountDownLatch release = new CountDownLatch(1);
        Long id = asyncTaskApi.submit("MRP", "长任务", "system", ctx -> release.await(10, TimeUnit.SECONDS));
        waitTask(id, s -> s.equals("RUNNING"));
        try {
            taskService.markInterrupted();
            JsonNode t = ok(doGet("/api/system/tasks/" + id, admin));
            assertThat(t.at("/status").asText()).isEqualTo("FAILED");
            assertThat(t.at("/errorMessage").asText()).isEqualTo("服务重启，任务中断，请重新提交");
        } finally {
            release.countDown();
        }
        Thread.sleep(200);
        // 中断标记不会被随后结束的执行覆盖
        assertThat(ok(doGet("/api/system/tasks/" + id, admin)).at("/status").asText()).isEqualTo("FAILED");
    }

    @Test
    void otherUsersCannotSeeMyTasks() throws Exception {
        actAsAdmin();
        Long id = asyncTaskApi.submit("EXPORT", "管理员的任务", "system", ctx -> ctx.resultMessage("ok"));
        waitTask(id, s -> s.equals("SUCCESS"));
        String dept = createDept(HQ, "任务部门" + uniq());
        String role = createRole("SELF", List.of());
        String u = "tk" + uniq();
        createUser(u, dept, List.of(role), false);
        String token = login(u, PASSWORD);
        assertError(doGet("/api/system/tasks/" + id, token), "任务不存在");
        assertThat(ok(doGet("/api/system/tasks?pageNo=1&pageSize=20&all=true", token)).at("/total").asLong()).isZero();
    }

    @Test
    void exportOverLimitBecomesBackgroundTask_T01() throws Exception {
        String dept = createDept(HQ, "导出部门" + uniq());
        String role = createRole("SELF", List.of());
        createUser("ex" + uniq(), dept, List.of(role), false);
        createUser("ex" + uniq(), dept, List.of(role), false);
        // 参数校验要求 ≥ 1000，这里直接改库模拟“数据量超过同步导出上限”
        jdbc.update("UPDATE sys_param SET param_value = '1' WHERE param_key = 'sys.export.sync-max-rows'");
        caches.clear(SystemCaches.PARAM);
        try {
            String body = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                            .get("/api/system/users/export?pageNo=1&pageSize=20").header("Authorization", admin))
                    .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
            JsonNode data = ok(read(body));
            assertThat(data.at("/async").asBoolean()).isTrue();
            JsonNode t = waitTask(Long.valueOf(data.at("/taskId").asText()), s -> s.equals("SUCCESS") || s.equals("FAILED"));
            assertThat(t.at("/status").asText()).isEqualTo("SUCCESS");
            assertThat(t.at("/name").asText()).isEqualTo("导出用户");
            assertThat(t.at("/resultMessage").asText()).startsWith("成功导出");
        } finally {
            jdbc.update("UPDATE sys_param SET param_value = '10000' WHERE param_key = 'sys.export.sync-max-rows'");
            caches.clear(SystemCaches.PARAM);
        }
    }

    private JsonNode job(String code) throws Exception {
        return find(ok(doGet("/api/system/jobs", admin)), j -> j.at("/code").asText().equals(code));
    }

    private static JsonNode find(JsonNode arr, Predicate<JsonNode> p) {
        for (JsonNode n : arr) if (p.test(n)) return n;
        throw new AssertionError("not found");
    }

    private JsonNode waitTask(Long id, Predicate<String> done) throws Exception {
        JsonNode t = null;
        for (int i = 0; i < 100; i++) {
            t = ok(doGet("/api/system/tasks/" + id, admin));
            if (done.test(t.at("/status").asText())) return t;
            Thread.sleep(50);
        }
        throw new AssertionError("任务状态未达到预期：" + t);
    }
}
