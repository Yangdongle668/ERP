package com.erp.module.workbench.service.dashboard;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.common.exception.BizException;
import com.erp.module.workbench.api.WorkbenchErrorCodes;
import com.erp.module.workbench.api.dashboard.CardData;
import com.erp.module.workbench.api.dashboard.DashboardCard;
import com.erp.module.workbench.config.WorkbenchModuleConfig;
import com.erp.module.workbench.controller.vo.WbVOs.CardDataVO;
import com.erp.module.workbench.controller.vo.WbVOs.CardVO;
import com.erp.module.workbench.controller.vo.WbVOs.LayoutItem;
import com.erp.module.workbench.controller.vo.WbVOs.Point;
import com.erp.module.workbench.controller.vo.WbVOs.Summary;
import com.erp.module.workbench.dal.dataobject.WbLayoutDO;
import com.erp.module.workbench.dal.dataobject.WbShortcutDO;
import com.erp.module.workbench.dal.mapper.WbLayoutMapper;
import com.erp.module.workbench.dal.mapper.WbShortcutMapper;
import com.erp.module.workbench.service.WbSupport;
import com.erp.module.workbench.service.alert.AlertService;
import com.erp.module.workbench.service.message.MessageService;
import com.erp.module.workbench.service.todo.TodoService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工作台首页（需求 02-01）：欢迎区计数、看板卡片（各模块注册的 {@link DashboardCard}，按权限过滤，按用户缓存）、布局、快捷入口。
 */
@Slf4j
@Service
public class DashboardService {

    public static final int MAX_SHORTCUTS = 12;

    private final List<DashboardCard> cards;
    private final WbLayoutMapper layoutMapper;
    private final WbShortcutMapper shortcutMapper;
    private final TodoService todoService;
    private final AlertService alertService;
    private final MessageService messageService;
    private final WbSupport support;
    private final ObjectMapper objectMapper;
    private final Map<String, CardDataVO> cache = new ConcurrentHashMap<>();

    public DashboardService(List<DashboardCard> cards, WbLayoutMapper layoutMapper, WbShortcutMapper shortcutMapper, TodoService todoService,
                            AlertService alertService, MessageService messageService, WbSupport support, ObjectMapper objectMapper) {
        this.cards = cards.stream().sorted(Comparator.comparingInt(DashboardCard::sort).thenComparing(DashboardCard::code)).toList();
        this.layoutMapper = layoutMapper;
        this.shortcutMapper = shortcutMapper;
        this.todoService = todoService;
        this.alertService = alertService;
        this.messageService = messageService;
        this.support = support;
        this.objectMapper = objectMapper;
    }

    public Summary summary() {
        Long me = support.currentUser();
        return new Summary(todoService.count(me, TodoService.APPROVAL), todoService.count(me, "TASK"), alertService.openCount(),
                messageService.unread(me).total(), support.params().getInt(WorkbenchModuleConfig.P_TODO_POLL));
    }

    // ==================== 卡片 ====================

    private Map<String, DashboardCard> available() {
        Map<String, DashboardCard> map = new LinkedHashMap<>();
        for (DashboardCard c : cards) if (support.hasPermission(c.permission())) map.put(c.code(), c);
        return map;
    }

    /** 当前用户可用卡片：先按保存的布局顺序，再追加新卡片（默认显示） */
    public List<CardVO> cards() {
        Map<String, DashboardCard> avail = available();
        List<CardVO> list = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (LayoutItem i : layout(support.currentUser())) {
            DashboardCard c = avail.get(i.code());
            if (c != null && seen.add(c.code())) list.add(new CardVO(c.code(), c.name(), c.type(), c.route(), i.visible()));
        }
        for (DashboardCard c : avail.values()) if (seen.add(c.code())) list.add(new CardVO(c.code(), c.name(), c.type(), c.route(), true));
        return list;
    }

