package com.erp.module.engineering.service;

import com.erp.common.enums.EnableStatus;
import com.erp.common.exception.BizException;
import com.erp.common.result.PageResult;
import com.erp.common.statemachine.StateMachine;
import com.erp.framework.event.DomainEventPublisher;
import com.erp.framework.security.LoginUser;
import com.erp.framework.security.SecurityUtils;
import com.erp.module.engineering.api.EngineeringErrorCodes;
import com.erp.module.engineering.api.material.IssueRule;
import com.erp.module.engineering.api.material.MaterialChangedEvent;
import com.erp.module.engineering.api.material.MaterialReferenceChecker;
import com.erp.module.engineering.api.material.MaterialStatus;
import com.erp.module.engineering.api.material.MaterialStatusChangedEvent;
import com.erp.module.engineering.api.material.MaterialType;
import com.erp.module.engineering.api.material.MaterialUsage;
import com.erp.module.engineering.api.material.OrderPolicy;
import com.erp.module.engineering.api.material.SourceType;
import com.erp.module.engineering.api.material.Tracking;
import com.erp.module.engineering.config.EngineeringModuleConfig;
import com.erp.module.engineering.controller.vo.MaterialPageReqVO;
import com.erp.module.engineering.controller.vo.MaterialRespVO;
import com.erp.module.engineering.controller.vo.MaterialSaveReqVO;
import com.erp.module.engineering.controller.vo.MaterialVOs.BatchResult;
import com.erp.module.engineering.controller.vo.MaterialVOs.DuplicateCheckReq;
import com.erp.module.engineering.controller.vo.MaterialVOs.DuplicateCheckResp;
import com.erp.module.engineering.controller.vo.MaterialVOs.Failure;
import com.erp.module.engineering.controller.vo.MaterialVOs.References;
import com.erp.module.engineering.controller.vo.MaterialVOs.Settings;
import com.erp.module.engineering.controller.vo.MaterialVOs.Suspect;
import com.erp.module.engineering.dal.dataobject.MaterialCategoryDO;
import com.erp.module.engineering.dal.dataobject.MaterialDO;
import com.erp.module.engineering.dal.dataobject.MaterialUomDO;
import com.erp.module.engineering.dal.mapper.BomLineMapper;
import com.erp.module.engineering.dal.mapper.MaterialMapper;
import com.erp.module.engineering.dal.mapper.MaterialUomMapper;
import com.erp.module.system.api.coderule.CodeRuleApi;
import com.erp.module.system.api.file.FileApi;
import com.erp.module.system.api.log.DocLogApi;
import com.erp.module.system.api.param.ParamApi;
import com.erp.module.system.api.uom.UomApi;
import com.erp.module.system.api.user.UserApi;
import com.erp.module.system.api.user.UserDTO;
import com.erp.module.system.api.workflow.ApprovalCompletedEvent;
import com.erp.module.system.api.workflow.StartResult;
import com.erp.module.system.api.workflow.WorkflowApi;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 物料（需求 05-02）。
 * <ul>
 *   <li>编码全局唯一（不区分大小写，统一大写保存），为空时按 ENG_MATERIAL 规则以类别前缀生成（R01、R02）；</li>
 *   <li>启用后编码、物料类型、基本单位不可修改；已被单据使用的单位换算不可修改或删除（R07）；</li>
 *   <li>启用前校验必填属性（R06）；参数要求审批时走审批流 ENG_MATERIAL；</li>
 *   <li>只有草稿且从未被引用的物料可以删除（R09）。</li>
 * </ul>
 */
@Service
public class MaterialService {

    public static final String BIZ_TYPE = "ENG_MATERIAL";
    public static final String PERM_COST = "eng:material:cost";
    static final String PARAM_APPROVAL = "eng.material.enable-approval";
    static final String PARAM_DUP = "eng.material.duplicate-check";
    static final String PARAM_ALLOW_DRAFT = "eng.bom.allow-draft-component";

    enum Action implements StateMachine.Labeled {
        ENABLE("启用"), DISABLE("停用"), SUBMIT("提交启用"), APPROVE("审批通过"), REJECT("驳回");

        private final String label;

        Action(String label) {
            this.label = label;
        }

        @Override
        public String label() {
            return label;
        }
    }

