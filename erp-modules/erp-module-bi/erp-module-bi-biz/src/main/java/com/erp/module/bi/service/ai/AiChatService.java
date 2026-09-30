package com.erp.module.bi.service.ai;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.erp.common.exception.BizException;
import com.erp.common.result.PageParam;
import com.erp.common.result.PageResult;
import com.erp.framework.security.LoginUser;
import com.erp.framework.security.SecurityUtils;
import com.erp.module.bi.api.BiErrorCodes;
import com.erp.module.bi.dal.dataobject.AiConversationDO;
import com.erp.module.bi.dal.dataobject.AiMessageDO;
import com.erp.module.bi.dal.dataobject.AiQueryLogDO;
import com.erp.module.bi.dal.mapper.AiConversationMapper;
import com.erp.module.bi.dal.mapper.AiMessageMapper;
import com.erp.module.bi.dal.mapper.AiQueryLogMapper;
import com.erp.module.bi.service.ai.BiQueryTool.Execution;
import com.erp.module.bi.service.ai.LlmAdapter.LlmRequest;
import com.erp.module.bi.service.ai.LlmAdapter.LlmResult;
import com.erp.module.bi.service.ai.LlmAdapter.ToolOutcome;
import com.erp.module.bi.service.ai.LlmAdapter.Turn;
import com.erp.module.bi.service.metric.BiMetricService;
import com.erp.module.bi.service.metric.BiMetricService.MetricInfo;
import com.erp.module.bi.service.metric.MetricRegistry;
import com.erp.module.bi.service.query.BiQueryResult;
import com.erp.module.system.api.user.UserApi;
import com.erp.module.system.api.user.UserDTO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI 问数（需求 13-04 第 2.1 节）：会话、提问、反馈、问答日志、用量。AI 只读、只建议：模型只能通过 bi_query 取数。
 */
@Slf4j
@Service
public class AiChatService {

    public static final String USER = "USER";
    public static final String ASSISTANT = "ASSISTANT";
    static final String DEFAULT_TITLE = "新对话";
    static final int HISTORY_TURNS = 10;
    static final int TIMEOUT_SECONDS = 60;
    static final Pattern CHART = Pattern.compile("(?m)^\\s*图表\\s*[:：]\\s*(line|bar|pie|table)\\s*$");

    /** 返回给页面的消息：results 为真实数值的数据表 */
    public record MessageView(Long id, String role, String content, String chart, List<Map<String, Object>> toolCalls, List<BiQueryResult> results,
                              Integer tokens, String feedback, String feedbackRemark, LocalDateTime createdAt) {
    }

    public record Status(boolean enabled, boolean configured, String model, int quota, long usedToday, String message) {
    }

    public record UsageRow(String date, Long userId, String userName, long questions, long tokens, long failures) {
    }

    public record LogRow(Long id, Long userId, String userName, String question, String toolCalls, Integer resultRows, Long latencyMs, Integer tokens,
                         Boolean success, String error, String feedback, LocalDateTime createdAt) {
    }

    private final AiConversationMapper conversationMapper;
    private final AiMessageMapper messageMapper;
    private final AiQueryLogMapper logMapper;
    private final AiSettings settings;
    private final ObjectProvider<LlmAdapter> adapters;
    private final BiQueryTool tool;
    private final BiMetricService metricService;
    private final UserApi userApi;
    private final ObjectMapper json;

    public AiChatService(AiConversationMapper conversationMapper, AiMessageMapper messageMapper, AiQueryLogMapper logMapper, AiSettings settings,
                         ObjectProvider<LlmAdapter> adapters, BiQueryTool tool, BiMetricService metricService, UserApi userApi, ObjectMapper json) {
        this.conversationMapper = conversationMapper;
        this.messageMapper = messageMapper;
        this.logMapper = logMapper;
        this.settings = settings;
        this.adapters = adapters;
        this.tool = tool;
        this.metricService = metricService;
        this.userApi = userApi;
        this.json = json;
    }

    // ==================== 状态 ====================

    public Status status() {
        AiSettings.Snapshot s = settings.get();
        long used = usedToday(SecurityUtils.getLoginUser().id());
        return new Status(s.enabled(), s.ready(), s.model(), s.quota(), used, s.ready() ? null : BiErrorCodes.AI_DISABLED.message());
    }

