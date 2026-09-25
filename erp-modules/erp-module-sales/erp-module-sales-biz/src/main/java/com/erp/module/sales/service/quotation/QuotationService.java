package com.erp.module.sales.service.quotation;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.common.enums.DocStatus;
import com.erp.common.exception.BizException;
import com.erp.common.exception.GlobalErrorCodes;
import com.erp.common.result.PageResult;
import com.erp.common.util.Decimals;
import com.erp.framework.datascope.DataScopes;
import com.erp.framework.event.DomainEventPublisher;
import com.erp.module.crm.api.customer.ContactDTO;
import com.erp.module.crm.api.customer.CustomerDTO;
import com.erp.module.crm.api.customer.CustomerStatus;
import com.erp.module.crm.api.opportunity.OpportunityApi;
import com.erp.module.crm.api.part.CustomerPartApi;
import com.erp.module.crm.api.part.CustomerPartDTO;
import com.erp.module.engineering.api.material.MaterialDTO;
import com.erp.module.sales.api.SalesErrorCodes;
import com.erp.module.sales.api.quotation.QuotationCreatedEvent;
import com.erp.module.sales.config.SalesModuleConfig;
import com.erp.module.sales.controller.vo.CommonVOs.DocResult;
import com.erp.module.sales.controller.vo.CommonVOs.RelatedDoc;
import com.erp.module.sales.controller.vo.CommonVOs.SaveResult;
import com.erp.module.sales.controller.vo.OrderVOs.FromQuotationLine;
import com.erp.module.sales.controller.vo.OrderVOs.FromQuotationResult;
import com.erp.module.sales.controller.vo.OrderVOs.OrderLineSave;
import com.erp.module.sales.controller.vo.OrderVOs.OrderSave;
import com.erp.module.sales.controller.vo.QuoteVOs.LoseReq;
import com.erp.module.sales.controller.vo.QuoteVOs.QuotationDetail;
import com.erp.module.sales.controller.vo.QuoteVOs.QuotationLineResp;
import com.erp.module.sales.controller.vo.QuoteVOs.QuotationLineSave;
import com.erp.module.sales.controller.vo.QuoteVOs.QuotationQuery;
import com.erp.module.sales.controller.vo.QuoteVOs.QuotationRow;
import com.erp.module.sales.controller.vo.QuoteVOs.QuotationSave;
import com.erp.module.sales.controller.vo.QuoteVOs.QuoteOpenLine;
import com.erp.module.sales.controller.vo.QuoteVOs.RevisionRow;
import com.erp.module.sales.controller.vo.QuoteVOs.ToOrderLine;
import com.erp.module.sales.dal.dataobject.SalOrderDO;
import com.erp.module.sales.dal.dataobject.SalQuotationDO;
import com.erp.module.sales.dal.dataobject.SalQuotationLineDO;
import com.erp.module.sales.dal.dataobject.SalRfqDO;
import com.erp.module.sales.dal.mapper.SalOrderMapper;
import com.erp.module.sales.dal.mapper.SalQuotationLineMapper;
import com.erp.module.sales.dal.mapper.SalQuotationMapper;
import com.erp.module.sales.dal.mapper.SalRfqMapper;
import com.erp.module.sales.service.CostService;
import com.erp.module.sales.service.QuoteStatus;
import com.erp.module.sales.service.SalAction;
import com.erp.module.sales.service.SalStateMachines;
import com.erp.module.sales.service.SalSupport;
import com.erp.module.sales.service.order.OrderService;
import com.erp.module.sales.service.price.PriceListService;
import com.erp.module.system.api.file.FileApi;
import com.erp.module.system.api.org.OrgDTO;
import com.erp.module.system.api.paymentterm.PaymentTermApi;
import com.erp.module.system.api.paymentterm.PaymentTermDTO;
import com.erp.module.system.api.user.UserDTO;
import com.erp.module.system.api.workflow.ApprovalCompletedEvent;
import com.erp.module.system.api.workflow.StartResult;
import com.erp.module.system.api.workflow.WorkflowApi;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 报价单（需求 04-02）：草稿 → 审批中 → 已审核 → 已发送 → 已成交 / 未成交 / 已过期；可修订为新版本（原单 → 已修订）。
 */
@Service("salQuotationService")
public class QuotationService {

    public static final String BIZ_TYPE = SalesModuleConfig.QUOTATION;
    static final List<String> DEFAULT_STATUSES = List.of(QuoteStatus.DRAFT.name(), QuoteStatus.PENDING.name(), QuoteStatus.APPROVED.name(),
            QuoteStatus.SENT.name());

    private final SalQuotationMapper mapper;
    private final SalQuotationLineMapper lineMapper;
    private final SalRfqMapper rfqMapper;
    private final SalOrderMapper orderMapper;
    private final OrderService orderService;
    private final PriceListService priceListService;
    private final CostService costService;
    private final SalSupport support;
    private final CustomerPartApi customerPartApi;
    private final OpportunityApi opportunityApi;
    private final PaymentTermApi paymentTermApi;
    private final WorkflowApi workflowApi;
    private final FileApi fileApi;
    private final DomainEventPublisher eventPublisher;

