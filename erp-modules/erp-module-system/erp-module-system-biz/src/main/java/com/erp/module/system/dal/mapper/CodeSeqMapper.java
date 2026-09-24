package com.erp.module.system.dal.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** 流水号表是技术表，没有审计字段，直接用 SQL 操作。 */
@Mapper
public interface CodeSeqMapper {

    /** 原子自增；返回影响行数，0 表示该重置键还没有记录。执行后当前事务持有该行锁直到提交。 */
    @Update("UPDATE sys_code_seq SET current_value = current_value + 1 WHERE biz_code = #{bizCode} AND reset_key = #{resetKey}")
    int increment(@Param("bizCode") String bizCode, @Param("resetKey") String resetKey);

    @Insert("INSERT INTO sys_code_seq (biz_code, reset_key, current_value) VALUES (#{bizCode}, #{resetKey}, 1)")
    int insertFirst(@Param("bizCode") String bizCode, @Param("resetKey") String resetKey);

    @Select("SELECT current_value FROM sys_code_seq WHERE biz_code = #{bizCode} AND reset_key = #{resetKey}")
    Long selectCurrent(@Param("bizCode") String bizCode, @Param("resetKey") String resetKey);
}
