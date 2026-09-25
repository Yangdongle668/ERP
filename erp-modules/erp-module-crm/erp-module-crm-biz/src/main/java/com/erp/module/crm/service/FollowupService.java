package com.erp.module.crm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.common.exception.BizException;
import com.erp.common.result.PageResult;
import com.erp.module.crm.api.CrmErrorCodes;
import com.erp.module.crm.config.CrmModuleConfig;
import com.erp.module.crm.controller.vo.FollowupVOs.FollowupQuery;
import com.erp.module.crm.controller.vo.FollowupVOs.FollowupRow;
import com.erp.module.crm.controller.vo.FollowupVOs.FollowupSave;
import com.erp.module.crm.dal.dataobject.CustomerContactDO;
import com.erp.module.crm.dal.dataobject.CustomerDO;
import com.erp.module.crm.dal.dataobject.FollowupDO;
import com.erp.module.crm.dal.dataobject.OpportunityDO;
import com.erp.module.crm.dal.mapper.CustomerContactMapper;
import com.erp.module.crm.dal.mapper.FollowupMapper;
import com.erp.module.crm.dal.mapper.OpportunityMapper;
import com.erp.module.system.api.file.FileApi;
import com.erp.module.system.api.job.ErpJob;
import com.erp.module.system.api.notify.NotifyApi;
import com.erp.module.system.api.notify.TodoCreatedEvent;
import com.erp.module.system.api.notify.TodoDoneEvent;
import com.erp.module.system.api.user.UserDTO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 跟进记录（需求 03-04）：R01 时间校验；R02 只有记录人本人 24 小时内可修改、删除；
 * R03 每天按参数时间提醒当天到期的下次跟进（同一客户已有更晚的跟进时视为已跟进）；R04 能看到客户就能看到跟进。
 */
@Service("crmFollowupService")
public class FollowupService {

    public static final String BIZ_TYPE = CrmModuleConfig.FOLLOWUP;

    private final FollowupMapper mapper;
    private final CustomerContactMapper contactMapper;
    private final OpportunityMapper opportunityMapper;
    private final CustomerService customerService;
    private final CrmSupport support;
    private final FileApi fileApi;
    private final NotifyApi notifyApi;

    public FollowupService(FollowupMapper mapper, CustomerContactMapper contactMapper, OpportunityMapper opportunityMapper,
                           CustomerService customerService, CrmSupport support, FileApi fileApi, NotifyApi notifyApi) {
        this.mapper = mapper;
        this.contactMapper = contactMapper;
        this.opportunityMapper = opportunityMapper;
        this.customerService = customerService;
        this.support = support;
        this.fileApi = fileApi;
        this.notifyApi = notifyApi;
    }

    public PageResult<FollowupRow> page(FollowupQuery q) {
        LambdaQueryWrapper<FollowupDO> w = new LambdaQueryWrapper<FollowupDO>()
                .eq(q.getCustomerId() != null, FollowupDO::getCustomerId, q.getCustomerId())
                .eq(q.getOwnerId() != null, FollowupDO::getOwnerId, q.getOwnerId())
                .eq(StringUtils.hasText(q.getFollowupType()), FollowupDO::getFollowupType, q.getFollowupType())
                .eq(q.getOpportunityId() != null, FollowupDO::getOpportunityId, q.getOpportunityId())
                .ge(q.getDateFrom() != null, FollowupDO::getFollowupAt, q.getDateFrom() == null ? null : q.getDateFrom().atStartOfDay())
                .lt(q.getDateTo() != null, FollowupDO::getFollowupAt, q.getDateTo() == null ? null : q.getDateTo().plusDays(1).atStartOfDay());
        if (Boolean.TRUE.equals(q.getPendingOnly())) {
            w.isNotNull(FollowupDO::getNextFollowupAt).le(FollowupDO::getNextFollowupAt, LocalDate.now().plusDays(7))
                    .apply("NOT EXISTS (SELECT 1 FROM crm_followup f2 WHERE f2.deleted = 0 AND f2.customer_id = crm_followup.customer_id "
                            + "AND f2.followup_at > crm_followup.followup_at)");
        }
        String scope = CrmScope.visibleCustomerIds();
        if (scope != null) w.inSql(FollowupDO::getCustomerId, scope);
        w.orderByDesc(FollowupDO::getFollowupAt).orderByDesc(FollowupDO::getId);
        PageResult<FollowupDO> page = mapper.selectPage(q, w);
        return new PageResult<>(rows(page.list()), page.total());
    }

