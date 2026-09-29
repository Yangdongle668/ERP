package com.erp.module.pmc.dal.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.framework.mybatis.BaseMapperX;
import com.erp.module.pmc.dal.dataobject.PmcMpsLineDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

@Mapper
public interface PmcMpsLineMapper extends BaseMapperX<PmcMpsLineDO> {

    default List<PmcMpsLineDO> selectByParent(Long parentId) {
        return selectList(new LambdaQueryWrapper<PmcMpsLineDO>().eq(PmcMpsLineDO::getMpsId, parentId).orderByAsc(PmcMpsLineDO::getId));
    }

    default List<PmcMpsLineDO> selectByParents(Collection<Long> parentIds) {
        if (parentIds == null || parentIds.isEmpty()) return List.of();
        return selectList(new LambdaQueryWrapper<PmcMpsLineDO>().in(PmcMpsLineDO::getMpsId, parentIds).orderByAsc(PmcMpsLineDO::getId));
    }

    /** 物理删除（明细随单据保存整体替换） */
    @Delete("DELETE FROM pmc_mps_line WHERE mps_id = #{parentId}")
    int deleteByParent(@Param("parentId") Long parentId);
}
