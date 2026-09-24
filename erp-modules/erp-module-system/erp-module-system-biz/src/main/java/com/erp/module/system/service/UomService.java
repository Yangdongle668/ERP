package com.erp.module.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.common.enums.EnableStatus;
import com.erp.common.exception.BizException;
import com.erp.module.system.api.SystemErrorCodes;
import com.erp.module.system.api.uom.UomApi;
import com.erp.module.system.api.uom.UomDTO;
import com.erp.module.system.api.uom.UomReferenceChecker;
import com.erp.module.system.controller.vo.UomVOs.ConversionResp;
import com.erp.module.system.controller.vo.UomVOs.ConversionSave;
import com.erp.module.system.controller.vo.UomVOs.UomResp;
import com.erp.module.system.controller.vo.UomVOs.UomSave;
import com.erp.module.system.controller.vo.UomVOs.UomSimple;
import com.erp.module.system.dal.dataobject.UomConversionDO;
import com.erp.module.system.dal.dataobject.UomDO;
import com.erp.module.system.dal.mapper.UomConversionMapper;
import com.erp.module.system.dal.mapper.UomMapper;
import com.erp.module.system.enums.UomCategory;
import com.erp.module.system.service.support.SystemCaches;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 计量单位与通用换算（01-06），同时是 {@link UomApi} 的实现。 */
@Service
public class UomService implements UomApi {

    private static final MathContext MC = new MathContext(20, RoundingMode.HALF_UP);

    private final UomMapper uomMapper;
    private final UomConversionMapper conversionMapper;
    private final SystemCaches caches;
    private final List<UomReferenceChecker> referenceCheckers;

    public UomService(UomMapper uomMapper, UomConversionMapper conversionMapper, SystemCaches caches, List<UomReferenceChecker> referenceCheckers) {
        this.uomMapper = uomMapper;
        this.conversionMapper = conversionMapper;
        this.caches = caches;
        this.referenceCheckers = referenceCheckers;
    }

    // ==================== 单位 ====================

    public List<UomResp> list(String keyword, String category, String status) {
        LambdaQueryWrapper<UomDO> w = new LambdaQueryWrapper<UomDO>()
                .eq(StringUtils.hasText(category), UomDO::getCategory, StringUtils.hasText(category) ? UomCategory.valueOf(category) : null)
                .eq(StringUtils.hasText(status), UomDO::getStatus, StringUtils.hasText(status) ? EnableStatus.valueOf(status) : null)
                .and(StringUtils.hasText(keyword), x -> x.like(UomDO::getCode, keyword.trim()).or().like(UomDO::getName, keyword.trim()));
        return uomMapper.selectList(w).stream()
                .sorted(Comparator.comparing((UomDO u) -> u.getCategory().ordinal()).thenComparingInt(UomDO::getSort))
                .map(u -> new UomResp(u.getId(), u.getCode(), u.getName(), u.getNameEn(), u.getCategory().name(), u.getPrecision(),
                        u.getSort(), Boolean.TRUE.equals(u.getBuiltin()), u.getStatus().name(), u.getVersion()))
                .toList();
    }

