package com.erp.module.system.dal.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.framework.mybatis.BaseMapperX;
import com.erp.module.system.dal.dataobject.RoleDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface RoleMapper extends BaseMapperX<RoleDO> {

    default RoleDO selectByCode(String code) {
        return selectOne(new LambdaQueryWrapper<RoleDO>().eq(RoleDO::getCode, code));
    }

    default RoleDO selectByName(String name) {
        return selectOne(new LambdaQueryWrapper<RoleDO>().eq(RoleDO::getName, name));
    }

    // ---------- 角色权限（技术表） ----------

    @Select("SELECT permission FROM sys_role_permission WHERE role_id = #{roleId} ORDER BY permission")
    List<String> selectPermissions(@Param("roleId") Long roleId);

    @Insert("INSERT INTO sys_role_permission (role_id, permission) VALUES (#{roleId}, #{permission})")
    int insertPermission(@Param("roleId") Long roleId, @Param("permission") String permission);

    @Delete("DELETE FROM sys_role_permission WHERE role_id = #{roleId}")
    int deletePermissions(@Param("roleId") Long roleId);

    // ---------- 自定义数据范围（技术表） ----------

    @Select("SELECT dept_id FROM sys_role_data_dept WHERE role_id = #{roleId}")
    List<Long> selectDataDeptIds(@Param("roleId") Long roleId);

    @Insert("INSERT INTO sys_role_data_dept (role_id, dept_id) VALUES (#{roleId}, #{deptId})")
    int insertDataDept(@Param("roleId") Long roleId, @Param("deptId") Long deptId);

    @Delete("DELETE FROM sys_role_data_dept WHERE role_id = #{roleId}")
    int deleteDataDepts(@Param("roleId") Long roleId);
}
