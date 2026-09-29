package com.erp.module.finance.dal.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.framework.mybatis.BaseMapperX;
import com.erp.module.finance.dal.dataobject.FinPurchaseInvoiceLineDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

@Mapper
public interface FinPurchaseInvoiceLineMapper extends BaseMapperX<FinPurchaseInvoiceLineDO> {

    default List<FinPurchaseInvoiceLineDO> selectByParent(Long parentId) {
        return selectList(new LambdaQueryWrapper<FinPurchaseInvoiceLineDO>().eq(FinPurchaseInvoiceLineDO::getInvoiceId, parentId).orderByAsc(FinPurchaseInvoiceLineDO::getLineNo));
    }

    default List<FinPurchaseInvoiceLineDO> selectByParents(Collection<Long> parentIds) {
        if (parentIds == null || parentIds.isEmpty()) return List.of();
        return selectList(new LambdaQueryWrapper<FinPurchaseInvoiceLineDO>().in(FinPurchaseInvoiceLineDO::getInvoiceId, parentIds).orderByAsc(FinPurchaseInvoiceLineDO::getLineNo));
    }

    /** 物理删除（明细随单据保存整体替换） */
    @Delete("DELETE FROM fin_purchase_invoice_line WHERE invoice_id = #{parentId}")
    int deleteByParent(@Param("parentId") Long parentId);
}
