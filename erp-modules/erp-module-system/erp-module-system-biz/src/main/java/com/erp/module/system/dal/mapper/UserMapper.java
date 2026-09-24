package com.erp.module.system.dal.mapper;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import com.erp.common.enums.EnableStatus;
import com.erp.framework.datascope.DataScope;
import com.erp.framework.mybatis.BaseMapperX;
import com.erp.module.system.dal.dataobject.UserDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Mapper
public interface UserMapper extends BaseMapperX<UserDO> {

    /** 用户列表分页受数据范围约束（SYS-USR-R11）：“仅本人”只能看到自己 */
    @Override
    @DataScope(orgColumn = "org_id", deptColumn = "dept_id", userColumn = "id")
    <P extends IPage<UserDO>> P selectPage(P page, @Param(Constants.WRAPPER) Wrapper<UserDO> queryWrapper);

    /** 用户名唯一（存小写，比较时忽略大小写） */
    default UserDO selectByUsername(String username) {
        return selectOne(new LambdaQueryWrapper<UserDO>().eq(UserDO::getUsername, username == null ? null : username.toLowerCase()));
    }

    default UserDO selectByEmployeeNo(String employeeNo) {
        return selectOne(new LambdaQueryWrapper<UserDO>().eq(UserDO::getEmployeeNo, employeeNo));
    }

    default UserDO selectByMobile(String mobile) {
        return selectOne(new LambdaQueryWrapper<UserDO>().eq(UserDO::getMobile, mobile));
    }

    default long countEnabledByDept(Long deptId) {
        return selectCount(new LambdaQueryWrapper<UserDO>().eq(UserDO::getDeptId, deptId).eq(UserDO::getStatus, EnableStatus.ENABLED));
    }

    /** 主部门或兼职部门为该组织的用户数（含停用） */
    @Select("""
            SELECT COUNT(*) FROM sys_user u
             WHERE u.deleted = 0 AND (u.dept_id = #{orgId} OR u.org_id = #{orgId}
                   OR EXISTS (SELECT 1 FROM sys_user_dept d WHERE d.user_id = u.id AND d.dept_id = #{orgId}))
            """)
    long countAllByOrg(@Param("orgId") Long orgId);

    /** 各部门的启用用户数（主部门） */
    @Select("SELECT dept_id AS id, COUNT(*) AS cnt FROM sys_user WHERE deleted = 0 AND status = 'ENABLED' GROUP BY dept_id")
    List<IdCount> countEnabledGroupByDept();

    /** 用户所有启用角色的权限标识 */
    @Select("""
            SELECT DISTINCT rp.permission
              FROM sys_role_permission rp
              JOIN sys_user_role ur ON ur.role_id = rp.role_id
              JOIN sys_role r ON r.id = rp.role_id AND r.status = 'ENABLED' AND r.deleted = 0
             WHERE ur.user_id = #{userId}
            """)
    Set<String> selectPermissions(@Param("userId") Long userId);

    /** 拥有某权限（或 *）的启用用户 */
    @Select("""
            SELECT DISTINCT u.* FROM sys_user u
              JOIN sys_user_role ur ON ur.user_id = u.id
              JOIN sys_role r ON r.id = ur.role_id AND r.status = 'ENABLED' AND r.deleted = 0
              JOIN sys_role_permission rp ON rp.role_id = r.id
             WHERE u.deleted = 0 AND u.status = 'ENABLED' AND (rp.permission = #{permission} OR rp.permission = '*')
            """)
    List<UserDO> selectByPermission(@Param("permission") String permission);

    @Select("""
            SELECT u.* FROM sys_user u JOIN sys_user_role ur ON ur.user_id = u.id
             WHERE ur.role_id = #{roleId} AND u.deleted = 0
             ORDER BY u.username
            """)
    List<UserDO> selectByRole(@Param("roleId") Long roleId);

    /** 登录失败计数；达到阈值时同时设置锁定时间。不走乐观锁，避免并发登录互相覆盖。 */
    @Update("""
            UPDATE sys_user
               SET fail_count = fail_count + 1,
                   lock_until = CASE WHEN fail_count + 1 >= #{maxFail} THEN #{lockUntil} ELSE lock_until END
             WHERE id = #{id}
            """)
    int increaseFailCount(@Param("id") Long id, @Param("maxFail") int maxFail, @Param("lockUntil") LocalDateTime lockUntil);

    @Update("UPDATE sys_user SET fail_count = 0, lock_until = NULL, last_login_at = #{now}, last_login_ip = #{ip} WHERE id = #{id}")
    int markLoginSuccess(@Param("id") Long id, @Param("now") LocalDateTime now, @Param("ip") String ip);

    @Update("UPDATE sys_user SET fail_count = 0, lock_until = NULL WHERE id = #{id}")
    int unlock(@Param("id") Long id);

    /** 令牌版本 +1：使该用户已签发的全部令牌失效 */
    @Update("UPDATE sys_user SET token_version = token_version + 1 WHERE id = #{id}")
    int increaseTokenVersion(@Param("id") Long id);

    /** 通用计数结果 */
    record IdCount(Long id, Long cnt) {
    }
}
