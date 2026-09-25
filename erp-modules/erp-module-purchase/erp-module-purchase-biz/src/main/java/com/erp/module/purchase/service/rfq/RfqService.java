package com.erp.module.purchase.service.rfq;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.common.exception.BizException;
import com.erp.common.exception.GlobalErrorCodes;
import com.erp.common.result.PageResult;
import com.erp.common.statemachine.StateMachine;
import com.erp.common.util.Decimals;
import com.erp.framework.datascope.DataScopes;
import com.erp.module.engineering.api.material.MaterialDTO;
import com.erp.module.purchase.api.PurchaseErrorCodes;
import com.erp.module.purchase.config.PurchaseModuleConfig;
import com.erp.module.purchase.controller.vo.PriceVOs.AdjustLineSave;
import com.erp.module.purchase.controller.vo.PriceVOs.AdjustSave;
import com.erp.module.purchase.controller.vo.RfqVOs.AwardLine;
import com.erp.module.purchase.controller.vo.RfqVOs.AwardSupplier;
import com.erp.module.purchase.controller.vo.RfqVOs.QuoteCell;
import com.erp.module.purchase.controller.vo.RfqVOs.QuoteMatrix;
import com.erp.module.purchase.controller.vo.RfqVOs.QuoteRow;
import com.erp.module.purchase.controller.vo.RfqVOs.QuoteSave;
import com.erp.module.purchase.controller.vo.RfqVOs.QuotesResult;
import com.erp.module.purchase.controller.vo.RfqVOs.RfqDetail;
import com.erp.module.purchase.controller.vo.RfqVOs.RfqLineResp;
import com.erp.module.purchase.controller.vo.RfqVOs.RfqLineSave;
import com.erp.module.purchase.controller.vo.RfqVOs.RfqQuery;
import com.erp.module.purchase.controller.vo.RfqVOs.RfqRow;
import com.erp.module.purchase.controller.vo.RfqVOs.RfqSave;
import com.erp.module.purchase.controller.vo.RfqVOs.RfqSupplierResp;
import com.erp.module.purchase.controller.vo.RfqVOs.SupplierTotal;
import com.erp.module.purchase.controller.vo.SupplierVOs.SupplierMaterialResp;
import com.erp.module.purchase.dal.dataobject.PriceAdjustDO;
import com.erp.module.purchase.dal.dataobject.PriceDO;
import com.erp.module.purchase.dal.dataobject.RfqDO;
import com.erp.module.purchase.dal.dataobject.RfqLineDO;
import com.erp.module.purchase.dal.dataobject.RfqQuoteDO;
import com.erp.module.purchase.dal.dataobject.RfqSupplierDO;
import com.erp.module.purchase.dal.dataobject.SupplierDO;
import com.erp.module.purchase.dal.mapper.PriceAdjustMapper;
import com.erp.module.purchase.dal.mapper.RfqLineMapper;
import com.erp.module.purchase.dal.mapper.RfqMapper;
import com.erp.module.purchase.dal.mapper.RfqQuoteMapper;
import com.erp.module.purchase.dal.mapper.RfqSupplierMapper;
import com.erp.module.purchase.service.PurSupport;
import com.erp.module.purchase.service.price.PriceService;
import com.erp.module.purchase.service.supplier.SupplierService;
import com.erp.module.system.api.currency.CurrencyApi;
import com.erp.module.system.api.file.FileApi;
import com.erp.module.system.api.user.UserDTO;
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

import static com.erp.module.purchase.service.rfq.RfqStatus.AWARDED;
import static com.erp.module.purchase.service.rfq.RfqStatus.CANCELED;
import static com.erp.module.purchase.service.rfq.RfqStatus.COMPARING;
import static com.erp.module.purchase.service.rfq.RfqStatus.DRAFT;
import static com.erp.module.purchase.service.rfq.RfqStatus.QUOTING;

/**
 * 询价比价（需求 07-04）：草稿 →（发出）报价中 →（结束报价）比价中 →（定标）已定标；未定标前可取消。
 * 定标后按中标供应商分组，每家生成一张草稿调价单（来源 RFQ，新价格 = 报价）。
 */
@Service
public class RfqService {

    public static final String BIZ_TYPE = PurchaseModuleConfig.RFQ;
    static final BigDecimal HUNDRED = PurSupport.HUNDRED;

