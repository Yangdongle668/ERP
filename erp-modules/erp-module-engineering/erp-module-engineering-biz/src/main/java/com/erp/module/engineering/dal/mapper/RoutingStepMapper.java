package com.erp.module.engineering.dal.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.framework.mybatis.BaseMapperX;
import com.erp.module.engineering.dal.dataobject.RoutingStepDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

@Mapper
public interface RoutingStepMapper extends BaseMapperX<RoutingStepDO> {

    default List<RoutingStepDO> selectByParent(Long parentId) {
        return selectList(new LambdaQueryWrapper<RoutingStepDO>().eq(RoutingStepDO::getRoutingId, parentId).orderByAsc(RoutingStepDO::getSeq));
    }

    default List<RoutingStepDO> selectByParents(Collection<Long> parentIds) {
        if (parentIds.isEmpty()) return List.of();
        return selectList(new LambdaQueryWrapper<RoutingStepDO>().in(RoutingStepDO::getRoutingId, parentIds).orderByAsc(RoutingStepDO::getSeq));
    }

    /** 物理删除（明细随单据保存整体替换） */
    @Delete("DELETE FROM eng_routing_step WHERE routing_id = #{parentId}")
    int deleteByParent(@Param("parentId") Long parentId);
}
