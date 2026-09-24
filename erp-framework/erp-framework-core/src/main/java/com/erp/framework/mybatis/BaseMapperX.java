package com.erp.framework.mybatis;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.common.exception.BizException;
import com.erp.common.exception.GlobalErrorCodes;
import com.erp.common.result.PageParam;
import com.erp.common.result.PageResult;

/** 在 MyBatis-Plus BaseMapper 基础上增加常用方法。所有 Mapper 继承本接口。 */
public interface BaseMapperX<T> extends BaseMapper<T> {

    default PageResult<T> selectPage(PageParam param, Wrapper<T> wrapper) {
        Page<T> page = selectPage(Page.of(param.getPageNo(), param.getPageSize()), wrapper);
        return new PageResult<>(page.getRecords(), page.getTotal());
    }

    /**
     * 按 ID 更新并校验乐观锁：实体必须带上读取时的 version，
     * 更新行数为 0（被他人修改或已删除）时抛出并发修改异常，事务回滚。
     */
    default void updateByIdOrFail(T entity) {
        if (updateById(entity) == 0) {
            throw new BizException(GlobalErrorCodes.CONCURRENT_MODIFICATION);
        }
    }
}
