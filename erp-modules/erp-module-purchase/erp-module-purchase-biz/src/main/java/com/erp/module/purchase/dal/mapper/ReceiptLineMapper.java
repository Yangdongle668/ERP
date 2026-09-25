package com.erp.module.purchase.dal.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.framework.mybatis.BaseMapperX;
import com.erp.module.purchase.dal.dataobject.ReceiptLineDO;
import org.apache.ibatis.annotations.Delete;
import com.erp.module.purchase.dal.dataobject.OrderLineAggRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

@Mapper
public interface ReceiptLineMapper extends BaseMapperX<ReceiptLineDO> {

    default List<ReceiptLineDO> selectByParent(Long parentId) {
        return selectList(new LambdaQueryWrapper<ReceiptLineDO>().eq(ReceiptLineDO::getReceiptId, parentId).orderByAsc(ReceiptLineDO::getLineNo));
    }

    default List<ReceiptLineDO> selectByParents(Collection<Long> parentIds) {
        if (parentIds == null || parentIds.isEmpty()) return List.of();
        return selectList(new LambdaQueryWrapper<ReceiptLineDO>().in(ReceiptLineDO::getReceiptId, parentIds).orderByAsc(ReceiptLineDO::getLineNo));
    }

    /** 物理删除（明细随单据保存整体替换） */
    @Delete("DELETE FROM pur_receipt_line WHERE receipt_id = #{parentId}")
    int deleteByParent(@Param("parentId") Long parentId);

    /** 采购订单行的到货汇总（已审核、已完成的到货单） */
    @Select("""
            <script>
            SELECT l.order_line_id AS id, SUM(l.base_qty) AS received_qty, SUM(l.stocked_qty) AS stocked_qty, SUM(l.qualified_qty) AS qualified_qty,
                   SUM(l.statement_qty) AS statement_qty, MIN(r.arrival_at) AS first_arrival
              FROM pur_receipt_line l JOIN pur_receipt r ON r.id = l.receipt_id
             WHERE l.deleted = 0 AND r.deleted = 0 AND r.status IN ('APPROVED', 'COMPLETED')
               AND l.order_line_id IN <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
             GROUP BY l.order_line_id
            </script>
            """)
    List<OrderLineAggRow> sumByOrderLines(@Param("ids") Collection<Long> ids);

    /** 委外单的收货汇总（委外收货的到货单，order_id 为委外单） */
    @Select("""
            <script>
            SELECT l.order_id AS id, SUM(l.base_qty) AS received_qty, SUM(l.stocked_qty) AS stocked_qty, SUM(l.qualified_qty) AS qualified_qty,
                   SUM(l.statement_qty) AS statement_qty, MIN(r.arrival_at) AS first_arrival
              FROM pur_receipt_line l JOIN pur_receipt r ON r.id = l.receipt_id
             WHERE l.deleted = 0 AND r.deleted = 0 AND r.status IN ('APPROVED', 'COMPLETED') AND r.receipt_type = 'OUTSOURCE'
               AND l.order_id IN <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
             GROUP BY l.order_id
            </script>
            """)
    List<OrderLineAggRow> sumByOutsourcings(@Param("ids") Collection<Long> ids);
}
