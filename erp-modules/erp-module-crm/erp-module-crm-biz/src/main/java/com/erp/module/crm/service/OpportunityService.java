package com.erp.module.crm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.common.exception.BizException;
import com.erp.common.result.PageResult;
import com.erp.module.crm.api.CrmErrorCodes;
import com.erp.module.crm.api.opportunity.OpportunityApi;
import com.erp.module.crm.config.CrmModuleConfig;
import com.erp.module.crm.controller.vo.OpportunityVOs.Funnel;
import com.erp.module.crm.controller.vo.OpportunityVOs.FunnelStage;
import com.erp.module.crm.controller.vo.OpportunityVOs.OppQuery;
import com.erp.module.crm.controller.vo.OpportunityVOs.OppRow;
import com.erp.module.crm.controller.vo.OpportunityVOs.OppSave;
import com.erp.module.crm.dal.dataobject.CustomerContactDO;
import com.erp.module.crm.dal.dataobject.CustomerDO;
import com.erp.module.crm.dal.dataobject.FollowupDO;
import com.erp.module.crm.dal.dataobject.OpportunityDO;
import com.erp.module.crm.dal.mapper.CustomerContactMapper;
import com.erp.module.crm.dal.mapper.FollowupMapper;
import com.erp.module.crm.dal.mapper.OpportunityMapper;
import com.erp.module.system.api.currency.CurrencyApi;
import com.erp.module.system.api.user.UserDTO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 商机（需求 03-05）：阶段 CONTACT → REQUIREMENT → QUOTATION → NEGOTIATION，结果 WON / LOST / SHELVED；
 * 已结束（赢单/输单）的商机不能修改（R02）；报价、订单节点由销售模块通过 {@link OpportunityApi} 回调（R03、R04）。
 */
@Service("crmOpportunityService")
public class OpportunityService implements OpportunityApi {

    public static final String BIZ_TYPE = CrmModuleConfig.OPPORTUNITY;
    /** 进行中的阶段及默认赢率 */
    static final Map<String, BigDecimal> STAGE_RATES = new LinkedHashMap<>();

    static {
        STAGE_RATES.put("CONTACT", new BigDecimal("0.10"));
        STAGE_RATES.put("REQUIREMENT", new BigDecimal("0.30"));
        STAGE_RATES.put("QUOTATION", new BigDecimal("0.50"));
        STAGE_RATES.put("NEGOTIATION", new BigDecimal("0.70"));
    }

    static final List<String> STAGES = List.copyOf(STAGE_RATES.keySet());
    static final Map<String, String> STATUS_LABELS = Map.of("OPEN", "进行中", "WON", "赢单", "LOST", "输单", "SHELVED", "搁置");

    private final OpportunityMapper mapper;
    private final FollowupMapper followupMapper;
    private final CustomerContactMapper contactMapper;
    private final CustomerService customerService;
    private final CrmSupport support;
    private final CurrencyApi currencyApi;

    public OpportunityService(OpportunityMapper mapper, FollowupMapper followupMapper, CustomerContactMapper contactMapper,
                              CustomerService customerService, CrmSupport support, CurrencyApi currencyApi) {
        this.mapper = mapper;
        this.followupMapper = followupMapper;
        this.contactMapper = contactMapper;
        this.customerService = customerService;
        this.support = support;
        this.currencyApi = currencyApi;
    }

    // ==================== 查询 ====================

    public PageResult<OppRow> page(OppQuery q) {
        PageResult<OpportunityDO> page = mapper.selectPage(q, query(q));
        return new PageResult<>(rows(page.list()), page.total());
    }

    private LambdaQueryWrapper<OpportunityDO> query(OppQuery q) {
        LambdaQueryWrapper<OpportunityDO> w = new LambdaQueryWrapper<OpportunityDO>()
                .eq(q.getCustomerId() != null, OpportunityDO::getCustomerId, q.getCustomerId())
                .eq(q.getOwnerId() != null, OpportunityDO::getOwnerId, q.getOwnerId())
                .eq(StringUtils.hasText(q.getStage()), OpportunityDO::getStage, q.getStage())
                .ge(q.getExpectedFrom() != null, OpportunityDO::getExpectedDate, q.getExpectedFrom())
                .le(q.getExpectedTo() != null, OpportunityDO::getExpectedDate, q.getExpectedTo());
        if (StringUtils.hasText(q.getKeyword())) {
            String k = q.getKeyword().trim();
            w.and(x -> x.likeRight(OpportunityDO::getCode, k.toUpperCase(Locale.ROOT)).or().like(OpportunityDO::getName, k));
        }
        List<String> statuses = StringUtils.hasText(q.getStatuses()) ? Arrays.stream(q.getStatuses().split(",")).map(String::trim).toList() : List.of("OPEN");
        w.in(OpportunityDO::getOppStatus, statuses);
        String scope = CrmScope.visibleCustomerIds();
        if (scope != null) w.inSql(OpportunityDO::getCustomerId, scope);
        return w.orderByAsc(OpportunityDO::getExpectedDate).orderByDesc(OpportunityDO::getId);
    }

