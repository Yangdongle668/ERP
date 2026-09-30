package com.erp.module.bi.dal.mapper;

import com.erp.framework.mybatis.BaseMapperX;
import com.erp.module.bi.dal.dataobject.BiEtlLockDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface BiEtlLockMapper extends BaseMapperX<BiEtlLockDO> {

    /** 原子抢锁：未被持有或已到期才成功，返回 1 */
    @Update("UPDATE bi_etl_lock SET locked_until = #{until}, locked_by = #{node} WHERE lock_key = #{key} AND deleted = 0 "
            + "AND (locked_until IS NULL OR locked_until < #{now})")
    int tryLock(@Param("key") String key, @Param("node") String node, @Param("now") LocalDateTime now, @Param("until") LocalDateTime until);

    /** 释放（只释放自己持有的） */
    @Update("UPDATE bi_etl_lock SET locked_until = NULL, locked_by = NULL WHERE lock_key = #{key} AND locked_by = #{node}")
    int unlock(@Param("key") String key, @Param("node") String node);
}
