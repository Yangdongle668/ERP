package com.erp.module.sales.service.price;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.common.enums.DocStatus;
import com.erp.module.crm.api.customer.CustomerDTO;
import com.erp.module.engineering.api.material.MaterialDTO;
import com.erp.module.sales.api.price.SalesPriceApi;
import com.erp.module.sales.api.price.SalesPriceDTO;
import com.erp.module.sales.dal.dataobject.SalOrderDO;
import com.erp.module.sales.dal.dataobject.SalOrderLineDO;
import com.erp.module.sales.dal.dataobject.SalPriceListDO;
import com.erp.module.sales.dal.dataobject.SalPriceListItemDO;
import com.erp.module.sales.dal.dataobject.SalQuotationDO;
import com.erp.module.sales.dal.dataobject.SalQuotationLineDO;
import com.erp.module.sales.dal.mapper.SalOrderLineMapper;
import com.erp.module.sales.dal.mapper.SalOrderMapper;
import com.erp.module.sales.dal.mapper.SalPriceListItemMapper;
import com.erp.module.sales.dal.mapper.SalPriceListMapper;
import com.erp.module.sales.dal.mapper.SalQuotationLineMapper;
import com.erp.module.sales.dal.mapper.SalQuotationMapper;
import com.erp.module.sales.service.QuoteStatus;
import com.erp.module.sales.service.SalSupport;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 取价（需求 04-01 第 3 节）：客户价格表 → 等级价格表 → 通用价格表 → 有效报价 → 最近成交价；找到即返回。
 * 同一层级多张有效价格表取生效日期最晚的一张；阶梯取起始数量 ≤ 数量的最大一档。
 */
@Service("salPriceLookupService")
public class PriceLookupService implements SalesPriceApi {

    private final SalPriceListMapper priceListMapper;
    private final SalPriceListItemMapper itemMapper;
    private final SalQuotationMapper quotationMapper;
    private final SalQuotationLineMapper quotationLineMapper;
    private final SalOrderMapper orderMapper;
    private final SalOrderLineMapper orderLineMapper;
    private final SalSupport support;

    public PriceLookupService(SalPriceListMapper priceListMapper, SalPriceListItemMapper itemMapper, SalQuotationMapper quotationMapper,
                              SalQuotationLineMapper quotationLineMapper, SalOrderMapper orderMapper, SalOrderLineMapper orderLineMapper, SalSupport support) {
        this.priceListMapper = priceListMapper;
        this.itemMapper = itemMapper;
        this.quotationMapper = quotationMapper;
        this.quotationLineMapper = quotationLineMapper;
        this.orderMapper = orderMapper;
        this.orderLineMapper = orderLineMapper;
        this.support = support;
    }

    @Override
    public Optional<SalesPriceDTO> getPrice(Long customerId, Long materialId, BigDecimal qty, LocalDate date, String currency) {
        MaterialDTO m = support.materialApi().getMaterial(materialId).orElse(null);
        if (m == null) return Optional.empty();
        return getPrice(customerId, materialId, qty, m.baseUom(), date, currency);
    }

    @Override
    public Optional<SalesPriceDTO> getPrice(Long customerId, Long materialId, BigDecimal qty, String uom, LocalDate date, String currency) {
        if (materialId == null || !StringUtils.hasText(currency) || !StringUtils.hasText(uom)) return Optional.empty();
        LocalDate d = date == null ? LocalDate.now() : date;
        BigDecimal q = qty == null ? BigDecimal.ZERO : qty;
        CustomerDTO c = customerId == null ? null : support.customerApi().getCustomer(customerId).orElse(null);
        if (c != null) {
            Optional<SalesPriceDTO> r = fromPriceLists("CUSTOMER", w -> w.eq(SalPriceListDO::getCustomerId, c.id()), materialId, q, uom, d, currency,
                    "PRICE_LIST_CUSTOMER", "客户价格表");
            if (r.isPresent()) return r;
            if (StringUtils.hasText(c.level())) {
                r = fromPriceLists("LEVEL", w -> w.eq(SalPriceListDO::getCustomerLevel, c.level()), materialId, q, uom, d, currency,
                        "PRICE_LIST_LEVEL", "等级价格表");
                if (r.isPresent()) return r;
            }
        }
        Optional<SalesPriceDTO> r = fromPriceLists("ALL", w -> w, materialId, q, uom, d, currency, "PRICE_LIST_ALL", "通用价格表");
        if (r.isPresent() || c == null) return r;
        r = fromQuotation(c.id(), materialId, q, uom, d, currency);
        if (r.isPresent()) return r;
        return fromLastOrder(c.id(), materialId, uom, currency);
    }

