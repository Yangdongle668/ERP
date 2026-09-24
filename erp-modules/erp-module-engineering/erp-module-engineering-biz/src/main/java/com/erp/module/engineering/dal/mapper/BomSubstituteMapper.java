package com.erp.module.engineering.dal.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.framework.mybatis.BaseMapperX;
import com.erp.module.engineering.dal.dataobject.BomSubstituteDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

@Mapper
public interface BomSubstituteMapper extends BaseMapperX<BomSubstituteDO> {

    default List<BomSubstituteDO> selectByBoms(Collection<Long> bomIds) {
        if (bomIds.isEmpty()) return List.of();
        return selectList(new LambdaQueryWrapper<BomSubstituteDO>().in(BomSubstituteDO::getBomId, bomIds)
                .orderByAsc(BomSubstituteDO::getPriority));
    }

    @Delete("DELETE FROM eng_bom_substitute WHERE bom_id = #{bomId}")
    int deleteByBom(@Param("bomId") Long bomId);
}
