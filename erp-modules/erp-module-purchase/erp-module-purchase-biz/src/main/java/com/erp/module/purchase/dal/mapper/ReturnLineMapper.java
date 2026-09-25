package com.erp.module.purchase.dal.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.framework.mybatis.BaseMapperX;
import com.erp.module.purchase.dal.dataobject.ReturnLineDO;
import org.apache.ibatis.annotations.Delete;
import com.erp.module.purchase.dal.dataobject.ReturnAggRow;
import com.erp.module.purchase.dal.dataobject.IdQtyRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

@Mapper
public interface ReturnLineMapper extends BaseMapperX<ReturnLineDO> {

    default List<ReturnLineDO> selectByParent(Long parentId) {
        return selectList(new LambdaQueryWrapper<ReturnLineDO>().eq(ReturnLineDO::getReturnId, parentId).orderByAsc(ReturnLineDO::getLineNo));
    }

    default List<ReturnLineDO> selectByParents(Collection<Long> parentIds) {
        if (parentIds == null || parentIds.isEmpty()) return List.of();
        return selectList(new LambdaQueryWrapper<ReturnLineDO>().in(ReturnLineDO::getReturnId, parentIds).orderByAsc(ReturnLineDO::getLineNo));
    }

    /** 物理删除（明细随单据保存整体替换） */
    @Delete("DELETE FROM pur_return_line WHERE return_id = #{parentId}")
    int deleteByParent(@Param("parentId") Long parentId);

    /** 采购订单行的退货汇总（已出库数量，按处理方式） */
    @Select("""
            <script>
            SELECT l.order_line_id AS id,
                   SUM(CASE WHEN r.handling = 'REFUND' THEN l.out_qty ELSE 0 END) AS refund_qty,
                   SUM(CASE WHEN r.handling = 'REPLACE' THEN l.out_qty ELSE 0 END) AS replace_qty
              FROM pur_return_line l JOIN pur_return r ON r.id = l.return_id
             WHERE l.deleted = 0 AND r.deleted = 0 AND r.status &lt;&gt; 'VOIDED'
               AND l.order_line_id IN <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
             GROUP BY l.order_line_id
            </script>
            """)
    List<ReturnAggRow> sumByOrderLines(@Param("ids") Collection<Long> ids);

    /** 到货行已退货（已出库）数量 */
    @Select("""
            <script>
            SELECT l.receipt_line_id AS id, SUM(l.out_qty) AS qty
              FROM pur_return_line l JOIN pur_return r ON r.id = l.return_id
             WHERE l.deleted = 0 AND r.deleted = 0 AND r.status &lt;&gt; 'VOIDED'
               AND l.receipt_line_id IN <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
             GROUP BY l.receipt_line_id
            </script>
            """)
    List<IdQtyRow> sumOutByReceiptLines(@Param("ids") Collection<Long> ids);

    /** 到货行被未完成（草稿、待审批、已审核未出库）退货单占用的数量 */
    @Select("""
            <script>
            SELECT l.receipt_line_id AS id, SUM(l.qty) AS qty
              FROM pur_return_line l JOIN pur_return r ON r.id = l.return_id
             WHERE l.deleted = 0 AND r.deleted = 0 AND r.status IN ('DRAFT', 'PENDING_APPROVAL', 'APPROVED')
               AND l.receipt_line_id IN <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
             GROUP BY l.receipt_line_id
            </script>
            """)
    List<IdQtyRow> sumOpenByReceiptLines(@Param("ids") Collection<Long> ids);
}
