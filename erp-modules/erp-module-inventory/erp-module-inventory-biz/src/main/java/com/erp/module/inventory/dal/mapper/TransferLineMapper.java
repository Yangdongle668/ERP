package com.erp.module.inventory.dal.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.framework.mybatis.BaseMapperX;
import com.erp.module.inventory.dal.dataobject.TransferLineDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

@Mapper
public interface TransferLineMapper extends BaseMapperX<TransferLineDO> {

    default List<TransferLineDO> selectByDoc(Long docId) {
        return selectList(new LambdaQueryWrapper<TransferLineDO>().eq(TransferLineDO::getTransferId, docId).orderByAsc(TransferLineDO::getLineNo));
    }

    default List<TransferLineDO> selectByDocs(Collection<Long> docIds) {
        if (docIds.isEmpty()) return List.of();
        return selectList(new LambdaQueryWrapper<TransferLineDO>().in(TransferLineDO::getTransferId, docIds).orderByAsc(TransferLineDO::getLineNo));
    }

    /** 物理删除（行随单据保存整体替换） */
    @Delete("DELETE FROM inv_transfer_line WHERE transfer_id = #{docId}")
    int deleteByDoc(@Param("docId") Long docId);
}
