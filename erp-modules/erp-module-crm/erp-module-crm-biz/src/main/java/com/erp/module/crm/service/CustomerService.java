package com.erp.module.crm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.common.exception.BizException;
import com.erp.common.exception.GlobalErrorCodes;
import com.erp.common.result.PageResult;
import com.erp.common.statemachine.StateMachine;
import com.erp.framework.datascope.DataScopes;
import com.erp.framework.event.DomainEventPublisher;
import com.erp.module.crm.api.CrmErrorCodes;
import com.erp.module.crm.api.customer.AddressDTO;
import com.erp.module.crm.api.customer.ContactDTO;
import com.erp.module.crm.api.customer.CustomerApi;
import com.erp.module.crm.api.customer.CustomerDTO;
import com.erp.module.crm.api.customer.CustomerOwnerChangedEvent;
import com.erp.module.crm.api.customer.CustomerReferenceChecker;
import com.erp.module.crm.api.customer.CustomerStatus;
import com.erp.module.crm.api.customer.CustomerStatusChangedEvent;
import com.erp.module.crm.config.CrmModuleConfig;
import com.erp.module.crm.controller.vo.CustomerVOs.AddressResp;
import com.erp.module.crm.controller.vo.CustomerVOs.AddressSave;
import com.erp.module.crm.controller.vo.CustomerVOs.BankResp;
import com.erp.module.crm.controller.vo.CustomerVOs.BankSave;
import com.erp.module.crm.controller.vo.CustomerVOs.ContactQuery;
import com.erp.module.crm.controller.vo.CustomerVOs.ContactResp;
import com.erp.module.crm.controller.vo.CustomerVOs.ContactRow;
import com.erp.module.crm.controller.vo.CustomerVOs.ContactSave;
import com.erp.module.crm.controller.vo.CustomerVOs.CreditSummary;
import com.erp.module.crm.controller.vo.CustomerVOs.CustomerBrief;
import com.erp.module.crm.controller.vo.CustomerVOs.CustomerDetail;
import com.erp.module.crm.controller.vo.CustomerVOs.CustomerQuery;
import com.erp.module.crm.controller.vo.CustomerVOs.CustomerRow;
import com.erp.module.crm.controller.vo.CustomerVOs.CustomerSave;
import com.erp.module.crm.controller.vo.CustomerVOs.DuplicateCheckReq;
import com.erp.module.crm.controller.vo.CustomerVOs.DuplicateRow;
import com.erp.module.crm.controller.vo.CustomerVOs.SaveResult;
import com.erp.module.crm.controller.vo.CustomerVOs.TransferLogRow;
import com.erp.module.crm.controller.vo.CustomerVOs.TransferReq;
import com.erp.module.crm.dal.dataobject.CustomerAddressDO;
import com.erp.module.crm.dal.dataobject.CustomerBankDO;
import com.erp.module.crm.dal.dataobject.CustomerContactDO;
import com.erp.module.crm.dal.dataobject.CustomerCreditDO;
import com.erp.module.crm.dal.dataobject.CustomerDO;
import com.erp.module.crm.dal.dataobject.CustomerPartDO;
import com.erp.module.crm.dal.dataobject.CustomerTransferLogDO;
import com.erp.module.crm.dal.dataobject.FollowupDO;
import com.erp.module.crm.dal.dataobject.OpportunityDO;
import com.erp.module.crm.dal.mapper.CustomerAddressMapper;
import com.erp.module.crm.dal.mapper.CustomerBankMapper;
import com.erp.module.crm.dal.mapper.CustomerContactMapper;
import com.erp.module.crm.dal.mapper.CustomerCreditMapper;
import com.erp.module.crm.dal.mapper.CustomerMapper;
import com.erp.module.crm.dal.mapper.CustomerPartMapper;
import com.erp.module.crm.dal.mapper.CustomerTransferLogMapper;
import com.erp.module.crm.dal.mapper.FollowupMapper;
import com.erp.module.crm.dal.mapper.OpportunityMapper;
import com.erp.module.system.api.currency.CurrencyApi;
import com.erp.module.system.api.file.FileApi;
import com.erp.module.system.api.org.OrgDTO;
import com.erp.module.system.api.paymentterm.PaymentTermApi;
import com.erp.module.system.api.paymentterm.PaymentTermDTO;
import com.erp.module.system.api.user.UserDTO;
import com.erp.module.system.api.workflow.ApprovalCompletedEvent;
import com.erp.module.system.api.workflow.StartResult;
import com.erp.module.system.api.workflow.WorkflowApi;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.erp.module.crm.api.customer.CustomerStatus.ACTIVE;
import static com.erp.module.crm.api.customer.CustomerStatus.BLACKLIST;
import static com.erp.module.crm.api.customer.CustomerStatus.DISABLED;
import static com.erp.module.crm.api.customer.CustomerStatus.PENDING;
import static com.erp.module.crm.api.customer.CustomerStatus.PROSPECT;

/**
 * 客户（需求 03-01）：档案、联系人、地址、银行、查重、转正式（审批 CRM_CUSTOMER_ACTIVATE）、停用、黑名单、转移、删除。
 * 列表受数据权限约束（负责部门 / 负责人）；{@link CustomerApi} 的查询不受数据权限限制。
 */
@Service("crmCustomerService")
public class CustomerService implements CustomerApi {

    public static final String BIZ_TYPE = CrmModuleConfig.CUSTOMER;
    /** 本国：国家不同于本国的客户默认为外销客户 */
    static final String HOME_COUNTRY = "CN";
    static final Set<String> GENDERS = Set.of("MALE", "FEMALE", "UNKNOWN");
    static final Set<String> ADDRESS_TYPES = Set.of("SHIP_TO", "BILL_TO", "NOTIFY");
    static final Set<String> CREDIT_CONTROLS = Set.of("DEFAULT", "NONE", "WARN", "BLOCK");
    static final Map<CustomerStatus, String> STATUS_LABELS = Map.of(PROSPECT, "潜在", PENDING, "审批中", ACTIVE, "正式", DISABLED, "停用",
            BLACKLIST, "黑名单");
    /** 查重时忽略的公司后缀（已去掉空格和标点、转小写） */
    static final List<String> SUFFIXES = List.of("股份有限公司", "有限责任公司", "有限公司", "集团", "公司", "coltd", "incorporated", "corporation", "company",
            "limited", "pteltd", "gmbh", "ltd", "inc", "llc", "corp", "plc", "srl", "pte", "co", "sa", "bv", "ag");

    public enum Op implements StateMachine.Labeled {
        SUBMIT("提交转正式"), APPROVE("转正式"), BACK("驳回/撤回"), DISABLE("停用"), ENABLE("启用"), BLACKLIST("加入黑名单"),
        UNBLACKLIST_ACTIVE("移出黑名单"), UNBLACKLIST_PROSPECT("移出黑名单");

        private final String label;

        Op(String label) {
            this.label = label;
        }

        @Override
        public String label() {
            return label;
        }
    }