    public QuotationService(SalQuotationMapper mapper, SalQuotationLineMapper lineMapper, SalRfqMapper rfqMapper, SalOrderMapper orderMapper,
                            OrderService orderService, PriceListService priceListService, CostService costService, SalSupport support,
                            CustomerPartApi customerPartApi, OpportunityApi opportunityApi, PaymentTermApi paymentTermApi, WorkflowApi workflowApi,
                            FileApi fileApi, DomainEventPublisher eventPublisher) {
        this.mapper = mapper;
        this.lineMapper = lineMapper;
        this.rfqMapper = rfqMapper;
        this.orderMapper = orderMapper;
        this.orderService = orderService;
        this.priceListService = priceListService;
        this.costService = costService;
        this.support = support;
        this.customerPartApi = customerPartApi;
        this.opportunityApi = opportunityApi;
        this.paymentTermApi = paymentTermApi;
        this.workflowApi = workflowApi;
        this.fileApi = fileApi;
        this.eventPublisher = eventPublisher;
    }

    // ==================== 查询 ====================

    public PageResult<QuotationRow> page(QuotationQuery q) {
        LambdaQueryWrapper<SalQuotationDO> w = new LambdaQueryWrapper<SalQuotationDO>().eq(SalQuotationDO::getDeleted, false)
                .likeRight(StringUtils.hasText(q.getDocNo()), SalQuotationDO::getDocNo, q.getDocNo() == null ? null : q.getDocNo().trim().toUpperCase())
                .eq(q.getCustomerId() != null, SalQuotationDO::getCustomerId, q.getCustomerId())
                .eq(q.getOwnerId() != null, SalQuotationDO::getOwnerId, q.getOwnerId())
                .ge(q.getValidFrom() != null, SalQuotationDO::getValidUntil, q.getValidFrom())
                .le(q.getValidTo() != null, SalQuotationDO::getValidUntil, q.getValidTo())
                .ge(q.getDateFrom() != null, SalQuotationDO::getDocDate, q.getDateFrom())
                .le(q.getDateTo() != null, SalQuotationDO::getDocDate, q.getDateTo())
                .in(SalQuotationDO::getQuoteStatus, StringUtils.hasText(q.getStatuses()) ? Arrays.asList(q.getStatuses().split(",")) : DEFAULT_STATUSES);
        if (q.getMaterialId() != null) {
            w.inSql(SalQuotationDO::getId, "SELECT quotation_id FROM sal_quotation_line WHERE deleted = 0 AND material_id = " + q.getMaterialId().longValue());
        }
        w.orderByDesc(SalQuotationDO::getDocDate).orderByDesc(SalQuotationDO::getId);
        IPage<SalQuotationDO> page = mapper.selectScopedPage(Page.of(q.getPageNo(), q.getPageSize()), w);
        List<SalQuotationDO> list = page.getRecords();
        Map<Long, CustomerDTO> cs = support.customers(list.stream().map(SalQuotationDO::getCustomerId).toList());
        Map<Long, UserDTO> users = support.users(list.stream().map(SalQuotationDO::getOwnerId).toList());
        boolean cost = SalSupport.canViewQuoteCost();
        BigDecimal minMargin = costService.minMarginRate();
        LocalDate today = LocalDate.now();
        return new PageResult<>(list.stream().map(x -> new QuotationRow(x.getId(), x.getDocNo(), x.getRevision(), x.getCustomerId(),
                SalSupport.shortName(cs, x.getCustomerId()), x.getCurrency(), x.getTotalAmount(), SalSupport.mask(x.getMinMarginRate(), cost),
                cost && x.getMinMarginRate() != null && x.getMinMarginRate().compareTo(minMargin) < 0, x.getValidUntil(),
                x.getValidUntil().isBefore(today), x.getQuoteStatus(), x.getOwnerId(), SalSupport.name(users, x.getOwnerId()), x.getDocDate())).toList(),
                page.getTotal());
    }

    public QuotationDetail detail(Long id) {
        SalQuotationDO q = getOrThrow(id);
        DataScopes.check(q.getOrgId(), q.getDeptId(), q.getOwnerId(), "报价单");
        CustomerDTO c = support.customer(q.getCustomerId());
        boolean cost = SalSupport.canViewQuoteCost();
        List<SalQuotationLineDO> lines = lineMapper.selectByParent(id);
        ContactDTO contact = q.getContactId() == null ? null
                : support.customerApi().getContacts(c.id()).stream().filter(x -> x.id().equals(q.getContactId())).findFirst().orElse(null);
        PaymentTermDTO term = q.getPaymentTermId() == null ? null : paymentTermApi.get(q.getPaymentTermId()).orElse(null);
        SalRfqDO rfq = q.getRfqId() == null ? null : rfqMapper.selectById(q.getRfqId());
        Long root = q.getRootQuotationId() != null ? q.getRootQuotationId() : q.getId();
        List<SalQuotationDO> chain = mapper.selectList(new LambdaQueryWrapper<SalQuotationDO>()
                .and(w -> w.eq(SalQuotationDO::getId, root).or().eq(SalQuotationDO::getRootQuotationId, root)).orderByAsc(SalQuotationDO::getRevision));
        Map<Long, List<SalQuotationLineDO>> chainLines = lineMapper.selectByParents(chain.stream().map(SalQuotationDO::getId).toList()).stream()
                .collect(Collectors.groupingBy(SalQuotationLineDO::getQuotationId));
        List<RevisionRow> revisions = chain.size() <= 1 ? List.of() : chain.stream().map(r -> new RevisionRow(r.getId(), r.getDocNo(), r.getRevision(),
                r.getQuoteStatus(), r.getDocDate(), r.getTotalAmount(), lineResps(chainLines.getOrDefault(r.getId(), List.of()), cost))).toList();
        List<RelatedDoc> related = new ArrayList<>();
        if (rfq != null) related.add(new RelatedDoc("UP", "RFQ", rfq.getDocNo(), rfq.getDocDate(), rfq.getRfqStatus(), rfq.getRfqStatus(), "/sales/rfq/" + rfq.getId()));
        orderMapper.selectList(new LambdaQueryWrapper<SalOrderDO>().eq(SalOrderDO::getQuotationId, id).orderByAsc(SalOrderDO::getId)).forEach(o ->
                related.add(new RelatedDoc("DOWN", "销售订单", o.getDocNo(), o.getDocDate(), o.getStatus().name(), o.getStatus().label(), "/sales/order/" + o.getId())));
        return new QuotationDetail(q.getId(), q.getDocNo(), q.getRevision(), q.getDocDate(), q.getQuoteStatus(), c.id(), c.shortName(), c.status().name(),
                c.level(), q.getContactId(), contact == null ? null : contact.name(), q.getRfqId(), rfq == null ? null : rfq.getDocNo(), q.getOpportunityId(),
                q.getCurrency(), q.getExchangeRate(), q.getTradeTerm(), q.getPaymentTermId(), term == null ? null : term.name(),
                Boolean.TRUE.equals(q.getTaxIncluded()), q.getValidUntil(), q.getValidUntil().isBefore(LocalDate.now()), q.getTerms(), q.getRemark(),
                q.getTotalAmount(), q.getTotalAmountBase(), SalSupport.mask(q.getMinMarginRate(), cost), cost && Boolean.TRUE.equals(q.getBelowFloor()),
                q.getLostReason(), q.getLostRemark(), q.getSentAt(), q.getParentQuotationId(), q.getOwnerId(), support.userName(q.getOwnerId()), cost,
                q.getCreatedAt(), q.getVersion(), lineResps(lines, cost), revisions, related);
    }

