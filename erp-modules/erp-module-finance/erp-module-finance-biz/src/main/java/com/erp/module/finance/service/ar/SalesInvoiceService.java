package com.erp.module.finance.service.ar;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.common.exception.BizException;
import com.erp.common.result.PageResult;
import com.erp.common.util.Decimals;
import com.erp.module.crm.api.customer.CustomerDTO;
import com.erp.module.engineering.api.material.MaterialDTO;
import com.erp.module.finance.api.FinanceErrorCodes;
import com.erp.module.finance.api.invoice.InvoiceIssuedEvent;
import com.erp.module.finance.config.FinanceModuleConfig;
import com.erp.module.finance.controller.vo.ArVOs.InvoiceDetail;
import com.erp.module.finance.controller.vo.ArVOs.InvoiceLineReq;
import com.erp.module.finance.controller.vo.ArVOs.InvoiceLineVO;
import com.erp.module.finance.controller.vo.ArVOs.InvoiceQuery;
import com.erp.module.finance.controller.vo.ArVOs.InvoiceRow;
import com.erp.module.finance.controller.vo.ArVOs.InvoiceSave;
import com.erp.module.finance.controller.vo.ArVOs.UninvoicedLine;
import com.erp.module.finance.dal.dataobject.FinReceivableDO;
import com.erp.module.finance.dal.dataobject.FinReceivableLineDO;
import com.erp.module.finance.dal.dataobject.FinSalesInvoiceDO;
import com.erp.module.finance.dal.dataobject.FinSalesInvoiceLineDO;
import com.erp.module.finance.dal.mapper.FinReceivableLineMapper;
import com.erp.module.finance.dal.mapper.FinReceivableMapper;
import com.erp.module.finance.dal.mapper.FinSalesInvoiceLineMapper;
import com.erp.module.finance.dal.mapper.FinSalesInvoiceMapper;
import com.erp.module.finance.service.ArStatus;
import com.erp.module.finance.service.FinSupport;
import com.erp.module.sales.api.order.SalesOrderWritebackApi;
import com.erp.module.system.api.user.UserDTO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 销项发票登记（12-02 第 3.4 节）：选择应收行部分开票，回写应收已开票与销售订单已开票数量 */
@Service
public class SalesInvoiceService {

    static final String BIZ_TYPE = FinanceModuleConfig.SALES_INVOICE;
    static final String REGISTERED = "REGISTERED";
    static final String VOIDED = "VOIDED";
    /** 开票金额可调整尾差 */
    static final BigDecimal TAIL = BigDecimal.ONE;

    private final FinSalesInvoiceMapper mapper;
    private final FinSalesInvoiceLineMapper lineMapper;
    private final FinReceivableMapper arMapper;
    private final FinReceivableLineMapper arLineMapper;
    private final SalesOrderWritebackApi writebackApi;
    private final FinSupport support;

    public SalesInvoiceService(FinSalesInvoiceMapper mapper, FinSalesInvoiceLineMapper lineMapper, FinReceivableMapper arMapper,
                               FinReceivableLineMapper arLineMapper, SalesOrderWritebackApi writebackApi, FinSupport support) {
        this.mapper = mapper;
        this.lineMapper = lineMapper;
        this.arMapper = arMapper;
        this.arLineMapper = arLineMapper;
        this.writebackApi = writebackApi;
        this.support = support;
    }

