package com.erp.module.inventory.dal.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.framework.mybatis.BaseMapperX;
import com.erp.module.inventory.dal.dataobject.StockInLineDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

@Mapper
public interface StockInLineMapper extends BaseMapperX<StockInLineDO> {

    default List<StockInLineDO> selectByDoc(Long docId) {
        return selectList(new LambdaQueryWrapper<StockInLineDO>().eq(StockInLineDO::getStockInId, docId).orderByAsc(StockInLineDO::getLineNo));
    }

    default List<StockInLineDO> selectByDocs(Collection<Long> docIds) {
        if (docIds.isEmpty()) return List.of();
        return selectList(new LambdaQueryWrapper<StockInLineDO>().in(StockInLineDO::getStockInId, docIds).orderByAsc(StockInLineDO::getLineNo));
    }

    /** 物理删除（行随单据保存整体替换） */
    @Delete("DELETE FROM inv_stock_in_line WHERE stock_in_id = #{docId}")
    int deleteByDoc(@Param("docId") Long docId);
}