    public List<UomSimple> simple() {
        return all().values().stream().filter(u -> u.getStatus() == EnableStatus.ENABLED)
                .sorted(Comparator.comparing((UomDO u) -> u.getCategory().ordinal()).thenComparingInt(UomDO::getSort))
                .map(u -> new UomSimple(u.getCode(), u.getName(), u.getNameEn(), u.getCategory().name(), u.getPrecision())).toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(UomSave req) {
        String code = req.code().trim().toUpperCase();
        if (uomMapper.selectByCode(code) != null) throw BizException.of(SystemErrorCodes.UOM_CODE_DUPLICATE, code);
        checkName(req.name().trim(), null);
        UomDO u = new UomDO();
        u.setCode(code);
        u.setName(req.name().trim());
        u.setNameEn(StringUtils.hasText(req.nameEn()) ? req.nameEn().trim() : null);
        u.setCategory(UomCategory.valueOf(req.category()));
        u.setPrecision(req.precision());
        u.setSort(req.sort());
        u.setBuiltin(false);
        u.setStatus(EnableStatus.ENABLED);
        uomMapper.insert(u);
        changed();
        return u.getId();
    }

    /** 编码不可改；被使用后类别不可改（R04）；精度只能调大（R05） */
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, UomSave req) {
        UomDO u = getUom(id);
        checkName(req.name().trim(), id);
        UomCategory category = UomCategory.valueOf(req.category());
        if (category != u.getCategory() && (Boolean.TRUE.equals(u.getBuiltin()) || isReferenced(u.getCode()))) {
            throw new BizException(SystemErrorCodes.UOM_CATEGORY_LOCKED);
        }
        if (req.precision() < u.getPrecision()) throw new BizException(SystemErrorCodes.UOM_PRECISION_DECREASE);
        u.setName(req.name().trim());
        u.setNameEn(StringUtils.hasText(req.nameEn()) ? req.nameEn().trim() : null);
        u.setCategory(category);
        u.setPrecision(req.precision());
        u.setSort(req.sort());
        u.setVersion(req.version());
        uomMapper.updateByIdOrFail(u);
        changed();
    }

    @Transactional(rollbackFor = Exception.class)
    public void changeStatus(Long id, EnableStatus status) {
        UomDO u = getUom(id);
        if (u.getStatus() == status) return;
        u.setStatus(status);
        uomMapper.updateByIdOrFail(u);
        changed();
    }