    /** 可开票应收行：客户已确认、未完全开票的蓝字应收（可限定应收单） */
    public List<UninvoicedLine> uninvoiced(Long customerId, String currency, List<Long> receivableIds) {
        List<FinReceivableDO> ars = arMapper.selectList(new LambdaQueryWrapper<FinReceivableDO>().eq(FinReceivableDO::getCustomerId, customerId)
                .eq(StringUtils.hasText(currency), FinReceivableDO::getCurrency, currency)
                .in(receivableIds != null && !receivableIds.isEmpty(), FinReceivableDO::getId, receivableIds)
                .eq(FinReceivableDO::getArStatus, ArStatus.CONFIRMED.name()).gt(FinReceivableDO::getTotalAmount, 0)
                .apply("invoiced_amount < total_amount").orderByAsc(FinReceivableDO::getBizDate).orderByAsc(FinReceivableDO::getId));
        Map<Long, FinReceivableDO> byId = ars.stream().collect(Collectors.toMap(FinReceivableDO::getId, Function.identity()));
        List<FinReceivableLineDO> lines = arLineMapper.selectByParents(byId.keySet()).stream()
                .filter(l -> l.getTotalAmount().signum() > 0 && FinSupport.nz(l.getInvoicedAmount()).compareTo(l.getTotalAmount()) < 0).toList();
        Map<Long, MaterialDTO> ms = support.materials(lines.stream().map(FinReceivableLineDO::getMaterialId).toList());
        List<UninvoicedLine> result = new ArrayList<>();
        for (FinReceivableDO r : ars) {
            for (FinReceivableLineDO l : lines) {
                if (!l.getReceivableId().equals(r.getId())) continue;
                MaterialDTO m = ms.get(l.getMaterialId());
                BigDecimal openQty = l.getQty() == null ? null : Decimals.qty(l.getQty().subtract(FinSupport.nz(l.getInvoicedQty())));
                result.add(new UninvoicedLine(r.getId(), r.getDocNo(), l.getId(), r.getSourceNo(), r.getBizDate(), r.getCurrency(), l.getOrderLineId(),
                        l.getOrderNo(), l.getMaterialId(), m == null ? null : m.code(), m == null ? null : m.name(), l.getDescription(), l.getQty(),
                        l.getPriceInclTax(), l.getTaxRate(), l.getTotalAmount(), l.getInvoicedQty(), openQty,
                        l.getTotalAmount().subtract(FinSupport.nz(l.getInvoicedAmount()))));
            }
        }
        return result;
    }