    /** 第 4 节状态流转 */
    static final StateMachine<CustomerStatus, Op> MACHINE = StateMachine.builder(CustomerStatus.class, Op.class)
            .transition(PROSPECT, Op.SUBMIT, PENDING)
            .transition(PROSPECT, Op.APPROVE, ACTIVE)
            .transition(PENDING, Op.APPROVE, ACTIVE)
            .transition(PENDING, Op.BACK, PROSPECT)
            .transition(ACTIVE, Op.DISABLE, DISABLED)
            .transition(DISABLED, Op.ENABLE, ACTIVE)
            .transition(PROSPECT, Op.BLACKLIST, BLACKLIST)
            .transition(ACTIVE, Op.BLACKLIST, BLACKLIST)
            .transition(DISABLED, Op.BLACKLIST, BLACKLIST)
            .transition(BLACKLIST, Op.UNBLACKLIST_ACTIVE, ACTIVE)
            .transition(BLACKLIST, Op.UNBLACKLIST_PROSPECT, PROSPECT)
            .build();

    private final CustomerMapper mapper;
    private final CustomerContactMapper contactMapper;
    private final CustomerAddressMapper addressMapper;
    private final CustomerBankMapper bankMapper;
    private final CustomerTransferLogMapper transferLogMapper;
    private final CustomerCreditMapper creditMapper;
    private final CustomerPartMapper partMapper;
    private final FollowupMapper followupMapper;
    private final OpportunityMapper opportunityMapper;
    private final CrmSupport support;
    private final CurrencyApi currencyApi;
    private final PaymentTermApi paymentTermApi;
    private final WorkflowApi workflowApi;
    private final FileApi fileApi;
    private final DomainEventPublisher eventPublisher;
    private final List<CustomerReferenceChecker> referenceCheckers;

    public CustomerService(CustomerMapper mapper, CustomerContactMapper contactMapper, CustomerAddressMapper addressMapper, CustomerBankMapper bankMapper,
                           CustomerTransferLogMapper transferLogMapper, CustomerCreditMapper creditMapper, CustomerPartMapper partMapper,
                           FollowupMapper followupMapper, OpportunityMapper opportunityMapper, CrmSupport support, CurrencyApi currencyApi,
                           PaymentTermApi paymentTermApi, WorkflowApi workflowApi, FileApi fileApi, DomainEventPublisher eventPublisher,
                           List<CustomerReferenceChecker> referenceCheckers) {
        this.mapper = mapper;
        this.contactMapper = contactMapper;
        this.addressMapper = addressMapper;
        this.bankMapper = bankMapper;
        this.transferLogMapper = transferLogMapper;
        this.creditMapper = creditMapper;
        this.partMapper = partMapper;
        this.followupMapper = followupMapper;
        this.opportunityMapper = opportunityMapper;
        this.support = support;
        this.currencyApi = currencyApi;
        this.paymentTermApi = paymentTermApi;
        this.workflowApi = workflowApi;
        this.fileApi = fileApi;
        this.eventPublisher = eventPublisher;
        this.referenceCheckers = referenceCheckers;
    }

    // ==================== 查询 ====================

    public PageResult<CustomerRow> page(CustomerQuery q) {
        IPage<CustomerDO> page = mapper.selectScopedPage(Page.of(q.getPageNo(), q.getPageSize()), query(q));
        return new PageResult<>(rows(page.getRecords()), page.getTotal());
    }

    public List<CustomerRow> listForExport(CustomerQuery q, int limit) {
        return rows(mapper.selectScopedList(query(q).last("LIMIT " + limit)));
    }

    private LambdaQueryWrapper<CustomerDO> query(CustomerQuery q) {
        LambdaQueryWrapper<CustomerDO> w = new LambdaQueryWrapper<CustomerDO>().eq(CustomerDO::getDeleted, false)
                .eq(q.getOwnerId() != null, CustomerDO::getOwnerId, q.getOwnerId())
                .eq(StringUtils.hasText(q.getCustomerType()), CustomerDO::getCustomerType, q.getCustomerType())
                .eq(StringUtils.hasText(q.getSource()), CustomerDO::getSource, q.getSource())
                .ge(q.getLastOrderFrom() != null, CustomerDO::getLastOrderDate, q.getLastOrderFrom())
                .le(q.getLastOrderTo() != null, CustomerDO::getLastOrderDate, q.getLastOrderTo());
        if (StringUtils.hasText(q.getKeyword())) {
            String k = q.getKeyword().trim();
            w.and(x -> x.likeRight(CustomerDO::getCode, k.toUpperCase()).or().like(CustomerDO::getName, k).or().like(CustomerDO::getNameEn, k)
                    .or().like(CustomerDO::getShortName, k));
        }
        List<CustomerStatus> statuses = StringUtils.hasText(q.getStatuses())
                ? Arrays.stream(q.getStatuses().split(",")).map(String::trim).map(CustomerStatus::valueOf).toList()
                : List.of(PROSPECT, PENDING, ACTIVE);
        w.in(CustomerDO::getCustomerStatus, statuses);
        if (StringUtils.hasText(q.getLevels())) w.in(CustomerDO::getCustomerLevel, csv(q.getLevels()));
        if (StringUtils.hasText(q.getCountries())) w.in(CustomerDO::getCountry, csv(q.getCountries()).stream().map(s -> s.toUpperCase()).toList());
        if (q.getNoOrderDays() != null && q.getNoOrderDays() > 0) {
            LocalDate before = LocalDate.now().minusDays(q.getNoOrderDays());
            w.and(x -> x.isNull(CustomerDO::getLastOrderDate).or().lt(CustomerDO::getLastOrderDate, before));
        }
        return w.orderByAsc(CustomerDO::getCode);
    }

    private static List<String> csv(String s) {
        return Arrays.stream(s.split(",")).map(String::trim).filter(StringUtils::hasText).toList();
    }

    private List<CustomerRow> rows(List<CustomerDO> list) {
        if (list.isEmpty()) return List.of();
        Map<Long, CustomerContactDO> primary = contactMapper.selectByParents(list.stream().map(CustomerDO::getId).toList()).stream()
                .filter(c -> Boolean.TRUE.equals(c.getIsPrimary())).collect(Collectors.toMap(CustomerContactDO::getCustomerId, c -> c, (a, b) -> a));
        Map<Long, UserDTO> users = support.users(list.stream().map(CustomerDO::getOwnerId).toList());
        boolean credit = CrmSupport.canViewCredit();
        return list.stream().map(c -> {
            CustomerContactDO p = primary.get(c.getId());
            return new CustomerRow(c.getId(), c.getCode(), c.getShortName(), c.getName(), c.getNameEn(), c.getCountry(), c.getCustomerType(),
                    c.getCustomerLevel(), c.getOwnerId(), CrmSupport.name(users, c.getOwnerId()), p == null ? null : p.getName(),
                    p == null ? null : p.getEmail(), c.getCurrency(), credit ? c.getCreditLimit() : null, credit, c.getLastOrderDate(),
                    c.getCustomerStatus().name(), c.getCreatedAt(), c.getVersion());
        }).toList();
    }

