package com.erp.module.workbench.service.notice;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.common.exception.BizException;
import com.erp.common.result.PageResult;
import com.erp.module.system.api.file.FileApi;
import com.erp.module.system.api.org.OrgDTO;
import com.erp.module.system.api.user.UserDTO;
import com.erp.module.workbench.api.WorkbenchErrorCodes;
import com.erp.module.workbench.controller.vo.WbVOs.ActiveNotice;
import com.erp.module.workbench.controller.vo.WbVOs.NoticeDetail;
import com.erp.module.workbench.controller.vo.WbVOs.NoticeQuery;
import com.erp.module.workbench.controller.vo.WbVOs.NoticeReader;
import com.erp.module.workbench.controller.vo.WbVOs.NoticeRow;
import com.erp.module.workbench.controller.vo.WbVOs.NoticeSave;
import com.erp.module.workbench.dal.dataobject.WbNoticeDO;
import com.erp.module.workbench.dal.dataobject.WbNoticeReadDO;
import com.erp.module.workbench.dal.mapper.WbNoticeMapper;
import com.erp.module.workbench.dal.mapper.WbNoticeReadMapper;
import com.erp.module.workbench.service.WbSupport;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 公告（需求 02-03）：富文本保存前过滤脚本、事件属性、iframe（WB-NTC-R01）；按全员或部门（含下级部门）发布，可定时发布、设置过期；
 * 重要公告登录后未读弹窗，“我已阅读”后不再弹出（WB-NTC-R02）。
 */
@Service("wbNoticeService")
public class NoticeService {

    public static final String BIZ_TYPE = "WB_NOTICE";
    public static final String DRAFT = "DRAFT";
    public static final String PUBLISHED = "PUBLISHED";
    public static final String WITHDRAWN = "WITHDRAWN";
    /** 允许基础格式标签（段落、列表、表格、链接、图片），不允许 script / iframe / on* 属性 */
    static final Safelist SAFELIST = Safelist.relaxed().addAttributes(":all", "style").removeTags("iframe");

    private final WbNoticeMapper mapper;
    private final WbNoticeReadMapper readMapper;
    private final FileApi fileApi;
    private final WbSupport support;

    public NoticeService(WbNoticeMapper mapper, WbNoticeReadMapper readMapper, FileApi fileApi, WbSupport support) {
        this.mapper = mapper;
        this.readMapper = readMapper;
        this.fileApi = fileApi;
        this.support = support;
    }

    /** WB-NTC-R01 */
    public static String sanitize(String html) {
        return Jsoup.clean(html == null ? "" : html, SAFELIST);
    }

    // ==================== 管理 ====================

    @Transactional(rollbackFor = Exception.class)
    public Long create(NoticeSave req) {
        WbNoticeDO n = new WbNoticeDO();
        n.setNoticeStatus(DRAFT);
        apply(n, req);
        mapper.insert(n);
        if (req.fileIds() != null && !req.fileIds().isEmpty()) fileApi.bind(req.fileIds(), BIZ_TYPE, n.getId());
        return n.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, NoticeSave req) {
        WbNoticeDO n = get(id);
        apply(n, req);
        mapper.updateByIdOrFail(n);
        if (req.fileIds() != null && !req.fileIds().isEmpty()) fileApi.bind(req.fileIds(), BIZ_TYPE, n.getId());
    }

