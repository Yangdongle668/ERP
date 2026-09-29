package com.erp.module.shipping.dal.mapper;

import com.erp.framework.mybatis.BaseMapperX;
import com.erp.module.shipping.dal.dataobject.ShpCartonDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ShpCartonMapper extends BaseMapperX<ShpCartonDO> {

    default List<ShpCartonDO> selectByNotice(Long noticeId) {
        return selectList(new LambdaQueryWrapper<ShpCartonDO>().eq(ShpCartonDO::getNoticeId, noticeId).orderByAsc(ShpCartonDO::getCartonNo));
    }

    default List<ShpCartonDO> selectByShipment(Long shipmentId) {
        return selectList(new LambdaQueryWrapper<ShpCartonDO>().eq(ShpCartonDO::getShipmentId, shipmentId).orderByAsc(ShpCartonDO::getCartonNo));
    }
}
