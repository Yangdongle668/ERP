package com.erp.module.engineering.dal.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.erp.common.result.PageResult;
import com.erp.framework.mybatis.BaseMapperX;
import com.erp.module.engineering.api.material.MaterialStatus;
import com.erp.module.engineering.api.material.MaterialType;
import com.erp.module.engineering.controller.vo.MaterialPageReqVO;
import com.erp.module.engineering.dal.dataobject.MaterialDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Mapper
public interface MaterialMapper extends BaseMapperX<MaterialDO> {

    /** 编码不区分大小写（ENG-MAT-R01）：编码保存时统一大写 */
    default MaterialDO selectByCode(String code) {
        return selectOne(new LambdaQueryWrapper<MaterialDO>().eq(MaterialDO::getCode, code == null ? null : code.trim().toUpperCase()));
    }

    default List<MaterialDO> selectByCodes(Collection<String> codes) {
        if (codes.isEmpty()) return List.of();
        return selectList(new LambdaQueryWrapper<MaterialDO>().in(MaterialDO::getCode, codes));
    }

    /**
     * 分页查询（需求 05-02 3.1）。categoryIds 为选中类别及其下级。
     */
    default PageResult<MaterialDO> selectPage(MaterialPageReqVO req, List<Long> categoryIds) {
        return selectPage(req, query(req, categoryIds));
    }

    default List<MaterialDO> selectForExport(MaterialPageReqVO req, List<Long> categoryIds, int limit) {
        return selectList(query(req, categoryIds).last("LIMIT " + limit));
    }

    private static LambdaQueryWrapper<MaterialDO> query(MaterialPageReqVO req, List<Long> categoryIds) {
        String keyword = StringUtils.hasText(req.getKeyword()) ? req.getKeyword().trim() : null;
        String name = StringUtils.hasText(req.getName()) ? req.getName().trim() : null;
        LambdaQueryWrapper<MaterialDO> q = new LambdaQueryWrapper<MaterialDO>()
                .likeRight(StringUtils.hasText(req.getCode()), MaterialDO::getCode, req.getCode() == null ? null : req.getCode().trim().toUpperCase())
                .and(name != null, x -> x.like(MaterialDO::getName, name).or().like(MaterialDO::getSpec, name))
                .eq(req.getMaterialType() != null, MaterialDO::getMaterialType, req.getMaterialType())
                .in(StringUtils.hasText(req.getTypes()), MaterialDO::getMaterialType, types(req.getTypes()))
                .in(categoryIds != null, MaterialDO::getCategoryId, categoryIds)
                .eq(req.getStatus() != null, MaterialDO::getStatus, req.getStatus())
                .like(StringUtils.hasText(req.getMpn()), MaterialDO::getMpn, req.getMpn() == null ? null : req.getMpn().trim())
                .eq(req.getSourceType() != null, MaterialDO::getSourceType, req.getSourceType())
                .eq(req.getBuyerId() != null, MaterialDO::getBuyerId, req.getBuyerId())
                .eq(req.getTracking() != null, MaterialDO::getTracking, req.getTracking())
                .ge(req.getCreatedFrom() != null, MaterialDO::getCreatedAt, req.getCreatedFrom())
                .le(req.getCreatedTo() != null, MaterialDO::getCreatedAt, req.getCreatedTo())
                .and(keyword != null, x -> x.likeRight(MaterialDO::getCode, keyword.toUpperCase())
                        .or().like(MaterialDO::getName, keyword).or().like(MaterialDO::getSpec, keyword));
        boolean asc = !"desc".equalsIgnoreCase(req.getSortOrder());
        switch (req.getSortField() == null ? "" : req.getSortField()) {
            case "name" -> q.orderBy(true, asc, MaterialDO::getName);
            case "updatedAt" -> q.orderBy(true, asc, MaterialDO::getUpdatedAt);
            case "code" -> q.orderBy(true, asc, MaterialDO::getCode);
            default -> q.orderByAsc(MaterialDO::getCode);
        }
        return q;
    }

    /** 选择器远程搜索：编码前缀或名称/规格模糊，最多 limit 条；ids 非空时按 ID 回显 */
    default List<MaterialDO> search(String keyword, String types, MaterialStatus status, List<Long> ids, int limit) {
        if (ids != null && !ids.isEmpty()) return selectBatchIds(ids);
        return search(keyword, types(types), status, limit);
    }

