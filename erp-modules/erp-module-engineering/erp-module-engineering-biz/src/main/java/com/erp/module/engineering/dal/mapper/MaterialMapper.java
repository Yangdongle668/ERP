package com.erp.module.engineering.dal.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.common.result.PageResult;
import com.erp.framework.mybatis.BaseMapperX;
import com.erp.module.engineering.controller.vo.MaterialPageReqVO;
import com.erp.module.engineering.dal.dataobject.MaterialDO;
import com.erp.module.engineering.api.material.MaterialStatus;
import com.erp.module.engineering.api.material.MaterialType;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;

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
                .and(StringUtils.hasText(req.getKeyword()), x -> x.likeRight(MaterialDO::getCode, req.getKeyword().trim().toUpperCase())
                        .or().like(MaterialDO::getName, req.getKeyword().trim()).or().like(MaterialDO::getSpec, req.getKeyword().trim()))
                .in(StringUtils.hasText(req.getTypes()), MaterialDO::getMaterialType, types(req.getTypes()))
                .orderByDesc(MaterialDO::getId);
        return selectPage(req, q);
    }

    /** 选择器远程搜索：编码前缀或名称/规格模糊，最多 limit 条；ids 非空时按 ID 回显 */
    default List<MaterialDO> search(String keyword, String types, MaterialStatus status, List<Long> ids, int limit) {
        if (ids != null && !ids.isEmpty()) return selectBatchIds(ids);
        LambdaQueryWrapper<MaterialDO> q = new LambdaQueryWrapper<MaterialDO>()
                .eq(status != null, MaterialDO::getStatus, status)
                .in(StringUtils.hasText(types), MaterialDO::getMaterialType, types(types))
                .and(StringUtils.hasText(keyword), x -> x.likeRight(MaterialDO::getCode, keyword.trim().toUpperCase())
                        .or().like(MaterialDO::getName, keyword.trim()).or().like(MaterialDO::getSpec, keyword.trim()))
                .orderByAsc(MaterialDO::getCode)
                .last("LIMIT " + Math.max(1, Math.min(limit, 50)));
        return selectList(q);
    }

    private static List<MaterialType> types(String types) {
        if (!StringUtils.hasText(types)) return List.of();
        return Arrays.stream(types.split(",")).map(String::trim).filter(StringUtils::hasText).map(MaterialType::valueOf).toList();
    }

    default long countByUom(String uom) {
        return selectCount(new LambdaQueryWrapper<MaterialDO>().eq(MaterialDO::getBaseUom, uom));
    }
}
