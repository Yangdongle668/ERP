package com.erp.module.engineering.dal.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.framework.mybatis.BaseMapperX;
import com.erp.module.engineering.dal.dataobject.BomLineDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

@Mapper
public interface BomLineMapper extends BaseMapperX<BomLineDO> {

    default List<BomLineDO> selectByBom(Long bomId) {
        return selectList(new LambdaQueryWrapper<BomLineDO>().eq(BomLineDO::getBomId, bomId).orderByAsc(BomLineDO::getLineNo));
    }

    default List<BomLineDO> selectByBoms(Collection<Long> bomIds) {
        if (bomIds.isEmpty()) return List.of();
        return selectList(new LambdaQueryWrapper<BomLineDO>().in(BomLineDO::getBomId, bomIds).orderByAsc(BomLineDO::getLineNo));
    }

    default List<BomLineDO> selectByComponent(Long componentId) {
        return selectList(new LambdaQueryWrapper<BomLineDO>().eq(BomLineDO::getComponentId, componentId));
    }

    /** 物理删除（行随单据保存整体替换） */
    @Delete("DELETE FROM eng_bom_line WHERE bom_id = #{bomId}")
    int deleteByBom(@Param("bomId") Long bomId);

    /**
     * 使用该物料作为子件或替代料的 BOM 数量。
     *
     * @param approvedOnly true 只统计已审核版本（停用提示 R08），false 统计全部未作废版本（删除 R09）
     */
    @Select("<script>SELECT COUNT(DISTINCT b.id) FROM eng_bom b WHERE b.deleted = 0 AND b.status != 'VOIDED'"
            + "<if test='approvedOnly'> AND b.status = 'APPROVED'</if>"
            + " AND (EXISTS (SELECT 1 FROM eng_bom_line l WHERE l.bom_id = b.id AND l.deleted = 0 AND l.component_id = #{materialId})"
            + " OR EXISTS (SELECT 1 FROM eng_bom_substitute s WHERE s.bom_id = b.id AND s.deleted = 0 AND s.substitute_id = #{materialId}))</script>")
    long countBomsUsing(@Param("materialId") Long materialId, @Param("approvedOnly") boolean approvedOnly);

    @Select("SELECT COUNT(*) FROM eng_bom WHERE deleted = 0 AND material_id = #{materialId}")
    long countBomsAsParent(@Param("materialId") Long materialId);
}