    private Optional<SalesPriceDTO> fromPriceLists(String scope, Function<LambdaQueryWrapper<SalPriceListDO>, LambdaQueryWrapper<SalPriceListDO>> cond,
                                                   Long materialId, BigDecimal qty, String uom, LocalDate date, String currency,
                                                   String sourceType, String label) {
        LambdaQueryWrapper<SalPriceListDO> w = new LambdaQueryWrapper<SalPriceListDO>().eq(SalPriceListDO::getScope, scope)
                .eq(SalPriceListDO::getStatus, DocStatus.APPROVED).eq(SalPriceListDO::getCurrency, currency)
                .le(SalPriceListDO::getEffectiveFrom, date)
                .and(x -> x.isNull(SalPriceListDO::getEffectiveTo).or().ge(SalPriceListDO::getEffectiveTo, date))
                .inSql(SalPriceListDO::getId, "SELECT price_list_id FROM sal_price_list_item WHERE deleted = 0 AND material_id = " + materialId.longValue())
                .orderByDesc(SalPriceListDO::getEffectiveFrom).orderByDesc(SalPriceListDO::getId);
        for (SalPriceListDO p : priceListMapper.selectList(cond.apply(w))) {
            Optional<SalPriceListItemDO> tier = itemMapper.selectList(new LambdaQueryWrapper<SalPriceListItemDO>().eq(SalPriceListItemDO::getPriceListId, p.getId())
                    .eq(SalPriceListItemDO::getMaterialId, materialId).eq(SalPriceListItemDO::getUom, uom)).stream()
                    .filter(i -> i.getMinQty().compareTo(qty) <= 0).max(Comparator.comparing(SalPriceListItemDO::getMinQty));
            if (tier.isPresent()) {
                return Optional.of(new SalesPriceDTO(tier.get().getPrice(), Boolean.TRUE.equals(p.getTaxIncluded()), uom, sourceType, p.getId(),
                        p.getDocNo(), label + " " + p.getDocNo()));
            }
        }
        return Optional.empty();
    }

    /** 有效报价：该客户该物料最近一张已审核 / 已发送、未过期的报价（数量阶梯匹配） */
    private Optional<SalesPriceDTO> fromQuotation(Long customerId, Long materialId, BigDecimal qty, String uom, LocalDate date, String currency) {
        List<SalQuotationDO> qs = quotationMapper.selectList(new LambdaQueryWrapper<SalQuotationDO>().eq(SalQuotationDO::getCustomerId, customerId)
                .in(SalQuotationDO::getQuoteStatus, QuoteStatus.APPROVED.name(), QuoteStatus.SENT.name()).eq(SalQuotationDO::getCurrency, currency)
                .ge(SalQuotationDO::getValidUntil, date)
                .inSql(SalQuotationDO::getId, "SELECT quotation_id FROM sal_quotation_line WHERE deleted = 0 AND material_id = " + materialId.longValue())
                .orderByDesc(SalQuotationDO::getDocDate).orderByDesc(SalQuotationDO::getId).last("LIMIT 5"));
        for (SalQuotationDO qt : qs) {
            Optional<SalQuotationLineDO> tier = quotationLineMapper.selectList(new LambdaQueryWrapper<SalQuotationLineDO>()
                    .eq(SalQuotationLineDO::getQuotationId, qt.getId()).eq(SalQuotationLineDO::getMaterialId, materialId).eq(SalQuotationLineDO::getUom, uom))
                    .stream().filter(l -> l.getMinQty().compareTo(qty) <= 0).max(Comparator.comparing(SalQuotationLineDO::getMinQty));
            if (tier.isPresent()) {
                String no = qt.getDocNo() + " R" + qt.getRevision();
                return Optional.of(new SalesPriceDTO(tier.get().getPrice(), Boolean.TRUE.equals(qt.getTaxIncluded()), uom, "QUOTATION", qt.getId(),
                        no, "报价单 " + no));
            }
        }
        return Optional.empty();
    }

    /** 最近成交价：该客户该物料最近一张已审核订单（同币别、同单位）的单价；补货订单不计 */
    private Optional<SalesPriceDTO> fromLastOrder(Long customerId, Long materialId, String uom, String currency) {
        List<SalOrderDO> orders = orderMapper.selectList(new LambdaQueryWrapper<SalOrderDO>().eq(SalOrderDO::getCustomerId, customerId)
                .eq(SalOrderDO::getCurrency, currency).ne(SalOrderDO::getOrderType, "REPLACEMENT")
                .in(SalOrderDO::getStatus, DocStatus.APPROVED, DocStatus.IN_PROGRESS, DocStatus.COMPLETED, DocStatus.CLOSED)
                .inSql(SalOrderDO::getId, "SELECT order_id FROM sal_order_line WHERE deleted = 0 AND material_id = " + materialId.longValue())
                .orderByDesc(SalOrderDO::getApprovedAt).orderByDesc(SalOrderDO::getId).last("LIMIT 5"));
        if (orders.isEmpty()) return Optional.empty();
        Map<Long, List<SalOrderLineDO>> lines = orderLineMapper.selectByParents(orders.stream().map(SalOrderDO::getId).toList()).stream()
                .filter(l -> l.getMaterialId().equals(materialId) && l.getUom().equals(uom))
                .collect(Collectors.groupingBy(SalOrderLineDO::getOrderId));
        for (SalOrderDO o : orders) {
            List<SalOrderLineDO> ls = lines.get(o.getId());
            if (ls == null || ls.isEmpty()) continue;
            SalOrderLineDO l = ls.get(0);
            boolean incl = Boolean.TRUE.equals(o.getTaxIncluded());
            return Optional.of(new SalesPriceDTO(incl ? l.getPriceInclTax() : l.getPrice(), incl, uom, "LAST_ORDER", o.getId(), o.getDocNo(),
                    "最近成交价 " + o.getDocNo()));
        }
        return Optional.empty();
    }

    /** 按单据的含税口径换算取价结果（价格表含税 / 不含税与单据不一致时按行税率换算） */
    public static BigDecimal convert(SalesPriceDTO p, boolean docTaxIncluded, BigDecimal taxRate) {
        if (p.taxIncluded() == docTaxIncluded) return p.price();
        BigDecimal onePlus = BigDecimal.ONE.add(taxRate == null ? BigDecimal.ZERO : taxRate);
        return docTaxIncluded ? p.price().multiply(onePlus).setScale(6, RoundingMode.HALF_UP)
                : p.price().divide(onePlus, 6, RoundingMode.HALF_UP);
    }
}
