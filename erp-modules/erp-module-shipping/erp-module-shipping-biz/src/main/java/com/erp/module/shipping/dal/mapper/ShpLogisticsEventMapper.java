package com.erp.module.shipping.dal.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.framework.mybatis.BaseMapperX;
import com.erp.module.shipping.dal.dataobject.ShpLogisticsEventDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

@Mapper
public interface ShpLogisticsEventMapper extends BaseMapperX<ShpLogisticsEventDO> {

    default List<ShpLogisticsEventDO> selectByParent(Long parentId) {
        return selectList(new LambdaQueryWrapper<ShpLogisticsEventDO>().eq(ShpLogisticsEventDO::getShipmentId, parentId).orderByAsc(ShpLogisticsEventDO::getOccurredAt));
    }

    default List<ShpLogisticsEventDO> selectByParents(Collection<Long> parentIds) {
        if (parentIds == null || parentIds.isEmpty()) return List.of();
        return selectList(new LambdaQueryWrapper<ShpLogisticsEventDO>().in(ShpLogisticsEventDO::getShipmentId, parentIds).orderByAsc(ShpLogisticsEventDO::getOccurredAt));
    }

    /** 物理删除（明细随单据保存整体替换） */
    @Delete("DELETE FROM shp_logistics_event WHERE shipment_id = #{parentId}")
    int deleteByParent(@Param("parentId") Long parentId);
}