    List<QuotationLineResp> lineResps(List<SalQuotationLineDO> lines, boolean cost) {
        Map<Long, MaterialDTO> ms = support.materials(lines.stream().map(SalQuotationLineDO::getMaterialId).toList());
        return lines.stream().map(l -> {
            MaterialDTO m = ms.get(l.getMaterialId());
            return new QuotationLineResp(l.getId(), l.getLineNo(), l.getMaterialId(), m == null ? null : m.code(), m == null ? null : m.name(),
                    m == null ? null : m.spec(), m == null ? null : m.baseUom(), l.getCustomerPartNo(), l.getDescription(), l.getUom(), l.getMinQty(),
                    l.getPrice(), l.getTaxRate(), SalSupport.mask(l.getCostPrice(), cost), SalSupport.mask(l.getMarginRate(), cost),
                    cost && Boolean.TRUE.equals(l.getBelowFloor()), l.getMoq(), l.getLeadTimeDays(), l.getToolingFee(), l.getRfqLineId(), l.getRemark());
        }).toList();
    }

    /** 订单“从报价生成”选单：已审核 / 已发送、未过期的报价单行 */
    public List<QuoteOpenLine> openLines(Long customerId) {
        List<SalQuotationDO> qs = mapper.selectScopedList(new LambdaQueryWrapper<SalQuotationDO>().eq(SalQuotationDO::getDeleted, false)
                .eq(customerId != null, SalQuotationDO::getCustomerId, customerId)
                .in(SalQuotationDO::getQuoteStatus, QuoteStatus.APPROVED.name(), QuoteStatus.SENT.name()).ge(SalQuotationDO::getValidUntil, LocalDate.now())
                .orderByDesc(SalQuotationDO::getId).last("LIMIT 200"));
        if (qs.isEmpty()) return List.of();
        Map<Long, SalQuotationDO> byId = qs.stream().collect(Collectors.toMap(SalQuotationDO::getId, x -> x));
        List<SalQuotationLineDO> lines = lineMapper.selectByParents(byId.keySet());
        Map<Long, MaterialDTO> ms = support.materials(lines.stream().map(SalQuotationLineDO::getMaterialId).toList());
        Map<Long, CustomerDTO> cs = support.customers(qs.stream().map(SalQuotationDO::getCustomerId).toList());
        return lines.stream().map(l -> {
            SalQuotationDO q = byId.get(l.getQuotationId());
            MaterialDTO m = ms.get(l.getMaterialId());
            return new QuoteOpenLine(q.getId(), q.getDocNo(), q.getRevision(), q.getCustomerId(), SalSupport.shortName(cs, q.getCustomerId()), q.getCurrency(),
                    l.getId(), l.getMaterialId(), m == null ? null : m.code(), m == null ? null : m.name(), l.getUom(), l.getMinQty(), l.getPrice(),
                    Boolean.TRUE.equals(q.getTaxIncluded()), l.getLeadTimeDays(), q.getValidUntil());
        }).toList();
    }

    // ==================== 编辑 ====================

    @Transactional(rollbackFor = Exception.class)
    public SaveResult create(QuotationSave req) {
        return create(req, null, null, null);
    }

