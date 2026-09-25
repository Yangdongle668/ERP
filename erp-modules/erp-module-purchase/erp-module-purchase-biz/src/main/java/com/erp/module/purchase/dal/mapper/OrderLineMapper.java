package com.erp.module.purchase.dal.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.framework.mybatis.BaseMapperX;
import com.erp.module.purchase.dal.dataobject.IdQtyRow;
import com.erp.module.purchase.dal.dataobject.OrderLineDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

@Mapper
public interface OrderLineMapper extends BaseMapperX<OrderLineDO> {

    default List<OrderLineDO> selectByParent(Long parentId) {
        return selectList(new LambdaQueryWrapper<OrderLineDO>().eq(OrderLineDO::getOrderId, parentId).orderByAsc(OrderLineDO::getLineNo));
    }

    default List<OrderLineDO> selectByParents(Collection<Long> parentIds) {
        if (parentIds == null || parentIds.isEmpty()) return List.of();
        return selectList(new LambdaQueryWrapper<OrderLineDO>().in(OrderLineDO::getOrderId, parentIds).orderByAsc(OrderLineDO::getLineNo));
    }

    /** 物理删除（明细随单据保存整体替换） */
    @Delete("DELETE FROM pur_order_line WHERE order_id = #{parentId}")
    int deleteByParent(@Param("parentId") Long parentId);

    /** 申请行已下单数量（基本单位）：已审核、执行中、已完成、已关闭订单中引用该申请行的订单行 */
    @Select("""
            <script>
            SELECT l.requisition_line_id AS id, SUM(l.base_qty) AS qty FROM pur_order_line l JOIN pur_order o ON o.id = l.order_id
             WHERE l.deleted = 0 AND o.deleted = 0 AND o.status IN ('APPROVED', 'IN_PROGRESS', 'COMPLETED', 'CLOSED')
               AND l.requisition_line_id IN <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
             GROUP BY l.requisition_line_id
            </script>
            """)
    List<IdQtyRow> sumOrderedByRequisitionLines(@Param("ids") Collection<Long> ids);

    /** 引用申请行的有效订单行数（草稿、待审批也算） */
    @Select("""
            <script>
            SELECT COUNT(*) FROM pur_order_line l JOIN pur_order o ON o.id = l.order_id
             WHERE l.deleted = 0 AND o.deleted = 0 AND o.status &lt;&gt; 'VOIDED'
               AND l.requisition_line_id IN <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
            </script>
            """)
    long countByRequisitionLines(@Param("ids") Collection<Long> ids);
}
