package com.erp.framework.module;

import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 按模块执行 Flyway 迁移（替代 Spring Boot 默认的单一 Flyway）。
 *
 * <p>启动时按 {@link ErpModule#order()} 顺序逐个迁移；任一模块失败则启动失败，避免带着不完整的表结构运行。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "erp.migration", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ModuleMigrationRunner implements InitializingBean {

    private final DataSource dataSource;
    private final List<ErpModule> modules;

    public ModuleMigrationRunner(DataSource dataSource, List<ErpModule> modules) {
        this.dataSource = dataSource;
        this.modules = modules;
    }

    @Override
    public void afterPropertiesSet() {
        Set<String> seen = new HashSet<>();
        modules.stream()
                .sorted(Comparator.comparingInt(ErpModule::order).thenComparing(ErpModule::code))
                .forEach(module -> {
                    if (!seen.add(module.code())) {
                        throw new IllegalStateException("模块编码重复: " + module.code());
                    }
                    migrate(module);
                });
    }

    private void migrate(ErpModule module) {
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations(module.migrationLocation())
                .table(module.historyTable())
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .failOnMissingLocations(false)
                .load();
        int applied = flyway.migrate().migrationsExecuted;
        log.info("[模块迁移] {}({}) 执行脚本 {} 个", module.name(), module.code(), applied);
    }
}
