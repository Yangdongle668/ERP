package com.erp.framework.mybatis;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.TableFieldInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.toolkit.reflect.GenericTypeUtils;
import com.erp.framework.datascope.DataScopes;
import org.springframework.context.ApplicationContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 组织引用检查辅助（系统管理删除组织前的 {@code OrgReferenceChecker}，SYS-ORG-R07）。
 *
 * <p>每个业务模块用本模块的包名创建一个实例：检查本模块全部单据表（{@link BaseDocDO} 的 org_id / dept_id）
 * 以及显式列出的主数据表（如客户的负责部门、工作中心的车间）。只访问本模块的表，符合模块边界；
 * 计算结果类表（MRP 结果、汇总表等）不列入，避免历史计算数据阻止删除组织。查询跳过数据权限。
 */
public final class ModuleOrgReferences {

    static final List<String> COLUMNS = List.of("org_id", "dept_id");

    private record Target(BaseMapper<?> mapper, List<String> columns) {
    }

    private final ApplicationContext context;
    private final String basePackage;
    private final Set<Class<?>> extraEntities;
    private volatile List<Target> targets;

    /**
     * @param basePackage   本模块包名前缀，如 {@code com.erp.module.sales.}
     * @param extraEntities 需要一并检查的非单据实体（含 org_id 或 dept_id 列）
     */
    public ModuleOrgReferences(ApplicationContext context, String basePackage, Class<?>... extraEntities) {
        this.context = context;
        this.basePackage = basePackage.endsWith(".") ? basePackage : basePackage + ".";
        this.extraEntities = Set.of(extraEntities);
    }

    /** 本模块是否有数据引用该组织（公司或部门） */
    public boolean isReferenced(Long orgId) {
        if (orgId == null) return false;
        return DataScopes.ignore(() -> targets().stream().anyMatch(t -> exists(t, orgId)));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static boolean exists(Target t, Long orgId) {
        QueryWrapper<Object> w = new QueryWrapper<>();
        if (t.columns().size() == 1) {
            w.eq(t.columns().get(0), orgId);
        } else {
            w.and(x -> {
                for (int i = 0; i < t.columns().size(); i++) {
                    if (i > 0) x.or();
                    x.eq(t.columns().get(i), orgId);
                }
            });
        }
        return ((BaseMapper) t.mapper()).exists(w);
    }

    private List<Target> targets() {
        List<Target> list = targets;
        if (list == null) {
            list = new ArrayList<>();
            for (BaseMapperX<?> bean : context.getBeansOfType(BaseMapperX.class).values()) {
                Class<?> iface = mapperInterface(bean);
                if (iface == null || !iface.getName().startsWith(basePackage)) continue;
                Class<?>[] args = GenericTypeUtils.resolveTypeArguments(iface, BaseMapper.class);
                if (args == null || args.length == 0) continue;
                Class<?> entity = args[0];
                if (!BaseDocDO.class.isAssignableFrom(entity) && !extraEntities.contains(entity)) continue;
                TableInfo info = TableInfoHelper.getTableInfo(entity);
                if (info == null) continue;
                List<String> cols = info.getFieldList().stream().map(TableFieldInfo::getColumn).filter(COLUMNS::contains).toList();
                if (!cols.isEmpty()) list.add(new Target(bean, cols));
            }
            targets = list;
        }
        return list;
    }

    private static Class<?> mapperInterface(Object bean) {
        for (Class<?> i : bean.getClass().getInterfaces()) {
            if (BaseMapperX.class.isAssignableFrom(i) && i != BaseMapperX.class) return i;
        }
        return null;
    }
}
