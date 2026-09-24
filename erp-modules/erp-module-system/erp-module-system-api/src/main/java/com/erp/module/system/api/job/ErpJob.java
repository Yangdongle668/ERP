package com.erp.module.system.api.job;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明定时任务（需求 01-系统管理/12 第 3 节）。加在 Spring Bean 的无参方法上，启动时同步到 sys_job，
 * 由系统管理模块按 Cron 调度；管理员可在“定时任务”页面启停、修改 Cron、立即执行、查看执行日志。
 *
 * <p>方法返回 String 时作为执行结果说明记录到日志（如“删除 120 条”）；抛出异常记为失败。
 * 多实例部署时同一任务同一时刻只在一个实例执行（数据库锁）。不要再使用 {@code @Scheduled}。
 *
 * <pre>
 * &#64;ErpJob(code = "INV_STOCK_ALERT", name = "库存预警检查", cron = "0 0 7 * * ?")
 * public String checkStockAlerts() { ... return "生成预警 3 条"; }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ErpJob {

    /** 任务编码，全局唯一，建议以模块前缀开头，如 SYS_LOG_CLEANUP */
    String code();

    String name();

    /** 默认 Cron（6 段：秒 分 时 日 月 周），如 "0 0 2 * * ?" 表示每天 02:00 */
    String cron();
}
