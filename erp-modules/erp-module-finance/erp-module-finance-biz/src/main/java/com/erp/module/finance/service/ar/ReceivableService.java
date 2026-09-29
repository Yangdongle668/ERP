package com.erp.module.finance.service.ar;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.common.exception.BizException;
import com.erp.common.result.PageResult;
import com.erp.common.util.Decimals;
import com.erp.module.crm.api.customer.CustomerDTO;
import com.erp.module.engineering.api.material.MaterialDTO;
import com.erp.module.finance.api.FinanceErrorCodes;
import com.erp.module.finance.config.FinanceModuleConfig;
import com.erp.module.finance.controller.vo.ArVOs.ArDetail;
import com.erp.module.finance.controller.vo.ArVOs.ArLineVO;
import com.erp.module.finance.controller.vo.ArVOs.ArQuery;
import com.erp.module.finance.controller.vo.ArVOs.ArRow;
import com.erp.module.finance.controller.vo.ArVOs.InvoiceRef;
import com.erp.module.finance.controller.vo.ArVOs.OtherArSave;
import com.erp.module.finance.controller.vo.ArVOs.OtherLine;
import com.erp.module.finance.controller.vo.ArVOs.SubmitResult;
import com.erp.module.finance.dal.dataobject.FinReceivableDO;
import com.erp.module.finance.dal.dataobject.FinReceivableLineDO;
import com.erp.module.finance.dal.dataobject.FinSalesInvoiceDO;
import com.erp.module.finance.dal.dataobject.FinSalesInvoiceLineDO;
import com.erp.module.finance.dal.mapper.FinReceivableLineMapper;
import com.erp.module.finance.dal.mapper.FinReceivableMapper;
import com.erp.module.finance.dal.mapper.FinSalesInvoiceLineMapper;
import com.erp.module.finance.dal.mapper.FinSalesInvoiceMapper;
import com.erp.module.finance.service.ArStatus;
import com.erp.module.finance.service.FinAction;
import com.erp.module.finance.service.FinStateMachines;
import com.erp.module.finance.service.FinSupport;
import com.erp.module.finance.service.verify.VerificationService;
import com.erp.module.inventory.api.doc.StockDocEvent;
import com.erp.module.quality.api.complaint.ComplaintClaimAgreedEvent;
import com.erp.module.sales.api.order.SalesOrderHeaderDTO;
import com.erp.module.sales.api.order.SalesOrderQueryApi;
import com.erp.module.sales.api.returns.SalesReturnReceivedEvent;
import com.erp.module.shipping.api.shipment.BillOfLadingReceivedEvent;
import com.erp.module.shipping.api.shipment.ShipmentConfirmedEvent;
import com.erp.module.shipping.api.shipment.ShipmentReversedEvent;
import com.erp.module.system.api.paymentterm.BaseEvent;
import com.erp.module.system.api.paymentterm.DueNode;
import com.erp.module.system.api.paymentterm.PaymentTermApi;
import com.erp.module.system.api.user.UserDTO;
import com.erp.module.system.api.workflow.ApprovalCompletedEvent;
import com.erp.module.system.api.workflow.StartResult;
import com.erp.module.system.api.workflow.WorkflowApi;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** 应收单（12-02）：业务事件生成、确认 / 反确认 / 作废、其他应收 */
@Service
public class ReceivableService {

    public static final String BIZ_TYPE = FinanceModuleConfig.RECEIVABLE;
    public static final String SALES = "SALES";
    public static final String SALES_RETURN = "SALES_RETURN";
    public static final String DISCOUNT = "DISCOUNT";
    public static final String OTHER = "OTHER";
    static final String SRC_SHIPMENT = "SHP_SHIPMENT";
    static final String SRC_RETURN = "SAL_RETURN";
    static final String SRC_COMPLAINT = "QC_COMPLAINT";
    /** 到期日计算使用的出货类节点（FIN-AR-R09） */
    static final Set<BaseEvent> AFTER_SHIPMENT = Set.of(BaseEvent.SHIPMENT, BaseEvent.BL_DATE, BaseEvent.MONTH_END, BaseEvent.INVOICE_DATE,
            BaseEvent.RECEIPT_DATE);

    private final FinReceivableMapper mapper;
    private final FinReceivableLineMapper lineMapper;
    private final FinSalesInvoiceMapper invoiceMapper;
    private final FinSalesInvoiceLineMapper invoiceLineMapper;
    private final SalesOrderQueryApi orderQueryApi;
    private final PaymentTermApi paymentTermApi;
    private final WorkflowApi workflowApi;
    private final VerificationService verificationService;
    private final FinSupport support;

    public ReceivableService(FinReceivableMapper mapper, FinReceivableLineMapper lineMapper, FinSalesInvoiceMapper invoiceMapper,
                             FinSalesInvoiceLineMapper invoiceLineMapper, SalesOrderQueryApi orderQueryApi, PaymentTermApi paymentTermApi,
                             WorkflowApi workflowApi, VerificationService verificationService, FinSupport support) {
        this.mapper = mapper;
        this.lineMapper = lineMapper;
        this.invoiceMapper = invoiceMapper;
        this.invoiceLineMapper = invoiceLineMapper;
        this.orderQueryApi = orderQueryApi;
        this.paymentTermApi = paymentTermApi;
        this.workflowApi = workflowApi;
        this.verificationService = verificationService;
        this.support = support;
    }

