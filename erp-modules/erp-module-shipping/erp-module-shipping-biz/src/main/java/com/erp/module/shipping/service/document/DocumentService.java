package com.erp.module.shipping.service.document;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.common.exception.BizException;
import com.erp.common.result.PageResult;
import com.erp.common.util.Decimals;
import com.erp.framework.mybatis.BaseDO;
import com.erp.module.crm.api.customer.AddressDTO;
import com.erp.module.crm.api.customer.CustomerDTO;
import com.erp.module.engineering.api.material.MaterialDTO;
import com.erp.module.engineering.api.material.MaterialStockAttr;
import com.erp.module.sales.api.order.SalesOrderHeaderDTO;
import com.erp.module.sales.api.order.SalesOrderQueryApi;
import com.erp.module.shipping.api.ShippingErrorCodes;
import com.erp.module.shipping.config.ShippingModuleConfig;
import com.erp.module.shipping.controller.vo.DocumentVOs.CustomsDetail;
import com.erp.module.shipping.controller.vo.DocumentVOs.CustomsItemEdit;
import com.erp.module.shipping.controller.vo.DocumentVOs.CustomsItemVO;
import com.erp.module.shipping.controller.vo.DocumentVOs.CustomsSave;
import com.erp.module.shipping.controller.vo.DocumentVOs.DocQuery;
import com.erp.module.shipping.controller.vo.DocumentVOs.DocRow;
import com.erp.module.shipping.controller.vo.DocumentVOs.InvoiceDetail;
import com.erp.module.shipping.controller.vo.DocumentVOs.InvoiceLineEdit;
import com.erp.module.shipping.controller.vo.DocumentVOs.InvoiceLineVO;
import com.erp.module.shipping.controller.vo.DocumentVOs.InvoiceSave;
import com.erp.module.shipping.controller.vo.DocumentVOs.PackingListDetail;
import com.erp.module.shipping.controller.vo.DocumentVOs.PackingListSave;
import com.erp.module.shipping.controller.vo.DocumentVOs.PlLine;
import com.erp.module.shipping.controller.vo.DocumentVOs.PlLineEdit;
import com.erp.module.shipping.controller.vo.DocumentVOs.PlTotals;
import com.erp.module.shipping.controller.vo.PackingVOs.CartonLineVO;
import com.erp.module.shipping.controller.vo.PackingVOs.CartonVO;
import com.erp.module.shipping.dal.dataobject.ShpCustomsDO;
import com.erp.module.shipping.dal.dataobject.ShpCustomsItemDO;
import com.erp.module.shipping.dal.dataobject.ShpInvoiceDO;
import com.erp.module.shipping.dal.dataobject.ShpInvoiceLineDO;
import com.erp.module.shipping.dal.dataobject.ShpNoticeDO;
import com.erp.module.shipping.dal.dataobject.ShpPackingListDO;
import com.erp.module.shipping.dal.dataobject.ShpShipmentDO;
import com.erp.module.shipping.dal.dataobject.ShpShipmentLineDO;
import com.erp.module.shipping.dal.mapper.ShpCustomsItemMapper;
import com.erp.module.shipping.dal.mapper.ShpCustomsMapper;
import com.erp.module.shipping.dal.mapper.ShpInvoiceLineMapper;
import com.erp.module.shipping.dal.mapper.ShpInvoiceMapper;
import com.erp.module.shipping.dal.mapper.ShpNoticeMapper;
import com.erp.module.shipping.dal.mapper.ShpPackingListMapper;
import com.erp.module.shipping.dal.mapper.ShpShipmentLineMapper;
import com.erp.module.shipping.dal.mapper.ShpShipmentMapper;
import com.erp.module.shipping.service.ShipmentStatus;
import com.erp.module.shipping.service.ShpSupport;
import com.erp.module.shipping.service.shipment.ShipmentService;
import com.erp.module.system.api.org.OrgDTO;
import com.erp.module.system.api.paymentterm.PaymentTermApi;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 出货单证：Packing List、Commercial Invoice、报关资料（11-04） */
@Service
public class DocumentService {

    /** 收款银行信息（财务维护，与销售 PI 共用） */
    private static final String BANK_INFO_PARAM = "sal.print.bank-info";

    private final ShpPackingListMapper plMapper;
    private final ShpInvoiceMapper invoiceMapper;
    private final ShpInvoiceLineMapper invoiceLineMapper;
    private final ShpCustomsMapper customsMapper;
    private final ShpCustomsItemMapper customsItemMapper;
    private final ShpShipmentMapper shipmentMapper;
    private final ShpShipmentLineMapper shipmentLineMapper;
    private final ShpNoticeMapper noticeMapper;
    private final ShipmentService shipmentService;
    private final SalesOrderQueryApi orderQueryApi;
    private final PaymentTermApi paymentTermApi;
    private final ShpSupport support;

    public DocumentService(ShpPackingListMapper plMapper, ShpInvoiceMapper invoiceMapper, ShpInvoiceLineMapper invoiceLineMapper, ShpCustomsMapper customsMapper,
                           ShpCustomsItemMapper customsItemMapper, ShpShipmentMapper shipmentMapper, ShpShipmentLineMapper shipmentLineMapper,
                           ShpNoticeMapper noticeMapper, ShipmentService shipmentService, SalesOrderQueryApi orderQueryApi, PaymentTermApi paymentTermApi,
                           ShpSupport support) {
        this.plMapper = plMapper;
        this.invoiceMapper = invoiceMapper;
        this.invoiceLineMapper = invoiceLineMapper;
        this.customsMapper = customsMapper;
        this.customsItemMapper = customsItemMapper;
        this.shipmentMapper = shipmentMapper;
        this.shipmentLineMapper = shipmentLineMapper;
        this.noticeMapper = noticeMapper;
        this.shipmentService = shipmentService;
        this.orderQueryApi = orderQueryApi;
        this.paymentTermApi = paymentTermApi;
        this.support = support;
    }