    static final StateMachine<MaterialStatus, Action> STATE_MACHINE =
            StateMachine.builder(MaterialStatus.class, Action.class)
                    .transition(MaterialStatus.DRAFT, Action.ENABLE, MaterialStatus.ENABLED)
                    .transition(MaterialStatus.DRAFT, Action.SUBMIT, MaterialStatus.PENDING)
                    .transition(MaterialStatus.PENDING, Action.APPROVE, MaterialStatus.ENABLED)
                    .transition(MaterialStatus.PENDING, Action.REJECT, MaterialStatus.DRAFT)
                    .transition(MaterialStatus.ENABLED, Action.DISABLE, MaterialStatus.DISABLED)
                    .transition(MaterialStatus.DISABLED, Action.ENABLE, MaterialStatus.ENABLED)
                    .build();

    private final MaterialMapper materialMapper;
    private final MaterialUomMapper uomMapper;
    private final BomLineMapper bomLineMapper;
    private final MaterialCategoryService categoryService;
    private final CodeRuleApi codeRuleApi;
    private final UomApi uomApi;
    private final ParamApi paramApi;
    private final UserApi userApi;
    private final FileApi fileApi;
    private final WorkflowApi workflowApi;
    private final DocLogApi docLogApi;
    private final DomainEventPublisher eventPublisher;
    private final List<MaterialReferenceChecker> referenceCheckers;
    private final TransactionTemplate tx;

    public MaterialService(MaterialMapper materialMapper, MaterialUomMapper uomMapper, BomLineMapper bomLineMapper,
                           MaterialCategoryService categoryService, CodeRuleApi codeRuleApi, UomApi uomApi, ParamApi paramApi,
                           UserApi userApi, FileApi fileApi, WorkflowApi workflowApi, DocLogApi docLogApi,
                           DomainEventPublisher eventPublisher, List<MaterialReferenceChecker> referenceCheckers,
                           PlatformTransactionManager transactionManager) {
        this.materialMapper = materialMapper;
        this.uomMapper = uomMapper;
        this.bomLineMapper = bomLineMapper;
        this.categoryService = categoryService;
        this.codeRuleApi = codeRuleApi;
        this.uomApi = uomApi;
        this.paramApi = paramApi;
        this.userApi = userApi;
        this.fileApi = fileApi;
        this.workflowApi = workflowApi;
        this.docLogApi = docLogApi;
        this.eventPublisher = eventPublisher;
        this.referenceCheckers = referenceCheckers;
        this.tx = new TransactionTemplate(transactionManager);
    }

    // ==================== 查询 ====================

    public PageResult<MaterialRespVO> page(MaterialPageReqVO req) {
        PageResult<MaterialDO> page = materialMapper.selectPage(req, categoryFilter(req.getCategoryId()));
        return new PageResult<>(toResp(page.list()), page.total());
    }

    /** 导出：按查询条件，最多 limit 行 */
    public List<MaterialRespVO> listForExport(MaterialPageReqVO req, int limit) {
        return toResp(materialMapper.selectForExport(req, categoryFilter(req.getCategoryId()), limit));
    }

    private List<Long> categoryFilter(Long categoryId) {
        return categoryId == null ? null : categoryService.getDescendantIds(categoryId);
    }

    /** 选择器远程搜索（登录即可） */
    public List<MaterialRespVO> search(String keyword, String types, MaterialStatus status, List<Long> ids, int limit) {
        return toResp(materialMapper.search(keyword, types, status, ids, limit));
    }

    /** 按编码精确查询（明细行输入编码回车）；不存在或未启用时返回 null */
    public MaterialRespVO getEnabledByCode(String code) {
        MaterialDO m = materialMapper.selectByCode(code);
        return m == null || m.getStatus() != MaterialStatus.ENABLED ? null : toResp(List.of(m)).get(0);
    }

    public MaterialRespVO get(Long id) {
        MaterialDO m = getOrThrow(id);
        boolean used = usage(id).used();
        List<MaterialRespVO.UomRow> uoms = uomMapper.selectByMaterial(id).stream()
                .map(u -> new MaterialRespVO.UomRow(u.getId(), u.getUom(), u.getRate(), u.getRemark(), used)).toList();
        return toResp(List.of(m), uoms).get(0);
    }

    public Settings settings() {
        return new Settings(paramApi.getBool(PARAM_APPROVAL), codeRuleApi.isManualAllowed(EngineeringModuleConfig.CODE_RULE_MATERIAL),
                paramApi.getString(PARAM_DUP), canViewCost());
    }

    // ==================== 新建 / 修改 ====================