    /** @param rfqId、parent 来源（RFQ 生成、修订） */
    @Transactional(rollbackFor = Exception.class)
    public SaveResult create(QuotationSave req, Long rfqId, SalQuotationDO parent, Long ownerId) {
        SalQuotationDO q = new SalQuotationDO();
        q.setDocNo(parent != null ? parent.getDocNo() : support.nextNo(BIZ_TYPE));
        q.setDocDate(LocalDate.now());
        q.setQuoteStatus(QuoteStatus.DRAFT.name());
        q.setStatus(DocStatus.DRAFT);
        q.setRevision(parent == null ? 0 : parent.getRevision() + 1);
        q.setParentQuotationId(parent == null ? null : parent.getId());
        q.setRootQuotationId(parent == null ? null : parent.getRootQuotationId() != null ? parent.getRootQuotationId() : parent.getId());
        q.setRfqId(rfqId != null ? rfqId : parent == null ? null : parent.getRfqId());
        q.setTotalAmount(BigDecimal.ZERO);
        q.setTotalAmountBase(BigDecimal.ZERO);
        q.setBelowFloor(false);
        CustomerDTO c = fillHeader(q, req, ownerId);
        mapper.insert(q);
        List<String> warnings = saveLines(q, c, req.lines());
        mapper.updateByIdOrFail(q);
        if (req.fileIds() != null && !req.fileIds().isEmpty()) fileApi.bind(req.fileIds(), BIZ_TYPE, q.getId());
        if (parent == null) {
            if (q.getOpportunityId() != null) opportunityApi.onQuotationCreated(q.getOpportunityId());
            eventPublisher.publish(new QuotationCreatedEvent(q.getId(), q.getDocNo(), q.getCustomerId(), q.getOpportunityId()));
        }
        return new SaveResult(q.getId(), warnings);
    }

    @Transactional(rollbackFor = Exception.class)
    public SaveResult update(Long id, QuotationSave req) {
        SalQuotationDO q = getOrThrow(id);
        DataScopes.check(q.getOrgId(), q.getDeptId(), q.getOwnerId(), "报价单");
        requireStatus(q, "修改", QuoteStatus.DRAFT);
        if (req.version() != null) q.setVersion(req.version());
        CustomerDTO c = fillHeader(q, req, null);
        List<String> warnings = saveLines(q, c, req.lines());
        mapper.updateByIdOrFail(q);
        if (req.fileIds() != null && !req.fileIds().isEmpty()) fileApi.bind(req.fileIds(), BIZ_TYPE, q.getId());
        return new SaveResult(q.getId(), warnings);
    }

    static QuoteStatus status(SalQuotationDO q) {
        return QuoteStatus.valueOf(q.getQuoteStatus());
    }

    static void requireStatus(SalQuotationDO q, String action, QuoteStatus... allowed) {
        QuoteStatus s = status(q);
        if (Arrays.stream(allowed).noneMatch(a -> a == s)) throw BizException.of(SalesErrorCodes.QUOTATION_STATUS, s.label(), action);
    }

    /** R01：客户为潜在、审批中或正式；黑名单 / 停用不能报价 */
    private CustomerDTO fillHeader(SalQuotationDO q, QuotationSave req, Long ownerId) {
        CustomerDTO c = support.customer(req.customerId());
        if (c.status() == CustomerStatus.BLACKLIST || c.status() == CustomerStatus.DISABLED) {
            throw BizException.of(SalesErrorCodes.QUOTATION_CUSTOMER_BLOCKED, c.shortName());
        }
        support.customerApi().validateCanQuote(c.id());
        q.setCustomerId(c.id());
        if (req.contactId() != null && support.customerApi().getContacts(c.id()).stream().noneMatch(x -> x.id().equals(req.contactId()))) {
            throw BizException.of(GlobalErrorCodes.DATA_NOT_EXISTS, "联系人");
        }
        q.setContactId(req.contactId() != null ? req.contactId()
                : support.customerApi().getContacts(c.id()).stream().filter(ContactDTO::primary).map(ContactDTO::id).findFirst().orElse(null));
        q.setOpportunityId(req.opportunityId());
        String currency = StringUtils.hasText(req.currency()) ? req.currency().trim().toUpperCase()
                : StringUtils.hasText(c.currency()) ? c.currency() : support.baseCurrency();
        support.currencyApi().validate(currency);
        q.setCurrency(currency);
        BigDecimal rate = currency.equals(support.baseCurrency()) ? BigDecimal.ONE
                : req.exchangeRate() != null ? req.exchangeRate() : support.currencyApi().getRate(currency, q.getDocDate());
        if (rate.signum() <= 0) throw new BizException(SalesErrorCodes.EXCHANGE_RATE_POSITIVE);
        q.setExchangeRate(rate);
        String trade = req.tradeTerm() != null ? SalSupport.trim(req.tradeTerm()) : c.tradeTerm();
        if (trade != null && !trade.equals(q.getTradeTerm())) support.dict().validate("sys_trade_term", trade, "贸易条款");
        q.setTradeTerm(trade);
        Long termId = req.paymentTermId() != null ? req.paymentTermId() : c.paymentTermId();
        if (termId != null) paymentTermApi.validate(termId, "SALES");
        q.setPaymentTermId(termId);
        q.setTaxIncluded(req.taxIncluded() != null ? req.taxIncluded() : !c.foreign());
        LocalDate valid = req.validUntil() != null ? req.validUntil()
                : q.getDocDate().plusDays(support.params().getInt(SalesModuleConfig.P_QUOTE_VALID_DAYS));
        if (valid.isBefore(q.getDocDate())) throw new BizException(SalesErrorCodes.QUOTATION_VALID_UNTIL);
        q.setValidUntil(valid);
        q.setTerms(SalSupport.trim(req.terms()));
        q.setRemark(SalSupport.trim(req.remark()));
        support.fillOwner(q, ownerId != null ? ownerId : q.getOwnerId() != null ? q.getOwnerId() : c.ownerId());
        return c;
    }

