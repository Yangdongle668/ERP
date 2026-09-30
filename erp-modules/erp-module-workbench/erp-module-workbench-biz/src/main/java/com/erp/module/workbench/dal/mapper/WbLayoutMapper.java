package com.erp.module.workbench.dal.mapper;

import com.erp.framework.mybatis.BaseMapperX;
import com.erp.module.workbench.dal.dataobject.WbLayoutDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface WbLayoutMapper extends BaseMapperX<WbLayoutDO> {

    /** 按用户整体替换（物理删除） */
    @Delete("DELETE FROM wb_layout WHERE user_id = #{userId}")
    int deleteByUser(@Param("userId") Long userId);
}
