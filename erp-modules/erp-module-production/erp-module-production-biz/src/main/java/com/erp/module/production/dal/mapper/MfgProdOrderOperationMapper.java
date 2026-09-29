package com.erp.module.production.dal.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.framework.mybatis.BaseMapperX;
import com.erp.module.production.dal.dataobject.MfgProdOrderOperationDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

@Mapper
public interface MfgProdOrderOperationMapper extends BaseMapperX<MfgProdOrderOperationDO> {

    default List<MfgProdOrderOperationDO> selectByParent(Long parentId) {
        return selectList(new LambdaQueryWrapper<MfgProdOrderOperationDO>().eq(MfgProdOrderOperationDO::getProdOrderId, parentId).orderByAsc(MfgProdOrderOperationDO::getSeq));
    }

    default List<MfgProdOrderOperationDO> selectByParents(Collection<Long> parentIds) {
        if (parentIds == null || parentIds.isEmpty()) return List.of();
        return selectList(new LambdaQueryWrapper<MfgProdOrderOperationDO>().in(MfgProdOrderOperationDO::getProdOrderId, parentIds).orderByAsc(MfgProdOrderOperationDO::getSeq));
    }

    /** 物理删除（明细随单据保存整体替换） */
    @Delete("DELETE FROM mfg_prod_order_operation WHERE prod_order_id = #{parentId}")
    int deleteByParent(@Param("parentId") Long parentId);
}