    /** FIN-AR-R08：开票数量 ≤ 未开票数量；同一发票号码不重复；金额按数量比例计算，可调整尾差 ≤ 1 元 */
    @Transactional(rollbackFor = Exception.class)
    public Long register(InvoiceSave req) {
        String invoiceNo = req.invoiceNo().trim();
        if (mapper.selectCount(new LambdaQueryWrapper<FinSalesInvoiceDO>().eq(FinSalesInvoiceDO::getInvoiceNo, invoiceNo)
                .ne(FinSalesInvoiceDO::getInvoiceStatus, VOIDED)) > 0) {
            throw BizException.of(FinanceErrorCodes.AR_INVOICE_NO_DUPLICATE, invoiceNo);
        }
        List<InvoiceLineReq> reqLines = req.lines() == null ? List.of() : req.lines().stream().filter(l -> l.qty() != null && l.qty().signum() > 0).toList();
        if (reqLines.isEmpty()) throw new BizException(FinanceErrorCodes.NO_LINES);
        Map<Long, FinReceivableLineDO> arLines = arLineMapper.selectBatchIds(reqLines.stream().map(InvoiceLineReq::receivableLineId).toList())
                .stream().collect(Collectors.toMap(FinReceivableLineDO::getId, Function.identity()));
        Map<Long, FinReceivableDO> ars = arMapper.selectBatchIds(arLines.values().stream().map(FinReceivableLineDO::getReceivableId).distinct().toList())
                .stream().collect(Collectors.toMap(FinReceivableDO::getId, Function.identity()));
        Set<String> currencies = new HashSet<>();
        for (FinReceivableDO r : ars.values()) {
            if (!r.getCustomerId().equals(req.customerId())) throw new BizException(FinanceErrorCodes.AR_CUSTOMER_MIXED);
            if (!ArStatus.CONFIRMED.name().equals(r.getArStatus())) throw BizException.of(FinanceErrorCodes.STATUS_NOT_ALLOWED, ArStatus.valueOf(r.getArStatus()).label(), "开票");
            currencies.add(r.getCurrency());
        }
        if (currencies.size() != 1) throw new BizException(FinanceErrorCodes.AR_CUSTOMER_MIXED);
        FinSalesInvoiceDO inv = new FinSalesInvoiceDO();
        inv.setDocNo(support.nextNo(BIZ_TYPE));
        inv.setCustomerId(req.customerId());
        inv.setInvoiceType(req.invoiceType());
        inv.setInvoiceNo(invoiceNo);
        inv.setInvoiceDate(req.invoiceDate());
        inv.setCurrency(currencies.iterator().next());
        inv.setInvoiceStatus(REGISTERED);
        inv.setRemark(FinSupport.trim(req.remark()));
        inv.setAmount(BigDecimal.ZERO);
        inv.setTaxAmount(BigDecimal.ZERO);
        inv.setTotalAmount(BigDecimal.ZERO);
        mapper.insert(inv);
        int no = 1;
        List<InvoiceIssuedEvent.Line> evLines = new ArrayList<>();
        Map<Long, BigDecimal> arDelta = new HashMap<>();
        BigDecimal[] sum = {BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO};
        for (InvoiceLineReq lr : reqLines) {
            FinReceivableLineDO l = arLines.get(lr.receivableLineId());
            if (l == null || !ars.containsKey(l.getReceivableId()) || l.getTotalAmount().signum() <= 0) throw BizException.of(FinanceErrorCodes.NOT_EXISTS, "应收行");
            BigDecimal qty = Decimals.qty(lr.qty());
            BigDecimal openAmount = l.getTotalAmount().subtract(FinSupport.nz(l.getInvoicedAmount()));
            BigDecimal total;
            if (l.getQty() != null && l.getQty().signum() > 0) {
                BigDecimal openQty = l.getQty().subtract(FinSupport.nz(l.getInvoicedQty()));
                if (qty.compareTo(openQty) > 0) throw BizException.of(FinanceErrorCodes.AR_INVOICE_QTY_EXCEED, no, Decimals.qty(openQty).stripTrailingZeros().toPlainString());
                BigDecimal calc = qty.compareTo(openQty) == 0 ? openAmount
                        : l.getTotalAmount().multiply(qty).divide(l.getQty(), Decimals.AMOUNT_SCALE, RoundingMode.HALF_UP);
                total = lr.totalAmount() == null ? calc : Decimals.amount(lr.totalAmount());
                BigDecimal diff = total.subtract(calc).abs();
                if (diff.compareTo(TAIL) > 0) throw BizException.of(FinanceErrorCodes.AR_INVOICE_DIFF, FinSupport.plain(diff));
            } else {
                // 无数量行（其他应收）：数量视为比例 1，金额取输入或未开票金额
                total = lr.totalAmount() == null ? openAmount : Decimals.amount(lr.totalAmount());
                qty = null;
            }
            if (total.compareTo(openAmount.add(TAIL)) > 0) throw BizException.of(FinanceErrorCodes.AR_INVOICE_QTY_EXCEED, no, FinSupport.plain(openAmount));
            BigDecimal[] s = FinSupport.split(total, l.getTaxRate());
            FinSalesInvoiceLineDO x = new FinSalesInvoiceLineDO();
            x.setInvoiceId(inv.getId());
            x.setLineNo(no++);
            x.setReceivableId(l.getReceivableId());
            x.setReceivableLineId(l.getId());
            x.setOrderLineId(l.getOrderLineId());
            x.setMaterialId(l.getMaterialId());
            x.setQty(qty);
            x.setAmount(s[0]);
            x.setTaxAmount(s[1]);
            x.setTotalAmount(s[2]);
            lineMapper.insert(x);
            sum[0] = sum[0].add(s[0]);
            sum[1] = sum[1].add(s[1]);
            sum[2] = sum[2].add(s[2]);
            l.setInvoicedQty(qty == null ? l.getInvoicedQty() : Decimals.qty(FinSupport.nz(l.getInvoicedQty()).add(qty)));
            l.setInvoicedAmount(Decimals.amount(FinSupport.nz(l.getInvoicedAmount()).add(total)));
            arLineMapper.updateByIdOrFail(l);
            arDelta.merge(l.getReceivableId(), total, BigDecimal::add);
            if (l.getOrderLineId() != null && qty != null) {
                writebackApi.onInvoiced(l.getOrderLineId(), qty, req.invoiceDate());
                evLines.add(new InvoiceIssuedEvent.Line(l.getOrderLineId(), qty, total));
            }
        }
        inv.setAmount(sum[0]);
        inv.setTaxAmount(sum[1]);
        inv.setTotalAmount(sum[2]);
        mapper.updateByIdOrFail(inv);
        applyAr(arDelta, 1, "开票登记 " + invoiceNo);
        support.bindFiles(req.fileIds(), BIZ_TYPE, inv.getId());
        support.log(BIZ_TYPE, inv.getId(), inv.getDocNo(), "REGISTER", "开票登记", null, REGISTERED, invoiceNo);
        support.events().publish(new InvoiceIssuedEvent(inv.getId(), invoiceNo, req.customerId(), req.invoiceDate(), evLines));
        return inv.getId();
    }