    // ==================== 业务事件 ====================

    /** FIN-AR-R01：出货确认生成应收（补货等金额 0 的行不生成）；同一出货行只生成一次（唯一约束 + 来源检查） */
    @EventListener
    public void onShipmentConfirmed(ShipmentConfirmedEvent e) {
        if (active(SRC_SHIPMENT, e.getShipmentId(), null) != null) return;
        List<ShipmentConfirmedEvent.Line> lines = e.getLines().stream().filter(l -> l.amount() != null && l.amount().signum() != 0).toList();
        if (lines.isEmpty()) return;
        Map<Long, SalesOrderHeaderDTO> orders = orderQueryApi.getOrderHeaders(lines.stream().map(ShipmentConfirmedEvent.Line::orderId)
                .filter(Objects::nonNull).collect(Collectors.toSet()));
        SalesOrderHeaderDTO first = lines.stream().map(l -> orders.get(l.orderId())).filter(Objects::nonNull).findFirst().orElse(null);
        FinReceivableDO r = header(SALES, e.getCustomerId(), e.getCurrency(), e.getExchangeRate(), e.getShipDate());
        r.setSourceType(SRC_SHIPMENT);
        r.setSourceId(e.getShipmentId());
        r.setSourceNo(e.getShipmentNo());
        r.setPaymentTermId(first == null ? null : first.paymentTermId());
        r.setOrderId(lines.stream().map(ShipmentConfirmedEvent.Line::orderId).distinct().count() == 1 ? lines.get(0).orderId() : null);
        r.setDescription("出货 " + e.getShipmentNo());
        support.fillOwner(r, first == null ? null : first.ownerId());
        mapper.insert(r);
        int no = 1;
        List<FinReceivableLineDO> saved = new ArrayList<>();
        for (ShipmentConfirmedEvent.Line l : lines) {
            SalesOrderHeaderDTO o = orders.get(l.orderId());
            FinReceivableLineDO x = line(r, no++, SALES, l.shipmentLineId(), l.materialId(), l.baseQty(), l.priceInclTax(), l.taxRate(), l.amount());
            x.setOrderId(l.orderId());
            x.setOrderNo(o == null ? null : o.orderNo());
            x.setOrderLineId(l.orderLineId());
            lineMapper.insert(x);
            saved.add(x);
        }
        totals(r, saved);
        r.setDueDate(dueDate(r.getPaymentTermId(), r.getBizDate(), null, r.getCustomerId()));
        mapper.updateByIdOrFail(r);
        created(r, "出货单 " + e.getShipmentNo());
        autoConfirm(r);
    }

    /** FIN-AR-R02：出货反确认 → 作废对应应收（已核销 / 已开票的在出库反确认时已被阻止） */
    @EventListener
    public void onShipmentReversed(ShipmentReversedEvent e) {
        for (FinReceivableDO r : actives(SRC_SHIPMENT, e.getShipmentId())) {
            if (processed(r)) throw new BizException(FinanceErrorCodes.AR_BLOCK_REVERSE);
            voidInternal(r, "出货反确认" + (e.getReason() == null ? "" : "：" + e.getReason()));
        }
    }

    /** FIN-AR-R07：销售出库反确认前校验 */
    @EventListener
    public void onStockDoc(StockDocEvent e) {
        if (e.getKind() != StockDocEvent.Kind.OUT_REVERSING || e.getSource() == null || !SRC_SHIPMENT.equals(e.getSource().sourceType())) return;
        for (FinReceivableDO r : actives(SRC_SHIPMENT, e.getSource().sourceId())) {
            if (processed(r)) throw new BizException(FinanceErrorCodes.AR_BLOCK_REVERSE);
        }
    }

    /** FIN-AR-R09：提单日期回填后重算到期日 */
    @EventListener
    public void onBillOfLading(BillOfLadingReceivedEvent e) {
        for (FinReceivableDO r : actives(SRC_SHIPMENT, e.getShipmentId())) {
            r.setBlDate(e.getBlDate());
            r.setDueDate(dueDate(r.getPaymentTermId(), r.getBizDate(), e.getBlDate(), r.getCustomerId()));
            mapper.updateByIdOrFail(r);
            support.log(BIZ_TYPE, r.getId(), r.getDocNo(), "DUE_DATE", "到期日重算", null, null,
                    "提单 " + Objects.toString(e.getBlNo(), "") + " " + e.getBlDate() + " → 到期日 " + r.getDueDate());
        }
    }

