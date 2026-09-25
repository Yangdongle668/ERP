package com.erp.module.purchase.service.statement;

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
import com.erp.module.engineering.api.material.MaterialDTO;
import com.erp.module.purchase.api.PurchaseErrorCodes;
import com.erp.module.purchase.api.statement.PurchaseStatementConfirmedEvent;
import com.erp.module.purchase.api.statement.PurchaseStatementUnconfirmingEvent;
import com.erp.module.purchase.config.PurchaseModuleConfig;
import com.erp.module.purchase.controller.vo.CommonVOs.DocResult;
import com.erp.module.purchase.controller.vo.StatementVOs.LineResp;
import com.erp.module.purchase.controller.vo.StatementVOs.LineSave;
import com.erp.module.purchase.controller.vo.StatementVOs.StatementDetail;
import com.erp.module.purchase.controller.vo.StatementVOs.StatementQuery;
import com.erp.module.purchase.controller.vo.StatementVOs.StatementRow;
import com.erp.module.purchase.controller.vo.StatementVOs.StatementSave;
import com.erp.module.purchase.dal.dataobject.IdQtyRow;
import com.erp.module.purchase.dal.dataobject.OrderDO;
import com.erp.module.purchase.dal.dataobject.OrderLineDO;
import com.erp.module.purchase.dal.dataobject.OutsourcingDO;
import com.erp.module.purchase.dal.dataobject.ReceiptDO;
import com.erp.module.purchase.dal.dataobject.ReceiptLineDO;
import com.erp.module.purchase.dal.dataobject.ReturnDO;
import com.erp.module.purchase.dal.dataobject.ReturnLineDO;
import com.erp.module.purchase.dal.dataobject.StatementDO;
import com.erp.module.purchase.dal.dataobject.StatementLineDO;
import com.erp.module.purchase.dal.dataobject.SupplierDO;
import com.erp.module.purchase.dal.mapper.ReceiptLineMapper;
import com.erp.module.purchase.dal.mapper.ReceiptMapper;
import com.erp.module.purchase.dal.mapper.ReturnLineMapper;
import com.erp.module.purchase.dal.mapper.ReturnMapper;
import com.erp.module.purchase.dal.mapper.StatementLineMapper;
import com.erp.module.purchase.dal.mapper.StatementMapper;
import com.erp.module.purchase.service.PurAction;
import com.erp.module.purchase.service.PurStateMachines;
import com.erp.module.purchase.service.PurSupport;
import com.erp.module.purchase.service.order.OrderService;
import com.erp.module.purchase.service.outsourcing.OutsourcingService;
import com.erp.module.purchase.service.receipt.ReceiptService;
import com.erp.module.purchase.service.receipt.ReturnService;
import com.erp.module.purchase.service.supplier.SupplierService;
import com.erp.module.system.api.currency.CurrencyApi;
import com.erp.module.system.api.file.FileApi;
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
import java.util.Collections;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 供应商对账（需求 07-09）：草稿 → 待审批 → 已审核（回写已对账数量）→ 已确认（供应商确认，推送财务）。
 * 可对账口径：货款 = 到货行合格数量（含特采；免检为入库数量）；退货 = 退款类退货出库数量（负数）；
 * 委外加工费 = 委外收货合格数量 × 加工费单价；调整手工录入。同一来源行按剩余可对账数量加载，不能重复对账。
 */
@Service
public class StatementService {

    public static final String BIZ_TYPE = PurchaseModuleConfig.STATEMENT;
    public static final String GOODS = "GOODS";
    public static final String RETURN = "RETURN";
    public static final String PROCESS_FEE = "PROCESS_FEE";
    public static final String ADJUST = "ADJUST";
    static final Set<String> TYPES = Set.of(GOODS, RETURN, PROCESS_FEE, ADJUST);
    static final Map<String, String> TYPE_NAMES = Map.of(GOODS, "货款", RETURN, "退货", PROCESS_FEE, "委外加工费", ADJUST, "调整");
    static final List<String> NOT_VOIDED = List.of("DRAFT", "PENDING_APPROVAL", "APPROVED", "COMPLETED");
    static final List<String> APPROVED_STATUSES = List.of("APPROVED", "COMPLETED");

    private final StatementMapper mapper;
    private final StatementLineMapper lineMapper;
    private final ReceiptMapper receiptMapper;
    private final ReceiptLineMapper receiptLineMapper;
    private final ReturnMapper returnMapper;
    private final ReturnLineMapper returnLineMapper;
    private final OrderService orderService;
    private final OutsourcingService outsourcingService;
    private final SupplierService supplierService;
    private final PurSupport support;
    private final CurrencyApi currencyApi;
    private final WorkflowApi workflowApi;
    private final FileApi fileApi;
    private final DomainEventPublisher eventPublisher;