    /** R02：同一物料的阶梯起始数量不重复，且有一档 = MOQ 或 0；R03 毛利与底价 */
    private List<String> saveLines(SalQuotationDO q, CustomerDTO c, List<QuotationLineSave> lines) {
        if (lines == null || lines.isEmpty()) throw new BizException(SalesErrorCodes.DOC_NO_LINES);
        Map<Long, MaterialDTO> ms = support.materials(lines.stream().map(QuotationLineSave::materialId).toList());
        Map<Long, CostService.UnitCost> costs = costService.unitCosts(ms.keySet());
        BigDecimal minMargin = costService.minMarginRate();
        Map<String, List<QuotationLineSave>> tiers = new LinkedHashMap<>();
        for (QuotationLineSave l : lines) {
            MaterialDTO m = ms.get(l.materialId());
            if (m == null) throw BizException.of(GlobalErrorCodes.DATA_NOT_EXISTS, "物料");
            String uom = StringUtils.hasText(l.uom()) ? l.uom().trim() : m.baseUom();
            tiers.computeIfAbsent(m.id() + "|" + uom, k -> new ArrayList<>()).add(l);
        }
        for (Map.Entry<String, List<QuotationLineSave>> e : tiers.entrySet()) {
            MaterialDTO m = ms.get(Long.valueOf(e.getKey().split("\\|")[0]));
            Set<BigDecimal> seen = new HashSet<>();
            boolean start = false;
            for (QuotationLineSave l : e.getValue()) {
                BigDecimal min = Decimals.qty(SalSupport.nz(l.minQty()));
                if (!seen.add(min)) throw BizException.of(SalesErrorCodes.QUOTATION_TIER_DUPLICATE, m.code());
                if (min.signum() == 0 || l.moq() != null && min.compareTo(Decimals.qty(l.moq())) == 0) start = true;
            }
            if (!start) throw BizException.of(SalesErrorCodes.QUOTATION_TIER_START, m.code());
        }
        lineMapper.deleteByParent(q.getId());
        List<String> warnings = new ArrayList<>();
        boolean incl = Boolean.TRUE.equals(q.getTaxIncluded());
        BigDecimal defaultTax = c.salesTaxRate() != null ? c.salesTaxRate() : priceListService.defaultTaxRate();
        int no = 0;
        for (QuotationLineSave l : lines) {
            no++;
            MaterialDTO m = ms.get(l.materialId());
            if (l.price().signum() < 0) throw BizException.of(SalesErrorCodes.LINE_PRICE_NEGATIVE, no);
            SalQuotationLineDO d = new SalQuotationLineDO();
            d.setQuotationId(q.getId());
            d.setLineNo(no);
            d.setMaterialId(m.id());
            d.setUom(StringUtils.hasText(l.uom()) ? l.uom().trim() : m.baseUom());
            BigDecimal basePerUom = support.toBase(m.id(), BigDecimal.ONE, d.getUom());
            CustomerPartDTO cp = StringUtils.hasText(l.customerPartNo()) ? null : customerPartApi.toCustomerPart(c.id(), m.id()).orElse(null);
            d.setCustomerPartNo(StringUtils.hasText(l.customerPartNo()) ? l.customerPartNo().trim() : cp == null ? null : cp.customerPartNo());
            d.setDescription(StringUtils.hasText(l.description()) ? l.description().trim()
                    : (c.foreign() && StringUtils.hasText(m.nameEn()) ? m.nameEn() : m.name()) + (StringUtils.hasText(m.spec()) ? " " + m.spec() : ""));
            d.setMinQty(Decimals.qty(SalSupport.nz(l.minQty())));
            d.setPrice(Decimals.price(l.price()));
            d.setTaxRate(l.taxRate() != null ? l.taxRate() : defaultTax);
            d.setMoq(l.moq() == null ? null : Decimals.qty(l.moq()));
            d.setLeadTimeDays(l.leadTimeDays());
            d.setToolingFee(l.toolingFee() == null ? null : Decimals.amount(l.toolingFee()));
            d.setRfqLineId(l.rfqLineId());
            d.setRemark(SalSupport.trim(l.remark()));
            BigDecimal unitCost = l.costPrice() != null ? l.costPrice() : costs.containsKey(m.id()) ? costs.get(m.id()).cost() : null;
            BigDecimal excl = incl ? d.getPrice().divide(BigDecimal.ONE.add(d.getTaxRate()), 10, RoundingMode.HALF_UP) : d.getPrice();
            CostService.Margin mg = costService.margin(excl, q.getExchangeRate(), basePerUom, unitCost, minMargin);
            d.setCostPrice(mg.cost());
            d.setMarginRate(mg.marginRate());
            d.setBelowFloor(mg.belowFloor());
            lineMapper.insert(d);
        }
        recalcTotals(q);
        return warnings;
    }

    /** 参考金额：每个物料首档（起始数量最小）的数量 × 单价（首档为 0 时取 MOQ，都没有按 1） */
    private void recalcTotals(SalQuotationDO q) {
        List<SalQuotationLineDO> lines = lineMapper.selectByParent(q.getId());
        Map<String, SalQuotationLineDO> first = new LinkedHashMap<>();
        for (SalQuotationLineDO l : lines) {
            first.merge(l.getMaterialId() + "|" + l.getUom(), l, (a, b) -> a.getMinQty().compareTo(b.getMinQty()) <= 0 ? a : b);
        }
        BigDecimal total = BigDecimal.ZERO;
        for (SalQuotationLineDO l : first.values()) {
            BigDecimal qty = l.getMinQty().signum() > 0 ? l.getMinQty() : l.getMoq() != null && l.getMoq().signum() > 0 ? l.getMoq() : BigDecimal.ONE;
            total = total.add(Decimals.multiplyAmount(qty, l.getPrice()));
        }
        q.setTotalAmount(total);
        q.setTotalAmountBase(support.currencyApi().toBase(total, q.getExchangeRate()));
        q.setMinMarginRate(lines.stream().map(SalQuotationLineDO::getMarginRate).filter(Objects::nonNull).min(Comparator.naturalOrder()).orElse(null));
        q.setBelowFloor(lines.stream().anyMatch(l -> Boolean.TRUE.equals(l.getBelowFloor())));
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        SalQuotationDO q = getOrThrow(id);
        DataScopes.check(q.getOrgId(), q.getDeptId(), q.getOwnerId(), "报价单");
        requireStatus(q, "删除", QuoteStatus.DRAFT);
        lineMapper.deleteByParent(id);
        mapper.deleteById(id);
        fileApi.deleteByBiz(BIZ_TYPE, id);
    }