    /** FIN-AR-R03：退货入库（退款）生成红字应收；入库反确认时作废（已核销则阻止） */
    @EventListener
    public void onSalesReturn(SalesReturnReceivedEvent e) {
        if (!"REFUND".equals(e.getHandling())) return;
        FinReceivableDO existing = active(SRC_RETURN, e.getReturnId(), e.getStockInId());
        if (e.isReversed()) {
            if (existing == null) return;
            if (processed(existing)) throw new BizException(FinanceErrorCodes.AR_BLOCK_REVERSE);
            voidInternal(existing, "退货入库反确认");
            return;
        }
        if (existing != null) return;
        List<SalesReturnReceivedEvent.Line> lines = e.getLines().stream().filter(l -> l.amount() != null && l.amount().signum() != 0).toList();
        if (lines.isEmpty()) return;
        Map<Long, SalesOrderHeaderDTO> orders = orderQueryApi.getOrderHeaders(lines.stream().map(SalesReturnReceivedEvent.Line::orderId)
                .filter(Objects::nonNull).collect(Collectors.toSet()));
        FinReceivableDO r = header(SALES_RETURN, e.getCustomerId(), e.getCurrency(), e.getExchangeRate(), LocalDate.now());
        r.setSourceType(SRC_RETURN);
        r.setSourceId(e.getReturnId());
        r.setSourceNo(e.getReturnNo());
        r.setSourceRefId(e.getStockInId());
        r.setOrderId(lines.stream().map(SalesReturnReceivedEvent.Line::orderId).distinct().count() == 1 ? lines.get(0).orderId() : null);
        r.setDescription("销售退货 " + e.getReturnNo());
        SalesOrderHeaderDTO first = lines.stream().map(l -> orders.get(l.orderId())).filter(Objects::nonNull).findFirst().orElse(null);
        support.fillOwner(r, first == null ? null : first.ownerId());
        mapper.insert(r);
        int no = 1;
        List<FinReceivableLineDO> saved = new ArrayList<>();
        for (SalesReturnReceivedEvent.Line l : lines) {
            SalesOrderHeaderDTO o = orders.get(l.orderId());
            FinReceivableLineDO x = line(r, no++, SALES_RETURN, null, l.materialId(), l.baseQty().negate(), l.priceInclTax(), l.taxRate(), l.amount().negate());
            x.setOrderId(l.orderId());
            x.setOrderNo(o == null ? null : o.orderNo());
            x.setOrderLineId(l.orderLineId());
            lineMapper.insert(x);
            saved.add(x);
        }
        totals(r, saved);
        r.setDueDate(r.getBizDate());
        mapper.updateByIdOrFail(r);
        created(r, "退货单 " + e.getReturnNo());
        autoConfirm(r);
    }

    /** FIN-AR-R04：客诉同意赔偿 → 折让红字应收（可核销到该客户的蓝字应收） */
    @EventListener
    public void onComplaintClaim(ComplaintClaimAgreedEvent e) {
        if (e.getAgreedAmount() == null || e.getAgreedAmount().signum() <= 0) return;
        if (active(SRC_COMPLAINT, e.getComplaintId(), null) != null) return;
        String currency = StringUtils.hasText(e.getCurrency()) ? e.getCurrency() : support.baseCurrency();
        FinReceivableDO r = header(DISCOUNT, e.getCustomerId(), currency, null, LocalDate.now());
        r.setSourceType(SRC_COMPLAINT);
        r.setSourceId(e.getComplaintId());
        r.setSourceNo(e.getComplaintNo());
        r.setDescription("客诉赔偿 " + e.getComplaintNo());
        support.fillOwner(r, support.customer(e.getCustomerId()).ownerId());
        mapper.insert(r);
        FinReceivableLineDO x = line(r, 1, DISCOUNT, null, null, null, null, BigDecimal.ZERO, e.getAgreedAmount().negate());
        x.setDescription("客诉 " + e.getComplaintNo() + " 赔偿折让");
        lineMapper.insert(x);
        totals(r, List.of(x));
        r.setDueDate(r.getBizDate());
        mapper.updateByIdOrFail(r);
        created(r, "客诉单 " + e.getComplaintNo());
        autoConfirm(r);
    }

    private FinReceivableDO header(String type, Long customerId, String currency, BigDecimal rate, LocalDate bizDate) {
        FinReceivableDO r = new FinReceivableDO();
        r.setDocNo(support.nextNo(BIZ_TYPE));
        r.setDocDate(LocalDate.now());
        r.setArType(type);
        r.setCustomerId(customerId);
        r.setCurrency(currency);
        r.setExchangeRate(rate != null && rate.signum() > 0 ? rate : support.rateOrZero(currency, bizDate));
        r.setBizDate(bizDate);
        r.setAmount(BigDecimal.ZERO);
        r.setTaxAmount(BigDecimal.ZERO);
        r.setTotalAmount(BigDecimal.ZERO);
        r.setTotalAmountBase(BigDecimal.ZERO);
        r.setVerifiedAmount(BigDecimal.ZERO);
        r.setInvoicedAmount(BigDecimal.ZERO);
        r.setArStatus(ArStatus.DRAFT.name());
        r.setStatus(ArStatus.DRAFT.docStatus());
        return r;
    }