    public StatementService(StatementMapper mapper, StatementLineMapper lineMapper, ReceiptMapper receiptMapper, ReceiptLineMapper receiptLineMapper,
                            ReturnMapper returnMapper, ReturnLineMapper returnLineMapper, OrderService orderService, OutsourcingService outsourcingService,
                            SupplierService supplierService, PurSupport support, CurrencyApi currencyApi, WorkflowApi workflowApi, FileApi fileApi,
                            DomainEventPublisher eventPublisher) {
        this.mapper = mapper;
        this.lineMapper = lineMapper;
        this.receiptMapper = receiptMapper;
        this.receiptLineMapper = receiptLineMapper;
        this.returnMapper = returnMapper;
        this.returnLineMapper = returnLineMapper;
        this.orderService = orderService;
        this.outsourcingService = outsourcingService;
        this.supplierService = supplierService;
        this.support = support;
        this.currencyApi = currencyApi;
        this.workflowApi = workflowApi;
        this.fileApi = fileApi;
        this.eventPublisher = eventPublisher;
    }

    // ==================== 查询 ====================

    public PageResult<StatementRow> page(StatementQuery q) {
        LambdaQueryWrapper<StatementDO> w = new LambdaQueryWrapper<StatementDO>().eq(StatementDO::getDeleted, false)
                .likeRight(StringUtils.hasText(q.getDocNo()), StatementDO::getDocNo, q.getDocNo() == null ? null : q.getDocNo().trim().toUpperCase())
                .eq(q.getSupplierId() != null, StatementDO::getSupplierId, q.getSupplierId())
                .eq(q.getOwnerId() != null, StatementDO::getOwnerId, q.getOwnerId())
                .ge(q.getPeriodFrom() != null, StatementDO::getPeriodTo, q.getPeriodFrom())
                .le(q.getPeriodTo() != null, StatementDO::getPeriodFrom, q.getPeriodTo());
        if (StringUtils.hasText(q.getStatuses())) {
            w.in(StatementDO::getStatus, Arrays.stream(q.getStatuses().split(",")).map(String::trim).map(DocStatus::valueOf).toList());
        }
        w.orderByDesc(StatementDO::getPeriodTo).orderByDesc(StatementDO::getId);
        IPage<StatementDO> page = mapper.selectScopedPage(Page.of(q.getPageNo(), q.getPageSize()), w);
        Map<Long, SupplierDO> ss = supplierService.byIds(page.getRecords().stream().map(StatementDO::getSupplierId).toList());
        Map<Long, UserDTO> users = support.users(page.getRecords().stream().map(StatementDO::getOwnerId).toList());
        boolean price = PurSupport.canViewPrice();
        return new PageResult<>(page.getRecords().stream().map(s -> new StatementRow(s.getId(), s.getDocNo(), s.getSupplierId(),
                ss.containsKey(s.getSupplierId()) ? ss.get(s.getSupplierId()).getShortName() : null, s.getPeriodFrom(), s.getPeriodTo(), s.getCurrency(),
                PurSupport.mask(s.getGoodsAmount(), price), PurSupport.mask(s.getReturnAmount(), price), PurSupport.mask(s.getAdjustAmount(), price),
                PurSupport.mask(s.getTotalAmount(), price), s.getStatus().name(), s.getSupplierConfirmedAt(), PurSupport.name(users, s.getOwnerId())))
                .toList(), page.getTotal());
    }

    public StatementDetail detail(Long id) {
        StatementDO s = getOrThrow(id);
        DataScopes.check(s.getOrgId(), s.getDeptId(), s.getOwnerId(), "对账单");
        SupplierDO sup = supplierService.getOrThrow(s.getSupplierId());
        List<StatementLineDO> lines = lineMapper.selectByParent(id);
        boolean price = PurSupport.canViewPrice();
        Map<Long, MaterialDTO> ms = support.materials(lines.stream().map(StatementLineDO::getMaterialId).toList());
        return new StatementDetail(s.getId(), s.getDocNo(), s.getDocDate(), s.getStatus().name(), sup.getId(), sup.getShortName(), s.getPeriodFrom(),
                s.getPeriodTo(), s.getCurrency(), s.getExchangeRate(), PurSupport.mask(s.getGoodsAmount(), price), PurSupport.mask(s.getReturnAmount(), price),
                PurSupport.mask(s.getAdjustAmount(), price), PurSupport.mask(s.getTotalAmount(), price), PurSupport.mask(s.getTaxAmount(), price),
                s.getSupplierConfirmedAt(), s.getSupplierConfirmer(), s.getRemark(), s.getOwnerId(), support.userName(s.getOwnerId()), s.getCreatedAt(),
                s.getVersion(), price, lines.stream().map(l -> {
                    MaterialDTO m = ms.get(l.getMaterialId());
                    return new LineResp(l.getId(), l.getLineNo(), l.getLineType(), l.getSourceType(), l.getSourceId(), l.getSourceLineId(), l.getSourceNo(),
                            l.getOrderNo(), l.getMaterialId(), m == null ? null : m.code(), m == null ? null : m.name(), m == null ? null : m.baseUom(),
                            l.getBizDate(), l.getQty(), null, PurSupport.mask(l.getPriceInclTax(), price), l.getTaxRate(), PurSupport.mask(l.getAmount(), price),
                            PurSupport.mask(l.getTaxAmount(), price), PurSupport.mask(l.getTotalAmount(), price), l.getRemark());
                }).toList());
    }

