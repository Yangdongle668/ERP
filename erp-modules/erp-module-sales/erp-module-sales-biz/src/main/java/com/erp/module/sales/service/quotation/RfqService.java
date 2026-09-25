package com.erp.module.sales.service.quotation;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.common.exception.BizException;
import com.erp.common.exception.GlobalErrorCodes;
import com.erp.common.result.PageResult;
import com.erp.common.util.Decimals;
import com.erp.framework.datascope.DataScopes;
import com.erp.module.crm.api.customer.ContactDTO;
import com.erp.module.crm.api.customer.CustomerDTO;
import com.erp.module.engineering.api.bom.BomApi;
import com.erp.module.engineering.api.bom.BomDTO;
import com.erp.module.engineering.api.bom.BomExplodeLine;
import com.erp.module.engineering.api.material.MaterialDTO;
import com.erp.module.engineering.api.routing.RoutingApi;
import com.erp.module.engineering.api.routing.RoutingDTO;
import com.erp.module.engineering.api.routing.WorkCenterApi;
import com.erp.module.engineering.api.routing.WorkCenterDTO;
import com.erp.module.sales.api.SalesErrorCodes;
import com.erp.module.sales.config.SalesModuleConfig;
import com.erp.module.sales.controller.vo.CommonVOs.RelatedDoc;
import com.erp.module.sales.controller.vo.CommonVOs.SaveResult;
import com.erp.module.sales.controller.vo.QuoteVOs.AssignReq;
import com.erp.module.sales.controller.vo.QuoteVOs.CostSheetBrief;
import com.erp.module.sales.controller.vo.QuoteVOs.CostSheetReq;
import com.erp.module.sales.controller.vo.QuoteVOs.CostSheetResp;
import com.erp.module.sales.controller.vo.QuoteVOs.FeasibilityReq;
import com.erp.module.sales.controller.vo.QuoteVOs.MaterialCostRow;
import com.erp.module.sales.controller.vo.QuoteVOs.OperationCostRow;
import com.erp.module.sales.controller.vo.QuoteVOs.QuotationLineSave;
import com.erp.module.sales.controller.vo.QuoteVOs.QuotationSave;
import com.erp.module.sales.controller.vo.QuoteVOs.RfqDetail;
import com.erp.module.sales.controller.vo.QuoteVOs.RfqLineResp;
import com.erp.module.sales.controller.vo.QuoteVOs.RfqLineSave;
import com.erp.module.sales.controller.vo.QuoteVOs.RfqQuery;
import com.erp.module.sales.controller.vo.QuoteVOs.RfqRow;
import com.erp.module.sales.controller.vo.QuoteVOs.RfqSave;
import com.erp.module.sales.dal.dataobject.SalCostSheetDO;
import com.erp.module.sales.dal.dataobject.SalQuotationDO;
import com.erp.module.sales.dal.dataobject.SalRfqDO;
import com.erp.module.sales.dal.dataobject.SalRfqLineDO;
import com.erp.module.sales.dal.mapper.SalCostSheetMapper;
import com.erp.module.sales.dal.mapper.SalQuotationMapper;
import com.erp.module.sales.dal.mapper.SalRfqLineMapper;
import com.erp.module.sales.dal.mapper.SalRfqMapper;
import com.erp.module.sales.service.CostService;
import com.erp.module.sales.service.RfqStatus;
import com.erp.module.sales.service.SalAction;
import com.erp.module.sales.service.SalStateMachines;
import com.erp.module.sales.service.SalSupport;
import com.erp.module.system.api.file.FileApi;
import com.erp.module.system.api.user.UserDTO;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * 客户询价 RFQ 与成本核算（需求 04-02）：草稿 → 评估中（分派工程 / 成本工程师）→ 已核算 → 已报价；可关闭。
 */
@Service("salRfqService")
public class RfqService {

    public static final String BIZ_TYPE = SalesModuleConfig.RFQ;
    static final BigDecimal DEFAULT_ADMIN = new BigDecimal("0.05");
    static final BigDecimal DEFAULT_PROFIT = new BigDecimal("0.15");
    static final BigDecimal SECONDS_PER_HOUR = new BigDecimal("3600");
    static final BigDecimal MINUTES_PER_HOUR = new BigDecimal("60");

    private final SalRfqMapper mapper;
    private final SalRfqLineMapper lineMapper;
    private final SalCostSheetMapper costSheetMapper;
    private final SalQuotationMapper quotationMapper;
    private final QuotationService quotationService;
    private final CostService costService;
    private final SalSupport support;
    private final BomApi bomApi;
    private final RoutingApi routingApi;
    private final WorkCenterApi workCenterApi;
    private final FileApi fileApi;

    public RfqService(SalRfqMapper mapper, SalRfqLineMapper lineMapper, SalCostSheetMapper costSheetMapper, SalQuotationMapper quotationMapper,
                      QuotationService quotationService, CostService costService, SalSupport support, BomApi bomApi, RoutingApi routingApi,
                      WorkCenterApi workCenterApi, FileApi fileApi) {
        this.mapper = mapper;
        this.lineMapper = lineMapper;
        this.costSheetMapper = costSheetMapper;
        this.quotationMapper = quotationMapper;
        this.quotationService = quotationService;
        this.costService = costService;
        this.support = support;
        this.bomApi = bomApi;
        this.routingApi = routingApi;
        this.workCenterApi = workCenterApi;
        this.fileApi = fileApi;
    }

