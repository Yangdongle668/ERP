package com.erp.module.crm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.common.exception.BizException;
import com.erp.common.exception.GlobalErrorCodes;
import com.erp.common.result.PageResult;
import com.erp.framework.excel.ExcelColumn;
import com.erp.framework.excel.ImportResult;
import com.erp.framework.excel.ImportRow;
import com.erp.module.crm.api.CrmErrorCodes;
import com.erp.module.crm.api.part.CustomerPartApi;
import com.erp.module.crm.api.part.CustomerPartDTO;
import com.erp.module.crm.api.part.CustomerPartReferenceChecker;
import com.erp.module.crm.controller.vo.PartVOs.PartQuery;
import com.erp.module.crm.controller.vo.PartVOs.PartRow;
import com.erp.module.crm.controller.vo.PartVOs.PartSave;
import com.erp.module.crm.dal.dataobject.CustomerDO;
import com.erp.module.crm.dal.dataobject.CustomerPartDO;
import com.erp.module.crm.dal.mapper.CustomerPartMapper;
import com.erp.module.engineering.api.material.MaterialApi;
import com.erp.module.engineering.api.material.MaterialDTO;
import com.erp.module.engineering.api.material.MaterialStatus;
import com.erp.module.engineering.api.material.MaterialType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 客户料号对照（需求 03-02）：同一客户下客户料号唯一；本厂物料为启用的半成品/成品 */
@Service("crmCustomerPartService")
public class CustomerPartService implements CustomerPartApi {

    public static final String BIZ_TYPE = "CRM_CUSTOMER_PART";
    static final Set<MaterialType> MATERIAL_TYPES = Set.of(MaterialType.SEMI_FINISHED, MaterialType.FINISHED);

    public static final List<ExcelColumn<Object>> IMPORT_COLUMNS = List.of(
            ExcelColumn.input("customerCode", "客户编码", true, null),
            ExcelColumn.input("customerPartNo", "客户料号", true, "同一客户内唯一"),
            ExcelColumn.input("customerPartName", "客户品名", false, null),
            ExcelColumn.input("customerPartSpec", "客户规格", false, null),
            ExcelColumn.input("customerRevision", "客户版本", false, null),
            ExcelColumn.input("materialCode", "本厂物料编码", true, "启用的半成品或成品"));

    private final CustomerPartMapper mapper;
    private final CustomerService customerService;
    private final MaterialApi materialApi;
    private final CrmSupport support;
    private final List<CustomerPartReferenceChecker> referenceCheckers;

    public CustomerPartService(CustomerPartMapper mapper, CustomerService customerService, MaterialApi materialApi, CrmSupport support,
                               List<CustomerPartReferenceChecker> referenceCheckers) {
        this.mapper = mapper;
        this.customerService = customerService;
        this.materialApi = materialApi;
        this.support = support;
        this.referenceCheckers = referenceCheckers;
    }

    public PageResult<PartRow> page(PartQuery q) {
        LambdaQueryWrapper<CustomerPartDO> w = new LambdaQueryWrapper<CustomerPartDO>()
                .eq(q.getCustomerId() != null, CustomerPartDO::getCustomerId, q.getCustomerId())
                .likeRight(StringUtils.hasText(q.getCustomerPartNo()), CustomerPartDO::getCustomerPartNo,
                        q.getCustomerPartNo() == null ? null : q.getCustomerPartNo().trim())
                .eq(q.getMaterialId() != null, CustomerPartDO::getMaterialId, q.getMaterialId())
                .eq(StringUtils.hasText(q.getStatus()), CustomerPartDO::getPartStatus, q.getStatus());
        String scope = CrmScope.visibleCustomerIds();
        if (scope != null) w.inSql(CustomerPartDO::getCustomerId, scope);
        w.orderByAsc(CustomerPartDO::getCustomerId).orderByAsc(CustomerPartDO::getCustomerPartNo);
        PageResult<CustomerPartDO> page = mapper.selectPage(q, w);
        return new PageResult<>(rows(page.list()), page.total());
    }