    // ==================== 状态流转 ====================

    private void fire(SalQuotationDO q, SalAction action, String reason) {
        QuoteStatus from = status(q);
        QuoteStatus to = SalStateMachines.QUOTATION.fire(from, action);
        q.setQuoteStatus(to.name());
        q.setStatus(to.docStatus());
        mapper.updateByIdOrFail(q);
        support.log(BIZ_TYPE, q.getId(), q.getDocNo(), action.name(), action.label(), from.name(), to.name(), reason);
    }

    /** R03：提交时计算毛利与底价（审批条件）；成本缺失按参数提示 */
    @Transactional(rollbackFor = Exception.class)
    public DocResult submit(Long id) {
        SalQuotationDO q = getOrThrow(id);
        DataScopes.check(q.getOrgId(), q.getDeptId(), q.getOwnerId(), "报价单");
        requireStatus(q, "提交", QuoteStatus.DRAFT);
        CustomerDTO c = support.customerApi().validateCanQuote(q.getCustomerId());
        List<SalQuotationLineDO> lines = lineMapper.selectByParent(id);
        if (lines.isEmpty()) throw new BizException(SalesErrorCodes.DOC_NO_LINES);
        if (q.getValidUntil().isBefore(LocalDate.now())) throw new BizException(SalesErrorCodes.QUOTATION_VALID_UNTIL);
        List<String> warnings = new ArrayList<>();
        Map<Long, MaterialDTO> ms = support.materials(lines.stream().map(SalQuotationLineDO::getMaterialId).toList());
        for (SalQuotationLineDO l : lines) support.materialApi().validateUsable(l.getMaterialId());
        if (!"IGNORE".equals(support.params().getString(SalesModuleConfig.P_NO_COST_POLICY))) {
            lines.stream().filter(l -> l.getCostPrice() == null).map(SalQuotationLineDO::getMaterialId).distinct()
                    .forEach(mid -> warnings.add("物料「" + ms.get(mid).code() + "」没有成本，无法计算毛利"));
        }
        fire(q, SalAction.SUBMIT, null);
        Map<String, Object> vars = new HashMap<>();
        vars.put("amountBase", q.getTotalAmountBase());
        vars.put("minMarginRate", q.getMinMarginRate() == null ? null : q.getMinMarginRate().multiply(SalSupport.HUNDRED));
        vars.put("belowFloor", Boolean.TRUE.equals(q.getBelowFloor()));
        vars.put("customerLevel", c.level());
        Map<String, Long> users = new HashMap<>();
        users.put("ownerId", q.getOwnerId());
        StartResult r = workflowApi.start(BIZ_TYPE, id, q.getDocNo(), "报价单 " + q.getDocNo() + " R" + q.getRevision() + " " + c.shortName(), vars, users,
                support.currentUser());
        if (!r.isStarted()) fire(q, SalAction.APPROVE, null);
        return new DocResult(q.getQuoteStatus(), warnings);
    }

    @EventListener
    public void onApproval(ApprovalCompletedEvent e) {
        if (!BIZ_TYPE.equals(e.getBizType())) return;
        SalQuotationDO q = getOrThrow(e.getBizId());
        if (status(q) != QuoteStatus.PENDING) return;
        switch (e.getResult()) {
            case APPROVED -> fire(q, SalAction.APPROVE, null);
            case WITHDRAWN -> fire(q, SalAction.WITHDRAW, null);
            default -> fire(q, SalAction.REJECT, e.getComment());
        }
    }

    /** 发送：记录发送时间 → 已发送 */
    @Transactional(rollbackFor = Exception.class)
    public void send(Long id) {
        SalQuotationDO q = getOrThrow(id);
        DataScopes.check(q.getOrgId(), q.getDeptId(), q.getOwnerId(), "报价单");
        q.setSentAt(LocalDateTime.now());
        fire(q, SalAction.SEND, null);
    }

    @Transactional(rollbackFor = Exception.class)
    public void lose(Long id, LoseReq req) {
        SalQuotationDO q = getOrThrow(id);
        DataScopes.check(q.getOrgId(), q.getDeptId(), q.getOwnerId(), "报价单");
        if (!StringUtils.hasText(req.lostReason())) throw new BizException(SalesErrorCodes.QUOTATION_LOST_REASON);
        support.dict().validate("sal_quote_lost_reason", req.lostReason(), "未成交原因");
        q.setLostReason(req.lostReason());
        q.setLostRemark(SalSupport.trim(req.remark()));
        fire(q, SalAction.LOSE, support.dict().label("sal_quote_lost_reason", req.lostReason()) + (req.remark() == null ? "" : "：" + req.remark()));
    }