    // ==================== 查询 ====================

    public PageResult<RfqRow> page(RfqQuery q) {
        LambdaQueryWrapper<SalRfqDO> w = new LambdaQueryWrapper<SalRfqDO>().eq(SalRfqDO::getDeleted, false)
                .likeRight(StringUtils.hasText(q.getDocNo()), SalRfqDO::getDocNo, q.getDocNo() == null ? null : q.getDocNo().trim().toUpperCase())
                .eq(q.getCustomerId() != null, SalRfqDO::getCustomerId, q.getCustomerId())
                .eq(q.getOwnerId() != null, SalRfqDO::getOwnerId, q.getOwnerId())
                .ge(q.getReplyFrom() != null, SalRfqDO::getReplyDueDate, q.getReplyFrom())
                .le(q.getReplyTo() != null, SalRfqDO::getReplyDueDate, q.getReplyTo());
        if (q.getEngineerId() != null) {
            w.and(x -> x.eq(SalRfqDO::getEngineerId, q.getEngineerId()).or().eq(SalRfqDO::getCostEngineerId, q.getEngineerId()));
        }
        if (StringUtils.hasText(q.getStatuses())) w.in(SalRfqDO::getRfqStatus, Arrays.asList(q.getStatuses().split(",")));
        else w.ne(SalRfqDO::getRfqStatus, RfqStatus.CLOSED.name());
        w.orderByDesc(SalRfqDO::getId);
        IPage<SalRfqDO> page = mapper.selectScopedPage(Page.of(q.getPageNo(), q.getPageSize()), w);
        List<SalRfqDO> list = page.getRecords();
        Map<Long, List<SalRfqLineDO>> lines = list.isEmpty() ? Map.of()
                : lineMapper.selectByParents(list.stream().map(SalRfqDO::getId).toList()).stream().collect(Collectors.groupingBy(SalRfqLineDO::getRfqId));
        Map<Long, CustomerDTO> cs = support.customers(list.stream().map(SalRfqDO::getCustomerId).toList());
        List<Long> userIds = new ArrayList<>();
        list.forEach(r -> {
            userIds.add(r.getOwnerId());
            userIds.add(r.getEngineerId());
            userIds.add(r.getCostEngineerId());
        });
        Map<Long, UserDTO> users = support.users(userIds);
        LocalDate today = LocalDate.now();
        return new PageResult<>(list.stream().map(r -> {
            List<SalRfqLineDO> ls = lines.getOrDefault(r.getId(), List.of());
            boolean open = !RfqStatus.QUOTED.name().equals(r.getRfqStatus()) && !RfqStatus.CLOSED.name().equals(r.getRfqStatus());
            String due = !open ? null : r.getReplyDueDate().isBefore(today) ? "OVERDUE" : !r.getReplyDueDate().isAfter(today.plusDays(2)) ? "SOON" : null;
            String feas = ls.stream().anyMatch(l -> "NG".equals(l.getFeasibility())) ? "HAS_NG"
                    : !ls.isEmpty() && ls.stream().allMatch(l -> "OK".equals(l.getFeasibility())) ? "ALL_OK" : "PENDING";
            return new RfqRow(r.getId(), r.getDocNo(), r.getCustomerId(), SalSupport.shortName(cs, r.getCustomerId()), ls.size(), r.getReplyDueDate(), due,
                    r.getEngineerId(), SalSupport.name(users, r.getEngineerId()), r.getCostEngineerId(), SalSupport.name(users, r.getCostEngineerId()),
                    feas, r.getRfqStatus(), r.getOwnerId(), SalSupport.name(users, r.getOwnerId()), r.getDocDate());
        }).toList(), page.getTotal());
    }

    public RfqDetail detail(Long id) {
        SalRfqDO r = getOrThrow(id);
        checkScope(r);
        CustomerDTO c = support.customer(r.getCustomerId());
        List<SalRfqLineDO> lines = lineMapper.selectByParent(id);
        boolean cost = SalSupport.canViewQuoteCost();
        Map<Long, List<SalCostSheetDO>> sheets = sheets(lines.stream().map(SalRfqLineDO::getId).toList());
        Map<Long, MaterialDTO> ms = support.materials(lines.stream().map(SalRfqLineDO::getMaterialId).toList());
        ContactDTO contact = r.getContactId() == null ? null
                : support.customerApi().getContacts(c.id()).stream().filter(x -> x.id().equals(r.getContactId())).findFirst().orElse(null);
        Map<Long, UserDTO> users = support.users(Arrays.asList(r.getOwnerId(), r.getEngineerId(), r.getCostEngineerId()));
        List<RelatedDoc> related = new ArrayList<>();
        quotationMapper.selectList(new LambdaQueryWrapper<SalQuotationDO>().eq(SalQuotationDO::getRfqId, id).orderByAsc(SalQuotationDO::getId)).forEach(q ->
                related.add(new RelatedDoc("DOWN", "报价单", q.getDocNo() + " R" + q.getRevision(), q.getDocDate(), q.getQuoteStatus(),
                        q.getStatus().label(), "/sales/quotation/" + q.getId())));
        return new RfqDetail(r.getId(), r.getDocNo(), r.getDocDate(), r.getRfqStatus(), c.id(), c.shortName(), c.status().name(), r.getContactId(),
                contact == null ? null : contact.name(), r.getOpportunityId(), r.getCurrency(), r.getTradeTerm(), r.getReplyDueDate(), r.getEngineerId(),
                SalSupport.name(users, r.getEngineerId()), r.getCostEngineerId(), SalSupport.name(users, r.getCostEngineerId()), r.getCloseReason(),
                r.getRemark(), r.getOwnerId(), SalSupport.name(users, r.getOwnerId()), canEvaluate(r), SalSupport.hasPermission("sales:rfq:cost"), cost,
                r.getCreatedAt(), r.getVersion(), lines.stream().map(l -> {
                    MaterialDTO m = l.getMaterialId() == null ? null : ms.get(l.getMaterialId());
                    List<CostSheetBrief> cb = sheets.getOrDefault(l.getId(), List.of()).stream().map(s -> new CostSheetBrief(s.getId(), s.getQty(),
                            SalSupport.mask(s.getTotalCost(), cost), SalSupport.mask(s.getSuggestedPrice(), cost),
                            SalSupport.mask(s.getSuggestedPriceCur(), cost))).toList();
                    return new RfqLineResp(l.getId(), l.getLineNo(), l.getCustomerPartNo(), l.getDescription(), l.getMaterialId(),
                            m == null ? null : m.code(), m == null ? null : m.name(), m == null ? null : m.spec(), m == null ? null : m.baseUom(),
                            l.getAnnualQty(), l.getQtyBreaks(), l.getTargetPrice(), l.getRequiredDate(), l.getFeasibility(), l.getFeasibilityRemark(),
                            l.getRemark(), cb);
                }).toList(), related);
    }

