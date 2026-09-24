package com.erp.module.inventory.dal.mapper;

import com.erp.framework.mybatis.BaseMapperX;
import com.erp.module.inventory.dal.dataobject.StockTxnDO;
import com.erp.module.inventory.dal.dataobject.TxnBucketRow;
import com.erp.module.inventory.dal.dataobject.TxnSumRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface StockTxnMapper extends BaseMapperX<StockTxnDO> {

    /** 截至某日（含）的结存数量，按物料 + 仓库（冲销流水方向相反，直接按方向求和） */
    @Select("SELECT material_id, warehouse_id, SUM(CASE WHEN direction = 'IN' THEN qty ELSE -qty END) AS qty, "
            + "SUM(CASE WHEN direction = 'IN' THEN COALESCE(amount, 0) ELSE -COALESCE(amount, 0) END) AS amount "
            + "FROM inv_stock_txn WHERE deleted = 0 AND biz_date <= #{end} GROUP BY material_id, warehouse_id")
    List<TxnSumRow> balances(@Param("end") LocalDate end);

    /** 期间内按物料 + 仓库 + 单据类型 + 出入库类型 + 方向 + 是否冲销汇总（收发存） */
    @Select("SELECT material_id, warehouse_id, doc_type, biz_type, direction, is_reversal, SUM(qty) AS qty, SUM(amount) AS amount, "
            + "COUNT(amount) AS amount_count, COUNT(*) AS txn_count "
            + "FROM inv_stock_txn WHERE deleted = 0 AND biz_date BETWEEN #{from} AND #{to} "
            + "GROUP BY material_id, warehouse_id, doc_type, biz_type, direction, is_reversal")
    List<TxnBucketRow> buckets(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
