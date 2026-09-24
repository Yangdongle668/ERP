package com.erp.framework.security;

import java.util.Optional;

/**
 * 扩展点：根据用户 ID 加载登录用户及权限。由系统管理模块实现。
 *
 * <p>框架层只定义接口，不依赖系统管理模块，保证依赖方向始终是“业务 → 框架”。
 * 实现方负责缓存（每次请求都会调用）。
 */
public interface LoginUserLoader {

    /** 用户不存在或已停用时返回 empty，请求将被视为未登录。 */
    Optional<LoginUser> load(Long userId);
}