    /** 业务员看自己的（数据权限）；被分派的工程师 / 成本工程师也能查看 */
    private void checkScope(SalRfqDO r) {
        Long me = support.currentUser();
        if (me != null && (me.equals(r.getEngineerId()) || me.equals(r.getCostEngineerId()))) return;
        DataScopes.check(r.getOrgId(), r.getDeptId(), r.getOwnerId(), "RFQ");
    }

    private boolean canEvaluate(SalRfqDO r) {
        Long me = support.currentUser();
        return SalSupport.hasPermission("sales:rfq:assign") || me != null && me.equals(r.getEngineerId());
    }

    private Map<Long, List<SalCostSheetDO>> sheets(List<Long> lineIds) {
        if (lineIds.isEmpty()) return Map.of();
        return costSheetMapper.selectList(new LambdaQueryWrapper<SalCostSheetDO>().in(SalCostSheetDO::getRfqLineId, lineIds).orderByAsc(SalCostSheetDO::getQty))
                .stream().collect(Collectors.groupingBy(SalCostSheetDO::getRfqLineId));
    }

    // ==================== 编辑 ====================

    @Transactional(rollbackFor = Exception.class)
    public SaveResult create(RfqSave req) {
        SalRfqDO r = new SalRfqDO();
        r.setDocNo(support.nextNo(BIZ_TYPE));
        r.setDocDate(LocalDate.now());
        r.setRfqStatus(RfqStatus.DRAFT.name());
        r.setStatus(RfqStatus.DRAFT.docStatus());
        fillHeader(r, req);
        support.fillOwner(r, null);
        mapper.insert(r);
        saveLines(r, req.lines());
        if (req.fileIds() != null && !req.fileIds().isEmpty()) fileApi.bind(req.fileIds(), BIZ_TYPE, r.getId());
        return new SaveResult(r.getId(), List.of());
    }

    @Transactional(rollbackFor = Exception.class)
    public SaveResult update(Long id, RfqSave req) {
        SalRfqDO r = getOrThrow(id);
        DataScopes.check(r.getOrgId(), r.getDeptId(), r.getOwnerId(), "RFQ");
        requireStatus(r, "修改", RfqStatus.DRAFT, RfqStatus.EVALUATING);
        if (req.version() != null) r.setVersion(req.version());
        fillHeader(r, req);
        mapper.updateByIdOrFail(r);
        saveLines(r, req.lines());
        if (req.fileIds() != null && !req.fileIds().isEmpty()) fileApi.bind(req.fileIds(), BIZ_TYPE, r.getId());
        return new SaveResult(r.getId(), List.of());
    }

    private static void requireStatus(SalRfqDO r, String action, RfqStatus... allowed) {
        RfqStatus s = RfqStatus.valueOf(r.getRfqStatus());
        if (Arrays.stream(allowed).noneMatch(a -> a == s)) throw BizException.of(SalesErrorCodes.RFQ_STATUS, s.label(), action);
    }

    private CustomerDTO fillHeader(SalRfqDO r, RfqSave req) {
        CustomerDTO c = support.customerApi().validateCanQuote(req.customerId());
        r.setCustomerId(c.id());
        if (req.contactId() != null && support.customerApi().getContacts(c.id()).stream().noneMatch(x -> x.id().equals(req.contactId()))) {
            throw BizException.of(GlobalErrorCodes.DATA_NOT_EXISTS, "联系人");
        }
        r.setContactId(req.contactId());
        r.setOpportunityId(req.opportunityId());
        String currency = StringUtils.hasText(req.currency()) ? req.currency().trim().toUpperCase()
                : StringUtils.hasText(c.currency()) ? c.currency() : support.baseCurrency();
        support.currencyApi().validate(currency);
        r.setCurrency(currency);
        String trade = req.tradeTerm() != null ? SalSupport.trim(req.tradeTerm()) : c.tradeTerm();
        if (trade != null && !trade.equals(r.getTradeTerm())) support.dict().validate("sys_trade_term", trade, "贸易条款");
        r.setTradeTerm(trade);
        r.setReplyDueDate(req.replyDueDate());
        r.setRemark(SalSupport.trim(req.remark()));
        return c;
    }

