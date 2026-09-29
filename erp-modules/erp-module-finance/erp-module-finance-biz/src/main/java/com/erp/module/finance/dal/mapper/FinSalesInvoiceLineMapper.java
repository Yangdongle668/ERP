package com.erp.module.finance.dal.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.framework.mybatis.BaseMapperX;
import com.erp.module.finance.dal.dataobject.FinSalesInvoiceLineDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

@Mapper
public interface FinSalesInvoiceLineMapper extends BaseMapperX<FinSalesInvoiceLineDO> {

    default List<FinSalesInvoiceLineDO> selectByParent(Long parentId) {
        return selectList(new LambdaQueryWrapper<FinSalesInvoiceLineDO>().eq(FinSalesInvoiceLineDO::getInvoiceId, parentId).orderByAsc(FinSalesInvoiceLineDO::getLineNo));
    }

    default List<FinSalesInvoiceLineDO> selectByParents(Collection<Long> parentIds) {
        if (parentIds == null || parentIds.isEmpty()) return List.of();
        return selectList(new LambdaQueryWrapper<FinSalesInvoiceLineDO>().in(FinSalesInvoiceLineDO::getInvoiceId, parentIds).orderByAsc(FinSalesInvoiceLineDO::getLineNo));
    }

    /** 物理删除（明细随单据保存整体替换） */
    @Delete("DELETE FROM fin_sales_invoice_line WHERE invoice_id = #{parentId}")
    int deleteByParent(@Param("parentId") Long parentId);
}
