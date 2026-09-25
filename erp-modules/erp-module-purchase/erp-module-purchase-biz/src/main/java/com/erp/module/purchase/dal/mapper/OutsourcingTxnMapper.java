package com.erp.module.purchase.dal.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.framework.mybatis.BaseMapperX;
import com.erp.module.purchase.dal.dataobject.OutsourcingTxnDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface OutsourcingTxnMapper extends BaseMapperX<OutsourcingTxnDO> {

    default List<OutsourcingTxnDO> selectByOutsourcing(Long outsourcingId) {
        return selectList(new LambdaQueryWrapper<OutsourcingTxnDO>().eq(OutsourcingTxnDO::getOutsourcingId, outsourcingId).orderByAsc(OutsourcingTxnDO::getId));
    }

    default List<OutsourcingTxnDO> selectByStockDoc(Long stockDocId) {
        return selectList(new LambdaQueryWrapper<OutsourcingTxnDO>().eq(OutsourcingTxnDO::getStockDocId, stockDocId));
    }
}
