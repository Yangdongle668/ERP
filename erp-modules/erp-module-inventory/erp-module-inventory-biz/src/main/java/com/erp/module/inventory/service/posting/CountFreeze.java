package com.erp.module.inventory.service.posting;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.module.inventory.dal.dataobject.CountDO;
import com.erp.module.inventory.dal.dataobject.CountLineDO;
import com.erp.module.inventory.dal.mapper.CountLineMapper;
import com.erp.module.inventory.dal.mapper.CountMapper;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 盘点冻结（INV-CNT-R02）：盘点中（已生成盘点表、未审核/作废）的盘点单冻结其范围内的库存。
 * 全盘冻结整个仓库；抽盘冻结盘点表中的（仓库 + 物料）。
 */
@Component
public class CountFreeze {

    public static final List<String> ACTIVE = List.of("COUNTING", "SUBMITTED");

    private final CountMapper countMapper;
    private final CountLineMapper lineMapper;

    public CountFreeze(CountMapper countMapper, CountLineMapper lineMapper) {
        this.countMapper = countMapper;
        this.lineMapper = lineMapper;
    }

    /** @return 冻结该物料的盘点单号，未冻结时为 null */
    public String frozenBy(Long warehouseId, Long materialId) {
        List<CountDO> active = countMapper.selectList(new LambdaQueryWrapper<CountDO>().in(CountDO::getCountStatus, ACTIVE));
        if (active.isEmpty()) return null;
        Map<Long, CountDO> byId = active.stream().collect(Collectors.toMap(CountDO::getId, c -> c));
        for (CountDO c : active) {
            if ("FULL".equals(c.getCountType()) && ids(c.getWarehouseIds()).contains(warehouseId)) return c.getDocNo();
        }
        CountLineDO hit = lineMapper.selectOne(new LambdaQueryWrapper<CountLineDO>().in(CountLineDO::getCountId, byId.keySet())
                .eq(CountLineDO::getWarehouseId, warehouseId).eq(CountLineDO::getMaterialId, materialId).last("LIMIT 1"));
        return hit == null ? null : byId.get(hit.getCountId()).getDocNo();
    }

    static List<Long> ids(String csv) {
        if (csv == null || csv.isBlank()) return List.of();
        return Arrays.stream(csv.split(",")).map(String::trim).filter(s -> !s.isEmpty()).map(Long::valueOf).toList();
    }
}
