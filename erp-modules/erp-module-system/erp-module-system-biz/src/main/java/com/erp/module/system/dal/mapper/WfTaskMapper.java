package com.erp.module.system.dal.mapper;

import com.erp.framework.mybatis.BaseMapperX;
import com.erp.module.system.dal.dataobject.WfTaskDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface WfTaskMapper extends BaseMapperX<WfTaskDO> {

    /** 只有仍为 PENDING 的任务能被处理（R10：重复提交只有第一次生效） */
    @Update("UPDATE wf_task SET status = #{status}, comment_text = #{comment}, transfer_to_id = #{transferTo}, auto_reason = #{reason}, "
            + "handled_at = #{at}, handled_by = #{operator}, updated_at = #{at}, updated_by = #{operator}, version = version + 1 "
            + "WHERE id = #{id} AND status = 'PENDING' AND deleted = 0")
    int handle(@Param("id") Long id, @Param("status") String status, @Param("comment") String comment, @Param("transferTo") Long transferTo,
               @Param("reason") String reason, @Param("operator") Long operator, @Param("at") LocalDateTime at);
}
