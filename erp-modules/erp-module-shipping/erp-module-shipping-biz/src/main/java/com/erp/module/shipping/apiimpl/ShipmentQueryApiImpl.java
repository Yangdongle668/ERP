package com.erp.module.shipping.apiimpl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.framework.mybatis.BaseDO;
import com.erp.module.shipping.api.query.ShipmentQueryApi;
import com.erp.module.shipping.api.query.ShippedLineDTO;
import com.erp.module.shipping.dal.dataobject.ShpShipmentDO;
import com.erp.module.shipping.dal.dataobject.ShpShipmentLineDO;
import com.erp.module.shipping.dal.mapper.ShpShipmentLineMapper;
import com.erp.module.shipping.dal.mapper.ShpShipmentMapper;
import com.erp.module.shipping.service.ShipmentStatus;
import com.erp.module.shipping.service.ShpSupport;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** ShipmentQueryApi：只返回已出货 / 已完成的出货单行 */
@Service
public class ShipmentQueryApiImpl implements ShipmentQueryApi {

    private static final List<String> SHIPPED = List.of(ShipmentStatus.SHIPPED.name(), ShipmentStatus.COMPLETED.name());

    private final ShpShipmentMapper mapper;
    private final ShpShipmentLineMapper lineMapper;

    public ShipmentQueryApiImpl(ShpShipmentMapper mapper, ShpShipmentLineMapper lineMapper) {
        this.mapper = mapper;
        this.lineMapper = lineMapper;
    }

    @Override
    public List<ShippedLineDTO> getShippedLines(Long customerId, Long materialId, LocalDate from) {
        List<ShpShipmentDO> ships = mapper.selectList(new LambdaQueryWrapper<ShpShipmentDO>().in(ShpShipmentDO::getShipmentStatus, SHIPPED)
                .eq(ShpShipmentDO::getCustomerId, customerId).ge(from != null, ShpShipmentDO::getShipDate, from));
        return lines(ships, l -> materialId == null || materialId.equals(l.getMaterialId()));
    }

    @Override
    public List<ShippedLineDTO> getShipmentsByBatch(Long materialId, String batchNo) {
        if (batchNo == null || batchNo.isBlank()) return List.of();
        String b = batchNo.trim();
        List<ShpShipmentLineDO> ls = lineMapper.selectList(new LambdaQueryWrapper<ShpShipmentLineDO>().eq(ShpShipmentLineDO::getMaterialId, materialId)
                .like(ShpShipmentLineDO::getBatchNo, b)).stream()
                .filter(l -> Arrays.stream(l.getBatchNo().split(",")).map(String::trim).anyMatch(b::equals)).toList();
        if (ls.isEmpty()) return List.of();
        List<ShpShipmentDO> ships = mapper.selectList(new LambdaQueryWrapper<ShpShipmentDO>().in(ShpShipmentDO::getShipmentStatus, SHIPPED)
                .in(BaseDO::getId, ls.stream().map(ShpShipmentLineDO::getShipmentId).distinct().toList()));
        List<Long> lineIds = ls.stream().map(BaseDO::getId).toList();
        return lines(ships, l -> lineIds.contains(l.getId()));
    }

    @Override
    public List<ShippedLineDTO> getShipmentsByOrder(Long orderId) {
        List<Long> sids = lineMapper.selectList(new LambdaQueryWrapper<ShpShipmentLineDO>().eq(ShpShipmentLineDO::getOrderId, orderId)).stream()
                .map(ShpShipmentLineDO::getShipmentId).distinct().toList();
        if (sids.isEmpty()) return List.of();
        List<ShpShipmentDO> ships = mapper.selectList(new LambdaQueryWrapper<ShpShipmentDO>().in(ShpShipmentDO::getShipmentStatus, SHIPPED).in(BaseDO::getId, sids));
        return lines(ships, l -> orderId.equals(l.getOrderId()));
    }

    private List<ShippedLineDTO> lines(List<ShpShipmentDO> ships, java.util.function.Predicate<ShpShipmentLineDO> filter) {
        if (ships.isEmpty()) return List.of();
        Map<Long, ShpShipmentDO> byId = ships.stream().collect(Collectors.toMap(BaseDO::getId, Function.identity()));
        return lineMapper.selectByParents(byId.keySet()).stream().filter(filter).map(l -> {
                    ShpShipmentDO s = byId.get(l.getShipmentId());
                    return new ShippedLineDTO(s.getId(), s.getDocNo(), l.getId(), s.getShipDate(), s.getShipmentStatus(), s.getCustomerId(), l.getOrderId(),
                            l.getOrderNo(), l.getOrderLineId(), l.getMaterialId(), l.getBatchNo(),
                            ShpSupport.nz(l.getOutQty()).signum() > 0 ? l.getOutQty() : l.getBaseQty(), s.getCurrency(), l.getPriceInclTax(), l.getTotalAmount(),
                            s.getBlNo(), s.getTransportMode());
                })
                .sorted(Comparator.comparing(ShippedLineDTO::shipDate, Comparator.nullsLast(Comparator.reverseOrder())).thenComparing(ShippedLineDTO::shipmentLineId))
                .toList();
    }
}
