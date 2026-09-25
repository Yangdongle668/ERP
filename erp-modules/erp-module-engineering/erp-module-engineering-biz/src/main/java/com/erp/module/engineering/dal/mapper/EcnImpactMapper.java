package com.erp.module.engineering.dal.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.framework.mybatis.BaseMapperX;
import com.erp.module.engineering.dal.dataobject.EcnImpactDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

@Mapper
public interface EcnImpactMapper extends BaseMapperX<EcnImpactDO> {

    default List<EcnImpactDO> selectByParent(Long parentId) {
        return selectList(new LambdaQueryWrapper<EcnImpactDO>().eq(EcnImpactDO::getEcnId, parentId).orderByAsc(EcnImpactDO::getId));
    }

    default List<EcnImpactDO> selectByParents(Collection<Long> parentIds) {
        if (parentIds.isEmpty()) return List.of();
        return selectList(new LambdaQueryWrapper<EcnImpactDO>().in(EcnImpactDO::getEcnId, parentIds).orderByAsc(EcnImpactDO::getId));
    }

    /** 物理删除（明细随单据保存整体替换） */
    @Delete("DELETE FROM eng_ecn_impact WHERE ecn_id = #{parentId}")
    int deleteByParent(@Param("parentId") Long parentId);
}
