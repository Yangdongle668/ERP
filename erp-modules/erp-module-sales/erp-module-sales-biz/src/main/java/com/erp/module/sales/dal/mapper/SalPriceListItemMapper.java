package com.erp.module.sales.dal.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.framework.mybatis.BaseMapperX;
import com.erp.module.sales.dal.dataobject.SalPriceListItemDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

@Mapper
public interface SalPriceListItemMapper extends BaseMapperX<SalPriceListItemDO> {

    default List<SalPriceListItemDO> selectByParent(Long parentId) {
        return selectList(new LambdaQueryWrapper<SalPriceListItemDO>().eq(SalPriceListItemDO::getPriceListId, parentId).orderByAsc(SalPriceListItemDO::getLineNo));
    }

    default List<SalPriceListItemDO> selectByParents(Collection<Long> parentIds) {
        if (parentIds == null || parentIds.isEmpty()) return List.of();
        return selectList(new LambdaQueryWrapper<SalPriceListItemDO>().in(SalPriceListItemDO::getPriceListId, parentIds).orderByAsc(SalPriceListItemDO::getLineNo));
    }

    /** 物理删除（明细随单据保存整体替换） */
    @Delete("DELETE FROM sal_price_list_item WHERE price_list_id = #{parentId}")
    int deleteByParent(@Param("parentId") Long parentId);
}