    private void apply(WbNoticeDO n, NoticeSave req) {
        String scope = "DEPT".equals(req.scope()) ? "DEPT" : "ALL";
        List<Long> depts = req.deptIds() == null ? List.of() : req.deptIds().stream().filter(Objects::nonNull).distinct().toList();
        if ("DEPT".equals(scope) && depts.isEmpty()) throw new BizException(WorkbenchErrorCodes.NOTICE_DEPT_REQUIRED);
        LocalDateTime publishAt = req.publishAt() == null ? LocalDateTime.now() : req.publishAt();
        if (req.expireAt() != null && !req.expireAt().isAfter(publishAt)) throw new BizException(WorkbenchErrorCodes.NOTICE_EXPIRE_BEFORE_PUBLISH);
        n.setTitle(WbSupport.limit(req.title().trim(), 128));
        n.setContent(sanitize(req.content()));
        n.setScope(scope);
        n.setDeptIds("DEPT".equals(scope) ? depts.stream().map(String::valueOf).collect(Collectors.joining(",")) : null);
        n.setImportant(req.important());
        n.setPublishAt(publishAt);
        n.setExpireAt(req.expireAt());
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        WbNoticeDO n = get(id);
        if (PUBLISHED.equals(n.getNoticeStatus())) throw BizException.of(WorkbenchErrorCodes.STATUS_NOT_ALLOWED, "已发布", "删除");
        mapper.deleteById(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public void publish(Long id) {
        WbNoticeDO n = get(id);
        if (PUBLISHED.equals(n.getNoticeStatus())) return;
        n.setNoticeStatus(PUBLISHED);
        n.setPublisherId(support.currentUser());
        if (n.getPublishAt() == null) n.setPublishAt(LocalDateTime.now());
        mapper.updateByIdOrFail(n);
    }

    @Transactional(rollbackFor = Exception.class)
    public void withdraw(Long id) {
        WbNoticeDO n = get(id);
        if (!PUBLISHED.equals(n.getNoticeStatus())) throw BizException.of(WorkbenchErrorCodes.STATUS_NOT_ALLOWED, label(n.getNoticeStatus()), "撤回");
        n.setNoticeStatus(WITHDRAWN);
        mapper.updateByIdOrFail(n);
    }

    public WbNoticeDO get(Long id) {
        WbNoticeDO n = id == null ? null : mapper.selectById(id);
        if (n == null) throw BizException.of(WorkbenchErrorCodes.NOT_EXISTS, "公告");
        return n;
    }

    static String label(String status) {
        return switch (status) {
            case PUBLISHED -> "已发布";
            case WITHDRAWN -> "已撤回";
            default -> "草稿";
        };
    }

    public PageResult<NoticeRow> page(NoticeQuery q) {
        IPage<WbNoticeDO> p = mapper.selectPage(new Page<>(q.getPageNo(), q.getPageSize()), new LambdaQueryWrapper<WbNoticeDO>()
                .like(StringUtils.hasText(q.getKeyword()), WbNoticeDO::getTitle, q.getKeyword())
                .eq(StringUtils.hasText(q.getStatus()), WbNoticeDO::getNoticeStatus, q.getStatus())
                .orderByDesc(WbNoticeDO::getPublishAt).orderByDesc(WbNoticeDO::getId));
        Map<Long, UserDTO> users = support.users(p.getRecords().stream().map(WbNoticeDO::getPublisherId).toList());
        Map<Long, OrgDTO> orgs = support.orgs(p.getRecords().stream().flatMap(n -> depts(n).stream()).toList());
        return new PageResult<>(p.getRecords().stream().map(n -> row(n, users, orgs)).toList(), p.getTotal());
    }

    private NoticeRow row(WbNoticeDO n, Map<Long, UserDTO> users, Map<Long, OrgDTO> orgs) {
        List<Long> depts = depts(n);
        long read = readMapper.selectCount(new LambdaQueryWrapper<WbNoticeReadDO>().eq(WbNoticeReadDO::getNoticeId, n.getId()));
        return new NoticeRow(n.getId(), n.getTitle(), n.getScope(), depts,
                depts.stream().map(d -> orgs.containsKey(d) ? orgs.get(d).name() : String.valueOf(d)).collect(Collectors.joining("、")),
                Boolean.TRUE.equals(n.getImportant()), n.getPublishAt(), n.getExpireAt(), n.getNoticeStatus(), read, targets(n).size(),
                WbSupport.name(users, n.getPublisherId()), n.getCreatedAt());
    }

    public NoticeDetail detail(Long id) {
        WbNoticeDO n = get(id);
        Map<Long, OrgDTO> orgs = support.orgs(depts(n));
        boolean read = readMapper.selectCount(new LambdaQueryWrapper<WbNoticeReadDO>().eq(WbNoticeReadDO::getNoticeId, id)
                .eq(WbNoticeReadDO::getUserId, support.currentUser())) > 0;
        return new NoticeDetail(row(n, support.users(java.util.Collections.singletonList(n.getPublisherId())), orgs), n.getContent(), read,
                fileApi.list(BIZ_TYPE, id));
    }

    /** 阅读情况：已读用户 */
    public List<NoticeReader> readers(Long id) {
        get(id);
        List<WbNoticeReadDO> list = readMapper.selectList(new LambdaQueryWrapper<WbNoticeReadDO>().eq(WbNoticeReadDO::getNoticeId, id)
                .orderByDesc(WbNoticeReadDO::getReadAt));
        Map<Long, UserDTO> users = support.users(list.stream().map(WbNoticeReadDO::getUserId).toList());
        return list.stream().map(r -> {
            UserDTO u = users.get(r.getUserId());
            return new NoticeReader(r.getUserId(), u == null ? null : u.realName(), u == null ? null : u.deptName(), r.getReadAt());
        }).toList();
    }

    static List<Long> depts(WbNoticeDO n) {
        if (!StringUtils.hasText(n.getDeptIds())) return List.of();
        return Arrays.stream(n.getDeptIds().split(",")).filter(StringUtils::hasText).map(s -> Long.valueOf(s.trim())).toList();
    }

    /** 范围部门（含下级） */
    private Set<Long> scopeDepts(WbNoticeDO n) {
        Set<Long> set = new HashSet<>();
        for (Long d : depts(n)) set.addAll(support.orgApi().getSelfAndChildrenIds(d));
        return set;
    }

    /** 应读用户 */
    private List<UserDTO> targets(WbNoticeDO n) {
        if (!"DEPT".equals(n.getScope())) return support.userApi().listEnabled(null);
        Set<Long> depts = scopeDepts(n);
        return depts.isEmpty() ? List.of() : support.userApi().listEnabled(depts);
    }

    // ==================== 用户侧 ====================

    /** 当前用户可见的、已发布未过期的公告（首页、登录弹窗） */
    public List<ActiveNotice> active(boolean importantUnreadOnly) {
        Long me = support.currentUser();
        Long myDept = support.userApi().get(me).map(UserDTO::deptId).orElse(null);
        LocalDateTime now = LocalDateTime.now();
        List<WbNoticeDO> list = mapper.selectList(new LambdaQueryWrapper<WbNoticeDO>().eq(WbNoticeDO::getNoticeStatus, PUBLISHED)
                .le(WbNoticeDO::getPublishAt, now).and(w -> w.isNull(WbNoticeDO::getExpireAt).or().gt(WbNoticeDO::getExpireAt, now))
                .eq(importantUnreadOnly, WbNoticeDO::getImportant, true)
                .orderByDesc(WbNoticeDO::getImportant).orderByDesc(WbNoticeDO::getPublishAt).last("LIMIT 50"));
        Set<Long> readIds = list.isEmpty() ? Set.of() : readMapper.selectList(new LambdaQueryWrapper<WbNoticeReadDO>().eq(WbNoticeReadDO::getUserId, me)
                .in(WbNoticeReadDO::getNoticeId, list.stream().map(WbNoticeDO::getId).toList())).stream().map(WbNoticeReadDO::getNoticeId)
                .collect(Collectors.toSet());
        return list.stream().filter(n -> !"DEPT".equals(n.getScope()) || (myDept != null && scopeDepts(n).contains(myDept)))
                .filter(n -> !importantUnreadOnly || !readIds.contains(n.getId()))
                .map(n -> new ActiveNotice(n.getId(), n.getTitle(), n.getContent(), Boolean.TRUE.equals(n.getImportant()), n.getPublishAt(),
                        readIds.contains(n.getId())))
                .toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public void read(Long id) {
        get(id);
        Long me = support.currentUser();
        if (readMapper.selectCount(new LambdaQueryWrapper<WbNoticeReadDO>().eq(WbNoticeReadDO::getNoticeId, id).eq(WbNoticeReadDO::getUserId, me)) > 0) return;
        WbNoticeReadDO r = new WbNoticeReadDO();
        r.setNoticeId(id);
        r.setUserId(me);
        r.setReadAt(LocalDateTime.now());
        try {
            readMapper.insert(r);
        } catch (DuplicateKeyException ignored) {
            // 并发重复阅读
        }
    }
}
