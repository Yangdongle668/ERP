package com.erp.module.purchase.service.supplier;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.common.enums.DocStatus;
import com.erp.common.exception.BizException;
import com.erp.common.result.PageResult;
import com.erp.common.statemachine.StateMachine;
import com.erp.framework.datascope.DataScopes;
import com.erp.framework.event.DomainEventPublisher;
import com.erp.module.engineering.api.material.MaterialDTO;
import com.erp.module.purchase.api.PurchaseErrorCodes;
import com.erp.module.purchase.api.supplier.SupplierDTO;
import com.erp.module.purchase.api.supplier.SupplierStatus;
import com.erp.module.purchase.api.supplier.SupplierStatusChangedEvent;
import com.erp.module.purchase.config.PurchaseModuleConfig;
import com.erp.module.purchase.controller.vo.SupplierVOs.BankResp;
import com.erp.module.purchase.controller.vo.SupplierVOs.BankSave;
import com.erp.module.purchase.controller.vo.SupplierVOs.CertResp;
import com.erp.module.purchase.controller.vo.SupplierVOs.CertSave;
import com.erp.module.purchase.controller.vo.SupplierVOs.ContactResp;
import com.erp.module.purchase.controller.vo.SupplierVOs.ContactSave;
import com.erp.module.purchase.controller.vo.SupplierVOs.MaterialSave;
import com.erp.module.purchase.controller.vo.SupplierVOs.QualityLot;
import com.erp.module.purchase.controller.vo.SupplierVOs.QualitySummary;
import com.erp.module.purchase.controller.vo.SupplierVOs.StatusResult;
import com.erp.module.purchase.controller.vo.SupplierVOs.SupplierBrief;
import com.erp.module.purchase.controller.vo.SupplierVOs.SupplierDetail;
import com.erp.module.purchase.controller.vo.SupplierVOs.SupplierMaterialResp;
import com.erp.module.purchase.controller.vo.SupplierVOs.SupplierQuery;
import com.erp.module.purchase.controller.vo.SupplierVOs.SupplierRow;
import com.erp.module.purchase.controller.vo.SupplierVOs.SupplierSave;
import com.erp.module.purchase.dal.dataobject.OrderDO;
import com.erp.module.purchase.dal.dataobject.PriceAdjustDO;
import com.erp.module.purchase.dal.dataobject.PriceDO;
import com.erp.module.purchase.dal.dataobject.ReceiptDO;
import com.erp.module.purchase.dal.dataobject.ReceiptLineDO;
import com.erp.module.purchase.dal.dataobject.RfqSupplierDO;
import com.erp.module.purchase.dal.dataobject.SupplierBankDO;
import com.erp.module.purchase.dal.dataobject.SupplierCertDO;
import com.erp.module.purchase.dal.dataobject.SupplierContactDO;
import com.erp.module.purchase.dal.dataobject.SupplierDO;
import com.erp.module.purchase.dal.dataobject.SupplierMaterialDO;
import com.erp.module.purchase.dal.mapper.OrderMapper;
import com.erp.module.purchase.dal.mapper.PriceAdjustMapper;
import com.erp.module.purchase.dal.mapper.PriceMapper;
import com.erp.module.purchase.dal.mapper.ReceiptLineMapper;
import com.erp.module.purchase.dal.mapper.ReceiptMapper;
import com.erp.module.purchase.dal.mapper.RfqSupplierMapper;
import com.erp.module.purchase.dal.mapper.SupplierBankMapper;
import com.erp.module.purchase.dal.mapper.SupplierCertMapper;
import com.erp.module.purchase.dal.mapper.SupplierContactMapper;
import com.erp.module.purchase.dal.mapper.SupplierMapper;
import com.erp.module.purchase.dal.mapper.SupplierMaterialMapper;
import com.erp.module.purchase.service.PurSupport;
import com.erp.module.system.api.currency.CurrencyApi;
import com.erp.module.system.api.file.FileApi;
import com.erp.module.system.api.file.FileInfo;
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
import java.util.Collections;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static com.erp.module.purchase.api.supplier.SupplierStatus.ELIMINATED;
import static com.erp.module.purchase.api.supplier.SupplierStatus.PENDING;
import static com.erp.module.purchase.api.supplier.SupplierStatus.POTENTIAL;
import static com.erp.module.purchase.api.supplier.SupplierStatus.QUALIFIED;
import static com.erp.module.purchase.api.supplier.SupplierStatus.SUSPENDED;

/**
 * 供应商（需求 07-01）：档案、联系人、银行、资质、可供物料；准入（审批 PUR_SUPPLIER_QUALIFY）、暂停、恢复、淘汰。
 * 状态：潜在 → 审批中 → 合格 ⇄ 暂停 → 淘汰。
 */
@Service
public class SupplierService {

    public static final String BIZ_TYPE = PurchaseModuleConfig.SUPPLIER;
    static final Set<String> INVOICE_TYPES = Set.of("SPECIAL_VAT", "NORMAL_VAT", "NONE");
    static final Set<String> SUPPLY_STATUSES = Set.of("TRIAL", "QUALIFIED", "DISABLED");
    /** 下单校验的关键资质（R04） */
    static final Set<String> KEY_CERTS = Set.of("LICENSE", "ISO9001");
    static final List<DocStatus> OPEN_ORDER = List.of(DocStatus.DRAFT, DocStatus.PENDING_APPROVAL, DocStatus.APPROVED, DocStatus.IN_PROGRESS);

    enum Action implements StateMachine.Labeled {
        QUALIFY("提交准入"), APPROVE("准入通过"), REJECT("驳回"), WITHDRAW("撤回"), SUSPEND("暂停"), RESUME("恢复"), ELIMINATE("淘汰");

        private final String label;

        Action(String label) {
            this.label = label;
        }

        @Override
        public String label() {
            return label;
        }
    }

    static final StateMachine<SupplierStatus, Action> MACHINE = StateMachine.builder(SupplierStatus.class, Action.class)
            .transition(POTENTIAL, Action.QUALIFY, PENDING)
            .transition(PENDING, Action.APPROVE, QUALIFIED)
            .transition(PENDING, Action.REJECT, POTENTIAL)
            .transition(PENDING, Action.WITHDRAW, POTENTIAL)
            .transition(QUALIFIED, Action.SUSPEND, SUSPENDED)
            .transition(SUSPENDED, Action.RESUME, QUALIFIED)
            .transition(QUALIFIED, Action.ELIMINATE, ELIMINATED)
            .transition(SUSPENDED, Action.ELIMINATE, ELIMINATED)
            .build();

