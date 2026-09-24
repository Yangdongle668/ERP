package com.erp.module.system.dal.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** 用户-角色、用户-兼职部门（技术表，直接 SQL） */
@Mapper
public interface UserRoleMapper {

    @Select("SELECT role_id FROM sys_user_role WHERE user_id = #{userId}")
    List<Long> selectRoleIds(@Param("userId") Long userId);

    @Select("SELECT user_id FROM sys_user_role WHERE role_id = #{roleId}")
    List<Long> selectUserIds(@Param("roleId") Long roleId);

    @Select("SELECT COUNT(*) FROM sys_user_role WHERE role_id = #{roleId}")
    long countByRole(@Param("roleId") Long roleId);

    @Select("""
            SELECT ur.role_id AS id, COUNT(*) AS cnt FROM sys_user_role ur
              JOIN sys_user u ON u.id = ur.user_id AND u.deleted = 0 AND u.status = 'ENABLED'
             GROUP BY ur.role_id
            """)
    List<UserMapper.IdCount> countEnabledUsersGroupByRole();

    @Insert("INSERT INTO sys_user_role (user_id, role_id) VALUES (#{userId}, #{roleId})")
    int insert(@Param("userId") Long userId, @Param("roleId") Long roleId);

    @Delete("DELETE FROM sys_user_role WHERE user_id = #{userId}")
    int deleteByUser(@Param("userId") Long userId);

    @Delete("DELETE FROM sys_user_role WHERE user_id = #{userId} AND role_id = #{roleId}")
    int delete(@Param("userId") Long userId, @Param("roleId") Long roleId);

    @Select("SELECT dept_id FROM sys_user_dept WHERE user_id = #{userId}")
    List<Long> selectPartDeptIds(@Param("userId") Long userId);

    @Insert("INSERT INTO sys_user_dept (user_id, dept_id) VALUES (#{userId}, #{deptId})")
    int insertPartDept(@Param("userId") Long userId, @Param("deptId") Long deptId);

    @Delete("DELETE FROM sys_user_dept WHERE user_id = #{userId}")
    int deletePartDepts(@Param("userId") Long userId);

    /** 用户的启用角色数（不含指定角色），用于判断停用/移除角色后用户是否还有可用角色 */
    @Select("""
            SELECT COUNT(*) FROM sys_user_role ur JOIN sys_role r ON r.id = ur.role_id
             WHERE ur.user_id = #{userId} AND r.status = 'ENABLED' AND r.deleted = 0 AND r.id <> #{excludeRoleId}
            """)
    long countOtherEnabledRoles(@Param("userId") Long userId, @Param("excludeRoleId") Long excludeRoleId);
}