    @Transactional(rollbackFor = Exception.class)
    public Long create(MaterialSaveReqVO req) {
        MaterialCategoryDO category = checkCategory(req.categoryId());
        String code = StringUtils.hasText(req.code()) && codeRuleApi.isManualAllowed(EngineeringModuleConfig.CODE_RULE_MATERIAL)
                ? req.code().trim().toUpperCase()
                : codeRuleApi.nextCode(EngineeringModuleConfig.CODE_RULE_MATERIAL, Map.of("categoryPrefix", category.getCodePrefix()));
        assertCodeUnique(code, null);

        MaterialDO m = new MaterialDO();
        m.setCode(code);
        m.setStatus(MaterialStatus.DRAFT);
        m.setMaterialType(req.materialType());
        m.setBaseUom(uomApi.validate(req.baseUom().trim()).code());
        m.setLowLevelCode(0);
        applyDefaults(m, category);
        fill(m, req, true);
        validate(m, req.uoms());
        checkDuplicates(m);
        try {
            materialMapper.insert(m);
        } catch (DuplicateKeyException e) {
            throw BizException.of(EngineeringErrorCodes.MATERIAL_CODE_DUPLICATE, code);
        }
        saveUoms(m, req.uoms(), false);
        if (m.getImageFileId() != null) fileApi.bind(List.of(m.getImageFileId()), BIZ_TYPE, m.getId());
        return m.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, MaterialSaveReqVO req) {
        MaterialDO m = getOrThrow(id);
        MaterialDO before = copy(m);
        if (req.version() != null) m.setVersion(req.version());
        if (m.getStatus() == MaterialStatus.DRAFT) {
            if (!Objects.equals(req.categoryId(), m.getCategoryId())) checkCategory(req.categoryId());
            if (StringUtils.hasText(req.code()) && !req.code().trim().equalsIgnoreCase(m.getCode())) {
                String code = req.code().trim().toUpperCase();
                assertCodeUnique(code, id);
                m.setCode(code);
            }
            m.setMaterialType(req.materialType());
            m.setBaseUom(uomApi.validate(req.baseUom().trim()).code());
        } else {
            if (changesLockedFields(m, req)) throw BizException.of(EngineeringErrorCodes.MATERIAL_NOT_EDITABLE, m.getCode());
            if (!Objects.equals(req.categoryId(), m.getCategoryId())) checkCategory(req.categoryId());
        }
        fill(m, req, false);
        // R10：有库存或未完成出入库单据时不能修改库存管理方式
        if (before.getTracking() != m.getTracking() && m.getStatus() != MaterialStatus.DRAFT && usage(id).trackingLocked()) {
            throw new BizException(EngineeringErrorCodes.MATERIAL_TRACKING_LOCKED);
        }
        validate(m, req.uoms());
        checkDuplicates(m);
        materialMapper.updateByIdOrFail(m);
        saveUoms(m, req.uoms(), m.getStatus() != MaterialStatus.DRAFT && usage(id).used());
        if (m.getImageFileId() != null && !Objects.equals(before.getImageFileId(), m.getImageFileId())) {
            fileApi.bind(List.of(m.getImageFileId()), BIZ_TYPE, m.getId());
        }
        Set<String> groups = changedGroups(before, m);
        if (!groups.isEmpty() && m.getStatus() != MaterialStatus.DRAFT) {
            eventPublisher.publish(new MaterialChangedEvent(m.getId(), m.getCode(), groups));
        }
    }

    /** 选择类别后带出默认值（仅新建时，前端对未手工修改的字段同样处理） */
    private static void applyDefaults(MaterialDO m, MaterialCategoryDO c) {
        m.setTracking(c.getDefaultTracking());
        m.setIqcRequired(Boolean.TRUE.equals(c.getDefaultIqcRequired()));
        m.setShelfLifeDays(c.getDefaultShelfLifeDays());
    }