    private List<FollowupRow> rows(List<FollowupDO> list) {
        if (list.isEmpty()) return List.of();
        Map<Long, CustomerDO> customers = customerService.byIds(list.stream().map(FollowupDO::getCustomerId).toList());
        List<Long> contactIds = list.stream().map(FollowupDO::getContactId).filter(Objects::nonNull).distinct().toList();
        Map<Long, CustomerContactDO> contacts = contactIds.isEmpty() ? Map.of()
                : contactMapper.selectBatchIds(contactIds).stream().collect(Collectors.toMap(CustomerContactDO::getId, Function.identity()));
        List<Long> oppIds = list.stream().map(FollowupDO::getOpportunityId).filter(Objects::nonNull).distinct().toList();
        Map<Long, OpportunityDO> opps = oppIds.isEmpty() ? Map.of()
                : opportunityMapper.selectBatchIds(oppIds).stream().collect(Collectors.toMap(OpportunityDO::getId, Function.identity()));
        Map<Long, UserDTO> users = support.users(list.stream().map(FollowupDO::getOwnerId).toList());
        LocalDate today = LocalDate.now();
        Long me = support.currentUser();
        return list.stream().map(f -> {
            CustomerDO c = customers.get(f.getCustomerId());
            CustomerContactDO ct = f.getContactId() == null ? null : contacts.get(f.getContactId());
            OpportunityDO o = f.getOpportunityId() == null ? null : opps.get(f.getOpportunityId());
            return new FollowupRow(f.getId(), f.getCustomerId(), c == null ? null : c.getCode(), c == null ? null : c.getShortName(), f.getContactId(),
                    ct == null ? null : ct.getName(), f.getOpportunityId(), o == null ? null : o.getName(), f.getFollowupType(), f.getFollowupAt(),
                    f.getSubject(), f.getContent(), f.getNextFollowupAt(), f.getNextPlan(),
                    f.getNextFollowupAt() != null && !f.getNextFollowupAt().isAfter(today), f.getOwnerId(), CrmSupport.name(users, f.getOwnerId()),
                    fileApi.list(BIZ_TYPE, f.getId()).size(), editable(f, me), f.getVersion());
        }).toList();
    }

