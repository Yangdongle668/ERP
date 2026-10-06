package com.erp.module.asset.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.common.exception.BizException;
import com.erp.common.result.PageResult;
import com.erp.common.statemachine.StateMachine;
import com.erp.module.asset.api.AssetErrorCodes;
import com.erp.module.asset.config.AssetModuleConfig;
import com.erp.module.asset.controller.vo.AssetVOs.AssetQuery;
import com.erp.module.asset.controller.vo.AssetVOs.AssetRow;
import com.erp.module.asset.controller.vo.AssetVOs.AssetSave;
import com.erp.module.asset.controller.vo.AssetVOs.CodePreview;
import com.erp.module.asset.controller.vo.AssetVOs.ScrapReq;
import com.erp.module.asset.dal.dataobject.AssetDO;
import com.erp.module.asset.dal.mapper.AssetMapper;
import com.erp.module.system.api.coderule.CodeRuleApi;
import com.erp.module.system.api.dict.DictApi;
import com.erp.module.system.api.log.DocLogApi;
import com.erp.module.system.api.org.OrgApi;
import com.erp.module.system.api.org.OrgDTO;
import com.erp.module.system.api.user.UserApi;
import com.erp.module.system.api.user.UserDTO;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 固定资产台账（需求 15-固定资产）。资产编码按《编码规则管理制度》5.4：
 * {@code LD{公司}-{分类}-{名称缩写}-{年月}-{流水}}，如 {@code LD1-PD-CPJ-264-001}（26 年 4 月购买的冲片机 001 号，生产专用设备）。
 * 公司取工厂代码末位（11 广东蓝电 → 1，10 东莞蓝电 → 0）；月份 1～9，10/11/12 月用 A/B/C；流水号按前缀独立计数，报废、删除的编码不复用。
 */
@Service
public class AssetService {

    /** 资产状态 */
    public enum State implements StateMachine.Labeled {
        IN_USE("在用"), IDLE("闲置"), REPAIRING("维修中"), SCRAPPED("已报废");

        private final String label;

        State(String label) {
            this.label = label;
        }

        @Override
        public String label() {
            return label;
        }
    }

    public enum Op implements StateMachine.Labeled {
        USE("启用"), IDLE("闲置"), REPAIR("送修"), REPAIR_END("修复"), SCRAP("报废");

        private final String label;

        Op(String label) {
            this.label = label;
        }

        @Override
        public String label() {
            return label;
        }
    }

    /** R04：报废后不可恢复 */
    static final StateMachine<State, Op> MACHINE = StateMachine.builder(State.class, Op.class)
            .transition(State.IN_USE, Op.IDLE, State.IDLE)
            .transition(State.IDLE, Op.USE, State.IN_USE)
            .transition(State.IN_USE, Op.REPAIR, State.REPAIRING)
            .transition(State.IDLE, Op.REPAIR, State.REPAIRING)
            .transition(State.REPAIRING, Op.REPAIR_END, State.IN_USE)
            .transition(State.IN_USE, Op.SCRAP, State.SCRAPPED)
            .transition(State.IDLE, Op.SCRAP, State.SCRAPPED)
            .transition(State.REPAIRING, Op.SCRAP, State.SCRAPPED)
            .build();

    private final AssetMapper mapper;
    private final CodeRuleApi codeRuleApi;
    private final DictApi dictApi;
    private final OrgApi orgApi;
    private final UserApi userApi;
    private final DocLogApi docLogApi;

    public AssetService(AssetMapper mapper, CodeRuleApi codeRuleApi, DictApi dictApi, OrgApi orgApi, UserApi userApi, DocLogApi docLogApi) {
        this.mapper = mapper;
        this.codeRuleApi = codeRuleApi;
        this.dictApi = dictApi;
        this.orgApi = orgApi;
        this.userApi = userApi;
        this.docLogApi = docLogApi;
    }

    // ==================== 编码 ====================

    /** 名称缩写：大写，不足 3 位用 X 补位（R01） */
    static String abbr(String s) {
        String a = s.trim().toUpperCase(Locale.ROOT);
        return a.length() >= 3 ? a.substring(0, 3) : a + "X".repeat(3 - a.length());
    }

    /** 年月：年两位 + 月一位（10/11/12 月为 A/B/C） */
    static String yearMonth(LocalDate d) {
        int m = d.getMonthValue();
        return String.format("%02d", d.getYear() % 100) + (m <= 9 ? String.valueOf(m) : String.valueOf((char) ('A' + m - 10)));
    }

    static String prefix(String companyNo, String assetClass, String nameAbbr, LocalDate purchaseDate) {
        return "LD" + companyNo.charAt(companyNo.length() - 1) + "-" + assetClass + "-" + abbr(nameAbbr) + "-" + yearMonth(purchaseDate) + "-";
    }