    private List<OppRow> rows(List<OpportunityDO> list) {
        if (list.isEmpty()) return List.of();
        Map<Long, CustomerDO> customers = customerService.byIds(list.stream().map(OpportunityDO::getCustomerId).toList());
        List<Long> contactIds = list.stream().map(OpportunityDO::getContactId).filter(Objects::nonNull).distinct().toList();
        Map<Long, CustomerContactDO> contacts = contactIds.isEmpty() ? Map.of()
                : contactMapper.selectBatchIds(contactIds).stream().collect(Collectors.toMap(CustomerContactDO::getId, Function.identity()));
        Map<Long, UserDTO> users = support.users(list.stream().map(OpportunityDO::getOwnerId).toList());
        Map<Long, LocalDateTime> lastFollowup = new HashMap<>();
        followupMapper.selectList(new LambdaQueryWrapper<FollowupDO>().in(FollowupDO::getOpportunityId, list.stream().map(OpportunityDO::getId).toList()))
                .forEach(f -> lastFollowup.merge(f.getOpportunityId(), f.getFollowupAt(), (a, b) -> a.isAfter(b) ? a : b));
        LocalDate today = LocalDate.now();
        return list.stream().map(o -> {
            CustomerDO c = customers.get(o.getCustomerId());
            CustomerContactDO ct = o.getContactId() == null ? null : contacts.get(o.getContactId());
            return new OppRow(o.getId(), o.getCode(), o.getName(), o.getCustomerId(), c == null ? null : c.getCode(), c == null ? null : c.getShortName(),
                    o.getContactId(), ct == null ? null : ct.getName(), o.getStage(), o.getOppStatus(), o.getAmount(), o.getCurrency(), o.getWinRate(),
                    o.getExpectedDate(), "OPEN".equals(o.getOppStatus()) && o.getExpectedDate().isBefore(today), o.getProducts(), o.getCompetitor(),
                    o.getOwnerId(), CrmSupport.name(users, o.getOwnerId()), lastFollowup.get(o.getId()), o.getLostReason(), o.getLostRemark(),
                    o.getWonOrderNo(), o.getRemark(), o.getClosedAt(), o.getVersion());
        }).toList();
    }

    public OppRow detail(Long id) {
        return rows(List.of(getVisible(id))).get(0);
    }

