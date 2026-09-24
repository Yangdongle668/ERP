package com.erp.framework.module;

/**
 * 业务模块描述。每个 biz 模块在自己的配置类里声明一个 {@code @Bean ErpModule}。
 *
 * <p>框架据此为每个模块独立执行数据库迁移：
 * <ul>
 *   <li>脚本目录：{@code classpath:db/migration/<code>/}</li>
 *   <li>迁移历史表：{@code flyway_history_<code>}</li>
 * </ul>
 * 因此各模块的 Flyway 版本号互相独立（每个模块都可以从 V1 开始），
 * 多个分支并行开发、并行合并不会出现版本号冲突。
 *
 * @param code  模块编码，小写字母，与 Java 包名、前端目录名、API 路径前缀一致，例如 {@code sales}
 * @param name  模块中文名
 * @param order 迁移顺序，越小越先执行；按依赖层级分配（平台 0~99，基础 100~199，业务 200~299，财务 300~399，分析 400~499）
 */
public record ErpModule(String code, String name, int order) {

    public ErpModule {
        if (code == null || !code.matches("[a-z][a-z0-9]*")) {
            throw new IllegalArgumentException("模块编码必须是小写字母开头的字母数字: " + code);
        }
    }

    public String migrationLocation() {
        return "classpath:db/migration/" + code;
    }

    public String historyTable() {
        return "flyway_history_" + code;
    }
}