    private static FinReceivableLineDO line(FinReceivableDO r, int no, String type, Long sourceLineId, Long materialId, BigDecimal qty,
                                            BigDecimal price, BigDecimal taxRate, BigDecimal total) {
        FinReceivableLineDO x = new FinReceivableLineDO();
        x.setReceivableId(r.getId());
        x.setLineNo(no);
        x.setArType(type);
        x.setSourceLineId(sourceLineId);
        x.setSourceLineKey(sourceLineId);
        x.setMaterialId(materialId);
        x.setQty(qty == null ? null : Decimals.qty(qty));
        x.setPriceInclTax(price == null ? null : Decimals.price(price));
        x.setTaxRate(FinSupport.nz(taxRate));
        BigDecimal[] s = FinSupport.split(total, taxRate);
        x.setAmount(s[0]);
        x.setTaxAmount(s[1]);
        x.setTotalAmount(s[2]);
        x.setInvoicedQty(BigDecimal.ZERO);
        x.setInvoicedAmount(BigDecimal.ZERO);
        return x;
    }

    private static void totals(FinReceivableDO r, List<FinReceivableLineDO> lines) {
        r.setAmount(lines.stream().map(FinReceivableLineDO::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
        r.setTaxAmount(lines.stream().map(FinReceivableLineDO::getTaxAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
        r.setTotalAmount(lines.stream().map(FinReceivableLineDO::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
        r.setTotalAmountBase(FinSupport.toBase(r.getTotalAmount(), r.getExchangeRate()));
    }

    private void created(FinReceivableDO r, String from) {
        support.log(BIZ_TYPE, r.getId(), r.getDocNo(), FinAction.CREATE.name(), FinAction.CREATE.label(), null, r.getArStatus(), from);
    }

    /** 参数自动确认；期间已结账时保留草稿（由应收会计处理） */
    private void autoConfirm(FinReceivableDO r) {
        if (!support.params().getBool(FinanceModuleConfig.P_AR_AUTO_CONFIRM)) return;
        if (r.getExchangeRate().signum() == 0) return;
        if (support.isClosed(FinSupport.periodOf(r.getBizDate()))) return;
        confirmInternal(get(r.getId()), "自动确认");
    }

    /**
     * 到期日（FIN-AR-R09）：按付款条件第一个出货类节点计算（出货日 / 提单日 / 月结）；提单未到时按出货日暂估；
     * 无付款条件时按客户信用天数
     */
    LocalDate dueDate(Long termId, LocalDate shipDate, LocalDate blDate, Long customerId) {
        if (termId != null) {
            try {
                Map<BaseEvent, LocalDate> events = new EnumMap<>(BaseEvent.class);
                events.put(BaseEvent.SHIPMENT, shipDate);
                events.put(BaseEvent.INVOICE_DATE, shipDate);
                events.put(BaseEvent.RECEIPT_DATE, shipDate);
                if (blDate != null) events.put(BaseEvent.BL_DATE, blDate);
                for (DueNode n : paymentTermApi.calcDueDates(termId, BigDecimal.ONE, events)) {
                    if (!AFTER_SHIPMENT.contains(n.baseEvent())) continue;
                    return n.dueDate() != null ? n.dueDate() : shipDate.plusDays(n.days());
                }
                return shipDate;
            } catch (BizException ignored) {
                // 付款条件已删除：按客户信用天数
            }
        }
        CustomerDTO c = support.customerApi().getCustomer(customerId).orElse(null);
        int days = c == null || c.creditDays() == null ? 0 : c.creditDays();
        return shipDate.plusDays(days);
    }

    private FinReceivableDO active(String sourceType, Long sourceId, Long refId) {
        return mapper.selectList(new LambdaQueryWrapper<FinReceivableDO>().eq(FinReceivableDO::getSourceType, sourceType)
                        .eq(FinReceivableDO::getSourceId, sourceId).eq(refId != null, FinReceivableDO::getSourceRefId, refId)
                        .ne(FinReceivableDO::getArStatus, ArStatus.VOIDED.name()))
                .stream().findFirst().orElse(null);
    }

    private List<FinReceivableDO> actives(String sourceType, Long sourceId) {
        return mapper.selectList(new LambdaQueryWrapper<FinReceivableDO>().eq(FinReceivableDO::getSourceType, sourceType)
                .eq(FinReceivableDO::getSourceId, sourceId).ne(FinReceivableDO::getArStatus, ArStatus.VOIDED.name()));
    }

    /** 已核销、已开票或已生成凭证 */
    static boolean processed(FinReceivableDO r) {
        return FinSupport.nz(r.getVerifiedAmount()).signum() != 0 || FinSupport.nz(r.getInvoicedAmount()).signum() != 0;
    }

    // ==================== 确认、反确认、作废 ====================

    /** FIN-AR-R05 业务日期所在期间未结账；FIN-AR-R06 刷新信用 */
    @Transactional(rollbackFor = Exception.class)
    public void confirm(Long id) {
        FinReceivableDO r = get(id);
        if (ArStatus.PENDING.name().equals(r.getArStatus())) throw new BizException(FinanceErrorCodes.DOC_PENDING);
        confirmInternal(r, null);
    }

    /** 批量确认：返回成功张数，失败的跳过并说明 */
    @Transactional(rollbackFor = Exception.class)
    public List<String> batchConfirm(List<Long> ids) {
        List<String> errors = new ArrayList<>();
        for (Long id : ids == null ? List.<Long>of() : ids) {
            FinReceivableDO r = get(id);
            if (!ArStatus.DRAFT.name().equals(r.getArStatus()) || OTHER.equals(r.getArType())) {
                errors.add(r.getDocNo() + "：当前状态不能确认");
                continue;
            }
            if (support.isClosed(FinSupport.periodOf(r.getBizDate()))) {
                errors.add(r.getDocNo() + "：会计期间 " + FinSupport.periodOf(r.getBizDate()) + " 已结账");
                continue;
            }
            confirmInternal(r, "批量确认");
        }
        return errors;
    }

    private void confirmInternal(FinReceivableDO r, String reason) {
        support.requireOpen(r.getBizDate());
        rerate(r);
        r.setConfirmedAt(LocalDateTime.now());
        fire(r, FinAction.CONFIRM, reason);
        support.balanceChanged(List.of(r.getCustomerId()));
    }

    /** 生成时汇率未维护（为 0）：确认时按业务日期取汇率并重算本位币 */
    private void rerate(FinReceivableDO r) {
        if (r.getExchangeRate().signum() != 0) return;
        r.setExchangeRate(support.rate(r.getCurrency(), r.getBizDate()));
        r.setTotalAmountBase(FinSupport.toBase(r.getTotalAmount(), r.getExchangeRate()));
    }

    /** 反确认：未核销、未开票、未生成凭证 */
    @Transactional(rollbackFor = Exception.class)
    public void unconfirm(Long id, String reason) {
        String why = FinSupport.requireText(reason, "反确认原因");
        FinReceivableDO r = get(id);
        if (processed(r)) throw BizException.of(FinanceErrorCodes.AR_PROCESSED, "反确认");
        if (r.getVoucherId() != null) throw new BizException(FinanceErrorCodes.VCH_AUDITED);
        support.requireOpen(r.getBizDate());
        r.setConfirmedAt(null);
        fire(r, FinAction.UNCONFIRM, why);
        support.balanceChanged(List.of(r.getCustomerId()));
    }

    /** 作废草稿（手工） */
    @Transactional(rollbackFor = Exception.class)
    public void voidDoc(Long id, String reason) {
        String why = FinSupport.requireText(reason, "作废原因");
        FinReceivableDO r = get(id);
        if (!ArStatus.DRAFT.name().equals(r.getArStatus())) throw BizException.of(FinanceErrorCodes.STATUS_NOT_ALLOWED, status(r).label(), "作废");
        voidInternal(r, why);
    }

    private void voidInternal(FinReceivableDO r, String reason) {
        boolean confirmed = ArStatus.CONFIRMED.name().equals(r.getArStatus());
        if (confirmed) support.requireOpen(r.getBizDate());
        r.setVoidReason(FinSupport.limit(reason, 256));
        fire(r, FinAction.VOID, reason);
        // 释放出货行唯一键，重新出货确认时可再次生成
        lineMapper.update(null, new LambdaUpdateWrapper<FinReceivableLineDO>().set(FinReceivableLineDO::getSourceLineKey, null)
                .eq(FinReceivableLineDO::getReceivableId, r.getId()));
        if (confirmed) support.balanceChanged(List.of(r.getCustomerId()));
    }

    private void fire(FinReceivableDO r, FinAction action, String reason) {
        ArStatus from = status(r);
        ArStatus to = FinStateMachines.AR.next(from, action)
                .orElseThrow(() -> BizException.of(FinanceErrorCodes.STATUS_NOT_ALLOWED, from.label(), action.label()));
        r.setArStatus(to.name());
        r.setStatus(to.docStatus());
        mapper.updateByIdOrFail(r);
        support.log(BIZ_TYPE, r.getId(), r.getDocNo(), action.name(), action.label(), from.name(), to.name(), reason);
    }

    static ArStatus status(FinReceivableDO r) {
        return ArStatus.valueOf(r.getArStatus());
    }

    // ==================== 其他应收 ====================

    @Transactional(rollbackFor = Exception.class)
    public Long createOther(OtherArSave req) {
        FinReceivableDO r = header(OTHER, req.customerId(), req.currency(), req.exchangeRate(), req.bizDate());
        support.customer(req.customerId());
        support.currencyApi().validate(req.currency());
        fillOther(r, req);
        support.fillOwner(r, null);
        mapper.insert(r);
        saveOtherLines(r, req.lines());
        support.bindFiles(req.fileIds(), BIZ_TYPE, r.getId());
        created(r, "手工新建");
        return r.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateOther(Long id, OtherArSave req) {
        FinReceivableDO r = get(id);
        if (!OTHER.equals(r.getArType()) || !ArStatus.DRAFT.name().equals(r.getArStatus())) {
            throw BizException.of(FinanceErrorCodes.STATUS_NOT_ALLOWED, status(r).label(), "修改");
        }
        support.customer(req.customerId());
        support.currencyApi().validate(req.currency());
        r.setCustomerId(req.customerId());
        r.setCurrency(req.currency());
        r.setBizDate(req.bizDate());
        r.setExchangeRate(req.exchangeRate() != null && req.exchangeRate().signum() > 0 ? req.exchangeRate() : BigDecimal.ZERO);
        fillOther(r, req);
        mapper.updateByIdOrFail(r);
        lineMapper.deleteByParent(id);
        saveOtherLines(get(id), req.lines());
        support.bindFiles(req.fileIds(), BIZ_TYPE, id);
    }

    private void fillOther(FinReceivableDO r, OtherArSave req) {
        if (r.getExchangeRate() == null || r.getExchangeRate().signum() <= 0) r.setExchangeRate(support.rate(r.getCurrency(), r.getBizDate()));
        r.setDueDate(req.dueDate() != null ? req.dueDate() : req.bizDate());
        r.setDescription(FinSupport.limit(req.description(), 512));
        r.setRemark(FinSupport.trim(req.remark()));
    }

    private void saveOtherLines(FinReceivableDO r, List<OtherLine> reqLines) {
        List<OtherLine> lines = reqLines == null ? List.of() : reqLines.stream().filter(l -> l.totalAmount() != null && l.totalAmount().signum() != 0).toList();
        if (lines.isEmpty()) throw new BizException(FinanceErrorCodes.NO_LINES);
        int no = 1;
        List<FinReceivableLineDO> saved = new ArrayList<>();
        for (OtherLine l : lines) {
            FinReceivableLineDO x = line(r, no++, OTHER, null, null, null, null, l.taxRate(), l.totalAmount());
            x.setDescription(FinSupport.limit(l.description(), 256));
            lineMapper.insert(x);
            saved.add(x);
        }
        totals(r, saved);
        mapper.updateByIdOrFail(r);
    }

    /** 提交审批（FIN_OTHER_RECEIVABLE）；未配置审批流时直接确认 */
    @Transactional(rollbackFor = Exception.class)
    public SubmitResult submit(Long id) {
        FinReceivableDO r = get(id);
        if (!OTHER.equals(r.getArType())) throw BizException.of(FinanceErrorCodes.STATUS_NOT_ALLOWED, status(r).label(), "提交");
        support.requireOpen(r.getBizDate());
        fire(r, FinAction.SUBMIT, null);
        Map<String, Object> vars = new HashMap<>();
        vars.put("amountBase", FinSupport.nz(r.getTotalAmountBase()).abs());
        Map<String, Long> users = new HashMap<>();
        if (r.getOwnerId() != null) users.put("ownerId", r.getOwnerId());
        CustomerDTO c = support.customer(r.getCustomerId());
        StartResult sr = workflowApi.start(FinanceModuleConfig.OTHER_RECEIVABLE, id, r.getDocNo(),
                "其他应收 " + r.getDocNo() + "（" + FinSupport.customerName(c) + "）", vars, users, support.currentUser());
        if (!sr.isStarted()) approve(get(id));
        return new SubmitResult(id, get(id).getArStatus());
    }

    @Transactional(rollbackFor = Exception.class)
    public void withdraw(Long id) {
        FinReceivableDO r = get(id);
        if (!ArStatus.PENDING.name().equals(r.getArStatus())) throw BizException.of(FinanceErrorCodes.STATUS_NOT_ALLOWED, status(r).label(), "撤回");
        workflowApi.withdraw(FinanceModuleConfig.OTHER_RECEIVABLE, id, support.currentUser());
        FinReceivableDO fresh = get(id);
        if (ArStatus.PENDING.name().equals(fresh.getArStatus())) fire(fresh, FinAction.WITHDRAW, null);
    }

    @EventListener
    public void onApproval(ApprovalCompletedEvent e) {
        if (!FinanceModuleConfig.OTHER_RECEIVABLE.equals(e.getBizType())) return;
        FinReceivableDO r = mapper.selectById(e.getBizId());
        if (r == null || !ArStatus.PENDING.name().equals(r.getArStatus())) return;
        switch (e.getResult()) {
            case APPROVED -> approve(r);
            case WITHDRAWN -> fire(r, FinAction.WITHDRAW, null);
            default -> fire(r, FinAction.REJECT, e.getComment());
        }
    }

    private void approve(FinReceivableDO r) {
        support.requireOpen(r.getBizDate());
        rerate(r);
        r.setConfirmedAt(LocalDateTime.now());
        fire(r, FinAction.APPROVE, null);
        support.balanceChanged(List.of(r.getCustomerId()));
    }

    // ==================== 核销、开票回写（供核销 / 开票服务调用） ====================

    /** 核销金额变动（原币，与单据同号） */
    public void addVerified(Long id, BigDecimal delta) {
        FinReceivableDO r = get(id);
        r.setVerifiedAmount(Decimals.amount(FinSupport.nz(r.getVerifiedAmount()).add(delta)));
        mapper.updateByIdOrFail(r);
    }

    // ==================== 查询 ====================

    public FinReceivableDO get(Long id) {
        FinReceivableDO r = id == null ? null : mapper.selectById(id);
        if (r == null) throw BizException.of(FinanceErrorCodes.NOT_EXISTS, "应收单");
        return r;
    }

    public PageResult<ArRow> page(ArQuery q) {
        LambdaQueryWrapper<FinReceivableDO> w = query(q);
        if (w == null) return new PageResult<>(List.of(), 0L);
        IPage<FinReceivableDO> p = mapper.selectScopedPage(new Page<>(q.getPageNo(), q.getPageSize()), w);
        return new PageResult<>(rows(p.getRecords()), p.getTotal());
    }

    public List<ArRow> list(ArQuery q) {
        LambdaQueryWrapper<FinReceivableDO> w = query(q);
        return w == null ? List.of() : rows(mapper.selectScopedList(w));
    }

    /** 列表底部合计（本位币）：价税合计、未核销 */
    public Map<String, BigDecimal> summary(ArQuery q) {
        LambdaQueryWrapper<FinReceivableDO> w = query(q);
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal open = BigDecimal.ZERO;
        if (w != null) {
            for (FinReceivableDO r : mapper.selectScopedList(w)) {
                total = total.add(FinSupport.nz(r.getTotalAmountBase()));
                open = open.add(openBase(r));
            }
        }
        return Map.of("totalAmountBase", total, "unverifiedBase", Decimals.amount(open));
    }

    /** 未核销本位币 = 本位币 × 未核销 ÷ 价税合计 */
    public static BigDecimal openBase(FinReceivableDO r) {
        BigDecimal total = FinSupport.nz(r.getTotalAmount());
        if (total.signum() == 0) return BigDecimal.ZERO;
        BigDecimal open = total.subtract(FinSupport.nz(r.getVerifiedAmount()));
        if (open.compareTo(total) == 0) return FinSupport.nz(r.getTotalAmountBase());
        return Decimals.amount(FinSupport.nz(r.getTotalAmountBase()).multiply(open).divide(total, 6, java.math.RoundingMode.HALF_UP));
    }

    private LambdaQueryWrapper<FinReceivableDO> query(ArQuery q) {
        List<Long> byOrder = null;
        if (StringUtils.hasText(q.getOrderNo())) {
            byOrder = lineMapper.selectList(new LambdaQueryWrapper<FinReceivableLineDO>().like(FinReceivableLineDO::getOrderNo, q.getOrderNo().trim()))
                    .stream().map(FinReceivableLineDO::getReceivableId).distinct().toList();
            if (byOrder.isEmpty()) return null;
        }
        List<String> types = split(q.getArTypes());
        List<String> statuses = split(q.getStatuses());
        LambdaQueryWrapper<FinReceivableDO> w = new LambdaQueryWrapper<FinReceivableDO>().eq(FinReceivableDO::getDeleted, false)
                .like(StringUtils.hasText(q.getDocNo()), FinReceivableDO::getDocNo, q.getDocNo())
                .eq(q.getCustomerId() != null, FinReceivableDO::getCustomerId, q.getCustomerId())
                .in(!types.isEmpty(), FinReceivableDO::getArType, types)
                .in(!statuses.isEmpty(), FinReceivableDO::getArStatus, statuses)
                .ge(q.getBizDateFrom() != null, FinReceivableDO::getBizDate, q.getBizDateFrom())
                .le(q.getBizDateTo() != null, FinReceivableDO::getBizDate, q.getBizDateTo())
                .like(StringUtils.hasText(q.getSourceNo()), FinReceivableDO::getSourceNo, q.getSourceNo())
                .eq(StringUtils.hasText(q.getCurrency()), FinReceivableDO::getCurrency, q.getCurrency())
                .in(byOrder != null, FinReceivableDO::getId, byOrder)
                .ge(q.getDueFrom() != null, FinReceivableDO::getDueDate, q.getDueFrom())
                .le(q.getDueTo() != null, FinReceivableDO::getDueDate, q.getDueTo());
        state(w, q.getVerifyState(), "verified_amount");
        state(w, q.getInvoiceState(), "invoiced_amount");
        if (Boolean.TRUE.equals(q.getOverdueOnly())) {
            w.eq(FinReceivableDO::getArStatus, ArStatus.CONFIRMED.name()).lt(FinReceivableDO::getDueDate, LocalDate.now())
                    .apply("verified_amount <> total_amount").gt(FinReceivableDO::getTotalAmount, 0);
        }
        return w.orderByDesc(FinReceivableDO::getBizDate).orderByDesc(FinReceivableDO::getId);
    }

    private static void state(LambdaQueryWrapper<FinReceivableDO> w, String state, String column) {
        if (!StringUtils.hasText(state)) return;
        switch (state) {
            case "NONE" -> w.apply(column + " = 0");
            case "PARTIAL" -> w.apply(column + " <> 0 AND " + column + " <> total_amount");
            case "FULL" -> w.apply(column + " = total_amount");
            case "OPEN" -> w.apply(column + " <> total_amount");
            default -> {
            }
        }
    }

    static List<String> split(String text) {
        return StringUtils.hasText(text) ? Arrays.stream(text.split(",")).map(String::trim).filter(StringUtils::hasText).toList() : List.of();
    }

    private List<ArRow> rows(List<FinReceivableDO> list) {
        Map<Long, CustomerDTO> cs = support.customers(list.stream().map(FinReceivableDO::getCustomerId).toList());
        Map<Long, UserDTO> users = support.users(list.stream().map(FinReceivableDO::getOwnerId).toList());
        return list.stream().map(r -> row(r, cs, users)).toList();
    }

    private static ArRow row(FinReceivableDO r, Map<Long, CustomerDTO> cs, Map<Long, UserDTO> users) {
        BigDecimal open = FinSupport.nz(r.getTotalAmount()).subtract(FinSupport.nz(r.getVerifiedAmount()));
        int overdue = 0;
        if (ArStatus.CONFIRMED.name().equals(r.getArStatus()) && r.getDueDate() != null && open.signum() > 0 && r.getDueDate().isBefore(LocalDate.now())) {
            overdue = (int) ChronoUnit.DAYS.between(r.getDueDate(), LocalDate.now());
        }
        return new ArRow(r.getId(), r.getDocNo(), r.getArType(), r.getCustomerId(), FinSupport.customerName(cs.get(r.getCustomerId())), r.getSourceType(),
                r.getSourceId(), r.getSourceNo(), r.getBizDate(), r.getCurrency(), r.getExchangeRate(), r.getTotalAmount(), r.getTotalAmountBase(),
                r.getVerifiedAmount(), open, r.getInvoicedAmount(), r.getDueDate(), overdue, r.getArStatus(), FinSupport.name(users, r.getOwnerId()),
                r.getDescription(), r.getCreatedAt());
    }

    public ArDetail detail(Long id) {
        FinReceivableDO r = get(id);
        ArRow row = rows(List.of(r)).get(0);
        List<FinReceivableLineDO> lines = lineMapper.selectByParent(id);
        Map<Long, MaterialDTO> ms = support.materials(lines.stream().map(FinReceivableLineDO::getMaterialId).toList());
        List<ArLineVO> lineVOs = lines.stream().map(l -> {
            MaterialDTO m = ms.get(l.getMaterialId());
            return new ArLineVO(l.getId(), l.getLineNo(), l.getOrderId(), l.getOrderNo(), l.getOrderLineId(), l.getMaterialId(), m == null ? null : m.code(),
                    m == null ? null : m.name(), l.getDescription(), l.getQty(), l.getPriceInclTax(), l.getTaxRate(), l.getAmount(), l.getTaxAmount(),
                    l.getTotalAmount(), l.getInvoicedQty(), l.getInvoicedAmount());
        }).toList();
        List<FinSalesInvoiceLineDO> ils = invoiceLineMapper.selectList(new LambdaQueryWrapper<FinSalesInvoiceLineDO>().eq(FinSalesInvoiceLineDO::getReceivableId, id));
        Map<Long, FinSalesInvoiceDO> invs = ils.isEmpty() ? Map.of() : invoiceMapper.selectBatchIds(ils.stream().map(FinSalesInvoiceLineDO::getInvoiceId)
                .distinct().toList()).stream().collect(Collectors.toMap(FinSalesInvoiceDO::getId, x -> x));
        Map<Long, List<FinSalesInvoiceLineDO>> byInvoice = ils.stream().collect(Collectors.groupingBy(FinSalesInvoiceLineDO::getInvoiceId));
        List<InvoiceRef> invoices = byInvoice.entrySet().stream().filter(en -> invs.containsKey(en.getKey())).map(en -> {
            FinSalesInvoiceDO inv = invs.get(en.getKey());
            return new InvoiceRef(inv.getId(), inv.getDocNo(), inv.getInvoiceNo(), inv.getInvoiceType(), inv.getInvoiceDate(),
                    en.getValue().stream().map(x -> FinSupport.nz(x.getQty())).reduce(BigDecimal.ZERO, BigDecimal::add),
                    en.getValue().stream().map(FinSalesInvoiceLineDO::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add), inv.getInvoiceStatus());
        }).toList();
        return new ArDetail(row, r.getAmount(), r.getTaxAmount(), r.getPaymentTermId(), r.getOrderId(), r.getBlDate(), r.getConfirmedAt(), r.getVoidReason(),
                r.getVoucherId(), r.getRemark(), lineVOs, verificationService.listByDoc(VerificationService.DOC_RECEIVABLE, id), invoices);
    }
}
