package com.erp.module.engineering.dal.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.framework.mybatis.BaseMapperX;
import com.erp.module.engineering.dal.dataobject.EcnLineDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

@Mapper
public interface EcnLineMapper extends BaseMapperX<EcnLineDO> {

    default List<EcnLineDO> selectByParent(Long parentId) {
        return selectList(new LambdaQueryWrapper<EcnLineDO>().eq(EcnLineDO::getEcnId, parentId).orderByAsc(EcnLineDO::getLineNo));
    }

    default List<EcnLineDO> selectByParents(Collection<Long> parentIds) {
        if (parentIds.isEmpty()) return List.of();
        return selectList(new LambdaQueryWrapper<EcnLineDO>().in(EcnLineDO::getEcnId, parentIds).orderByAsc(EcnLineDO::getLineNo));
    }

    /** 物理删除（明细随单据保存整体替换） */
    @Delete("DELETE FROM eng_ecn_line WHERE ecn_id = #{parentId}")
    int deleteByParent(@Param("parentId") Long parentId);
}
