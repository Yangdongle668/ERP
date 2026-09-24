package com.erp.framework.cache;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * 本地缓存（Caffeine）。所有缓存都设置过期时间作为兜底，防止数据变更后缓存长期不一致。
 * 多实例部署时替换为 Redis 的 CacheManager，业务代码中的 {@code @Cacheable} 不需要改。
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterWrite(Duration.ofMinutes(10)));
        manager.setAllowNullValues(false);
        return manager;
    }
}
