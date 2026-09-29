package com.erp.module.production.dal.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.framework.mybatis.BaseMapperX;
import com.erp.module.production.dal.dataobject.MfgReturnLineDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

@Mapper
public interface MfgReturnLineMapper extends BaseMapperX<MfgReturnLineDO> {

    default List<MfgReturnLineDO> selectByParent(Long parentId) {
        return selectList(new LambdaQueryWrapper<MfgReturnLineDO>().eq(MfgReturnLineDO::getReturnId, parentId).orderByAsc(MfgReturnLineDO::getLineNo));
    }

    default List<MfgReturnLineDO> selectByParents(Collection<Long> parentIds) {
        if (parentIds == null || parentIds.isEmpty()) return List.of();
        return selectList(new LambdaQueryWrapper<MfgReturnLineDO>().in(MfgReturnLineDO::getReturnId, parentIds).orderByAsc(MfgReturnLineDO::getLineNo));
    }

    /** 物理删除（明细随单据保存整体替换） */
    @Delete("DELETE FROM mfg_return_line WHERE return_id = #{parentId}")
    int deleteByParent(@Param("parentId") Long parentId);
}
