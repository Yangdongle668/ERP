package com.erp.module.finance.service.setting;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.common.exception.BizException;
import com.erp.module.finance.api.FinanceErrorCodes;
import com.erp.module.finance.controller.vo.SettingVOs.AccountNode;
import com.erp.module.finance.controller.vo.SettingVOs.AccountOption;
import com.erp.module.finance.controller.vo.SettingVOs.AccountSave;
import com.erp.module.finance.controller.vo.SettingVOs.BankAccountSave;
import com.erp.module.finance.controller.vo.SettingVOs.BankAccountVO;
import com.erp.module.finance.controller.vo.SettingVOs.BankOption;
import com.erp.module.finance.controller.vo.SettingVOs.InitYearReq;
import com.erp.module.finance.controller.vo.SettingVOs.MappingEntry;
import com.erp.module.finance.controller.vo.SettingVOs.MappingSave;
import com.erp.module.finance.controller.vo.SettingVOs.MappingVO;
import com.erp.module.finance.controller.vo.SettingVOs.PeriodVO;
import com.erp.module.finance.dal.dataobject.FinAccountDO;
import com.erp.module.finance.dal.dataobject.FinAccountMappingDO;
import com.erp.module.finance.dal.dataobject.FinBankAccountDO;
import com.erp.module.finance.dal.dataobject.FinPeriodDO;
import com.erp.module.finance.dal.mapper.FinAccountMapper;
import com.erp.module.finance.dal.mapper.FinAccountMappingMapper;
import com.erp.module.finance.dal.mapper.FinBankAccountMapper;
import com.erp.module.finance.dal.mapper.FinPeriodMapper;
import com.erp.module.finance.service.FinSupport;
import com.erp.module.system.api.user.UserDTO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/** 财务基础设置（12-01）：会计科目、会计期间、银行账户、科目映射 */
@Service
public class SettingService {

    public static final String ENABLED = "ENABLED";
    public static final String DISABLED = "DISABLED";
    static final Set<String> ACCOUNT_TYPES = Set.of("ASSET", "LIABILITY", "EQUITY", "COST", "PROFIT_LOSS");
    static final Set<String> AUX_TYPES = Set.of("CUSTOMER", "SUPPLIER", "DEPT", "MATERIAL", "PROJECT");

    private final FinAccountMapper accountMapper;
    private final FinPeriodMapper periodMapper;
    private final FinBankAccountMapper bankMapper;
    private final FinAccountMappingMapper mappingMapper;
    private final ObjectMapper objectMapper;
    private final FinSupport support;
    /** 凭证等后续功能登记的“科目已使用”判断（FIN-SET-R01） */
    private final List<Predicate<String>> usageCheckers = new ArrayList<>();

    public SettingService(FinAccountMapper accountMapper, FinPeriodMapper periodMapper, FinBankAccountMapper bankMapper,
                          FinAccountMappingMapper mappingMapper, ObjectMapper objectMapper, FinSupport support) {
        this.accountMapper = accountMapper;
        this.periodMapper = periodMapper;
        this.bankMapper = bankMapper;
        this.mappingMapper = mappingMapper;
        this.objectMapper = objectMapper;
        this.support = support;
    }

    public void registerUsageChecker(Predicate<String> checker) {
        usageCheckers.add(checker);
    }

    // ==================== 会计科目 ====================

    public List<AccountNode> accountTree() {
        List<FinAccountDO> all = accountMapper.selectList(new LambdaQueryWrapper<FinAccountDO>().orderByAsc(FinAccountDO::getCode));
        Map<String, List<FinAccountDO>> byParent = new HashMap<>();
        for (FinAccountDO a : all) byParent.computeIfAbsent(a.getParentCode() == null ? "" : a.getParentCode(), k -> new ArrayList<>()).add(a);
        return children("", byParent);
    }

    private List<AccountNode> children(String parent, Map<String, List<FinAccountDO>> byParent) {
        return byParent.getOrDefault(parent, List.of()).stream().map(a -> new AccountNode(a.getId(), a.getCode(), a.getName(), a.getParentCode(),
                a.getAccountType(), a.getDirection(), aux(a.getAuxTypes()), Boolean.TRUE.equals(a.getCurrencyAccounting()), Boolean.TRUE.equals(a.getIsLeaf()),
                a.getAccountLevel() == null ? 1 : a.getAccountLevel(), a.getAccountStatus(), children(a.getCode(), byParent))).toList();
    }

