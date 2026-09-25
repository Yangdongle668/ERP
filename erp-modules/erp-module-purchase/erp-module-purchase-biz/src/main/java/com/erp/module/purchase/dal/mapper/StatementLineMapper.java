package com.erp.module.purchase.dal.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.framework.mybatis.BaseMapperX;
import com.erp.module.purchase.dal.dataobject.IdQtyRow;
import com.erp.module.purchase.dal.dataobject.StatementLineDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

@Mapper
public interface StatementLineMapper extends BaseMapperX<StatementLineDO> {

    default List<StatementLineDO> selectByParent(Long parentId) {
        return selectList(new LambdaQueryWrapper<StatementLineDO>().eq(StatementLineDO::getStatementId, parentId).orderByAsc(StatementLineDO::getLineNo));
    }

    default List<StatementLineDO> selectByParents(Collection<Long> parentIds) {
        if (parentIds == null || parentIds.isEmpty()) return List.of();
        return selectList(new LambdaQueryWrapper<StatementLineDO>().in(StatementLineDO::getStatementId, parentIds).orderByAsc(StatementLineDO::getLineNo));
    }

    /** 物理删除（明细随单据保存整体替换） */
    @Delete("DELETE FROM pur_statement_line WHERE statement_id = #{parentId}")
    int deleteByParent(@Param("parentId") Long parentId);

    /**
     * 来源行在对账单中的数量合计（退货为负数）。
     *
     * @param statuses 对账单状态（如全部未作废，或已审核、已确认）
     */
    @Select("""
            <script>
            SELECT l.source_line_id AS id, SUM(l.qty) AS qty FROM pur_statement_line l JOIN pur_statement s ON s.id = l.statement_id
             WHERE l.deleted = 0 AND s.deleted = 0 AND l.line_type IN <foreach collection="types" item="t" open="(" separator="," close=")">#{t}</foreach>
               AND s.status IN <foreach collection="statuses" item="st" open="(" separator="," close=")">#{st}</foreach>
               AND l.source_line_id IN <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
             GROUP BY l.source_line_id
            </script>
            """)
    List<IdQtyRow> sumBySourceLines(@Param("types") Collection<String> types, @Param("statuses") Collection<String> statuses,
                                    @Param("ids") Collection<Long> ids);
}