    /** 删除（R03）：内置单位、被物料或换算引用的单位不能删除 */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        UomDO u = getUom(id);
        if (Boolean.TRUE.equals(u.getBuiltin())) throw new BizException(SystemErrorCodes.UOM_BUILTIN);
        boolean inConversion = conversionMapper.selectCount(new LambdaQueryWrapper<UomConversionDO>()
                .eq(UomConversionDO::getFromUom, u.getCode()).or().eq(UomConversionDO::getToUom, u.getCode())) > 0;
        if (inConversion || isReferenced(u.getCode())) throw new BizException(SystemErrorCodes.UOM_REFERENCED);
        uomMapper.deleteById(id);
        changed();
    }

    private boolean isReferenced(String code) {
        return referenceCheckers.stream().anyMatch(c -> c.isReferenced(code));
    }

    private void checkName(String name, Long excludeId) {
        UomDO other = uomMapper.selectByName(name);
        if (other != null && !other.getId().equals(excludeId)) throw BizException.of(SystemErrorCodes.UOM_NAME_DUPLICATE, name);
    }

    // ==================== 通用换算 ====================

    public List<ConversionResp> conversions() {
        return conversionMapper.selectList(new LambdaQueryWrapper<UomConversionDO>().orderByAsc(UomConversionDO::getId)).stream()
                .map(c -> new ConversionResp(c.getId(), c.getFromUom(), c.getToUom(), c.getRate().stripTrailingZeros(), c.getVersion())).toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public Long createConversion(ConversionSave req) {
        UomConversionDO c = new UomConversionDO();
        checkConversion(req, null);
        c.setFromUom(req.fromUom());
        c.setToUom(req.toUom());
        c.setRate(req.rate());
        conversionMapper.insert(c);
        changed();
        return c.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateConversion(Long id, ConversionSave req) {
        UomConversionDO c = conversionMapper.selectById(id);
        if (c == null) throw BizException.of(SystemErrorCodes.UOM_NOT_EXISTS, id);
        checkConversion(req, id);
        c.setFromUom(req.fromUom());
        c.setToUom(req.toUom());
        c.setRate(req.rate());
        c.setVersion(req.version());
        conversionMapper.updateByIdOrFail(c);
        changed();
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteConversion(Long id) {
        conversionMapper.deleteById(id);
        changed();
    }

    /** R06：同类别；同一对单位（含反向）只能有一条 */
    private void checkConversion(ConversionSave req, Long excludeId) {
        if (req.fromUom().equals(req.toUom())) throw new BizException(SystemErrorCodes.UOM_CONVERSION_SAME);
        UomDO from = uomMapper.selectByCode(req.fromUom());
        UomDO to = uomMapper.selectByCode(req.toUom());
        if (from == null) throw BizException.of(SystemErrorCodes.UOM_NOT_EXISTS, req.fromUom());
        if (to == null) throw BizException.of(SystemErrorCodes.UOM_NOT_EXISTS, req.toUom());
        if (from.getCategory() != to.getCategory()) throw new BizException(SystemErrorCodes.UOM_CONVERSION_CATEGORY);
        boolean dup = conversionMapper.selectList(null).stream().anyMatch(c -> !c.getId().equals(excludeId)
                && ((c.getFromUom().equals(req.fromUom()) && c.getToUom().equals(req.toUom()))
                || (c.getFromUom().equals(req.toUom()) && c.getToUom().equals(req.fromUom()))));
        if (dup) throw new BizException(SystemErrorCodes.UOM_CONVERSION_DUPLICATE);
    }

    private UomDO getUom(Long id) {
        UomDO u = uomMapper.selectById(id);
        if (u == null) throw BizException.of(SystemErrorCodes.UOM_NOT_EXISTS, id);
        return u;
    }

    private void changed() {
        caches.clear(SystemCaches.UOM);
    }

    // ==================== UomApi（缓存） ====================

    private Map<String, UomDO> all() {
        return caches.get(SystemCaches.UOM, "all", () -> {
            Map<String, UomDO> m = new LinkedHashMap<>();
            uomMapper.selectList(null).forEach(u -> m.put(u.getCode(), u));
            return m;
        });
    }

    private List<UomConversionDO> allConversions() {
        return caches.get(SystemCaches.UOM, "conversions", () -> conversionMapper.selectList(null));
    }

    @Override
    public Optional<UomDTO> get(String code) {
        UomDO u = code == null ? null : all().get(code);
        return Optional.ofNullable(u).map(x -> new UomDTO(x.getCode(), x.getName(), x.getNameEn(), x.getCategory().name(),
                x.getPrecision(), x.getStatus() == EnableStatus.ENABLED));
    }

    @Override
    public UomDTO validate(String code) {
        return get(code).filter(UomDTO::enabled).orElseThrow(() -> BizException.of(SystemErrorCodes.UOM_NOT_EXISTS, code));
    }

    /** R07：相同单位直接返回；直接或反向换算；经同类别一个中间单位换算一次；结果按目标单位精度舍入 */
    @Override
    public BigDecimal convert(BigDecimal qty, String fromUom, String toUom) {
        if (qty == null) return null;
        if (fromUom.equals(toUom)) return qty;
        BigDecimal rate = rate(fromUom, toUom);
        if (rate == null) {
            UomDO from = all().get(fromUom);
            if (from != null) {
                for (UomDO mid : all().values()) {
                    if (mid.getCategory() != from.getCategory() || mid.getCode().equals(fromUom) || mid.getCode().equals(toUom)) continue;
                    BigDecimal r1 = rate(fromUom, mid.getCode());
                    BigDecimal r2 = r1 == null ? null : rate(mid.getCode(), toUom);
                    if (r2 != null) {
                        rate = r1.multiply(r2, MC);
                        break;
                    }
                }
            }
        }
        if (rate == null) throw BizException.of(SystemErrorCodes.UOM_NO_CONVERSION, fromUom, toUom);
        return round(qty.multiply(rate, MC), toUom);
    }

    private BigDecimal rate(String from, String to) {
        for (UomConversionDO c : allConversions()) {
            if (c.getFromUom().equals(from) && c.getToUom().equals(to)) return c.getRate();
            if (c.getFromUom().equals(to) && c.getToUom().equals(from)) return BigDecimal.ONE.divide(c.getRate(), MC);
        }
        return null;
    }

    @Override
    public BigDecimal round(BigDecimal qty, String uom) {
        return qty == null ? null : qty.setScale(precision(uom), RoundingMode.HALF_UP);
    }

    @Override
    public int precision(String uom) {
        UomDO u = uom == null ? null : all().get(uom);
        return u == null ? 4 : u.getPrecision();
    }
}
