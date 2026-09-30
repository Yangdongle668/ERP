package com.erp.module.workbench.dal.mapper;

import com.erp.framework.mybatis.BaseMapperX;
import com.erp.module.workbench.dal.dataobject.WbTodoDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

@Mapper
public interface WbTodoMapper extends BaseMapperX<WbTodoDO> {

    /** 清理已处理待办（物理删除：同一 todoKey 以后还可能再次创建） */
    @Delete("DELETE FROM wb_todo WHERE todo_status <> 'PENDING' AND done_at < #{before}")
    int deleteDoneBefore(@Param("before") LocalDateTime before);
}