    /** 修订：复制为新报价单（revision + 1），原单 → 已修订 */
    @Transactional(rollbackFor = Exception.class)
    public Long revise(Long id) {
        SalQuotationDO q = getOrThrow(id);
        DataScopes.check(q.getOrgId(), q.getDeptId(), q.getOwnerId(), "报价单");
        requireStatus(q, "修订", QuoteStatus.APPROVED, QuoteStatus.SENT, QuoteStatus.EXPIRED);
        List<QuotationLineSave> lines = lineMapper.selectByParent(id).stream().map(l -> new QuotationLineSave(l.getMaterialId(), l.getCustomerPartNo(),
                l.getDescription(), l.getUom(), l.getMinQty(), l.getPrice(), l.getTaxRate(), l.getMoq(), l.getLeadTimeDays(), l.getToolingFee(),
                l.getRfqLineId(), null, l.getRemark())).toList();
        LocalDate valid = LocalDate.now().plusDays(support.params().getInt(SalesModuleConfig.P_QUOTE_VALID_DAYS));
        SaveResult r = create(new QuotationSave(q.getCustomerId(), q.getContactId(), q.getOpportunityId(), q.getCurrency(), null, q.getTradeTerm(),
                q.getPaymentTermId(), q.getTaxIncluded(), valid, q.getTerms(), q.getRemark(), lines, null, null), null, q, q.getOwnerId());
        fire(q, SalAction.REVISE, "新版本 R" + (q.getRevision() + 1));
        return r.id();
    }

    /** 转订单（R05、R06）：选择的行和数量生成草稿订单，单价按数量匹配阶梯；报价 → 已成交 */
    @Transactional(rollbackFor = Exception.class)
    public Long toOrder(Long id, List<ToOrderLine> lines) {
        if (lines == null || lines.isEmpty()) throw new BizException(SalesErrorCodes.QUOTATION_TO_ORDER_EMPTY);
        FromQuotationResult r = toOrders(lines.stream().map(l -> new FromQuotationLine(l.quotationLineId(), l.qty(), l.requiredDate())).toList(), id);
        return r.orderIds().get(0);
    }