    /** 明细整体替换；已有行按行号保留可行性与核算（行号不变的行视为同一行） */
    private void saveLines(SalRfqDO r, List<RfqLineSave> lines) {
        if (lines == null || lines.isEmpty()) throw new BizException(SalesErrorCodes.DOC_NO_LINES);
        Map<Integer, SalRfqLineDO> old = lineMapper.selectByParent(r.getId()).stream().collect(Collectors.toMap(SalRfqLineDO::getLineNo, l -> l));
        int no = 0;
        for (RfqLineSave s : lines) {
            no++;
            String breaks = normalizeBreaks(s.qtyBreaks(), no);
            if (s.materialId() != null) support.material(s.materialId());
            SalRfqLineDO d = old.remove(no);
            boolean isNew = d == null;
            if (isNew) {
                d = new SalRfqLineDO();
                d.setRfqId(r.getId());
                d.setLineNo(no);
                d.setFeasibility("PENDING");
            }
            d.setCustomerPartNo(SalSupport.trim(s.customerPartNo()));
            d.setDescription(s.description().trim());
            d.setMaterialId(s.materialId());
            d.setAnnualQty(s.annualQty());
            d.setQtyBreaks(breaks);
            d.setTargetPrice(s.targetPrice());
            d.setRequiredDate(s.requiredDate());
            d.setRemark(SalSupport.trim(s.remark()));
            if (isNew) lineMapper.insert(d);
            else lineMapper.updateByIdOrFail(d);
        }
        for (SalRfqLineDO removed : old.values()) {
            costSheetMapper.delete(new LambdaQueryWrapper<SalCostSheetDO>().eq(SalCostSheetDO::getRfqLineId, removed.getId()));
            lineMapper.deleteById(removed.getId());
        }
    }

    /** 数量阶梯：逗号分隔的正数，去重升序 */
    static String normalizeBreaks(String s, int lineNo) {
        TreeSet<BigDecimal> set = new TreeSet<>();
        try {
            for (String p : s.split("[,，\\s]+")) {
                if (p.isBlank()) continue;
                BigDecimal v = new BigDecimal(p.trim());
                if (v.signum() <= 0) throw new NumberFormatException();
                set.add(v.stripTrailingZeros());
            }
        } catch (NumberFormatException e) {
            throw BizException.of(SalesErrorCodes.RFQ_QTY_BREAKS, lineNo);
        }
        if (set.isEmpty()) throw BizException.of(SalesErrorCodes.RFQ_QTY_BREAKS, lineNo);
        return set.stream().map(BigDecimal::toPlainString).collect(Collectors.joining(","));
    }

    static List<BigDecimal> breaks(SalRfqLineDO l) {
        return Arrays.stream(l.getQtyBreaks().split(",")).map(BigDecimal::new).toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        SalRfqDO r = getOrThrow(id);
        DataScopes.check(r.getOrgId(), r.getDeptId(), r.getOwnerId(), "RFQ");
        requireStatus(r, "删除", RfqStatus.DRAFT);
        for (SalRfqLineDO l : lineMapper.selectByParent(id)) {
            costSheetMapper.delete(new LambdaQueryWrapper<SalCostSheetDO>().eq(SalCostSheetDO::getRfqLineId, l.getId()));
        }
        lineMapper.deleteByParent(id);
        mapper.deleteById(id);
        fileApi.deleteByBiz(BIZ_TYPE, id);
    }

    private void fire(SalRfqDO r, SalAction action, String reason) {
        RfqStatus from = RfqStatus.valueOf(r.getRfqStatus());
        RfqStatus to = SalStateMachines.RFQ.fire(from, action);
        r.setRfqStatus(to.name());
        r.setStatus(to.docStatus());
        mapper.updateByIdOrFail(r);
        support.log(BIZ_TYPE, r.getId(), r.getDocNo(), action.name(), action.label(), from.name(), to.name(), reason);
    }

    // ==================== 分派、评估、核算 ====================

    /** 分派工程师、成本工程师 → 评估中，双方收到待办 */
    @Transactional(rollbackFor = Exception.class)
    public void assign(Long id, AssignReq req) {
        SalRfqDO r = getOrThrow(id);
        DataScopes.check(r.getOrgId(), r.getDeptId(), r.getOwnerId(), "RFQ");
        if (req.engineerId() == null && req.costEngineerId() == null) throw new BizException(SalesErrorCodes.RFQ_ASSIGN_REQUIRED);
        if (req.engineerId() != null && support.user(req.engineerId()) == null) throw BizException.of(GlobalErrorCodes.DATA_NOT_EXISTS, "工程师");
        if (req.costEngineerId() != null && support.user(req.costEngineerId()) == null) throw BizException.of(GlobalErrorCodes.DATA_NOT_EXISTS, "成本工程师");
        r.setEngineerId(req.engineerId());
        r.setCostEngineerId(req.costEngineerId());
        fire(r, SalAction.ASSIGN, "工程师 " + Objects.toString(support.userName(req.engineerId()), "-") + "，成本工程师 "
                + Objects.toString(support.userName(req.costEngineerId()), "-"));
        String customer = support.customer(r.getCustomerId()).shortName();
        if (req.engineerId() != null) {
            support.todo(BIZ_TYPE + ":" + id + ":ENG", List.of(req.engineerId()), BIZ_TYPE, id, r.getDocNo(),
                    "RFQ " + r.getDocNo() + "（" + customer + "）请评估可行性，回复截止 " + r.getReplyDueDate(), "/sales/rfq/" + id);
        }
        if (req.costEngineerId() != null) {
            support.todo(BIZ_TYPE + ":" + id + ":COST", List.of(req.costEngineerId()), BIZ_TYPE, id, r.getDocNo(),
                    "RFQ " + r.getDocNo() + "（" + customer + "）请做成本核算，回复截止 " + r.getReplyDueDate(), "/sales/rfq/" + id);
        }
    }

