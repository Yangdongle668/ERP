package com.erp.module.purchase.dal.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.framework.mybatis.BaseMapperX;
import com.erp.module.purchase.dal.dataobject.RequisitionLineDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

@Mapper
public interface RequisitionLineMapper extends BaseMapperX<RequisitionLineDO> {

    default List<RequisitionLineDO> selectByParent(Long parentId) {
        return selectList(new LambdaQueryWrapper<RequisitionLineDO>().eq(RequisitionLineDO::getRequisitionId, parentId).orderByAsc(RequisitionLineDO::getLineNo));
    }

    default List<RequisitionLineDO> selectByParents(Collection<Long> parentIds) {
        if (parentIds == null || parentIds.isEmpty()) return List.of();
        return selectList(new LambdaQueryWrapper<RequisitionLineDO>().in(RequisitionLineDO::getRequisitionId, parentIds).orderByAsc(RequisitionLineDO::getLineNo));
    }

    /** 物理删除（明细随单据保存整体替换） */
    @Delete("DELETE FROM pur_requisition_line WHERE requisition_id = #{parentId}")
    int deleteByParent(@Param("parentId") Long parentId);
}