    // ==================== 可对账明细 ====================

    /** 加载可对账明细（按剩余可对账数量） */
    public List<LineResp> candidates(Long supplierId, String currency, LocalDate from, LocalDate to) {
        if (from == null || to == null || from.isAfter(to)) throw new BizException(PurchaseErrorCodes.STATEMENT_PERIOD);
        SupplierDO s = supplierService.getOrThrow(supplierId);
        String cur = StringUtils.hasText(currency) ? currency.toUpperCase() : s.getCurrency();
        boolean price = PurSupport.canViewPrice();
        return buildCandidates(supplierId, from, to).stream().filter(c -> c.currency().equals(cur))
                .map(c -> toResp(c.line(), c.remaining(), price)).toList();
    }

    /** 候选来源行（含币别） */
    record Candidate(StatementLineDO line, String currency, BigDecimal remaining) {
    }

    private List<Candidate> buildCandidates(Long supplierId, LocalDate from, LocalDate to) {
        List<Candidate> list = new ArrayList<>();
        // 货款 / 委外加工费：检验已判定、已入库确认，入库确认日期在区间内
        List<ReceiptDO> receipts = receiptMapper.selectList(new LambdaQueryWrapper<ReceiptDO>()
                .eq(supplierId != null, ReceiptDO::getSupplierId, supplierId).in(ReceiptDO::getStatus, DocStatus.APPROVED, DocStatus.COMPLETED));
        if (!receipts.isEmpty()) {
            Map<Long, ReceiptDO> byId = receipts.stream().collect(Collectors.toMap(ReceiptDO::getId, r -> r));
            List<ReceiptLineDO> rls = receiptLineMapper.selectList(new LambdaQueryWrapper<ReceiptLineDO>().in(ReceiptLineDO::getReceiptId, byId.keySet())
                    .ge(ReceiptLineDO::getStockedDate, from).le(ReceiptLineDO::getStockedDate, to).ne(ReceiptLineDO::getInspectStatus, ReceiptService.PENDING)
                    .gt(ReceiptLineDO::getQualifiedQty, 0).orderByAsc(ReceiptLineDO::getStockedDate).orderByAsc(ReceiptLineDO::getId));
            Map<Long, BigDecimal> used = used(List.of(GOODS, PROCESS_FEE), NOT_VOIDED, rls.stream().map(ReceiptLineDO::getId).toList());
            for (ReceiptLineDO l : rls) {
                BigDecimal remaining = l.getQualifiedQty().subtract(used.getOrDefault(l.getId(), BigDecimal.ZERO));
                if (remaining.signum() <= 0) continue;
                ReceiptDO r = byId.get(l.getReceiptId());
                StatementLineDO d = new StatementLineDO();
                Source src = receiptSource(r, l);
                d.setLineType(ReceiptService.OUTSOURCE.equals(r.getReceiptType()) ? PROCESS_FEE : GOODS);
                d.setSourceType(PurchaseModuleConfig.RECEIPT);
                d.setSourceId(r.getId());
                d.setSourceLineId(l.getId());
                d.setSourceNo(r.getDocNo());
                d.setOrderLineId(l.getOrderLineId());
                d.setOrderNo(src.orderNo);
                d.setMaterialId(l.getMaterialId());
                d.setBizDate(l.getStockedDate());
                d.setPriceInclTax(src.priceInclTax);
                d.setTaxRate(src.taxRate);
                amounts(d, remaining);
                list.add(new Candidate(d, src.currency, remaining));
            }
        }
        // 退货：退款类退货出库已确认，出库日期在区间内（负数）
        List<ReturnDO> returns = returnMapper.selectList(new LambdaQueryWrapper<ReturnDO>().eq(supplierId != null, ReturnDO::getSupplierId, supplierId)
                .eq(ReturnDO::getHandling, ReturnService.REFUND).eq(ReturnDO::getStatus, DocStatus.COMPLETED));
        if (!returns.isEmpty()) {
            Map<Long, ReturnDO> byId = returns.stream().collect(Collectors.toMap(ReturnDO::getId, r -> r));
            List<ReturnLineDO> rls = returnLineMapper.selectList(new LambdaQueryWrapper<ReturnLineDO>().in(ReturnLineDO::getReturnId, byId.keySet())
                    .ge(ReturnLineDO::getOutDate, from).le(ReturnLineDO::getOutDate, to).gt(ReturnLineDO::getOutQty, 0));
            Map<Long, BigDecimal> used = used(List.of(RETURN), NOT_VOIDED, rls.stream().map(ReturnLineDO::getId).toList());
            Map<Long, OrderLineDO> ols = orderService.linesByIds(rls.stream().map(ReturnLineDO::getOrderLineId).toList());
            Map<Long, OrderDO> orders = orderService.byIds(ols.values().stream().map(OrderLineDO::getOrderId).toList());
            for (ReturnLineDO l : rls) {
                BigDecimal remaining = l.getOutQty().subtract(used.getOrDefault(l.getId(), BigDecimal.ZERO).negate());
                if (remaining.signum() <= 0) continue;
                ReturnDO r = byId.get(l.getReturnId());
                OrderLineDO ol = ols.get(l.getOrderLineId());
                StatementLineDO d = new StatementLineDO();
                d.setLineType(RETURN);
                d.setSourceType(PurchaseModuleConfig.RETURN);
                d.setSourceId(r.getId());
                d.setSourceLineId(l.getId());
                d.setSourceNo(r.getDocNo());
                d.setOrderLineId(l.getOrderLineId());
                d.setOrderNo(ol == null || !orders.containsKey(ol.getOrderId()) ? null : orders.get(ol.getOrderId()).getDocNo());
                d.setMaterialId(l.getMaterialId());
                d.setBizDate(l.getOutDate());
                d.setPriceInclTax(l.getPriceInclTax());
                d.setTaxRate(l.getTaxRate());
                amounts(d, remaining.negate());
                list.add(new Candidate(d, r.getCurrency(), remaining.negate()));
            }
        }
        return list;
    }

