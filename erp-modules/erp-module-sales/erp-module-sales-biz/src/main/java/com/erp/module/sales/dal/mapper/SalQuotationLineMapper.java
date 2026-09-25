package com.erp.module.sales.dal.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.framework.mybatis.BaseMapperX;
import com.erp.module.sales.dal.dataobject.SalQuotationLineDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

@Mapper
public interface SalQuotationLineMapper extends BaseMapperX<SalQuotationLineDO> {

    default List<SalQuotationLineDO> selectByParent(Long parentId) {
        return selectList(new LambdaQueryWrapper<SalQuotationLineDO>().eq(SalQuotationLineDO::getQuotationId, parentId).orderByAsc(SalQuotationLineDO::getLineNo));
    }

    default List<SalQuotationLineDO> selectByParents(Collection<Long> parentIds) {
        if (parentIds == null || parentIds.isEmpty()) return List.of();
        return selectList(new LambdaQueryWrapper<SalQuotationLineDO>().in(SalQuotationLineDO::getQuotationId, parentIds).orderByAsc(SalQuotationLineDO::getLineNo));
    }

    /** 物理删除（明细随单据保存整体替换） */
    @Delete("DELETE FROM sal_quotation_line WHERE quotation_id = #{parentId}")
    int deleteByParent(@Param("parentId") Long parentId);
}