    public CustomerDetail detail(Long id) {
        CustomerDO c = getVisible(id);
        Map<Long, UserDTO> users = support.users(Stream.of(c.getOwnerId(), c.getCreatedBy()).filter(Objects::nonNull).toList());
        String deptName = c.getDeptId() == null ? null : Optional.ofNullable(support.orgs(List.of(c.getDeptId())).get(c.getDeptId())).map(OrgDTO::name).orElse(null);
        String termName = c.getPaymentTermId() == null ? null : paymentTermApi.get(c.getPaymentTermId()).map(PaymentTermDTO::name).orElse(null);
        return new CustomerDetail(c.getId(), c.getCode(), c.getName(), c.getNameEn(), c.getShortName(), c.getCustomerType(), c.getCustomerLevel(),
                c.getCustomerStatus().name(), Boolean.TRUE.equals(c.getIsForeign()), c.getCountry(), c.getProvince(), c.getCity(), c.getAddress(),
                c.getIndustry(), c.getSource(), c.getWebsite(), c.getPhone(), c.getEmail(), c.getTaxNo(), c.getOwnerId(),
                CrmSupport.name(users, c.getOwnerId()), c.getDeptId(), deptName, c.getCurrency(), c.getPaymentTermId(), termName, c.getTradeTerm(),
                c.getSalesTaxRate(), c.getBlacklistReason(), c.getFirstOrderDate(), c.getLastOrderDate(), c.getRemark(),
                contactMapper.selectByParent(id).stream().map(CustomerService::contactResp).toList(),
                addressMapper.selectByParent(id).stream().map(CustomerService::addressResp).toList(),
                bankMapper.selectByParent(id).stream().map(b -> new BankResp(b.getId(), b.getBankName(), b.getAccountName(), b.getAccountNo(),
                        b.getSwift(), b.getCurrency(), b.getRemark())).toList(),
                CrmSupport.canViewCredit() ? creditSummary(c) : null,
                CrmSupport.name(users, c.getCreatedBy()), c.getCreatedAt(), c.getUpdatedAt(), c.getVersion());
    }

    static ContactResp contactResp(CustomerContactDO x) {
        return new ContactResp(x.getId(), x.getName(), x.getGender(), x.getTitle(), x.getContactRole(), x.getEmail(), x.getPhone(), x.getMobile(),
                x.getIm(), x.getBirthday(), Boolean.TRUE.equals(x.getIsPrimary()), x.getContactStatus(), x.getRemark());
    }

    static AddressResp addressResp(CustomerAddressDO a) {
        return new AddressResp(a.getId(), a.getAddressType(), a.getCompanyName(), a.getContactName(), a.getPhone(), a.getCountry(), a.getProvince(),
                a.getCity(), a.getZip(), a.getAddressLine(), Boolean.TRUE.equals(a.getIsDefault()), a.getRemark());
    }

    /** 已用 = 应收余额 + 未出货订单；使用率 = 已用 ÷ 额度（未设置额度时为空） */
    CreditSummary creditSummary(CustomerDO c) {
        CustomerCreditDO cr = creditMapper.selectOne(new LambdaQueryWrapper<CustomerCreditDO>().eq(CustomerCreditDO::getCustomerId, c.getId()));
        BigDecimal receivable = cr == null ? BigDecimal.ZERO : cr.getReceivableBalance();
        BigDecimal overdue = cr == null ? BigDecimal.ZERO : cr.getOverdueAmount();
        BigDecimal open = cr == null ? BigDecimal.ZERO : cr.getOpenOrderAmount();
        BigDecimal used = receivable.add(open);
        BigDecimal limit = c.getCreditLimit();
        BigDecimal available = limit == null ? null : limit.subtract(used);
        BigDecimal pct = limit == null || limit.signum() == 0 ? null : used.divide(limit, 4, RoundingMode.HALF_UP);
        return new CreditSummary(limit, c.getCreditDays(), c.getCreditControl(), receivable, overdue, open, used, available, pct);
    }

    /** 选择器：按数据权限；ids 用于回显（不受数据权限限制） */
    public List<CustomerBrief> search(String keyword, String statuses, String ids, int limit) {
        List<CustomerDO> list;
        if (StringUtils.hasText(ids)) {
            List<Long> idList = Arrays.stream(ids.split(",")).map(String::trim).filter(x -> x.matches("\\d+")).map(Long::valueOf).toList();
            list = idList.isEmpty() ? List.of() : mapper.selectBatchIds(idList);
        } else {
            LambdaQueryWrapper<CustomerDO> w = new LambdaQueryWrapper<CustomerDO>().eq(CustomerDO::getDeleted, false);
            if (StringUtils.hasText(keyword)) {
                String k = keyword.trim();
                w.and(x -> x.likeRight(CustomerDO::getCode, k.toUpperCase()).or().like(CustomerDO::getName, k).or().like(CustomerDO::getShortName, k)
                        .or().like(CustomerDO::getNameEn, k));
            }
            List<CustomerStatus> st = StringUtils.hasText(statuses) ? csv(statuses).stream().map(CustomerStatus::valueOf).toList()
                    : List.of(PROSPECT, PENDING, ACTIVE);
            w.in(CustomerDO::getCustomerStatus, st).orderByAsc(CustomerDO::getCode).last("LIMIT " + Math.max(1, Math.min(limit, 50)));
            list = mapper.selectScopedList(w);
        }
        return list.stream().map(c -> new CustomerBrief(c.getId(), c.getCode(), c.getName(), c.getShortName(), c.getCustomerStatus().name(),
                c.getCurrency(), c.getPaymentTermId(), c.getTradeTerm(), c.getSalesTaxRate(), c.getOwnerId())).toList();
    }

    // ==================== 新建 / 修改 ====================

    @Transactional(rollbackFor = Exception.class)
    public SaveResult create(CustomerSave req) {
        CustomerDO c = new CustomerDO();
        String code = CrmSupport.trim(req.code());
        if (code != null && support.manualCodeAllowed(BIZ_TYPE)) c.setCode(code.toUpperCase(Locale.ROOT));
        else c.setCode(support.nextNo(BIZ_TYPE));
        c.setCustomerStatus(PROSPECT);
        c.setCreditControl("DEFAULT");
        fill(c, req, true);
        List<String> warnings = new ArrayList<>(checkUnique(c));
        mapper.insert(c);
        saveChildren(c, req);
        if (req.fileIds() != null && !req.fileIds().isEmpty()) fileApi.bind(req.fileIds(), BIZ_TYPE, c.getId());
        support.log(BIZ_TYPE, c.getId(), c.getCode(), "CREATE", "新建", null, PROSPECT.name(), null);
        return new SaveResult(c.getId(), warnings);
    }

