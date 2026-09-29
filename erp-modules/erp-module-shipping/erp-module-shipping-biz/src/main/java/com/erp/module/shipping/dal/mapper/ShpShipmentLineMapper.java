package com.erp.module.shipping.dal.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.framework.mybatis.BaseMapperX;
import com.erp.module.shipping.dal.dataobject.ShpShipmentLineDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

@Mapper
public interface ShpShipmentLineMapper extends BaseMapperX<ShpShipmentLineDO> {

    default List<ShpShipmentLineDO> selectByParent(Long parentId) {
        return selectList(new LambdaQueryWrapper<ShpShipmentLineDO>().eq(ShpShipmentLineDO::getShipmentId, parentId).orderByAsc(ShpShipmentLineDO::getLineNo));
    }

    default List<ShpShipmentLineDO> selectByParents(Collection<Long> parentIds) {
        if (parentIds == null || parentIds.isEmpty()) return List.of();
        return selectList(new LambdaQueryWrapper<ShpShipmentLineDO>().in(ShpShipmentLineDO::getShipmentId, parentIds).orderByAsc(ShpShipmentLineDO::getLineNo));
    }

    /** 物理删除（明细随单据保存整体替换） */
    @Delete("DELETE FROM shp_shipment_line WHERE shipment_id = #{parentId}")
    int deleteByParent(@Param("parentId") Long parentId);
}
