package com.erp.module.system.service.support;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

import java.util.concurrent.Callable;
import java.util.function.Supplier;

/**
 * 系统管理模块的缓存（经 Spring CacheManager，单机为 Caffeine，多实例部署时替换为 Redis 即可）。
 * 数据变更后调用对应的 evict 方法，保证修改立即生效。
 */
@Component
public class SystemCaches {

    public static final String LOGIN_USER = "loginUser";
    public static final String DICT = "sys:dict";
    public static final String PARAM = "sys:param";
    public static final String ORG = "sys:org";
    public static final String UOM = "sys:uom";
    public static final String CURRENCY = "sys:currency";

    private final CacheManager cacheManager;

    public SystemCaches(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    public <T> T get(String cache, Object key, Callable<T> loader) {
        return cache(cache).get(key, loader);
    }

    /** 加载结果可能为空（不缓存空值，下次重新加载） */
    @SuppressWarnings("unchecked")
    public <T> T getNullable(String cache, Object key, Supplier<T> loader) {
        Cache c = cache(cache);
        Cache.ValueWrapper w = c.get(key);
        if (w != null) return (T) w.get();
        T v = loader.get();
        if (v != null) c.put(key, v);
        return v;
    }

    public void evict(String cache, Object key) {
        cache(cache).evict(key);
    }

    public void clear(String cache) {
        cache(cache).clear();
    }

    /** 权限、角色、组织、数据范围变化后清除全部登录用户缓存，使权限立即生效 */
    public void clearLoginUsers() {
        clear(LOGIN_USER);
    }

    public void evictLoginUser(Long userId) {
        evict(LOGIN_USER, userId);
    }

    private Cache cache(String name) {
        Cache c = cacheManager.getCache(name);
        if (c == null) throw new IllegalStateException("缓存不存在: " + name);
        return c;
    }
}