    /** 来源行已在对账单中的数量（退货为负数） */
    private Map<Long, BigDecimal> used(List<String> types, List<String> statuses, List<Long> ids) {
        if (ids.isEmpty()) return Collections.emptyMap();
        return lineMapper.sumBySourceLines(types, statuses, ids).stream().collect(Collectors.toMap(IdQtyRow::getId, IdQtyRow::getQty));
    }

    record Source(String currency, String orderNo, BigDecimal priceInclTax, BigDecimal taxRate) {
    }

    private Source receiptSource(ReceiptDO r, ReceiptLineDO l) {
        if (ReceiptService.OUTSOURCE.equals(r.getReceiptType())) {
            OutsourcingDO o = outsourcingService.getOrThrow(l.getOrderId());
            return new Source(o.getCurrency(), o.getDocNo(), Decimals.price(o.getProcessPrice().multiply(BigDecimal.ONE.add(o.getTaxRate()))), o.getTaxRate());
        }
        OrderDO o = orderService.getOrThrow(l.getOrderId());
        OrderLineDO ol = orderService.linesByIds(List.of(l.getOrderLineId())).get(l.getOrderLineId());
        return new Source(o.getCurrency(), o.getDocNo(), ReceiptService.basePriceInclTax(ol), ol.getTaxRate());
    }

    /** 价税合计 = ROUND(数量 × 含税单价, 2)，不含税 = ROUND(价税合计 ÷ (1 + 税率), 2)，税额 = 差额 */
    static void amounts(StatementLineDO d, BigDecimal qty) {
        d.setQty(Decimals.qty(qty));
        d.setTotalAmount(Decimals.multiplyAmount(qty, d.getPriceInclTax()));
        d.setAmount(Decimals.amount(d.getTotalAmount().divide(BigDecimal.ONE.add(d.getTaxRate()), 10, RoundingMode.HALF_UP)));
        d.setTaxAmount(d.getTotalAmount().subtract(d.getAmount()));
    }

