package com.erp.module.engineering.dal.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.common.enums.DocStatus;
import com.erp.common.result.PageParam;
import com.erp.common.result.PageResult;
import com.erp.framework.mybatis.BaseMapperX;
import com.erp.module.engineering.dal.dataobject.BomDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

@Mapper
public interface BomMapper extends BaseMapperX<BomDO> {

    default List<BomDO> selectByMaterial(Long materialId) {
        return selectList(new LambdaQueryWrapper<BomDO>().eq(BomDO::getMaterialId, materialId).orderByDesc(BomDO::getBomVersion));
    }

    default BomDO selectDefault(Long materialId) {
        return selectOne(new LambdaQueryWrapper<BomDO>().eq(BomDO::getMaterialId, materialId).eq(BomDO::getIsDefault, true)
                .eq(BomDO::getStatus, DocStatus.APPROVED).last("LIMIT 1"));
    }

    /** 全部已审核的默认版本（展开、低位码计算） */
    default List<BomDO> selectAllDefaults() {
        return selectList(new LambdaQueryWrapper<BomDO>().eq(BomDO::getIsDefault, true).eq(BomDO::getStatus, DocStatus.APPROVED));
    }

    default List<BomDO> selectByMaterials(Collection<Long> materialIds, Collection<DocStatus> statuses) {
        if (materialIds.isEmpty()) return List.of();
        return selectList(new LambdaQueryWrapper<BomDO>().in(BomDO::getMaterialId, materialIds).in(BomDO::getStatus, statuses));
    }

    @Select("SELECT COALESCE(MAX(bom_version), 0) FROM eng_bom WHERE material_id = #{materialId} AND deleted = 0")
    int maxVersion(Long materialId);

    default PageResult<BomDO> page(PageParam p, LambdaQueryWrapper<BomDO> q) {
        return selectPage(p, q);
    }
}
