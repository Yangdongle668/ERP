package com.erp.module.system.service;

import com.erp.common.enums.EnableStatus;
import com.erp.framework.security.LoginUser;
import com.erp.framework.security.LoginUserLoader;
import com.erp.module.system.dal.dataobject.UserDO;
import com.erp.module.system.dal.mapper.UserMapper;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.Set;

/** 为框架提供登录用户与权限。结果缓存 10 分钟（见 CacheConfig），角色权限变更时应清除 loginUser 缓存。 */
@Service
public class LoginUserLoaderImpl implements LoginUserLoader {

    public static final String CACHE = "loginUser";

    private final UserMapper userMapper;

    public LoginUserLoaderImpl(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    @Cacheable(cacheNames = CACHE, key = "#userId", unless = "#result == null")
    public Optional<LoginUser> load(Long userId) {
        UserDO user = userMapper.selectById(userId);
        if (user == null || user.getStatus() != EnableStatus.ENABLED) {
            return Optional.empty();
        }
        Set<String> permissions = Set.copyOf(userMapper.selectPermissions(userId));
        return Optional.of(new LoginUser(user.getId(), user.getUsername(), user.getRealName(),
                user.getOrgId(), user.getDeptId(), permissions));
    }
}