    private LineResp toResp(StatementLineDO l, BigDecimal remaining, boolean price) {
        MaterialDTO m = l.getMaterialId() == null ? null : support.materials(List.of(l.getMaterialId())).get(l.getMaterialId());
        return new LineResp(l.getId(), l.getLineNo(), l.getLineType(), l.getSourceType(), l.getSourceId(), l.getSourceLineId(), l.getSourceNo(),
                l.getOrderNo(), l.getMaterialId(), m == null ? null : m.code(), m == null ? null : m.name(), m == null ? null : m.baseUom(), l.getBizDate(),
                l.getQty(), remaining, PurSupport.mask(l.getPriceInclTax(), price), l.getTaxRate(), PurSupport.mask(l.getAmount(), price),
                PurSupport.mask(l.getTaxAmount(), price), PurSupport.mask(l.getTotalAmount(), price), l.getRemark());
    }

    // ==================== 编辑 ====================

    @Transactional(rollbackFor = Exception.class)
    public Long create(StatementSave req) {
        StatementDO s = new StatementDO();
        s.setDocNo(support.nextNo(BIZ_TYPE));
        s.setDocDate(LocalDate.now());
        s.setStatus(DocStatus.DRAFT);
        SupplierDO sup = supplierService.getOrThrow(req.supplierId());
        support.fillOwner(s, sup.getBuyerId());
        fillHeader(s, sup, req);
        zero(s);
        mapper.insert(s);
        saveLines(s, req.lines());
        mapper.updateByIdOrFail(s);
        if (req.fileIds() != null && !req.fileIds().isEmpty()) fileApi.bind(req.fileIds(), BIZ_TYPE, s.getId());
        return s.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, StatementSave req) {
        StatementDO s = getOrThrow(id);
        DataScopes.check(s.getOrgId(), s.getDeptId(), s.getOwnerId(), "对账单");
        PurSupport.requireDraft(s);
        if (req.version() != null) s.setVersion(req.version());
        fillHeader(s, supplierService.getOrThrow(req.supplierId()), req);
        saveLines(s, req.lines());
        mapper.updateByIdOrFail(s);
        if (req.fileIds() != null && !req.fileIds().isEmpty()) fileApi.bind(req.fileIds(), BIZ_TYPE, s.getId());
    }

    private void fillHeader(StatementDO s, SupplierDO sup, StatementSave req) {
        if (req.periodFrom().isAfter(req.periodTo())) throw new BizException(PurchaseErrorCodes.STATEMENT_PERIOD);
        String cur = StringUtils.hasText(req.currency()) ? req.currency().trim().toUpperCase() : sup.getCurrency();
        currencyApi.validate(cur);
        s.setSupplierId(sup.getId());
        s.setCurrency(cur);
        s.setExchangeRate(currencyApi.getRate(cur, req.periodTo().isAfter(LocalDate.now()) ? LocalDate.now() : req.periodTo()));
        s.setPeriodFrom(req.periodFrom());
        s.setPeriodTo(req.periodTo());
        s.setRemark(PurSupport.trim(req.remark()));
    }

    private static void zero(StatementDO s) {
        s.setGoodsAmount(BigDecimal.ZERO);
        s.setReturnAmount(BigDecimal.ZERO);
        s.setAdjustAmount(BigDecimal.ZERO);
        s.setTotalAmount(BigDecimal.ZERO);
        s.setTaxAmount(BigDecimal.ZERO);
    }

