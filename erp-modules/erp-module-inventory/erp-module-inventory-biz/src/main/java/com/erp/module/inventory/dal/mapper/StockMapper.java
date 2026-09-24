package com.erp.module.inventory.dal.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.framework.mybatis.BaseMapperX;
import com.erp.module.inventory.dal.dataobject.StockDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.time.LocalDate;

@Mapper
public interface StockMapper extends BaseMapperX<StockDO> {

    default StockDO selectByDim(Long materialId, Long warehouseId, Long locationId, String batchNo) {
        return selectOne(new LambdaQueryWrapper<StockDO>().eq(StockDO::getMaterialId, materialId).eq(StockDO::getWarehouseId, warehouseId)
                .eq(StockDO::getLocationId, locationId).eq(StockDO::getBatchNo, batchNo));
    }

    /** 入库：数量增加（行锁保证并发安全） */
    @Update("UPDATE inv_stock SET qty = qty + #{qty}, last_in_date = #{date}, version = version + 1, updated_at = CURRENT_TIMESTAMP "
            + "WHERE id = #{id} AND deleted = 0")
    int increase(@Param("id") Long id, @Param("qty") BigDecimal qty, @Param("date") LocalDate date);

    /** 出库：条件扣减；allowNegative 为 false 时要求余额足够，影响 0 行表示库存不足 */
    @Update("<script>UPDATE inv_stock SET qty = qty - #{qty}, last_out_date = #{date}, version = version + 1, updated_at = CURRENT_TIMESTAMP "
            + "WHERE id = #{id} AND deleted = 0<if test='!allowNegative'> AND qty &gt;= #{qty}</if></script>")
    int decrease(@Param("id") Long id, @Param("qty") BigDecimal qty, @Param("date") LocalDate date, @Param("allowNegative") boolean allowNegative);

    /** 冲销：数量回退（不改最近出入库日期） */
    @Update("<script>UPDATE inv_stock SET qty = qty + #{delta}, version = version + 1, updated_at = CURRENT_TIMESTAMP "
            + "WHERE id = #{id} AND deleted = 0<if test='!allowNegative'> AND qty + #{delta} &gt;= 0</if></script>")
    int adjust(@Param("id") Long id, @Param("delta") BigDecimal delta, @Param("allowNegative") boolean allowNegative);
}
