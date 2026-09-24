package com.erp.module.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.OrderItem;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.common.enums.EnableStatus;
import com.erp.common.exception.BizException;
import com.erp.common.result.PageResult;
import com.erp.framework.excel.ImportRow;
import com.erp.module.system.api.SystemErrorCodes;
import com.erp.module.system.api.currency.CurrencyApi;
import com.erp.module.system.api.currency.CurrencyDTO;
import com.erp.module.system.api.currency.CurrencyReferenceChecker;
import com.erp.module.system.api.currency.RateType;
import com.erp.module.system.controller.vo.CurrencyVOs.CurrencyResp;
import com.erp.module.system.controller.vo.CurrencyVOs.CurrencySave;
import com.erp.module.system.controller.vo.CurrencyVOs.CurrencySimple;
import com.erp.module.system.controller.vo.CurrencyVOs.RateBatch;
import com.erp.module.system.controller.vo.CurrencyVOs.RateLookup;
import com.erp.module.system.controller.vo.CurrencyVOs.RateQuery;
import com.erp.module.system.controller.vo.CurrencyVOs.RateResp;
import com.erp.module.system.controller.vo.CurrencyVOs.RateSave;
import com.erp.module.system.dal.dataobject.CurrencyDO;
import com.erp.module.system.dal.dataobject.ExchangeRateDO;
import com.erp.module.system.dal.dataobject.UserDO;
import com.erp.module.system.dal.mapper.CurrencyMapper;
import com.erp.module.system.dal.mapper.ExchangeRateMapper;
import com.erp.module.system.dal.mapper.UserMapper;
import com.erp.module.system.service.support.SystemCaches;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** 币别与汇率（01-07），同时是 {@link CurrencyApi} 的实现。 */
@Service
public class CurrencyService implements CurrencyApi {

    private static final Map<String, RateType> RATE_TYPE_LABELS = Map.of("日汇率", RateType.DAILY, "月末汇率", RateType.MONTH_END);

    private final CurrencyMapper currencyMapper;
    private final ExchangeRateMapper rateMapper;
    private final UserMapper userMapper;
    private final SystemCaches caches;
    private final List<CurrencyReferenceChecker> referenceCheckers;

    public CurrencyService(CurrencyMapper currencyMapper, ExchangeRateMapper rateMapper, UserMapper userMapper, SystemCaches caches,
                           List<CurrencyReferenceChecker> referenceCheckers) {
        this.currencyMapper = currencyMapper;
        this.rateMapper = rateMapper;
        this.userMapper = userMapper;
        this.caches = caches;
        this.referenceCheckers = referenceCheckers;
    }

    // ==================== 币别 ====================

    public List<CurrencyResp> list() {
        return currencyMapper.selectList(null).stream()
                .sorted(Comparator.comparingInt(CurrencyDO::getSort).thenComparing(CurrencyDO::getCode))
                .map(c -> new CurrencyResp(c.getId(), c.getCode(), c.getName(), c.getNameEn(), c.getSymbol(), c.getAmountPrecision(),
                        Boolean.TRUE.equals(c.getBase()), c.getSort(), c.getStatus().name(), c.getVersion()))
                .toList();
    }

    public List<CurrencySimple> simple() {
        return all().values().stream().filter(c -> c.getStatus() == EnableStatus.ENABLED)
                .sorted(Comparator.comparingInt(CurrencyDO::getSort))
                .map(c -> new CurrencySimple(c.getCode(), c.getName(), c.getSymbol(), c.getAmountPrecision(), Boolean.TRUE.equals(c.getBase())))
                .toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(CurrencySave req) {
        String code = req.code().trim().toUpperCase();
        if (currencyMapper.selectByCode(code) != null) throw BizException.of(SystemErrorCodes.CURRENCY_DUPLICATE, code);
        CurrencyDO c = new CurrencyDO();
        c.setCode(code);
        fill(c, req);
        c.setBase(false);
        c.setStatus(EnableStatus.ENABLED);
        try {
            currencyMapper.insert(c);
        } catch (DuplicateKeyException e) {
            throw BizException.of(SystemErrorCodes.CURRENCY_DUPLICATE, code);
        }
        changed();
        return c.getId();
    }

    /** 代码不可改；系统中已有金额数据后不能修改金额精度 */
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, CurrencySave req) {
        CurrencyDO c = getCurrency(id);
        if (!Objects.equals(c.getAmountPrecision(), req.amountPrecision()) && hasAmountData()) {
            throw new BizException(SystemErrorCodes.CURRENCY_PRECISION_LOCKED);
        }
        fill(c, req);
        c.setVersion(req.version());
        currencyMapper.updateByIdOrFail(c);
        changed();
    }