    /** 明细：来源行按剩余可对账数量校验（R01）、币别一致（R02）；单价与金额按来源重新计算 */
    private void saveLines(StatementDO s, List<LineSave> lines) {
        lineMapper.deleteByParent(s.getId());
        List<LineSave> list = lines == null ? List.of() : lines;
        if (list.isEmpty()) throw new BizException(PurchaseErrorCodes.DOC_NO_LINES);
        Map<Long, Candidate> goods = new HashMap<>();
        Map<Long, Candidate> rets = new HashMap<>();
        if (list.stream().anyMatch(l -> !ADJUST.equals(l.lineType()))) {
            for (Candidate c : buildCandidates(s.getSupplierId(), LocalDate.of(1900, 1, 1), LocalDate.of(9999, 12, 31))) {
                (RETURN.equals(c.line().getLineType()) ? rets : goods).put(c.line().getSourceLineId(), c);
            }
        }
        zero(s);
        int no = 0;
        for (LineSave l : list) {
            no++;
            if (!TYPES.contains(l.lineType())) throw BizException.of(GlobalErrorCodes.BAD_REQUEST, "明细类型");
            StatementLineDO d;
            if (ADJUST.equals(l.lineType())) {
                if (!StringUtils.hasText(l.remark())) throw new BizException(PurchaseErrorCodes.STATEMENT_ADJUST_REMARK);
                if (l.totalAmount() == null) throw BizException.of(PurchaseErrorCodes.LINE_FIELD_REQUIRED, no, "金额");
                d = new StatementLineDO();
                d.setLineType(ADJUST);
                d.setBizDate(l.bizDate() != null ? l.bizDate() : s.getPeriodTo());
                d.setQty(BigDecimal.ZERO);
                d.setPriceInclTax(BigDecimal.ZERO);
                d.setTaxRate(l.taxRate() != null ? l.taxRate() : BigDecimal.ZERO);
                d.setTotalAmount(Decimals.amount(l.totalAmount()));
                d.setAmount(Decimals.amount(d.getTotalAmount().divide(BigDecimal.ONE.add(d.getTaxRate()), 10, RoundingMode.HALF_UP)));
                d.setTaxAmount(d.getTotalAmount().subtract(d.getAmount()));
                d.setRemark(l.remark().trim());
            } else {
                Candidate c = (RETURN.equals(l.lineType()) ? rets : goods).get(l.sourceLineId());
                BigDecimal remaining = c == null ? BigDecimal.ZERO : c.remaining().abs();
                BigDecimal qty = l.qty() == null ? remaining : l.qty().abs();
                if (c == null || qty.signum() <= 0 || qty.compareTo(remaining) > 0) {
                    throw BizException.of(PurchaseErrorCodes.STATEMENT_LINE_TAKEN, no, PurSupport.plain(remaining));
                }
                if (!c.currency().equals(s.getCurrency())) throw new BizException(PurchaseErrorCodes.STATEMENT_CURRENCY);
                d = c.line();
                amounts(d, RETURN.equals(l.lineType()) ? qty.negate() : qty);
                d.setRemark(PurSupport.trim(l.remark()));
                (RETURN.equals(l.lineType()) ? rets : goods).put(l.sourceLineId(), new Candidate(c.line(), c.currency(), BigDecimal.ZERO));
            }
            d.setStatementId(s.getId());
            d.setLineNo(no);
            d.setId(null);
            lineMapper.insert(d);
            switch (d.getLineType()) {
                case RETURN -> s.setReturnAmount(s.getReturnAmount().add(d.getTotalAmount()));
                case ADJUST -> s.setAdjustAmount(s.getAdjustAmount().add(d.getTotalAmount()));
                default -> s.setGoodsAmount(s.getGoodsAmount().add(d.getTotalAmount()));
            }
            s.setTaxAmount(s.getTaxAmount().add(d.getTaxAmount()));
        }
        s.setTotalAmount(s.getGoodsAmount().add(s.getReturnAmount()).add(s.getAdjustAmount()));
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        StatementDO s = getOrThrow(id);
        DataScopes.check(s.getOrgId(), s.getDeptId(), s.getOwnerId(), "对账单");
        PurSupport.requireDraft(s);
        lineMapper.deleteByParent(id);
        mapper.deleteById(id);
        fileApi.deleteByBiz(BIZ_TYPE, id);
    }

    @Transactional(rollbackFor = Exception.class)
    public void voidDoc(Long id, String reason) {
        StatementDO s = getOrThrow(id);
        DataScopes.check(s.getOrgId(), s.getDeptId(), s.getOwnerId(), "对账单");
        support.fire(PurStateMachines.STATEMENT, mapper, s, BIZ_TYPE, PurAction.VOID, PurSupport.trim(reason));
    }

    // ==================== 提交 / 审核 / 确认 ====================

    @Transactional(rollbackFor = Exception.class)
    public DocResult submit(Long id) {
        StatementDO s = getOrThrow(id);
        DataScopes.check(s.getOrgId(), s.getDeptId(), s.getOwnerId(), "对账单");
        PurSupport.requireDraft(s);
        if (lineMapper.selectByParent(id).isEmpty()) throw new BizException(PurchaseErrorCodes.DOC_NO_LINES);
        support.fire(PurStateMachines.STATEMENT, mapper, s, BIZ_TYPE, PurAction.SUBMIT, null);
        Map<String, Object> vars = new HashMap<>();
        vars.put("amountBase", currencyApi.toBase(s.getTotalAmount(), s.getExchangeRate()));
        StartResult r = workflowApi.start(BIZ_TYPE, id, s.getDocNo(), "供应商对账单 " + s.getDocNo(), vars, Map.of(), support.currentUser());
        if (!r.isStarted()) approve(s);
        return DocResult.of(s.getStatus().name());
    }

    @EventListener
    public void onApproval(ApprovalCompletedEvent e) {
        if (!BIZ_TYPE.equals(e.getBizType())) return;
        StatementDO s = getOrThrow(e.getBizId());
        if (s.getStatus() != DocStatus.PENDING_APPROVAL) return;
        switch (e.getResult()) {
            case APPROVED -> approve(s);
            case WITHDRAWN -> support.fire(PurStateMachines.STATEMENT, mapper, s, BIZ_TYPE, PurAction.WITHDRAW, null);
            default -> support.fire(PurStateMachines.STATEMENT, mapper, s, BIZ_TYPE, PurAction.REJECT, e.getComment());
        }
    }