    public static final Map<SupplierStatus, String> STATUS_LABELS = Map.of(POTENTIAL, "潜在", PENDING, "审批中", QUALIFIED, "合格",
            SUSPENDED, "暂停", ELIMINATED, "淘汰");

    private final SupplierMapper mapper;
    private final SupplierContactMapper contactMapper;
    private final SupplierBankMapper bankMapper;
    private final SupplierCertMapper certMapper;
    private final SupplierMaterialMapper materialMapper;
    private final OrderMapper orderMapper;
    private final PriceMapper priceMapper;
    private final PriceAdjustMapper adjustMapper;
    private final RfqSupplierMapper rfqSupplierMapper;
    private final ReceiptMapper receiptMapper;
    private final ReceiptLineMapper receiptLineMapper;
    private final PurSupport support;
    private final PaymentTermApi paymentTermApi;
    private final CurrencyApi currencyApi;
    private final FileApi fileApi;
    private final WorkflowApi workflowApi;
    private final DomainEventPublisher eventPublisher;

    public SupplierService(SupplierMapper mapper, SupplierContactMapper contactMapper, SupplierBankMapper bankMapper, SupplierCertMapper certMapper,
                           SupplierMaterialMapper materialMapper, OrderMapper orderMapper, PriceMapper priceMapper, PriceAdjustMapper adjustMapper,
                           RfqSupplierMapper rfqSupplierMapper, ReceiptMapper receiptMapper, ReceiptLineMapper receiptLineMapper, PurSupport support,
                           PaymentTermApi paymentTermApi, CurrencyApi currencyApi, FileApi fileApi, WorkflowApi workflowApi,
                           DomainEventPublisher eventPublisher) {
        this.mapper = mapper;
        this.contactMapper = contactMapper;
        this.bankMapper = bankMapper;
        this.certMapper = certMapper;
        this.materialMapper = materialMapper;
        this.orderMapper = orderMapper;
        this.priceMapper = priceMapper;
        this.adjustMapper = adjustMapper;
        this.rfqSupplierMapper = rfqSupplierMapper;
        this.receiptMapper = receiptMapper;
        this.receiptLineMapper = receiptLineMapper;
        this.support = support;
        this.paymentTermApi = paymentTermApi;
        this.currencyApi = currencyApi;
        this.fileApi = fileApi;
        this.workflowApi = workflowApi;
        this.eventPublisher = eventPublisher;
    }

    // ==================== 查询 ====================

    public PageResult<SupplierRow> page(SupplierQuery q) {
        LambdaQueryWrapper<SupplierDO> w = query(q);
        if (w == null) return PageResult.empty();
        IPage<SupplierDO> page = mapper.selectScopedPage(Page.of(q.getPageNo(), q.getPageSize()), w);
        return new PageResult<>(rows(page.getRecords()), page.getTotal());
    }

    public List<SupplierRow> listForExport(SupplierQuery q, int limit) {
        LambdaQueryWrapper<SupplierDO> w = query(q);
        if (w == null) return List.of();
        return rows(mapper.selectScopedList(w.last("LIMIT " + limit)));
    }

    private LambdaQueryWrapper<SupplierDO> query(SupplierQuery q) {
        LambdaQueryWrapper<SupplierDO> w = new LambdaQueryWrapper<SupplierDO>().eq(SupplierDO::getDeleted, false)
                .eq(StringUtils.hasText(q.getLevel()), SupplierDO::getSupplierLevel, q.getLevel())
                .eq(StringUtils.hasText(q.getSupplierType()), SupplierDO::getSupplierType, q.getSupplierType())
                .eq(q.getBuyerId() != null, SupplierDO::getBuyerId, q.getBuyerId());
        if (StringUtils.hasText(q.getKeyword())) {
            String k = q.getKeyword().trim();
            w.and(x -> x.likeRight(SupplierDO::getCode, k.toUpperCase()).or().like(SupplierDO::getName, k).or().like(SupplierDO::getShortName, k));
        }
        if (StringUtils.hasText(q.getStatuses())) {
            w.in(SupplierDO::getSupplierStatus, Arrays.stream(q.getStatuses().split(",")).map(String::trim).map(SupplierStatus::valueOf).toList());
        }
        if (q.getMaterialId() != null) {
            w.inSql(SupplierDO::getId, "SELECT supplier_id FROM pur_supplier_material WHERE deleted = 0 AND material_id = " + q.getMaterialId().longValue());
        }
        if ("EXPIRED".equals(q.getCertExpiry())) {
            w.inSql(SupplierDO::getId, "SELECT supplier_id FROM pur_supplier_cert WHERE deleted = 0 AND expire_date < '" + LocalDate.now() + "'");
        } else if ("SOON".equals(q.getCertExpiry())) {
            w.inSql(SupplierDO::getId, "SELECT supplier_id FROM pur_supplier_cert WHERE deleted = 0 AND expire_date >= '" + LocalDate.now()
                    + "' AND expire_date <= '" + LocalDate.now().plusDays(30) + "'");
        }
        return w.orderByAsc(SupplierDO::getCode);
    }

    private List<SupplierRow> rows(List<SupplierDO> list) {
        if (list.isEmpty()) return List.of();
        List<Long> ids = list.stream().map(SupplierDO::getId).toList();
        Map<Long, List<SupplierContactDO>> contacts = contactMapper.selectByParents(ids).stream().collect(Collectors.groupingBy(SupplierContactDO::getSupplierId));
        Map<Long, List<SupplierCertDO>> certs = certMapper.selectByParents(ids).stream().collect(Collectors.groupingBy(SupplierCertDO::getSupplierId));
        Map<Long, UserDTO> users = support.users(list.stream().map(SupplierDO::getBuyerId).toList());
        Map<Long, String> terms = termNames(list.stream().map(SupplierDO::getPaymentTermId).toList());
        LocalDate today = LocalDate.now();
        return list.stream().map(s -> {
            SupplierContactDO primary = contacts.getOrDefault(s.getId(), List.of()).stream().filter(c -> Boolean.TRUE.equals(c.getIsPrimary())).findFirst().orElse(null);
            List<SupplierCertDO> cs = certs.getOrDefault(s.getId(), List.of());
            boolean expired = cs.stream().anyMatch(c -> c.getExpireDate() != null && c.getExpireDate().isBefore(today));
            boolean expiring = cs.stream().anyMatch(c -> c.getExpireDate() != null && !c.getExpireDate().isBefore(today) && !c.getExpireDate().isAfter(today.plusDays(30)));
            return new SupplierRow(s.getId(), s.getCode(), s.getShortName(), s.getName(), s.getSupplierType(), s.getSupplierLevel(), s.getCountry(),
                    s.getBuyerId(), PurSupport.name(users, s.getBuyerId()), s.getCurrency(), s.getPaymentTermId(), terms.get(s.getPaymentTermId()),
                    primary == null ? null : primary.getName(), primary == null ? null : Objects.requireNonNullElse(primary.getMobile(), primary.getPhone()),
                    expired, expiring, s.getSupplierStatus().name(), s.getQualifiedAt(), s.getUpdatedAt());
        }).toList();
    }

