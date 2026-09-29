package com.erp.module.production.dal.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.framework.mybatis.BaseMapperX;
import com.erp.module.production.dal.dataobject.MfgProdOrderMaterialDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

@Mapper
public interface MfgProdOrderMaterialMapper extends BaseMapperX<MfgProdOrderMaterialDO> {

    default List<MfgProdOrderMaterialDO> selectByParent(Long parentId) {
        return selectList(new LambdaQueryWrapper<MfgProdOrderMaterialDO>().eq(MfgProdOrderMaterialDO::getProdOrderId, parentId).orderByAsc(MfgProdOrderMaterialDO::getLineNo));
    }

    default List<MfgProdOrderMaterialDO> selectByParents(Collection<Long> parentIds) {
        if (parentIds == null || parentIds.isEmpty()) return List.of();
        return selectList(new LambdaQueryWrapper<MfgProdOrderMaterialDO>().in(MfgProdOrderMaterialDO::getProdOrderId, parentIds).orderByAsc(MfgProdOrderMaterialDO::getLineNo));
    }

    /** 物理删除（明细随单据保存整体替换） */
    @Delete("DELETE FROM mfg_prod_order_material WHERE prod_order_id = #{parentId}")
    int deleteByParent(@Param("parentId") Long parentId);
}