    /** R03：审核回写已对账数量 */
    private void approve(StatementDO s) {
        support.fire(PurStateMachines.STATEMENT, mapper, s, BIZ_TYPE, PurAction.APPROVE, null);
        writeBack(lineMapper.selectByParent(s.getId()));
    }

    /** R06：反审核扣回已对账数量 */
    @Transactional(rollbackFor = Exception.class)
    public void unapprove(Long id, String reason) {
        StatementDO s = getOrThrow(id);
        DataScopes.check(s.getOrgId(), s.getDeptId(), s.getOwnerId(), "对账单");
        support.fire(PurStateMachines.STATEMENT, mapper, s, BIZ_TYPE, PurAction.UNAPPROVE, PurSupport.requireReason(reason, "反审核"));
        writeBack(lineMapper.selectByParent(id));
    }

    /** 按已审核、已确认的对账单重新汇总到货行、退货行的已对账数量，再汇总到订单行与委外单 */
    private void writeBack(List<StatementLineDO> lines) {
        List<Long> receiptLineIds = lines.stream().filter(l -> GOODS.equals(l.getLineType()) || PROCESS_FEE.equals(l.getLineType()))
                .map(StatementLineDO::getSourceLineId).filter(Objects::nonNull).distinct().toList();
        List<Long> returnLineIds = lines.stream().filter(l -> RETURN.equals(l.getLineType())).map(StatementLineDO::getSourceLineId)
                .filter(Objects::nonNull).distinct().toList();
        if (!receiptLineIds.isEmpty()) {
            Map<Long, BigDecimal> used = used(List.of(GOODS, PROCESS_FEE), APPROVED_STATUSES, receiptLineIds);
            List<ReceiptLineDO> rls = receiptLineMapper.selectBatchIds(receiptLineIds);
            for (ReceiptLineDO l : rls) {
                l.setStatementQty(Decimals.qty(used.getOrDefault(l.getId(), BigDecimal.ZERO)));
                receiptLineMapper.updateByIdOrFail(l);
            }
            orderService.refreshLines(rls.stream().map(ReceiptLineDO::getOrderLineId).filter(Objects::nonNull).toList());
            outsourcingService.refreshReceipts(rls.stream().filter(l -> l.getOrderLineId() == null).map(ReceiptLineDO::getOrderId).toList());
        }
        if (!returnLineIds.isEmpty()) {
            Map<Long, BigDecimal> used = used(List.of(RETURN), APPROVED_STATUSES, returnLineIds);
            for (ReturnLineDO l : returnLineMapper.selectBatchIds(returnLineIds)) {
                l.setStatementQty(Decimals.qty(used.getOrDefault(l.getId(), BigDecimal.ZERO).abs()));
                returnLineMapper.updateByIdOrFail(l);
            }
        }
    }

    /** R04：供应商确认：必须上传供应商签字的对账单；确认后发布事件（财务生成应付单） */
    @Transactional(rollbackFor = Exception.class)
    public void confirm(Long id, String confirmer, LocalDateTime confirmedAt, List<Long> fileIds) {
        StatementDO s = getOrThrow(id);
        DataScopes.check(s.getOrgId(), s.getDeptId(), s.getOwnerId(), "对账单");
        if (!StringUtils.hasText(confirmer)) throw new BizException(PurchaseErrorCodes.STATEMENT_CONFIRMER);
        if (fileIds != null && !fileIds.isEmpty()) fileApi.bind(fileIds, BIZ_TYPE, id);
        if (fileApi.list(BIZ_TYPE, id).isEmpty()) throw new BizException(PurchaseErrorCodes.STATEMENT_ATTACHMENT);
        s.setSupplierConfirmer(confirmer.trim());
        s.setSupplierConfirmedAt(confirmedAt != null ? confirmedAt : LocalDateTime.now().withNano(0));
        support.fire(PurStateMachines.STATEMENT, mapper, s, BIZ_TYPE, PurAction.CONFIRM, "供应商确认人：" + s.getSupplierConfirmer());
        List<StatementLineDO> lines = lineMapper.selectByParent(id);
        eventPublisher.publish(new PurchaseStatementConfirmedEvent(s.getId(), s.getDocNo(), s.getSupplierId(), s.getCurrency(), s.getPeriodFrom(),
                s.getPeriodTo(), s.getTotalAmount(), s.getTaxAmount(), lines.stream().map(l -> new PurchaseStatementConfirmedEvent.Line(l.getId(),
                l.getLineType(), l.getSourceType(), l.getSourceId(), l.getSourceLineId(), l.getSourceNo(), l.getOrderNo(), l.getMaterialId(), l.getBizDate(),
                l.getQty(), l.getPriceInclTax(), l.getTaxRate(), l.getAmount(), l.getTaxAmount(), l.getTotalAmount())).toList()));
    }

