package com.erp.module.finance.service.ap;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.common.exception.BizException;
import com.erp.common.result.PageResult;
import com.erp.common.util.Decimals;
import com.erp.module.engineering.api.material.MaterialDTO;
import com.erp.module.finance.api.FinanceErrorCodes;
import com.erp.module.finance.config.FinanceModuleConfig;
import com.erp.module.finance.controller.vo.ApVOs.PiDetail;
import com.erp.module.finance.controller.vo.ApVOs.PiLineReq;
import com.erp.module.finance.controller.vo.ApVOs.PiLineVO;
import com.erp.module.finance.controller.vo.ApVOs.PiQuery;
import com.erp.module.finance.controller.vo.ApVOs.PiRow;
import com.erp.module.finance.controller.vo.ApVOs.PiSave;
import com.erp.module.finance.controller.vo.ApVOs.UninvoicedApLine;
import com.erp.module.finance.dal.dataobject.FinPayableDO;
import com.erp.module.finance.dal.dataobject.FinPayableLineDO;
import com.erp.module.finance.dal.dataobject.FinPurchaseInvoiceDO;
import com.erp.module.finance.dal.dataobject.FinPurchaseInvoiceLineDO;
import com.erp.module.finance.dal.mapper.FinPayableLineMapper;
import com.erp.module.finance.dal.mapper.FinPayableMapper;
import com.erp.module.finance.dal.mapper.FinPurchaseInvoiceLineMapper;
import com.erp.module.finance.dal.mapper.FinPurchaseInvoiceMapper;
import com.erp.module.finance.service.ArStatus;
import com.erp.module.finance.service.FinSupport;
import com.erp.module.purchase.api.supplier.SupplierDTO;
import com.erp.module.system.api.user.UserDTO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 进项发票登记与三单匹配（12-04 第 3.3 节）：发票单价与应付单价比较（容差 %），明细合计与发票价税合计比较（金额容差内自动调整尾差）；
 * 有超容差行时为“有差异”，财务主管确认后生成价差调整应付行。
 */
@Service
public class PurchaseInvoiceService {

    static final String BIZ_TYPE = FinanceModuleConfig.PURCHASE_INVOICE;
    public static final String UNMATCHED = "UNMATCHED";
    public static final String MATCHED = "MATCHED";
    public static final String DIFF = "DIFF";
    public static final String REGISTERED = "REGISTERED";
    static final String VOIDED = "VOIDED";

    private final FinPurchaseInvoiceMapper mapper;
    private final FinPurchaseInvoiceLineMapper lineMapper;
    private final FinPayableMapper apMapper;
    private final FinPayableLineMapper apLineMapper;
    private final FinSupport support;

    public PurchaseInvoiceService(FinPurchaseInvoiceMapper mapper, FinPurchaseInvoiceLineMapper lineMapper, FinPayableMapper apMapper,
                                  FinPayableLineMapper apLineMapper, FinSupport support) {
        this.mapper = mapper;
        this.lineMapper = lineMapper;
        this.apMapper = apMapper;
        this.apLineMapper = apLineMapper;
        this.support = support;
    }

    /** 应付行不含税单价 = 不含税金额 ÷ 数量 */
    static BigDecimal apPrice(FinPayableLineDO l) {
        if (l.getQty() == null || l.getQty().signum() == 0) return null;
        return l.getAmount().divide(l.getQty(), Decimals.PRICE_SCALE, RoundingMode.HALF_UP);
    }

    static boolean hasQty(FinPayableLineDO l) {
        return l.getQty() != null && l.getQty().signum() != 0;
    }