    private List<PartRow> rows(List<CustomerPartDO> list) {
        Map<Long, CustomerDO> customers = customerService.byIds(list.stream().map(CustomerPartDO::getCustomerId).toList());
        Map<Long, MaterialDTO> materials = materials(list.stream().map(CustomerPartDO::getMaterialId).collect(Collectors.toSet()));
        return list.stream().map(p -> {
            CustomerDO c = customers.get(p.getCustomerId());
            MaterialDTO m = materials.get(p.getMaterialId());
            return new PartRow(p.getId(), p.getCustomerId(), c == null ? null : c.getCode(), c == null ? null : c.getShortName(), p.getCustomerPartNo(),
                    p.getCustomerPartName(), p.getCustomerPartSpec(), p.getCustomerRevision(), p.getMaterialId(), m == null ? null : m.code(),
                    m == null ? null : m.name(), m == null ? null : m.spec(), p.getPartStatus(), p.getRemark(), p.getUpdatedAt(), p.getVersion());
        }).toList();
    }

    private Map<Long, MaterialDTO> materials(Set<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        return materialApi.getMaterials(ids).stream().collect(Collectors.toMap(MaterialDTO::id, Function.identity(), (a, b) -> a));
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(PartSave req) {
        CustomerPartDO p = new CustomerPartDO();
        p.setPartStatus("ENABLED");
        fill(p, req);
        mapper.insert(p);
        return p.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, PartSave req) {
        CustomerPartDO p = getVisible(id);
        if (req.version() != null) p.setVersion(req.version());
        fill(p, req);
        mapper.updateByIdOrFail(p);
    }

    /** R01：同一客户下客户料号唯一 */
    private void fill(CustomerPartDO p, PartSave req) {
        CustomerDO c = customerService.getVisible(req.customerId());
        String no = req.customerPartNo().trim();
        CustomerPartDO same = mapper.selectOne(new LambdaQueryWrapper<CustomerPartDO>().eq(CustomerPartDO::getCustomerId, c.getId())
                .eq(CustomerPartDO::getCustomerPartNo, no).ne(p.getId() != null, CustomerPartDO::getId, p.getId()).last("LIMIT 1"));
        if (same != null) {
            String code = materialApi.getMaterial(same.getMaterialId()).map(MaterialDTO::code).orElse(String.valueOf(same.getMaterialId()));
            throw BizException.of(CrmErrorCodes.PART_DUPLICATE, no, code);
        }
        if (!req.materialId().equals(p.getMaterialId())) {
            MaterialDTO m = materialApi.getMaterial(req.materialId()).orElse(null);
            if (m == null || m.status() != MaterialStatus.ENABLED || !MATERIAL_TYPES.contains(m.materialType())) {
                throw new BizException(CrmErrorCodes.PART_MATERIAL_INVALID);
            }
        }
        p.setCustomerId(c.getId());
        p.setCustomerPartNo(no);
        p.setCustomerPartName(CrmSupport.trim(req.customerPartName()));
        p.setCustomerPartSpec(CrmSupport.trim(req.customerPartSpec()));
        p.setCustomerRevision(CrmSupport.trim(req.customerRevision()));
        p.setMaterialId(req.materialId());
        p.setRemark(CrmSupport.trim(req.remark()));
    }

    @Transactional(rollbackFor = Exception.class)
    public void setStatus(Long id, boolean enabled) {
        CustomerPartDO p = getVisible(id);
        p.setPartStatus(enabled ? "ENABLED" : "DISABLED");
        mapper.updateByIdOrFail(p);
    }

    /** R04：已被订单引用的对照只能停用 */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        CustomerPartDO p = getVisible(id);
        if (referenceCheckers.stream().anyMatch(c -> c.isUsed(id))) throw new BizException(CrmErrorCodes.PART_USED);
        mapper.deleteById(p.getId());
    }

    public CustomerPartDO getVisible(Long id) {
        CustomerPartDO p = id == null ? null : mapper.selectById(id);
        if (p == null) throw new BizException(CrmErrorCodes.PART_NOT_EXISTS);
        customerService.getVisible(p.getCustomerId());
        return p;
    }

    // ==================== 导入 ====================