    @Transactional(rollbackFor = Exception.class)
    public SaveResult update(Long id, CustomerSave req) {
        CustomerDO c = getVisible(id);
        if (c.getCustomerStatus() == BLACKLIST) throw BizException.of(CrmErrorCodes.CUSTOMER_STATUS, STATUS_LABELS.get(BLACKLIST), "修改");
        if (req.version() != null) c.setVersion(req.version());
        String code = CrmSupport.trim(req.code());
        if (code != null && !code.equalsIgnoreCase(c.getCode()) && support.manualCodeAllowed(BIZ_TYPE)) c.setCode(code.toUpperCase(Locale.ROOT));
        String oldName = c.getName();
        String oldNameEn = c.getNameEn();
        String oldTax = c.getTaxNo();
        fill(c, req, false);
        List<String> warnings = new ArrayList<>(checkUnique(c));
        mapper.updateByIdOrFail(c);
        saveChildren(c, req);
        if (req.fileIds() != null && !req.fileIds().isEmpty()) fileApi.bind(req.fileIds(), BIZ_TYPE, c.getId());
        // R06：正式客户修改名称、英文名称、税号时记录旧值新值
        if (c.getCustomerStatus() == ACTIVE) {
            List<String> changes = new ArrayList<>();
            if (!Objects.equals(oldName, c.getName())) changes.add("名称：" + oldName + " → " + c.getName());
            if (!Objects.equals(oldNameEn, c.getNameEn())) changes.add("英文名称：" + Objects.toString(oldNameEn, "空") + " → " + Objects.toString(c.getNameEn(), "空"));
            if (!Objects.equals(oldTax, c.getTaxNo())) changes.add("税号：" + Objects.toString(oldTax, "空") + " → " + Objects.toString(c.getTaxNo(), "空"));
            if (!changes.isEmpty()) {
                support.log(BIZ_TYPE, c.getId(), c.getCode(), "CHANGE_NAME", "修改名称/税号", ACTIVE.name(), ACTIVE.name(), String.join("；", changes));
                warnings.add("已有单据中的客户名称不会改变");
            }
        }
        return new SaveResult(id, warnings);
    }

    private void fill(CustomerDO c, CustomerSave req, boolean creating) {
        c.setName(req.name().trim());
        c.setNameEn(CrmSupport.trim(req.nameEn()));
        String shortName = CrmSupport.trim(req.shortName());
        if (shortName == null) shortName = defaultShortName(c.getName());
        c.setShortName(shortName);
        c.setNameKey(nameKey(c.getName()));
        c.setNameCore(nameCore(c.getName()));
        String type = StringUtils.hasText(req.customerType()) ? req.customerType() : (creating ? "END_USER" : c.getCustomerType());
        if (!type.equals(c.getCustomerType())) support.dict().validate("crm_customer_type", type, "客户类型");
        c.setCustomerType(type);
        String level = StringUtils.hasText(req.level()) ? req.level() : (creating ? "C" : c.getCustomerLevel());
        if (!level.equals(c.getCustomerLevel())) support.dict().validate("crm_customer_level", level, "客户等级");
        c.setCustomerLevel(level);
        String country = req.country().trim().toUpperCase(Locale.ROOT);
        if (!country.matches("[A-Z]{2}")) throw BizException.of(CrmErrorCodes.CUSTOMER_FIELD_INVALID, "国家请使用 ISO 二位代码");
        c.setCountry(country);
        boolean foreign = req.isForeign() != null ? req.isForeign() : !HOME_COUNTRY.equals(country);
        c.setIsForeign(foreign);
        // R02：外销客户必须填写英文名称
        if (foreign && c.getNameEn() == null) throw new BizException(CrmErrorCodes.CUSTOMER_NAME_EN_REQUIRED);
        c.setProvince(CrmSupport.trim(req.province()));
        c.setCity(CrmSupport.trim(req.city()));
        c.setAddress(CrmSupport.trim(req.address()));
        String industry = CrmSupport.trim(req.industry());
        if (industry != null && !industry.equals(c.getIndustry())) support.dict().validate("crm_industry", industry, "行业");
        c.setIndustry(industry);
        String source = CrmSupport.trim(req.source());
        if (source != null && !source.equals(c.getSource())) support.dict().validate("crm_source", source, "客户来源");
        c.setSource(source);
        c.setWebsite(CrmSupport.trim(req.website()));
        c.setWebsiteDomain(domain(c.getWebsite()));
        c.setPhone(CrmSupport.trim(req.phone()));
        c.setEmail(CrmSupport.trim(req.email()));
        c.setTaxNo(req.taxNo() == null ? null : CrmSupport.trim(req.taxNo().toUpperCase(Locale.ROOT)));
        // R08：没有转移权限时负责人只能是自己（新建）或保持不变（修改）
        Long me = support.currentUser();
        Long owner = req.ownerId() != null ? req.ownerId() : (creating ? me : c.getOwnerId());
        if (owner == null) throw BizException.of(GlobalErrorCodes.BAD_REQUEST, "请选择负责业务员");
        boolean ownerChanged = creating ? !Objects.equals(owner, me) : !Objects.equals(owner, c.getOwnerId());
        if (ownerChanged && !CrmSupport.hasPermission("crm:customer:transfer")) throw new BizException(CrmErrorCodes.CUSTOMER_OWNER_FORBIDDEN);
        if (creating || ownerChanged) {
            UserDTO u = support.user(owner);
            if (u == null) throw BizException.of(GlobalErrorCodes.DATA_NOT_EXISTS, "负责业务员");
            c.setOwnerId(owner);
            c.setDeptId(u.deptId());
            c.setOrgId(support.companyOf(u.deptId(), u.orgId()));
        }
        String base = currencyApi.getBaseCurrency();
        String currency = StringUtils.hasText(req.currency()) ? req.currency().trim().toUpperCase(Locale.ROOT)
                : (creating ? (foreign ? "USD" : base) : c.getCurrency());
        if (!currency.equals(c.getCurrency())) currencyApi.validate(currency);
        c.setCurrency(currency);
        if (req.paymentTermId() != null && !req.paymentTermId().equals(c.getPaymentTermId())) paymentTermApi.validate(req.paymentTermId(), "SALES");
        c.setPaymentTermId(req.paymentTermId());
        String trade = CrmSupport.trim(req.tradeTerm());
        if (trade != null && !trade.equals(c.getTradeTerm())) support.dict().validate("sys_trade_term", trade, "贸易条款");
        c.setTradeTerm(trade);
        BigDecimal rate = req.salesTaxRate() != null ? req.salesTaxRate() : (creating ? (foreign ? BigDecimal.ZERO : new BigDecimal("0.13")) : c.getSalesTaxRate());
        if (rate.signum() < 0 || rate.compareTo(BigDecimal.ONE) >= 0) throw BizException.of(CrmErrorCodes.CUSTOMER_FIELD_INVALID, "税率必须在 0～1 之间（小数，如 0.13）");
        c.setSalesTaxRate(rate);
        // 信用期、控制方式属于信用字段，没有字段权限时保持不变；额度只能通过信用调整修改
        if (CrmSupport.canViewCredit()) {
            if (req.creditDays() != null && req.creditDays() < 0) throw BizException.of(CrmErrorCodes.CUSTOMER_FIELD_INVALID, "信用期不能小于 0");
            c.setCreditDays(req.creditDays());
            String control = StringUtils.hasText(req.creditControl()) ? req.creditControl() : "DEFAULT";
            if (!CREDIT_CONTROLS.contains(control)) throw BizException.of(CrmErrorCodes.CUSTOMER_FIELD_INVALID, "信用控制方式不正确");
            c.setCreditControl(control);
        }
        c.setRemark(CrmSupport.trim(req.remark()));
    }

