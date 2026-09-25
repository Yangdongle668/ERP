package com.erp.module.purchase.dal.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.framework.mybatis.BaseMapperX;
import com.erp.module.purchase.dal.dataobject.OutsourcingMaterialDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

@Mapper
public interface OutsourcingMaterialMapper extends BaseMapperX<OutsourcingMaterialDO> {

    default List<OutsourcingMaterialDO> selectByParent(Long parentId) {
        return selectList(new LambdaQueryWrapper<OutsourcingMaterialDO>().eq(OutsourcingMaterialDO::getOutsourcingId, parentId).orderByAsc(OutsourcingMaterialDO::getLineNo));
    }

    default List<OutsourcingMaterialDO> selectByParents(Collection<Long> parentIds) {
        if (parentIds == null || parentIds.isEmpty()) return List.of();
        return selectList(new LambdaQueryWrapper<OutsourcingMaterialDO>().in(OutsourcingMaterialDO::getOutsourcingId, parentIds).orderByAsc(OutsourcingMaterialDO::getLineNo));
    }

    /** 物理删除（明细随单据保存整体替换） */
    @Delete("DELETE FROM pur_outsourcing_material WHERE outsourcing_id = #{parentId}")
    int deleteByParent(@Param("parentId") Long parentId);
}