    /** 凭证、映射、银行账户中的科目下拉：启用的末级科目 */
    public List<AccountOption> accountOptions() {
        List<FinAccountDO> all = accountMapper.selectList(new LambdaQueryWrapper<FinAccountDO>().orderByAsc(FinAccountDO::getCode));
        Map<String, FinAccountDO> byCode = all.stream().collect(Collectors.toMap(FinAccountDO::getCode, Function.identity()));
        return all.stream().filter(a -> Boolean.TRUE.equals(a.getIsLeaf()) && ENABLED.equals(a.getAccountStatus()))
                .map(a -> new AccountOption(a.getCode(), a.getName(), fullName(a, byCode), aux(a.getAuxTypes()))).toList();
    }

    public static String fullName(FinAccountDO a, Map<String, FinAccountDO> byCode) {
        List<String> names = new ArrayList<>();
        FinAccountDO x = a;
        while (x != null) {
            names.add(0, x.getName());
            x = x.getParentCode() == null ? null : byCode.get(x.getParentCode());
        }
        return String.join("-", names);
    }

    public static List<String> aux(String text) {
        return StringUtils.hasText(text) ? Arrays.stream(text.split(",")).map(String::trim).filter(StringUtils::hasText).toList() : List.of();
    }

    public FinAccountDO account(String code) {
        return code == null ? null : accountMapper.selectOne(new LambdaQueryWrapper<FinAccountDO>().eq(FinAccountDO::getCode, code));
    }

    /** 可用于凭证 / 映射的科目：存在、启用、末级（FIN-SET-R02） */
    public FinAccountDO requireLeaf(String code) {
        FinAccountDO a = account(code);
        if (a == null) throw BizException.of(FinanceErrorCodes.NOT_EXISTS, "科目「" + code + "」");
        if (!Boolean.TRUE.equals(a.getIsLeaf())) throw BizException.of(FinanceErrorCodes.SET_NOT_LEAF, code);
        if (!ENABLED.equals(a.getAccountStatus())) throw BizException.of(FinanceErrorCodes.SET_ACCOUNT_DISABLED, code);
        return a;
    }