    private ShpShipmentDO shipmentForDoc(Long shipmentId) {
        ShpShipmentDO s = shipmentService.get(shipmentId);
        String st = s.getShipmentStatus();
        if (ShipmentStatus.DRAFT.name().equals(st) || ShipmentStatus.PENDING.name().equals(st) || ShipmentStatus.VOIDED.name().equals(st)) {
            throw new BizException(ShippingErrorCodes.DOC_SHIPMENT_DRAFT);
        }
        return s;
    }

    private static String englishName(CustomerDTO c) {
        return StringUtils.hasText(c.nameEn()) ? c.nameEn() : c.name();
    }

    private static String addressLine(String head, AddressDTO a) {
        StringBuilder sb = new StringBuilder(head);
        for (String x : new String[]{a.addressLine(), a.city(), a.province(), a.zip(), a.country()}) {
            if (StringUtils.hasText(x)) sb.append(", ").append(x);
        }
        return sb.toString();
    }

    /** 收货人：收货地址公司名 + 地址 */
    private String consignee(ShpShipmentDO s, CustomerDTO c) {
        AddressDTO a = support.address(s.getShipToSnapshot());
        if (a == null) return englishName(c);
        return addressLine(StringUtils.hasText(a.companyName()) ? a.companyName() : englishName(c), a);
    }

    private String notifyParty(ShpShipmentDO s) {
        ShpNoticeDO n = s.getNoticeId() == null ? null : noticeMapper.selectById(s.getNoticeId());
        return n == null ? null : n.getNotifyParty();
    }

    private String companyName(ShpShipmentDO s) {
        OrgDTO org = support.company(s.getOrgId());
        return org == null ? "" : StringUtils.hasText(org.nameEn()) ? org.nameEn() : org.name();
    }

    // ==================== Packing List ====================

    /** 从出货单生成 PL：箱号区间、每箱数量、毛净重、CBM 与装箱一致（SHP-DOC-T01） */
    @Transactional(rollbackFor = Exception.class)
    public Long createPackingList(Long shipmentId) {
        ShpShipmentDO s = shipmentForDoc(shipmentId);
        ShpPackingListDO exist = plMapper.selectOne(new LambdaQueryWrapper<ShpPackingListDO>().eq(ShpPackingListDO::getShipmentId, shipmentId).last("LIMIT 1"));
        if (exist != null) throw BizException.of(ShippingErrorCodes.DOC_EXISTS, "Packing List", exist.getPlNo());
        CustomerDTO c = support.customer(s.getCustomerId());
        List<PlLine> lines = plLines(s);
        PlTotals totals = plTotals(lines);
        ShpPackingListDO pl = new ShpPackingListDO();
        pl.setShipmentId(shipmentId);
        pl.setPlNo(support.nextNo(ShippingModuleConfig.PACKING_LIST));
        pl.setPlDate(s.getShipDate() != null ? s.getShipDate() : LocalDate.now());
        pl.setConsignee(ShpSupport.limit(consignee(s, c), 512));
        pl.setNotifyParty(ShpSupport.limit(notifyParty(s), 512));
        pl.setShippingMarks(ShpSupport.limit(englishName(c) + "\nC/NO. 1-" + totals.cartons() + "\nMADE IN CHINA", 1000));
        pl.setLineData(support.toJson(lines));
        pl.setTotalData(support.toJson(totals));
        pl.setInvalid(false);
        plMapper.insert(pl);
        ShpShipmentDO fresh = shipmentService.get(shipmentId);
        fresh.setPackingListId(pl.getId());
        shipmentMapper.updateByIdOrFail(fresh);
        support.log(ShippingModuleConfig.SHIPMENT, shipmentId, s.getDocNo(), "PACKING_LIST", "生成 Packing List", null, null, pl.getPlNo());
        return pl.getId();
    }