    enum Action implements StateMachine.Labeled {
        SEND("发出询价"), END_QUOTE("结束报价"), AWARD("定标"), CANCEL("取消");

        private final String label;

        Action(String label) {
            this.label = label;
        }

        @Override
        public String label() {
            return label;
        }
    }

    static final StateMachine<RfqStatus, Action> MACHINE = StateMachine.builder(RfqStatus.class, Action.class)
            .transition(DRAFT, Action.SEND, QUOTING)
            .transition(QUOTING, Action.END_QUOTE, COMPARING)
            .transition(COMPARING, Action.AWARD, AWARDED)
            .transition(DRAFT, Action.CANCEL, CANCELED)
            .transition(QUOTING, Action.CANCEL, CANCELED)
            .transition(COMPARING, Action.CANCEL, CANCELED)
            .build();

    private final RfqMapper mapper;
    private final RfqLineMapper lineMapper;
    private final RfqSupplierMapper supplierMapper;
    private final RfqQuoteMapper quoteMapper;
    private final PriceAdjustMapper adjustMapper;
    private final SupplierService supplierService;
    private final PriceService priceService;
    private final PurSupport support;
    private final CurrencyApi currencyApi;
    private final FileApi fileApi;

    public RfqService(RfqMapper mapper, RfqLineMapper lineMapper, RfqSupplierMapper supplierMapper, RfqQuoteMapper quoteMapper,
                      PriceAdjustMapper adjustMapper, SupplierService supplierService, PriceService priceService, PurSupport support,
                      CurrencyApi currencyApi, FileApi fileApi) {
        this.mapper = mapper;
        this.lineMapper = lineMapper;
        this.supplierMapper = supplierMapper;
        this.quoteMapper = quoteMapper;
        this.adjustMapper = adjustMapper;
        this.supplierService = supplierService;
        this.priceService = priceService;
        this.support = support;
        this.currencyApi = currencyApi;
        this.fileApi = fileApi;
    }

    // ==================== 查询 ====================

    public PageResult<RfqRow> page(RfqQuery q) {
        LambdaQueryWrapper<RfqDO> w = new LambdaQueryWrapper<RfqDO>().eq(RfqDO::getDeleted, false)
                .likeRight(StringUtils.hasText(q.getDocNo()), RfqDO::getDocNo, q.getDocNo() == null ? null : q.getDocNo().trim().toUpperCase())
                .like(StringUtils.hasText(q.getTitle()), RfqDO::getTitle, q.getTitle())
                .ge(q.getDeadlineFrom() != null, RfqDO::getQuoteDeadline, q.getDeadlineFrom())
                .le(q.getDeadlineTo() != null, RfqDO::getQuoteDeadline, q.getDeadlineTo());
        if (StringUtils.hasText(q.getStatuses())) {
            w.in(RfqDO::getRfqStatus, Arrays.stream(q.getStatuses().split(",")).map(String::trim).map(RfqStatus::valueOf).toList());
        }
        if (q.getMaterialId() != null) w.inSql(RfqDO::getId, "SELECT rfq_id FROM pur_rfq_line WHERE deleted = 0 AND material_id = " + q.getMaterialId().longValue());
        if (q.getSupplierId() != null) w.inSql(RfqDO::getId, "SELECT rfq_id FROM pur_rfq_supplier WHERE deleted = 0 AND supplier_id = " + q.getSupplierId().longValue());
        w.orderByDesc(RfqDO::getDocDate).orderByDesc(RfqDO::getId);
        IPage<RfqDO> page = mapper.selectScopedPage(Page.of(q.getPageNo(), q.getPageSize()), w);
        List<Long> ids = page.getRecords().stream().map(RfqDO::getId).toList();
        Map<Long, Long> lineCounts = lineMapper.selectByParents(ids).stream().collect(Collectors.groupingBy(RfqLineDO::getRfqId, Collectors.counting()));
        Map<Long, List<RfqSupplierDO>> sups = supplierMapper.selectByParents(ids).stream().collect(Collectors.groupingBy(RfqSupplierDO::getRfqId));
        Map<Long, UserDTO> users = support.users(page.getRecords().stream().map(RfqDO::getOwnerId).toList());
        LocalDate today = LocalDate.now();
        return new PageResult<>(page.getRecords().stream().map(r -> {
            List<RfqSupplierDO> ss = sups.getOrDefault(r.getId(), List.of());
            boolean open = r.getRfqStatus() == QUOTING || r.getRfqStatus() == COMPARING;
            return new RfqRow(r.getId(), r.getDocNo(), r.getTitle(), r.getCurrency(), lineCounts.getOrDefault(r.getId(), 0L).intValue(), ss.size(),
                    (int) ss.stream().filter(x -> Boolean.TRUE.equals(x.getQuoted())).count(), r.getQuoteDeadline(),
                    open && r.getQuoteDeadline().isBefore(today), r.getRfqStatus().name(), PurSupport.name(users, r.getOwnerId()), r.getDocDate());
        }).toList(), page.getTotal());
    }

