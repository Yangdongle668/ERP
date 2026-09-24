package com.erp.architecture;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * 模块边界检查。多人/多窗口并行开发时，由 CI 自动拦截越界依赖。
 */
class ModuleBoundaryTest {

    private static final Pattern MODULE_PKG = Pattern.compile("^com\\.erp\\.module\\.([a-z0-9]+)(\\..*)?$");

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.erp");
    }

    @Test
    void modulesOnlyDependOnOtherModulesApi() {
        classes().that().resideInAPackage("com.erp.module..")
                .should(onlyAccessOtherModulesThroughApi())
                .check(classes);
    }

    @Test
    void apiPackagesStayFrameworkFree() {
        noClasses().that().resideInAPackage("com.erp.module.*.api..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..", "com.baomidou..", "com.erp.framework..", "org.apache.ibatis..")
                .because("api 是纯契约，只能依赖 erp-common 和 JDK")
                .check(classes);
    }

    @Test
    void frameworkDoesNotDependOnModules() {
        noClasses().that().resideInAnyPackage("com.erp.framework..", "com.erp.common..")
                .should().dependOnClassesThat().resideInAPackage("com.erp.module..")
                .because("依赖方向只能是 业务模块 → 框架")
                .check(classes);
    }

    @Test
    void controllersDoNotUseMappersDirectly() {
        noClasses().that().resideInAPackage("com.erp.module.*.controller..")
                .should().dependOnClassesThat().resideInAPackage("com.erp.module.*.dal.mapper..")
                .because("Controller 只调用 Service，事务与业务规则在 Service 层")
                .check(classes);
    }

    private static ArchCondition<JavaClass> onlyAccessOtherModulesThroughApi() {
        return new ArchCondition<>("only depend on other modules through their api package") {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
                String owner = moduleOf(item.getPackageName());
                for (Dependency dep : item.getDirectDependenciesFromSelf()) {
                    String targetPkg = dep.getTargetClass().getPackageName();
                    String target = moduleOf(targetPkg);
                    if (target == null || target.equals(owner)) {
                        continue;
                    }
                    if (!targetPkg.startsWith("com.erp.module." + target + ".api")) {
                        events.add(SimpleConditionEvent.violated(dep,
                                "模块 [" + owner + "] 越界访问了模块 [" + target + "] 的内部类: " + dep.getDescription()));
                    }
                }
            }
        };
    }

    private static String moduleOf(String pkg) {
        Matcher m = MODULE_PKG.matcher(pkg);
        return m.matches() ? m.group(1) : null;
    }
}