    /** 作废（或红冲）：回退应收已开票与订单已开票数量 */
    @Transactional(rollbackFor = Exception.class)
    public void voidInvoice(Long id, String reason, boolean red) {
        String why = FinSupport.requireText(reason, red ? "红冲原因" : "作废原因");
        FinSalesInvoiceDO inv = get(id);
        if (!REGISTERED.equals(inv.getInvoiceStatus())) throw BizException.of(FinanceErrorCodes.STATUS_NOT_ALLOWED, inv.getInvoiceStatus(), "作废");
        Map<Long, BigDecimal> arDelta = new HashMap<>();
        for (FinSalesInvoiceLineDO x : lineMapper.selectByParent(id)) {
            FinReceivableLineDO l = arLineMapper.selectById(x.getReceivableLineId());
            if (l != null) {
                if (x.getQty() != null) l.setInvoicedQty(Decimals.qty(FinSupport.nz(l.getInvoicedQty()).subtract(x.getQty())));
                l.setInvoicedAmount(Decimals.amount(FinSupport.nz(l.getInvoicedAmount()).subtract(x.getTotalAmount())));
                arLineMapper.updateByIdOrFail(l);
            }
            arDelta.merge(x.getReceivableId(), x.getTotalAmount(), BigDecimal::add);
            if (x.getOrderLineId() != null && x.getQty() != null) writebackApi.onInvoiced(x.getOrderLineId(), x.getQty().negate(), inv.getInvoiceDate());
        }
        applyAr(arDelta, -1, (red ? "发票红冲 " : "发票作废 ") + inv.getInvoiceNo());
        String to = red ? "RED" : VOIDED;
        inv.setInvoiceStatus(to);
        inv.setVoidReason(FinSupport.limit(why, 256));
        mapper.updateByIdOrFail(inv);
        support.log(BIZ_TYPE, id, inv.getDocNo(), red ? "RED" : "VOID", red ? "红冲" : "作废", REGISTERED, to, why);
    }

    private void applyAr(Map<Long, BigDecimal> delta, int sign, String reason) {
        for (Map.Entry<Long, BigDecimal> e : delta.entrySet()) {
            FinReceivableDO r = arMapper.selectById(e.getKey());
            r.setInvoicedAmount(Decimals.amount(FinSupport.nz(r.getInvoicedAmount()).add(e.getValue().multiply(BigDecimal.valueOf(sign)))));
            arMapper.updateByIdOrFail(r);
            support.log(ReceivableService.BIZ_TYPE, r.getId(), r.getDocNo(), "INVOICE", sign > 0 ? "开票" : "取消开票", null, null,
                    reason + "，金额 " + FinSupport.plain(e.getValue()));
        }
    }

    public FinSalesInvoiceDO get(Long id) {
        FinSalesInvoiceDO inv = id == null ? null : mapper.selectById(id);
        if (inv == null) throw BizException.of(FinanceErrorCodes.NOT_EXISTS, "发票登记");
        return inv;
    }

    public PageResult<InvoiceRow> page(InvoiceQuery q) {
        IPage<FinSalesInvoiceDO> p = mapper.selectPage(new Page<>(q.getPageNo(), q.getPageSize()), query(q));
        return new PageResult<>(rows(p.getRecords()), p.getTotal());
    }