    /** 卡片数据：按用户缓存 wb.dashboard.refresh-minutes 分钟；单个卡片失败不影响其他（WB-HOME-R02） */
    public CardDataVO data(String code, boolean refresh) {
        DashboardCard c = available().get(code);
        if (c == null) throw BizException.of(WorkbenchErrorCodes.CARD_NOT_EXISTS, code);
        String key = support.currentUser() + ":" + code;
        int minutes = support.params().getInt(WorkbenchModuleConfig.P_DASHBOARD_REFRESH);
        CardDataVO cached = cache.get(key);
        if (!refresh && cached != null && minutes > 0 && cached.updatedAt().isAfter(LocalDateTime.now().minusMinutes(minutes))) return cached;
        CardData d;
        try {
            d = c.load();
        } catch (BizException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("看板卡片 {} 加载失败：{}", code, e.getMessage(), e);
            throw BizException.of(WorkbenchErrorCodes.CARD_LOAD_FAILED, c.name());
        }
        CardDataVO vo = new CardDataVO(code, d.value(), d.unit(), d.changePct(), d.compareLabel(), d.costLike(), d.subText(),
                d.series() == null ? List.of() : d.series().stream().map(p -> new Point(p.label(), p.value())).toList(), LocalDateTime.now());
        cache.put(key, vo);
        return vo;
    }

    // ==================== 布局 ====================

    private List<LayoutItem> layout(Long userId) {
        WbLayoutDO l = layoutMapper.selectOne(new LambdaQueryWrapper<WbLayoutDO>().eq(WbLayoutDO::getUserId, userId));
        if (l == null || !StringUtils.hasText(l.getLayoutJson())) return List.of();
        try {
            return objectMapper.readValue(l.getLayoutJson(), new TypeReference<List<LayoutItem>>() {
            });
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void saveLayout(List<LayoutItem> items) {
        Long me = support.currentUser();
        List<LayoutItem> clean = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (LayoutItem i : items == null ? List.<LayoutItem>of() : items) {
            if (i != null && StringUtils.hasText(i.code()) && seen.add(i.code())) clean.add(new LayoutItem(i.code(), i.visible()));
        }
        String json;
        try {
            json = objectMapper.writeValueAsString(clean);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
        WbLayoutDO l = layoutMapper.selectOne(new LambdaQueryWrapper<WbLayoutDO>().eq(WbLayoutDO::getUserId, me));
        if (l == null) {
            l = new WbLayoutDO();
            l.setUserId(me);
            l.setLayoutJson(WbSupport.limit(json, 4000));
            layoutMapper.insert(l);
        } else {
            l.setLayoutJson(WbSupport.limit(json, 4000));
            layoutMapper.updateByIdOrFail(l);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void resetLayout() {
        layoutMapper.deleteByUser(support.currentUser());
    }

    // ==================== 快捷入口 ====================

    /** 保存的快捷入口路由；未保存时为空（前端按角色给默认入口） */
    public List<String> shortcuts() {
        return shortcutMapper.selectList(new LambdaQueryWrapper<WbShortcutDO>().eq(WbShortcutDO::getUserId, support.currentUser())
                .orderByAsc(WbShortcutDO::getSort)).stream().map(WbShortcutDO::getMenuRoute).toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public void saveShortcuts(List<String> routes) {
        List<String> list = (routes == null ? List.<String>of() : routes).stream().filter(StringUtils::hasText).map(String::trim).distinct().toList();
        if (list.size() > MAX_SHORTCUTS) throw BizException.of(WorkbenchErrorCodes.SHORTCUT_TOO_MANY, MAX_SHORTCUTS);
        Long me = support.currentUser();
        shortcutMapper.deleteByUser(me);
        int sort = 0;
        for (String r : list) {
            WbShortcutDO s = new WbShortcutDO();
            s.setUserId(me);
            s.setMenuRoute(WbSupport.limit(r, 256));
            s.setSort(sort++);
            shortcutMapper.insert(s);
        }
    }
}
