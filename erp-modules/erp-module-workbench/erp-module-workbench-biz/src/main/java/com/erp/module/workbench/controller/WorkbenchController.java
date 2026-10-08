package com.erp.module.workbench.controller;

import com.erp.common.result.CommonResult;
import com.erp.common.result.PageResult;
import com.erp.framework.security.SecurityUtils;
import com.erp.module.workbench.controller.vo.WbVOs.AlertQuery;
import com.erp.module.workbench.controller.vo.WbVOs.AlertStats;
import com.erp.module.workbench.controller.vo.WbVOs.AlertVO;
import com.erp.module.workbench.controller.vo.WbVOs.CardDataVO;
import com.erp.module.workbench.controller.vo.WbVOs.CardVO;
import com.erp.module.workbench.controller.vo.WbVOs.LayoutSave;
import com.erp.module.workbench.controller.vo.WbVOs.MessageQuery;
import com.erp.module.workbench.controller.vo.WbVOs.MessageVO;
import com.erp.module.workbench.controller.vo.WbVOs.RemarkReq;
import com.erp.module.workbench.controller.vo.WbVOs.ShortcutSave;
import com.erp.module.workbench.controller.vo.WbVOs.Summary;
import com.erp.module.workbench.controller.vo.WbVOs.TodoQuery;
import com.erp.module.workbench.controller.vo.WbVOs.TodoVO;
import com.erp.module.workbench.controller.vo.WbVOs.UnreadCount;
import com.erp.module.workbench.service.WbSupport;
import com.erp.module.workbench.service.alert.AlertService;
import com.erp.module.workbench.service.dashboard.DashboardService;
import com.erp.module.workbench.service.message.MessageService;
import com.erp.module.workbench.service.push.WorkbenchPushService;
import com.erp.module.workbench.service.todo.TodoService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

/** 工作台：首页、待办、消息、预警（登录即可，只操作自己的数据） */
@Tag(name = "工作台")
@RestController
@RequestMapping("/api/workbench")
public class WorkbenchController {

    private final DashboardService dashboardService;
    private final TodoService todoService;
    private final MessageService messageService;
    private final AlertService alertService;
    private final WbSupport support;
    private final WorkbenchPushService pushService;
    private final com.erp.module.workbench.service.weather.WeatherService weatherService;

    public WorkbenchController(DashboardService dashboardService, TodoService todoService, MessageService messageService, AlertService alertService, WorkbenchPushService pushService,
                               WbSupport support, com.erp.module.workbench.service.weather.WeatherService weatherService) {
        this.weatherService = weatherService;
        this.dashboardService = dashboardService;
        this.todoService = todoService;
        this.messageService = messageService;
        this.alertService = alertService;
        this.support = support;
        this.pushService = pushService;
    }

    // ==================== 首页 ====================

    /**
     * 实时推送（SSE）：待办、消息、预警变化时推送 refresh 事件（约每 25 秒一个 ping 心跳），前端收到后刷新角标。
     * 用 POST 是为了与前端统一的 fetch 流式读取（可带 Authorization 头，EventSource 做不到）。
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("isAuthenticated()")
    public SseEmitter stream() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes a && a.getResponse() != null) {
            a.getResponse().setHeader("X-Accel-Buffering", "no");
            a.getResponse().setHeader("Cache-Control", "no-cache");
        }
        return pushService.subscribe(SecurityUtils.getLoginUser().id());
    }

    /** 顶部天气：传入经纬度（自动定位）按位置查询，否则按个人设置的城市（默认东莞）；缓存 30 分钟 */
    @GetMapping("/weather")
    @PreAuthorize("isAuthenticated()")
    public CommonResult<com.erp.module.workbench.service.weather.WeatherService.Weather> weather(
            @RequestParam(required = false) java.math.BigDecimal lat, @RequestParam(required = false) java.math.BigDecimal lon) {
        return CommonResult.success(weatherService.current(lat, lon));
    }

    @GetMapping("/weather/pref")
    @PreAuthorize("isAuthenticated()")
    public CommonResult<com.erp.module.workbench.service.weather.WeatherService.Pref> weatherPref() {
        return CommonResult.success(weatherService.pref());
    }

    /** 保存个人天气设置：mode AUTO 自动定位 / MANUAL 手选城市 */
    @PutMapping("/weather/pref")
    @PreAuthorize("isAuthenticated()")
    public CommonResult<Void> saveWeatherPref(@RequestBody com.erp.module.workbench.service.weather.WeatherService.Pref req) {
        weatherService.savePref(req);
        return CommonResult.success();
    }

    @GetMapping("/weather/cities")
    @PreAuthorize("isAuthenticated()")
    public CommonResult<java.util.List<com.erp.module.workbench.service.weather.WeatherCities.City>> weatherCities(
            @RequestParam(required = false) String keyword) {
        return CommonResult.success(weatherService.cities(keyword));
    }

