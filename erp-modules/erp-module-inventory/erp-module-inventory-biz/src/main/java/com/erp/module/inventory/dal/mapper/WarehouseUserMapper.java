package com.erp.module.inventory.dal.mapper;

import com.erp.module.inventory.dal.dataobject.WarehouseUserRow;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** 仓库操作人员（技术表 inv_warehouse_user，无实体） */
@Mapper
public interface WarehouseUserMapper {

    @Select("SELECT warehouse_id FROM inv_warehouse_user WHERE user_id = #{userId}")
    List<Long> selectWarehouseIds(@Param("userId") Long userId);

    @Select("SELECT warehouse_id, user_id FROM inv_warehouse_user")
    List<WarehouseUserRow> selectAll();

    @Select("SELECT user_id FROM inv_warehouse_user WHERE warehouse_id = #{warehouseId}")
    List<Long> selectUserIds(@Param("warehouseId") Long warehouseId);

    @Delete("DELETE FROM inv_warehouse_user WHERE warehouse_id = #{warehouseId}")
    int deleteByWarehouse(@Param("warehouseId") Long warehouseId);

    @Insert("INSERT INTO inv_warehouse_user (warehouse_id, user_id) VALUES (#{warehouseId}, #{userId})")
    int insert(@Param("warehouseId") Long warehouseId, @Param("userId") Long userId);
}
