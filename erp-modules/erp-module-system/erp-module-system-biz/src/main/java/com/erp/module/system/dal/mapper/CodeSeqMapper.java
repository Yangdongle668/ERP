package com.erp.module.system.dal.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/** 流水号表是技术表，没有审计字段，直接用 SQL 操作。 */
@Mapper
public interface CodeSeqMapper {

    /** 原子自增；返回影响行数，0 表示该重置键还没有记录。执行后当前事务持有该行锁直到提交。 */
    @Update("UPDATE sys_code_seq SET current_value = current_value + 1, updated_at = CURRENT_TIMESTAMP WHERE biz_code = #{bizCode} AND reset_key = #{resetKey}")
    int increment(@Param("bizCode") String bizCode, @Param("resetKey") String resetKey);

    @Insert("INSERT INTO sys_code_seq (biz_code, reset_key, current_value, updated_at) VALUES (#{bizCode}, #{resetKey}, 1, CURRENT_TIMESTAMP)")
    int insertFirst(@Param("bizCode") String bizCode, @Param("resetKey") String resetKey);

    @Select("SELECT current_value FROM sys_code_seq WHERE biz_code = #{bizCode} AND reset_key = #{resetKey}")
    Long selectCurrent(@Param("bizCode") String bizCode, @Param("resetKey") String resetKey);

    @Select("SELECT biz_code, reset_key, current_value, updated_at FROM sys_code_seq WHERE biz_code = #{bizCode} ORDER BY updated_at DESC, reset_key DESC")
    List<SeqRow> selectByBizCode(@Param("bizCode") String bizCode);

    /** 调整流水号：只允许调大（条件中比较，防止并发下调小） */
    @Update("UPDATE sys_code_seq SET current_value = #{newValue}, updated_at = CURRENT_TIMESTAMP WHERE biz_code = #{bizCode} AND reset_key = #{resetKey} AND current_value < #{newValue}")
    int adjust(@Param("bizCode") String bizCode, @Param("resetKey") String resetKey, @Param("newValue") long newValue);

    record SeqRow(String bizCode, String resetKey, Long currentValue, LocalDateTime updatedAt) {
    }
}