    public List<InvoiceRow> list(InvoiceQuery q) {
        return rows(mapper.selectList(query(q)));
    }

    private LambdaQueryWrapper<FinSalesInvoiceDO> query(InvoiceQuery q) {
        return new LambdaQueryWrapper<FinSalesInvoiceDO>()
                .and(StringUtils.hasText(q.getKeyword()), w -> w.like(FinSalesInvoiceDO::getInvoiceNo, q.getKeyword()).or().like(FinSalesInvoiceDO::getDocNo, q.getKeyword()))
                .eq(q.getCustomerId() != null, FinSalesInvoiceDO::getCustomerId, q.getCustomerId())
                .eq(StringUtils.hasText(q.getInvoiceType()), FinSalesInvoiceDO::getInvoiceType, q.getInvoiceType())
                .eq(StringUtils.hasText(q.getStatus()), FinSalesInvoiceDO::getInvoiceStatus, q.getStatus())
                .ge(q.getDateFrom() != null, FinSalesInvoiceDO::getInvoiceDate, q.getDateFrom())
                .le(q.getDateTo() != null, FinSalesInvoiceDO::getInvoiceDate, q.getDateTo())
                .orderByDesc(FinSalesInvoiceDO::getInvoiceDate).orderByDesc(FinSalesInvoiceDO::getId);
    }

    private List<InvoiceRow> rows(List<FinSalesInvoiceDO> list) {
        Map<Long, CustomerDTO> cs = support.customers(list.stream().map(FinSalesInvoiceDO::getCustomerId).toList());
        Map<Long, UserDTO> users = support.users(list.stream().map(FinSalesInvoiceDO::getCreatedBy).toList());
        return list.stream().map(i -> new InvoiceRow(i.getId(), i.getDocNo(), i.getCustomerId(), FinSupport.customerName(cs.get(i.getCustomerId())),
                i.getInvoiceType(), i.getInvoiceNo(), i.getInvoiceDate(), i.getCurrency(), i.getAmount(), i.getTaxAmount(), i.getTotalAmount(),
                i.getInvoiceStatus(), i.getVoidReason(), i.getRemark(), FinSupport.name(users, i.getCreatedBy()), i.getCreatedAt())).toList();
    }

    public InvoiceDetail detail(Long id) {
        FinSalesInvoiceDO inv = get(id);
        List<FinSalesInvoiceLineDO> lines = lineMapper.selectByParent(id);
        Map<Long, FinReceivableDO> ars = lines.isEmpty() ? Map.of() : arMapper.selectBatchIds(lines.stream().map(FinSalesInvoiceLineDO::getReceivableId)
                .distinct().toList()).stream().collect(Collectors.toMap(FinReceivableDO::getId, Function.identity()));
        Map<Long, FinReceivableLineDO> arLines = lines.isEmpty() ? Map.of() : arLineMapper.selectBatchIds(lines.stream()
                .map(FinSalesInvoiceLineDO::getReceivableLineId).toList()).stream().collect(Collectors.toMap(FinReceivableLineDO::getId, Function.identity()));
        Map<Long, MaterialDTO> ms = support.materials(lines.stream().map(FinSalesInvoiceLineDO::getMaterialId).toList());
        List<InvoiceLineVO> vos = lines.stream().map(x -> {
            MaterialDTO m = ms.get(x.getMaterialId());
            FinReceivableDO r = ars.get(x.getReceivableId());
            FinReceivableLineDO l = arLines.get(x.getReceivableLineId());
            return new InvoiceLineVO(x.getId(), x.getLineNo(), x.getReceivableId(), r == null ? null : r.getDocNo(), x.getReceivableLineId(),
                    x.getOrderLineId(), l == null ? null : l.getOrderNo(), x.getMaterialId(), m == null ? null : m.code(), m == null ? null : m.name(),
                    x.getQty(), x.getAmount(), x.getTaxAmount(), x.getTotalAmount());
        }).toList();
        return new InvoiceDetail(rows(List.of(inv)).get(0), vos);
    }
}