    /** R04（阻止）+ R01（按参数提示或阻止）；返回提示 */
    private List<String> checkUnique(CustomerDO c) {
        CustomerDO same = mapper.selectOne(new LambdaQueryWrapper<CustomerDO>().eq(CustomerDO::getNameKey, c.getNameKey())
                .eq(CustomerDO::getCountry, c.getCountry()).ne(c.getId() != null, CustomerDO::getId, c.getId()).last("LIMIT 1"));
        if (same != null) throw BizException.of(CrmErrorCodes.CUSTOMER_NAME_EXISTS, same.getName());
        CustomerDO byCode = mapper.selectOne(new LambdaQueryWrapper<CustomerDO>().eq(CustomerDO::getCode, c.getCode())
                .ne(c.getId() != null, CustomerDO::getId, c.getId()).last("LIMIT 1"));
        if (byCode != null) throw BizException.of(CrmErrorCodes.CUSTOMER_CODE_EXISTS, c.getCode());
        String mode = support.param().getString(CrmModuleConfig.P_DUPLICATE_CHECK);
        if ("OFF".equals(mode)) return List.of();
        List<DuplicateRow> dups = duplicates(c.getId(), c.getNameCore(), c.getTaxNo(), c.getWebsiteDomain());
        if (dups.isEmpty()) return List.of();
        String codes = dups.stream().map(DuplicateRow::code).distinct().collect(Collectors.joining("、"));
        if ("BLOCK".equals(mode)) throw BizException.of(CrmErrorCodes.CUSTOMER_DUPLICATE, codes);
        return List.of("发现疑似重复客户：" + codes);
    }

    /** R01 查重（失焦时前端调用；不受数据权限限制，只返回编码、名称、负责人） */
    public List<DuplicateRow> duplicateCheck(DuplicateCheckReq req) {
        String core = StringUtils.hasText(req.name()) ? nameCore(req.name()) : null;
        String tax = StringUtils.hasText(req.taxNo()) ? req.taxNo().trim().toUpperCase(Locale.ROOT) : null;
        return duplicates(req.id(), core, tax, domain(req.website()));
    }

    private List<DuplicateRow> duplicates(Long selfId, String core, String taxNo, String domain) {
        if (!StringUtils.hasText(core) && taxNo == null && domain == null) return List.of();
        LambdaQueryWrapper<CustomerDO> w = new LambdaQueryWrapper<CustomerDO>().ne(selfId != null, CustomerDO::getId, selfId);
        w.and(x -> {
            x.eq(StringUtils.hasText(core), CustomerDO::getNameCore, core);
            if (taxNo != null) x.or().eq(CustomerDO::getTaxNo, taxNo);
            if (domain != null) x.or().eq(CustomerDO::getWebsiteDomain, domain);
        });
        List<CustomerDO> list = mapper.selectList(w.last("LIMIT 20"));
        Map<Long, UserDTO> users = support.users(list.stream().map(CustomerDO::getOwnerId).toList());
        return list.stream().map(c -> new DuplicateRow(c.getId(), c.getCode(), c.getName(), c.getCountry(), CrmSupport.name(users, c.getOwnerId()),
                Objects.equals(c.getNameCore(), core) ? "NAME" : taxNo != null && taxNo.equals(c.getTaxNo()) ? "TAX_NO" : "WEBSITE")).toList();
    }

    /** 默认简称：中文取前 10 个字；英文等在 20 个字符内按单词截断 */
    static String defaultShortName(String name) {
        if (name.codePoints().anyMatch(cp -> Character.UnicodeScript.of(cp) == Character.UnicodeScript.HAN)) {
            return name.length() > 10 ? name.substring(0, 10) : name;
        }
        if (name.length() <= 20) return name;
        int space = name.lastIndexOf(' ', 20);
        return (space > 0 ? name.substring(0, space) : name.substring(0, 20)).trim();
    }

    /** 名称比较键：小写，去掉空格和标点（R04） */
    static String nameKey(String name) {
        return name == null ? "" : name.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
    }

    /** 查重键：在名称比较键基础上去掉公司后缀（R01） */
    static String nameCore(String name) {
        String k = nameKey(name);
        boolean changed = true;
        while (changed) {
            changed = false;
            for (String s : SUFFIXES) {
                if (k.endsWith(s) && k.length() - s.length() >= 2) {
                    k = k.substring(0, k.length() - s.length());
                    changed = true;
                    break;
                }
            }
        }
        return k;
    }

    static String domain(String website) {
        if (!StringUtils.hasText(website)) return null;
        String d = website.trim().toLowerCase(Locale.ROOT).replaceFirst("^[a-z]+://", "");
        int slash = d.indexOf('/');
        if (slash >= 0) d = d.substring(0, slash);
        int colon = d.indexOf(':');
        if (colon >= 0) d = d.substring(0, colon);
        if (d.startsWith("www.")) d = d.substring(4);
        return d.isEmpty() ? null : d;
    }

    private void saveChildren(CustomerDO c, CustomerSave req) {
        if (req.contacts() != null) saveContacts(c.getId(), req.contacts());
        if (req.addresses() != null) saveAddresses(c, req.addresses());
        if (req.banks() != null) saveBanks(c.getId(), req.banks());
    }

    /** R05：至少一种联系方式；主联系人最多一个 */
    private void saveContacts(Long customerId, List<ContactSave> list) {
        if (list.stream().filter(x -> Boolean.TRUE.equals(x.isPrimary())).count() > 1) {
            throw BizException.of(CrmErrorCodes.CUSTOMER_FIELD_INVALID, "主联系人只能有一个");
        }
        contactMapper.deleteByParent(customerId);
        for (ContactSave x : list) {
            if (!StringUtils.hasText(x.email()) && !StringUtils.hasText(x.phone()) && !StringUtils.hasText(x.mobile())) {
                throw BizException.of(CrmErrorCodes.CONTACT_WAY_REQUIRED, x.name().trim());
            }
            String role = CrmSupport.trim(x.role());
            if (role != null) support.dict().validate("crm_contact_role", role, "联系人角色");
            CustomerContactDO d = new CustomerContactDO();
            d.setCustomerId(customerId);
            d.setName(x.name().trim());
            d.setGender(x.gender() != null && GENDERS.contains(x.gender()) ? x.gender() : "UNKNOWN");
            d.setTitle(CrmSupport.trim(x.title()));
            d.setContactRole(role);
            d.setEmail(CrmSupport.trim(x.email()));
            d.setPhone(CrmSupport.trim(x.phone()));
            d.setMobile(CrmSupport.trim(x.mobile()));
            d.setIm(CrmSupport.trim(x.im()));
            d.setBirthday(x.birthday());
            d.setIsPrimary(Boolean.TRUE.equals(x.isPrimary()));
            d.setContactStatus("LEFT".equals(x.status()) ? "LEFT" : "ACTIVE");
            d.setRemark(CrmSupport.trim(x.remark()));
            contactMapper.insert(d);
        }
    }