    /** R05：取消确认（财务已生成应付单时由财务阻止） */
    @Transactional(rollbackFor = Exception.class)
    public void unconfirm(Long id, String reason) {
        StatementDO s = getOrThrow(id);
        DataScopes.check(s.getOrgId(), s.getDeptId(), s.getOwnerId(), "对账单");
        String why = PurSupport.requireReason(reason, "取消确认");
        if (s.getStatus() == DocStatus.COMPLETED) eventPublisher.publish(new PurchaseStatementUnconfirmingEvent(s.getId(), s.getDocNo()));
        s.setSupplierConfirmer(null);
        s.setSupplierConfirmedAt(null);
        support.fire(PurStateMachines.STATEMENT, mapper, s, BIZ_TYPE, PurAction.UNCONFIRM, why);
    }

    // ==================== 批量生成 ====================

    /** 为区间内有可对账数据的所有供应商各生成一张草稿（按币别拆分） */
    @Transactional(rollbackFor = Exception.class)
    public List<Long> batchGenerate(LocalDate from, LocalDate to) {
        if (from == null || to == null || from.isAfter(to)) throw new BizException(PurchaseErrorCodes.STATEMENT_PERIOD);
        Map<String, List<Candidate>> groups = new LinkedHashMap<>();
        Map<String, Long> supplierOf = new HashMap<>();
        Set<Long> suppliers = new LinkedHashSet<>();
        receiptMapper.selectList(new LambdaQueryWrapper<ReceiptDO>().in(ReceiptDO::getStatus, DocStatus.APPROVED, DocStatus.COMPLETED))
                .forEach(r -> suppliers.add(r.getSupplierId()));
        returnMapper.selectList(new LambdaQueryWrapper<ReturnDO>().eq(ReturnDO::getStatus, DocStatus.COMPLETED)).forEach(r -> suppliers.add(r.getSupplierId()));
        for (Long sid : suppliers) {
            for (Candidate c : buildCandidates(sid, from, to)) {
                String key = sid + "|" + c.currency();
                groups.computeIfAbsent(key, k -> new ArrayList<>()).add(c);
                supplierOf.put(key, sid);
            }
        }
        List<Long> ids = new ArrayList<>();
        for (Map.Entry<String, List<Candidate>> e : groups.entrySet()) {
            String currency = e.getKey().substring(e.getKey().indexOf('|') + 1);
            List<LineSave> lines = e.getValue().stream().map(c -> new LineSave(c.line().getLineType(), c.line().getSourceType(), c.line().getSourceLineId(),
                    c.remaining().abs(), null, null, null, null)).toList();
            ids.add(create(new StatementSave(supplierOf.get(e.getKey()), currency, from, to, null, lines,
                    null, null)));
        }
        return ids;
    }

    // ==================== 打印 ====================

    public Map<String, Object> printData(Long id) {
        StatementDetail d = detail(id);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("docNo", d.docNo());
        data.put("status", d.status());
        data.put("supplierName", d.supplierName());
        data.put("periodFrom", d.periodFrom());
        data.put("periodTo", d.periodTo());
        data.put("currency", d.currency());
        data.put("goodsAmount", d.goodsAmount());
        data.put("returnAmount", d.returnAmount());
        data.put("adjustAmount", d.adjustAmount());
        data.put("totalAmount", d.totalAmount());
        data.put("taxAmount", d.taxAmount());
        data.put("lines", d.lines().stream().map(l -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("lineTypeName", TYPE_NAMES.get(l.lineType()));
            m.put("sourceNo", Objects.toString(l.sourceNo(), ""));
            m.put("orderNo", Objects.toString(l.orderNo(), ""));
            m.put("materialCode", Objects.toString(l.materialCode(), ""));
            m.put("materialName", Objects.toString(l.materialName(), ""));
            m.put("bizDate", l.bizDate());
            m.put("qty", l.qty());
            m.put("priceInclTax", l.priceInclTax());
            m.put("totalAmount", l.totalAmount());
            m.put("remark", Objects.toString(l.remark(), ""));
            return m;
        }).toList());
        return data;
    }

    public StatementDO getOrThrow(Long id) {
        StatementDO s = id == null ? null : mapper.selectById(id);
        if (s == null) throw new BizException(PurchaseErrorCodes.STATEMENT_NOT_EXISTS);
        return s;
    }
}