    /** 相同内容（单一物料 + 批次 + 数量 + 规格重量）的连续箱合并为“1-12”；混装箱逐行列出；未装箱时按出货单行 */
    List<PlLine> plLines(ShpShipmentDO s) {
        List<CartonVO> cartons = shipmentService.cartons(s.getId());
        List<ShpShipmentLineDO> sls = shipmentLineMapper.selectByParent(s.getId());
        Map<Long, MaterialDTO> ms = support.materials(sls.stream().map(ShpShipmentLineDO::getMaterialId).toList());
        Map<Long, String> descByNoticeLine = new HashMap<>();
        for (ShpShipmentLineDO l : sls) descByNoticeLine.putIfAbsent(l.getNoticeLineId(), description(l, ms.get(l.getMaterialId())));
        List<PlLine> out = new ArrayList<>();
        if (cartons.isEmpty()) {
            Map<Long, MaterialStockAttr> attrs = new HashMap<>();
            for (ShpShipmentLineDO l : sls) {
                MaterialStockAttr a = attrs.computeIfAbsent(l.getMaterialId(), id -> support.materialApi().getStockAttr(id));
                MaterialDTO m = ms.get(l.getMaterialId());
                BigDecimal nw = a == null || a.unitNetWeight() == null ? null : a.unitNetWeight().multiply(l.getBaseQty()).setScale(3, RoundingMode.HALF_UP);
                BigDecimal gw = a == null || a.unitGrossWeight() == null ? nw : a.unitGrossWeight().multiply(l.getBaseQty()).setScale(3, RoundingMode.HALF_UP);
                out.add(new PlLine("", description(l, m), partNo(l.getCustomerPartNo(), m), l.getBatchNo(), null, null, l.getBaseQty(),
                        m == null ? l.getUom() : m.baseUom(), nw, gw, null));
            }
            return out;
        }
        int i = 0;
        while (i < cartons.size()) {
            CartonVO c = cartons.get(i);
            if (c.lines().size() == 1) {
                int j = i + 1;
                while (j < cartons.size() && sameContent(c, cartons.get(j)) && cartons.get(j).cartonNo() == cartons.get(j - 1).cartonNo() + 1) j++;
                CartonLineVO l = c.lines().get(0);
                int n = j - i;
                MaterialDTO m = ms.get(l.materialId());
                String range = n == 1 ? String.valueOf(c.cartonNo()) : c.cartonNo() + "-" + cartons.get(j - 1).cartonNo();
                out.add(new PlLine(range, descByNoticeLine.getOrDefault(l.noticeLineId(), m == null ? null : m.name()), partNo(l.customerPartNo(), m),
                        l.batchNo(), l.qty(), n, l.qty().multiply(BigDecimal.valueOf(n)), m == null ? null : m.baseUom(), mul(c.netWeightKg(), n),
                        mul(c.grossWeightKg(), n), mul(c.cbm(), n)));
                i = j;
            } else {
                boolean first = true;
                for (CartonLineVO l : c.lines()) {
                    MaterialDTO m = ms.get(l.materialId());
                    out.add(new PlLine(first ? String.valueOf(c.cartonNo()) : "", descByNoticeLine.getOrDefault(l.noticeLineId(), m == null ? null : m.name()),
                            partNo(l.customerPartNo(), m), l.batchNo(), l.qty(), first ? 1 : null, l.qty(), m == null ? null : m.baseUom(),
                            first ? c.netWeightKg() : null, first ? c.grossWeightKg() : null, first ? c.cbm() : null));
                    first = false;
                }
                i++;
            }
        }
        return out;
    }

    private static boolean sameContent(CartonVO a, CartonVO b) {
        if (b.lines().size() != 1) return false;
        CartonLineVO x = a.lines().get(0);
        CartonLineVO y = b.lines().get(0);
        return Objects.equals(x.noticeLineId(), y.noticeLineId()) && Objects.equals(x.batchNo(), y.batchNo()) && x.qty().compareTo(y.qty()) == 0
                && eq(a.grossWeightKg(), b.grossWeightKg()) && eq(a.netWeightKg(), b.netWeightKg()) && eq(a.cbm(), b.cbm())
                && Objects.equals(a.cartonSpec(), b.cartonSpec());
    }

    private static boolean eq(BigDecimal a, BigDecimal b) {
        return a == null ? b == null : b != null && a.compareTo(b) == 0;
    }

    private static BigDecimal mul(BigDecimal v, int n) {
        return v == null ? null : v.multiply(BigDecimal.valueOf(n));
    }

    private static String partNo(String customerPartNo, MaterialDTO m) {
        return StringUtils.hasText(customerPartNo) ? customerPartNo : m == null ? null : m.code();
    }

    private static String description(ShpShipmentLineDO l, MaterialDTO m) {
        if (StringUtils.hasText(l.getDescription())) return l.getDescription();
        if (m == null) return null;
        return StringUtils.hasText(m.nameEn()) ? m.nameEn() : m.name();
    }

    static PlTotals plTotals(List<PlLine> lines) {
        int cartons = lines.stream().map(PlLine::cartons).filter(Objects::nonNull).mapToInt(Integer::intValue).sum();
        return new PlTotals(cartons, sum(lines, PlLine::qty), sum(lines, PlLine::netWeight), sum(lines, PlLine::grossWeight), sum(lines, PlLine::cbm));
    }