    private void fill(MaterialDO m, MaterialSaveReqVO req, boolean creating) {
        m.setName(req.name().trim());
        m.setNameEn(trim(req.nameEn()));
        m.setSpec(trim(req.spec()));
        m.setCategoryId(req.categoryId());
        m.setDrawingNo(trim(req.drawingNo()));
        m.setRevision(trim(req.revision()));
        m.setBrand(trim(req.brand()));
        m.setManufacturer(trim(req.manufacturer()));
        m.setMpn(trim(req.mpn()));
        m.setMpnKey(normalizeKey(req.mpn()));
        m.setDupKey(normalizeKey(req.name()) + "|" + Objects.toString(normalizeKey(req.spec()), ""));
        m.setHsCode(trim(req.hsCode()));
        m.setUnitNetWeight(req.unitNetWeight());
        m.setUnitGrossWeight(req.unitGrossWeight());
        m.setImageFileId(req.imageFileId());
        m.setRemark(trim(req.remark()));
        MaterialType type = m.getMaterialType();
        // 计划属性：取得方式默认按物料类型
        m.setSourceType(req.sourceType() != null ? req.sourceType() : creating ? defaultSource(type) : m.getSourceType());
        m.setLeadTimeDays(req.leadTimeDays() != null ? req.leadTimeDays() : creating ? 0 : m.getLeadTimeDays());
        m.setSafetyStock(qty(req.safetyStock(), creating ? BigDecimal.ZERO : m.getSafetyStock()));
        m.setMaxStock(req.maxStock());
        m.setOrderPolicy(req.orderPolicy() != null ? req.orderPolicy() : creating ? OrderPolicy.LOT_FOR_LOT : m.getOrderPolicy());
        m.setFixedLotQty(m.getOrderPolicy() == OrderPolicy.FIXED_QTY ? req.fixedLotQty() : null);
        m.setPeriodDays(m.getOrderPolicy() == OrderPolicy.PERIOD ? req.periodDays() : null);
        m.setMoq(qty(req.moq(), creating ? BigDecimal.ZERO : m.getMoq()));
        m.setMpq(qty(req.mpq(), creating ? BigDecimal.ZERO : m.getMpq()));
        m.setPlannerId(req.plannerId());
        // 采购属性
        m.setBuyerId(req.buyerId());
        m.setPurchaseUom(trim(req.purchaseUom()));
        m.setOverReceivePct(pct(req.overReceivePct(), creating ? BigDecimal.ZERO : m.getOverReceivePct()));
        // 库存属性
        if (req.tracking() != null) m.setTracking(req.tracking());
        if (m.getTracking() == null) m.setTracking(Tracking.NONE);
        m.setIssueRule(req.issueRule() != null ? req.issueRule() : creating ? IssueRule.FIFO : m.getIssueRule());
        if (req.shelfLifeDays() != null || !creating) m.setShelfLifeDays(req.shelfLifeDays());
        m.setMinRemainingLifePct(req.minRemainingLifePct());
        // 质量属性
        if (req.iqcRequired() != null) m.setIqcRequired(req.iqcRequired());
        if (m.getIqcRequired() == null) m.setIqcRequired(true);
        m.setFqcRequired(req.fqcRequired() != null ? req.fqcRequired()
                : creating ? (type == MaterialType.SEMI_FINISHED || type == MaterialType.FINISHED) : m.getFqcRequired());
        m.setOqcRequired(req.oqcRequired() != null ? req.oqcRequired() : creating ? type == MaterialType.FINISHED : m.getOqcRequired());
        // 财务与销售：没有成本字段权限时忽略该字段（R13）
        if (canViewCost()) m.setStandardCost(req.standardCost());
        m.setSalesUom(trim(req.salesUom()));
        m.setPurchaseTaxRate(pct(req.purchaseTaxRate(), creating ? new BigDecimal("0.13") : m.getPurchaseTaxRate()));
        m.setSalesTaxRate(pct(req.salesTaxRate(), creating ? new BigDecimal("0.13") : m.getSalesTaxRate()));
    }

    static SourceType defaultSource(MaterialType type) {
        return switch (type) {
            case SEMI_FINISHED, FINISHED, PHANTOM -> SourceType.MAKE;
            default -> SourceType.PURCHASE;
        };
    }