    /**
     * 订单“从报价生成”：同一客户、同一币别的行合并到一张草稿订单；单价按数量匹配报价阶梯。
     *
     * @param onlyQuotationId 非空时要求所有行属于该报价单
     */
    @Transactional(rollbackFor = Exception.class)
    public FromQuotationResult toOrders(List<FromQuotationLine> picks, Long onlyQuotationId) {
        if (picks == null || picks.isEmpty()) throw new BizException(SalesErrorCodes.QUOTATION_TO_ORDER_EMPTY);
        Map<Long, SalQuotationLineDO> lines = lineMapper.selectBatchIds(picks.stream().map(FromQuotationLine::quotationLineId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(SalQuotationLineDO::getId, l -> l));
        Map<Long, SalQuotationDO> quotes = new HashMap<>();
        Map<String, List<FromQuotationLine>> groups = new LinkedHashMap<>();
        LocalDate today = LocalDate.now();
        for (FromQuotationLine p : picks) {
            SalQuotationLineDO l = lines.get(p.quotationLineId());
            if (l == null) throw BizException.of(GlobalErrorCodes.DATA_NOT_EXISTS, "报价单行");
            if (p.qty() == null || p.qty().signum() <= 0) throw new BizException(SalesErrorCodes.QUOTATION_TO_ORDER_EMPTY);
            SalQuotationDO q = quotes.computeIfAbsent(l.getQuotationId(), this::getOrThrow);
            if (onlyQuotationId != null && !onlyQuotationId.equals(q.getId())) throw BizException.of(GlobalErrorCodes.BAD_REQUEST, "报价单行不属于该报价单");
            DataScopes.check(q.getOrgId(), q.getDeptId(), q.getOwnerId(), "报价单");
            QuoteStatus s = status(q);
            if (s == QuoteStatus.REVISED) throw new BizException(SalesErrorCodes.QUOTATION_REVISED);
            if (s == QuoteStatus.EXPIRED || s.isEffective() && q.getValidUntil().isBefore(today)) throw new BizException(SalesErrorCodes.QUOTATION_EXPIRED);
            if (!s.isEffective() && s != QuoteStatus.WON) throw BizException.of(SalesErrorCodes.QUOTATION_STATUS, s.label(), "转订单");
            CustomerDTO c = support.customer(q.getCustomerId());
            if (c.status() != CustomerStatus.ACTIVE) throw BizException.of(SalesErrorCodes.QUOTATION_CUSTOMER_NOT_ACTIVE, c.shortName());
            groups.computeIfAbsent(q.getCustomerId() + "|" + q.getCurrency() + "|" + q.getTaxIncluded(), k -> new ArrayList<>()).add(p);
        }
        List<Long> ids = new ArrayList<>();
        List<String> messages = new ArrayList<>();
        for (List<FromQuotationLine> group : groups.values()) {
            SalQuotationDO first = quotes.get(lines.get(group.get(0).quotationLineId()).getQuotationId());
            List<OrderLineSave> saves = new ArrayList<>();
            for (FromQuotationLine p : group) {
                SalQuotationLineDO picked = lines.get(p.quotationLineId());
                SalQuotationDO q = quotes.get(picked.getQuotationId());
                // 单价按数量匹配阶梯：同一报价、同一物料、同一单位中起始数量 ≤ 数量的最大一档
                SalQuotationLineDO tier = lineMapper.selectByParent(q.getId()).stream()
                        .filter(t -> t.getMaterialId().equals(picked.getMaterialId()) && t.getUom().equals(picked.getUom()))
                        .filter(t -> t.getMinQty().compareTo(p.qty()) <= 0).max(Comparator.comparing(SalQuotationLineDO::getMinQty)).orElse(picked);
                LocalDate required = p.requiredDate() != null ? p.requiredDate()
                        : today.plusDays(tier.getLeadTimeDays() == null ? 0 : tier.getLeadTimeDays());
                saves.add(new OrderLineSave(tier.getMaterialId(), tier.getCustomerPartNo(), tier.getDescription(), tier.getUom(), p.qty(), tier.getPrice(),
                        tier.getTaxRate(), required, tier.getId(), "报价单 " + q.getDocNo() + " R" + q.getRevision(), null));
            }
            SaveResult r = orderService.create(new OrderSave(null, "NORMAL", first.getCustomerId(), first.getContactId(), null, null, today,
                    first.getOwnerId(), first.getCurrency(), null, first.getTaxIncluded(), first.getPaymentTermId(), first.getTradeTerm(), null, null,
                    null, null, null, null, saves, null, null));
            SalOrderDO o = orderService.getOrThrow(r.id());
            o.setQuotationId(first.getId());
            o.setSourceType(BIZ_TYPE);
            o.setSourceId(first.getId());
            o.setSourceNo(first.getDocNo());
            orderMapper.updateByIdOrFail(o);
            ids.add(r.id());
            messages.addAll(r.warnings());
        }
        for (SalQuotationDO q : quotes.values()) {
            if (status(q).isEffective()) fire(q, SalAction.WIN, "转订单");
        }
        return new FromQuotationResult(ids, messages);
    }

    /** R04：每天 00:10 有效期已过的已审核 / 已发送报价 → 已过期 */
    @Transactional(rollbackFor = Exception.class)
    public int expire() {
        List<SalQuotationDO> list = mapper.selectList(new LambdaQueryWrapper<SalQuotationDO>()
                .in(SalQuotationDO::getQuoteStatus, QuoteStatus.APPROVED.name(), QuoteStatus.SENT.name()).lt(SalQuotationDO::getValidUntil, LocalDate.now()));
        list.forEach(q -> fire(q, SalAction.EXPIRE, "超过有效期"));
        return list.size();
    }

    // ==================== 打印 ====================

    public Map<String, Object> printData(Long id, String lang) {
        QuotationDetail d = detail(id);
        SalQuotationDO q = getOrThrow(id);
        boolean en = lang != null && lang.toLowerCase().startsWith("en");
        CustomerDTO c = support.customer(d.customerId());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("docNo", d.docNo());
        data.put("revisionText", "R" + d.revision());
        data.put("docDate", d.docDate());
        data.put("validUntil", d.validUntil());
        data.put("status", q.getStatus().name());
        OrgDTO company = support.orgs(List.of(SalSupport.nz(q.getOrgId()))).values().stream().findFirst().orElse(null);
        data.put("companyName", company == null ? "" : (en && company.nameEn() != null ? company.nameEn() : company.name()));
        data.put("companyAddress", company == null ? "" : Objects.toString(en && company.addressEn() != null ? company.addressEn() : company.address(), ""));
        data.put("customerName", en && StringUtils.hasText(c.nameEn()) ? c.nameEn() : c.name());
        data.put("contactName", Objects.toString(d.contactName(), ""));
        data.put("currency", d.currency());
        data.put("tradeTerm", Objects.toString(d.tradeTerm(), ""));
        PaymentTermDTO term = q.getPaymentTermId() == null ? null : paymentTermApi.get(q.getPaymentTermId()).orElse(null);
        data.put("paymentTermName", term == null ? "" : en && StringUtils.hasText(term.nameEn()) ? term.nameEn() : term.name());
        data.put("taxIncludedText", d.taxIncluded() ? (en ? "Tax included" : "含税") : (en ? "Tax excluded" : "不含税"));
        data.put("terms", Objects.toString(d.terms(), ""));
        data.put("ownerName", Objects.toString(d.ownerName(), ""));
        List<Map<String, Object>> lines = new ArrayList<>();
        for (QuotationLineResp l : d.lines()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("lineNo", l.lineNo());
            m.put("materialCode", l.materialCode());
            m.put("customerPartNo", Objects.toString(l.customerPartNo(), ""));
            m.put("description", Objects.toString(l.description(), l.materialName()));
            m.put("uom", l.uom());
            m.put("minQty", l.minQty());
            m.put("price", l.price());
            m.put("moq", l.moq());
            m.put("leadTimeDays", l.leadTimeDays());
            m.put("toolingFee", l.toolingFee());
            m.put("remark", Objects.toString(l.remark(), ""));
            lines.add(m);
        }
        data.put("lines", lines);
        return data;
    }

    public SalQuotationDO getOrThrow(Long id) {
        SalQuotationDO q = id == null ? null : mapper.selectById(id);
        if (q == null) throw new BizException(SalesErrorCodes.QUOTATION_NOT_EXISTS);
        return q;
    }

    /** 客户转移：未完成报价的业务员改为新负责人 */
    public void transferOwner(java.util.Collection<Long> customerIds, Long newOwner) {
        for (SalQuotationDO q : mapper.selectList(new LambdaQueryWrapper<SalQuotationDO>().in(SalQuotationDO::getCustomerId, customerIds)
                .in(SalQuotationDO::getQuoteStatus, DEFAULT_STATUSES))) {
            if (newOwner.equals(q.getOwnerId())) continue;
            support.fillOwner(q, newOwner);
            mapper.updateByIdOrFail(q);
        }
    }
}
