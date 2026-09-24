package com.erp.module.system.dal.mapper;

import com.erp.framework.mybatis.BaseMapperX;
import com.erp.module.system.dal.dataobject.JobDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface JobMapper extends BaseMapperX<JobDO> {

    /** 抢占执行锁（SYS-JOB-R02）：锁空闲或已过期时才能拿到，返回 1 表示成功 */
    @Update("UPDATE sys_job SET locked_until = #{until}, locked_by = #{node} WHERE code = #{code} AND deleted = 0 "
            + "AND (locked_until IS NULL OR locked_until < #{now})")
    int tryLock(@Param("code") String code, @Param("node") String node, @Param("now") LocalDateTime now, @Param("until") LocalDateTime until);

    /** 执行结束：释放锁并记录结果 */
    @Update("UPDATE sys_job SET locked_until = NULL, locked_by = NULL, last_run_at = #{runAt}, last_result = #{result}, "
            + "last_message = #{message}, next_run_at = #{nextRunAt}, updated_at = #{runAt} WHERE code = #{code}")
    int finish(@Param("code") String code, @Param("runAt") LocalDateTime runAt, @Param("result") String result,
               @Param("message") String message, @Param("nextRunAt") LocalDateTime nextRunAt);
}
