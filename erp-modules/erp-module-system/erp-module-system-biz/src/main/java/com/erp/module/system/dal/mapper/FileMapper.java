package com.erp.module.system.dal.mapper;

import com.erp.framework.mybatis.BaseMapperX;
import com.erp.module.system.dal.dataobject.FileDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface FileMapper extends BaseMapperX<FileDO> {

    /** 逻辑删除超过保留期的附件（清理物理文件用，绕过逻辑删除过滤） */
    @Select("SELECT id, path, storage FROM sys_file WHERE deleted = 1 AND updated_at < #{before} LIMIT #{limit}")
    List<FileDO> selectDeletedBefore(@Param("before") LocalDateTime before, @Param("limit") int limit);

    /** 该路径是否仍被未删除的记录引用（同一文件可能被多条记录引用，如复制单据） */
    @Select("SELECT COUNT(*) FROM sys_file WHERE path = #{path} AND deleted = 0")
    long countAlive(@Param("path") String path);

    @Delete("DELETE FROM sys_file WHERE id = #{id}")
    int purge(@Param("id") Long id);
}
