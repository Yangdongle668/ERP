package com.erp.module.purchase.apiimpl;

import com.erp.module.purchase.api.price.PurchasePriceApi;
import com.erp.module.purchase.api.price.PurchasePriceDTO;
import com.erp.module.purchase.dal.dataobject.PriceDO;
import com.erp.module.purchase.service.price.PriceService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

/** 采购价格对外接口 */
@Service
public class PurchasePriceApiImpl implements PurchasePriceApi {

    private final PriceService priceService;

    public PurchasePriceApiImpl(PriceService priceService) {
        this.priceService = priceService;
    }

    @Override
    public Optional<PurchasePriceDTO> getEffectivePrice(Long supplierId, Long materialId, BigDecimal qty, LocalDate date, String currency) {
        return priceService.effective(supplierId, materialId, qty, date, currency).map(PurchasePriceApiImpl::toDto);
    }

    @Override
    public Optional<PurchasePriceDTO> getLatestPrice(Long materialId) {
        return priceService.latest(materialId).map(PurchasePriceApiImpl::toDto);
    }

    static PurchasePriceDTO toDto(PriceDO p) {
        return new PurchasePriceDTO(p.getId(), p.getSupplierId(), p.getMaterialId(), p.getCurrency(), p.getMinQty(), p.getPrice(), p.getTaxRate(),
                p.getPriceInclTax(), p.getEffectiveFrom(), p.getEffectiveTo());
    }
}