    /** 工程评估：被分派的工程师或有分派权限的用户 */
    @Transactional(rollbackFor = Exception.class)
    public void feasibility(Long id, List<FeasibilityReq> reqs) {
        SalRfqDO r = getOrThrow(id);
        if (!canEvaluate(r)) throw new BizException(SalesErrorCodes.RFQ_NOT_ASSIGNEE);
        requireStatus(r, "评估", RfqStatus.DRAFT, RfqStatus.EVALUATING, RfqStatus.COSTED);
        Map<Long, SalRfqLineDO> lines = lineMapper.selectByParent(id).stream().collect(Collectors.toMap(SalRfqLineDO::getId, l -> l));
        for (FeasibilityReq f : reqs == null ? List.<FeasibilityReq>of() : reqs) {
            SalRfqLineDO l = lines.get(f.lineId());
            if (l == null) throw new BizException(SalesErrorCodes.RFQ_LINE_NOT_EXISTS);
            if (!Set.of("PENDING", "OK", "NG").contains(f.feasibility())) throw BizException.of(GlobalErrorCodes.BAD_REQUEST, "可行性");
            if (f.materialId() != null) support.material(f.materialId());
            l.setFeasibility(f.feasibility());
            l.setFeasibilityRemark(SalSupport.trim(f.remark()));
            if (f.materialId() != null) l.setMaterialId(f.materialId());
            lineMapper.updateByIdOrFail(l);
        }
        support.log(BIZ_TYPE, r.getId(), r.getDocNo(), "FEASIBILITY", "工程评估", r.getRfqStatus(), r.getRfqStatus(), reqs == null ? "" : reqs.size() + " 行");
    }

    public List<CostSheetResp> costSheets(Long rfqId, Long lineId) {
        SalRfqDO r = getOrThrow(rfqId);
        checkScope(r);
        requireCostView();
        SalRfqLineDO l = line(rfqId, lineId);
        return costSheetMapper.selectList(new LambdaQueryWrapper<SalCostSheetDO>().eq(SalCostSheetDO::getRfqLineId, lineId).orderByAsc(SalCostSheetDO::getQty))
                .stream().map(s -> resp(s, r, l)).toList();
    }

    private static void requireCostView() {
        if (!SalSupport.canViewQuoteCost()) throw new BizException(SalesErrorCodes.NO_COST_VIEW);
    }

    /** 核算预览（不保存） */
    public CostSheetResp calc(Long rfqId, Long lineId, CostSheetReq req) {
        SalRfqDO r = getOrThrow(rfqId);
        checkScope(r);
        requireCostView();
        SalRfqLineDO l = line(rfqId, lineId);
        SalCostSheetDO s = compute(r, l, req);
        return resp(s, r, l);
    }

    /** 保存核算（同一行同一数量覆盖）；全部可报价行的全部阶梯核算完成后 → 已核算，通知业务员 */
    @Transactional(rollbackFor = Exception.class)
    public CostSheetResp saveCostSheet(Long rfqId, Long lineId, CostSheetReq req) {
        SalRfqDO r = getOrThrow(rfqId);
        checkScope(r);
        requireCostView();
        requireStatus(r, "核算", RfqStatus.DRAFT, RfqStatus.EVALUATING, RfqStatus.COSTED);
        SalRfqLineDO l = line(rfqId, lineId);
        if (breaks(l).stream().noneMatch(b -> b.compareTo(req.qty()) == 0)) throw BizException.of(SalesErrorCodes.RFQ_QTY_NOT_IN_BREAKS, SalSupport.plain(req.qty()));
        SalCostSheetDO s = compute(r, l, req);
        SalCostSheetDO old = costSheetMapper.selectList(new LambdaQueryWrapper<SalCostSheetDO>().eq(SalCostSheetDO::getRfqLineId, lineId)).stream()
                .filter(x -> x.getQty().compareTo(req.qty()) == 0).findFirst().orElse(null);
        if (old != null) {
            s.setId(old.getId());
            s.setVersion(old.getVersion());
            costSheetMapper.updateByIdOrFail(s);
        } else {
            costSheetMapper.insert(s);
        }
        support.log(BIZ_TYPE, r.getId(), r.getDocNo(), "COST_SHEET", "成本核算", r.getRfqStatus(), r.getRfqStatus(),
                "第 " + l.getLineNo() + " 行 数量 " + SalSupport.plain(req.qty()) + "：建议售价 " + SalSupport.plain(s.getSuggestedPrice()));
        if (RfqStatus.EVALUATING.name().equals(r.getRfqStatus()) && allCosted(r)) {
            fire(r, SalAction.COST, null);
            support.message(List.of(r.getOwnerId()), "RFQ 已完成成本核算", "RFQ " + r.getDocNo() + " 已完成成本核算，可以生成报价单", "/sales/rfq/" + r.getId());
        }
        return resp(costSheetMapper.selectById(s.getId()), r, l);
    }

