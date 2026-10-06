package com.erp.module.crm.dal.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.framework.mybatis.BaseMapperX;
import com.erp.module.crm.dal.dataobject.CrmImportItemDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface CrmImportItemMapper extends BaseMapperX<CrmImportItemDO> {

    default List<CrmImportItemDO> selectByBatch(Long batchId) {
        return selectList(new LambdaQueryWrapper<CrmImportItemDO>().eq(CrmImportItemDO::getBatchId, batchId).orderByAsc(CrmImportItemDO::getSeq));
    }
}