    /** 保存时的字段级规则 */
    private void validate(MaterialDO m, List<MaterialSaveReqVO.UomSave> uoms) {
        if (m.getMaterialType() == MaterialType.PHANTOM && m.getSourceType() != null && m.getSourceType() != SourceType.MAKE) {
            throw new BizException(EngineeringErrorCodes.MATERIAL_PHANTOM_MUST_MAKE);
        }
        if (m.getOrderPolicy() == OrderPolicy.FIXED_QTY && (m.getFixedLotQty() == null || m.getFixedLotQty().signum() <= 0)) {
            throw new BizException(EngineeringErrorCodes.MATERIAL_FIXED_LOT_REQUIRED);
        }
        if (m.getOrderPolicy() == OrderPolicy.PERIOD && m.getPeriodDays() == null) {
            throw new BizException(EngineeringErrorCodes.MATERIAL_PERIOD_REQUIRED);
        }
        if (m.getMaxStock() != null && m.getMaxStock().compareTo(m.getSafetyStock()) < 0) {
            throw new BizException(EngineeringErrorCodes.MATERIAL_MAX_LT_SAFETY);
        }
        if (m.getIssueRule() == IssueRule.FEFO && m.getShelfLifeDays() == null) {
            throw new BizException(EngineeringErrorCodes.MATERIAL_FEFO_SHELF_LIFE);
        }
        if (m.getMinRemainingLifePct() != null && m.getShelfLifeDays() == null) {
            throw new BizException(EngineeringErrorCodes.MATERIAL_MIN_LIFE_SHELF_LIFE);
        }
        if (m.getUnitNetWeight() != null && m.getUnitGrossWeight() != null && m.getUnitGrossWeight().compareTo(m.getUnitNetWeight()) < 0) {
            throw new BizException(EngineeringErrorCodes.MATERIAL_GROSS_LT_NET);
        }
        // 单位换算与采购/销售单位（R11）
        Set<String> aux = new LinkedHashSet<>();
        for (MaterialSaveReqVO.UomSave u : uoms == null ? List.<MaterialSaveReqVO.UomSave>of() : uoms) {
            String code = uomApi.validate(u.uom().trim()).code();
            if (code.equals(m.getBaseUom())) throw BizException.of(EngineeringErrorCodes.MATERIAL_UOM_SAME_AS_BASE, code);
            if (!aux.add(code)) throw BizException.of(EngineeringErrorCodes.MATERIAL_UOM_DUPLICATE, code);
        }
        checkUnit("采购单位 ", m.getPurchaseUom(), m.getBaseUom(), aux);
        checkUnit("销售单位 ", m.getSalesUom(), m.getBaseUom(), aux);
    }

    private void checkUnit(String label, String uom, String base, Set<String> aux) {
        if (uom == null || uom.equals(base) || aux.contains(uom)) return;
        throw BizException.of(EngineeringErrorCodes.MATERIAL_UOM_NO_CONVERSION, label, uom);
    }

    /** 整体替换单位换算；locked 时已有的换算行不能删除、不能改换算率（R07） */
    private void saveUoms(MaterialDO m, List<MaterialSaveReqVO.UomSave> uoms, boolean locked) {
        List<MaterialSaveReqVO.UomSave> list = uoms == null ? List.of() : uoms;
        if (locked) {
            Map<String, BigDecimal> requested = new HashMap<>();
            list.forEach(u -> requested.put(u.uom().trim().toUpperCase(), u.rate()));
            for (MaterialUomDO old : uomMapper.selectByMaterial(m.getId())) {
                BigDecimal rate = requested.get(old.getUom().toUpperCase());
                if (rate == null || rate.compareTo(old.getRate()) != 0) throw new BizException(EngineeringErrorCodes.MATERIAL_UOM_USED);
            }
        }
        uomMapper.deleteByMaterial(m.getId());
        for (MaterialSaveReqVO.UomSave u : list) {
            MaterialUomDO row = new MaterialUomDO();
            row.setMaterialId(m.getId());
            row.setUom(uomApi.validate(u.uom().trim()).code());
            row.setRate(u.rate());
            row.setRemark(trim(u.remark()));
            uomMapper.insert(row);
        }
    }

    private MaterialCategoryDO checkCategory(Long categoryId) {
        MaterialCategoryDO c = categoryService.getOrThrow(categoryId);
        if (c.getStatus() != EnableStatus.ENABLED || !categoryService.isLeaf(categoryId)) {
            throw new BizException(EngineeringErrorCodes.MATERIAL_CATEGORY_NOT_LEAF);
        }
        return c;
    }

    // ==================== 查重 ====================

    public DuplicateCheckResp duplicateCheck(DuplicateCheckReq req) {
        String mode = paramApi.getString(PARAM_DUP);
        if ("OFF".equals(mode)) return new DuplicateCheckResp(mode, List.of());
        return new DuplicateCheckResp(mode, suspects(req.categoryId(), req.name(), req.spec(), req.mpn(), req.id()));
    }

    List<Suspect> suspects(Long categoryId, String name, String spec, String mpn, Long excludeId) {
        String dupKey = StringUtils.hasText(name) ? normalizeKey(name) + "|" + Objects.toString(normalizeKey(spec), "") : null;
        String mpnKey = normalizeKey(mpn);
        return materialMapper.selectDuplicates(categoryId, dupKey, mpnKey, excludeId).stream()
                .map(d -> new Suspect(d.getId(), d.getCode(), d.getName(), d.getSpec(), d.getMpn(), d.getStatus(),
                        mpnKey != null && mpnKey.equals(d.getMpnKey()) ? "MPN" : "NAME_SPEC"))
                .toList();
    }