    /** 可开票应付行：供应商已确认、未完全开票 */
    public List<UninvoicedApLine> uninvoiced(Long supplierId, String currency, List<Long> payableIds) {
        List<FinPayableDO> aps = apMapper.selectList(new LambdaQueryWrapper<FinPayableDO>().eq(FinPayableDO::getSupplierId, supplierId)
                .eq(StringUtils.hasText(currency), FinPayableDO::getCurrency, currency)
                .in(payableIds != null && !payableIds.isEmpty(), FinPayableDO::getId, payableIds)
                .eq(FinPayableDO::getApStatus, ArStatus.CONFIRMED.name()).orderByAsc(FinPayableDO::getBizDate).orderByAsc(FinPayableDO::getId));
        Map<Long, FinPayableDO> byId = aps.stream().collect(Collectors.toMap(FinPayableDO::getId, Function.identity()));
        List<FinPayableLineDO> lines = apLineMapper.selectByParents(byId.keySet()).stream()
                .filter(l -> !PayableService.PRICE_DIFF.equals(l.getLineType()) && FinSupport.nz(l.getInvoicedAmount()).compareTo(l.getTotalAmount()) != 0)
                .toList();
        Map<Long, MaterialDTO> ms = support.materials(lines.stream().map(FinPayableLineDO::getMaterialId).toList());
        List<UninvoicedApLine> result = new ArrayList<>();
        for (FinPayableDO p : aps) {
            for (FinPayableLineDO l : lines) {
                if (!l.getPayableId().equals(p.getId())) continue;
                MaterialDTO m = ms.get(l.getMaterialId());
                BigDecimal openQty = hasQty(l) ? Decimals.qty(l.getQty().subtract(FinSupport.nz(l.getInvoicedQty()))) : null;
                result.add(new UninvoicedApLine(p.getId(), p.getDocNo(), l.getId(), p.getStatementNo(), l.getSourceNo(), l.getOrderNo(), p.getBizDate(),
                        p.getCurrency(), l.getLineType(), l.getMaterialId(), m == null ? null : m.code(), m == null ? null : m.name(), l.getDescription(),
                        l.getQty(), l.getPriceInclTax(), l.getTaxRate(), apPrice(l), l.getTotalAmount(), l.getInvoicedQty(), openQty,
                        l.getTotalAmount().subtract(FinSupport.nz(l.getInvoicedAmount()))));
            }
        }
        return result;
    }

    // ==================== 登记与匹配 ====================

