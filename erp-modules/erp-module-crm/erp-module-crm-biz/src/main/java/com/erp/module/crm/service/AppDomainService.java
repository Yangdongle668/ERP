package com.erp.module.crm.service;

import com.erp.common.enums.EnableStatus;
import com.erp.common.exception.BizException;
import com.erp.module.crm.api.CrmErrorCodes;
import com.erp.module.crm.config.CrmModuleConfig;
import com.erp.module.crm.controller.vo.AppDomainVOs.AppDomainOption;
import com.erp.module.crm.controller.vo.AppDomainVOs.AppDomainRow;
import com.erp.module.crm.controller.vo.AppDomainVOs.AppDomainSave;
import com.erp.module.crm.dal.dataobject.AppDomainDO;
import com.erp.module.crm.dal.mapper.AppDomainMapper;
import com.erp.module.crm.dal.mapper.CustomerMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 应用领域（需求 03-01 R11，《编码规则管理制度》5.1）：领域字母即客户编码 LD-字母-流水号 中的字母，每个领域独立计流水号。
 * 已有客户的领域不能改字母、不能删除，只能停用（停用后不能再选它新建客户）。
 */
@Service
public class AppDomainService {

    private static final Pattern TAIL_NUMBER = Pattern.compile("^(.*?)(\\d+)$");

    private final AppDomainMapper mapper;
    private final CustomerMapper customerMapper;
    private final CrmSupport support;

    public AppDomainService(AppDomainMapper mapper, CustomerMapper customerMapper, CrmSupport support) {
        this.mapper = mapper;
        this.customerMapper = customerMapper;
        this.support = support;
    }

    public List<AppDomainRow> list() {
        Map<String, Long> counts = customerMapper.countByAppDomain();
        return mapper.selectSorted().stream().map(d -> new AppDomainRow(d.getId(), d.getCode(), d.getName(), d.getNameEn(), d.getSort(),
                d.getStatus().name(), d.getRemark(), counts.getOrDefault(d.getCode(), 0L),
                d.getStatus() == EnableStatus.ENABLED ? nextCode(d.getCode()) : null, d.getVersion())).toList();
    }

    public List<AppDomainOption> options() {
        return mapper.selectSorted().stream().map(d -> new AppDomainOption(d.getCode(), d.getName(), d.getNameEn(), d.getStatus().name())).toList();
    }

    /** 字母 → 名称（含停用） */
    public Map<String, String> names() {
        Map<String, String> m = new LinkedHashMap<>();
        mapper.selectSorted().forEach(d -> m.put(d.getCode(), d.getName()));
        return m;
    }

    /** 新建客户时选择的领域：必须存在且启用 */
    public AppDomainDO requireUsable(String code) {
        AppDomainDO d = mapper.selectByCode(code);
        if (d == null) throw BizException.of(CrmErrorCodes.APP_DOMAIN_NOT_EXISTS, code);
        if (d.getStatus() != EnableStatus.ENABLED) throw BizException.of(CrmErrorCodes.APP_DOMAIN_DISABLED, d.getCode() + " " + d.getName());
        return d;
    }

    /** 该领域下一个自动生成的客户编码（预览，不占用流水号；跳过已被手工 / 导入占用的编码） */
    public String nextCode(String code) {
        String next = support.peekNo(CrmModuleConfig.CUSTOMER, Map.of("domain", code));
        for (int i = 0; next != null && i < 1000 && customerMapper.existsCode(next); i++) next = increment(next);
        return next;
    }

    static String increment(String code) {
        Matcher m = TAIL_NUMBER.matcher(code);
        if (!m.matches()) return null;
        String digits = m.group(2);
        String n = String.valueOf(Long.parseLong(digits) + 1);
        return m.group(1) + (n.length() < digits.length() ? "0".repeat(digits.length() - n.length()) + n : n);
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(AppDomainSave req) {
        AppDomainDO d = new AppDomainDO();
        d.setCode(letter(req.code()));
        checkCodeUnique(d.getCode(), null);
        fill(d, req);
        d.setStatus(EnableStatus.ENABLED);
        mapper.insert(d);
        return d.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, AppDomainSave req) {
        AppDomainDO d = getOrThrow(id);
        if (req.version() != null) d.setVersion(req.version());
        String code = letter(req.code());
        if (!code.equals(d.getCode())) {
            long used = customerMapper.countByAppDomain().getOrDefault(d.getCode(), 0L);
            if (used > 0) throw BizException.of(CrmErrorCodes.APP_DOMAIN_IN_USE, used, "修改领域字母");
            checkCodeUnique(code, id);
            d.setCode(code);
        }
        fill(d, req);
        mapper.updateByIdOrFail(d);
    }

    @Transactional(rollbackFor = Exception.class)
    public void setStatus(Long id, EnableStatus status) {
        AppDomainDO d = getOrThrow(id);
        d.setStatus(status);
        mapper.updateByIdOrFail(d);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        AppDomainDO d = getOrThrow(id);
        long used = customerMapper.countByAppDomain().getOrDefault(d.getCode(), 0L);
        if (used > 0) throw BizException.of(CrmErrorCodes.APP_DOMAIN_IN_USE, used, "删除，可以停用");
        mapper.hardDelete(id);
    }

    private void fill(AppDomainDO d, AppDomainSave req) {
        d.setName(req.name().trim());
        d.setNameEn(CrmSupport.trim(req.nameEn()));
        d.setSort(req.sort() != null ? req.sort() : (d.getCode().charAt(0) - 'A' + 1) * 10);
        d.setRemark(CrmSupport.trim(req.remark()));
    }

    private void checkCodeUnique(String code, Long selfId) {
        AppDomainDO same = mapper.selectByCode(code);
        if (same != null && !same.getId().equals(selfId)) throw BizException.of(CrmErrorCodes.APP_DOMAIN_CODE_EXISTS, code, same.getName());
    }

    private static String letter(String code) {
        String c = code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
        if (!c.matches("[A-Z]")) throw new BizException(CrmErrorCodes.APP_DOMAIN_CODE_INVALID);
        return c;
    }

    private AppDomainDO getOrThrow(Long id) {
        AppDomainDO d = mapper.selectById(id);
        if (d == null) throw BizException.of(CrmErrorCodes.APP_DOMAIN_NOT_EXISTS, String.valueOf(id));
        return d;
    }
}
