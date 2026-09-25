package com.erp.module.purchase.dal.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.framework.mybatis.BaseMapperX;
import com.erp.module.purchase.dal.dataobject.RfqQuoteDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

@Mapper
public interface RfqQuoteMapper extends BaseMapperX<RfqQuoteDO> {

    default List<RfqQuoteDO> selectByParent(Long parentId) {
        return selectList(new LambdaQueryWrapper<RfqQuoteDO>().eq(RfqQuoteDO::getRfqId, parentId).orderByAsc(RfqQuoteDO::getId));
    }

    default List<RfqQuoteDO> selectByParents(Collection<Long> parentIds) {
        if (parentIds == null || parentIds.isEmpty()) return List.of();
        return selectList(new LambdaQueryWrapper<RfqQuoteDO>().in(RfqQuoteDO::getRfqId, parentIds).orderByAsc(RfqQuoteDO::getId));
    }

    /** 物理删除（明细随单据保存整体替换） */
    @Delete("DELETE FROM pur_rfq_quote WHERE rfq_id = #{parentId}")
    int deleteByParent(@Param("parentId") Long parentId);
}
