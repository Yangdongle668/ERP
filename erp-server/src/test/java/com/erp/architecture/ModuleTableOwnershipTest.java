package com.erp.architecture;

import com.baomidou.mybatisplus.annotation.TableName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 表归属检查（CLAUDE.md 硬性规则 1、3）：表名使用本模块前缀，模块不得访问其他模块的表。
 * 检查实体 {@code @TableName} 的前缀，以及各模块 main 源码、迁移脚本、Mapper XML 中 SQL 引用的表。
 */
class ModuleTableOwnershipTest {

    /** 模块编码 → 本模块表前缀 */
    static final Map<String, Set<String>> PREFIXES = Map.ofEntries(
            Map.entry("system", Set.of("sys", "wf")),
            Map.entry("workbench", Set.of("wb")),
            Map.entry("crm", Set.of("crm")),
            Map.entry("sales", Set.of("sal")),
            Map.entry("engineering", Set.of("eng")),
            Map.entry("pmc", Set.of("pmc")),
            Map.entry("purchase", Set.of("pur")),
            Map.entry("inventory", Set.of("inv")),
            Map.entry("production", Set.of("mfg")),
            Map.entry("quality", Set.of("qc")),
            Map.entry("shipping", Set.of("shp")),
            Map.entry("finance", Set.of("fin")),
            Map.entry("bi", Set.of("bi", "ai")));

    private static final Set<String> ALL_PREFIXES = PREFIXES.values().stream().flatMap(Set::stream).collect(Collectors.toSet());
    private static final Pattern MODULE_PACKAGE = Pattern.compile("^com\\.erp\\.module\\.([a-z0-9]+)\\..*");
    private static final Pattern TABLE_REF = Pattern.compile("(?i)\\b(?:FROM|JOIN|UPDATE|INTO|TABLE|EXISTS)\\s+`?([a-z]+)_[a-z0-9_]+");
    private static final Path MODULES = Path.of("..", "erp-modules");

    @Test
    void entityTablesUseOwnModulePrefix() throws Exception {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(TableName.class));
        List<String> bad = new ArrayList<>();
        int count = 0;
        for (BeanDefinition bd : scanner.findCandidateComponents("com.erp.module")) {
            Matcher m = MODULE_PACKAGE.matcher(bd.getBeanClassName());
            if (!m.matches()) continue;
            count++;
            Class<?> c = Class.forName(bd.getBeanClassName());
            String table = c.getAnnotation(TableName.class).value().replace("`", "");
            String prefix = table.contains("_") ? table.substring(0, table.indexOf('_')).toLowerCase(Locale.ROOT) : "";
            Set<String> own = PREFIXES.get(m.group(1));
            if (own == null) bad.add(c.getName() + "：模块 " + m.group(1) + " 未登记表前缀");
            else if (!own.contains(prefix)) bad.add(c.getSimpleName() + " 表 " + table + " 不是本模块前缀 " + own);
        }
        assertThat(count).as("扫描到的实体数").isGreaterThan(100);
        assertThat(bad).as("实体表名必须使用本模块前缀").isEmpty();
    }

    @Test
    void sqlDoesNotReferenceOtherModulesTables() throws IOException {
        List<String> bad = new ArrayList<>();
        int files = 0;
        for (Map.Entry<String, Set<String>> e : PREFIXES.entrySet()) {
            Path dir = MODULES.resolve("erp-module-" + e.getKey());
            try (Stream<Path> s = Files.walk(dir)) {
                for (Path p : s.filter(ModuleTableOwnershipTest::isSource).toList()) {
                    files++;
                    List<String> lines = Files.readAllLines(p);
                    for (int i = 0; i < lines.size(); i++) {
                        Matcher m = TABLE_REF.matcher(lines.get(i));
                        while (m.find()) {
                            String prefix = m.group(1).toLowerCase(Locale.ROOT);
                            if (ALL_PREFIXES.contains(prefix) && !e.getValue().contains(prefix)) {
                                bad.add(e.getKey() + "：" + p.getFileName() + ":" + (i + 1) + " " + m.group().trim());
                            }
                        }
                    }
                }
            }
        }
        assertThat(files).as("扫描到的源文件数").isGreaterThan(100);
        assertThat(bad).as("模块不得直接访问其他模块的表，请改为调用对方 -api").isEmpty();
    }

    private static boolean isSource(Path p) {
        String s = p.toString();
        return s.contains("src" + java.io.File.separator + "main") && (s.endsWith(".java") || s.endsWith(".sql") || s.endsWith(".xml"));
    }
}