    public CodePreview preview(String companyNo, String assetClass, String nameAbbr, LocalDate purchaseDate) {
        if (!StringUtils.hasText(companyNo) || !StringUtils.hasText(assetClass) || !StringUtils.hasText(nameAbbr) || purchaseDate == null) {
            return new CodePreview(null, null);
        }
        String p = prefix(companyNo.trim(), assetClass.trim().toUpperCase(Locale.ROOT), nameAbbr, purchaseDate);
        return new CodePreview(p, p + "001");
    }

    // ==================== 查询 ====================

    public PageResult<AssetRow> page(AssetQuery q) {
        String k = StringUtils.hasText(q.getKeyword()) ? q.getKeyword().trim() : null;
        LambdaQueryWrapper<AssetDO> w = new LambdaQueryWrapper<AssetDO>()
                .and(k != null, x -> x.likeRight(AssetDO::getCode, k.toUpperCase(Locale.ROOT)).or().like(AssetDO::getName, k).or().like(AssetDO::getSpec, k))
                .eq(StringUtils.hasText(q.getAssetClass()), AssetDO::getAssetClass, q.getAssetClass())
                .eq(StringUtils.hasText(q.getCompanyNo()), AssetDO::getCompanyNo, q.getCompanyNo())
                .eq(StringUtils.hasText(q.getAssetStatus()), AssetDO::getAssetStatus, q.getAssetStatus())
                .eq(q.getDeptId() != null, AssetDO::getDeptId, q.getDeptId())
                .orderByDesc(AssetDO::getPurchaseDate).orderByAsc(AssetDO::getCode);
        PageResult<AssetDO> page = mapper.selectPage(q, w);
        return new PageResult<>(toRows(page.list()), page.total());
    }

    public AssetRow get(Long id) {
        return toRows(List.of(getOrThrow(id))).get(0);
    }

    private AssetDO getOrThrow(Long id) {
        AssetDO a = id == null ? null : mapper.selectById(id);
        if (a == null) throw new BizException(AssetErrorCodes.ASSET_NOT_EXISTS);
        return a;
    }

    private List<AssetRow> toRows(List<AssetDO> list) {
        Set<Long> depts = new HashSet<>();
        Set<Long> users = new HashSet<>();
        list.forEach(a -> {
            if (a.getDeptId() != null) depts.add(a.getDeptId());
            if (a.getCustodianId() != null) users.add(a.getCustodianId());
            if (a.getCreatedBy() != null) users.add(a.getCreatedBy());
        });
        Map<Long, OrgDTO> orgs = depts.isEmpty() ? Map.of() : orgApi.list(depts);
        Map<Long, UserDTO> us = users.isEmpty() ? Map.of() : userApi.list(users);
        return list.stream().map(a -> new AssetRow(a.getId(), a.getCode(), a.getCompanyNo(), a.getAssetClass(), a.getName(), a.getNameAbbr(),
                a.getSpec(), a.getPurchaseDate(), a.getDeptId(), a.getDeptId() == null || !orgs.containsKey(a.getDeptId()) ? null : orgs.get(a.getDeptId()).name(),
                a.getCustodianId(), name(us, a.getCustodianId()), a.getLocation(), a.getSupplierName(), a.getCustomerName(), a.getOriginalValue(),
                a.getUsefulLifeMonths(), a.getAssetStatus(), a.getScrappedDate(), a.getScrapReason(), a.getRemark(), name(us, a.getCreatedBy()),
                a.getCreatedAt(), a.getVersion())).toList();
    }

    private static String name(Map<Long, UserDTO> users, Long id) {
        return id == null || !users.containsKey(id) ? null : users.get(id).realName();
    }

    // ==================== 维护 ====================

    @Transactional(rollbackFor = Exception.class)
    public Long create(AssetSave req) {
        AssetDO a = new AssetDO();
        a.setCompanyNo(req.companyNo().trim());
        a.setAssetClass(req.assetClass().trim().toUpperCase(Locale.ROOT));
        a.setNameAbbr(abbr(req.nameAbbr()));
        a.setPurchaseDate(req.purchaseDate());
        dictApi.validate(AssetModuleConfig.DICT_FACTORY, a.getCompanyNo(), "所属公司");
        dictApi.validate(AssetModuleConfig.DICT_CLASS, a.getAssetClass(), "资产分类");
        fill(a, req);
        String prefix = prefix(a.getCompanyNo(), a.getAssetClass(), a.getNameAbbr(), a.getPurchaseDate());
        String code = codeRuleApi.nextCode(AssetModuleConfig.CODE_RULE, Map.of("asset", prefix), 3);
        // 流水号为 001～999：超过时不能生成更长的编码
        if (code.length() > prefix.length() + 3) throw BizException.of(AssetErrorCodes.ASSET_SEQ_OVERFLOW, prefix);
        a.setCode(code);
        a.setAssetStatus(State.IN_USE.name());
        try {
            mapper.insert(a);
        } catch (DuplicateKeyException e) {
            throw BizException.of(AssetErrorCodes.ASSET_CODE_DUPLICATE, code);
        }
        docLogApi.record(AssetModuleConfig.BIZ_TYPE, a.getId(), code, "CREATE", "新建", null, a.getAssetStatus(), null);
        return a.getId();
    }