    /** R05：每类地址默认最多一个 */
    private void saveAddresses(CustomerDO c, List<AddressSave> list) {
        Set<String> defaults = new HashSet<>();
        for (AddressSave a : list) {
            if (!ADDRESS_TYPES.contains(a.addressType())) throw BizException.of(CrmErrorCodes.CUSTOMER_FIELD_INVALID, "地址类型不正确");
            if (Boolean.TRUE.equals(a.isDefault()) && !defaults.add(a.addressType())) {
                throw BizException.of(CrmErrorCodes.CUSTOMER_FIELD_INVALID, "每类地址只能有一个默认地址");
            }
        }
        addressMapper.deleteByParent(c.getId());
        for (AddressSave a : list) {
            CustomerAddressDO d = new CustomerAddressDO();
            d.setCustomerId(c.getId());
            d.setAddressType(a.addressType());
            d.setCompanyName(a.companyName().trim());
            d.setContactName(CrmSupport.trim(a.contactName()));
            d.setPhone(CrmSupport.trim(a.phone()));
            d.setCountry(a.country().trim().toUpperCase(Locale.ROOT));
            d.setProvince(CrmSupport.trim(a.province()));
            d.setCity(CrmSupport.trim(a.city()));
            d.setZip(CrmSupport.trim(a.zip()));
            d.setAddressLine(a.addressLine().trim());
            d.setIsDefault(Boolean.TRUE.equals(a.isDefault()));
            d.setRemark(CrmSupport.trim(a.remark()));
            addressMapper.insert(d);
        }
    }

    private void saveBanks(Long customerId, List<BankSave> list) {
        bankMapper.deleteByParent(customerId);
        for (BankSave b : list) {
            CustomerBankDO d = new CustomerBankDO();
            d.setCustomerId(customerId);
            d.setBankName(b.bankName().trim());
            d.setAccountName(b.accountName().trim());
            d.setAccountNo(b.accountNo().trim());
            d.setSwift(CrmSupport.trim(b.swift()));
            d.setCurrency(b.currency() == null ? null : CrmSupport.trim(b.currency().toUpperCase(Locale.ROOT)));
            d.setRemark(CrmSupport.trim(b.remark()));
            bankMapper.insert(d);
        }
    }

    // ==================== 状态 ====================

    /** 转正式（R03）：发起审批 CRM_CUSTOMER_ACTIVATE；未配置审批流时直接成为正式客户 */
    @Transactional(rollbackFor = Exception.class)
    public String activate(Long id) {
        CustomerDO c = getVisible(id);
        requireStatus(c, "转正式", PROSPECT);
        List<String> missing = new ArrayList<>();
        List<CustomerContactDO> contacts = contactMapper.selectByParent(id);
        List<CustomerAddressDO> addresses = addressMapper.selectByParent(id);
        if (contacts.stream().noneMatch(x -> Boolean.TRUE.equals(x.getIsPrimary()))) missing.add("主联系人");
        if (addresses.stream().noneMatch(a -> "SHIP_TO".equals(a.getAddressType()) && Boolean.TRUE.equals(a.getIsDefault()))) missing.add("默认收货地址");
        if (c.getPaymentTermId() == null) missing.add("付款条件");
        if (!Boolean.TRUE.equals(c.getIsForeign())) {
            if (c.getTaxNo() == null) missing.add("税号");
            if (addresses.stream().noneMatch(a -> "BILL_TO".equals(a.getAddressType()) && Boolean.TRUE.equals(a.getIsDefault()))) missing.add("默认开票地址");
        }
        if (!missing.isEmpty()) throw BizException.of(CrmErrorCodes.CUSTOMER_ACTIVATE_MISSING, String.join("、", missing));
        Map<String, Object> vars = new HashMap<>();
        vars.put("customerType", c.getCustomerType());
        vars.put("country", c.getCountry());
        vars.put("creditLimitBase", c.getCreditLimit() == null ? BigDecimal.ZERO : c.getCreditLimit());
        StartResult r = workflowApi.start(CrmModuleConfig.CUSTOMER_ACTIVATE, c.getId(), c.getCode(), "客户转正式 " + c.getCode() + " " + c.getShortName(),
                vars, Map.of("ownerId", c.getOwnerId()), support.currentUser());
        fire(c, r.isStarted() ? Op.SUBMIT : Op.APPROVE, null);
        return c.getCustomerStatus().name();
    }

