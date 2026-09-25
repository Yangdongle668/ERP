package com.erp.module.purchase.apiimpl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.common.enums.DocStatus;
import com.erp.module.purchase.api.order.InTransitDTO;
import com.erp.module.purchase.api.order.PurchaseQueryApi;
import com.erp.module.purchase.dal.dataobject.OrderDO;
import com.erp.module.purchase.dal.dataobject.OrderLineDO;
import com.erp.module.purchase.dal.dataobject.OutsourcingDO;
import com.erp.module.purchase.dal.mapper.OrderLineMapper;
import com.erp.module.purchase.dal.mapper.OrderMapper;
import com.erp.module.purchase.dal.mapper.OutsourcingMapper;
import com.erp.module.purchase.service.order.OrderService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** 在途查询（PMC、仓库、研发工程使用）：已审核/执行中订单未关闭行的未到货数量 + 委外单未收货数量 */
@Service
public class PurchaseQueryApiImpl implements PurchaseQueryApi {

    static final List<DocStatus> ACTIVE = List.of(DocStatus.APPROVED, DocStatus.IN_PROGRESS);

    private final OrderMapper orderMapper;
    private final OrderLineMapper lineMapper;
    private final OutsourcingMapper outsourcingMapper;

    public PurchaseQueryApiImpl(OrderMapper orderMapper, OrderLineMapper lineMapper, OutsourcingMapper outsourcingMapper) {
        this.orderMapper = orderMapper;
        this.lineMapper = lineMapper;
        this.outsourcingMapper = outsourcingMapper;
    }

    @Override
    public Map<Long, InTransitDTO> getInTransitQty(Collection<Long> materialIds) {
        Set<Long> ids = materialIds == null ? Set.of() : materialIds.stream().filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) return Collections.emptyMap();
        Map<Long, List<InTransitDTO.Detail>> details = new HashMap<>();
        List<OrderLineDO> lines = lineMapper.selectList(new LambdaQueryWrapper<OrderLineDO>().in(OrderLineDO::getMaterialId, ids)
                .eq(OrderLineDO::getLineStatus, OrderService.OPEN));
        if (!lines.isEmpty()) {
            Map<Long, OrderDO> orders = orderMapper.selectList(new LambdaQueryWrapper<OrderDO>().in(OrderDO::getId, lines.stream().map(OrderLineDO::getOrderId)
                    .collect(Collectors.toSet())).in(OrderDO::getStatus, ACTIVE)).stream().collect(Collectors.toMap(OrderDO::getId, o -> o));
            for (OrderLineDO l : lines) {
                OrderDO o = orders.get(l.getOrderId());
                BigDecimal open = l.getBaseQty().subtract(l.getReceivedQty());
                if (o == null || open.signum() <= 0) continue;
                details.computeIfAbsent(l.getMaterialId(), k -> new ArrayList<>()).add(new InTransitDTO.Detail("PUR_ORDER", o.getId(), o.getDocNo(),
                        l.getId(), o.getSupplierId(), open, OrderService.dueDate(l)));
            }
        }
        for (OutsourcingDO o : outsourcingMapper.selectList(new LambdaQueryWrapper<OutsourcingDO>().in(OutsourcingDO::getMaterialId, ids)
                .in(OutsourcingDO::getStatus, ACTIVE))) {
            BigDecimal open = o.getQty().subtract(o.getReceivedQty());
            if (open.signum() <= 0) continue;
            details.computeIfAbsent(o.getMaterialId(), k -> new ArrayList<>()).add(new InTransitDTO.Detail("PUR_OUTSOURCING", o.getId(), o.getDocNo(),
                    null, o.getSupplierId(), open, o.getRequiredDate()));
        }
        Map<Long, InTransitDTO> result = new HashMap<>();
        details.forEach((mid, ds) -> result.put(mid, new InTransitDTO(mid, ds.stream().map(InTransitDTO.Detail::qty).reduce(BigDecimal.ZERO, BigDecimal::add), ds)));
        return result;
    }

    @Override
    public BigDecimal getOpenQtyByMaterial(Long materialId) {
        InTransitDTO dto = getInTransitQty(List.of(materialId)).get(materialId);
        return dto == null ? BigDecimal.ZERO : dto.details().stream().filter(d -> "PUR_ORDER".equals(d.docType()))
                .map(InTransitDTO.Detail::qty).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
