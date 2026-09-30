package com.erp.module.workbench.dal.mapper;

import com.erp.framework.mybatis.BaseMapperX;
import com.erp.module.workbench.dal.dataobject.WbMessageDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

@Mapper
public interface WbMessageMapper extends BaseMapperX<WbMessageDO> {

    @Delete("DELETE FROM wb_message WHERE created_at < #{before}")
    int deleteBefore(@Param("before") LocalDateTime before);

    /** 删除已读 */
    @Delete("DELETE FROM wb_message WHERE user_id = #{userId} AND read_flag = 1")
    int deleteRead(@Param("userId") Long userId);
}
