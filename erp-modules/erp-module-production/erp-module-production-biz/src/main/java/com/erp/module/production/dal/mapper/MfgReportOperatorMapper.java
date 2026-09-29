package com.erp.module.production.dal.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.framework.mybatis.BaseMapperX;
import com.erp.module.production.dal.dataobject.MfgReportOperatorDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

@Mapper
public interface MfgReportOperatorMapper extends BaseMapperX<MfgReportOperatorDO> {

    default List<MfgReportOperatorDO> selectByParent(Long parentId) {
        return selectList(new LambdaQueryWrapper<MfgReportOperatorDO>().eq(MfgReportOperatorDO::getReportId, parentId).orderByAsc(MfgReportOperatorDO::getId));
    }

    default List<MfgReportOperatorDO> selectByParents(Collection<Long> parentIds) {
        if (parentIds == null || parentIds.isEmpty()) return List.of();
        return selectList(new LambdaQueryWrapper<MfgReportOperatorDO>().in(MfgReportOperatorDO::getReportId, parentIds).orderByAsc(MfgReportOperatorDO::getId));
    }

    /** 物理删除（明细随单据保存整体替换） */
    @Delete("DELETE FROM mfg_report_operator WHERE report_id = #{parentId}")
    int deleteByParent(@Param("parentId") Long parentId);
}