    /** R02：编码组成字段（公司、分类、缩写、购置日期）生成后不能修改，保证编码与资产属性一致 */
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, AssetSave req) {
        AssetDO a = getOrThrow(id);
        if (State.SCRAPPED.name().equals(a.getAssetStatus())) throw new BizException(AssetErrorCodes.ASSET_SCRAPPED);
        boolean codeFieldsChanged = !Objects.equals(a.getCompanyNo(), req.companyNo().trim())
                || !Objects.equals(a.getAssetClass(), req.assetClass().trim().toUpperCase(Locale.ROOT))
                || !Objects.equals(a.getNameAbbr(), abbr(req.nameAbbr()))
                || !Objects.equals(yearMonth(a.getPurchaseDate()), yearMonth(req.purchaseDate()));
        if (codeFieldsChanged) throw new BizException(AssetErrorCodes.ASSET_CODE_LOCKED);
        a.setPurchaseDate(req.purchaseDate());
        fill(a, req);
        if (req.version() != null) a.setVersion(req.version());
        mapper.updateByIdOrFail(a);
    }

    private void fill(AssetDO a, AssetSave req) {
        a.setName(req.name().trim());
        a.setSpec(trim(req.spec()));
        a.setDeptId(req.deptId());
        a.setCustodianId(req.custodianId());
        a.setLocation(trim(req.location()));
        a.setSupplierName(trim(req.supplierName()));
        a.setCustomerName(trim(req.customerName()));
        // R03：客户资产必须填写所属客户
        if ("CU".equals(a.getAssetClass()) && a.getCustomerName() == null) {
            throw BizException.of(AssetErrorCodes.ASSET_FIELD_INVALID, "客户资产请填写所属客户");
        }
        a.setOriginalValue(req.originalValue());
        a.setUsefulLifeMonths(req.usefulLifeMonths());
        a.setRemark(trim(req.remark()));
    }

    @Transactional(rollbackFor = Exception.class)
    public void changeStatus(Long id, String opName, String reason) {
        Op op;
        try {
            op = Op.valueOf(opName);
        } catch (IllegalArgumentException e) {
            throw BizException.of(AssetErrorCodes.ASSET_FIELD_INVALID, "不支持的操作：" + opName);
        }
        if (op == Op.SCRAP) throw BizException.of(AssetErrorCodes.ASSET_FIELD_INVALID, "报废请使用报废功能");
        AssetDO a = getOrThrow(id);
        String from = transit(a, op);
        mapper.updateByIdOrFail(a);
        docLogApi.record(AssetModuleConfig.BIZ_TYPE, a.getId(), a.getCode(), op.name(), op.label(), from, a.getAssetStatus(), trim(reason));
    }

    @Transactional(rollbackFor = Exception.class)
    public void scrap(Long id, ScrapReq req) {
        AssetDO a = getOrThrow(id);
        String from = transit(a, Op.SCRAP);
        a.setScrappedDate(req.scrappedDate());
        a.setScrapReason(req.reason().trim());
        mapper.updateByIdOrFail(a);
        docLogApi.record(AssetModuleConfig.BIZ_TYPE, a.getId(), a.getCode(), Op.SCRAP.name(), Op.SCRAP.label(), from, a.getAssetStatus(), a.getScrapReason());
    }

    /** 删除（录错时）：编码不复用（流水号只增不减） */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        AssetDO a = getOrThrow(id);
        mapper.deleteById(a.getId());
        docLogApi.record(AssetModuleConfig.BIZ_TYPE, a.getId(), a.getCode(), "DELETE", "删除", a.getAssetStatus(), null, null);
    }

    private static String transit(AssetDO a, Op op) {
        State from = State.valueOf(a.getAssetStatus());
        if (!MACHINE.canFire(from, op)) throw BizException.of(AssetErrorCodes.ASSET_STATUS, a.getCode(), from.label(), op.label());
        a.setAssetStatus(MACHINE.fire(from, op).name());
        return from.name();
    }

    private static String trim(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }
}
