package com.erp.module.system.dal.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.framework.mybatis.BaseMapperX;
import com.erp.module.system.dal.dataobject.UserDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.Set;

@Mapper
public interface UserMapper extends BaseMapperX<UserDO> {

    default UserDO selectByUsername(String username) {
        return selectOne(new LambdaQueryWrapper<UserDO>().eq(UserDO::getUsername, username));
    }

    /** 用户所有启用角色的权限标识。 */
    @Select("""
            SELECT DISTINCT rp.permission
              FROM sys_role_permission rp
              JOIN sys_user_role ur ON ur.role_id = rp.role_id
              JOIN sys_role r ON r.id = rp.role_id AND r.status = 'ENABLED' AND r.deleted = 0
             WHERE ur.user_id = #{userId}
            """)
    Set<String> selectPermissions(@Param("userId") Long userId);

    /** 登录失败计数；达到阈值时同时设置锁定时间。不走乐观锁，避免并发登录互相覆盖。 */
    @Update("""
            UPDATE sys_user
               SET fail_count = fail_count + 1,
                   lock_until = CASE WHEN fail_count + 1 >= #{maxFail} THEN #{lockUntil} ELSE lock_until END
             WHERE id = #{id}
            """)
    int increaseFailCount(@Param("id") Long id, @Param("maxFail") int maxFail, @Param("lockUntil") LocalDateTime lockUntil);

    @Update("UPDATE sys_user SET fail_count = 0, lock_until = NULL, last_login_at = #{now} WHERE id = #{id}")
    int markLoginSuccess(@Param("id") Long id, @Param("now") LocalDateTime now);
}
