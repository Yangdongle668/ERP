package com.erp.module.engineering.dal.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.framework.mybatis.BaseMapperX;
import com.erp.module.engineering.dal.dataobject.ImportBatchDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ImportBatchMapper extends BaseMapperX<ImportBatchDO> {

    default List<ImportBatchDO> selectRecent(String bizType, int limit) {
        return selectList(new LambdaQueryWrapper<ImportBatchDO>().eq(ImportBatchDO::getBizType, bizType)
                .orderByDesc(ImportBatchDO::getCreatedAt).orderByDesc(ImportBatchDO::getId).last("LIMIT " + limit));
    }
}
