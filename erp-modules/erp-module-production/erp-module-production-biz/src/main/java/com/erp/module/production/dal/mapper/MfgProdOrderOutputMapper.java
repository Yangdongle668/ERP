package com.erp.module.production.dal.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.framework.mybatis.BaseMapperX;
import com.erp.module.production.dal.dataobject.MfgProdOrderOutputDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface MfgProdOrderOutputMapper extends BaseMapperX<MfgProdOrderOutputDO> {

    default List<MfgProdOrderOutputDO> selectByParent(Long parentId) {
        return selectList(new LambdaQueryWrapper<MfgProdOrderOutputDO>().eq(MfgProdOrderOutputDO::getProdOrderId, parentId)
                .orderByAsc(MfgProdOrderOutputDO::getLineNo));
    }

    /** 物理删除（撤销下达时整体删除） */
    @Delete("DELETE FROM mfg_prod_order_output WHERE prod_order_id = #{parentId}")
    int deleteByParent(@Param("parentId") Long parentId);
}
