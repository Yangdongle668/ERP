package com.erp.module.quality.dal.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.framework.mybatis.BaseMapperX;
import com.erp.module.quality.dal.dataobject.QcNcrDispositionDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

@Mapper
public interface QcNcrDispositionMapper extends BaseMapperX<QcNcrDispositionDO> {

    default List<QcNcrDispositionDO> selectByParent(Long parentId) {
        return selectList(new LambdaQueryWrapper<QcNcrDispositionDO>().eq(QcNcrDispositionDO::getNcrId, parentId).orderByAsc(QcNcrDispositionDO::getSeq));
    }

    default List<QcNcrDispositionDO> selectByParents(Collection<Long> parentIds) {
        if (parentIds == null || parentIds.isEmpty()) return List.of();
        return selectList(new LambdaQueryWrapper<QcNcrDispositionDO>().in(QcNcrDispositionDO::getNcrId, parentIds).orderByAsc(QcNcrDispositionDO::getSeq));
    }

    /** 物理删除（明细随单据保存整体替换） */
    @Delete("DELETE FROM qc_ncr_disposition WHERE ncr_id = #{parentId}")
    int deleteByParent(@Param("parentId") Long parentId);
}