    @GetMapping("/summary")
    @PreAuthorize("isAuthenticated()")
    public CommonResult<Summary> summary() {
        return CommonResult.success(dashboardService.summary());
    }

    @GetMapping("/cards")
    @PreAuthorize("isAuthenticated()")
    public CommonResult<List<CardVO>> cards() {
        return CommonResult.success(dashboardService.cards());
    }

    @GetMapping("/cards/{code}/data")
    @PreAuthorize("isAuthenticated()")
    public CommonResult<CardDataVO> cardData(@PathVariable String code, @RequestParam(defaultValue = "false") boolean refresh) {
        return CommonResult.success(dashboardService.data(code, refresh));
    }

    @PutMapping("/layout")
    @PreAuthorize("isAuthenticated()")
    public CommonResult<Void> saveLayout(@RequestBody LayoutSave req) {
        dashboardService.saveLayout(req.items());
        return CommonResult.success();
    }

    @PostMapping("/layout/reset")
    @PreAuthorize("isAuthenticated()")
    public CommonResult<Void> resetLayout() {
        dashboardService.resetLayout();
        return CommonResult.success();
    }

    @GetMapping("/shortcuts")
    @PreAuthorize("isAuthenticated()")
    public CommonResult<List<String>> shortcuts() {
        return CommonResult.success(dashboardService.shortcuts());
    }

    @PutMapping("/shortcuts")
    @PreAuthorize("isAuthenticated()")
    public CommonResult<Void> saveShortcuts(@RequestBody ShortcutSave req) {
        dashboardService.saveShortcuts(req.routes());
        return CommonResult.success();
    }

    // ==================== 待办 ====================

    @GetMapping("/todos")
    @PreAuthorize("isAuthenticated()")
    public CommonResult<PageResult<TodoVO>> todos(@Valid TodoQuery q) {
        return CommonResult.success(todoService.page(q));
    }

    @GetMapping("/todos/count")
    @PreAuthorize("isAuthenticated()")
    public CommonResult<Long> todoCount() {
        return CommonResult.success(todoService.count(support.currentUser(), null));
    }

    @PostMapping("/todos/{id}/done")
    @PreAuthorize("isAuthenticated()")
    public CommonResult<Void> todoDone(@PathVariable Long id) {
        todoService.markDone(id);
        return CommonResult.success();
    }

    // ==================== 消息 ====================

    @GetMapping("/messages")
    @PreAuthorize("isAuthenticated()")
    public CommonResult<PageResult<MessageVO>> messages(@Valid MessageQuery q) {
        return CommonResult.success(messageService.page(q));
    }

    @GetMapping("/messages/unread-count")
    @PreAuthorize("isAuthenticated()")
    public CommonResult<UnreadCount> unreadCount() {
        return CommonResult.success(messageService.unread(support.currentUser()));
    }

    @PostMapping("/messages/{id}/read")
    @PreAuthorize("isAuthenticated()")
    public CommonResult<Void> readMessage(@PathVariable Long id) {
        messageService.read(id);
        return CommonResult.success();
    }

    @PostMapping("/messages/read-all")
    @PreAuthorize("isAuthenticated()")
    public CommonResult<Map<String, Integer>> readAll(@RequestParam(required = false) String type) {
        return CommonResult.success(Map.of("count", messageService.readAll(type)));
    }

    @DeleteMapping("/messages/read")
    @PreAuthorize("isAuthenticated()")
    public CommonResult<Map<String, Integer>> deleteRead() {
        return CommonResult.success(Map.of("count", messageService.deleteRead()));
    }

    // ==================== 预警 ====================

    @GetMapping("/alerts")
    @PreAuthorize("isAuthenticated()")
    public CommonResult<PageResult<AlertVO>> alerts(@Valid AlertQuery q) {
        return CommonResult.success(alertService.page(q));
    }

    @GetMapping("/alerts/stats")
    @PreAuthorize("isAuthenticated()")
    public CommonResult<AlertStats> alertStats() {
        return CommonResult.success(alertService.stats());
    }

    @PostMapping("/alerts/{id}/handle")
    @PreAuthorize("isAuthenticated()")
    public CommonResult<Void> handleAlert(@PathVariable Long id, @RequestBody RemarkReq req) {
        alertService.handle(id, req.remark());
        return CommonResult.success();
    }

    @PostMapping("/alerts/{id}/ignore")
    @PreAuthorize("isAuthenticated()")
    public CommonResult<Void> ignoreAlert(@PathVariable Long id, @RequestBody RemarkReq req) {
        alertService.ignore(id, req.remark());
        return CommonResult.success();
    }
}