    /** FIN-AP-R03 发票号不重复；FIN-AP-R04 数量 ≤ 未开票数量、单价容差、金额尾差 */
    @Transactional(rollbackFor = Exception.class)
    public Long register(PiSave req) {
        String invoiceNo = req.invoiceNo().trim();
        if (mapper.selectCount(new LambdaQueryWrapper<FinPurchaseInvoiceDO>().eq(FinPurchaseInvoiceDO::getSupplierId, req.supplierId())
                .eq(FinPurchaseInvoiceDO::getInvoiceNo, invoiceNo).ne(FinPurchaseInvoiceDO::getInvoiceStatus, VOIDED)) > 0) {
            throw BizException.of(FinanceErrorCodes.AP_INVOICE_DUPLICATE, invoiceNo);
        }
        support.supplier(req.supplierId());
        List<PiLineReq> reqLines = req.lines() == null ? List.of() : req.lines();
        if (reqLines.isEmpty()) throw new BizException(FinanceErrorCodes.NO_LINES);
        Map<Long, FinPayableLineDO> apLines = apLineMapper.selectBatchIds(reqLines.stream().map(PiLineReq::payableLineId).toList()).stream()
                .collect(Collectors.toMap(FinPayableLineDO::getId, Function.identity()));
        Map<Long, FinPayableDO> aps = apLines.isEmpty() ? Map.of() : apMapper.selectBatchIds(apLines.values().stream().map(FinPayableLineDO::getPayableId)
                .distinct().toList()).stream().collect(Collectors.toMap(FinPayableDO::getId, Function.identity()));
        Set<String> currencies = new HashSet<>();
        for (FinPayableDO p : aps.values()) {
            if (!p.getSupplierId().equals(req.supplierId())) throw BizException.of(FinanceErrorCodes.PAY_SUPPLIER_MISMATCH, p.getDocNo());
            if (!ArStatus.CONFIRMED.name().equals(p.getApStatus())) throw BizException.of(FinanceErrorCodes.STATUS_NOT_ALLOWED, ArStatus.valueOf(p.getApStatus()).label(), "登记发票");
            currencies.add(p.getCurrency());
        }
        if (currencies.size() != 1) throw BizException.of(FinanceErrorCodes.PAY_SUPPLIER_MISMATCH, "");
        BigDecimal tolerancePct = FinSupport.nz(support.params().getDecimal(FinanceModuleConfig.P_PRICE_TOLERANCE));
        BigDecimal amountTolerance = FinSupport.nz(support.params().getDecimal(FinanceModuleConfig.P_AMOUNT_TOLERANCE));

        List<FinPurchaseInvoiceLineDO> lines = new ArrayList<>();
        int no = 1;
        for (PiLineReq lr : reqLines) {
            FinPayableLineDO l = apLines.get(lr.payableLineId());
            if (l == null || !aps.containsKey(l.getPayableId())) throw BizException.of(FinanceErrorCodes.NOT_EXISTS, "应付行");
            FinPurchaseInvoiceLineDO x = new FinPurchaseInvoiceLineDO();
            x.setLineNo(no);
            x.setPayableId(l.getPayableId());
            x.setPayableLineId(l.getId());
            x.setMaterialId(l.getMaterialId());
            BigDecimal openAmount = l.getTotalAmount().subtract(FinSupport.nz(l.getInvoicedAmount()));
            if (hasQty(l)) {
                BigDecimal openQty = l.getQty().subtract(FinSupport.nz(l.getInvoicedQty()));
                BigDecimal qty = Decimals.qty(lr.qty() == null ? openQty : lr.qty());
                if (qty.signum() == 0 || qty.signum() != openQty.signum() || qty.abs().compareTo(openQty.abs()) > 0) {
                    throw BizException.of(FinanceErrorCodes.AP_INVOICE_QTY_EXCEED, no, Decimals.qty(openQty).stripTrailingZeros().toPlainString());
                }
                BigDecimal ap = apPrice(l);
                BigDecimal price = lr.invoicePrice() == null ? ap : Decimals.price(lr.invoicePrice());
                x.setQty(qty);
                x.setApPrice(ap);
                x.setInvoicePrice(price);
                BigDecimal amount = Decimals.amount(qty.multiply(price));
                BigDecimal tax = Decimals.amount(amount.multiply(FinSupport.nz(l.getTaxRate())));
                x.setAmount(amount);
                x.setTaxAmount(tax);
                x.setTotalAmount(amount.add(tax));
                // 冲减应付行的金额：全部开完取剩余金额，否则按数量比例
                x.setApAmount(qty.compareTo(openQty) == 0 ? openAmount
                        : l.getTotalAmount().multiply(qty).divide(l.getQty(), Decimals.AMOUNT_SCALE, RoundingMode.HALF_UP));
                BigDecimal pct = ap == null || ap.signum() == 0 ? BigDecimal.ZERO
                        : price.subtract(ap).multiply(BigDecimal.valueOf(100)).divide(ap, 4, RoundingMode.HALF_UP);
                x.setPriceDiffPct(pct);
                x.setOverTolerance(pct.abs().compareTo(tolerancePct) > 0);
            } else {
                // 无数量行（扣款、调整）：发票金额 = 输入的不含税金额，默认应付未开票部分
                BigDecimal[] sp = FinSupport.split(openAmount, l.getTaxRate());
                BigDecimal amount = lr.invoicePrice() == null ? sp[0] : Decimals.amount(lr.invoicePrice());
                BigDecimal tax = lr.invoicePrice() == null ? sp[1] : Decimals.amount(amount.multiply(FinSupport.nz(l.getTaxRate())));
                x.setAmount(amount);
                x.setTaxAmount(tax);
                x.setTotalAmount(amount.add(tax));
                x.setApAmount(openAmount);
                BigDecimal pct = sp[0].signum() == 0 ? BigDecimal.ZERO
                        : amount.subtract(sp[0]).multiply(BigDecimal.valueOf(100)).divide(sp[0], 4, RoundingMode.HALF_UP);
                x.setPriceDiffPct(pct);
                x.setOverTolerance(pct.abs().compareTo(tolerancePct) > 0);
            }
            x.setDiffReason(FinSupport.limit(lr.diffReason(), 256));
            if (Boolean.TRUE.equals(x.getOverTolerance()) && !StringUtils.hasText(x.getDiffReason())) {
                throw BizException.of(FinanceErrorCodes.AP_DIFF_REASON, no, x.getPriceDiffPct().stripTrailingZeros().toPlainString());
            }
            lines.add(x);
            no++;
        }
        // 金额尾差：单头价税合计 − 明细合计 ≤ 容差时调整最后一行，超过不允许保存
        BigDecimal headerTotal = Decimals.amount(req.totalAmount());
        BigDecimal linesTotal = lines.stream().map(FinPurchaseInvoiceLineDO::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal diff = headerTotal.subtract(linesTotal);
        if (diff.abs().compareTo(amountTolerance) > 0) throw BizException.of(FinanceErrorCodes.AP_INVOICE_DIFF, FinSupport.plain(diff));
        if (diff.signum() != 0) {
            FinPurchaseInvoiceLineDO last = lines.get(lines.size() - 1);
            last.setTotalAmount(last.getTotalAmount().add(diff));
            last.setAmount(last.getTotalAmount().subtract(last.getTaxAmount()));
        }
        boolean hasDiff = lines.stream().anyMatch(l -> Boolean.TRUE.equals(l.getOverTolerance()));

        FinPurchaseInvoiceDO inv = new FinPurchaseInvoiceDO();
        inv.setDocNo(support.nextNo(BIZ_TYPE));
        inv.setSupplierId(req.supplierId());
        inv.setInvoiceType(req.invoiceType());
        inv.setInvoiceNo(invoiceNo);
        inv.setInvoiceCode(FinSupport.limit(req.invoiceCode(), 32));
        inv.setInvoiceDate(req.invoiceDate());
        inv.setCurrency(currencies.iterator().next());
        inv.setTotalAmount(headerTotal);
        inv.setTaxAmount(Decimals.amount(req.taxAmount()));
        inv.setAmount(headerTotal.subtract(inv.getTaxAmount()));
        inv.setMatchStatus(hasDiff ? DIFF : MATCHED);
        inv.setDeductionStatus("VAT_SPECIAL".equals(req.invoiceType()) ? "NOT_CERTIFIED" : null);
        inv.setInvoiceStatus(REGISTERED);
        inv.setRemark(FinSupport.trim(req.remark()));
        mapper.insert(inv);
        Map<Long, BigDecimal> apDelta = new LinkedHashMap<>();
        for (FinPurchaseInvoiceLineDO x : lines) {
            x.setInvoiceId(inv.getId());
            lineMapper.insert(x);
            FinPayableLineDO l = apLineMapper.selectById(x.getPayableLineId());
            if (x.getQty() != null) l.setInvoicedQty(Decimals.qty(FinSupport.nz(l.getInvoicedQty()).add(x.getQty())));
            l.setInvoicedAmount(Decimals.amount(FinSupport.nz(l.getInvoicedAmount()).add(x.getApAmount())));
            apLineMapper.updateByIdOrFail(l);
            apDelta.merge(x.getPayableId(), x.getApAmount(), BigDecimal::add);
        }
        applyAp(apDelta, 1, "发票 " + invoiceNo);
        support.bindFiles(req.fileIds(), BIZ_TYPE, inv.getId());
        support.log(BIZ_TYPE, inv.getId(), inv.getDocNo(), "REGISTER", "登记发票", null, inv.getMatchStatus(), invoiceNo);
        if (hasDiff) {
            support.message(support.usersWithPermission("fin:payable:confirm"), "进项发票价格差异待确认：" + invoiceNo,
                    "发票 " + invoiceNo + " 有超出容差的单价差异，请确认", "/finance/payable/invoice/" + inv.getId());
        }
        return inv.getId();
    }

    private void applyAp(Map<Long, BigDecimal> delta, int sign, String reason) {
        for (Map.Entry<Long, BigDecimal> e : delta.entrySet()) {
            FinPayableDO p = apMapper.selectById(e.getKey());
            p.setInvoicedAmount(Decimals.amount(FinSupport.nz(p.getInvoicedAmount()).add(e.getValue().multiply(BigDecimal.valueOf(sign)))));
            apMapper.updateByIdOrFail(p);
            support.log(PayableService.BIZ_TYPE, p.getId(), p.getDocNo(), "INVOICE", sign > 0 ? "匹配发票" : "取消发票匹配", null, null,
                    reason + "，金额 " + FinSupport.plain(e.getValue()));
        }
    }

    /** 差异确认：视为已匹配；差异金额（发票 − 应付）生成价差调整应付行，正负皆可 */
    @Transactional(rollbackFor = Exception.class)
    public void confirmDiff(Long id) {
        FinPurchaseInvoiceDO inv = get(id);
        if (!DIFF.equals(inv.getMatchStatus()) || !REGISTERED.equals(inv.getInvoiceStatus())) {
            throw BizException.of(FinanceErrorCodes.STATUS_NOT_ALLOWED, inv.getMatchStatus(), "确认差异");
        }
        Map<Long, BigDecimal> diffs = new LinkedHashMap<>();
        Map<Long, BigDecimal> taxRates = new LinkedHashMap<>();
        for (FinPurchaseInvoiceLineDO x : lineMapper.selectByParent(id)) {
            if (!Boolean.TRUE.equals(x.getOverTolerance())) continue;
            BigDecimal d = x.getTotalAmount().subtract(x.getApAmount());
            if (d.signum() == 0) continue;
            diffs.merge(x.getPayableId(), d, BigDecimal::add);
            FinPayableLineDO l = apLineMapper.selectById(x.getPayableLineId());
            taxRates.putIfAbsent(x.getPayableId(), l == null ? BigDecimal.ZERO : FinSupport.nz(l.getTaxRate()));
        }
        for (Map.Entry<Long, BigDecimal> e : diffs.entrySet()) {
            FinPayableDO p = apMapper.selectById(e.getKey());
            List<FinPayableLineDO> existing = apLineMapper.selectByParent(p.getId());
            FinPayableLineDO x = new FinPayableLineDO();
            x.setPayableId(p.getId());
            x.setLineNo(existing.stream().mapToInt(FinPayableLineDO::getLineNo).max().orElse(0) + 1);
            x.setLineType(PayableService.PRICE_DIFF);
            x.setSourceNo(inv.getDocNo());
            x.setDescription("发票 " + inv.getInvoiceNo() + " 价差调整");
            x.setTaxRate(taxRates.get(p.getId()));
            BigDecimal[] sp = FinSupport.split(e.getValue(), x.getTaxRate());
            x.setAmount(sp[0]);
            x.setTaxAmount(sp[1]);
            x.setTotalAmount(sp[2]);
            x.setInvoicedQty(BigDecimal.ZERO);
            x.setInvoicedAmount(sp[2]);
            apLineMapper.insert(x);
            existing.add(x);
            PayableService.totals(p, existing);
            p.setInvoicedAmount(Decimals.amount(FinSupport.nz(p.getInvoicedAmount()).add(sp[2])));
            apMapper.updateByIdOrFail(p);
            support.log(PayableService.BIZ_TYPE, p.getId(), p.getDocNo(), "PRICE_DIFF", "价差调整", null, null,
                    "发票 " + inv.getInvoiceNo() + "，差异 " + FinSupport.plain(e.getValue()));
        }
        inv.setMatchStatus(MATCHED);
        inv.setDiffConfirmedBy(support.currentUser());
        inv.setDiffConfirmedAt(LocalDateTime.now());
        mapper.updateByIdOrFail(inv);
        support.log(BIZ_TYPE, id, inv.getDocNo(), "CONFIRM_DIFF", "确认差异", DIFF, MATCHED, null);
    }

    /** 专票认证抵扣 */
    @Transactional(rollbackFor = Exception.class)
    public void certify(Long id, String period) {
        FinPurchaseInvoiceDO inv = get(id);
        if (!REGISTERED.equals(inv.getInvoiceStatus()) || !"VAT_SPECIAL".equals(inv.getInvoiceType())) {
            throw BizException.of(FinanceErrorCodes.STATUS_NOT_ALLOWED, inv.getInvoiceStatus(), "认证");
        }
        if (!period.matches("\\d{6}")) throw BizException.of(FinanceErrorCodes.REASON_REQUIRED, "认证所属期（yyyyMM）");
        inv.setDeductionStatus("CERTIFIED");
        inv.setCertifiedPeriod(period);
        mapper.updateByIdOrFail(inv);
        support.log(BIZ_TYPE, id, inv.getDocNo(), "CERTIFY", "认证抵扣", null, null, period);
    }

    /** 作废：回退应付已开票；已认证的发票不能作废；已确认差异的价差行一并删除（应付未付款时） */
    @Transactional(rollbackFor = Exception.class)
    public void voidInvoice(Long id, String reason) {
        String why = FinSupport.requireText(reason, "作废原因");
        FinPurchaseInvoiceDO inv = get(id);
        if (!REGISTERED.equals(inv.getInvoiceStatus()) || "CERTIFIED".equals(inv.getDeductionStatus())) {
            throw BizException.of(FinanceErrorCodes.STATUS_NOT_ALLOWED, "CERTIFIED".equals(inv.getDeductionStatus()) ? "已认证" : inv.getInvoiceStatus(), "作废");
        }
        Map<Long, BigDecimal> apDelta = new LinkedHashMap<>();
        for (FinPurchaseInvoiceLineDO x : lineMapper.selectByParent(id)) {
            FinPayableLineDO l = apLineMapper.selectById(x.getPayableLineId());
            if (l != null) {
                if (x.getQty() != null) l.setInvoicedQty(Decimals.qty(FinSupport.nz(l.getInvoicedQty()).subtract(x.getQty())));
                l.setInvoicedAmount(Decimals.amount(FinSupport.nz(l.getInvoicedAmount()).subtract(x.getApAmount())));
                apLineMapper.updateByIdOrFail(l);
            }
            apDelta.merge(x.getPayableId(), x.getApAmount(), BigDecimal::add);
        }
        applyAp(apDelta, -1, "发票作废 " + inv.getInvoiceNo());
        if (inv.getDiffConfirmedAt() != null) removePriceDiff(inv);
        inv.setInvoiceStatus(VOIDED);
        inv.setVoidReason(FinSupport.limit(why, 256));
        mapper.updateByIdOrFail(inv);
        support.log(BIZ_TYPE, id, inv.getDocNo(), "VOID", "作废", REGISTERED, VOIDED, why);
    }

    private void removePriceDiff(FinPurchaseInvoiceDO inv) {
        List<FinPayableLineDO> diffLines = apLineMapper.selectList(new LambdaQueryWrapper<FinPayableLineDO>()
                .eq(FinPayableLineDO::getLineType, PayableService.PRICE_DIFF).eq(FinPayableLineDO::getSourceNo, inv.getDocNo()));
        for (FinPayableLineDO d : diffLines) {
            FinPayableDO p = apMapper.selectById(d.getPayableId());
            apLineMapper.deleteById(d.getId());
            List<FinPayableLineDO> rest = apLineMapper.selectByParent(p.getId());
            PayableService.totals(p, rest);
            if (PayableService.requestable(p).signum() < 0) throw BizException.of(FinanceErrorCodes.AP_PROCESSED, "取消价差调整");
            p.setInvoicedAmount(Decimals.amount(FinSupport.nz(p.getInvoicedAmount()).subtract(d.getTotalAmount())));
            apMapper.updateByIdOrFail(p);
        }
    }

    // ==================== 查询 ====================

    public FinPurchaseInvoiceDO get(Long id) {
        FinPurchaseInvoiceDO inv = id == null ? null : mapper.selectById(id);
        if (inv == null) throw BizException.of(FinanceErrorCodes.NOT_EXISTS, "进项发票");
        return inv;
    }

    public PageResult<PiRow> page(PiQuery q) {
        IPage<FinPurchaseInvoiceDO> p = mapper.selectPage(new Page<>(q.getPageNo(), q.getPageSize()), query(q));
        return new PageResult<>(rows(p.getRecords()), p.getTotal());
    }

    public List<PiRow> list(PiQuery q) {
        return rows(mapper.selectList(query(q)));
    }

    private LambdaQueryWrapper<FinPurchaseInvoiceDO> query(PiQuery q) {
        return new LambdaQueryWrapper<FinPurchaseInvoiceDO>()
                .and(StringUtils.hasText(q.getKeyword()), w -> w.like(FinPurchaseInvoiceDO::getInvoiceNo, q.getKeyword()).or()
                        .like(FinPurchaseInvoiceDO::getDocNo, q.getKeyword()))
                .eq(q.getSupplierId() != null, FinPurchaseInvoiceDO::getSupplierId, q.getSupplierId())
                .eq(StringUtils.hasText(q.getInvoiceType()), FinPurchaseInvoiceDO::getInvoiceType, q.getInvoiceType())
                .eq(StringUtils.hasText(q.getMatchStatus()), FinPurchaseInvoiceDO::getMatchStatus, q.getMatchStatus())
                .eq(StringUtils.hasText(q.getDeductionStatus()), FinPurchaseInvoiceDO::getDeductionStatus, q.getDeductionStatus())
                .eq(StringUtils.hasText(q.getStatus()), FinPurchaseInvoiceDO::getInvoiceStatus, q.getStatus())
                .ge(q.getDateFrom() != null, FinPurchaseInvoiceDO::getInvoiceDate, q.getDateFrom())
                .le(q.getDateTo() != null, FinPurchaseInvoiceDO::getInvoiceDate, q.getDateTo())
                .orderByDesc(FinPurchaseInvoiceDO::getInvoiceDate).orderByDesc(FinPurchaseInvoiceDO::getId);
    }

    private List<PiRow> rows(List<FinPurchaseInvoiceDO> list) {
        Map<Long, SupplierDTO> ss = support.suppliers(list.stream().map(FinPurchaseInvoiceDO::getSupplierId).toList());
        Map<Long, UserDTO> users = support.users(list.stream().map(FinPurchaseInvoiceDO::getCreatedBy).toList());
        return list.stream().map(i -> {
            SupplierDTO s = ss.get(i.getSupplierId());
            return new PiRow(i.getId(), i.getDocNo(), i.getSupplierId(), s == null ? null : s.name(), i.getInvoiceType(), i.getInvoiceNo(), i.getInvoiceCode(),
                    i.getInvoiceDate(), i.getCurrency(), i.getAmount(), i.getTaxAmount(), i.getTotalAmount(), i.getMatchStatus(), i.getDeductionStatus(),
                    i.getCertifiedPeriod(), i.getInvoiceStatus(), i.getRemark(), FinSupport.name(users, i.getCreatedBy()), i.getCreatedAt());
        }).toList();
    }

    public PiDetail detail(Long id) {
        FinPurchaseInvoiceDO inv = get(id);
        List<FinPurchaseInvoiceLineDO> lines = lineMapper.selectByParent(id);
        Map<Long, FinPayableDO> aps = lines.isEmpty() ? Map.of() : apMapper.selectBatchIds(lines.stream().map(FinPurchaseInvoiceLineDO::getPayableId)
                .distinct().toList()).stream().collect(Collectors.toMap(FinPayableDO::getId, Function.identity()));
        Map<Long, FinPayableLineDO> apLines = lines.isEmpty() ? Map.of() : apLineMapper.selectBatchIds(lines.stream()
                .map(FinPurchaseInvoiceLineDO::getPayableLineId).toList()).stream().collect(Collectors.toMap(FinPayableLineDO::getId, Function.identity()));
        Map<Long, MaterialDTO> ms = support.materials(lines.stream().map(FinPurchaseInvoiceLineDO::getMaterialId).toList());
        List<PiLineVO> vos = lines.stream().map(x -> {
            MaterialDTO m = ms.get(x.getMaterialId());
            FinPayableDO p = aps.get(x.getPayableId());
            FinPayableLineDO l = apLines.get(x.getPayableLineId());
            return new PiLineVO(x.getId(), x.getLineNo(), x.getPayableId(), p == null ? null : p.getDocNo(), x.getPayableLineId(),
                    l == null ? null : l.getSourceNo(), l == null ? null : l.getOrderNo(), x.getMaterialId(), m == null ? null : m.code(),
                    m == null ? null : m.name(), x.getQty(), x.getInvoicePrice(), x.getApPrice(), x.getAmount(), x.getTaxAmount(), x.getTotalAmount(),
                    x.getApAmount(), x.getPriceDiffPct(), Boolean.TRUE.equals(x.getOverTolerance()), x.getDiffReason());
        }).toList();
        return new PiDetail(rows(List.of(inv)).get(0), support.userName(inv.getDiffConfirmedBy()), inv.getDiffConfirmedAt(), inv.getVoidReason(), vos);
    }
}