    private static <T> BigDecimal sum(List<T> list, Function<T, BigDecimal> f) {
        return list.stream().map(f).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public PackingListDetail packingList(Long id) {
        ShpPackingListDO pl = plMapper.selectById(id);
        if (pl == null) throw BizException.of(ShippingErrorCodes.NOT_EXISTS, "Packing List");
        ShpShipmentDO s = shipmentService.get(pl.getShipmentId());
        CustomerDTO c = support.customer(s.getCustomerId());
        List<PlLine> lines = support.fromJson(pl.getLineData(), new TypeReference<List<PlLine>>() {
        });
        PlTotals totals = support.fromJson(pl.getTotalData(), new TypeReference<PlTotals>() {
        });
        return new PackingListDetail(pl.getId(), pl.getPlNo(), pl.getPlDate(), s.getId(), s.getDocNo(), s.getShipmentStatus(), s.getCustomerId(),
                ShpSupport.customerName(c), pl.getConsignee(), pl.getNotifyParty(), pl.getShippingMarks(), lines == null ? List.of() : lines, totals,
                pl.getRemark(), Boolean.TRUE.equals(pl.getInvalid()), pl.getCreatedAt());
    }

    /** 可修改抬头、收货人、通知方、唛头、描述、料号、备注；数量与重量只读（SHP-DOC-R04 单号唯一） */
    @Transactional(rollbackFor = Exception.class)
    public void updatePackingList(Long id, PackingListSave req) {
        ShpPackingListDO pl = plMapper.selectById(id);
        if (pl == null) throw BizException.of(ShippingErrorCodes.NOT_EXISTS, "Packing List");
        String no = req.plNo().trim();
        if (!no.equals(pl.getPlNo()) && plMapper.selectCount(new LambdaQueryWrapper<ShpPackingListDO>().eq(ShpPackingListDO::getPlNo, no)) > 0) {
            throw BizException.of(ShippingErrorCodes.DOC_NO_DUPLICATE, no);
        }
        pl.setPlNo(ShpSupport.limit(no, 32));
        pl.setPlDate(req.plDate());
        pl.setConsignee(ShpSupport.limit(req.consignee(), 512));
        pl.setNotifyParty(ShpSupport.limit(req.notifyParty(), 512));
        pl.setShippingMarks(ShpSupport.limit(req.shippingMarks(), 1000));
        pl.setRemark(ShpSupport.limit(req.remark(), 1000));
        List<PlLine> lines = support.fromJson(pl.getLineData(), new TypeReference<List<PlLine>>() {
        });
        if (req.lines() != null && lines != null && lines.size() == req.lines().size()) {
            List<PlLine> edited = new ArrayList<>();
            for (int i = 0; i < lines.size(); i++) {
                PlLine l = lines.get(i);
                PlLineEdit e = req.lines().get(i);
                edited.add(new PlLine(l.cartonRange(), e.description() != null ? ShpSupport.limit(e.description(), 512) : l.description(),
                        e.partNo() != null ? ShpSupport.limit(e.partNo(), 64) : l.partNo(), l.batchNo(), l.qtyPerCarton(), l.cartons(), l.qty(), l.uom(),
                        l.netWeight(), l.grossWeight(), l.cbm()));
            }
            pl.setLineData(support.toJson(edited));
        }
        plMapper.updateByIdOrFail(pl);
    }

    public PageResult<DocRow> packingLists(DocQuery q) {
        List<Long> sids = shipmentFilter(q);
        if (sids != null && sids.isEmpty()) return new PageResult<>(List.of(), 0L);
        IPage<ShpPackingListDO> p = plMapper.selectPage(new Page<>(q.getPageNo(), q.getPageSize()), new LambdaQueryWrapper<ShpPackingListDO>()
                .like(StringUtils.hasText(q.getNo()), ShpPackingListDO::getPlNo, q.getNo())
                .in(sids != null, ShpPackingListDO::getShipmentId, sids)
                .ge(q.getDateFrom() != null, ShpPackingListDO::getPlDate, q.getDateFrom())
                .le(q.getDateTo() != null, ShpPackingListDO::getPlDate, q.getDateTo())
                .eq(q.getInvalid() != null, ShpPackingListDO::getInvalid, q.getInvalid())
                .orderByDesc(ShpPackingListDO::getId));
        Map<Long, ShpShipmentDO> ships = shipments(p.getRecords().stream().map(ShpPackingListDO::getShipmentId).toList());
        Map<Long, CustomerDTO> cus = support.customers(ships.values().stream().map(ShpShipmentDO::getCustomerId).toList());
        return new PageResult<>(p.getRecords().stream().map(pl -> {
            ShpShipmentDO s = ships.get(pl.getShipmentId());
            PlTotals t = support.fromJson(pl.getTotalData(), new TypeReference<PlTotals>() {
            });
            return new DocRow(pl.getId(), pl.getPlNo(), pl.getPlDate(), pl.getShipmentId(), s == null ? null : s.getDocNo(), s == null ? null : s.getCustomerId(),
                    s == null ? null : ShpSupport.customerName(cus.get(s.getCustomerId())), null, t == null ? null : t.qty(), t == null ? null : t.cartons(),
                    null, Boolean.TRUE.equals(pl.getInvalid()), pl.getCreatedAt());
        }).toList(), p.getTotal());
    }

    public Map<String, Object> packingListPrintData(Long id) {
        PackingListDetail d = packingList(id);
        ShpShipmentDO s = shipmentService.get(d.shipmentId());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("plNo", d.plNo());
        m.put("plDate", d.plDate());
        m.put("companyName", companyName(s));
        m.put("consignee", Objects.toString(d.consignee(), ""));
        m.put("notifyParty", Objects.toString(d.notifyParty(), ""));
        m.put("shippingMarks", Objects.toString(d.shippingMarks(), ""));
        m.put("invalid", d.invalid());
        m.put("lines", d.lines());
        m.put("totals", d.totals());
        return m;
    }

    // ==================== Commercial Invoice ====================

    @Transactional(rollbackFor = Exception.class)
    public Long createInvoice(Long shipmentId) {
        ShpShipmentDO s = shipmentForDoc(shipmentId);
        ShpInvoiceDO exist = invoiceMapper.selectOne(new LambdaQueryWrapper<ShpInvoiceDO>().eq(ShpInvoiceDO::getShipmentId, shipmentId).last("LIMIT 1"));
        if (exist != null) throw BizException.of(ShippingErrorCodes.DOC_EXISTS, "Invoice", exist.getInvoiceNo());
        CustomerDTO c = support.customer(s.getCustomerId());
        List<ShpShipmentLineDO> sls = shipmentLineMapper.selectByParent(shipmentId);
        Map<Long, MaterialDTO> ms = support.materials(sls.stream().map(ShpShipmentLineDO::getMaterialId).toList());
        Map<Long, SalesOrderHeaderDTO> heads = orderQueryApi.getOrderHeaders(sls.stream().map(ShpShipmentLineDO::getOrderId).distinct().toList());
        SalesOrderHeaderDTO h = sls.isEmpty() ? null : heads.get(sls.get(0).getOrderId());
        ShpInvoiceDO inv = new ShpInvoiceDO();
        inv.setShipmentId(shipmentId);
        inv.setInvoiceNo(support.nextNo(ShippingModuleConfig.INVOICE));
        inv.setInvoiceDate(s.getShipDate() != null ? s.getShipDate() : LocalDate.now());
        inv.setCustomerId(s.getCustomerId());
        inv.setBillTo(ShpSupport.limit(billTo(c, h), 512));
        inv.setConsignee(ShpSupport.limit(consignee(s, c), 512));
        inv.setNotifyParty(ShpSupport.limit(notifyParty(s), 512));
        inv.setCurrency(s.getCurrency());
        inv.setTradeTerm(s.getTradeTerm());
        inv.setPaymentTermText(ShpSupport.limit(paymentTerm(h, c), 256));
        inv.setPortOfLoading(s.getPortOfLoading());
        inv.setPortOfDestination(s.getPortOfDestination());
        inv.setTotalAmount(Decimals.amount(sls.stream().map(ShpShipmentLineDO::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add)));
        inv.setAmountInWords(AmountInWords.of(inv.getTotalAmount(), inv.getCurrency()));
        String bank = support.params().getString(BANK_INFO_PARAM);
        inv.setBankInfo(StringUtils.hasText(bank) ? ShpSupport.limit(bank.replace(";", "\n"), 1000) : null);
        inv.setInvalid(false);
        invoiceMapper.insert(inv);
        int no = 1;
        for (ShpShipmentLineDO l : sls) {
            MaterialDTO m = ms.get(l.getMaterialId());
            SalesOrderHeaderDTO oh = heads.get(l.getOrderId());
            ShpInvoiceLineDO il = new ShpInvoiceLineDO();
            il.setInvoiceId(inv.getId());
            il.setLineNo(no++);
            il.setShipmentLineId(l.getId());
            il.setOrderNo(l.getOrderNo());
            il.setCustomerPoNo(oh == null ? null : oh.customerPoNo());
            il.setCustomerPartNo(partNo(l.getCustomerPartNo(), m));
            il.setDescription(ShpSupport.limit(description(l, m), 512));
            il.setHsCode(m == null ? null : ShpSupport.trim(m.hsCode()));
            il.setOrigin("CHINA");
            il.setQty(l.getQty());
            il.setUom(l.getUom());
            il.setUnitPrice(l.getPriceInclTax());
            il.setAmount(l.getTotalAmount());
            invoiceLineMapper.insert(il);
        }
        ShpShipmentDO fresh = shipmentService.get(shipmentId);
        fresh.setInvoiceId(inv.getId());
        shipmentMapper.updateByIdOrFail(fresh);
        support.log(ShippingModuleConfig.SHIPMENT, shipmentId, s.getDocNo(), "INVOICE", "生成 Invoice", null, null, inv.getInvoiceNo());
        return inv.getId();
    }

    /** 开票抬头：订单开票地址，其次客户默认开票地址，都没有时取客户英文名 */
    private String billTo(CustomerDTO c, SalesOrderHeaderDTO h) {
        List<AddressDTO> bills = support.customerApi().getAddresses(c.id(), "BILL_TO");
        AddressDTO a = bills.stream().filter(x -> h != null && x.id().equals(h.billToAddressId())).findFirst()
                .orElse(bills.stream().filter(AddressDTO::isDefault).findFirst().orElse(bills.isEmpty() ? null : bills.get(0)));
        if (a == null) return englishName(c);
        return addressLine(StringUtils.hasText(a.companyName()) ? a.companyName() : englishName(c), a);
    }

    private String paymentTerm(SalesOrderHeaderDTO h, CustomerDTO c) {
        Long id = h != null && h.paymentTermId() != null ? h.paymentTermId() : c.paymentTermId();
        if (id == null) return null;
        return paymentTermApi.get(id).map(p -> StringUtils.hasText(p.nameEn()) ? p.nameEn() : p.name()).orElse(null);
    }

    public InvoiceDetail invoice(Long id) {
        ShpInvoiceDO inv = invoiceMapper.selectById(id);
        if (inv == null) throw BizException.of(ShippingErrorCodes.NOT_EXISTS, "Invoice");
        ShpShipmentDO s = shipmentService.get(inv.getShipmentId());
        CustomerDTO c = support.customer(inv.getCustomerId());
        boolean price = support.canSeePrice();
        List<InvoiceLineVO> lines = invoiceLineMapper.selectByParent(id).stream().map(l -> new InvoiceLineVO(l.getId(), l.getLineNo(), l.getShipmentLineId(),
                l.getOrderNo(), l.getCustomerPoNo(), l.getCustomerPartNo(), l.getDescription(), l.getHsCode(), l.getOrigin(), l.getQty(), l.getUom(),
                price ? l.getUnitPrice() : null, price ? l.getAmount() : null)).toList();
        return new InvoiceDetail(inv.getId(), inv.getInvoiceNo(), inv.getInvoiceDate(), s.getId(), s.getDocNo(), s.getShipmentStatus(), inv.getCustomerId(),
                ShpSupport.customerName(c), inv.getBillTo(), inv.getConsignee(), inv.getNotifyParty(), inv.getCurrency(), inv.getTradeTerm(),
                inv.getPaymentTermText(), inv.getPortOfLoading(), inv.getPortOfDestination(), inv.getVesselFlight(), price ? inv.getTotalAmount() : null,
                price ? inv.getAmountInWords() : null, inv.getBankInfo(), inv.getRemark(), Boolean.TRUE.equals(inv.getInvalid()), price, lines,
                inv.getCreatedAt());
    }

    /** SHP-DOC-R01、R02、R04：只改描述性字段；每行必须有 HS 编码；单号唯一 */
    @Transactional(rollbackFor = Exception.class)
    public void updateInvoice(Long id, InvoiceSave req) {
        ShpInvoiceDO inv = invoiceMapper.selectById(id);
        if (inv == null) throw BizException.of(ShippingErrorCodes.NOT_EXISTS, "Invoice");
        String no = req.invoiceNo().trim();
        if (!no.equals(inv.getInvoiceNo()) && invoiceMapper.selectCount(new LambdaQueryWrapper<ShpInvoiceDO>().eq(ShpInvoiceDO::getInvoiceNo, no)) > 0) {
            throw BizException.of(ShippingErrorCodes.DOC_NO_DUPLICATE, no);
        }
        Map<Long, InvoiceLineEdit> edits = req.lines() == null ? Map.of()
                : req.lines().stream().collect(Collectors.toMap(InvoiceLineEdit::id, e -> e, (a, b) -> b));
        List<ShpInvoiceLineDO> lines = invoiceLineMapper.selectByParent(id);
        for (ShpInvoiceLineDO l : lines) {
            InvoiceLineEdit e = edits.get(l.getId());
            if (e != null) {
                l.setCustomerPoNo(ShpSupport.limit(e.customerPoNo(), 64));
                l.setCustomerPartNo(ShpSupport.limit(e.customerPartNo(), 64));
                l.setDescription(ShpSupport.limit(e.description(), 512));
                l.setHsCode(ShpSupport.limit(ShpSupport.trim(e.hsCode()), 16));
                l.setOrigin(ShpSupport.limit(e.origin(), 32));
            }
            if (!StringUtils.hasText(l.getHsCode())) throw BizException.of(ShippingErrorCodes.DOC_HS_REQUIRED, l.getLineNo());
        }
        lines.forEach(invoiceLineMapper::updateByIdOrFail);
        inv.setInvoiceNo(ShpSupport.limit(no, 32));
        inv.setInvoiceDate(req.invoiceDate());
        inv.setBillTo(ShpSupport.limit(req.billTo(), 512));
        inv.setConsignee(ShpSupport.limit(req.consignee(), 512));
        inv.setNotifyParty(ShpSupport.limit(req.notifyParty(), 512));
        inv.setTradeTerm(ShpSupport.trim(req.tradeTerm()));
        inv.setPaymentTermText(ShpSupport.limit(req.paymentTermText(), 256));
        inv.setPortOfLoading(ShpSupport.limit(req.portOfLoading(), 64));
        inv.setPortOfDestination(ShpSupport.limit(req.portOfDestination(), 64));
        inv.setVesselFlight(ShpSupport.limit(req.vesselFlight(), 64));
        inv.setBankInfo(ShpSupport.limit(req.bankInfo(), 1000));
        inv.setRemark(ShpSupport.limit(req.remark(), 1000));
        invoiceMapper.updateByIdOrFail(inv);
    }

    public PageResult<DocRow> invoices(DocQuery q) {
        List<Long> sids = shipmentFilter(q);
        if (sids != null && sids.isEmpty()) return new PageResult<>(List.of(), 0L);
        IPage<ShpInvoiceDO> p = invoiceMapper.selectPage(new Page<>(q.getPageNo(), q.getPageSize()), new LambdaQueryWrapper<ShpInvoiceDO>()
                .like(StringUtils.hasText(q.getNo()), ShpInvoiceDO::getInvoiceNo, q.getNo())
                .in(sids != null, ShpInvoiceDO::getShipmentId, sids)
                .ge(q.getDateFrom() != null, ShpInvoiceDO::getInvoiceDate, q.getDateFrom())
                .le(q.getDateTo() != null, ShpInvoiceDO::getInvoiceDate, q.getDateTo())
                .eq(q.getInvalid() != null, ShpInvoiceDO::getInvalid, q.getInvalid())
                .orderByDesc(ShpInvoiceDO::getId));
        Map<Long, ShpShipmentDO> ships = shipments(p.getRecords().stream().map(ShpInvoiceDO::getShipmentId).toList());
        Map<Long, CustomerDTO> cus = support.customers(p.getRecords().stream().map(ShpInvoiceDO::getCustomerId).toList());
        boolean price = support.canSeePrice();
        return new PageResult<>(p.getRecords().stream().map(inv -> {
            ShpShipmentDO s = ships.get(inv.getShipmentId());
            return new DocRow(inv.getId(), inv.getInvoiceNo(), inv.getInvoiceDate(), inv.getShipmentId(), s == null ? null : s.getDocNo(), inv.getCustomerId(),
                    ShpSupport.customerName(cus.get(inv.getCustomerId())), inv.getCurrency(), s == null ? null : s.getTotalQty(),
                    s == null ? null : s.getCartonCount(), price ? inv.getTotalAmount() : null, Boolean.TRUE.equals(inv.getInvalid()), inv.getCreatedAt());
        }).toList(), p.getTotal());
    }

    /** SHP-DOC-R05：无价格权限时单价、金额显示 *** */
    public Map<String, Object> invoicePrintData(Long id) {
        InvoiceDetail d = invoice(id);
        ShpShipmentDO s = shipmentService.get(d.shipmentId());
        boolean price = d.priceVisible();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("invoiceNo", d.invoiceNo());
        m.put("invoiceDate", d.invoiceDate());
        m.put("companyName", companyName(s));
        m.put("billTo", Objects.toString(d.billTo(), ""));
        m.put("consignee", Objects.toString(d.consignee(), ""));
        m.put("notifyParty", Objects.toString(d.notifyParty(), ""));
        m.put("currency", d.currency());
        m.put("tradeTerm", Objects.toString(d.tradeTerm(), ""));
        m.put("paymentTermText", Objects.toString(d.paymentTermText(), ""));
        m.put("portOfLoading", Objects.toString(d.portOfLoading(), ""));
        m.put("portOfDestination", Objects.toString(d.portOfDestination(), ""));
        m.put("vesselFlight", Objects.toString(d.vesselFlight(), ""));
        m.put("totalAmount", price ? d.totalAmount() : "***");
        m.put("amountInWords", price ? d.amountInWords() : "***");
        m.put("bankInfo", Objects.toString(d.bankInfo(), ""));
        m.put("invalid", d.invalid());
        m.put("lines", d.lines().stream().map(l -> {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("lineNo", l.lineNo());
            r.put("customerPoNo", Objects.toString(l.customerPoNo(), ""));
            r.put("partNo", Objects.toString(l.customerPartNo(), ""));
            r.put("description", Objects.toString(l.description(), ""));
            r.put("hsCode", Objects.toString(l.hsCode(), ""));
            r.put("origin", Objects.toString(l.origin(), ""));
            r.put("qty", l.qty());
            r.put("uom", l.uom());
            r.put("unitPrice", price ? l.unitPrice() : "***");
            r.put("amount", price ? l.amount() : "***");
            return r;
        }).toList());
        return m;
    }

    // ==================== 报关资料 ====================

    /** 按 HS 编码合并出货行（SHP-DOC-T03），申报品名取物料中文名，申报要素按模板生成 */
    @Transactional(rollbackFor = Exception.class)
    public Long createCustoms(Long shipmentId) {
        ShpShipmentDO s = shipmentForDoc(shipmentId);
        ShpCustomsDO exist = customsMapper.selectOne(new LambdaQueryWrapper<ShpCustomsDO>().eq(ShpCustomsDO::getShipmentId, shipmentId).last("LIMIT 1"));
        if (exist != null) throw BizException.of(ShippingErrorCodes.DOC_EXISTS, "报关资料", exist.getDocCode());
        List<ShpShipmentLineDO> sls = shipmentLineMapper.selectByParent(shipmentId);
        Map<Long, MaterialDTO> ms = support.materials(sls.stream().map(ShpShipmentLineDO::getMaterialId).toList());
        Map<String, List<ShpShipmentLineDO>> groups = new LinkedHashMap<>();
        for (ShpShipmentLineDO l : sls) {
            MaterialDTO m = ms.get(l.getMaterialId());
            String key = m != null && StringUtils.hasText(m.hsCode()) ? "HS:" + m.hsCode().trim() : "M:" + l.getMaterialId();
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(l);
        }
        AddressDTO addr = support.address(s.getShipToSnapshot());
        ShpCustomsDO cd = new ShpCustomsDO();
        cd.setShipmentId(shipmentId);
        cd.setDocCode(support.nextNo(ShippingModuleConfig.CUSTOMS));
        cd.setTradeMode("一般贸易");
        cd.setDeclarePort(s.getPortOfLoading());
        cd.setDestinationCountry(addr == null ? null : addr.country());
        cd.setCurrency(s.getCurrency());
        cd.setTotalAmount(Decimals.amount(sls.stream().map(ShpShipmentLineDO::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add)));
        cd.setInvalid(false);
        customsMapper.insert(cd);
        Map<Long, MaterialStockAttr> attrs = new HashMap<>();
        int seq = 1;
        for (List<ShpShipmentLineDO> g : groups.values()) {
            MaterialDTO m = ms.get(g.get(0).getMaterialId());
            BigDecimal qty = g.stream().map(ShpShipmentLineDO::getBaseQty).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal amount = g.stream().map(ShpShipmentLineDO::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal nw = BigDecimal.ZERO;
            BigDecimal gw = BigDecimal.ZERO;
            for (ShpShipmentLineDO l : g) {
                MaterialStockAttr a = attrs.computeIfAbsent(l.getMaterialId(), id -> support.materialApi().getStockAttr(id));
                if (a != null && a.unitNetWeight() != null) nw = nw.add(a.unitNetWeight().multiply(l.getBaseQty()));
                if (a != null && a.unitGrossWeight() != null) gw = gw.add(a.unitGrossWeight().multiply(l.getBaseQty()));
            }
            ShpCustomsItemDO it = new ShpCustomsItemDO();
            it.setCustomsId(cd.getId());
            it.setSeq(seq++);
            it.setHsCode(m == null ? null : ShpSupport.trim(m.hsCode()));
            it.setDeclareName(ShpSupport.limit(g.stream().map(l -> ms.get(l.getMaterialId())).filter(Objects::nonNull).map(MaterialDTO::name).distinct()
                    .collect(Collectors.joining("/")), 128));
            it.setDeclareElements(ShpSupport.limit("0|0|" + Objects.toString(it.getDeclareName(), "") + "|无品牌|", 512));
            it.setQty(qty);
            it.setUom(m == null ? null : m.baseUom());
            it.setUnitPrice(qty.signum() == 0 ? BigDecimal.ZERO : amount.divide(qty, Decimals.PRICE_SCALE, RoundingMode.HALF_UP));
            it.setAmount(Decimals.amount(amount));
            it.setOrigin("中国");
            it.setNetWeight(nw.signum() == 0 ? null : nw.setScale(3, RoundingMode.HALF_UP));
            it.setGrossWeight(gw.signum() == 0 ? null : gw.setScale(3, RoundingMode.HALF_UP));
            customsItemMapper.insert(it);
        }
        ShpShipmentDO fresh = shipmentService.get(shipmentId);
        fresh.setCustomsId(cd.getId());
        shipmentMapper.updateByIdOrFail(fresh);
        support.log(ShippingModuleConfig.SHIPMENT, shipmentId, s.getDocNo(), "CUSTOMS", "生成报关资料", null, null, cd.getDocCode());
        return cd.getId();
    }

    public CustomsDetail customs(Long id) {
        ShpCustomsDO cd = customsMapper.selectById(id);
        if (cd == null) throw BizException.of(ShippingErrorCodes.NOT_EXISTS, "报关资料");
        ShpShipmentDO s = shipmentService.get(cd.getShipmentId());
        CustomerDTO c = support.customer(s.getCustomerId());
        List<CustomsItemVO> items = customsItemMapper.selectByParent(id).stream().map(it -> new CustomsItemVO(it.getId(), it.getSeq(), it.getHsCode(),
                it.getDeclareName(), it.getDeclareElements(), it.getQty(), it.getUom(), it.getSecondQty(), it.getSecondUom(), it.getUnitPrice(), it.getAmount(),
                it.getOrigin(), it.getNetWeight(), it.getGrossWeight())).toList();
        return new CustomsDetail(cd.getId(), cd.getDocCode(), s.getId(), s.getDocNo(), s.getShipmentStatus(), s.getCustomerId(), ShpSupport.customerName(c),
                cd.getCustomsNo(), cd.getDeclareDate(), cd.getTradeMode(), cd.getDeclarePort(), cd.getDestinationCountry(), cd.getCurrency(), cd.getTotalAmount(),
                cd.getRemark(), Boolean.TRUE.equals(cd.getInvalid()), items, cd.getCreatedAt());
    }

    /** 报关后回填报关单号、日期、附件（报关单 PDF）；明细可改 HS 编码、申报品名、要素、第二法定单位 */
    @Transactional(rollbackFor = Exception.class)
    public void updateCustoms(Long id, CustomsSave req) {
        ShpCustomsDO cd = customsMapper.selectById(id);
        if (cd == null) throw BizException.of(ShippingErrorCodes.NOT_EXISTS, "报关资料");
        cd.setCustomsNo(ShpSupport.limit(req.customsNo(), 32));
        cd.setDeclareDate(req.declareDate());
        cd.setTradeMode(ShpSupport.limit(req.tradeMode(), 32));
        cd.setDeclarePort(ShpSupport.limit(req.declarePort(), 64));
        cd.setDestinationCountry(ShpSupport.limit(req.destinationCountry(), 64));
        cd.setRemark(ShpSupport.limit(req.remark(), 1000));
        customsMapper.updateByIdOrFail(cd);
        Map<Long, CustomsItemEdit> edits = req.items() == null ? Map.of()
                : req.items().stream().collect(Collectors.toMap(CustomsItemEdit::id, e -> e, (a, b) -> b));
        for (ShpCustomsItemDO it : customsItemMapper.selectByParent(id)) {
            CustomsItemEdit e = edits.get(it.getId());
            if (e == null) continue;
            it.setHsCode(ShpSupport.limit(e.hsCode().trim(), 16));
            it.setDeclareName(ShpSupport.limit(e.declareName(), 128));
            it.setDeclareElements(ShpSupport.limit(e.declareElements(), 512));
            if (StringUtils.hasText(e.uom())) it.setUom(ShpSupport.limit(e.uom(), 16));
            it.setSecondQty(e.secondQty());
            it.setSecondUom(ShpSupport.limit(e.secondUom(), 16));
            it.setOrigin(ShpSupport.limit(e.origin(), 32));
            customsItemMapper.updateByIdOrFail(it);
        }
        support.bindFiles(req.fileIds(), ShippingModuleConfig.CUSTOMS, id);
    }

    public PageResult<DocRow> customsList(DocQuery q) {
        List<Long> sids = shipmentFilter(q);
        if (sids != null && sids.isEmpty()) return new PageResult<>(List.of(), 0L);
        IPage<ShpCustomsDO> p = customsMapper.selectPage(new Page<>(q.getPageNo(), q.getPageSize()), new LambdaQueryWrapper<ShpCustomsDO>()
                .and(StringUtils.hasText(q.getNo()), w -> w.like(ShpCustomsDO::getDocCode, q.getNo()).or().like(ShpCustomsDO::getCustomsNo, q.getNo()))
                .in(sids != null, ShpCustomsDO::getShipmentId, sids)
                .ge(q.getDateFrom() != null, ShpCustomsDO::getDeclareDate, q.getDateFrom())
                .le(q.getDateTo() != null, ShpCustomsDO::getDeclareDate, q.getDateTo())
                .eq(q.getInvalid() != null, ShpCustomsDO::getInvalid, q.getInvalid())
                .orderByDesc(ShpCustomsDO::getId));
        Map<Long, ShpShipmentDO> ships = shipments(p.getRecords().stream().map(ShpCustomsDO::getShipmentId).toList());
        Map<Long, CustomerDTO> cus = support.customers(ships.values().stream().map(ShpShipmentDO::getCustomerId).toList());
        return new PageResult<>(p.getRecords().stream().map(cd -> {
            ShpShipmentDO s = ships.get(cd.getShipmentId());
            return new DocRow(cd.getId(), cd.getDocCode(), cd.getDeclareDate(), cd.getShipmentId(), s == null ? null : s.getDocNo(),
                    s == null ? null : s.getCustomerId(), s == null ? null : ShpSupport.customerName(cus.get(s.getCustomerId())), cd.getCurrency(),
                    s == null ? null : s.getTotalQty(), s == null ? null : s.getCartonCount(), cd.getTotalAmount(), Boolean.TRUE.equals(cd.getInvalid()),
                    cd.getCreatedAt());
        }).toList(), p.getTotal());
    }

    // ==================== 公共 ====================

    /** 出货单号 / 客户过滤 → 出货单 ID（null 表示不过滤） */
    private List<Long> shipmentFilter(DocQuery q) {
        if (!StringUtils.hasText(q.getShipmentNo()) && q.getCustomerId() == null) return null;
        return shipmentMapper.selectList(new LambdaQueryWrapper<ShpShipmentDO>()
                .like(StringUtils.hasText(q.getShipmentNo()), ShpShipmentDO::getDocNo, q.getShipmentNo())
                .eq(q.getCustomerId() != null, ShpShipmentDO::getCustomerId, q.getCustomerId())).stream().map(BaseDO::getId).toList();
    }

    private Map<Long, ShpShipmentDO> shipments(List<Long> ids) {
        List<Long> set = ids.stream().filter(Objects::nonNull).distinct().toList();
        Map<Long, ShpShipmentDO> map = new HashMap<>();
        if (!set.isEmpty()) shipmentMapper.selectBatchIds(set).forEach(s -> map.put(s.getId(), s));
        return map;
    }
}
