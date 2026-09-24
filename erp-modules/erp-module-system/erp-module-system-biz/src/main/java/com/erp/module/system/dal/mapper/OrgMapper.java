package com.erp.module.system.dal.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.common.enums.EnableStatus;
import com.erp.framework.mybatis.BaseMapperX;
import com.erp.module.system.dal.dataobject.OrgDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface OrgMapper extends BaseMapperX<OrgDO> {

    default OrgDO selectByCode(String code) {
        return selectOne(new LambdaQueryWrapper<OrgDO>().eq(OrgDO::getCode, code));
    }

    default List<OrgDO> selectChildren(Long parentId) {
        return selectList(new LambdaQueryWrapper<OrgDO>().eq(OrgDO::getParentId, parentId));
    }

    /** 全部下级（不含自身） */
    default List<OrgDO> selectDescendants(String path) {
        return selectList(new LambdaQueryWrapper<OrgDO>().likeRight(OrgDO::getPath, path).ne(OrgDO::getPath, path));
    }

    default long countEnabledChildren(Long parentId) {
        return selectCount(new LambdaQueryWrapper<OrgDO>().eq(OrgDO::getParentId, parentId).eq(OrgDO::getStatus, EnableStatus.ENABLED));
    }

    /** 修改上级时批量更新全部下级的路径和层级（SYS-ORG-R09），与修改自身在同一事务 */
    @Update("""
            UPDATE sys_org
               SET path = CONCAT(#{newPrefix}, SUBSTRING(path, #{oldPrefixLength} + 1)),
                   org_level = org_level + #{levelDelta},
                   updated_at = CURRENT_TIMESTAMP
             WHERE path LIKE CONCAT(#{oldPrefix}, '%') AND path <> #{oldPrefix} AND deleted = 0
            """)
    int updateDescendantPaths(@Param("oldPrefix") String oldPrefix, @Param("oldPrefixLength") int oldPrefixLength,
                              @Param("newPrefix") String newPrefix, @Param("levelDelta") int levelDelta);
}
