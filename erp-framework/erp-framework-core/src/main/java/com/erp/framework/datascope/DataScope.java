package com.erp.framework.datascope;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 数据权限（01-03 角色与权限 1.2）：按当前用户的数据范围自动给查询拼接条件，业务代码不手写条件。
 *
 * <ul>
 *   <li>标注在 Mapper 方法上：该查询生效；</li>
 *   <li>标注在 Mapper 接口上：该 Mapper 的列表类查询（selectList、selectPage、selectCount、selectMaps、selectObjs）生效；
 *       按 ID 查询不过滤，详情接口需要时用 {@link DataScopes#check} 校验。</li>
 * </ul>
 * 条件形如 {@code (t.org_id IN (..) OR t.dept_id IN (..) OR t.owner_id = 当前用户)}；列名为空字符串表示不按该维度过滤。
 * 超级管理员、数据范围为“全部”的用户、未登录的后台任务不过滤。需要临时跳过时使用 {@link DataScopes#ignore}。
 *
 * <pre>{@code
 * @DataScope(deptColumn = "dept_id", userColumn = "owner_id")
 * @Mapper
 * public interface SalesOrderMapper extends BaseMapperX<SalesOrderDO> { }
 * }</pre>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface DataScope {

    /** 单据所属公司列 */
    String orgColumn() default "org_id";

    /** 单据所属部门列 */
    String deptColumn() default "dept_id";

    /** 单据负责人列（“仅本人”范围使用），如 owner_id 或 created_by */
    String userColumn() default "owner_id";

    /** 只对该表生效（多表联查时指定主表）；为空时对语句中的所有表生效 */
    String table() default "";
}
