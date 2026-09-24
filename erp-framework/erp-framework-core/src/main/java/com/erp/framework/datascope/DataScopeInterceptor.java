package com.erp.framework.datascope;

import com.baomidou.mybatisplus.extension.plugins.inner.DataPermissionInterceptor;
import com.erp.framework.security.SecurityUtils;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * 数据权限拦截器：只处理标注了 {@link DataScope} 的查询；未标注的语句、不受限的用户直接放行，
 * 避免对每条 SQL 做语法解析。数据权限只作用于查询，不改写 update / delete。
 */
public class DataScopeInterceptor extends DataPermissionInterceptor {

    private final DataScopeHandler handler;

    public DataScopeInterceptor(DataScopeHandler handler) {
        super(handler);
        this.handler = handler;
    }

    @Override
    public void beforeQuery(Executor executor, MappedStatement ms, Object parameter, RowBounds rowBounds,
                            ResultHandler resultHandler, BoundSql boundSql) throws SQLException {
        if (DataScopes.isIgnored() || !handler.isAnnotated(ms.getId()) || SecurityUtils.currentDataScope().all()) {
            return;
        }
        super.beforeQuery(executor, ms, parameter, rowBounds, resultHandler, boundSql);
    }

    @Override
    public void beforePrepare(StatementHandler sh, Connection connection, Integer transactionTimeout) {
        // 不改写 update / delete
    }
}