    Map<Long, String> termNames(Collection<Long> ids) {
        Map<Long, String> map = new HashMap<>();
        for (Long id : new HashSet<>(ids)) {
            if (id != null) paymentTermApi.get(id).ifPresent(t -> map.put(id, t.name()));
        }
        return map;
    }

    public SupplierDetail detail(Long id) {
        SupplierDO s = getOrThrow(id);
        DataScopes.check(s.getOrgId(), s.getDeptId(), s.getBuyerId(), "供应商");
        Map<Long, UserDTO> users = support.users(List.of(nz(s.getBuyerId()), nz(s.getCreatedBy())));
        String deptName = s.getDeptId() == null ? null : Optional.ofNullable(support.orgs(List.of(s.getDeptId())).get(s.getDeptId())).map(o -> o.name()).orElse(null);
        LocalDate today = LocalDate.now();
        Map<Long, String> fileNames = fileApi.list(BIZ_TYPE, id).stream().collect(Collectors.toMap(FileInfo::id, FileInfo::fileName, (a, b) -> a));
        List<CertResp> certs = certMapper.selectByParent(id).stream().map(c -> new CertResp(c.getId(), c.getCertType(), c.getCertNo(), c.getIssueDate(),
                c.getExpireDate(), c.getFileId(), fileNames.get(c.getFileId()), c.getRemark(), certStatus(c.getExpireDate(), today))).toList();
        return new SupplierDetail(s.getId(), s.getCode(), s.getName(), s.getNameEn(), s.getShortName(), s.getSupplierType(), s.getSupplierLevel(),
                s.getSupplierStatus().name(), s.getCountry(), s.getProvince(), s.getCity(), s.getAddress(), s.getTaxNo(), s.getPhone(), s.getEmail(),
                s.getWebsite(), s.getBuyerId(), PurSupport.name(users, s.getBuyerId()), s.getDeptId(), deptName, s.getCurrency(), s.getPaymentTermId(),
                termNames(List.of(s.getPaymentTermId())).get(s.getPaymentTermId()), s.getTradeTerm(), s.getPurchaseTaxRate(), s.getInvoiceType(),
                s.getLeadTimeDays(), s.getQualifiedAt(), s.getSuspendReason(), s.getRemark(),
                contactMapper.selectByParent(id).stream().map(c -> new ContactResp(c.getId(), c.getName(), c.getTitle(), c.getContactRole(), c.getPhone(),
                        c.getMobile(), c.getEmail(), Boolean.TRUE.equals(c.getIsPrimary()))).toList(),
                bankMapper.selectByParent(id).stream().map(b -> new BankResp(b.getId(), b.getBankName(), b.getAccountName(), b.getAccountNo(), b.getSwift(),
                        b.getCurrency(), Boolean.TRUE.equals(b.getIsDefault()))).toList(),
                certs, materialResps(materialMapper.selectByParent(id)), countOpenOrders(id), PurSupport.name(users, s.getCreatedBy()),
                s.getCreatedAt(), s.getUpdatedAt(), s.getVersion());
    }

    static String certStatus(LocalDate expire, LocalDate today) {
        if (expire == null) return "VALID";
        if (expire.isBefore(today)) return "EXPIRED";
        return expire.isAfter(today.plusDays(30)) ? "VALID" : "EXPIRING";
    }

