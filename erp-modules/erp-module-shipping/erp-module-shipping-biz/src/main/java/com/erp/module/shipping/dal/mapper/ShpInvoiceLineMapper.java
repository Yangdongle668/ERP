package com.erp.module.shipping.dal.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.framework.mybatis.BaseMapperX;
import com.erp.module.shipping.dal.dataobject.ShpInvoiceLineDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

@Mapper
public interface ShpInvoiceLineMapper extends BaseMapperX<ShpInvoiceLineDO> {

    default List<ShpInvoiceLineDO> selectByParent(Long parentId) {
        return selectList(new LambdaQueryWrapper<ShpInvoiceLineDO>().eq(ShpInvoiceLineDO::getInvoiceId, parentId).orderByAsc(ShpInvoiceLineDO::getLineNo));
    }

    default List<ShpInvoiceLineDO> selectByParents(Collection<Long> parentIds) {
        if (parentIds == null || parentIds.isEmpty()) return List.of();
        return selectList(new LambdaQueryWrapper<ShpInvoiceLineDO>().in(ShpInvoiceLineDO::getInvoiceId, parentIds).orderByAsc(ShpInvoiceLineDO::getLineNo));
    }

    /** 物理删除（明细随单据保存整体替换） */
    @Delete("DELETE FROM shp_invoice_line WHERE invoice_id = #{parentId}")
    int deleteByParent(@Param("parentId") Long parentId);
}
