package com.erp.framework.datascope;

import com.baomidou.mybatisplus.extension.plugins.handler.MultiDataPermissionHandler;
import com.erp.framework.security.SecurityUtils;
import com.erp.framework.security.UserDataScope;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Table;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/** 把 {@link DataScope} 注解转换为 SQL 条件，由 MyBatis-Plus 的 DataPermissionInterceptor 拼到 WHERE 中。 */
@Slf4j
public class DataScopeHandler implements MultiDataPermissionHandler {

    /** 接口级注解只对这些列表类方法生效 */
    private static final Set<String> LIST_METHODS = Set.of("selectList", "selectPage", "selectCount", "selectMaps", "selectObjs", "selectMapsPage");

    private final ConcurrentHashMap<String, Optional<DataScope>> cache = new ConcurrentHashMap<>();

    @Override
    public Expression getSqlSegment(Table table, Expression where, String mappedStatementId) {
        if (DataScopes.isIgnored()) return null;
        Optional<DataScope> anno = cache.computeIfAbsent(mappedStatementId, DataScopeHandler::resolve);
        if (anno.isEmpty()) return null;
        DataScope ds = anno.get();
        if (!ds.table().isEmpty() && !ds.table().equalsIgnoreCase(table.getName())) return null;
        UserDataScope scope = SecurityUtils.currentDataScope();
        if (scope.all()) return null;

        String prefix = (table.getAlias() != null ? table.getAlias().getName() : table.getName()) + ".";
        List<String> parts = new ArrayList<>();
        if (!ds.orgColumn().isEmpty() && !scope.orgIds().isEmpty()) {
            parts.add(prefix + ds.orgColumn() + " IN (" + join(scope.orgIds()) + ")");
        }
        if (!ds.deptColumn().isEmpty() && !scope.deptIds().isEmpty()) {
            parts.add(prefix + ds.deptColumn() + " IN (" + join(scope.deptIds()) + ")");
        }
        Long userId = SecurityUtils.getLoginUserIdOrNull();
        if (scope.self() && !ds.userColumn().isEmpty() && userId != null) {
            parts.add(prefix + ds.userColumn() + " = " + userId);
        }
        String condition = parts.isEmpty() ? "1 = 0" : "(" + String.join(" OR ", parts) + ")";
        try {
            return CCJSqlParserUtil.parseCondExpression(condition);
        } catch (JSQLParserException e) {
            throw new IllegalStateException("数据权限条件解析失败: " + condition, e);
        }
    }

    boolean isAnnotated(String mappedStatementId) {
        return cache.computeIfAbsent(mappedStatementId, DataScopeHandler::resolve).isPresent();
    }

    private static String join(Set<Long> ids) {
        return ids.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    /** 按 MappedStatement ID（Mapper 全名 + 方法名）查找注解：方法级优先，其次接口级（仅列表类方法） */
    static Optional<DataScope> resolve(String msId) {
        int dot = msId.lastIndexOf('.');
        if (dot < 0) return Optional.empty();
        String className = msId.substring(0, dot);
        String methodName = msId.substring(dot + 1);
        if (methodName.endsWith("_mpCount")) methodName = methodName.substring(0, methodName.length() - "_mpCount".length());
        try {
            Class<?> mapper = Class.forName(className, false, Thread.currentThread().getContextClassLoader());
            for (Method m : mapper.getMethods()) {
                if (m.getName().equals(methodName) && m.isAnnotationPresent(DataScope.class)) {
                    return Optional.of(m.getAnnotation(DataScope.class));
                }
            }
            DataScope typeLevel = mapper.getAnnotation(DataScope.class);
            if (typeLevel != null && LIST_METHODS.contains(methodName)) {
                return Optional.of(typeLevel);
            }
        } catch (ClassNotFoundException e) {
            log.debug("[数据权限] 找不到 Mapper {}", className);
        }
        return Optional.empty();
    }
}
