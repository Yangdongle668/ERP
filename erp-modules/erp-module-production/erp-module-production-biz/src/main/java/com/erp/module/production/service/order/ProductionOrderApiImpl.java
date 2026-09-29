package com.erp.module.production.service.order;

import com.erp.module.engineering.api.sample.SampleOrderCreator;
import com.erp.module.engineering.api.sample.SampleOrderRequest;
import com.erp.module.production.api.order.MrpSuggestion;
import com.erp.module.production.api.order.ProductionOrderApi;
import com.erp.module.production.dal.dataobject.MfgProdOrderDO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** 生产订单写入对外实现：PMC 转单（{@link ProductionOrderApi}）、研发工程样品订单（{@link SampleOrderCreator}） */
@Service("mfgProductionOrderApiImpl")
public class ProductionOrderApiImpl implements ProductionOrderApi, SampleOrderCreator {

    private final ProdOrderService orderService;

    public ProductionOrderApiImpl(ProdOrderService orderService) {
        this.orderService = orderService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<Long> createFromMrp(List<MrpSuggestion> suggestions) {
        return orderService.createFromMrp(suggestions);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<String> release(Long prodOrderId) {
        return orderService.release(prodOrderId, true).warnings();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updatePlanDates(Long prodOrderId, LocalDate planStart, LocalDate planEnd, String reason) {
        orderService.updatePlanDates(prodOrderId, planStart, planEnd, reason);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createSampleOrder(Long sampleId, String sampleNo, Long materialId, BigDecimal qty, LocalDate requiredDate) {
        return orderService.createSample(sampleId, sampleNo, materialId, qty, requiredDate).getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Ref create(SampleOrderRequest request) {
        MfgProdOrderDO o = orderService.createSample(request.sampleId(), request.sampleNo(), request.materialId(), request.qty(), request.requiredDate());
        return new Ref(o.getId(), o.getDocNo());
    }
}
