package com.erp.framework.mybatis;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.BlockAttackInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.erp.common.result.PageParam;
import com.erp.framework.security.SecurityUtils;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.reflection.MetaObject;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import java.time.LocalDateTime;

/** MyBatis-Plus 全局配置。只扫描标注了 @Mapper 的接口。 */
@Configuration
@EnableTransactionManagement
@MapperScan(basePackages = "com.erp.module", annotationClass = Mapper.class)
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 防止全表 update/delete
        interceptor.addInnerInterceptor(new BlockAttackInnerInterceptor());
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor();
        pagination.setMaxLimit((long) PageParam.MAX_PAGE_SIZE);
        interceptor.addInnerInterceptor(pagination);
        return interceptor;
    }

    /** 自动填充审计字段。 */
    @Bean
    public MetaObjectHandler auditMetaObjectHandler() {
        return new MetaObjectHandler() {
            @Override
            public void insertFill(MetaObject metaObject) {
                LocalDateTime now = LocalDateTime.now();
                Long userId = SecurityUtils.getLoginUserIdOrNull();
                strictInsertFill(metaObject, "createdAt", LocalDateTime.class, now);
                strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, now);
                strictInsertFill(metaObject, "createdBy", Long.class, userId);
                strictInsertFill(metaObject, "updatedBy", Long.class, userId);
                strictInsertFill(metaObject, "version", Integer.class, 0);
                strictInsertFill(metaObject, "deleted", Boolean.class, Boolean.FALSE);
            }

            @Override
            public void updateFill(MetaObject metaObject) {
                // 使用 setFieldValByName 强制覆盖（strictUpdateFill 在字段已有值时不覆盖）
                setFieldValByName("updatedAt", LocalDateTime.now(), metaObject);
                setFieldValByName("updatedBy", SecurityUtils.getLoginUserIdOrNull(), metaObject);
            }
        };
    }
}