    /** 选择器远程搜索：编码前缀、名称、简称；ids 用于回显 */
    public List<SupplierBrief> search(String keyword, String statuses, String ids, int limit) {
        LambdaQueryWrapper<SupplierDO> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(ids)) {
            List<Long> list = Arrays.stream(ids.split(",")).map(String::trim).filter(x -> x.matches("\\d+")).map(Long::valueOf).toList();
            if (list.isEmpty()) return List.of();
            w.in(SupplierDO::getId, list);
        } else {
            if (StringUtils.hasText(keyword)) {
                String k = keyword.trim();
                w.and(x -> x.likeRight(SupplierDO::getCode, k.toUpperCase()).or().like(SupplierDO::getName, k).or().like(SupplierDO::getShortName, k));
            }
            if (StringUtils.hasText(statuses)) {
                w.in(SupplierDO::getSupplierStatus, Arrays.stream(statuses.split(",")).map(String::trim).map(SupplierStatus::valueOf).toList());
            }
            w.orderByAsc(SupplierDO::getCode).last("LIMIT " + Math.max(1, Math.min(limit, 50)));
        }
        return mapper.selectList(w).stream().map(s -> new SupplierBrief(s.getId(), s.getCode(), s.getName(), s.getShortName(), s.getSupplierStatus().name(),
                s.getCurrency(), s.getPaymentTermId(), s.getPurchaseTaxRate(), s.getBuyerId(), s.getSupplierLevel())).toList();
    }

    public List<SupplierDTO> searchDto(String keyword, Collection<SupplierStatus> statuses, int limit) {
        LambdaQueryWrapper<SupplierDO> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            String k = keyword.trim();
            w.and(x -> x.likeRight(SupplierDO::getCode, k.toUpperCase()).or().like(SupplierDO::getName, k).or().like(SupplierDO::getShortName, k));
        }
        if (statuses != null && !statuses.isEmpty()) w.in(SupplierDO::getSupplierStatus, statuses);
        w.orderByAsc(SupplierDO::getCode).last("LIMIT " + Math.max(1, Math.min(limit, 50)));
        return mapper.selectList(w).stream().map(SupplierService::toDto).toList();
    }

    public static SupplierDTO toDto(SupplierDO s) {
        return new SupplierDTO(s.getId(), s.getCode(), s.getName(), s.getCurrency(), s.getBuyerId(), s.getSupplierStatus());
    }

    // ==================== 新建 / 修改 ====================

    @Transactional(rollbackFor = Exception.class)
    public Long create(SupplierSave req) {
        SupplierDO s = new SupplierDO();
        String code = PurSupport.trim(req.code());
        if (code != null && support.manualCodeAllowed(BIZ_TYPE)) s.setCode(code.toUpperCase());
        else s.setCode(support.nextNo(BIZ_TYPE));
        s.setSupplierStatus(POTENTIAL);
        s.setSupplierLevel(StringUtils.hasText(req.level()) ? req.level() : "C");
        fill(s, req, true);
        checkUnique(s);
        mapper.insert(s);
        saveChildren(s, req);
        if (req.fileIds() != null && !req.fileIds().isEmpty()) fileApi.bind(req.fileIds(), BIZ_TYPE, s.getId());
        support.log(BIZ_TYPE, s.getId(), s.getCode(), "CREATE", "新建", null, POTENTIAL.name(), null);
        return s.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, SupplierSave req) {
        SupplierDO s = getOrThrow(id);
        DataScopes.check(s.getOrgId(), s.getDeptId(), s.getBuyerId(), "供应商");
        if (s.getSupplierStatus() == ELIMINATED) throw BizException.of(PurchaseErrorCodes.SUPPLIER_ELIMINATED, s.getName());
        if (req.version() != null) s.setVersion(req.version());
        String code = PurSupport.trim(req.code());
        if (code != null && support.manualCodeAllowed(BIZ_TYPE)) s.setCode(code.toUpperCase());
        if (StringUtils.hasText(req.level()) && !req.level().equals(s.getSupplierLevel())) {
            support.dict().validate("pur_supplier_level", req.level(), "供应商等级");
            s.setSupplierLevel(req.level());
        }
        fill(s, req, false);
        checkUnique(s);
        mapper.updateByIdOrFail(s);
        saveChildren(s, req);
        if (req.fileIds() != null && !req.fileIds().isEmpty()) fileApi.bind(req.fileIds(), BIZ_TYPE, s.getId());
    }

    private void fill(SupplierDO s, SupplierSave req, boolean creating) {
        s.setName(req.name().trim());
        s.setNameEn(PurSupport.trim(req.nameEn()));
        s.setShortName(req.shortName().trim());
        String type = StringUtils.hasText(req.supplierType()) ? req.supplierType() : "MANUFACTURER";
        if (creating || !type.equals(s.getSupplierType())) support.dict().validate("pur_supplier_type", type, "供应商类型");
        s.setSupplierType(type);
        if (creating) support.dict().validate("pur_supplier_level", s.getSupplierLevel(), "供应商等级");
        s.setCountry(StringUtils.hasText(req.country()) ? req.country().trim().toUpperCase() : "CN");
        s.setProvince(PurSupport.trim(req.province()));
        s.setCity(PurSupport.trim(req.city()));
        s.setAddress(PurSupport.trim(req.address()));
        s.setTaxNo(req.taxNo() == null ? null : PurSupport.trim(req.taxNo().toUpperCase()));
        s.setPhone(PurSupport.trim(req.phone()));
        s.setEmail(PurSupport.trim(req.email()));
        s.setWebsite(PurSupport.trim(req.website()));
        Long buyer = req.buyerId() != null ? req.buyerId() : (creating ? support.currentUser() : s.getBuyerId());
        UserDTO u = support.user(buyer);
        if (u == null) throw BizException.of(com.erp.common.exception.GlobalErrorCodes.DATA_NOT_EXISTS, "采购员");
        s.setBuyerId(buyer);
        s.setDeptId(req.deptId() != null ? req.deptId() : u.deptId());
        s.setOrgId(support.companyOf(s.getDeptId(), u.orgId()));
        String currency = StringUtils.hasText(req.currency()) ? req.currency().trim().toUpperCase() : currencyApi.getBaseCurrency();
        if (creating || !currency.equals(s.getCurrency())) currencyApi.validate(currency);
        s.setCurrency(currency);
        if (creating || !Objects.equals(req.paymentTermId(), s.getPaymentTermId())) paymentTermApi.validate(req.paymentTermId(), "PURCHASE");
        s.setPaymentTermId(req.paymentTermId());
        String trade = PurSupport.trim(req.tradeTerm());
        if (trade != null && !trade.equals(s.getTradeTerm())) support.dict().validate("sys_trade_term", trade, "贸易条款");
        s.setTradeTerm(trade);
        BigDecimal rate = req.purchaseTaxRate() != null ? req.purchaseTaxRate() : new BigDecimal("0.13");
        if (rate.signum() < 0 || rate.compareTo(BigDecimal.ONE) >= 0) {
            throw BizException.of(com.erp.common.exception.GlobalErrorCodes.BAD_REQUEST, "税率必须在 0～1 之间（小数，如 0.13）");
        }
        s.setPurchaseTaxRate(rate);
        String invoice = StringUtils.hasText(req.invoiceType()) ? req.invoiceType() : "SPECIAL_VAT";
        if (invoice == null || !INVOICE_TYPES.contains(invoice)) throw BizException.of(com.erp.common.exception.GlobalErrorCodes.BAD_REQUEST, "发票类型");
        s.setInvoiceType(invoice);
        s.setLeadTimeDays(req.leadTimeDays());
        s.setRemark(PurSupport.trim(req.remark()));
    }

    /** R01：编码、名称唯一；税号非空时唯一 */
    private void checkUnique(SupplierDO s) {
        SupplierDO byName = mapper.selectOne(new LambdaQueryWrapper<SupplierDO>().eq(SupplierDO::getName, s.getName()).last("LIMIT 1"));
        if (byName != null && !byName.getId().equals(s.getId())) throw BizException.of(PurchaseErrorCodes.SUPPLIER_DUPLICATE, s.getName());
        SupplierDO byCode = mapper.selectOne(new LambdaQueryWrapper<SupplierDO>().eq(SupplierDO::getCode, s.getCode()).last("LIMIT 1"));
        if (byCode != null && !byCode.getId().equals(s.getId())) throw BizException.of(PurchaseErrorCodes.SUPPLIER_DUPLICATE, s.getCode());
        if (s.getTaxNo() != null) {
            SupplierDO byTax = mapper.selectOne(new LambdaQueryWrapper<SupplierDO>().eq(SupplierDO::getTaxNo, s.getTaxNo()).last("LIMIT 1"));
            if (byTax != null && !byTax.getId().equals(s.getId())) throw BizException.of(PurchaseErrorCodes.SUPPLIER_TAX_NO_DUPLICATE, s.getTaxNo(), byTax.getName());
        }
    }

    private void saveChildren(SupplierDO s, SupplierSave req) {
        if (req.contacts() != null) saveContacts(s.getId(), req.contacts());
        if (req.banks() != null) saveBanks(s.getId(), req.banks());
        if (req.certs() != null) saveCerts(s.getId(), req.certs());
        if (req.materials() != null) replaceMaterials(s, req.materials());
    }

    private void saveContacts(Long supplierId, List<ContactSave> list) {
        contactMapper.deleteByParent(supplierId);
        boolean primarySet = false;
        for (ContactSave c : list) {
            if (!StringUtils.hasText(c.phone()) && !StringUtils.hasText(c.mobile()) && !StringUtils.hasText(c.email())) {
                throw BizException.of(PurchaseErrorCodes.SUPPLIER_CONTACT_WAY, c.name());
            }
            SupplierContactDO d = new SupplierContactDO();
            d.setSupplierId(supplierId);
            d.setName(c.name().trim());
            d.setTitle(PurSupport.trim(c.title()));
            d.setContactRole(PurSupport.trim(c.role()));
            d.setPhone(PurSupport.trim(c.phone()));
            d.setMobile(PurSupport.trim(c.mobile()));
            d.setEmail(PurSupport.trim(c.email()));
            boolean primary = Boolean.TRUE.equals(c.isPrimary()) && !primarySet;
            primarySet |= primary;
            d.setIsPrimary(primary);
            contactMapper.insert(d);
        }
    }

    private void saveBanks(Long supplierId, List<BankSave> list) {
        bankMapper.deleteByParent(supplierId);
        boolean defaultSet = false;
        for (BankSave b : list) {
            SupplierBankDO d = new SupplierBankDO();
            d.setSupplierId(supplierId);
            d.setBankName(b.bankName().trim());
            d.setAccountName(b.accountName().trim());
            d.setAccountNo(b.accountNo().trim());
            d.setSwift(PurSupport.trim(b.swift()));
            d.setCurrency(PurSupport.trim(b.currency()));
            boolean def = Boolean.TRUE.equals(b.isDefault()) && !defaultSet;
            defaultSet |= def;
            d.setIsDefault(def);
            bankMapper.insert(d);
        }
    }

    private void saveCerts(Long supplierId, List<CertSave> list) {
        certMapper.deleteByParent(supplierId);
        List<Long> files = new ArrayList<>();
        for (CertSave c : list) {
            support.dict().validate("pur_cert_type", c.certType(), "资质类型");
            SupplierCertDO d = new SupplierCertDO();
            d.setSupplierId(supplierId);
            d.setCertType(c.certType());
            d.setCertNo(PurSupport.trim(c.certNo()));
            d.setIssueDate(c.issueDate());
            d.setExpireDate(c.expireDate());
            d.setFileId(c.fileId());
            d.setRemark(PurSupport.trim(c.remark()));
            certMapper.insert(d);
            if (c.fileId() != null) files.add(c.fileId());
        }
        if (!files.isEmpty()) fileApi.bind(files, BIZ_TYPE, supplierId);
    }

    /** 整体替换可供物料（保留已有行的 ID，以免影响引用） */
    private void replaceMaterials(SupplierDO s, List<MaterialSave> list) {
        Map<Long, SupplierMaterialDO> existing = materialMapper.selectByParent(s.getId()).stream()
                .collect(Collectors.toMap(SupplierMaterialDO::getMaterialId, m -> m));
        Set<Long> seen = new HashSet<>();
        Map<Long, MaterialDTO> materials = support.materials(list.stream().map(MaterialSave::materialId).toList());
        for (MaterialSave m : list) {
            MaterialDTO dto = materials.get(m.materialId());
            if (dto == null) throw BizException.of(com.erp.common.exception.GlobalErrorCodes.DATA_NOT_EXISTS, "物料");
            if (!seen.add(m.materialId())) throw BizException.of(PurchaseErrorCodes.SUPPLIER_MATERIAL_DUPLICATE, dto.code());
            saveMaterial(s, existing.get(m.materialId()), m);
        }
        for (SupplierMaterialDO old : existing.values()) {
            if (!seen.contains(old.getMaterialId())) materialMapper.deleteById(old.getId());
        }
    }

    private SupplierMaterialDO saveMaterial(SupplierDO s, SupplierMaterialDO d, MaterialSave m) {
        boolean creating = d == null;
        if (creating) {
            d = new SupplierMaterialDO();
            d.setSupplierId(s.getId());
            d.setMaterialId(m.materialId());
        }
        String status = StringUtils.hasText(m.supplyStatus()) ? m.supplyStatus() : "TRIAL";
        if (status == null || !SUPPLY_STATUSES.contains(status)) throw BizException.of(com.erp.common.exception.GlobalErrorCodes.BAD_REQUEST, "供货状态");
        d.setSupplierPartNo(PurSupport.trim(m.supplierPartNo()));
        d.setSupplyStatus(status);
        d.setIsDefault(Boolean.TRUE.equals(m.isDefault()) && !"DISABLED".equals(status));
        d.setLeadTimeDays(m.leadTimeDays());
        d.setMoq(m.moq());
        d.setMpq(m.mpq());
        d.setQuotaPct(m.quotaPct());
        d.setApprovedAt(m.approvedAt() != null ? m.approvedAt() : ("QUALIFIED".equals(status) && d.getApprovedAt() == null ? LocalDate.now() : d.getApprovedAt()));
        d.setRemark(PurSupport.trim(m.remark()));
        if (creating) materialMapper.insert(d);
        else materialMapper.updateByIdOrFail(d);
        if (Boolean.TRUE.equals(d.getIsDefault())) clearOtherDefaults(d);
        return d;
    }

    /** R02：同一物料只能有一个默认供应商，设置新默认时取消其他供应商的默认 */
    private void clearOtherDefaults(SupplierMaterialDO d) {
        for (SupplierMaterialDO o : materialMapper.selectList(new LambdaQueryWrapper<SupplierMaterialDO>()
                .eq(SupplierMaterialDO::getMaterialId, d.getMaterialId()).eq(SupplierMaterialDO::getIsDefault, true).ne(SupplierMaterialDO::getId, d.getId()))) {
            o.setIsDefault(false);
            materialMapper.updateByIdOrFail(o);
        }
    }

    // ==================== 可供物料 ====================

    public List<SupplierMaterialResp> materials(Long supplierId) {
        getOrThrow(supplierId);
        return materialResps(materialMapper.selectByParent(supplierId));
    }

    /** 某物料的可供供应商 */
    public List<SupplierMaterialResp> suppliersOfMaterial(Long materialId) {
        return materialResps(materialMapper.selectList(new LambdaQueryWrapper<SupplierMaterialDO>().eq(SupplierMaterialDO::getMaterialId, materialId)
                .orderByDesc(SupplierMaterialDO::getIsDefault).orderByAsc(SupplierMaterialDO::getId)));
    }

    @Transactional(rollbackFor = Exception.class)
    public Long saveMaterial(Long supplierId, Long id, MaterialSave req) {
        SupplierDO s = getOrThrow(supplierId);
        support.material(req.materialId());
        SupplierMaterialDO d = id == null ? null : materialMapper.selectById(id);
        SupplierMaterialDO same = materialMapper.selectOne(new LambdaQueryWrapper<SupplierMaterialDO>().eq(SupplierMaterialDO::getSupplierId, supplierId)
                .eq(SupplierMaterialDO::getMaterialId, req.materialId()));
        if (id == null && same != null) throw BizException.of(PurchaseErrorCodes.SUPPLIER_MATERIAL_DUPLICATE, support.material(req.materialId()).code());
        if (id != null && (d == null || !d.getSupplierId().equals(supplierId))) {
            throw BizException.of(com.erp.common.exception.GlobalErrorCodes.DATA_NOT_EXISTS, "可供物料");
        }
        if (d != null && !d.getMaterialId().equals(req.materialId())) {
            if (same != null) throw BizException.of(PurchaseErrorCodes.SUPPLIER_MATERIAL_DUPLICATE, support.material(req.materialId()).code());
            d.setMaterialId(req.materialId());
        }
        return saveMaterial(s, d, req).getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteMaterial(Long supplierId, Long id) {
        SupplierMaterialDO d = materialMapper.selectById(id);
        if (d == null || !d.getSupplierId().equals(supplierId)) throw BizException.of(com.erp.common.exception.GlobalErrorCodes.DATA_NOT_EXISTS, "可供物料");
        materialMapper.deleteById(id);
    }

    /** 调价单、询价定标选择非可供物料时自动加入为试用 */
    @Transactional(rollbackFor = Exception.class)
    public void ensureMaterial(Long supplierId, Long materialId) {
        SupplierMaterialDO same = materialMapper.selectOne(new LambdaQueryWrapper<SupplierMaterialDO>().eq(SupplierMaterialDO::getSupplierId, supplierId)
                .eq(SupplierMaterialDO::getMaterialId, materialId));
        if (same != null) return;
        SupplierMaterialDO d = new SupplierMaterialDO();
        d.setSupplierId(supplierId);
        d.setMaterialId(materialId);
        d.setSupplyStatus("TRIAL");
        d.setIsDefault(false);
        materialMapper.insert(d);
    }

    public Optional<SupplierMaterialDO> supplierMaterial(Long supplierId, Long materialId) {
        return Optional.ofNullable(materialMapper.selectOne(new LambdaQueryWrapper<SupplierMaterialDO>().eq(SupplierMaterialDO::getSupplierId, supplierId)
                .eq(SupplierMaterialDO::getMaterialId, materialId)));
    }

    /** 物料的默认供应商（默认标记、非停用） */
    public Optional<SupplierDO> defaultSupplier(Long materialId) {
        SupplierMaterialDO d = materialMapper.selectOne(new LambdaQueryWrapper<SupplierMaterialDO>().eq(SupplierMaterialDO::getMaterialId, materialId)
                .eq(SupplierMaterialDO::getIsDefault, true).ne(SupplierMaterialDO::getSupplyStatus, "DISABLED").last("LIMIT 1"));
        return d == null ? Optional.empty() : Optional.ofNullable(mapper.selectById(d.getSupplierId()));
    }

    private List<SupplierMaterialResp> materialResps(List<SupplierMaterialDO> list) {
        if (list.isEmpty()) return List.of();
        Map<Long, MaterialDTO> ms = support.materials(list.stream().map(SupplierMaterialDO::getMaterialId).toList());
        Map<Long, SupplierDO> ss = byIds(list.stream().map(SupplierMaterialDO::getSupplierId).toList());
        return list.stream().map(d -> {
            MaterialDTO m = ms.get(d.getMaterialId());
            SupplierDO s = ss.get(d.getSupplierId());
            return new SupplierMaterialResp(d.getId(), d.getSupplierId(), s == null ? null : s.getCode(), s == null ? null : s.getShortName(),
                    s == null ? null : s.getSupplierStatus().name(), d.getMaterialId(), m == null ? null : m.code(), m == null ? null : m.name(),
                    m == null ? null : m.spec(), m == null ? null : m.baseUom(), d.getSupplierPartNo(), d.getSupplyStatus(),
                    Boolean.TRUE.equals(d.getIsDefault()), d.getLeadTimeDays(), d.getMoq(), d.getMpq(), d.getQuotaPct(), d.getApprovedAt(), d.getRemark());
        }).toList();
    }

    // ==================== 准入与状态 ====================

    /** R03：提交准入。无审批流时直接合格 */
    @Transactional(rollbackFor = Exception.class)
    public String qualify(Long id) {
        SupplierDO s = getOrThrow(id);
        DataScopes.check(s.getOrgId(), s.getDeptId(), s.getBuyerId(), "供应商");
        if (!MACHINE.canFire(s.getSupplierStatus(), Action.QUALIFY)) MACHINE.fire(s.getSupplierStatus(), Action.QUALIFY);
        List<String> missing = new ArrayList<>();
        if (contactMapper.selectByParent(id).stream().noneMatch(c -> Boolean.TRUE.equals(c.getIsPrimary()))) missing.add("主联系人");
        if (bankMapper.selectByParent(id).stream().noneMatch(b -> Boolean.TRUE.equals(b.getIsDefault()))) missing.add("默认银行账户");
        if (s.getPaymentTermId() == null) missing.add("付款条件");
        if ("CN".equals(s.getCountry()) && certMapper.selectByParent(id).stream().noneMatch(c -> "LICENSE".equals(c.getCertType()))) missing.add("营业执照资质");
        if (materialMapper.selectByParent(id).isEmpty()) missing.add("至少一个可供物料");
        if (!missing.isEmpty()) throw BizException.of(PurchaseErrorCodes.SUPPLIER_QUALIFY_MISSING, String.join("、", missing));
        fire(s, Action.QUALIFY, null);
        Map<String, Object> vars = new HashMap<>();
        vars.put("supplierType", s.getSupplierType());
        Map<String, Long> users = new HashMap<>();
        users.put("buyerId", s.getBuyerId());
        StartResult r = workflowApi.start(PurchaseModuleConfig.SUPPLIER_QUALIFY, id, s.getCode(), "供应商准入 " + s.getCode() + " " + s.getShortName(),
                vars, users, support.currentUser());
        if (!r.isStarted()) approveQualify(s);
        return s.getSupplierStatus().name();
    }

    private void approveQualify(SupplierDO s) {
        s.setQualifiedAt(LocalDate.now());
        fire(s, Action.APPROVE, null);
        eventPublisher.publish(new SupplierStatusChangedEvent(s.getId(), s.getCode(), PENDING, QUALIFIED, null));
    }

    @EventListener
    public void onApproval(ApprovalCompletedEvent e) {
        if (!PurchaseModuleConfig.SUPPLIER_QUALIFY.equals(e.getBizType())) return;
        SupplierDO s = getOrThrow(e.getBizId());
        if (s.getSupplierStatus() != PENDING) return;
        switch (e.getResult()) {
            case APPROVED -> approveQualify(s);
            case WITHDRAWN -> fire(s, Action.WITHDRAW, null);
            default -> fire(s, Action.REJECT, e.getComment());
        }
    }

    /** R06：暂停（原因必填），返回未完成采购订单数用于提示 */
    @Transactional(rollbackFor = Exception.class)
    public StatusResult suspend(Long id, String reason) {
        return changeStatus(id, Action.SUSPEND, PurSupport.requireReason(reason, "暂停"));
    }

    @Transactional(rollbackFor = Exception.class)
    public StatusResult resume(Long id) {
        return changeStatus(id, Action.RESUME, null);
    }

    @Transactional(rollbackFor = Exception.class)
    public StatusResult eliminate(Long id, String reason) {
        return changeStatus(id, Action.ELIMINATE, PurSupport.requireReason(reason, "淘汰"));
    }

    private StatusResult changeStatus(Long id, Action action, String reason) {
        SupplierDO s = getOrThrow(id);
        DataScopes.check(s.getOrgId(), s.getDeptId(), s.getBuyerId(), "供应商");
        SupplierStatus from = s.getSupplierStatus();
        s.setSuspendReason(action == Action.RESUME ? null : reason);
        fire(s, action, reason);
        eventPublisher.publish(new SupplierStatusChangedEvent(s.getId(), s.getCode(), from, s.getSupplierStatus(), reason));
        return new StatusResult(s.getSupplierStatus().name(), countOpenOrders(id));
    }

    private void fire(SupplierDO s, Action action, String reason) {
        SupplierStatus from = s.getSupplierStatus();
        s.setSupplierStatus(MACHINE.fire(from, action));
        mapper.updateByIdOrFail(s);
        support.log(BIZ_TYPE, s.getId(), s.getCode(), action.name(), action.label(), from.name(), s.getSupplierStatus().name(), reason);
    }

    /** 评估发布后更新供应商等级（PUR-SC-R03） */
    @Transactional(rollbackFor = Exception.class)
    public void updateLevel(Long id, String level, String reason) {
        SupplierDO s = getOrThrow(id);
        if (Objects.equals(s.getSupplierLevel(), level)) return;
        String from = s.getSupplierLevel();
        s.setSupplierLevel(level);
        mapper.updateByIdOrFail(s);
        support.log(BIZ_TYPE, s.getId(), s.getCode(), "LEVEL", "等级变更 " + from + " → " + level, s.getSupplierStatus().name(),
                s.getSupplierStatus().name(), reason);
    }

    public int countOpenOrders(Long supplierId) {
        return Math.toIntExact(orderMapper.selectCount(new LambdaQueryWrapper<OrderDO>().eq(OrderDO::getSupplierId, supplierId)
                .in(OrderDO::getStatus, OPEN_ORDER)));
    }

    /** R07：只有潜在状态且无任何订单、价格、询价记录可删除 */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        SupplierDO s = getOrThrow(id);
        DataScopes.check(s.getOrgId(), s.getDeptId(), s.getBuyerId(), "供应商");
        if (s.getSupplierStatus() != POTENTIAL) throw new BizException(PurchaseErrorCodes.SUPPLIER_NOT_DELETABLE);
        boolean used = orderMapper.selectCount(new LambdaQueryWrapper<OrderDO>().eq(OrderDO::getSupplierId, id)) > 0
                || priceMapper.selectCount(new LambdaQueryWrapper<PriceDO>().eq(PriceDO::getSupplierId, id)) > 0
                || adjustMapper.selectCount(new LambdaQueryWrapper<PriceAdjustDO>().eq(PriceAdjustDO::getSupplierId, id)) > 0
                || rfqSupplierMapper.selectCount(new LambdaQueryWrapper<RfqSupplierDO>().eq(RfqSupplierDO::getSupplierId, id)) > 0
                || receiptMapper.selectCount(new LambdaQueryWrapper<ReceiptDO>().eq(ReceiptDO::getSupplierId, id)) > 0;
        if (used) throw new BizException(PurchaseErrorCodes.SUPPLIER_HAS_BIZ);
        contactMapper.deleteByParent(id);
        bankMapper.deleteByParent(id);
        certMapper.deleteByParent(id);
        materialMapper.deleteByParent(id);
        mapper.deleteById(id);
        fileApi.deleteByBiz(BIZ_TYPE, id);
    }

    // ==================== 校验（供下单、询价、到货使用） ====================

    /** R04：合格，且没有已过期的营业执照 / ISO9001 资质 */
    public SupplierDO validateQualified(Long id) {
        SupplierDO s = getOrThrow(id);
        if (s.getSupplierStatus() != QUALIFIED) throw BizException.of(PurchaseErrorCodes.SUPPLIER_NOT_QUALIFIED, s.getName());
        LocalDate today = LocalDate.now();
        for (SupplierCertDO c : certMapper.selectByParent(id)) {
            if (KEY_CERTS.contains(c.getCertType()) && c.getExpireDate() != null && c.getExpireDate().isBefore(today)) {
                throw BizException.of(PurchaseErrorCodes.SUPPLIER_CERT_EXPIRED, s.getName(), support.dict().label("pur_cert_type", c.getCertType()));
            }
        }
        return s;
    }

    /** 询价、样品订单：供应商不能是暂停/淘汰 */
    public SupplierDO validateActive(Long id) {
        SupplierDO s = getOrThrow(id);
        if (s.getSupplierStatus() == SUSPENDED) throw BizException.of(PurchaseErrorCodes.SUPPLIER_SUSPENDED, s.getName());
        if (s.getSupplierStatus() == ELIMINATED) throw BizException.of(PurchaseErrorCodes.SUPPLIER_ELIMINATED, s.getName());
        return s;
    }

    // ==================== 到货与质量 ====================

    /** 最近 12 个月到货批次与 IQC 合格率（特采算不合格） */
    public QualitySummary quality(Long supplierId) {
        getOrThrow(supplierId);
        List<ReceiptDO> receipts = receiptMapper.selectList(new LambdaQueryWrapper<ReceiptDO>().eq(ReceiptDO::getSupplierId, supplierId)
                .in(ReceiptDO::getStatus, DocStatus.APPROVED, DocStatus.COMPLETED).ge(ReceiptDO::getArrivalAt, LocalDate.now().minusMonths(12).atStartOfDay())
                .orderByDesc(ReceiptDO::getArrivalAt));
        if (receipts.isEmpty()) return new QualitySummary(0, 0, null, List.of());
        Map<Long, ReceiptDO> byId = receipts.stream().collect(Collectors.toMap(ReceiptDO::getId, r -> r, (a, b) -> a, LinkedHashMap::new));
        List<ReceiptLineDO> lines = receiptLineMapper.selectByParents(byId.keySet());
        Map<Long, MaterialDTO> ms = support.materials(lines.stream().map(ReceiptLineDO::getMaterialId).toList());
        List<QualityLot> lots = new ArrayList<>();
        int judged = 0;
        int pass = 0;
        for (ReceiptLineDO l : lines) {
            ReceiptDO r = byId.get(l.getReceiptId());
            MaterialDTO m = ms.get(l.getMaterialId());
            if (Boolean.TRUE.equals(l.getInspectRequired()) && !"PENDING".equals(l.getInspectStatus()) && !"NONE".equals(l.getInspectStatus())) {
                judged++;
                if ("QUALIFIED".equals(l.getInspectStatus())) pass++;
            }
            lots.add(new QualityLot(r.getId(), r.getDocNo(), r.getArrivalAt().toLocalDate(), m == null ? null : m.code(), m == null ? null : m.name(),
                    l.getBaseQty(), l.getInspectStatus(), l.getQualifiedQty(), l.getRejectedQty(), l.getInspectionNo()));
        }
        BigDecimal rate = judged == 0 ? null : BigDecimal.valueOf(pass).multiply(PurSupport.HUNDRED).divide(BigDecimal.valueOf(judged), 2, RoundingMode.HALF_UP);
        return new QualitySummary(judged, pass, rate, lots);
    }

    // ==================== 工具 ====================

    public SupplierDO getOrThrow(Long id) {
        SupplierDO s = id == null ? null : mapper.selectById(id);
        if (s == null) throw new BizException(PurchaseErrorCodes.SUPPLIER_NOT_EXISTS);
        return s;
    }

    public Optional<SupplierDO> find(Long id) {
        return Optional.ofNullable(id == null ? null : mapper.selectById(id));
    }

    public Map<Long, SupplierDO> byIds(Collection<Long> ids) {
        Set<Long> set = ids.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (set.isEmpty()) return Collections.emptyMap();
        return mapper.selectBatchIds(set).stream().collect(Collectors.toMap(SupplierDO::getId, s -> s));
    }

    public List<SupplierCertDO> certs(Long supplierId) {
        return certMapper.selectByParent(supplierId);
    }

    public List<SupplierContactDO> contacts(Long supplierId) {
        return contactMapper.selectByParent(supplierId);
    }

    public PaymentTermDTO paymentTerm(Long id) {
        return id == null ? null : paymentTermApi.get(id).orElse(null);
    }

    /** 资质到期提醒（R05）：到期前 30 天、到期当天提醒负责采购员和 SQE（有供应商准入权限的用户） */
    public int remindCertExpiry() {
        LocalDate today = LocalDate.now();
        List<SupplierCertDO> certs = certMapper.selectList(new LambdaQueryWrapper<SupplierCertDO>()
                .in(SupplierCertDO::getExpireDate, today, today.plusDays(30)));
        if (certs.isEmpty()) return 0;
        Map<Long, SupplierDO> suppliers = byIds(certs.stream().map(SupplierCertDO::getSupplierId).toList());
        List<Long> sqe = support.usersWithPermission("pur:supplier:qualify");
        int n = 0;
        for (SupplierCertDO c : certs) {
            SupplierDO s = suppliers.get(c.getSupplierId());
            if (s == null || s.getSupplierStatus() == ELIMINATED) continue;
            String type = support.dict().label("pur_cert_type", c.getCertType());
            String when = c.getExpireDate().equals(today) ? "今天到期" : "将于 " + c.getExpireDate() + " 到期";
            List<Long> to = new ArrayList<>(sqe);
            to.add(s.getBuyerId());
            support.message(to, "供应商资质到期提醒", "供应商「" + s.getName() + "」的资质「" + type + "」" + when + "，请及时更新", "/purchase/supplier/" + s.getId());
            n++;
        }
        return n;
    }

    private static Long nz(Long v) {
        return v == null ? 0L : v;
    }
}