    private boolean allCosted(SalRfqDO r) {
        List<SalRfqLineDO> lines = lineMapper.selectByParent(r.getId()).stream().filter(l -> !"NG".equals(l.getFeasibility())).toList();
        if (lines.isEmpty()) return false;
        Map<Long, List<SalCostSheetDO>> sheets = sheets(lines.stream().map(SalRfqLineDO::getId).toList());
        return lines.stream().allMatch(l -> l.getMaterialId() != null && breaks(l).stream().allMatch(b -> sheets.getOrDefault(l.getId(), List.of()).stream()
                .anyMatch(s -> s.getQty().compareTo(b) == 0)));
    }

    private SalRfqLineDO line(Long rfqId, Long lineId) {
        SalRfqLineDO l = lineMapper.selectById(lineId);
        if (l == null || !l.getRfqId().equals(rfqId)) throw new BizException(SalesErrorCodes.RFQ_LINE_NOT_EXISTS);
        return l;
    }

    /**
     * 核算（3.4）：材料 = Σ 末级子件累计用量 × 单价（手工 → 标准成本 → 最新采购价）；
     * 人工 / 制费 = Σ 工序标准工时 × 费率（含半成品工艺，按累计用量）；准备 = Σ 准备时间 × 费率 ÷ 数量；
     * 总成本 = (材料 + 人工 + 制费 + 准备 + 模具 + 包装运输) × (1 + 管理费率)；建议售价 = 总成本 × (1 + 利润率)。
     */
    SalCostSheetDO compute(SalRfqDO r, SalRfqLineDO l, CostSheetReq req) {
        if (l.getMaterialId() == null) throw BizException.of(SalesErrorCodes.RFQ_LINE_NO_MATERIAL, l.getLineNo());
        MaterialDTO top = support.material(l.getMaterialId());
        BomDTO bom = req.bomId() != null ? bomApi.getBom(req.bomId()).orElse(null) : bomApi.getDefaultBom(top.id(), LocalDate.now()).orElse(null);
        if (bom == null || !bom.materialId().equals(top.id())) throw BizException.of(SalesErrorCodes.RFQ_NO_BOM, top.code());
        BigDecimal qty = req.qty();
        List<BomExplodeLine> exploded = bomApi.explode(top.id(), BigDecimal.ONE, LocalDate.now(), 0);
        List<BomExplodeLine> leaves = exploded.stream().filter(x -> x.bomId() == null).toList();
        Map<Long, BigDecimal> qtyPer = new LinkedHashMap<>();
        leaves.forEach(x -> qtyPer.merge(x.componentId(), x.totalQtyPer(), BigDecimal::add));
        Map<Long, MaterialDTO> ms = support.materials(qtyPer.keySet());
        Map<Long, CostService.UnitCost> costs = costService.unitCosts(qtyPer.keySet());
        Map<Long, BigDecimal> manual = req.materialPrices() == null ? Map.of() : req.materialPrices();
        List<MaterialCostRow> materials = new ArrayList<>();
        BigDecimal materialCost = BigDecimal.ZERO;
        for (Map.Entry<Long, BigDecimal> e : qtyPer.entrySet()) {
            MaterialDTO m = ms.get(e.getKey());
            BigDecimal price;
            String source;
            if (manual.get(e.getKey()) != null) {
                price = manual.get(e.getKey());
                source = "MANUAL";
            } else if (costs.containsKey(e.getKey())) {
                price = costs.get(e.getKey()).cost();
                source = costs.get(e.getKey()).source();
            } else {
                price = BigDecimal.ZERO;
                source = "NONE";
            }
            BigDecimal amount = e.getValue().multiply(price).setScale(6, RoundingMode.HALF_UP);
            materialCost = materialCost.add(amount);
            materials.add(new MaterialCostRow(e.getKey(), m == null ? null : m.code(), m == null ? null : m.name(), m == null ? null : m.spec(),
                    m == null ? null : m.baseUom(), e.getValue(), price, source, amount));
        }
        // 工序：顶层 + 自制半成品（按累计用量）
        Map<Long, BigDecimal> made = new LinkedHashMap<>();
        made.put(top.id(), BigDecimal.ONE);
        exploded.stream().filter(x -> x.bomId() != null).forEach(x -> made.merge(x.componentId(), x.totalQtyPer(), BigDecimal::add));
        Map<Long, WorkCenterDTO> wcs = workCenterApi.list().stream().collect(Collectors.toMap(WorkCenterDTO::id, w -> w, (a, b) -> a));
        Map<Long, MaterialDTO> madeMs = support.materials(made.keySet());
        List<OperationCostRow> ops = new ArrayList<>();
        BigDecimal labor = BigDecimal.ZERO;
        BigDecimal overhead = BigDecimal.ZERO;
        BigDecimal setup = BigDecimal.ZERO;
        for (Map.Entry<Long, BigDecimal> e : made.entrySet()) {
            RoutingDTO routing = routingApi.getDefaultRouting(e.getKey()).orElse(null);
            if (routing == null) continue;
            for (RoutingDTO.Step st : routing.steps()) {
                if (st.outsourced()) continue;
                WorkCenterDTO wc = st.workCenterId() == null ? null : wcs.get(st.workCenterId());
                BigDecimal laborRate = wc == null ? BigDecimal.ZERO : SalSupport.nz(wc.laborRate());
                BigDecimal overheadRate = wc == null ? BigDecimal.ZERO : SalSupport.nz(wc.overheadRate());
                BigDecimal runHours = SalSupport.nz(st.runSeconds()).multiply(e.getValue()).divide(SECONDS_PER_HOUR, 8, RoundingMode.HALF_UP);
                BigDecimal setupHours = SalSupport.nz(st.setupMinutes()).divide(MINUTES_PER_HOUR, 8, RoundingMode.HALF_UP);
                BigDecimal lab = runHours.multiply(laborRate).setScale(6, RoundingMode.HALF_UP);
                BigDecimal ovh = runHours.multiply(overheadRate).setScale(6, RoundingMode.HALF_UP);
                BigDecimal set = setupHours.multiply(laborRate.add(overheadRate)).divide(qty, 6, RoundingMode.HALF_UP);
                labor = labor.add(lab);
                overhead = overhead.add(ovh);
                setup = setup.add(set);
                MaterialDTO mm = madeMs.get(e.getKey());
                ops.add(new OperationCostRow(e.getKey(), mm == null ? null : mm.code(), st.seq(), st.operation(), st.workCenterId(),
                        wc == null ? null : wc.name(), runHours.setScale(6, RoundingMode.HALF_UP), setupHours.setScale(4, RoundingMode.HALF_UP),
                        laborRate, overheadRate, lab, ovh, set));
            }
        }
        BigDecimal tooling = req.toolingTotal() != null && req.toolingQty() != null && req.toolingQty().signum() > 0
                ? req.toolingTotal().divide(req.toolingQty(), 6, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        BigDecimal packing = SalSupport.nz(req.packingFreightCost());
        BigDecimal admin = req.adminRate() != null ? req.adminRate() : DEFAULT_ADMIN;
        BigDecimal profit = req.profitRate() != null ? req.profitRate() : DEFAULT_PROFIT;
        BigDecimal total = materialCost.add(labor).add(overhead).add(setup).add(tooling).add(packing).multiply(BigDecimal.ONE.add(admin))
                .setScale(6, RoundingMode.HALF_UP);
        BigDecimal suggested = total.multiply(BigDecimal.ONE.add(profit)).setScale(6, RoundingMode.HALF_UP);
        BigDecimal rate = r.getCurrency().equals(support.baseCurrency()) ? BigDecimal.ONE : safeRate(r.getCurrency());
        SalCostSheetDO s = new SalCostSheetDO();
        s.setRfqId(r.getId());
        s.setRfqLineId(l.getId());
        s.setQty(Decimals.qty(qty));
        s.setBomId(bom.id());
        s.setMaterialCost(materialCost.setScale(6, RoundingMode.HALF_UP));
        s.setLaborCost(labor);
        s.setOverheadCost(overhead);
        s.setSetupCost(setup);
        s.setToolingTotal(req.toolingTotal());
        s.setToolingQty(req.toolingQty());
        s.setToolingCost(tooling);
        s.setPackingFreightCost(packing);
        s.setAdminRate(admin);
        s.setProfitRate(profit);
        s.setTotalCost(total);
        s.setSuggestedPrice(suggested);
        s.setSuggestedPriceCur(rate == null ? null : suggested.divide(rate, 6, RoundingMode.HALF_UP));
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("materials", materials);
        detail.put("operations", ops);
        s.setDetail(support.json(detail));
        return s;
    }

    private BigDecimal safeRate(String currency) {
        try {
            return support.currencyApi().getRate(currency, LocalDate.now());
        } catch (RuntimeException e) {
            return null;
        }
    }

    CostSheetResp resp(SalCostSheetDO s, SalRfqDO r, SalRfqLineDO l) {
        List<MaterialCostRow> materials = List.of();
        List<OperationCostRow> ops = List.of();
        if (s.getDetail() != null) {
            try {
                Map<String, Object> m = support.objectMapper().readValue(s.getDetail(), new TypeReference<Map<String, Object>>() {
                });
                materials = support.objectMapper().convertValue(m.getOrDefault("materials", List.of()), new TypeReference<List<MaterialCostRow>>() {
                });
                ops = support.objectMapper().convertValue(m.getOrDefault("operations", List.of()), new TypeReference<List<OperationCostRow>>() {
                });
            } catch (Exception ignored) {
                // 明细解析失败时只显示汇总
            }
        }
        BomDTO bom = s.getBomId() == null ? null : bomApi.getBom(s.getBomId()).orElse(null);
        BigDecimal diff = l.getTargetPrice() == null || s.getSuggestedPriceCur() == null || l.getTargetPrice().signum() == 0 ? null
                : s.getSuggestedPriceCur().subtract(l.getTargetPrice()).divide(l.getTargetPrice(), 4, RoundingMode.HALF_UP);
        return new CostSheetResp(s.getId(), s.getRfqLineId(), s.getQty(), s.getBomId(), bom == null ? null : bom.docNo() + " V" + bom.version(),
                s.getMaterialCost(), s.getLaborCost(), s.getOverheadCost(), s.getSetupCost(), s.getToolingTotal(), s.getToolingQty(), s.getToolingCost(),
                s.getPackingFreightCost(), s.getAdminRate(), s.getProfitRate(), s.getTotalCost(), s.getSuggestedPrice(), s.getSuggestedPriceCur(),
                support.baseCurrency(), r.getCurrency(), l.getTargetPrice(), diff, materials, ops);
    }

    // ==================== 生成报价、关闭 ====================

    /**
     * 生成报价单（R01）：可行性不为 NG 且关联物料的行，每个数量阶梯一档；单价取核算的建议售价（RFQ 币别，含税报价时加税）。
     */
    @Transactional(rollbackFor = Exception.class)
    public Long toQuotation(Long id) {
        SalRfqDO r = getOrThrow(id);
        DataScopes.check(r.getOrgId(), r.getDeptId(), r.getOwnerId(), "RFQ");
        requireStatus(r, "生成报价", RfqStatus.DRAFT, RfqStatus.EVALUATING, RfqStatus.COSTED, RfqStatus.QUOTED);
        CustomerDTO c = support.customer(r.getCustomerId());
        List<SalRfqLineDO> lines = lineMapper.selectByParent(id).stream().filter(l -> l.getMaterialId() != null && !"NG".equals(l.getFeasibility())).toList();
        if (lines.isEmpty()) throw new BizException(SalesErrorCodes.RFQ_NOTHING_TO_QUOTE);
        Map<Long, List<SalCostSheetDO>> sheets = sheets(lines.stream().map(SalRfqLineDO::getId).toList());
        boolean incl = !c.foreign();
        BigDecimal tax = c.salesTaxRate() != null ? c.salesTaxRate() : BigDecimal.ZERO;
        List<QuotationLineSave> saves = new ArrayList<>();
        for (SalRfqLineDO l : lines) {
            List<BigDecimal> bs = breaks(l);
            for (int i = 0; i < bs.size(); i++) {
                BigDecimal b = bs.get(i);
                SalCostSheetDO s = sheets.getOrDefault(l.getId(), List.of()).stream().filter(x -> x.getQty().compareTo(b) == 0).findFirst().orElse(null);
                BigDecimal price = s == null || s.getSuggestedPriceCur() == null ? BigDecimal.ZERO : s.getSuggestedPriceCur();
                if (incl) price = price.multiply(BigDecimal.ONE.add(tax)).setScale(6, RoundingMode.HALF_UP);
                BigDecimal unitCost = s == null ? null : s.getTotalCost();
                saves.add(new QuotationLineSave(l.getMaterialId(), l.getCustomerPartNo(), null, null, b, price, null, i == 0 ? b : null, null,
                        i == 0 && s != null && s.getToolingTotal() != null ? s.getToolingTotal() : null, l.getId(), unitCost, null));
            }
        }
        SaveResult res = quotationService.create(new QuotationSave(c.id(), r.getContactId(), r.getOpportunityId(), r.getCurrency(), null, r.getTradeTerm(),
                null, incl, null, null, null, saves, null, null), id, null, r.getOwnerId());
        fire(r, SalAction.QUOTE, quotationService.getOrThrow(res.id()).getDocNo());
        return res.id();
    }

    @Transactional(rollbackFor = Exception.class)
    public void close(Long id, String reason) {
        SalRfqDO r = getOrThrow(id);
        DataScopes.check(r.getOrgId(), r.getDeptId(), r.getOwnerId(), "RFQ");
        String why = SalSupport.requireReason(reason, "关闭");
        r.setCloseReason(why.length() > 256 ? why.substring(0, 256) : why);
        fire(r, SalAction.CLOSE, why);
    }

    /** R02：回复截止日期前 1 天仍未报价，提醒业务员和被分派人（每张只提醒一次） */
    @Transactional(rollbackFor = Exception.class)
    public int remindDue() {
        LocalDate limit = LocalDate.now().plusDays(1);
        List<SalRfqDO> list = mapper.selectList(new LambdaQueryWrapper<SalRfqDO>().in(SalRfqDO::getRfqStatus, RfqStatus.DRAFT.name(), RfqStatus.EVALUATING.name(),
                RfqStatus.COSTED.name()).le(SalRfqDO::getReplyDueDate, limit).isNull(SalRfqDO::getRemindedAt));
        for (SalRfqDO r : list) {
            support.message(Arrays.asList(r.getOwnerId(), r.getEngineerId(), r.getCostEngineerId()), "RFQ 即将到期",
                    "RFQ " + r.getDocNo() + " 回复截止 " + r.getReplyDueDate() + "，尚未报价", "/sales/rfq/" + r.getId());
            r.setRemindedAt(LocalDateTime.now());
            mapper.updateByIdOrFail(r);
        }
        return list.size();
    }

    public SalRfqDO getOrThrow(Long id) {
        SalRfqDO r = id == null ? null : mapper.selectById(id);
        if (r == null) throw new BizException(SalesErrorCodes.RFQ_NOT_EXISTS);
        return r;
    }

    /** 客户转移：未关闭 RFQ 的业务员改为新负责人 */
    public void transferOwner(java.util.Collection<Long> customerIds, Long newOwner) {
        for (SalRfqDO r : mapper.selectList(new LambdaQueryWrapper<SalRfqDO>().in(SalRfqDO::getCustomerId, customerIds)
                .in(SalRfqDO::getRfqStatus, RfqStatus.DRAFT.name(), RfqStatus.EVALUATING.name(), RfqStatus.COSTED.name()))) {
            if (newOwner.equals(r.getOwnerId())) continue;
            support.fillOwner(r, newOwner);
            mapper.updateByIdOrFail(r);
        }
    }
}
