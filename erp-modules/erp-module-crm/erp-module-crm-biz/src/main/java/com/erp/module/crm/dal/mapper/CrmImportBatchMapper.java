package com.erp.module.crm.dal.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.framework.mybatis.BaseMapperX;
import com.erp.module.crm.dal.dataobject.CrmImportBatchDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface CrmImportBatchMapper extends BaseMapperX<CrmImportBatchDO> {

    default List<CrmImportBatchDO> selectRecent(int limit) {
        return selectList(new LambdaQueryWrapper<CrmImportBatchDO>().orderByDesc(CrmImportBatchDO::getCreatedAt).orderByDesc(CrmImportBatchDO::getId)
                .last("LIMIT " + limit));
    }
}