    static boolean editable(FollowupDO f, Long me) {
        return me != null && me.equals(f.getOwnerId()) && f.getCreatedAt() != null && f.getCreatedAt().isAfter(LocalDateTime.now().minusHours(24));
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(FollowupSave req) {
        FollowupDO f = new FollowupDO();
        f.setOwnerId(support.currentUser());
        f.setReminded(false);
        fill(f, req);
        mapper.insert(f);
        if (req.fileIds() != null && !req.fileIds().isEmpty()) fileApi.bind(req.fileIds(), BIZ_TYPE, f.getId());
        closeEarlierReminders(f);
        return f.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, FollowupSave req) {
        FollowupDO f = getEditable(id);
        if (req.version() != null) f.setVersion(req.version());
        Long oldCustomer = f.getCustomerId();
        fill(f, req);
        if (!oldCustomer.equals(f.getCustomerId())) throw BizException.of(CrmErrorCodes.CUSTOMER_FIELD_INVALID, "不能修改跟进记录的客户");
        mapper.updateByIdOrFail(f);
        if (req.fileIds() != null && !req.fileIds().isEmpty()) fileApi.bind(req.fileIds(), BIZ_TYPE, f.getId());
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        FollowupDO f = getEditable(id);
        mapper.deleteById(f.getId());
        fileApi.deleteByBiz(BIZ_TYPE, f.getId());
        notifyApi.done(new TodoDoneEvent(todoKey(f.getId()), null, TodoDoneEvent.Result.CANCELED));
    }

    /** R01 */
    private void fill(FollowupDO f, FollowupSave req) {
        CustomerDO c = customerService.getVisible(req.customerId());
        if (req.followupAt().isAfter(LocalDateTime.now().plusMinutes(1))) throw new BizException(CrmErrorCodes.FOLLOWUP_FUTURE);
        if (req.nextFollowupAt() != null && req.nextFollowupAt().isBefore(LocalDate.now())) throw new BizException(CrmErrorCodes.FOLLOWUP_NEXT_DATE);
        support.dict().validate("crm_followup_type", req.followupType(), "跟进方式");
        if (req.contactId() != null) {
            CustomerContactDO ct = contactMapper.selectById(req.contactId());
            if (ct == null || !ct.getCustomerId().equals(c.getId())) throw BizException.of(CrmErrorCodes.CUSTOMER_FIELD_INVALID, "联系人不属于该客户");
        }
        if (req.opportunityId() != null) {
            OpportunityDO o = opportunityMapper.selectById(req.opportunityId());
            if (o == null || !o.getCustomerId().equals(c.getId())) throw BizException.of(CrmErrorCodes.CUSTOMER_FIELD_INVALID, "商机不属于该客户");
        }
        f.setCustomerId(c.getId());
        f.setContactId(req.contactId());
        f.setOpportunityId(req.opportunityId());
        f.setFollowupType(req.followupType());
        f.setFollowupAt(req.followupAt());
        f.setSubject(req.subject().trim());
        f.setContent(req.content().trim());
        if (!Objects.equals(f.getNextFollowupAt(), req.nextFollowupAt())) f.setReminded(false);
        f.setNextFollowupAt(req.nextFollowupAt());
        f.setNextPlan(CrmSupport.trim(req.nextPlan()));
    }

    /** R02：只有记录人本人 24 小时内可以修改、删除 */
    private FollowupDO getEditable(Long id) {
        FollowupDO f = id == null ? null : mapper.selectById(id);
        if (f == null) throw new BizException(CrmErrorCodes.FOLLOWUP_NOT_EXISTS);
        customerService.getVisible(f.getCustomerId());
        if (!Objects.equals(f.getOwnerId(), support.currentUser())) throw new BizException(CrmErrorCodes.FOLLOWUP_NOT_MINE);
        if (!editable(f, support.currentUser())) throw new BizException(CrmErrorCodes.FOLLOWUP_LOCKED);
        return f;
    }

    /** 新增跟进后，同一客户之前已提醒的跟进待办视为已完成 */
    private void closeEarlierReminders(FollowupDO f) {
        List<FollowupDO> earlier = mapper.selectList(new LambdaQueryWrapper<FollowupDO>().eq(FollowupDO::getCustomerId, f.getCustomerId())
                .ne(FollowupDO::getId, f.getId()).lt(FollowupDO::getFollowupAt, f.getFollowupAt()).isNotNull(FollowupDO::getNextFollowupAt));
        for (FollowupDO e : earlier) {
            if (Boolean.TRUE.equals(e.getReminded())) {
                notifyApi.done(new TodoDoneEvent(todoKey(e.getId()), null, TodoDoneEvent.Result.DONE));
            } else {
                e.setReminded(true);
                mapper.updateByIdOrFail(e);
            }
        }
    }

    static String todoKey(Long followupId) {
        return "CRM_FOLLOWUP:" + followupId;
    }

    /**
     * R03：每 30 分钟检查一次，到达参数 crm.followup.remind-time 后提醒下次跟进日期 ≤ 今天且未提醒的记录；
     * 同一客户已有更晚的跟进时视为已跟进，不再提醒。
     */
    @ErpJob(code = "CRM_FOLLOWUP_REMIND", name = "客户跟进提醒", cron = "0 0/30 * * * ?")
    @Transactional(rollbackFor = Exception.class)
    public String remind() {
        LocalTime at;
        try {
            at = LocalTime.parse(Objects.requireNonNullElse(support.param().getString(CrmModuleConfig.P_FOLLOWUP_REMIND), "09:00"));
        } catch (RuntimeException e) {
            at = LocalTime.of(9, 0);
        }
        if (LocalTime.now().isBefore(at)) return "未到提醒时间 " + at;
        return remindNow();
    }

    /** 立即执行提醒（不检查提醒时间） */
    @Transactional(rollbackFor = Exception.class)
    public String remindNow() {
        LocalDate today = LocalDate.now();
        List<FollowupDO> due = mapper.selectList(new LambdaQueryWrapper<FollowupDO>().eq(FollowupDO::getReminded, false)
                .isNotNull(FollowupDO::getNextFollowupAt).le(FollowupDO::getNextFollowupAt, today));
        Map<Long, CustomerDO> customers = customerService.byIds(due.stream().map(FollowupDO::getCustomerId).toList());
        int sent = 0;
        for (FollowupDO f : due) {
            f.setReminded(true);
            mapper.updateByIdOrFail(f);
            boolean followed = mapper.selectCount(new LambdaQueryWrapper<FollowupDO>().eq(FollowupDO::getCustomerId, f.getCustomerId())
                    .gt(FollowupDO::getFollowupAt, f.getFollowupAt())) > 0;
            CustomerDO c = customers.get(f.getCustomerId());
            if (followed || c == null) continue;
            notifyApi.todo(new TodoCreatedEvent(todoKey(f.getId()), List.of(f.getOwnerId()), TodoCreatedEvent.Category.TASK, BIZ_TYPE, f.getId(),
                    c.getCode(), "跟进客户 " + c.getShortName() + (f.getNextPlan() == null ? "" : "：" + f.getNextPlan()), "/crm/customer/" + c.getId(),
                    TodoCreatedEvent.Priority.NORMAL, null));
            sent++;
        }
        return "提醒 " + sent + " 条";
    }
}