    default List<MaterialDO> search(String keyword, Collection<MaterialType> types, MaterialStatus status, int limit) {
        LambdaQueryWrapper<MaterialDO> q = new LambdaQueryWrapper<MaterialDO>()
                .eq(status != null, MaterialDO::getStatus, status)
                .in(types != null && !types.isEmpty(), MaterialDO::getMaterialType, types)
                .and(StringUtils.hasText(keyword), x -> x.likeRight(MaterialDO::getCode, keyword.trim().toUpperCase())
                        .or().like(MaterialDO::getName, keyword.trim()).or().like(MaterialDO::getSpec, keyword.trim()))
                .orderByAsc(MaterialDO::getCode)
                .last("LIMIT " + Math.max(1, Math.min(limit, 50)));
        return selectList(q);
    }

    static List<MaterialType> types(String types) {
        if (!StringUtils.hasText(types)) return List.of();
        return Arrays.stream(types.split(",")).map(String::trim).filter(StringUtils::hasText).map(MaterialType::valueOf).toList();
    }

    /** 查重（ENG-MAT-R03）：同一类别下名称+规格相同，或制造商料号相同 */
    default List<MaterialDO> selectDuplicates(Long categoryId, String dupKey, String mpnKey, Long excludeId) {
        boolean byName = categoryId != null && StringUtils.hasText(dupKey);
        boolean byMpn = StringUtils.hasText(mpnKey);
        if (!byName && !byMpn) return List.of();
        return selectList(new LambdaQueryWrapper<MaterialDO>()
                .ne(excludeId != null, MaterialDO::getId, excludeId)
                .and(x -> {
                    if (byName) x.nested(n -> n.eq(MaterialDO::getCategoryId, categoryId).eq(MaterialDO::getDupKey, dupKey));
                    if (byName && byMpn) x.or();
                    if (byMpn) x.eq(MaterialDO::getMpnKey, mpnKey);
                })
                .orderByAsc(MaterialDO::getCode).last("LIMIT 10"));
    }

    default long countByUom(String uom) {
        return selectCount(new LambdaQueryWrapper<MaterialDO>().eq(MaterialDO::getBaseUom, uom)
                .or().eq(MaterialDO::getPurchaseUom, uom).or().eq(MaterialDO::getSalesUom, uom));
    }

    default long countByCategory(Long categoryId) {
        return selectCount(new LambdaQueryWrapper<MaterialDO>().eq(MaterialDO::getCategoryId, categoryId));
    }

    default Map<Long, Long> countEnabledByCategory() {
        return groupCount(new QueryWrapper<MaterialDO>().select("category_id AS cid", "COUNT(*) AS cnt")
                .eq("status", MaterialStatus.ENABLED.name()).groupBy("category_id"));
    }

    default Map<Long, Long> countAllByCategory() {
        return groupCount(new QueryWrapper<MaterialDO>().select("category_id AS cid", "COUNT(*) AS cnt").groupBy("category_id"));
    }

    private Map<Long, Long> groupCount(QueryWrapper<MaterialDO> q) {
        Map<Long, Long> result = new HashMap<>();
        for (Map<String, Object> row : selectMaps(q)) {
            Object cid = row.get("cid") != null ? row.get("cid") : row.get("CID");
            Object cnt = row.get("cnt") != null ? row.get("cnt") : row.get("CNT");
            if (cid != null) result.put(((Number) cid).longValue(), ((Number) cnt).longValue());
        }
        return result;
    }

    @Update("UPDATE eng_material SET low_level_code = #{code} WHERE id = #{id}")
    int updateLowLevelCode(@Param("id") Long id, @Param("code") int code);

    /** 当前低位码不为 0 的物料（ID、低位码） */
    default Map<Long, Integer> selectNonZeroLowLevelCodes() {
        Map<Long, Integer> result = new HashMap<>();
        selectList(new LambdaQueryWrapper<MaterialDO>().select(MaterialDO::getId, MaterialDO::getLowLevelCode).gt(MaterialDO::getLowLevelCode, 0))
                .forEach(m -> result.put(m.getId(), m.getLowLevelCode()));
        return result;
    }
}