    long usedToday(Long userId) {
        return logMapper.selectCount(new LambdaQueryWrapper<AiQueryLogDO>().eq(AiQueryLogDO::getUserId, userId)
                .ge(AiQueryLogDO::getCreatedAt, LocalDate.now().atStartOfDay()));
    }

    /** OpenAI 兼容协议的适配器（同一协议有多个实现时取排序最前的，测试中替换为模拟实现） */
    LlmAdapter adapter() {
        return adapters.orderedStream().filter(a -> OpenAiCompatibleLlmAdapter.PROVIDER.equalsIgnoreCase(a.provider()))
                .findFirst().orElseThrow(() -> new BizException(BiErrorCodes.AI_DISABLED));
    }

    // ==================== 会话 ====================

    public List<AiConversationDO> conversations() {
        return conversationMapper.selectList(new LambdaQueryWrapper<AiConversationDO>().eq(AiConversationDO::getUserId, SecurityUtils.getLoginUser().id())
                .orderByDesc(AiConversationDO::getUpdatedAt).last("LIMIT 200"));
    }

    @Transactional(rollbackFor = Exception.class)
    public AiConversationDO create(String title) {
        AiConversationDO c = new AiConversationDO();
        c.setUserId(SecurityUtils.getLoginUser().id());
        c.setTitle(title == null || title.isBlank() ? DEFAULT_TITLE : truncate(title.trim(), 128));
        conversationMapper.insert(c);
        return conversationMapper.selectById(c.getId());
    }

    @Transactional(rollbackFor = Exception.class)
    public void rename(Long id, String title) {
        AiConversationDO c = own(id);
        c.setTitle(title == null || title.isBlank() ? DEFAULT_TITLE : truncate(title.trim(), 128));
        conversationMapper.updateByIdOrFail(c);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        own(id);
        messageMapper.delete(new LambdaQueryWrapper<AiMessageDO>().eq(AiMessageDO::getConversationId, id));
        conversationMapper.deleteById(id);
    }

    public List<MessageView> messages(Long conversationId) {
        own(conversationId);
        return messageMapper.selectList(new LambdaQueryWrapper<AiMessageDO>().eq(AiMessageDO::getConversationId, conversationId)
                .orderByAsc(AiMessageDO::getCreatedAt).orderByAsc(AiMessageDO::getId)).stream().map(this::view).toList();
    }

    private AiConversationDO own(Long id) {
        AiConversationDO c = id == null ? null : conversationMapper.selectById(id);
        if (c == null || !c.getUserId().equals(SecurityUtils.getLoginUser().id())) throw BizException.of(BiErrorCodes.NOT_EXISTS, "会话");
        return c;
    }

    // ==================== 提问 ====================