    /** FIN-SET-R01：科目编码唯一；下级编码以上级编码开头；已使用的科目不能新增下级 */
    @Transactional(rollbackFor = Exception.class)
    public Long createAccount(AccountSave req) {
        String code = req.code().trim();
        if (account(code) != null) throw BizException.of(FinanceErrorCodes.CODE_DUPLICATE, code);
        FinAccountDO parent = null;
        String parentCode = FinSupport.trim(req.parentCode());
        if (parentCode != null) {
            parent = account(parentCode);
            if (parent == null) throw BizException.of(FinanceErrorCodes.NOT_EXISTS, "上级科目");
            if (!code.startsWith(parentCode) || code.length() <= parentCode.length()) throw BizException.of(FinanceErrorCodes.SET_PARENT_PREFIX, parentCode);
            if (Boolean.TRUE.equals(parent.getIsLeaf()) && used(parentCode)) throw new BizException(FinanceErrorCodes.SET_ACCOUNT_USED);
        }
        FinAccountDO a = new FinAccountDO();
        a.setCode(code);
        a.setParentCode(parentCode);
        fill(a, req, parent);
        a.setIsLeaf(true);
        a.setAccountLevel(parent == null ? 1 : (parent.getAccountLevel() == null ? 1 : parent.getAccountLevel()) + 1);
        a.setAccountStatus(ENABLED);
        accountMapper.insert(a);
        if (parent != null && Boolean.TRUE.equals(parent.getIsLeaf())) {
            parent.setIsLeaf(false);
            accountMapper.updateByIdOrFail(parent);
        }
        return a.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateAccount(Long id, AccountSave req) {
        FinAccountDO a = getAccount(id);
        fill(a, req, a.getParentCode() == null ? null : account(a.getParentCode()));
        accountMapper.updateByIdOrFail(a);
    }

    private void fill(FinAccountDO a, AccountSave req, FinAccountDO parent) {
        a.setName(req.name().trim());
        String type = StringUtils.hasText(req.accountType()) ? req.accountType() : parent != null ? parent.getAccountType() : null;
        if (type == null || !ACCOUNT_TYPES.contains(type)) throw BizException.of(FinanceErrorCodes.REASON_REQUIRED, "科目类型");
        a.setAccountType(type);
        String dir = StringUtils.hasText(req.direction()) ? req.direction() : parent != null ? parent.getDirection() : null;
        if (!"DEBIT".equals(dir) && !"CREDIT".equals(dir)) throw BizException.of(FinanceErrorCodes.REASON_REQUIRED, "余额方向");
        a.setDirection(dir);
        List<String> aux = req.auxTypes() == null ? List.of() : req.auxTypes().stream().filter(AUX_TYPES::contains).distinct().toList();
        a.setAuxTypes(aux.isEmpty() ? null : String.join(",", aux));
        a.setCurrencyAccounting(Boolean.TRUE.equals(req.currencyAccounting()));
    }

    @Transactional(rollbackFor = Exception.class)
    public void setAccountStatus(Long id, boolean enabled) {
        FinAccountDO a = getAccount(id);
        a.setAccountStatus(enabled ? ENABLED : DISABLED);
        accountMapper.updateByIdOrFail(a);
    }

    /** 有下级、已使用的科目不能删除 */
    @Transactional(rollbackFor = Exception.class)
    public void deleteAccount(Long id) {
        FinAccountDO a = getAccount(id);
        if (accountMapper.selectCount(new LambdaQueryWrapper<FinAccountDO>().eq(FinAccountDO::getParentCode, a.getCode())) > 0) {
            throw BizException.of(FinanceErrorCodes.SET_ACCOUNT_HAS_CHILDREN, a.getCode());
        }
        if (used(a.getCode())) throw new BizException(FinanceErrorCodes.SET_ACCOUNT_USED);
        accountMapper.deleteById(id);
        if (a.getParentCode() != null && accountMapper.selectCount(new LambdaQueryWrapper<FinAccountDO>().eq(FinAccountDO::getParentCode, a.getParentCode())) == 0) {
            FinAccountDO p = account(a.getParentCode());
            if (p != null) {
                p.setIsLeaf(true);
                accountMapper.updateByIdOrFail(p);
            }
        }
    }

    /** 科目已使用：银行账户、科目映射、凭证引用 */
    public boolean used(String code) {
        if (bankMapper.selectCount(new LambdaQueryWrapper<FinBankAccountDO>().eq(FinBankAccountDO::getAccountCode, code)) > 0) return true;
        for (FinAccountMappingDO m : mappingMapper.selectList(new LambdaQueryWrapper<FinAccountMappingDO>().like(FinAccountMappingDO::getEntries, code))) {
            if (entries(m).stream().anyMatch(e -> code.equals(e.accountCode()))) return true;
        }
        return usageCheckers.stream().anyMatch(c -> c.test(code));
    }

    private FinAccountDO getAccount(Long id) {
        FinAccountDO a = id == null ? null : accountMapper.selectById(id);
        if (a == null) throw BizException.of(FinanceErrorCodes.NOT_EXISTS, "科目");
        return a;
    }

    // ==================== 会计期间 ====================

    public List<PeriodVO> periods(Integer year) {
        List<FinPeriodDO> list = periodMapper.selectList(new LambdaQueryWrapper<FinPeriodDO>()
                .likeRight(year != null, FinPeriodDO::getPeriod, String.valueOf(year)).orderByDesc(FinPeriodDO::getPeriod));
        Map<Long, UserDTO> users = support.users(list.stream().map(FinPeriodDO::getClosedBy).toList());
        return list.stream().map(p -> new PeriodVO(p.getId(), p.getPeriod(), p.getStartDate(), p.getEndDate(), p.getPeriodStatus(),
                Boolean.TRUE.equals(p.getCostLocked()), FinSupport.name(users, p.getClosedBy()), p.getClosedAt())).toList();
    }

    /** 初始化年度：生成 12 个期间；openFrom（yyyyMM）起的期间为已开启，之前为未开启 */
    @Transactional(rollbackFor = Exception.class)
    public void initYear(InitYearReq req) {
        int year = req.year();
        String prefix = String.valueOf(year);
        if (periodMapper.selectCount(new LambdaQueryWrapper<FinPeriodDO>().likeRight(FinPeriodDO::getPeriod, prefix)) > 0) {
            throw BizException.of(FinanceErrorCodes.SET_YEAR_EXISTS, prefix);
        }
        String openFrom = StringUtils.hasText(req.openFrom()) ? req.openFrom().trim() : prefix + "01";
        for (int m = 1; m <= 12; m++) {
            YearMonth ym = YearMonth.of(year, m);
            FinPeriodDO p = new FinPeriodDO();
            p.setPeriod(ym.format(FinSupport.PERIOD));
            p.setStartDate(ym.atDay(1));
            p.setEndDate(ym.atEndOfMonth());
            p.setPeriodStatus(p.getPeriod().compareTo(openFrom) >= 0 ? "OPEN" : "NOT_OPEN");
            p.setCostLocked(false);
            periodMapper.insert(p);
        }
    }

    /** 取期间记录；不存在时按需补建（未初始化年度的业务仍可发生） */
    @Transactional(rollbackFor = Exception.class)
    public FinPeriodDO ensurePeriod(String period) {
        FinPeriodDO p = periodMapper.selectOne(new LambdaQueryWrapper<FinPeriodDO>().eq(FinPeriodDO::getPeriod, period));
        if (p != null) return p;
        YearMonth ym = YearMonth.parse(period, FinSupport.PERIOD);
        p = new FinPeriodDO();
        p.setPeriod(period);
        p.setStartDate(ym.atDay(1));
        p.setEndDate(ym.atEndOfMonth());
        p.setPeriodStatus("OPEN");
        p.setCostLocked(false);
        periodMapper.insert(p);
        return p;
    }

    // ==================== 银行账户 ====================

    public List<BankAccountVO> bankAccounts(String keyword, String currency, String status) {
        return bankMapper.selectList(new LambdaQueryWrapper<FinBankAccountDO>()
                        .and(StringUtils.hasText(keyword), w -> w.like(FinBankAccountDO::getCode, keyword).or().like(FinBankAccountDO::getName, keyword)
                                .or().like(FinBankAccountDO::getAccountNo, keyword))
                        .eq(StringUtils.hasText(currency), FinBankAccountDO::getCurrency, currency)
                        .eq(StringUtils.hasText(status), FinBankAccountDO::getBankStatus, status)
                        .orderByAsc(FinBankAccountDO::getCode))
                .stream().map(SettingService::bankVO).toList();
    }

    public List<BankOption> bankOptions(String currency) {
        return bankMapper.selectList(new LambdaQueryWrapper<FinBankAccountDO>().eq(FinBankAccountDO::getBankStatus, ENABLED)
                        .eq(StringUtils.hasText(currency), FinBankAccountDO::getCurrency, currency).orderByAsc(FinBankAccountDO::getCode))
                .stream().map(b -> new BankOption(b.getId(), b.getCode(), b.getName(), b.getCurrency(), b.getAccountNo(), Boolean.TRUE.equals(b.getIsDefault())))
                .toList();
    }

    static BankAccountVO bankVO(FinBankAccountDO b) {
        return new BankAccountVO(b.getId(), b.getCode(), b.getName(), b.getBankName(), b.getAccountNo(), b.getCurrency(), b.getSwift(),
                b.getBankAddress(), b.getAccountCode(), Boolean.TRUE.equals(b.getIsDefault()), b.getBankStatus(), b.getRemark());
    }

    public FinBankAccountDO bank(Long id) {
        FinBankAccountDO b = id == null ? null : bankMapper.selectById(id);
        if (b == null) throw BizException.of(FinanceErrorCodes.NOT_EXISTS, "银行账户");
        return b;
    }

    public Map<Long, FinBankAccountDO> banks(java.util.Collection<Long> ids) {
        List<Long> list = ids.stream().filter(Objects::nonNull).distinct().toList();
        return list.isEmpty() ? new HashMap<>() : bankMapper.selectBatchIds(list).stream().collect(Collectors.toMap(FinBankAccountDO::getId, Function.identity()));
    }

    @Transactional(rollbackFor = Exception.class)
    public Long createBank(BankAccountSave req) {
        String code = req.code().trim();
        if (bankMapper.selectCount(new LambdaQueryWrapper<FinBankAccountDO>().eq(FinBankAccountDO::getCode, code)) > 0) {
            throw BizException.of(FinanceErrorCodes.CODE_DUPLICATE, code);
        }
        FinBankAccountDO b = new FinBankAccountDO();
        b.setCode(code);
        fillBank(b, req);
        bankMapper.insert(b);
        defaultBank(b);
        return b.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateBank(Long id, BankAccountSave req) {
        FinBankAccountDO b = bank(id);
        String code = req.code().trim();
        if (!code.equals(b.getCode()) && bankMapper.selectCount(new LambdaQueryWrapper<FinBankAccountDO>().eq(FinBankAccountDO::getCode, code)) > 0) {
            throw BizException.of(FinanceErrorCodes.CODE_DUPLICATE, code);
        }
        b.setCode(code);
        fillBank(b, req);
        bankMapper.updateByIdOrFail(b);
        defaultBank(b);
    }

    private void fillBank(FinBankAccountDO b, BankAccountSave req) {
        support.currencyApi().validate(req.currency());
        String accountCode = FinSupport.trim(req.accountCode());
        if (accountCode != null) requireLeaf(accountCode);
        b.setName(req.name().trim());
        b.setBankName(req.bankName().trim());
        b.setAccountNo(req.accountNo().trim());
        b.setCurrency(req.currency());
        b.setSwift(FinSupport.trim(req.swift()));
        b.setBankAddress(FinSupport.trim(req.bankAddress()));
        b.setAccountCode(accountCode);
        b.setIsDefault(Boolean.TRUE.equals(req.isDefault()));
        b.setBankStatus(DISABLED.equals(req.status()) ? DISABLED : ENABLED);
        b.setRemark(FinSupport.trim(req.remark()));
    }

    /** 同币别只能有一个默认账户 */
    private void defaultBank(FinBankAccountDO b) {
        if (!Boolean.TRUE.equals(b.getIsDefault())) return;
        for (FinBankAccountDO o : bankMapper.selectList(new LambdaQueryWrapper<FinBankAccountDO>().eq(FinBankAccountDO::getCurrency, b.getCurrency())
                .eq(FinBankAccountDO::getIsDefault, true).ne(FinBankAccountDO::getId, b.getId()))) {
            o.setIsDefault(false);
            bankMapper.updateByIdOrFail(o);
        }
    }

    /** 已被收付款引用的账户只能停用 */
    @Transactional(rollbackFor = Exception.class)
    public void deleteBank(Long id, Predicate<Long> referenced) {
        FinBankAccountDO b = bank(id);
        if (referenced.test(id)) {
            b.setBankStatus(DISABLED);
            bankMapper.updateByIdOrFail(b);
            return;
        }
        bankMapper.deleteById(id);
    }

    // ==================== 科目映射 ====================

    public List<MappingVO> mappings(String bizType) {
        return mappingMapper.selectList(new LambdaQueryWrapper<FinAccountMappingDO>().eq(StringUtils.hasText(bizType), FinAccountMappingDO::getBizType, bizType)
                        .orderByAsc(FinAccountMappingDO::getBizType).orderByDesc(FinAccountMappingDO::getPriority).orderByAsc(FinAccountMappingDO::getId))
                .stream().map(this::mappingVO).toList();
    }

    public MappingVO mappingVO(FinAccountMappingDO m) {
        return new MappingVO(m.getId(), m.getBizType(), m.getMatchCondition(), m.getConditionDesc(), m.getPriority() == null ? 0 : m.getPriority(),
                entries(m), m.getMappingStatus(), m.getRemark());
    }

    public List<MappingEntry> entries(FinAccountMappingDO m) {
        if (!StringUtils.hasText(m.getEntries())) return List.of();
        try {
            return objectMapper.readValue(m.getEntries(), new TypeReference<List<MappingEntry>>() {
            });
        } catch (Exception e) {
            return List.of();
        }
    }

    /** 业务类型的启用映射（按优先级从高到低） */
    public List<FinAccountMappingDO> activeMappings(String bizType) {
        return mappingMapper.selectList(new LambdaQueryWrapper<FinAccountMappingDO>().eq(FinAccountMappingDO::getBizType, bizType)
                .eq(FinAccountMappingDO::getMappingStatus, ENABLED).orderByDesc(FinAccountMappingDO::getPriority).orderByAsc(FinAccountMappingDO::getId));
    }

    @Transactional(rollbackFor = Exception.class)
    public Long createMapping(MappingSave req) {
        FinAccountMappingDO m = new FinAccountMappingDO();
        fillMapping(m, req);
        mappingMapper.insert(m);
        return m.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateMapping(Long id, MappingSave req) {
        FinAccountMappingDO m = mapping(id);
        fillMapping(m, req);
        mappingMapper.updateByIdOrFail(m);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteMapping(Long id) {
        mappingMapper.deleteById(mapping(id).getId());
    }

    public FinAccountMappingDO mapping(Long id) {
        FinAccountMappingDO m = id == null ? null : mappingMapper.selectById(id);
        if (m == null) throw BizException.of(FinanceErrorCodes.NOT_EXISTS, "科目映射");
        return m;
    }

    /** FIN-SET-R02 引用启用的末级科目；FIN-SET-R03 同一业务类型只能有一条无条件的默认映射 */
    private void fillMapping(FinAccountMappingDO m, MappingSave req) {
        List<MappingEntry> entries = req.entries() == null ? List.of() : req.entries().stream()
                .filter(e -> e != null && StringUtils.hasText(e.accountCode())).toList();
        if (entries.isEmpty()) throw new BizException(FinanceErrorCodes.NO_LINES);
        List<MappingEntry> clean = new ArrayList<>();
        for (MappingEntry e : entries) {
            requireLeaf(e.accountCode().trim());
            String dir = "CREDIT".equals(e.direction()) ? "CREDIT" : "DEBIT";
            clean.add(new MappingEntry(dir, e.accountCode().trim(), StringUtils.hasText(e.amountField()) ? e.amountField() : "totalAmount",
                    FinSupport.trim(e.summaryTemplate()), FinSupport.trim(e.auxFrom())));
        }
        String condition = FinSupport.trim(req.matchCondition());
        String status = DISABLED.equals(req.status()) ? DISABLED : ENABLED;
        if (condition == null && ENABLED.equals(status)) {
            boolean exists = mappingMapper.selectList(new LambdaQueryWrapper<FinAccountMappingDO>().eq(FinAccountMappingDO::getBizType, req.bizType())
                            .eq(FinAccountMappingDO::getMappingStatus, ENABLED))
                    .stream().anyMatch(o -> !Objects.equals(o.getId(), m.getId()) && !StringUtils.hasText(o.getMatchCondition()));
            if (exists) throw BizException.of(FinanceErrorCodes.SET_DEFAULT_MAPPING_EXISTS, req.bizType());
        }
        m.setBizType(req.bizType());
        m.setMatchCondition(condition);
        m.setConditionDesc(FinSupport.trim(req.conditionDesc()));
        m.setPriority(req.priority() == null ? 0 : req.priority());
        try {
            m.setEntries(objectMapper.writeValueAsString(clean));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        m.setMappingStatus(status);
        m.setRemark(FinSupport.trim(req.remark()));
    }

    /** 条件 JSON 解析为键值（如 {"currency":"USD","foreign":"true"}） */
    public Map<String, String> condition(FinAccountMappingDO m) {
        if (!StringUtils.hasText(m.getMatchCondition())) return Map.of();
        try {
            Map<String, Object> raw = objectMapper.readValue(m.getMatchCondition(), new TypeReference<LinkedHashMap<String, Object>>() {
            });
            Map<String, String> map = new LinkedHashMap<>();
            raw.forEach((k, v) -> map.put(k, v == null ? null : String.valueOf(v)));
            return map;
        } catch (Exception e) {
            return Map.of("_invalid", "true");
        }
    }
}