    private static void fill(CurrencyDO c, CurrencySave req) {
        c.setName(req.name().trim());
        c.setNameEn(StringUtils.hasText(req.nameEn()) ? req.nameEn().trim() : null);
        c.setSymbol(StringUtils.hasText(req.symbol()) ? req.symbol().trim() : null);
        c.setAmountPrecision(req.amountPrecision());
        c.setSort(req.sort());
    }

    @Transactional(rollbackFor = Exception.class)
    public void changeStatus(Long id, EnableStatus status) {
        CurrencyDO c = getCurrency(id);
        if (status == EnableStatus.DISABLED && Boolean.TRUE.equals(c.getBase())) throw new BizException(SystemErrorCodes.CURRENCY_BASE_CANNOT_DISABLE);
        if (c.getStatus() == status) return;
        c.setStatus(status);
        currencyMapper.updateByIdOrFail(c);
        changed();
    }

    /** 设为本位币（R03）：任何业务单据中存在金额数据时不允许；同时删除新本位币的汇率记录 */
    @Transactional(rollbackFor = Exception.class)
    public void setBase(Long id) {
        CurrencyDO c = getCurrency(id);
        if (Boolean.TRUE.equals(c.getBase())) return;
        if (hasAmountData()) throw new BizException(SystemErrorCodes.CURRENCY_BASE_LOCKED);
        if (c.getStatus() != EnableStatus.ENABLED) throw BizException.of(SystemErrorCodes.CURRENCY_NOT_EXISTS, c.getCode());
        currencyMapper.clearBase();
        c = getCurrency(id);
        c.setBase(true);
        currencyMapper.updateByIdOrFail(c);
        rateMapper.delete(new LambdaQueryWrapper<ExchangeRateDO>().eq(ExchangeRateDO::getCurrency, c.getCode()));
        changed();
    }

    private boolean hasAmountData() {
        return referenceCheckers.stream().anyMatch(CurrencyReferenceChecker::hasAmountData);
    }

    private CurrencyDO getCurrency(Long id) {
        CurrencyDO c = currencyMapper.selectById(id);
        if (c == null) throw BizException.of(SystemErrorCodes.CURRENCY_NOT_EXISTS, id);
        return c;
    }

    // ==================== 汇率 ====================

    public PageResult<RateResp> pageRates(RateQuery q) {
        LambdaQueryWrapper<ExchangeRateDO> w = new LambdaQueryWrapper<ExchangeRateDO>()
                .eq(StringUtils.hasText(q.getCurrency()), ExchangeRateDO::getCurrency, q.getCurrency())
                .eq(StringUtils.hasText(q.getRateType()), ExchangeRateDO::getRateType, StringUtils.hasText(q.getRateType()) ? RateType.valueOf(q.getRateType()) : null)
                .ge(q.getDateFrom() != null, ExchangeRateDO::getEffectiveDate, q.getDateFrom())
                .le(q.getDateTo() != null, ExchangeRateDO::getEffectiveDate, q.getDateTo());
        Page<ExchangeRateDO> page = Page.of(q.getPageNo(), q.getPageSize());
        page.addOrder("asc".equalsIgnoreCase(q.getSortOrder()) ? OrderItem.asc("effective_date") : OrderItem.desc("effective_date"));
        page.addOrder(OrderItem.asc("currency"));
        Page<ExchangeRateDO> result = rateMapper.selectPage(page, w);
        Set<Long> userIds = result.getRecords().stream().map(ExchangeRateDO::getUpdatedBy).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> names = new java.util.HashMap<>();
        if (!userIds.isEmpty()) userMapper.selectBatchIds(userIds).forEach(u -> names.put(u.getId(), u.getRealName()));
        return new PageResult<>(result.getRecords().stream().map(r -> new RateResp(r.getId(), r.getCurrency(), r.getRateType().name(),
                r.getEffectiveDate(), r.getRate().stripTrailingZeros(), r.getSource(), r.getRemark(), names.get(r.getUpdatedBy()),
                r.getUpdatedAt(), r.getVersion())).toList(), result.getTotal());
    }

