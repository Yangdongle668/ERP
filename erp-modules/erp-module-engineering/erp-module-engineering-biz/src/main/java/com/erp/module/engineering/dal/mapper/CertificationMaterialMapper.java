package com.erp.module.engineering.dal.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.framework.mybatis.BaseMapperX;
import com.erp.module.engineering.dal.dataobject.CertificationMaterialDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

@Mapper
public interface CertificationMaterialMapper extends BaseMapperX<CertificationMaterialDO> {

    default List<CertificationMaterialDO> selectByParent(Long parentId) {
        return selectList(new LambdaQueryWrapper<CertificationMaterialDO>().eq(CertificationMaterialDO::getCertificationId, parentId).orderByAsc(CertificationMaterialDO::getId));
    }

    default List<CertificationMaterialDO> selectByParents(Collection<Long> parentIds) {
        if (parentIds.isEmpty()) return List.of();
        return selectList(new LambdaQueryWrapper<CertificationMaterialDO>().in(CertificationMaterialDO::getCertificationId, parentIds).orderByAsc(CertificationMaterialDO::getId));
    }

    /** 物理删除（明细随单据保存整体替换） */
    @Delete("DELETE FROM eng_certification_material WHERE certification_id = #{parentId}")
    int deleteByParent(@Param("parentId") Long parentId);
}
