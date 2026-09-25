package com.erp.module.engineering.dal.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.framework.mybatis.BaseMapperX;
import com.erp.module.engineering.dal.dataobject.EcnTaskDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

@Mapper
public interface EcnTaskMapper extends BaseMapperX<EcnTaskDO> {

    default List<EcnTaskDO> selectByParent(Long parentId) {
        return selectList(new LambdaQueryWrapper<EcnTaskDO>().eq(EcnTaskDO::getEcnId, parentId).orderByAsc(EcnTaskDO::getId));
    }

    default List<EcnTaskDO> selectByParents(Collection<Long> parentIds) {
        if (parentIds.isEmpty()) return List.of();
        return selectList(new LambdaQueryWrapper<EcnTaskDO>().in(EcnTaskDO::getEcnId, parentIds).orderByAsc(EcnTaskDO::getId));
    }

    /** 物理删除（明细随单据保存整体替换） */
    @Delete("DELETE FROM eng_ecn_task WHERE ecn_id = #{parentId}")
    int deleteByParent(@Param("parentId") Long parentId);
}