    /** 提问：校验启用 / 额度，调用模型（工具以当前用户身份执行），保存消息与问答日志 */
    public MessageView ask(Long conversationId, String question) {
        LoginUser user = SecurityUtils.getLoginUser();
        if (question == null || question.isBlank()) throw new BizException(BiErrorCodes.AI_QUESTION_REQUIRED);
        String q = truncate(question.trim(), 2000);
        AiSettings.Snapshot s = settings.get();
        if (!s.ready()) throw new BizException(BiErrorCodes.AI_DISABLED);
        if (usedToday(user.id()) >= s.quota()) throw BizException.of(BiErrorCodes.AI_QUOTA, s.quota());
        AiConversationDO conv = own(conversationId);
        List<Turn> history = history(conversationId);

        AiMessageDO um = new AiMessageDO();
        um.setConversationId(conversationId);
        um.setRole(USER);
        um.setContent(q);
        messageMapper.insert(um);
        if (DEFAULT_TITLE.equals(conv.getTitle())) {
            conv.setTitle(truncate(q, 30));
            conversationMapper.updateByIdOrFail(conv);
        } else {
            conversationMapper.updateByIdOrFail(conv);
        }

        List<MetricInfo> visible = metricService.listVisible(user);
        List<Execution> executions = new ArrayList<>();
        long t0 = System.currentTimeMillis();
        LlmRequest req = new LlmRequest(s.baseUrl(), s.model(), s.apiKey(), systemPrompt(visible, LocalDate.now()), history, q, List.of(tool.spec(visible)), 4096, 4,
                TIMEOUT_SECONDS);
        LlmResult result;
        try {
            result = adapter().converse(req, (name, input) -> {
                if (!BiQueryTool.NAME.equals(name)) return new ToolOutcome("只能使用 bi_query 工具", true);
                Execution e = tool.execute(input, s.mask());
                executions.add(e);
                return new ToolOutcome(e.modelContent(), e.error() != null);
            });
        } catch (BizException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("AI 调用失败", e);
            saveLog(user.id(), null, q, executions, System.currentTimeMillis() - t0, 0, false, e.getClass().getSimpleName() + ": " + e.getMessage());
            throw new BizException(BiErrorCodes.AI_UNAVAILABLE);
        }
        String text = result.text() == null ? "" : result.text().trim();
        if ("refusal".equals(result.stopReason()) && text.isEmpty()) text = "这个问题无法回答，请换一种问法或只询问经营数据。";
        if (text.isEmpty()) text = "没有得到回答，请换一种问法再试。";
        String chart = null;
        Matcher m = CHART.matcher(text);
        if (m.find()) {
            chart = m.group(1);
            text = m.replaceAll("").trim();
        }
        AiMessageDO am = new AiMessageDO();
        am.setConversationId(conversationId);
        am.setRole(ASSISTANT);
        am.setContent(text);
        am.setToolCall(toJson(toolCalls(executions, chart)));
        am.setResultJson(toJson(results(executions)));
        am.setTokens(result.totalTokens());
        messageMapper.insert(am);
        saveLog(user.id(), am.getId(), q, executions, System.currentTimeMillis() - t0, result.totalTokens(), true, null);
        return view(messageMapper.selectById(am.getId()));
    }

    private List<Turn> history(Long conversationId) {
        List<AiMessageDO> list = messageMapper.selectList(new LambdaQueryWrapper<AiMessageDO>().eq(AiMessageDO::getConversationId, conversationId)
                .in(AiMessageDO::getRole, USER, ASSISTANT).orderByDesc(AiMessageDO::getCreatedAt).orderByDesc(AiMessageDO::getId)
                .last("LIMIT " + HISTORY_TURNS * 2));
        Collections.reverse(list);
        List<Turn> turns = new ArrayList<>();
        for (AiMessageDO d : list) {
            String role = USER.equals(d.getRole()) ? "user" : "assistant";
            // 相邻同角色（如上次提问失败没有回答）合并，保持 user / assistant 交替
            if (!turns.isEmpty() && turns.get(turns.size() - 1).role().equals(role)) {
                Turn last = turns.remove(turns.size() - 1);
                turns.add(new Turn(role, last.text() + "\n" + d.getContent()));
            } else {
                turns.add(new Turn(role, d.getContent() == null ? "" : d.getContent()));
            }
        }
        if (!turns.isEmpty() && "user".equals(turns.get(turns.size() - 1).role())) turns.remove(turns.size() - 1);
        while (!turns.isEmpty() && "assistant".equals(turns.get(0).role())) turns.remove(0);
        return turns;
    }

