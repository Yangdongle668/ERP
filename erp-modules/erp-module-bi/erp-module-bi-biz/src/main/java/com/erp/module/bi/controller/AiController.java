package com.erp.module.bi.controller;

import com.erp.common.result.CommonResult;
import com.erp.common.result.PageParam;
import com.erp.common.result.PageResult;
import com.erp.framework.web.SseStreams;
import com.erp.module.bi.controller.vo.AiVOs.Ask;
import com.erp.module.bi.controller.vo.AiVOs.ConversationSave;
import com.erp.module.bi.controller.vo.AiVOs.Feedback;
import com.erp.module.bi.controller.vo.AiVOs.Settings;
import com.erp.module.bi.dal.dataobject.AiAnomalyDO;
import com.erp.module.bi.dal.dataobject.AiConversationDO;
import com.erp.module.bi.dal.dataobject.AiWeeklyReportDO;
import com.erp.module.bi.service.ai.AiAnomalyService;
import com.erp.module.bi.service.ai.AiChatService.LogRow;
import com.erp.module.bi.service.ai.AiChatService.MessageView;
import com.erp.module.bi.service.ai.AiChatService.Status;
import com.erp.module.bi.service.ai.AiChatService.UsageRow;
import com.erp.module.bi.service.ai.AiChatService;
import com.erp.module.bi.service.ai.AiSettings;
import com.erp.module.bi.service.ai.AiWeeklyReportService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** AI 分析（需求 13-04 第 6 节）：问数会话、反馈、异常、周报、用量与日志 */
@Tag(name = "BI - AI 分析")
@RestController
@RequestMapping("/api/bi/ai")
public class AiController {

    private final AiChatService chatService;
    private final AiAnomalyService anomalyService;
    private final AiWeeklyReportService weeklyReportService;
    private final AiSettings settings;
    private final SseStreams sse;

    public AiController(AiChatService chatService, AiAnomalyService anomalyService, AiWeeklyReportService weeklyReportService, AiSettings settings,
                        SseStreams sse) {
        this.sse = sse;
        this.chatService = chatService;
        this.anomalyService = anomalyService;
        this.weeklyReportService = weeklyReportService;
        this.settings = settings;
    }

    @GetMapping("/status")
    @PreAuthorize("@ss.has('ai:query:use')")
    public CommonResult<Status> status() {
        return CommonResult.success(chatService.status());
    }

    @GetMapping("/conversations")
    @PreAuthorize("@ss.has('ai:query:use')")
    public CommonResult<List<AiConversationDO>> conversations() {
        return CommonResult.success(chatService.conversations());
    }

    @PostMapping("/conversations")
    @PreAuthorize("@ss.has('ai:query:use')")
    public CommonResult<AiConversationDO> create(@Valid @RequestBody(required = false) ConversationSave req) {
        return CommonResult.success(chatService.create(req == null ? null : req.title()));
    }

    @PutMapping("/conversations/{id}")
    @PreAuthorize("@ss.has('ai:query:use')")
    public CommonResult<Void> rename(@PathVariable Long id, @Valid @RequestBody ConversationSave req) {
        chatService.rename(id, req.title());
        return CommonResult.success();
    }

    @DeleteMapping("/conversations/{id}")
    @PreAuthorize("@ss.has('ai:query:use')")
    public CommonResult<Void> delete(@PathVariable Long id) {
        chatService.delete(id);
        return CommonResult.success();
    }

    @GetMapping("/conversations/{id}/messages")
    @PreAuthorize("@ss.has('ai:query:use')")
    public CommonResult<List<MessageView>> messages(@PathVariable Long id) {
        return CommonResult.success(chatService.messages(id));
    }

    @PostMapping("/conversations/{id}/messages")
    @PreAuthorize("@ss.has('ai:query:use')")
    public CommonResult<MessageView> ask(@PathVariable Long id, @Valid @RequestBody Ask req) {
        return CommonResult.success(chatService.ask(id, req.question()));
    }

    /**
     * 流式提问（SSE）：事件 status（进度提示 {"text"}）、delta（回答文字片段 {"text"}）、done（最终消息 MessageView，含图表与数据）、
     * error（{"message"}）。delta 是模型的原始输出，最终展示以 done 为准（会去掉口径 / 图表标记）。
     */
    @PostMapping(value = "/conversations/{id}/messages/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("@ss.has('ai:query:use')")
    public SseEmitter askStream(@PathVariable Long id, @Valid @RequestBody Ask req) {
        return sse.stream(TimeUnit.MINUTES.toMillis(5), out -> {
            MessageView v = chatService.ask(id, req.question(), t -> out.send("delta", Map.of("text", t)), t -> out.send("status", Map.of("text", t)));
            out.send("done", v);
        });
    }

    @PostMapping("/messages/{id}/feedback")
    @PreAuthorize("@ss.has('ai:query:use')")
    public CommonResult<Void> feedback(@PathVariable Long id, @Valid @RequestBody Feedback req) {
        chatService.feedback(id, req.feedback(), req.remark());
        return CommonResult.success();
    }

    @GetMapping("/anomalies")
    @PreAuthorize("@ss.has('ai:query:use')")
    public CommonResult<List<AiAnomalyDO>> anomalies(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return CommonResult.success(anomalyService.list(from, to));
    }

    @GetMapping("/weekly-reports")
    @PreAuthorize("@ss.has('bi:dashboard:view')")
    public CommonResult<List<AiWeeklyReportDO>> weeklyReports() {
        return CommonResult.success(weeklyReportService.list());
    }

    /** 立即生成上周周报（管理员） */
    @PostMapping("/weekly-reports/generate")
    @PreAuthorize("@ss.has('ai:setting:manage')")
    public CommonResult<AiWeeklyReportDO> generateWeeklyReport() {
        return CommonResult.success(weeklyReportService.generate(LocalDate.now()));
    }

    /** 立即检测异常（管理员） */
    @PostMapping("/anomalies/detect")
    @PreAuthorize("@ss.has('ai:setting:manage')")
    public CommonResult<Integer> detect() {
        return CommonResult.success(anomalyService.detect(LocalDate.now()));
    }

    @GetMapping("/settings")
    @PreAuthorize("@ss.has('ai:setting:manage')")
    public CommonResult<Settings> settings() {
        AiSettings.Snapshot s = settings.get();
        return CommonResult.success(new Settings(s.enabled(), s.provider(), s.baseUrl(), s.model(), s.maskedKey(), s.keySource(), s.mask(), s.quota()));
    }

    @GetMapping("/usage")
    @PreAuthorize("@ss.has('ai:setting:manage')")
    public CommonResult<List<UsageRow>> usage(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return CommonResult.success(chatService.usage(from, to));
    }

    @GetMapping("/logs")
    @PreAuthorize("@ss.has('ai:log:view')")
    public CommonResult<PageResult<LogRow>> logs(@Valid PageParam page, @RequestParam(required = false) Long userId,
                                                 @RequestParam(required = false) Boolean success, @RequestParam(required = false) String feedback) {
        return CommonResult.success(chatService.logs(page, userId, success, feedback));
    }
}