    public RfqDetail detail(Long id) {
        RfqDO r = getOrThrow(id);
        DataScopes.check(r.getOrgId(), r.getDeptId(), r.getOwnerId(), "询价单");
        List<RfqLineDO> lines = lineMapper.selectByParent(id);
        Map<Long, MaterialDTO> ms = support.materials(lines.stream().map(RfqLineDO::getMaterialId).toList());
        List<Long> adjustIds = adjustMapper.selectList(new LambdaQueryWrapper<PriceAdjustDO>().eq(PriceAdjustDO::getRfqId, id)).stream()
                .map(PriceAdjustDO::getId).toList();
        return new RfqDetail(r.getId(), r.getDocNo(), r.getDocDate(), r.getRfqStatus().name(), r.getTitle(), r.getCurrency(), r.getQuoteDeadline(),
                r.getCancelReason(), r.getRemark(), r.getOwnerId(), support.userName(r.getOwnerId()), r.getCreatedAt(), r.getVersion(),
                lines.stream().map(l -> {
                    MaterialDTO m = ms.get(l.getMaterialId());
                    return new RfqLineResp(l.getId(), l.getLineNo(), l.getMaterialId(), m == null ? null : m.code(), m == null ? null : m.name(),
                            m == null ? null : m.spec(), m == null ? null : m.baseUom(), l.getQty(), l.getRequiredDate(), l.getRemark());
                }).toList(), supplierResps(id), adjustIds);
    }

    private List<RfqSupplierResp> supplierResps(Long rfqId) {
        List<RfqSupplierDO> list = supplierMapper.selectByParent(rfqId);
        Map<Long, SupplierDO> ss = supplierService.byIds(list.stream().map(RfqSupplierDO::getSupplierId).toList());
        return list.stream().map(x -> {
            SupplierDO s = ss.get(x.getSupplierId());
            return new RfqSupplierResp(x.getId(), x.getSupplierId(), s == null ? null : s.getCode(), s == null ? null : s.getShortName(),
                    s == null ? null : s.getSupplierStatus().name(), x.getSentAt(), Boolean.TRUE.equals(x.getQuoted()));
        }).toList();
    }

    /** 默认带出这些物料的可供供应商（非暂停/淘汰、非停用） */
    public List<SupplierMaterialResp> defaultSuppliers(List<Long> materialIds) {
        Map<Long, SupplierMaterialResp> map = new LinkedHashMap<>();
        for (Long mid : materialIds == null ? List.<Long>of() : materialIds) {
            for (SupplierMaterialResp r : supplierService.suppliersOfMaterial(mid)) {
                if ("DISABLED".equals(r.supplyStatus()) || "SUSPENDED".equals(r.supplierStatus()) || "ELIMINATED".equals(r.supplierStatus())) continue;
                map.putIfAbsent(r.supplierId(), r);
            }
        }
        return new ArrayList<>(map.values());
    }

    // ==================== 编辑 ====================

    @Transactional(rollbackFor = Exception.class)
    public Long create(RfqSave req) {
        RfqDO r = new RfqDO();
        r.setDocNo(support.nextNo(BIZ_TYPE));
        r.setDocDate(LocalDate.now());
        r.setRfqStatus(DRAFT);
        r.setStatus(DRAFT.docStatus());
        support.fillOwner(r, null);
        fill(r, req);
        mapper.insert(r);
        saveChildren(r, req);
        if (req.fileIds() != null && !req.fileIds().isEmpty()) fileApi.bind(req.fileIds(), BIZ_TYPE, r.getId());
        return r.getId();
    }