    /** BLOCK 时阻止保存（R03） */
    private void checkDuplicates(MaterialDO m) {
        if (!"BLOCK".equals(paramApi.getString(PARAM_DUP))) return;
        List<Suspect> dups = suspects(m.getCategoryId(), m.getName(), m.getSpec(), m.getMpn(), m.getId());
        if (!dups.isEmpty()) {
            throw BizException.of(EngineeringErrorCodes.MATERIAL_DUPLICATE_SUSPECT, dups.stream().map(Suspect::code).collect(Collectors.joining("、")));
        }
    }

    // ==================== 状态 ====================

    /**
     * 启用（R06）。草稿物料在参数要求审批时发起审批流 ENG_MATERIAL（进入待审批），
     * 未配置流程或全部自动通过时直接启用。
     *
     * @return 结果状态
     */
    @Transactional(rollbackFor = Exception.class)
    public MaterialStatus enable(Long id) {
        MaterialDO m = getOrThrow(id);
        checkComplete(m);
        if (m.getStatus() == MaterialStatus.DRAFT && paramApi.getBool(PARAM_APPROVAL)) {
            LoginUser user = SecurityUtils.getLoginUserOrNull();
            Map<String, Object> vars = new HashMap<>();
            vars.put("materialType", m.getMaterialType().name());
            vars.put("categoryId", String.valueOf(m.getCategoryId()));
            Map<String, Long> users = new HashMap<>();
            if (m.getCreatedBy() != null) users.put("createdBy", m.getCreatedBy());
            StartResult r = workflowApi.start(BIZ_TYPE, m.getId(), m.getCode(), "物料启用 " + m.getCode() + " " + m.getName(),
                    vars, users, user == null ? null : user.id());
            if (r.isStarted()) {
                change(m, Action.SUBMIT);
                return m.getStatus();
            }
        }
        change(m, Action.ENABLE);
        return m.getStatus();
    }

    @Transactional(rollbackFor = Exception.class)
    public void disable(Long id) {
        change(getOrThrow(id), Action.DISABLE);
    }

    /** 审批结果（同一事务内同步处理） */
    @EventListener
    public void onApprovalCompleted(ApprovalCompletedEvent e) {
        if (!BIZ_TYPE.equals(e.getBizType())) return;
        MaterialDO m = getOrThrow(e.getBizId());
        if (m.getStatus() != MaterialStatus.PENDING) return;
        if (e.getResult() == ApprovalCompletedEvent.Result.APPROVED) {
            checkComplete(m);
            change(m, Action.APPROVE);
        } else {
            change(m, Action.REJECT);
        }
    }

    /** 批量启用 / 停用：逐条执行，失败的记录原因 */
    public BatchResult batch(List<Long> ids, boolean enable) {
        int ok = 0;
        List<Failure> failures = new ArrayList<>();
        for (Long id : ids == null ? List.<Long>of() : ids) {
            MaterialDO m = materialMapper.selectById(id);
            if (m == null) continue;
            try {
                // 每条记录独立事务：一条失败不影响其他记录
                tx.executeWithoutResult(st -> {
                    if (enable) enable(id);
                    else disable(id);
                });
                ok++;
            } catch (BizException ex) {
                failures.add(new Failure(id, m.getCode(), ex.getMessage()));
            }
        }
        return new BatchResult(ok, failures);
    }


    private void change(MaterialDO m, Action action) {
        MaterialStatus old = m.getStatus();
        MaterialStatus next = STATE_MACHINE.fire(old, action);
        m.setStatus(next);
        materialMapper.updateByIdOrFail(m);
        docLogApi.record(BIZ_TYPE, m.getId(), m.getCode(), action.name(), action.label(), old.name(), next.name(), null);
        if (next == MaterialStatus.ENABLED || old == MaterialStatus.ENABLED) {
            eventPublisher.publish(new MaterialStatusChangedEvent(m.getId(), m.getCode(), old, next));
        }
    }

