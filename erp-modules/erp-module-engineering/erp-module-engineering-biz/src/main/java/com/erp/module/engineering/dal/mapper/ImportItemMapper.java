package com.erp.module.engineering.dal.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.framework.mybatis.BaseMapperX;
import com.erp.module.engineering.dal.dataobject.ImportItemDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

@Mapper
public interface ImportItemMapper extends BaseMapperX<ImportItemDO> {

    default List<ImportItemDO> selectByBatch(Long batchId) {
        return selectList(new LambdaQueryWrapper<ImportItemDO>().eq(ImportItemDO::getBatchId, batchId).orderByAsc(ImportItemDO::getSeq));
    }

    /** 之后（未回滚）的批次中涉及同一数据的批次号（回滚须从最近的批次开始） */
    @Select("<script>SELECT DISTINCT i.batch_id FROM eng_import_item i JOIN eng_import_batch b ON b.id = i.batch_id "
            + "WHERE b.deleted = 0 AND i.deleted = 0 AND b.batch_status = 'DONE' AND b.id &gt; #{batchId} "
            + "AND i.target_id IN <foreach collection='targets' item='t' open='(' separator=',' close=')'>#{t}</foreach></script>")
    List<Long> selectLaterBatches(@Param("batchId") Long batchId, @Param("targets") Collection<Long> targets);
}
