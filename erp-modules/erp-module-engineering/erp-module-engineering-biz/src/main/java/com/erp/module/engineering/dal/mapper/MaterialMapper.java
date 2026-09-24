package com.erp.module.engineering.dal.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.common.result.PageResult;
import com.erp.framework.mybatis.BaseMapperX;
import com.erp.module.engineering.controller.vo.MaterialPageReqVO;
import com.erp.module.engineering.dal.dataobject.MaterialDO;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.util.StringUtils;

@Mapper
public interface MaterialMapper extends BaseMapperX<MaterialDO> {

    default MaterialDO selectByCode(String code) {
        return selectOne(new LambdaQueryWrapper<MaterialDO>().eq(MaterialDO::getCode, code));
    }

    default PageResult<MaterialDO> selectPage(MaterialPageReqVO req) {
        LambdaQueryWrapper<MaterialDO> q = new LambdaQueryWrapper<MaterialDO>()
                .likeRight(StringUtils.hasText(req.getCode()), MaterialDO::getCode, req.getCode())
                .like(StringUtils.hasText(req.getName()), MaterialDO::getName, req.getName())
                .eq(req.getMaterialType() != null, MaterialDO::getMaterialType, req.getMaterialType())
                .eq(req.getCategoryId() != null, MaterialDO::getCategoryId, req.getCategoryId())
                .eq(req.getStatus() != null, MaterialDO::getStatus, req.getStatus())
                .orderByDesc(MaterialDO::getId);
        return selectPage(req, q);
    }
}
