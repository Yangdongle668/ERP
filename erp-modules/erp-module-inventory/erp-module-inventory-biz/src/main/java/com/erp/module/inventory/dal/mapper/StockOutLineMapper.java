package com.erp.module.inventory.dal.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.framework.mybatis.BaseMapperX;
import com.erp.module.inventory.dal.dataobject.StockOutLineDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

@Mapper
public interface StockOutLineMapper extends BaseMapperX<StockOutLineDO> {

    default List<StockOutLineDO> selectByDoc(Long docId) {
        return selectList(new LambdaQueryWrapper<StockOutLineDO>().eq(StockOutLineDO::getStockOutId, docId).orderByAsc(StockOutLineDO::getLineNo));
    }

    default List<StockOutLineDO> selectByDocs(Collection<Long> docIds) {
        if (docIds.isEmpty()) return List.of();
        return selectList(new LambdaQueryWrapper<StockOutLineDO>().in(StockOutLineDO::getStockOutId, docIds).orderByAsc(StockOutLineDO::getLineNo));
    }

    /** 物理删除（行随单据保存整体替换） */
    @Delete("DELETE FROM inv_stock_out_line WHERE stock_out_id = #{docId}")
    int deleteByDoc(@Param("docId") Long docId);
}