    @Transactional(rollbackFor = Exception.class)
    public Long createRate(RateSave req) {
        return upsertRate(req.currency(), RateType.valueOf(req.rateType()), req.effectiveDate(), req.rate(), req.remark(),
                ExchangeRateDO.SOURCE_MANUAL, false).getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateRate(Long id, RateSave req) {
        ExchangeRateDO r = rateMapper.selectById(id);
        if (r == null) throw new BizException(SystemErrorCodes.RATE_NOT_EXISTS);
        RateType type = RateType.valueOf(req.rateType());
        LocalDate date = normalizeDate(type, req.effectiveDate());
        checkForeign(req.currency());
        ExchangeRateDO same = findRate(req.currency(), type, date);
        if (same != null && !same.getId().equals(id)) throw duplicate(req.currency(), date, type);
        r.setCurrency(req.currency());
        r.setRateType(type);
        r.setEffectiveDate(date);
        r.setRate(checkRate(req.rate()));
        r.setRemark(req.remark());
        r.setVersion(req.version());
        rateMapper.updateByIdOrFail(r);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteRate(Long id) {
        rateMapper.deleteById(id);
    }

    /** 批量录入：一次保存；同键已存在时报错（R04） */
    @Transactional(rollbackFor = Exception.class)
    public int batch(RateBatch req) {
        RateType type = RateType.valueOf(req.rateType());
        for (var line : req.lines()) {
            upsertRate(line.currency(), type, req.effectiveDate(), line.rate(), null, ExchangeRateDO.SOURCE_MANUAL, false);
        }
        return req.lines().size();
    }

    private ExchangeRateDO upsertRate(String currency, RateType type, LocalDate effectiveDate, BigDecimal rate, String remark,
                                      String source, boolean overwrite) {
        checkForeign(currency);
        LocalDate date = normalizeDate(type, effectiveDate);
        ExchangeRateDO existing = findRate(currency, type, date);
        if (existing != null) {
            if (!overwrite) throw duplicate(currency, date, type);
            existing.setRate(checkRate(rate));
            existing.setSource(source);
            existing.setRemark(remark);
            rateMapper.updateByIdOrFail(existing);
            return existing;
        }
        ExchangeRateDO r = new ExchangeRateDO();
        r.setCurrency(currency);
        r.setRateType(type);
        r.setEffectiveDate(date);
        r.setRate(checkRate(rate));
        r.setSource(source);
        r.setRemark(remark);
        try {
            rateMapper.insert(r);
        } catch (DuplicateKeyException e) {
            throw duplicate(currency, date, type);
        }
        return r;
    }

    /** 月末汇率的生效日期自动调整为所选月份最后一天 */
    static LocalDate normalizeDate(RateType type, LocalDate date) {
        return type == RateType.MONTH_END ? YearMonth.from(date).atEndOfMonth() : date;
    }

    private static BigDecimal checkRate(BigDecimal rate) {
        if (rate == null || rate.signum() <= 0) throw new BizException(SystemErrorCodes.RATE_INVALID);
        return rate.setScale(6, RoundingMode.HALF_UP);
    }

    private void checkForeign(String currency) {
        CurrencyDO c = all().get(currency);
        if (c == null) throw BizException.of(SystemErrorCodes.CURRENCY_NOT_EXISTS, currency);
        if (Boolean.TRUE.equals(c.getBase())) throw new BizException(SystemErrorCodes.RATE_BASE_CURRENCY);
    }

    private ExchangeRateDO findRate(String currency, RateType type, LocalDate date) {
        return rateMapper.selectOne(new LambdaQueryWrapper<ExchangeRateDO>().eq(ExchangeRateDO::getCurrency, currency)
                .eq(ExchangeRateDO::getRateType, type).eq(ExchangeRateDO::getEffectiveDate, date));
    }

    private static BizException duplicate(String currency, LocalDate date, RateType type) {
        return BizException.of(SystemErrorCodes.RATE_DUPLICATE, currency, date, type == RateType.DAILY ? "日汇率" : "月末汇率");
    }

    /** 单据带出汇率（R05），返回实际命中的生效日期 */
    public RateLookup lookup(String currency, LocalDate date, RateType type) {
        CurrencyDO c = all().get(currency);
        if (c == null) throw BizException.of(SystemErrorCodes.CURRENCY_NOT_EXISTS, currency);
        if (Boolean.TRUE.equals(c.getBase())) return new RateLookup(BigDecimal.ONE, date);
        ExchangeRateDO r = latest(currency, date, type);
        if (r == null) throw BizException.of(SystemErrorCodes.RATE_NOT_FOUND, currency, date);
        return new RateLookup(r.getRate().stripTrailingZeros(), r.getEffectiveDate());
    }

    private ExchangeRateDO latest(String currency, LocalDate date, RateType type) {
        return rateMapper.selectOne(new LambdaQueryWrapper<ExchangeRateDO>().eq(ExchangeRateDO::getCurrency, currency)
                .eq(ExchangeRateDO::getRateType, type).le(ExchangeRateDO::getEffectiveDate, date)
                .orderByDesc(ExchangeRateDO::getEffectiveDate).last("LIMIT 1"));
    }

    // ==================== 导入导出 ====================

    /** 校验导入行；已存在同键记录时标注“覆盖” */
    public void checkImport(List<ImportRow> rows) {
        Set<String> seen = new HashSet<>();
        for (ImportRow r : rows) {
            String cur = r.get("currency") == null ? null : r.get("currency").toUpperCase();
            if (cur == null) r.error("币别不能为空");
            else {
                CurrencyDO c = all().get(cur);
                if (c == null) r.error("币别 " + cur + " 不存在");
                else if (Boolean.TRUE.equals(c.getBase())) r.error("本位币不需要维护汇率");
            }
            RateType type = RATE_TYPE_LABELS.get(r.get("rateType"));
            if (type == null) r.error("汇率类型只能是“日汇率”或“月末汇率”");
            LocalDate date = null;
            try {
                date = r.get("effectiveDate") == null ? null : LocalDate.parse(r.get("effectiveDate"));
            } catch (DateTimeParseException e) {
                r.error("生效日期格式不正确，应为 yyyy-MM-dd");
            }
            if (r.get("effectiveDate") == null) r.error("生效日期不能为空");
            try {
                BigDecimal rate = r.get("rate") == null ? null : new BigDecimal(r.get("rate"));
                if (rate == null) r.error("汇率不能为空");
                else if (rate.signum() <= 0) r.error("汇率必须大于 0");
            } catch (NumberFormatException e) {
                r.error("汇率必须是数字");
            }
            if (cur != null && type != null && date != null && !r.hasError()) {
                String key = cur + "|" + type + "|" + normalizeDate(type, date);
                if (!seen.add(key)) r.error("文件中存在重复记录");
            }
        }
    }

    public String importAction(ImportRow r) {
        if (r.hasError()) return null;
        RateType type = RATE_TYPE_LABELS.get(r.get("rateType"));
        LocalDate date = normalizeDate(type, LocalDate.parse(r.get("effectiveDate")));
        return findRate(r.get("currency").toUpperCase(), type, date) == null ? "新增" : "覆盖";
    }

    @Transactional(rollbackFor = Exception.class)
    public int importRates(List<ImportRow> rows) {
        for (ImportRow r : rows) {
            upsertRate(r.get("currency").toUpperCase(), RATE_TYPE_LABELS.get(r.get("rateType")), LocalDate.parse(r.get("effectiveDate")),
                    new BigDecimal(r.get("rate")), null, ExchangeRateDO.SOURCE_IMPORT, true);
        }
        return rows.size();
    }

    // ==================== CurrencyApi ====================

    private Map<String, CurrencyDO> all() {
        return caches.get(SystemCaches.CURRENCY, "all", () -> {
            Map<String, CurrencyDO> m = new LinkedHashMap<>();
            currencyMapper.selectList(null).forEach(c -> m.put(c.getCode(), c));
            return m;
        });
    }

    private void changed() {
        caches.clear(SystemCaches.CURRENCY);
    }

    @Override
    public String getBaseCurrency() {
        return all().values().stream().filter(c -> Boolean.TRUE.equals(c.getBase())).map(CurrencyDO::getCode).findFirst()
                .orElseThrow(() -> BizException.of(SystemErrorCodes.CURRENCY_NOT_EXISTS, "本位币"));
    }

    @Override
    public Optional<CurrencyDTO> get(String code) {
        CurrencyDO c = code == null ? null : all().get(code);
        return Optional.ofNullable(c).map(x -> new CurrencyDTO(x.getCode(), x.getName(), x.getNameEn(), x.getSymbol(),
                x.getAmountPrecision(), Boolean.TRUE.equals(x.getBase()), x.getStatus() == EnableStatus.ENABLED));
    }

    @Override
    public CurrencyDTO validate(String code) {
        return get(code).filter(CurrencyDTO::enabled).orElseThrow(() -> BizException.of(SystemErrorCodes.CURRENCY_NOT_EXISTS, code));
    }

    @Override
    public BigDecimal getRate(String currency, LocalDate date) {
        return getRate(currency, date, RateType.DAILY);
    }

    @Override
    public BigDecimal getRate(String currency, LocalDate date, RateType type) {
        return lookup(currency, date, type).rate();
    }

    @Override
    public int getPrecision(String currency) {
        CurrencyDO c = currency == null ? null : all().get(currency);
        return c == null ? 2 : c.getAmountPrecision();
    }

    @Override
    public BigDecimal roundAmount(BigDecimal amount, String currency) {
        return amount == null ? null : amount.setScale(getPrecision(currency), RoundingMode.HALF_UP);
    }

    @Override
    public BigDecimal toBase(BigDecimal amount, BigDecimal rate) {
        if (amount == null || rate == null) return null;
        return amount.multiply(rate).setScale(getPrecision(getBaseCurrency()), RoundingMode.HALF_UP);
    }
}
