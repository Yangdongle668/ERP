package com.erp.module.crm.dal.mapper;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import com.erp.framework.datascope.DataScope;
import com.erp.framework.mybatis.BaseMapperX;
import com.erp.module.crm.dal.dataobject.CustomerDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface CustomerMapper extends BaseMapperX<CustomerDO> {

    /** 列表分页，受数据范围约束（负责部门 / 负责人）。自定义 SQL 不会自动加逻辑删除条件，条件中需包含 deleted = 0。 */
    @DataScope(deptColumn = "dept_id", userColumn = "owner_id")
    @Select("SELECT * FROM crm_customer ${ew.customSqlSegment}")
    @ResultMap("mybatis-plus_CustomerDO")
    IPage<CustomerDO> selectScopedPage(IPage<CustomerDO> page, @Param(Constants.WRAPPER) Wrapper<CustomerDO> queryWrapper);

    @DataScope(deptColumn = "dept_id", userColumn = "owner_id")
    @Select("SELECT * FROM crm_customer ${ew.customSqlSegment}")
    @ResultMap("mybatis-plus_CustomerDO")
    List<CustomerDO> selectScopedList(@Param(Constants.WRAPPER) Wrapper<CustomerDO> queryWrapper);

    /** 各应用领域的客户数（不受数据范围限制） */
    default java.util.Map<String, Long> countByAppDomain() {
        java.util.Map<String, Long> result = new java.util.HashMap<>();
        for (java.util.Map<String, Object> row : selectMaps(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<CustomerDO>()
                .select("app_domain AS d", "COUNT(*) AS cnt").isNotNull("app_domain").groupBy("app_domain"))) {
            Object d = row.get("d") != null ? row.get("d") : row.get("D");
            Object cnt = row.get("cnt") != null ? row.get("cnt") : row.get("CNT");
            if (d != null) result.put(d.toString(), ((Number) cnt).longValue());
        }
        return result;
    }

    default boolean existsCode(String code) {
        return exists(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<CustomerDO>().eq(CustomerDO::getCode, code));
    }

    /** 物理删除（导入回滚） */
    @org.apache.ibatis.annotations.Delete("DELETE FROM crm_customer WHERE id = #{id}")
    int hardDelete(@org.apache.ibatis.annotations.Param("id") Long id);
}