    @EventListener
    public void onApproval(ApprovalCompletedEvent e) {
        if (!CrmModuleConfig.CUSTOMER_ACTIVATE.equals(e.getBizType())) return;
        CustomerDO c = mapper.selectById(e.getBizId());
        if (c == null || c.getCustomerStatus() != PENDING) return;
        switch (e.getResult()) {
            case APPROVED -> fire(c, Op.APPROVE, null);
            default -> fire(c, Op.BACK, e.getComment());
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void disable(Long id, String reason) {
        fire(getVisible(id), Op.DISABLE, CrmSupport.trim(reason));
    }

    @Transactional(rollbackFor = Exception.class)
    public void enable(Long id) {
        fire(getVisible(id), Op.ENABLE, null);
    }

    /** R07：原因必填；记录加入前的状态，移出时恢复 */
    @Transactional(rollbackFor = Exception.class)
    public void blacklist(Long id, String reason) {
        if (!StringUtils.hasText(reason)) throw new BizException(CrmErrorCodes.BLACKLIST_REASON_REQUIRED);
        CustomerDO c = getVisible(id);
        if (c.getCustomerStatus() == PENDING) throw BizException.of(CrmErrorCodes.CUSTOMER_STATUS, STATUS_LABELS.get(PENDING), "加入黑名单");
        c.setStatusBeforeBlacklist(c.getCustomerStatus());
        c.setBlacklistReason(reason.trim());
        fire(c, Op.BLACKLIST, reason.trim());
    }

    @Transactional(rollbackFor = Exception.class)
    public void unblacklist(Long id, String reason) {
        CustomerDO c = getVisible(id);
        boolean wasActive = c.getStatusBeforeBlacklist() == ACTIVE || c.getStatusBeforeBlacklist() == DISABLED;
        c.setBlacklistReason(null);
        c.setStatusBeforeBlacklist(null);
        fire(c, wasActive ? Op.UNBLACKLIST_ACTIVE : Op.UNBLACKLIST_PROSPECT, CrmSupport.trim(reason));
    }

    private void fire(CustomerDO c, Op op, String reason) {
        CustomerStatus from = c.getCustomerStatus();
        if (!MACHINE.canFire(from, op)) throw BizException.of(CrmErrorCodes.CUSTOMER_STATUS, STATUS_LABELS.get(from), op.label());
        c.setCustomerStatus(MACHINE.fire(from, op));
        mapper.updateByIdOrFail(c);
        support.log(BIZ_TYPE, c.getId(), c.getCode(), op.name(), op.label(), from.name(), c.getCustomerStatus().name(), reason);
        if (op != Op.SUBMIT) eventPublisher.publish(new CustomerStatusChangedEvent(c.getId(), c.getCode(), from, c.getCustomerStatus(), reason));
    }

    private static void requireStatus(CustomerDO c, String action, CustomerStatus expected) {
        if (c.getCustomerStatus() != expected) throw BizException.of(CrmErrorCodes.CUSTOMER_STATUS, STATUS_LABELS.get(c.getCustomerStatus()), action);
    }

    // ==================== 转移 ====================

    /** 客户转移（3.4）：负责人、负责部门改为新负责人；记录转移日志；发布 CustomerOwnerChangedEvent */
    @Transactional(rollbackFor = Exception.class)
    public int transfer(TransferReq req) {
        if (!StringUtils.hasText(req.reason())) throw new BizException(CrmErrorCodes.TRANSFER_REASON_REQUIRED);
        UserDTO u = support.user(req.newOwnerId());
        if (u == null || !u.enabled()) throw BizException.of(GlobalErrorCodes.DATA_NOT_EXISTS, "新负责人");
        boolean docs = req.transferDocs() == null || req.transferDocs();
        List<Long> moved = new ArrayList<>();
        for (Long id : new LinkedHashSet<>(req.customerIds())) {
            CustomerDO c = getVisible(id);
            if (Objects.equals(c.getOwnerId(), u.id())) continue;
            Long from = c.getOwnerId();
            c.setOwnerId(u.id());
            c.setDeptId(u.deptId());
            c.setOrgId(support.companyOf(u.deptId(), u.orgId()));
            mapper.updateByIdOrFail(c);
            CustomerTransferLogDO log = new CustomerTransferLogDO();
            log.setCustomerId(id);
            log.setFromOwnerId(from);
            log.setToOwnerId(u.id());
            log.setTransferDocs(docs);
            log.setReason(req.reason().trim());
            log.setOperatorId(support.currentUser());
            transferLogMapper.insert(log);
            support.log(BIZ_TYPE, id, c.getCode(), "TRANSFER", "转移", c.getCustomerStatus().name(), c.getCustomerStatus().name(),
                    "转移给 " + u.realName() + "：" + req.reason().trim());
            moved.add(id);
        }
        if (!moved.isEmpty()) eventPublisher.publish(new CustomerOwnerChangedEvent(moved, u.id(), docs));
        return moved.size();
    }

    public List<TransferLogRow> transferLogs(Long customerId) {
        getVisible(customerId);
        List<CustomerTransferLogDO> list = transferLogMapper.selectByParent(customerId);
        Map<Long, UserDTO> users = support.users(list.stream().flatMap(l -> Stream.of(l.getFromOwnerId(), l.getToOwnerId(), l.getOperatorId())).toList());
        return list.stream().map(l -> new TransferLogRow(l.getId(), l.getFromOwnerId(), CrmSupport.name(users, l.getFromOwnerId()), l.getToOwnerId(),
                CrmSupport.name(users, l.getToOwnerId()), Boolean.TRUE.equals(l.getTransferDocs()), l.getReason(),
                CrmSupport.name(users, l.getOperatorId()), l.getCreatedAt())).toList().reversed();
    }

    // ==================== 删除 ====================

    /** R09：只有潜在客户且没有任何业务数据时可以删除 */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        CustomerDO c = getVisible(id);
        requireStatus(c, "删除", PROSPECT);
        boolean hasData = followupMapper.selectCount(new LambdaQueryWrapper<FollowupDO>().eq(FollowupDO::getCustomerId, id)) > 0
                || opportunityMapper.selectCount(new LambdaQueryWrapper<OpportunityDO>().eq(OpportunityDO::getCustomerId, id)) > 0
                || partMapper.selectCount(new LambdaQueryWrapper<CustomerPartDO>().eq(CustomerPartDO::getCustomerId, id)) > 0
                || referenceCheckers.stream().anyMatch(ch -> ch.hasBusinessData(id));
        if (hasData) throw new BizException(CrmErrorCodes.CUSTOMER_HAS_DATA);
        contactMapper.deleteByParent(id);
        addressMapper.deleteByParent(id);
        bankMapper.deleteByParent(id);
        creditMapper.delete(new LambdaQueryWrapper<CustomerCreditDO>().eq(CustomerCreditDO::getCustomerId, id));
        mapper.deleteById(id);
        fileApi.deleteByBiz(BIZ_TYPE, id);
        support.log(BIZ_TYPE, id, c.getCode(), "DELETE", "删除", PROSPECT.name(), null, null);
    }

    // ==================== 联系人查询 ====================

    public PageResult<ContactRow> contacts(ContactQuery q) {
        LambdaQueryWrapper<CustomerContactDO> w = new LambdaQueryWrapper<CustomerContactDO>()
                .like(StringUtils.hasText(q.getName()), CustomerContactDO::getName, q.getName() == null ? null : q.getName().trim())
                .like(StringUtils.hasText(q.getEmail()), CustomerContactDO::getEmail, q.getEmail() == null ? null : q.getEmail().trim())
                .eq(q.getCustomerId() != null, CustomerContactDO::getCustomerId, q.getCustomerId())
                .eq(StringUtils.hasText(q.getRole()), CustomerContactDO::getContactRole, q.getRole())
                .eq(StringUtils.hasText(q.getStatus()), CustomerContactDO::getContactStatus, q.getStatus());
        if (StringUtils.hasText(q.getPhone())) {
            String p = q.getPhone().trim();
            w.and(x -> x.like(CustomerContactDO::getPhone, p).or().like(CustomerContactDO::getMobile, p));
        }
        String scope = CrmScope.visibleCustomerIds();
        if (scope != null) w.inSql(CustomerContactDO::getCustomerId, scope);
        w.orderByDesc(CustomerContactDO::getId);
        PageResult<CustomerContactDO> page = contactMapper.selectPage(q, w);
        Map<Long, CustomerDO> customers = byIds(page.list().stream().map(CustomerContactDO::getCustomerId).toList());
        return new PageResult<>(page.list().stream().map(x -> {
            CustomerDO c = customers.get(x.getCustomerId());
            return new ContactRow(x.getId(), x.getCustomerId(), c == null ? null : c.getCode(), c == null ? null : c.getShortName(), x.getName(),
                    x.getTitle(), x.getContactRole(), x.getEmail(), x.getMobile(), x.getPhone(), Boolean.TRUE.equals(x.getIsPrimary()),
                    x.getContactStatus());
        }).toList(), page.total());
    }

    // ==================== 公共 ====================

    public CustomerDO getOrThrow(Long id) {
        CustomerDO c = id == null ? null : mapper.selectById(id);
        if (c == null) throw new BizException(CrmErrorCodes.CUSTOMER_NOT_EXISTS);
        return c;
    }

    /** 按数据权限：看不到的客户按“不存在”处理 */
    public CustomerDO getVisible(Long id) {
        CustomerDO c = getOrThrow(id);
        DataScopes.check(c.getOrgId(), c.getDeptId(), c.getOwnerId(), "客户");
        return c;
    }

    public Map<Long, CustomerDO> byIds(Collection<Long> ids) {
        Set<Long> set = ids.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (set.isEmpty()) return Map.of();
        return mapper.selectBatchIds(set).stream().collect(Collectors.toMap(CustomerDO::getId, Function.identity()));
    }

    /** 名称 + 国家（R04 口径）查找客户 */
    public Optional<CustomerDO> findByName(String name, String country) {
        return Optional.ofNullable(mapper.selectOne(new LambdaQueryWrapper<CustomerDO>().eq(CustomerDO::getNameKey, nameKey(name))
                .eq(CustomerDO::getCountry, country.toUpperCase(Locale.ROOT)).last("LIMIT 1")));
    }

    public Optional<CustomerDO> findByCode(String code) {
        return Optional.ofNullable(mapper.selectOne(new LambdaQueryWrapper<CustomerDO>().eq(CustomerDO::getCode, code.trim().toUpperCase(Locale.ROOT))
                .last("LIMIT 1")));
    }

    public List<CustomerContactDO> contactsOf(Long customerId) {
        return contactMapper.selectByParent(customerId);
    }

    // ==================== CustomerApi ====================

    static CustomerDTO toDto(CustomerDO c) {
        return new CustomerDTO(c.getId(), c.getCode(), c.getName(), c.getNameEn(), c.getShortName(), c.getCustomerType(), c.getCustomerLevel(),
                c.getCountry(), Boolean.TRUE.equals(c.getIsForeign()), c.getCurrency(), c.getPaymentTermId(), c.getTradeTerm(), c.getSalesTaxRate(),
                c.getTaxNo(), c.getOwnerId(), c.getDeptId(), c.getCreditLimit(), c.getCreditDays(), c.getCustomerStatus());
    }

    @Override
    public Optional<CustomerDTO> getCustomer(Long id) {
        return id == null ? Optional.empty() : Optional.ofNullable(mapper.selectById(id)).map(CustomerService::toDto);
    }

    @Override
    public Map<Long, CustomerDTO> getCustomers(Collection<Long> ids) {
        Map<Long, CustomerDTO> map = new LinkedHashMap<>();
        byIds(ids).values().forEach(c -> map.put(c.getId(), toDto(c)));
        return map;
    }

    @Override
    public CustomerDTO validateCanOrder(Long id) {
        CustomerDO c = getOrThrow(id);
        switch (c.getCustomerStatus()) {
            case ACTIVE -> {
                return toDto(c);
            }
            case BLACKLIST -> throw BizException.of(CrmErrorCodes.CUSTOMER_BLACKLISTED, c.getShortName(), "下单");
            case DISABLED -> throw BizException.of(CrmErrorCodes.CUSTOMER_DISABLED, c.getShortName(), "下单");
            default -> throw BizException.of(CrmErrorCodes.CUSTOMER_NOT_ACTIVE, c.getShortName());
        }
    }

    @Override
    public CustomerDTO validateCanQuote(Long id) {
        CustomerDO c = getOrThrow(id);
        if (c.getCustomerStatus() == BLACKLIST) throw BizException.of(CrmErrorCodes.CUSTOMER_BLACKLISTED, c.getShortName(), "报价");
        if (c.getCustomerStatus() == DISABLED) throw BizException.of(CrmErrorCodes.CUSTOMER_DISABLED, c.getShortName(), "报价");
        return toDto(c);
    }

    @Override
    public CustomerDTO validateCanShip(Long id) {
        CustomerDO c = getOrThrow(id);
        if (c.getCustomerStatus() == BLACKLIST) throw BizException.of(CrmErrorCodes.CUSTOMER_BLACKLISTED, c.getShortName(), "出货");
        if (c.getCustomerStatus() == PROSPECT || c.getCustomerStatus() == PENDING) throw BizException.of(CrmErrorCodes.CUSTOMER_NOT_ACTIVE, c.getShortName());
        return toDto(c);
    }

    @Override
    public List<AddressDTO> getAddresses(Long customerId, String type) {
        return addressMapper.selectByParent(customerId).stream().filter(a -> type == null || type.equals(a.getAddressType()))
                .map(a -> new AddressDTO(a.getId(), a.getCustomerId(), a.getAddressType(), a.getCompanyName(), a.getContactName(), a.getPhone(),
                        a.getCountry(), a.getProvince(), a.getCity(), a.getZip(), a.getAddressLine(), Boolean.TRUE.equals(a.getIsDefault())))
                .toList();
    }

    @Override
    public Optional<AddressDTO> getDefaultAddress(Long customerId, String type) {
        List<AddressDTO> list = getAddresses(customerId, type);
        return list.stream().filter(AddressDTO::isDefault).findFirst().or(() -> list.stream().findFirst());
    }

    @Override
    public List<ContactDTO> getContacts(Long customerId) {
        return contactMapper.selectByParent(customerId).stream().filter(x -> !"LEFT".equals(x.getContactStatus()))
                .sorted((a, b) -> Boolean.compare(Boolean.TRUE.equals(b.getIsPrimary()), Boolean.TRUE.equals(a.getIsPrimary())))
                .map(x -> new ContactDTO(x.getId(), x.getCustomerId(), x.getName(), x.getTitle(), x.getContactRole(), x.getEmail(), x.getPhone(),
                        x.getMobile(), Boolean.TRUE.equals(x.getIsPrimary()), x.getContactStatus())).toList();
    }

    @Override
    public List<CustomerDTO> search(String keyword, Collection<CustomerStatus> statuses, int limit) {
        LambdaQueryWrapper<CustomerDO> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            String k = keyword.trim();
            w.and(x -> x.likeRight(CustomerDO::getCode, k.toUpperCase()).or().like(CustomerDO::getName, k).or().like(CustomerDO::getShortName, k));
        }
        if (statuses != null && !statuses.isEmpty()) w.in(CustomerDO::getCustomerStatus, statuses);
        w.orderByAsc(CustomerDO::getCode).last("LIMIT " + Math.max(1, Math.min(limit, 200)));
        return mapper.selectList(w).stream().map(CustomerService::toDto).toList();
    }

    /** R10：回写首次/最近下单日期 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recordOrder(Long customerId, LocalDate orderDate) {
        CustomerDO c = getOrThrow(customerId);
        LocalDate d = orderDate == null ? LocalDate.now() : orderDate;
        boolean changed = false;
        if (c.getFirstOrderDate() == null || d.isBefore(c.getFirstOrderDate())) {
            c.setFirstOrderDate(d);
            changed = true;
        }
        if (c.getLastOrderDate() == null || d.isAfter(c.getLastOrderDate())) {
            c.setLastOrderDate(d);
            changed = true;
        }
        if (changed) mapper.updateByIdOrFail(c);
    }
}
