package com.erp.module.finance.dal.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.framework.mybatis.BaseMapperX;
import com.erp.module.finance.dal.dataobject.FinVoucherLineDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

@Mapper
public interface FinVoucherLineMapper extends BaseMapperX<FinVoucherLineDO> {

    default List<FinVoucherLineDO> selectByParent(Long parentId) {
        return selectList(new LambdaQueryWrapper<FinVoucherLineDO>().eq(FinVoucherLineDO::getVoucherId, parentId).orderByAsc(FinVoucherLineDO::getLineNo));
    }

    default List<FinVoucherLineDO> selectByParents(Collection<Long> parentIds) {
        if (parentIds == null || parentIds.isEmpty()) return List.of();
        return selectList(new LambdaQueryWrapper<FinVoucherLineDO>().in(FinVoucherLineDO::getVoucherId, parentIds).orderByAsc(FinVoucherLineDO::getLineNo));
    }

    /** 物理删除（明细随单据保存整体替换） */
    @Delete("DELETE FROM fin_voucher_line WHERE voucher_id = #{parentId}")
    int deleteByParent(@Param("parentId") Long parentId);
}