    public Map<Integer, String> check(List<ImportRow> rows) {
        Map<Integer, String> actions = new HashMap<>();
        Set<String> seen = new HashSet<>();
        for (ImportRow r : rows) {
            CustomerDO c = r.get("customerCode") == null ? null : customerService.findByCode(r.get("customerCode")).orElse(null);
            if (c == null) r.error("客户编码不存在");
            String no = r.get("customerPartNo");
            if (no == null) r.error("客户料号不能为空");
            if (c != null && no != null) {
                if (!seen.add(c.getId() + "|" + no)) r.error("文件中客户料号重复");
                else if (mapper.selectCount(new LambdaQueryWrapper<CustomerPartDO>().eq(CustomerPartDO::getCustomerId, c.getId())
                        .eq(CustomerPartDO::getCustomerPartNo, no)) > 0) r.error("客户料号已存在");
            }
            MaterialDTO m = r.get("materialCode") == null ? null : materialApi.search(r.get("materialCode"), MATERIAL_TYPES, 20).stream()
                    .filter(x -> x.code().equalsIgnoreCase(r.get("materialCode"))).findFirst().orElse(null);
            if (m == null || m.status() != MaterialStatus.ENABLED) r.error("本厂物料编码不存在或不是启用的半成品/成品");
            if (!r.hasError()) actions.put(r.rowNo(), "CREATE");
        }
        return actions;
    }

    public ImportResult doImport(List<ImportRow> rows) {
        int ok = 0;
        List<ImportResult.Error> errors = new ArrayList<>();
        for (ImportRow r : rows) {
            try {
                CustomerDO c = customerService.findByCode(r.get("customerCode"))
                        .orElseThrow(() -> BizException.of(GlobalErrorCodes.DATA_NOT_EXISTS, "客户"));
                MaterialDTO m = materialApi.search(r.get("materialCode"), MATERIAL_TYPES, 20).stream()
                        .filter(x -> x.code().equalsIgnoreCase(r.get("materialCode"))).findFirst()
                        .orElseThrow(() -> BizException.of(GlobalErrorCodes.DATA_NOT_EXISTS, "物料"));
                create(new PartSave(c.getId(), r.get("customerPartNo"), r.get("customerPartName"), r.get("customerPartSpec"),
                        r.get("customerRevision"), m.id(), null, null));
                ok++;
            } catch (BizException e) {
                errors.add(new ImportResult.Error(r.rowNo(), e.getMessage()));
            }
        }
        return new ImportResult(ok, errors.size(), errors);
    }

    // ==================== CustomerPartApi ====================

    static CustomerPartDTO toDto(CustomerPartDO p) {
        return new CustomerPartDTO(p.getId(), p.getCustomerId(), p.getCustomerPartNo(), p.getCustomerPartName(), p.getCustomerPartSpec(),
                p.getCustomerRevision(), p.getMaterialId());
    }

    @Override
    public Optional<CustomerPartDTO> toMaterial(Long customerId, String customerPartNo) {
        if (customerId == null || !StringUtils.hasText(customerPartNo)) return Optional.empty();
        return Optional.ofNullable(mapper.selectOne(new LambdaQueryWrapper<CustomerPartDO>().eq(CustomerPartDO::getCustomerId, customerId)
                .eq(CustomerPartDO::getCustomerPartNo, customerPartNo.trim()).eq(CustomerPartDO::getPartStatus, "ENABLED").last("LIMIT 1")))
                .map(CustomerPartService::toDto);
    }

    @Override
    public Optional<CustomerPartDTO> toCustomerPart(Long customerId, Long materialId) {
        if (customerId == null || materialId == null) return Optional.empty();
        return Optional.ofNullable(mapper.selectOne(new LambdaQueryWrapper<CustomerPartDO>().eq(CustomerPartDO::getCustomerId, customerId)
                .eq(CustomerPartDO::getMaterialId, materialId).eq(CustomerPartDO::getPartStatus, "ENABLED")
                .orderByDesc(CustomerPartDO::getUpdatedAt).orderByDesc(CustomerPartDO::getId).last("LIMIT 1")))
                .map(CustomerPartService::toDto);
    }
}
