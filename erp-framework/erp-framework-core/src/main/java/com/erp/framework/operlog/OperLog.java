package com.erp.framework.operlog;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作日志说明（01-11 日志审计）。所有写接口（POST/PUT/DELETE/PATCH）都会自动记录；
 * 标注本注解可以指定更易读的操作名称，未标注时取 {@code @Operation(summary)}，再没有则取接口路径。
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface OperLog {

    /** 操作名称，如“新建用户” */
    String value() default "";

    /** 模块编码；为空时取路径 /api/{module}/... 的第一段 */
    String module() default "";

    /** 为 false 时不记录（如高频的无副作用 POST 查询） */
    boolean enabled() default true;
}