    /** R04：已定标的询价单不能修改；草稿可修改全部内容 */
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, RfqSave req) {
        RfqDO r = getOrThrow(id);
        DataScopes.check(r.getOrgId(), r.getDeptId(), r.getOwnerId(), "询价单");
        if (r.getRfqStatus() != DRAFT) throw new BizException(PurchaseErrorCodes.DOC_NOT_EDITABLE);
        if (req.version() != null) r.setVersion(req.version());
        fill(r, req);
        mapper.updateByIdOrFail(r);
        saveChildren(r, req);
        if (req.fileIds() != null && !req.fileIds().isEmpty()) fileApi.bind(req.fileIds(), BIZ_TYPE, r.getId());
    }

    private void fill(RfqDO r, RfqSave req) {
        String cur = StringUtils.hasText(req.currency()) ? req.currency().trim().toUpperCase() : currencyApi.getBaseCurrency();
        currencyApi.validate(cur);
        r.setTitle(req.title().trim());
        r.setCurrency(cur);
        r.setQuoteDeadline(req.quoteDeadline() != null ? req.quoteDeadline() : LocalDate.now().plusDays(7));
        if (r.getQuoteDeadline().isBefore(LocalDate.now())) throw new BizException(PurchaseErrorCodes.RFQ_DEADLINE_PAST);
        r.setRemark(PurSupport.trim(req.remark()));
    }

    private void saveChildren(RfqDO r, RfqSave req) {
        lineMapper.deleteByParent(r.getId());
        quoteMapper.deleteByParent(r.getId());
        Set<Long> seen = new HashSet<>();
        Map<Long, MaterialDTO> ms = support.materials(req.lines() == null ? List.of() : req.lines().stream().map(RfqLineSave::materialId).toList());
        int no = 0;
        for (RfqLineSave l : req.lines() == null ? List.<RfqLineSave>of() : req.lines()) {
            no++;
            MaterialDTO m = ms.get(l.materialId());
            if (m == null) throw BizException.of(GlobalErrorCodes.DATA_NOT_EXISTS, "物料");
            if (!seen.add(m.id())) throw BizException.of(PurchaseErrorCodes.RFQ_DUPLICATE, "物料", m.code());
            if (l.qty() == null || l.qty().signum() <= 0) throw BizException.of(PurchaseErrorCodes.LINE_QTY_POSITIVE, no);
            RfqLineDO d = new RfqLineDO();
            d.setRfqId(r.getId());
            d.setLineNo(no);
            d.setMaterialId(m.id());
            d.setQty(Decimals.qty(l.qty()));
            d.setRequiredDate(l.requiredDate());
            d.setRemark(PurSupport.trim(l.remark()));
            lineMapper.insert(d);
        }
        supplierMapper.deleteByParent(r.getId());
        Set<Long> sup = new HashSet<>();
        for (Long sid : req.supplierIds() == null ? List.<Long>of() : req.supplierIds()) {
            SupplierDO s = supplierService.getOrThrow(sid);
            if (!sup.add(sid)) throw BizException.of(PurchaseErrorCodes.RFQ_DUPLICATE, "供应商", s.getShortName());
            RfqSupplierDO d = new RfqSupplierDO();
            d.setRfqId(r.getId());
            d.setSupplierId(sid);
            d.setQuoted(false);
            supplierMapper.insert(d);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        RfqDO r = getOrThrow(id);
        DataScopes.check(r.getOrgId(), r.getDeptId(), r.getOwnerId(), "询价单");
        if (r.getRfqStatus() != DRAFT) throw new BizException(PurchaseErrorCodes.DOC_NOT_EDITABLE);
        lineMapper.deleteByParent(id);
        supplierMapper.deleteByParent(id);
        quoteMapper.deleteByParent(id);
        mapper.deleteById(id);
        fileApi.deleteByBiz(BIZ_TYPE, id);
    }

    // ==================== 状态 ====================

    /** R01：至少 1 个物料、1 家供应商；供应商不能是暂停/淘汰状态 */
    @Transactional(rollbackFor = Exception.class)
    public void send(Long id) {
        RfqDO r = getOrThrow(id);
        DataScopes.check(r.getOrgId(), r.getDeptId(), r.getOwnerId(), "询价单");
        List<RfqSupplierDO> sups = supplierMapper.selectByParent(id);
        if (lineMapper.selectByParent(id).isEmpty() || sups.isEmpty()) throw new BizException(PurchaseErrorCodes.RFQ_NEED_LINES);
        if (r.getQuoteDeadline().isBefore(LocalDate.now())) throw new BizException(PurchaseErrorCodes.RFQ_DEADLINE_PAST);
        for (RfqSupplierDO x : sups) {
            supplierService.validateActive(x.getSupplierId());
            x.setSentAt(LocalDateTime.now().withNano(0));
            supplierMapper.updateByIdOrFail(x);
        }
        fire(r, Action.SEND, null);
    }

    @Transactional(rollbackFor = Exception.class)
    public void endQuote(Long id) {
        RfqDO r = getOrThrow(id);
        DataScopes.check(r.getOrgId(), r.getDeptId(), r.getOwnerId(), "询价单");
        fire(r, Action.END_QUOTE, null);
    }

    /** R04：未定标时可取消，原因必填 */
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id, String reason) {
        RfqDO r = getOrThrow(id);
        DataScopes.check(r.getOrgId(), r.getDeptId(), r.getOwnerId(), "询价单");
        String why = PurSupport.requireReason(reason, "取消");
        r.setCancelReason(why);
        fire(r, Action.CANCEL, why);
    }

    private void fire(RfqDO r, Action action, String reason) {
        RfqStatus from = r.getRfqStatus();
        r.setRfqStatus(MACHINE.fire(from, action));
        r.setStatus(r.getRfqStatus().docStatus());
        mapper.updateByIdOrFail(r);
        support.log(BIZ_TYPE, r.getId(), r.getDocNo(), action.name(), action.label(), from.name(), r.getRfqStatus().name(), reason);
    }

    // ==================== 报价与比价 ====================

    /** 比价矩阵：行 = 物料，列 = 供应商；每行最低价标记，并与当前有效价比较 */
    public QuoteMatrix matrix(Long id) {
        RfqDO r = getOrThrow(id);
        DataScopes.check(r.getOrgId(), r.getDeptId(), r.getOwnerId(), "询价单");
        List<RfqLineDO> lines = lineMapper.selectByParent(id);
        List<RfqSupplierResp> sups = supplierResps(id);
        Map<String, RfqQuoteDO> quotes = quoteMapper.selectByParent(id).stream().collect(Collectors.toMap(q -> q.getRfqLineId() + "|" + q.getSupplierId(), q -> q));
        Map<Long, MaterialDTO> ms = support.materials(lines.stream().map(RfqLineDO::getMaterialId).toList());
        Map<Long, BigDecimal> totals = new LinkedHashMap<>();
        Map<Long, Integer> counts = new HashMap<>();
        List<QuoteRow> rows = new ArrayList<>();
        for (RfqLineDO l : lines) {
            List<RfqQuoteDO> qs = sups.stream().map(s -> quotes.get(l.getId() + "|" + s.supplierId())).filter(Objects::nonNull).toList();
            BigDecimal lowest = qs.stream().map(RfqQuoteDO::getPrice).min(Comparator.naturalOrder()).orElse(null);
            BigDecimal current = sups.stream().map(s -> priceService.effective(s.supplierId(), l.getMaterialId(), l.getQty(), LocalDate.now(), r.getCurrency())
                    .map(PriceDO::getPrice).orElse(null)).filter(Objects::nonNull).min(Comparator.naturalOrder()).orElse(null);
            List<QuoteCell> cells = new ArrayList<>();
            for (RfqSupplierResp s : sups) {
                RfqQuoteDO q = quotes.get(l.getId() + "|" + s.supplierId());
                if (q == null) {
                    cells.add(new QuoteCell(s.supplierId(), null, null, null, null, null, false, null, null, false));
                    continue;
                }
                totals.merge(s.supplierId(), Decimals.multiplyAmount(l.getQty(), q.getPrice()), BigDecimal::add);
                counts.merge(s.supplierId(), 1, Integer::sum);
                cells.add(new QuoteCell(s.supplierId(), q.getPrice(), q.getTaxRate(), q.getMoq(), q.getLeadTimeDays(), q.getValidUntil(),
                        Boolean.TRUE.equals(q.getIsAwarded()), q.getAwardQtyPct(), q.getRemark(), lowest != null && q.getPrice().compareTo(lowest) == 0));
            }
            MaterialDTO m = ms.get(l.getMaterialId());
            BigDecimal diff = lowest == null || current == null || current.signum() == 0 ? null
                    : lowest.subtract(current).divide(current, 4, RoundingMode.HALF_UP);
            rows.add(new QuoteRow(l.getId(), l.getMaterialId(), m == null ? null : m.code(), m == null ? null : m.name(), m == null ? null : m.baseUom(),
                    l.getQty(), current, lowest, diff, cells));
        }
        List<SupplierTotal> st = sups.stream().map(s -> new SupplierTotal(s.supplierId(), s.supplierName(), totals.get(s.supplierId()),
                counts.getOrDefault(s.supplierId(), 0) == lines.size() && !lines.isEmpty())).toList();
        return new QuoteMatrix(r.getId(), r.getRfqStatus().name(), r.getCurrency(), r.getQuoteDeadline(), r.getQuoteDeadline().isBefore(LocalDate.now()),
                sups, rows, st);
    }

    /** 批量保存报价（报价中）；单价为空表示删除该报价。R03：截止日期后仍可录入，给出提示 */
    @Transactional(rollbackFor = Exception.class)
    public QuotesResult saveQuotes(Long id, List<QuoteSave> list) {
        RfqDO r = getOrThrow(id);
        DataScopes.check(r.getOrgId(), r.getDeptId(), r.getOwnerId(), "询价单");
        if (r.getRfqStatus() != QUOTING) throw BizException.of(GlobalErrorCodes.ILLEGAL_STATE_TRANSITION, r.getRfqStatus().label(), "录入报价");
        Set<Long> lineIds = lineMapper.selectByParent(id).stream().map(RfqLineDO::getId).collect(Collectors.toSet());
        Map<Long, RfqSupplierDO> sups = supplierMapper.selectByParent(id).stream().collect(Collectors.toMap(RfqSupplierDO::getSupplierId, x -> x));
        int saved = 0;
        for (QuoteSave q : list == null ? List.<QuoteSave>of() : list) {
            if (!lineIds.contains(q.rfqLineId()) || !sups.containsKey(q.supplierId())) throw BizException.of(GlobalErrorCodes.DATA_NOT_EXISTS, "询价物料或供应商");
            RfqQuoteDO d = quoteMapper.selectOne(new LambdaQueryWrapper<RfqQuoteDO>().eq(RfqQuoteDO::getRfqLineId, q.rfqLineId())
                    .eq(RfqQuoteDO::getSupplierId, q.supplierId()));
            if (q.price() == null) {
                if (d != null) quoteMapper.deleteById(d.getId());
                continue;
            }
            if (q.price().signum() <= 0) throw BizException.of(GlobalErrorCodes.BAD_REQUEST, "报价单价必须大于 0");
            boolean creating = d == null;
            if (creating) {
                d = new RfqQuoteDO();
                d.setRfqId(id);
                d.setRfqLineId(q.rfqLineId());
                d.setSupplierId(q.supplierId());
                d.setIsAwarded(false);
            }
            d.setPrice(Decimals.price(q.price()));
            d.setTaxRate(q.taxRate() != null ? q.taxRate() : supplierService.getOrThrow(q.supplierId()).getPurchaseTaxRate());
            d.setMoq(q.moq());
            d.setLeadTimeDays(q.leadTimeDays());
            d.setValidUntil(q.validUntil());
            d.setRemark(PurSupport.trim(q.remark()));
            if (creating) quoteMapper.insert(d);
            else quoteMapper.updateByIdOrFail(d);
            saved++;
        }
        Set<Long> quoted = quoteMapper.selectByParent(id).stream().map(RfqQuoteDO::getSupplierId).collect(Collectors.toSet());
        for (RfqSupplierDO x : sups.values()) {
            boolean q = quoted.contains(x.getSupplierId());
            if (q != Boolean.TRUE.equals(x.getQuoted())) {
                x.setQuoted(q);
                supplierMapper.updateByIdOrFail(x);
            }
        }
        List<String> warnings = r.getQuoteDeadline().isBefore(LocalDate.now()) ? List.of("已过截止日期") : List.of();
        return new QuotesResult(saved, warnings);
    }

    /**
     * 定标（R02）：每个物料至少一家中标，多家时份额合计 100%；按中标供应商分组，每家生成一张草稿调价单。
     */
    @Transactional(rollbackFor = Exception.class)
    public List<Long> award(Long id, List<AwardLine> awards) {
        RfqDO r = getOrThrow(id);
        DataScopes.check(r.getOrgId(), r.getDeptId(), r.getOwnerId(), "询价单");
        if (!MACHINE.canFire(r.getRfqStatus(), Action.AWARD)) MACHINE.fire(r.getRfqStatus(), Action.AWARD);
        List<RfqLineDO> lines = lineMapper.selectByParent(id);
        Map<Long, AwardLine> byLine = new HashMap<>();
        if (awards != null) awards.forEach(a -> byLine.put(a.rfqLineId(), a));
        Map<Long, MaterialDTO> ms = support.materials(lines.stream().map(RfqLineDO::getMaterialId).toList());
        Map<String, RfqQuoteDO> quotes = quoteMapper.selectByParent(id).stream().collect(Collectors.toMap(q -> q.getRfqLineId() + "|" + q.getSupplierId(), q -> q));
        Map<Long, List<AdjustLineSave>> bySupplier = new LinkedHashMap<>();
        for (RfqLineDO l : lines) {
            String code = ms.containsKey(l.getMaterialId()) ? ms.get(l.getMaterialId()).code() : "";
            AwardLine a = byLine.get(l.getId());
            List<AwardSupplier> winners = a == null || a.awards() == null ? List.of() : a.awards();
            if (winners.isEmpty()) throw BizException.of(PurchaseErrorCodes.RFQ_AWARD_REQUIRED, code);
            BigDecimal sum = winners.size() == 1 && winners.get(0).pct() == null ? HUNDRED
                    : winners.stream().map(w -> w.pct() == null ? BigDecimal.ZERO : w.pct()).reduce(BigDecimal.ZERO, BigDecimal::add);
            if (sum.compareTo(HUNDRED) != 0) throw BizException.of(PurchaseErrorCodes.RFQ_AWARD_PCT, code);
            for (AwardSupplier w : winners) {
                RfqQuoteDO q = quotes.get(l.getId() + "|" + w.supplierId());
                if (q == null) {
                    SupplierDO s = supplierService.getOrThrow(w.supplierId());
                    throw BizException.of(PurchaseErrorCodes.RFQ_QUOTE_MISSING, s.getShortName(), code);
                }
                q.setIsAwarded(true);
                q.setAwardQtyPct(winners.size() == 1 && w.pct() == null ? HUNDRED : w.pct());
                quoteMapper.updateByIdOrFail(q);
                bySupplier.computeIfAbsent(w.supplierId(), k -> new ArrayList<>()).add(new AdjustLineSave(l.getMaterialId(), BigDecimal.ZERO, q.getPrice(),
                        q.getTaxRate(), LocalDate.now(), q.getValidUntil(), "询价定标 " + r.getDocNo()));
            }
        }
        fire(r, Action.AWARD, null);
        List<Long> ids = new ArrayList<>();
        for (Map.Entry<Long, List<AdjustLineSave>> e : bySupplier.entrySet()) {
            ids.add(priceService.createAdjust(new AdjustSave(e.getKey(), r.getCurrency(), "询价定标 " + r.getDocNo() + " " + r.getTitle(), null,
                    e.getValue(), null, null), "RFQ", r.getId(), r.getDocNo()));
        }
        return ids;
    }

    public Map<String, Object> printData(Long id) {
        RfqDetail d = detail(id);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("docNo", d.docNo());
        data.put("title", d.title());
        data.put("currency", d.currency());
        data.put("quoteDeadline", d.quoteDeadline());
        data.put("status", d.status());
        data.put("remark", Objects.toString(d.remark(), ""));
        data.put("ownerName", Objects.toString(d.ownerName(), ""));
        data.put("lines", d.lines().stream().map(l -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("lineNo", l.lineNo());
            m.put("materialCode", l.materialCode());
            m.put("materialName", l.materialName());
            m.put("spec", Objects.toString(l.materialSpec(), ""));
            m.put("uom", l.baseUom());
            m.put("qty", l.qty());
            m.put("requiredDate", l.requiredDate());
            return m;
        }).toList());
        data.put("suppliers", d.suppliers().stream().map(RfqSupplierResp::supplierName).toList());
        return data;
    }

    public RfqDO getOrThrow(Long id) {
        RfqDO r = id == null ? null : mapper.selectById(id);
        if (r == null) throw new BizException(PurchaseErrorCodes.RFQ_NOT_EXISTS);
        return r;
    }
}
