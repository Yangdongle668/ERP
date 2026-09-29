package com.erp.module.shipping.dal.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.framework.mybatis.BaseMapperX;
import com.erp.module.shipping.dal.dataobject.ShpNoticeLineDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

@Mapper
public interface ShpNoticeLineMapper extends BaseMapperX<ShpNoticeLineDO> {

    default List<ShpNoticeLineDO> selectByParent(Long parentId) {
        return selectList(new LambdaQueryWrapper<ShpNoticeLineDO>().eq(ShpNoticeLineDO::getNoticeId, parentId).orderByAsc(ShpNoticeLineDO::getLineNo));
    }

    default List<ShpNoticeLineDO> selectByParents(Collection<Long> parentIds) {
        if (parentIds == null || parentIds.isEmpty()) return List.of();
        return selectList(new LambdaQueryWrapper<ShpNoticeLineDO>().in(ShpNoticeLineDO::getNoticeId, parentIds).orderByAsc(ShpNoticeLineDO::getLineNo));
    }

    /** 物理删除（明细随单据保存整体替换） */
    @Delete("DELETE FROM shp_notice_line WHERE notice_id = #{parentId}")
    int deleteByParent(@Param("parentId") Long parentId);
}