    /** R06：必填属性完整 */
    void checkComplete(MaterialDO m) {
        List<String> missing = new ArrayList<>();
        if (m.getCategoryId() == null) missing.add("物料类别");
        if (!StringUtils.hasText(m.getName())) missing.add("名称");
        if (m.getMaterialType() == null) missing.add("物料类型");
        if (!StringUtils.hasText(m.getBaseUom())) missing.add("基本单位");
        if (m.getSourceType() == null) missing.add("取得方式");
        if (m.getSourceType() == SourceType.PURCHASE && m.getLeadTimeDays() == null) missing.add("采购提前期");
        if (m.getIssueRule() == IssueRule.FEFO && m.getShelfLifeDays() == null) missing.add("保质期");
        if (!missing.isEmpty()) throw BizException.of(EngineeringErrorCodes.MATERIAL_INCOMPLETE, m.getCode(), String.join("、", missing));
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        MaterialDO m = getOrThrow(id);
        if (m.getStatus() != MaterialStatus.DRAFT) throw new BizException(EngineeringErrorCodes.MATERIAL_NOT_DELETABLE);
        if (usage(id).used() || bomLineMapper.countBomsUsing(id, false) > 0 || bomLineMapper.countBomsAsParent(id) > 0) {
            throw new BizException(EngineeringErrorCodes.MATERIAL_IN_USE);
        }
        materialMapper.deleteById(id);
        uomMapper.deleteByMaterial(id);
        fileApi.deleteByBiz(BIZ_TYPE, id);
    }

    /** 停用前的引用统计（R08） */
    public References references(Long id) {
        getOrThrow(id);
        MaterialUsage u = usage(id);
        return new References(u.stockQty(), u.openDocCount(), (int) bomLineMapper.countBomsUsing(id, true));
    }

    MaterialUsage usage(Long id) {
        MaterialUsage total = MaterialUsage.NONE;
        for (MaterialReferenceChecker c : referenceCheckers) total = total.plus(c.usage(id));
        return total;
    }

    // ==================== 工具 ====================

    public MaterialDO getOrThrow(Long id) {
        MaterialDO m = id == null ? null : materialMapper.selectById(id);
        if (m == null) throw new BizException(EngineeringErrorCodes.MATERIAL_NOT_EXISTS);
        return m;
    }

    private void assertCodeUnique(String code, Long excludeId) {
        MaterialDO exists = materialMapper.selectByCode(code);
        if (exists != null && !exists.getId().equals(excludeId)) throw BizException.of(EngineeringErrorCodes.MATERIAL_CODE_DUPLICATE, code);
    }

    private static boolean changesLockedFields(MaterialDO m, MaterialSaveReqVO req) {
        return (StringUtils.hasText(req.code()) && !req.code().trim().equalsIgnoreCase(m.getCode()))
                || req.materialType() != m.getMaterialType()
                || !Objects.equals(req.baseUom().trim().toUpperCase(), m.getBaseUom().toUpperCase());
    }

    static boolean canViewCost() {
        LoginUser u = SecurityUtils.getLoginUserOrNull();
        return u == null || u.hasPermission(PERM_COST);
    }

    /** 查重键：去空格、大写 */
    static String normalizeKey(String s) {
        if (!StringUtils.hasText(s)) return null;
        return s.replaceAll("\\s+", "").toUpperCase();
    }

    private static String trim(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }

    private static BigDecimal qty(BigDecimal v, BigDecimal def) {
        return v == null ? def : v;
    }

    private static BigDecimal pct(BigDecimal v, BigDecimal def) {
        return v == null ? def : v;
    }

    private static MaterialDO copy(MaterialDO m) {
        MaterialDO c = new MaterialDO();
        org.springframework.beans.BeanUtils.copyProperties(m, c);
        return c;
    }

    private static Set<String> changedGroups(MaterialDO a, MaterialDO b) {
        Set<String> groups = new HashSet<>();
        if (!Objects.equals(a.getSourceType(), b.getSourceType()) || !Objects.equals(a.getLeadTimeDays(), b.getLeadTimeDays())
                || ne(a.getSafetyStock(), b.getSafetyStock()) || ne(a.getMaxStock(), b.getMaxStock()) || a.getOrderPolicy() != b.getOrderPolicy()
                || ne(a.getFixedLotQty(), b.getFixedLotQty()) || !Objects.equals(a.getPeriodDays(), b.getPeriodDays())
                || ne(a.getMoq(), b.getMoq()) || ne(a.getMpq(), b.getMpq()) || !Objects.equals(a.getPlannerId(), b.getPlannerId())) {
            groups.add(MaterialChangedEvent.PLAN);
        }
        if (!Objects.equals(a.getBuyerId(), b.getBuyerId()) || !Objects.equals(a.getPurchaseUom(), b.getPurchaseUom())
                || ne(a.getOverReceivePct(), b.getOverReceivePct())) {
            groups.add(MaterialChangedEvent.PURCHASE);
        }
        if (a.getTracking() != b.getTracking() || a.getIssueRule() != b.getIssueRule() || !Objects.equals(a.getShelfLifeDays(), b.getShelfLifeDays())
                || ne(a.getMinRemainingLifePct(), b.getMinRemainingLifePct())) {
            groups.add(MaterialChangedEvent.STOCK);
        }
        if (!Objects.equals(a.getIqcRequired(), b.getIqcRequired()) || !Objects.equals(a.getFqcRequired(), b.getFqcRequired())
                || !Objects.equals(a.getOqcRequired(), b.getOqcRequired())) {
            groups.add(MaterialChangedEvent.QUALITY);
        }
        if (ne(a.getStandardCost(), b.getStandardCost()) || !Objects.equals(a.getSalesUom(), b.getSalesUom())
                || ne(a.getPurchaseTaxRate(), b.getPurchaseTaxRate()) || ne(a.getSalesTaxRate(), b.getSalesTaxRate())) {
            groups.add(MaterialChangedEvent.FINANCE);
        }
        return groups;
    }

