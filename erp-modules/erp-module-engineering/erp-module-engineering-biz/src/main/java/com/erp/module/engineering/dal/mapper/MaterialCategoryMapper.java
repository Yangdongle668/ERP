package com.erp.module.engineering.dal.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.framework.mybatis.BaseMapperX;
import com.erp.module.engineering.dal.dataobject.MaterialCategoryDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface MaterialCategoryMapper extends BaseMapperX<MaterialCategoryDO> {

    default MaterialCategoryDO selectByCode(String code) {
        return selectOne(new LambdaQueryWrapper<MaterialCategoryDO>().eq(MaterialCategoryDO::getCode, code));
    }

    /** 下级（不含自身） */
    default List<MaterialCategoryDO> selectDescendants(String path) {
        return selectList(new LambdaQueryWrapper<MaterialCategoryDO>().likeRight(MaterialCategoryDO::getPath, path)
                .ne(MaterialCategoryDO::getPath, path));
    }

    default List<MaterialCategoryDO> selectChildren(Long parentId) {
        return selectList(new LambdaQueryWrapper<MaterialCategoryDO>()
                .isNull(parentId == null, MaterialCategoryDO::getParentId)
                .eq(parentId != null, MaterialCategoryDO::getParentId, parentId));
    }

    /** 移动节点：替换全部下级的路径前缀并调整层级 */
    @Update("UPDATE eng_material_category SET path = CONCAT(#{newPath}, SUBSTRING(path, #{oldLen} + 1)), level = level + #{levelDelta} "
            + "WHERE path LIKE CONCAT(#{oldPath}, '%') AND path <> #{oldPath} AND deleted = 0")
    int updateDescendantPaths(@Param("oldPath") String oldPath, @Param("oldLen") int oldLen, @Param("newPath") String newPath,
                              @Param("levelDelta") int levelDelta);
}
