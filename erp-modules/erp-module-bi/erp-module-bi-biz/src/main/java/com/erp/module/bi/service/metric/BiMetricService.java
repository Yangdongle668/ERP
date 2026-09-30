package com.erp.module.bi.service.metric;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.common.exception.BizException;
import com.erp.framework.security.LoginUser;
import com.erp.module.bi.api.BiErrorCodes;
import com.erp.module.bi.dal.dataobject.BiMetricDO;
import com.erp.module.bi.dal.mapper.BiMetricMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 指标库（需求 13-01 第 4 节）：代码注册的定义 + 数据库中可修改的展示名称、说明、负责人 */
@Service
public class BiMetricService {

    /** 指标及其可修改的说明 */
    public record MetricInfo(MetricDefinition def, String displayName, String remark, String ownerName) {
        public String name() {
            return displayName == null || displayName.isBlank() ? def.name() : displayName;
        }
    }

    private final BiMetricMapper metricMapper;

    public BiMetricService(BiMetricMapper metricMapper) {
        this.metricMapper = metricMapper;
    }

    public List<MetricInfo> list() {
        Map<String, BiMetricDO> saved = new HashMap<>();
        metricMapper.selectList(null).forEach(m -> saved.put(m.getCode(), m));
        return MetricRegistry.all().stream().map(d -> info(d, saved.get(d.code()))).toList();
    }

    public MetricInfo get(String code) {
        MetricDefinition d = MetricRegistry.find(code).orElseThrow(() -> BizException.of(BiErrorCodes.METRIC_NOT_EXISTS, code));
        return info(d, metricMapper.selectOne(new LambdaQueryWrapper<BiMetricDO>().eq(BiMetricDO::getCode, code)));
    }

    /** 当前用户有权限查看的指标 */
    public List<MetricInfo> listVisible(LoginUser user) {
        return list().stream().filter(m -> user != null && user.hasPermission(m.def().permission())).toList();
    }

    /** 名称映射（查询结果列名、AI 指标清单） */
    public Map<String, String> names() {
        Map<String, String> names = new HashMap<>();
        list().forEach(m -> names.put(m.def().code(), m.name()));
        return names;
    }

    @Transactional(rollbackFor = Exception.class)
    public MetricInfo update(String code, String displayName, String remark, String ownerName) {
        MetricRegistry.find(code).orElseThrow(() -> BizException.of(BiErrorCodes.METRIC_NOT_EXISTS, code));
        BiMetricDO row = metricMapper.selectOne(new LambdaQueryWrapper<BiMetricDO>().eq(BiMetricDO::getCode, code));
        boolean isNew = row == null;
        if (isNew) {
            row = new BiMetricDO();
            row.setCode(code);
        }
        row.setDisplayName(blankToNull(displayName));
        row.setDescription(blankToNull(remark));
        row.setOwnerName(blankToNull(ownerName));
        if (isNew) metricMapper.insert(row);
        else metricMapper.updateByIdOrFail(row);
        return get(code);
    }

    private static MetricInfo info(MetricDefinition d, BiMetricDO row) {
        return new MetricInfo(d, row == null ? null : row.getDisplayName(), row == null ? null : row.getDescription(),
                row == null ? null : row.getOwnerName());
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