    /** 漏斗（R05）：进行中商机按阶段汇总，金额按当日汇率折算本位币；加权 = 金额 × 赢率 */
    public Funnel funnel(OppQuery q) {
        q.setStatuses("OPEN");
        q.setStage(null);
        List<OpportunityDO> list = mapper.selectList(query(q));
        String base = currencyApi.getBaseCurrency();
        LocalDate today = LocalDate.now();
        Map<String, BigDecimal> rates = new HashMap<>();
        Map<String, int[]> counts = new LinkedHashMap<>();
        Map<String, BigDecimal[]> sums = new LinkedHashMap<>();
        for (String s : STAGES) {
            counts.put(s, new int[1]);
            sums.put(s, new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
        }
        for (OpportunityDO o : list) {
            BigDecimal rate = base.equals(o.getCurrency()) ? BigDecimal.ONE
                    : rates.computeIfAbsent(o.getCurrency(), cur -> Objects.requireNonNullElse(currencyApi.getRate(cur, today), BigDecimal.ONE));
            BigDecimal amount = o.getAmount().multiply(rate);
            if (!counts.containsKey(o.getStage())) continue;
            counts.get(o.getStage())[0]++;
            BigDecimal[] s = sums.get(o.getStage());
            s[0] = s[0].add(amount);
            s[1] = s[1].add(amount.multiply(o.getWinRate()));
        }
        List<FunnelStage> stages = new ArrayList<>();
        int total = 0;
        BigDecimal totalAmount = BigDecimal.ZERO;
        BigDecimal totalWeighted = BigDecimal.ZERO;
        for (String s : STAGES) {
            BigDecimal[] v = sums.get(s);
            BigDecimal amount = currencyApi.roundAmount(v[0], base);
            BigDecimal weighted = currencyApi.roundAmount(v[1], base);
            stages.add(new FunnelStage(s, counts.get(s)[0], amount, weighted));
            total += counts.get(s)[0];
            totalAmount = totalAmount.add(amount);
            totalWeighted = totalWeighted.add(weighted);
        }
        return new Funnel(base, stages, total, totalAmount, totalWeighted);
    }

    // ==================== 编辑 ====================

    @Transactional(rollbackFor = Exception.class)
    public Long create(OppSave req) {
        OpportunityDO o = new OpportunityDO();
        o.setCode(support.nextNo(BIZ_TYPE));
        o.setOppStatus("OPEN");
        fill(o, req, true);
        mapper.insert(o);
        support.log(BIZ_TYPE, o.getId(), o.getCode(), "CREATE", "新建", null, o.getStage(), null);
        return o.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, OppSave req) {
        OpportunityDO o = getVisible(id);
        requireNotClosed(o);
        if (req.version() != null) o.setVersion(req.version());
        String oldStage = o.getStage();
        fill(o, req, false);
        mapper.updateByIdOrFail(o);
        if (!oldStage.equals(o.getStage())) support.log(BIZ_TYPE, o.getId(), o.getCode(), "STAGE", "推进阶段", oldStage, o.getStage(), null);
    }

    private void fill(OpportunityDO o, OppSave req, boolean creating) {
        CustomerDO c = customerService.getVisible(req.customerId());
        if (!creating && !c.getId().equals(o.getCustomerId())) throw BizException.of(CrmErrorCodes.CUSTOMER_FIELD_INVALID, "不能修改商机的客户");
        if (creating) customerService.validateCanQuote(c.getId());
        if (req.contactId() != null) {
            CustomerContactDO ct = contactMapper.selectById(req.contactId());
            if (ct == null || !ct.getCustomerId().equals(c.getId())) throw BizException.of(CrmErrorCodes.CUSTOMER_FIELD_INVALID, "联系人不属于该客户");
        }
        String stage = StringUtils.hasText(req.stage()) ? req.stage() : (creating ? "CONTACT" : o.getStage());
        if (!STAGE_RATES.containsKey(stage)) throw BizException.of(CrmErrorCodes.CUSTOMER_FIELD_INVALID, "阶段不正确");
        String currency = StringUtils.hasText(req.currency()) ? req.currency().trim().toUpperCase(Locale.ROOT) : (creating ? c.getCurrency() : o.getCurrency());
        if (!currency.equals(o.getCurrency())) currencyApi.validate(currency);
        Long owner = req.ownerId() != null ? req.ownerId() : (creating ? c.getOwnerId() : o.getOwnerId());
        UserDTO u = support.user(owner);
        if (u == null) throw BizException.of(CrmErrorCodes.CUSTOMER_FIELD_INVALID, "负责人不存在");
        boolean stageChanged = !stage.equals(o.getStage());
        o.setName(req.name().trim());
        o.setCustomerId(c.getId());
        o.setContactId(req.contactId());
        o.setStage(stage);
        o.setAmount(req.amount());
        o.setCurrency(currency);
        o.setWinRate(req.winRate() != null ? req.winRate() : (stageChanged || o.getWinRate() == null ? STAGE_RATES.get(stage) : o.getWinRate()));
        o.setExpectedDate(req.expectedDate());
        o.setProducts(CrmSupport.trim(req.products()));
        o.setCompetitor(CrmSupport.trim(req.competitor()));
        o.setOwnerId(owner);
        o.setDeptId(u.deptId());
        o.setOrgId(support.companyOf(u.deptId(), u.orgId()));
        o.setRemark(CrmSupport.trim(req.remark()));
    }

    /** 推进阶段（赢率同步为新阶段默认值） */
    @Transactional(rollbackFor = Exception.class)
    public void changeStage(Long id, String stage) {
        OpportunityDO o = getVisible(id);
        requireOpen(o, "推进阶段");
        if (!STAGE_RATES.containsKey(stage)) throw BizException.of(CrmErrorCodes.CUSTOMER_FIELD_INVALID, "阶段不正确");
        if (stage.equals(o.getStage())) return;
        String old = o.getStage();
        o.setStage(stage);
        o.setWinRate(STAGE_RATES.get(stage));
        mapper.updateByIdOrFail(o);
        support.log(BIZ_TYPE, o.getId(), o.getCode(), "STAGE", "推进阶段", old, stage, null);
    }

    @Transactional(rollbackFor = Exception.class)
    public void win(Long id, String orderNo) {
        OpportunityDO o = getVisible(id);
        requireOpen(o, "赢单");
        close(o, "WON", CrmSupport.trim(orderNo));
    }

    /** R01：输单必须选择原因 */
    @Transactional(rollbackFor = Exception.class)
    public void lose(Long id, String reason, String remark) {
        if (!StringUtils.hasText(reason)) throw new BizException(CrmErrorCodes.OPP_LOST_REASON);
        OpportunityDO o = getVisible(id);
        requireOpen(o, "输单");
        support.dict().validate("crm_lost_reason", reason, "输单原因");
        o.setLostReason(reason);
        o.setLostRemark(CrmSupport.trim(remark));
        close(o, "LOST", remark);
    }

    private void close(OpportunityDO o, String result, String note) {
        String from = o.getOppStatus();
        o.setOppStatus(result);
        o.setStage(result);
        o.setWinRate("WON".equals(result) ? BigDecimal.ONE : BigDecimal.ZERO);
        if ("WON".equals(result)) o.setWonOrderNo(note);
        o.setClosedAt(LocalDateTime.now());
        mapper.updateByIdOrFail(o);
        support.log(BIZ_TYPE, o.getId(), o.getCode(), result, STATUS_LABELS.get(result), from, result, note);
    }

    @Transactional(rollbackFor = Exception.class)
    public void shelve(Long id, String remark) {
        OpportunityDO o = getVisible(id);
        requireOpen(o, "搁置");
        o.setOppStatus("SHELVED");
        mapper.updateByIdOrFail(o);
        support.log(BIZ_TYPE, o.getId(), o.getCode(), "SHELVE", "搁置", "OPEN", "SHELVED", CrmSupport.trim(remark));
    }

    /** 恢复：回到进行中，阶段不变 */
    @Transactional(rollbackFor = Exception.class)
    public void resume(Long id) {
        OpportunityDO o = getVisible(id);
        if (!"SHELVED".equals(o.getOppStatus())) throw BizException.of(CrmErrorCodes.OPP_STATUS, STATUS_LABELS.get(o.getOppStatus()), "恢复");
        o.setOppStatus("OPEN");
        mapper.updateByIdOrFail(o);
        support.log(BIZ_TYPE, o.getId(), o.getCode(), "RESUME", "恢复", "SHELVED", "OPEN", null);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        OpportunityDO o = getVisible(id);
        requireNotClosed(o);
        followupMapper.selectList(new LambdaQueryWrapper<FollowupDO>().eq(FollowupDO::getOpportunityId, id)).forEach(f -> {
            f.setOpportunityId(null);
            followupMapper.updateById(f);
        });
        mapper.deleteById(id);
    }

    private static void requireNotClosed(OpportunityDO o) {
        if ("WON".equals(o.getOppStatus()) || "LOST".equals(o.getOppStatus())) throw new BizException(CrmErrorCodes.OPP_CLOSED);
    }

    private static void requireOpen(OpportunityDO o, String action) {
        requireNotClosed(o);
        if (!"OPEN".equals(o.getOppStatus())) throw BizException.of(CrmErrorCodes.OPP_STATUS, STATUS_LABELS.get(o.getOppStatus()), action);
    }

    public OpportunityDO getVisible(Long id) {
        OpportunityDO o = id == null ? null : mapper.selectById(id);
        if (o == null) throw new BizException(CrmErrorCodes.OPP_NOT_EXISTS);
        customerService.getVisible(o.getCustomerId());
        return o;
    }

    // ==================== OpportunityApi ====================

    /** R03：报价单关联商机时，阶段在“报价”之前的自动推进到“报价” */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void onQuotationCreated(Long opportunityId) {
        OpportunityDO o = opportunityId == null ? null : mapper.selectById(opportunityId);
        if (o == null || !"OPEN".equals(o.getOppStatus())) return;
        if (STAGES.indexOf(o.getStage()) >= STAGES.indexOf("QUOTATION")) return;
        String old = o.getStage();
        o.setStage("QUOTATION");
        o.setWinRate(STAGE_RATES.get("QUOTATION"));
        mapper.updateByIdOrFail(o);
        support.log(BIZ_TYPE, o.getId(), o.getCode(), "STAGE", "报价自动推进", old, "QUOTATION", null);
    }

    /** R04：订单审核后商机自动赢单并记录订单号 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void onOrderApproved(Long opportunityId, String orderNo) {
        OpportunityDO o = opportunityId == null ? null : mapper.selectById(opportunityId);
        if (o == null || "WON".equals(o.getOppStatus()) || "LOST".equals(o.getOppStatus())) return;
        close(o, "WON", orderNo);
    }
}