    /** 系统提示：当前日期、可用指标（只含有权限的）、维度与回答要求 */
    String systemPrompt(List<MetricInfo> visible, LocalDate today) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是本公司 ERP 系统中的经营数据分析助手，用中文回答管理人员关于经营数据的问题。今天是 ").append(today).append("（")
                .append(today.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.CHINA)).append("）。\n\n");
        sb.append("取数方式：所有数字都必须来自 bi_query 工具的返回结果，工具已按提问人的数据权限过滤。不要编造、估算或凭常识补全数字；"
                + "工具没有返回的数据就说明没有数据。你只做分析和建议，不能执行任何业务操作。\n\n");
        sb.append("当前用户可用的指标（编码｜名称｜单位｜口径｜可用维度）：\n");
        for (MetricInfo m : visible) {
            sb.append("- ").append(m.def().code()).append("｜").append(m.name()).append("｜").append(m.def().unit()).append("｜")
                    .append(m.def().description()).append("｜").append(String.join("、", m.def().dimColumns().keySet())).append('\n');
        }
        if (visible.isEmpty()) sb.append("（无）\n");
        sb.append("\n维度编码：");
        List<String> dims = new ArrayList<>();
        MetricRegistry.DIMENSIONS.forEach((k, v) -> dims.add(k + " " + v));
        sb.append(String.join("、", dims)).append("\n\n");
        sb.append("""
                回答要求：
                - 先用一两句话给出结论，再列出关键数字和简要分析。金额为本位币（元），单位为 PERCENT 的指标已经是百分数。
                - 问题中的“上个月”“本季度”“近 6 个月”等相对期间，按今天换算成具体起止日期后再查询。
                - 如果问题需要的指标不在上面的清单中（例如成本、毛利、应收），说明当前账号没有该指标的查看权限或系统暂无该指标，不要给出任何相关数字。
                - 工具返回错误时，根据错误信息说明原因；参数问题可以修正后再查询一次。
                - 数据中标注了脱敏的指标只能引用排名、占比和变化率，不要推测具体金额。
                - 回答末尾单独一行注明口径，格式如：“口径：指标 出货额；期间 2026-08-01 ~ 2026-08-31；筛选 无”。
                - 最后一行写建议的图表类型：“图表：line”“图表：bar”“图表：pie”或“图表：table”（趋势用 line，排名对比用 bar，占比用 pie，其余用 table）。
                """);
        return sb.toString();
    }

    private List<Map<String, Object>> toolCalls(List<Execution> executions, String chart) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Execution e : executions) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("tool", BiQueryTool.NAME);
            m.put("input", e.input());
            if (e.error() != null) m.put("error", e.error());
            if (e.result() != null) m.put("rows", e.result().rows().size());
            list.add(m);
        }
        if (chart != null && !list.isEmpty()) list.get(list.size() - 1).put("chart", chart);
        return list;
    }

    private static List<BiQueryResult> results(List<Execution> executions) {
        List<BiQueryResult> list = new ArrayList<>();
        for (Execution e : executions) {
            if (e.result() == null) continue;
            BiQueryResult r = e.result();
            if (r.rows().size() > 200) {
                r = new BiQueryResult(r.columns(), r.rows().subList(0, 200), r.metrics(), r.from(), r.to(), r.granularity(), true, r.dataUpdatedAt());
            }
            list.add(r);
        }
        // 只保留最后 3 次成功查询
        return list.size() > 3 ? list.subList(list.size() - 3, list.size()) : list;
    }

    private void saveLog(Long userId, Long messageId, String question, List<Execution> executions, long latency, int tokens, boolean success,
                         String error) {
        AiQueryLogDO l = new AiQueryLogDO();
        l.setUserId(userId);
        l.setMessageId(messageId);
        l.setQuestion(question);
        List<Map<String, Object>> calls = new ArrayList<>();
        int rows = 0;
        for (Execution e : executions) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("input", e.input());
            if (e.error() != null) m.put("error", e.error());
            calls.add(m);
            if (e.result() != null) rows += e.result().rows().size();
        }
        l.setToolCalls(toJson(calls));
        l.setResultRows(rows);
        l.setLatencyMs(latency);
        l.setTokens(tokens);
        l.setSuccess(success);
        l.setError(error == null ? null : truncate(error, 1000));
        logMapper.insert(l);
    }

    // ==================== 反馈、日志、用量 ====================

    @Transactional(rollbackFor = Exception.class)
    public void feedback(Long messageId, String feedback, String remark) {
        AiMessageDO m = messageId == null ? null : messageMapper.selectById(messageId);
        if (m == null || !ASSISTANT.equals(m.getRole())) throw BizException.of(BiErrorCodes.NOT_EXISTS, "回答");
        own(m.getConversationId());
        String fb = "UP".equalsIgnoreCase(feedback) ? "UP" : "DOWN".equalsIgnoreCase(feedback) ? "DOWN" : null;
        m.setFeedback(fb);
        m.setFeedbackRemark(remark == null || remark.isBlank() ? null : truncate(remark.trim(), 500));
        messageMapper.updateByIdOrFail(m);
        AiQueryLogDO l = logMapper.selectOne(new LambdaQueryWrapper<AiQueryLogDO>().eq(AiQueryLogDO::getMessageId, messageId).last("LIMIT 1"));
        if (l != null) {
            l.setFeedback(fb);
            logMapper.updateByIdOrFail(l);
        }
    }

    public PageResult<LogRow> logs(PageParam page, Long userId, Boolean success, String feedback) {
        PageResult<AiQueryLogDO> p = logMapper.selectPage(page, new LambdaQueryWrapper<AiQueryLogDO>().eq(userId != null, AiQueryLogDO::getUserId, userId)
                .eq(success != null, AiQueryLogDO::getSuccess, success).eq(feedback != null && !feedback.isBlank(), AiQueryLogDO::getFeedback, feedback)
                .orderByDesc(AiQueryLogDO::getCreatedAt));
        Set<Long> ids = new HashSet<>();
        p.list().forEach(l -> ids.add(l.getUserId()));
        Map<Long, UserDTO> users = userApi.list(ids);
        return new PageResult<>(p.list().stream().map(l -> new LogRow(l.getId(), l.getUserId(), name(users, l.getUserId()), l.getQuestion(),
                l.getToolCalls(), l.getResultRows(), l.getLatencyMs(), l.getTokens(), l.getSuccess(), l.getError(), l.getFeedback(), l.getCreatedAt())).toList(),
                p.total());
    }

    /** 用量：按日、按用户的提问数与 token 数 */
    public List<UsageRow> usage(LocalDate from, LocalDate to) {
        LocalDate f = from == null ? LocalDate.now().minusDays(29) : from;
        LocalDate t = to == null ? LocalDate.now() : to;
        List<AiQueryLogDO> logs = logMapper.selectList(new QueryWrapper<AiQueryLogDO>().lambda()
                .ge(AiQueryLogDO::getCreatedAt, f.atStartOfDay()).lt(AiQueryLogDO::getCreatedAt, t.plusDays(1).atStartOfDay())
                .select(AiQueryLogDO::getUserId, AiQueryLogDO::getCreatedAt, AiQueryLogDO::getTokens, AiQueryLogDO::getSuccess));
        Map<String, long[]> agg = new LinkedHashMap<>();
        Set<Long> ids = new HashSet<>();
        for (AiQueryLogDO l : logs) {
            ids.add(l.getUserId());
            long[] a = agg.computeIfAbsent(l.getCreatedAt().toLocalDate() + "|" + l.getUserId(), k -> new long[3]);
            a[0]++;
            a[1] += l.getTokens() == null ? 0 : l.getTokens();
            if (Boolean.FALSE.equals(l.getSuccess())) a[2]++;
        }
        Map<Long, UserDTO> users = userApi.list(ids);
        List<UsageRow> rows = new ArrayList<>();
        agg.forEach((k, a) -> {
            String[] p = k.split("\\|");
            Long uid = Long.valueOf(p[1]);
            rows.add(new UsageRow(p[0], uid, name(users, uid), a[0], a[1], a[2]));
        });
        rows.sort((x, y) -> x.date().equals(y.date()) ? Long.compare(y.questions(), x.questions()) : y.date().compareTo(x.date()));
        return rows;
    }

    /** AI-R07：问答日志保留 180 天 */
    public int cleanupLogs() {
        return logMapper.delete(new LambdaQueryWrapper<AiQueryLogDO>().lt(AiQueryLogDO::getCreatedAt, LocalDate.now().minusDays(180).atStartOfDay()));
    }

    // ==================== 工具 ====================

    private MessageView view(AiMessageDO m) {
        List<Map<String, Object>> calls = fromJson(m.getToolCall(), new TypeReference<>() {
        });
        List<BiQueryResult> results = fromJson(m.getResultJson(), new TypeReference<>() {
        });
        String chart = null;
        if (calls != null) {
            for (Map<String, Object> c : calls) {
                if (c.get("chart") != null) chart = c.get("chart").toString();
            }
        }
        return new MessageView(m.getId(), m.getRole(), m.getContent(), chart, calls == null ? List.of() : calls, results == null ? List.of() : results,
                m.getTokens(), m.getFeedback(), m.getFeedbackRemark(), m.getCreatedAt());
    }

    private String toJson(Object o) {
        try {
            return json.writeValueAsString(o);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private <T> T fromJson(String s, TypeReference<T> type) {
        if (s == null || s.isBlank()) return null;
        try {
            return json.readValue(s, type);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            return null;
        }
    }

    private static String name(Map<Long, UserDTO> users, Long id) {
        UserDTO u = users.get(id);
        return u == null ? null : u.realName();
    }

    static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