    private static boolean ne(BigDecimal a, BigDecimal b) {
        if (a == null || b == null) return a != b;
        return a.compareTo(b) != 0;
    }

    // ==================== 转换 ====================

    List<MaterialRespVO> toResp(List<MaterialDO> list) {
        return toResp(list, null);
    }

    private List<MaterialRespVO> toResp(List<MaterialDO> list, List<MaterialRespVO.UomRow> uoms) {
        if (list.isEmpty()) return List.of();
        Set<Long> userIds = new HashSet<>();
        Set<Long> categoryIds = new HashSet<>();
        for (MaterialDO m : list) {
            if (m.getPlannerId() != null) userIds.add(m.getPlannerId());
            if (m.getBuyerId() != null) userIds.add(m.getBuyerId());
            if (m.getCreatedBy() != null) userIds.add(m.getCreatedBy());
            if (m.getCategoryId() != null) categoryIds.add(m.getCategoryId());
        }
        Map<Long, UserDTO> users = userIds.isEmpty() ? Map.of() : userApi.list(userIds);
        Map<Long, MaterialCategoryDO> categories = categoryService.byIds(categoryIds);
        boolean cost = canViewCost();
        Function<Long, String> userName = uid -> uid == null || !users.containsKey(uid) ? null : users.get(uid).realName();
        List<MaterialRespVO> result = new ArrayList<>(list.size());
        for (MaterialDO d : list) {
            MaterialCategoryDO c = categories.get(d.getCategoryId());
            result.add(new MaterialRespVO(d.getId(), d.getCode(), d.getName(), d.getNameEn(), d.getSpec(), d.getMaterialType(), d.getCategoryId(),
                    c == null ? null : c.getCode(), c == null ? null : c.getName(), d.getBaseUom(), d.getDrawingNo(), d.getRevision(), d.getBrand(),
                    d.getManufacturer(), d.getMpn(), d.getHsCode(), d.getUnitNetWeight(), d.getUnitGrossWeight(), d.getImageFileId(), d.getStatus(),
                    d.getRemark(), d.getSourceType(), d.getLeadTimeDays(), d.getSafetyStock(), d.getMaxStock(), d.getOrderPolicy(), d.getFixedLotQty(),
                    d.getPeriodDays(), d.getMoq(), d.getMpq(), d.getPlannerId(), userName.apply(d.getPlannerId()), d.getLowLevelCode(),
                    d.getBuyerId(), userName.apply(d.getBuyerId()), d.getPurchaseUom(), d.getOverReceivePct(),
                    d.getTracking(), d.getIssueRule(), d.getShelfLifeDays(), d.getMinRemainingLifePct(),
                    d.getIqcRequired(), d.getFqcRequired(), d.getOqcRequired(),
                    cost ? d.getStandardCost() : null, d.getSalesUom(), d.getPurchaseTaxRate(), d.getSalesTaxRate(),
                    d.getVersion(), d.getCreatedBy(), userName.apply(d.getCreatedBy()), d.getCreatedAt(), d.getUpdatedAt(), uoms));
        }
        return result;
    }

    /** 按 ID 批量取物料（BOM 等本模块服务使用） */
    public Map<Long, MaterialDO> byIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) return Map.of();
        return materialMapper.selectBatchIds(ids).stream().collect(Collectors.toMap(MaterialDO::getId, m -> m));
    }

    /** BOM 允许使用草稿子件（参数 eng.bom.allow-draft-component） */
    public boolean allowDraftComponent() {
        return paramApi.getBool(PARAM_ALLOW_DRAFT);
    }
}
