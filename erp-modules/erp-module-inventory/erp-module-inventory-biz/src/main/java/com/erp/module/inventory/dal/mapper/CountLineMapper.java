package com.erp.module.inventory.dal.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.framework.mybatis.BaseMapperX;
import com.erp.module.inventory.dal.dataobject.CountLineDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

@Mapper
public interface CountLineMapper extends BaseMapperX<CountLineDO> {

    default List<CountLineDO> selectByDoc(Long docId) {
        return selectList(new LambdaQueryWrapper<CountLineDO>().eq(CountLineDO::getCountId, docId).orderByAsc(CountLineDO::getLineNo));
    }

    default List<CountLineDO> selectByDocs(Collection<Long> docIds) {
        if (docIds.isEmpty()) return List.of();
        return selectList(new LambdaQueryWrapper<CountLineDO>().in(CountLineDO::getCountId, docIds).orderByAsc(CountLineDO::getLineNo));
    }

    /** 物理删除（行随单据保存整体替换） */
    @Delete("DELETE FROM inv_count_line WHERE count_id = #{docId}")
    int deleteByDoc(@Param("docId") Long docId);
}
