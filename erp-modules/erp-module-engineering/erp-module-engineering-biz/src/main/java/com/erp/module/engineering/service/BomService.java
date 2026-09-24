package com.erp.module.engineering.service;

import com.erp.common.enums.DocAction;
import com.erp.common.enums.DocStatus;
import com.erp.common.exception.BizException;
import com.erp.common.statemachine.DocStateMachines;
import com.erp.framework.event.DomainEventPublisher;
import com.erp.framework.security.SecurityUtils;
import com.erp.module.engineering.api.EngineeringErrorCodes;
import com.erp.module.engineering.api.bom.BomApprovedEvent;
import com.erp.module.engineering.api.bom.BomDefaultChangedEvent;
import com.erp.module.engineering.api.bom.BomReferenceChecker;
import com.erp.module.engineering.api.bom.IssueMethod;
import com.erp.module.engineering.api.material.MaterialStatus;
import com.erp.module.engineering.api.material.MaterialType;
import com.erp.module.engineering.controller.vo.BomVOs.BomSave;
import com.erp.module.engineering.controller.vo.BomVOs.LineSave;
import com.erp.module.engineering.controller.vo.BomVOs.SaveResult;
import com.erp.module.engineering.controller.vo.BomVOs.SubstituteSave;
import com.erp.module.engineering.dal.dataobject.BomDO;
import com.erp.module.engineering.dal.dataobject.BomLineDO;
import com.erp.module.engineering.dal.dataobject.BomSubstituteDO;
import com.erp.module.engineering.dal.dataobject.MaterialDO;
import com.erp.module.engineering.dal.mapper.BomLineMapper;
import com.erp.module.engineering.dal.mapper.BomMapper;
import com.erp.module.engineering.dal.mapper.BomSubstituteMapper;
import com.erp.module.system.api.file.FileApi;
import com.erp.module.system.api.log.DocLogApi;
import com.erp.module.system.api.param.ParamApi;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * BOM 维护与状态流转（需求 05-03 第 5、6 节）。
 * 草稿 → 提交（审批流 ENG_BOM；未配置时直接审核）→ 已审核；已审核非默认版本可反审核、设为默认、停用（CLOSED）。
 */
@Service
public class BomService {

    public static final String BIZ_TYPE = "ENG_BOM";
    private static final Set<MaterialType> PARENT_TYPES = Set.of(MaterialType.SEMI_FINISHED, MaterialType.FINISHED, MaterialType.PHANTOM);
    private static final Pattern RANGE = Pattern.compile("^([A-Za-z]*)(\\d+)-([A-Za-z]*)(\\d+)$");

    private final BomMapper bomMapper;
    private final BomLineMapper lineMapper;
    private final BomSubstituteMapper substituteMapper;
    private final BomQueryService queryService;
    private final MaterialService materialService;
    private final ParamApi paramApi;
    private final FileApi fileApi;
    private final WorkflowApi workflowApi;
    private final DocLogApi docLogApi;
    private final DomainEventPublisher eventPublisher;
    private final List<BomReferenceChecker> referenceCheckers;

    public BomService(BomMapper bomMapper, BomLineMapper lineMapper, BomSubstituteMapper substituteMapper, BomQueryService queryService,
                      MaterialService materialService, ParamApi paramApi, FileApi fileApi, WorkflowApi workflowApi, DocLogApi docLogApi,
                      DomainEventPublisher eventPublisher, List<BomReferenceChecker> referenceCheckers) {
        this.bomMapper = bomMapper;
        this.lineMapper = lineMapper;
        this.substituteMapper = substituteMapper;
        this.queryService = queryService;
        this.materialService = materialService;
        this.paramApi = paramApi;
        this.fileApi = fileApi;
        this.workflowApi = workflowApi;
        this.docLogApi = docLogApi;
        this.eventPublisher = eventPublisher;
        this.referenceCheckers = referenceCheckers;
    }

    // ==================== 新建 / 修改 ====================

    @Transactional(rollbackFor = Exception.class)
    public SaveResult create(BomSave req) {
        MaterialDO parent = checkParent(req.materialId());
        List<String> warnings = checkLines(parent, req.baseQty(), req.lines(), null);
        int version = queryService.nextVersion(parent.getId());
        BomDO b = new BomDO();
        b.setDocNo(parent.getCode() + "-V" + version);
        b.setDocDate(LocalDate.now());
        b.setStatus(DocStatus.DRAFT);
        b.setOwnerId(SecurityUtils.getLoginUserIdOrNull());
        b.setMaterialId(parent.getId());
        b.setBomVersion(version);
        b.setIsDefault(false);
        fillHeader(b, req);
        bomMapper.insert(b);
        saveLines(b, parent, req.lines());
        if (req.fileIds() != null && !req.fileIds().isEmpty()) fileApi.bind(req.fileIds(), BIZ_TYPE, b.getId());
        return new SaveResult(b.getId(), warnings);
    }

    @Transactional(rollbackFor = Exception.class)
    public SaveResult update(Long id, BomSave req) {
        BomDO b = queryService.getOrThrow(id);
        if (b.getStatus() != DocStatus.DRAFT) throw new BizException(EngineeringErrorCodes.BOM_NOT_EDITABLE);
        MaterialDO parent = materialService.getOrThrow(b.getMaterialId());
        if (b.getCopiedFromId() != null && !StringUtils.hasText(req.description())) throw new BizException(EngineeringErrorCodes.BOM_DESCRIPTION_REQUIRED);
        List<String> warnings = checkLines(parent, req.baseQty(), req.lines(), id);
        fillHeader(b, req);
        if (req.rowVersion() != null) b.setVersion(req.rowVersion());
        bomMapper.updateByIdOrFail(b);
        saveLines(b, parent, req.lines());
        if (req.fileIds() != null && !req.fileIds().isEmpty()) fileApi.bind(req.fileIds(), BIZ_TYPE, b.getId());
        return new SaveResult(b.getId(), warnings);
    }

    private static void fillHeader(BomDO b, BomSave req) {
        b.setBaseQty(req.baseQty());
        b.setDescription(StringUtils.hasText(req.description()) ? req.description().trim() : null);
        b.setRemark(StringUtils.hasText(req.remark()) ? req.remark().trim() : null);
        b.setLineCount(req.lines() == null ? 0 : req.lines().size());
    }

    /** R01：父件类型为半成品、成品或虚拟件，且为草稿或启用 */
    private MaterialDO checkParent(Long materialId) {
        MaterialDO parent = materialService.getOrThrow(materialId);
        if (!PARENT_TYPES.contains(parent.getMaterialType())) throw new BizException(EngineeringErrorCodes.BOM_PARENT_TYPE);
        if (parent.getStatus() == MaterialStatus.DISABLED) throw BizException.of(EngineeringErrorCodes.BOM_PARENT_NOT_USABLE, parent.getCode());
        return parent;
    }

    /**
     * 保存时的行校验（R02、R03、R05）：子件不能是父件、不能重复；子件为启用（参数允许时可为草稿）；
     * 替代料不能是主料本身或本 BOM 的其他主料；循环引用。返回位号个数不一致的警告。
     */
    private List<String> checkLines(MaterialDO parent, BigDecimal baseQty, List<LineSave> lines, Long bomId) {
        List<LineSave> list = lines == null ? List.of() : lines;
        Set<Long> ids = new HashSet<>();
        list.forEach(l -> {
            ids.add(l.componentId());
            if (l.substitutes() != null) l.substitutes().forEach(s -> ids.add(s.substituteId()));
        });
        Map<Long, MaterialDO> materials = materialService.byIds(ids);
        Set<Long> mains = new HashSet<>();
        boolean allowDraft = materialService.allowDraftComponent();
        List<String> warnings = new ArrayList<>();
        int n = 0;
        for (LineSave l : list) {
            n++;
            MaterialDO c = materials.get(l.componentId());
            if (c == null) throw new BizException(EngineeringErrorCodes.MATERIAL_NOT_EXISTS);
            if (c.getId().equals(parent.getId())) throw new BizException(EngineeringErrorCodes.BOM_COMPONENT_SELF);
            if (!mains.add(c.getId())) throw BizException.of(EngineeringErrorCodes.BOM_COMPONENT_DUPLICATE, c.getCode());
            if (!usable(c, allowDraft)) throw BizException.of(EngineeringErrorCodes.BOM_COMPONENT_DRAFT_NOT_ALLOWED, c.getCode());
            Integer count = positionCount(l.positionNo());
            if (count != null && baseQty != null && baseQty.signum() > 0) {
                BigDecimal perParent = l.qtyPer().divide(baseQty, 4, RoundingMode.HALF_UP).stripTrailingZeros();
                if (perParent.compareTo(BigDecimal.valueOf(count)) != 0) {
                    warnings.add(String.format("第 %d 行位号个数 %d 与用量 %s 不一致", n, count, perParent.toPlainString()));
                }
            }
        }
        n = 0;
        for (LineSave l : list) {
            n++;
            if (l.substitutes() == null) continue;
            Set<Long> seen = new HashSet<>();
            for (SubstituteSave s : l.substitutes()) {
                MaterialDO sm = materials.get(s.substituteId());
                if (sm == null) throw new BizException(EngineeringErrorCodes.MATERIAL_NOT_EXISTS);
                if (mains.contains(sm.getId()) || sm.getId().equals(parent.getId()) || !seen.add(sm.getId())) {
                    throw BizException.of(EngineeringErrorCodes.BOM_SUBSTITUTE_INVALID, n);
                }
                if (!usable(sm, allowDraft)) throw BizException.of(EngineeringErrorCodes.BOM_COMPONENT_DRAFT_NOT_ALLOWED, sm.getCode());
            }
        }
        checkCycle(parent, mains, bomId);
        return warnings;
    }

    private static boolean usable(MaterialDO m, boolean allowDraft) {
        return m.getStatus() == MaterialStatus.ENABLED || (allowDraft && (m.getStatus() == MaterialStatus.DRAFT || m.getStatus() == MaterialStatus.PENDING));
    }

    private void checkCycle(MaterialDO parent, Set<Long> components, Long bomId) {
        List<Long> cycle = queryService.findCycle(parent.getId(), components, bomId);
        if (cycle.isEmpty()) return;
        Map<Long, MaterialDO> ms = materialService.byIds(cycle);
        String path = cycle.stream().map(id -> ms.containsKey(id) ? ms.get(id).getCode() : String.valueOf(id)).collect(Collectors.joining(" → "));
        throw BizException.of(EngineeringErrorCodes.BOM_CYCLE, path);
    }

    /** 位号个数：按逗号、空格分隔，R5-R8 视为 4 个；为空返回 null */
    static Integer positionCount(String positions) {
        if (!StringUtils.hasText(positions)) return null;
        int count = 0;
        for (String token : positions.trim().split("[,，;；\\s]+")) {
            if (token.isEmpty()) continue;
            Matcher m = RANGE.matcher(token);
            if (m.matches()) {
                long from = Long.parseLong(m.group(2));
                long to = Long.parseLong(m.group(4));
                count += to >= from ? (int) Math.min(to - from + 1, 100_000) : 1;
            } else {
                count++;
            }
        }
        return count;
    }

    private void saveLines(BomDO b, MaterialDO parent, List<LineSave> lines) {
        substituteMapper.deleteByBom(b.getId());
        lineMapper.deleteByBom(b.getId());
        if (lines == null) return;
        Map<Long, MaterialDO> materials = materialService.byIds(lines.stream().map(LineSave::componentId).toList());
        int no = 0;
        for (LineSave l : lines) {
            BomLineDO line = new BomLineDO();
            line.setBomId(b.getId());
            line.setLineNo(++no);
            line.setComponentId(l.componentId());
            line.setQtyPer(l.qtyPer());
            // BOM 统一按子件基本单位
            line.setUom(materials.get(l.componentId()).getBaseUom());
            line.setScrapRate(l.scrapRate() == null ? BigDecimal.ZERO : l.scrapRate());
            line.setPositionNo(StringUtils.hasText(l.positionNo()) ? l.positionNo().trim() : null);
            line.setIssueMethod(l.issueMethod() == null ? IssueMethod.PICK : l.issueMethod());
            line.setOperationSeq(l.operationSeq());
            line.setIsKey(Boolean.TRUE.equals(l.isKey()));
            line.setRemark(StringUtils.hasText(l.remark()) ? l.remark().trim() : null);
            lineMapper.insert(line);
            if (l.substitutes() == null) continue;
            int p = 0;
            for (SubstituteSave s : l.substitutes()) {
                BomSubstituteDO sub = new BomSubstituteDO();
                sub.setBomId(b.getId());
                sub.setBomLineId(line.getId());
                sub.setSubstituteId(s.substituteId());
                sub.setPriority(s.priority() == null ? ++p : s.priority());
                sub.setRatio(s.ratio() == null ? BigDecimal.ONE : s.ratio());
                sub.setRemark(StringUtils.hasText(s.remark()) ? s.remark().trim() : null);
                substituteMapper.insert(sub);
            }
        }
    }

    // ==================== 状态流转 ====================

    /** 提交（R02～R04、R06、R12）：发起审批；未配置审批流或全部自动通过时直接审核 */
    @Transactional(rollbackFor = Exception.class)
    public String submit(Long id) {
        BomDO b = queryService.getOrThrow(id);
        if (b.getStatus() != DocStatus.DRAFT) throw new BizException(EngineeringErrorCodes.BOM_NOT_EDITABLE);
        if (b.getCopiedFromId() != null && !StringUtils.hasText(b.getDescription())) throw new BizException(EngineeringErrorCodes.BOM_DESCRIPTION_REQUIRED);
        MaterialDO parent = materialService.getOrThrow(b.getMaterialId());
        checkReady(b, parent);
        fire(b, DocAction.SUBMIT, null);
        Map<String, Object> vars = new HashMap<>();
        vars.put("parentMaterialType", parent.getMaterialType().name());
        vars.put("lineCount", b.getLineCount());
        StartResult r = workflowApi.start(BIZ_TYPE, b.getId(), b.getDocNo(), "BOM " + b.getDocNo() + " " + parent.getName(),
                vars, Map.of(), SecurityUtils.getLoginUserIdOrNull());
        if (!r.isStarted()) approve(b);
        return b.getStatus().name();
    }

    /** 提交与审核前的完整性检查 */
    private void checkReady(BomDO b, MaterialDO parent) {
        List<BomLineDO> lines = lineMapper.selectByBom(b.getId());
        if (lines.isEmpty()) throw new BizException(EngineeringErrorCodes.BOM_NO_LINES);
        if (parent.getStatus() != MaterialStatus.ENABLED && parent.getStatus() != MaterialStatus.DRAFT && parent.getStatus() != MaterialStatus.PENDING) {
            throw BizException.of(EngineeringErrorCodes.BOM_PARENT_NOT_USABLE, parent.getCode());
        }
        List<BomSubstituteDO> subs = substituteMapper.selectByBoms(List.of(b.getId()));
        Set<Long> ids = new HashSet<>();
        lines.forEach(l -> ids.add(l.getComponentId()));
        subs.forEach(s -> ids.add(s.getSubstituteId()));
        Map<Long, MaterialDO> materials = materialService.byIds(ids);
        for (BomLineDO l : lines) {
            MaterialDO c = materials.get(l.getComponentId());
            if (c == null || c.getStatus() != MaterialStatus.ENABLED) {
                throw BizException.of(EngineeringErrorCodes.BOM_COMPONENT_NOT_ENABLED, c == null ? String.valueOf(l.getComponentId()) : c.getCode());
            }
            if (c.getMaterialType() == MaterialType.PHANTOM && bomMapper.selectDefault(c.getId()) == null) {
                throw BizException.of(EngineeringErrorCodes.BOM_PHANTOM_NO_BOM, c.getCode());
            }
        }
        for (BomSubstituteDO s : subs) {
            MaterialDO sm = materials.get(s.getSubstituteId());
            if (sm == null || sm.getStatus() != MaterialStatus.ENABLED) {
                throw BizException.of(EngineeringErrorCodes.BOM_SUBSTITUTE_NOT_ENABLED, sm == null ? String.valueOf(s.getSubstituteId()) : sm.getCode());
            }
        }
        Set<Long> components = lines.stream().map(BomLineDO::getComponentId).collect(Collectors.toSet());
        checkCycle(parent, components, b.getId());
        int max = paramApi.getInt(BomQueryService.PARAM_MAX_LEVEL);
        if (queryService.depth(components) > max) throw BizException.of(EngineeringErrorCodes.BOM_TOO_DEEP, max);
    }

    /** 审核通过：没有默认版本时自动设为默认；重算低位码；发布 BomApprovedEvent */
    private void approve(BomDO b) {
        fire(b, DocAction.APPROVE, null);
        if (bomMapper.selectDefault(b.getMaterialId()) == null) {
            b.setIsDefault(true);
            b.setEffectiveDate(LocalDate.now());
            bomMapper.updateByIdOrFail(b);
            eventPublisher.publish(new BomDefaultChangedEvent(b.getMaterialId(), null, b.getId()));
        }
        queryService.recomputeLowLevelCodes();
        eventPublisher.publish(new BomApprovedEvent(b.getId(), b.getMaterialId(), b.getBomVersion()));
    }

    @EventListener
    public void onApprovalCompleted(ApprovalCompletedEvent e) {
        if (!BIZ_TYPE.equals(e.getBizType())) return;
        BomDO b = queryService.getOrThrow(e.getBizId());
        if (b.getStatus() != DocStatus.PENDING_APPROVAL) return;
        switch (e.getResult()) {
            case APPROVED -> {
                checkReady(b, materialService.getOrThrow(b.getMaterialId()));
                approve(b);
            }
            case WITHDRAWN -> fire(b, DocAction.WITHDRAW, null);
            default -> fire(b, DocAction.REJECT, e.getComment());
        }
    }

    /** 反审核（R09）：默认版本、被生产订单使用的版本不能反审核 */
    @Transactional(rollbackFor = Exception.class)
    public void unapprove(Long id, String reason) {
        BomDO b = queryService.getOrThrow(id);
        if (b.getStatus() == DocStatus.APPROVED && Boolean.TRUE.equals(b.getIsDefault())) throw new BizException(EngineeringErrorCodes.BOM_DEFAULT_UNAPPROVE);
        if (referenceCheckers.stream().anyMatch(c -> c.isUsed(id))) throw new BizException(EngineeringErrorCodes.BOM_USED_UNAPPROVE);
        fire(b, DocAction.UNAPPROVE, reason);
    }

    /** 设为默认（R07）：原默认版本取消默认 */
    @Transactional(rollbackFor = Exception.class)
    public void setDefault(Long id) {
        BomDO b = queryService.getOrThrow(id);
        if (b.getStatus() != DocStatus.APPROVED) throw new BizException(EngineeringErrorCodes.BOM_SET_DEFAULT_STATUS);
        if (Boolean.TRUE.equals(b.getIsDefault())) return;
        BomDO old = bomMapper.selectDefault(b.getMaterialId());
        if (old != null) {
            old.setIsDefault(false);
            bomMapper.updateByIdOrFail(old);
        }
        b.setIsDefault(true);
        b.setEffectiveDate(LocalDate.now());
        bomMapper.updateByIdOrFail(b);
        docLogApi.record(BIZ_TYPE, b.getId(), b.getDocNo(), "SET_DEFAULT", "设为默认", b.getStatus().name(), b.getStatus().name(), null);
        queryService.recomputeLowLevelCodes();
        eventPublisher.publish(new BomDefaultChangedEvent(b.getMaterialId(), old == null ? null : old.getId(), b.getId()));
    }

    /** 停用（R10）：默认版本不能停用；停用后不能恢复 */
    @Transactional(rollbackFor = Exception.class)
    public void disable(Long id) {
        BomDO b = queryService.getOrThrow(id);
        if (b.getStatus() == DocStatus.APPROVED && Boolean.TRUE.equals(b.getIsDefault())) throw new BizException(EngineeringErrorCodes.BOM_DEFAULT_DISABLE);
        fire(b, DocAction.CLOSE, null);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        BomDO b = queryService.getOrThrow(id);
        if (b.getStatus() != DocStatus.DRAFT) throw new BizException(EngineeringErrorCodes.BOM_PENDING_NOT_DELETABLE);
        substituteMapper.deleteByBom(id);
        lineMapper.deleteByBom(id);
        bomMapper.deleteById(id);
        fileApi.deleteByBiz(BIZ_TYPE, id);
    }

    /** 新建版本：复制为新草稿（版本号 = 该父件最大版本 + 1），版本说明需在提交前填写 */
    @Transactional(rollbackFor = Exception.class)
    public Long newVersion(Long id) {
        BomDO src = queryService.getOrThrow(id);
        if (src.getStatus() != DocStatus.APPROVED && src.getStatus() != DocStatus.CLOSED) {
            throw BizException.of(com.erp.common.exception.GlobalErrorCodes.ILLEGAL_STATE_TRANSITION, src.getStatus().label(), "新建版本");
        }
        MaterialDO parent = materialService.getOrThrow(src.getMaterialId());
        int version = queryService.nextVersion(parent.getId());
        BomDO b = new BomDO();
        b.setDocNo(parent.getCode() + "-V" + version);
        b.setDocDate(LocalDate.now());
        b.setStatus(DocStatus.DRAFT);
        b.setOwnerId(SecurityUtils.getLoginUserIdOrNull());
        b.setMaterialId(parent.getId());
        b.setBomVersion(version);
        b.setIsDefault(false);
        b.setBaseQty(src.getBaseQty());
        b.setRemark(src.getRemark());
        b.setCopiedFromId(src.getId());
        b.setLineCount(src.getLineCount());
        bomMapper.insert(b);
        copyLines(src.getId(), b.getId());
        return b.getId();
    }

    /** 复制行与替代料（新建版本、从其他 BOM 复制） */
    private void copyLines(Long fromBomId, Long toBomId) {
        Map<Long, List<BomSubstituteDO>> subs = substituteMapper.selectByBoms(List.of(fromBomId)).stream()
                .collect(Collectors.groupingBy(BomSubstituteDO::getBomLineId, LinkedHashMap::new, Collectors.toList()));
        for (BomLineDO l : lineMapper.selectByBom(fromBomId)) {
            Long oldId = l.getId();
            l.setId(null);
            l.setVersion(null);
            l.setBomId(toBomId);
            lineMapper.insert(l);
            for (BomSubstituteDO s : subs.getOrDefault(oldId, List.of())) {
                s.setId(null);
                s.setVersion(null);
                s.setBomId(toBomId);
                s.setBomLineId(l.getId());
                substituteMapper.insert(s);
            }
        }
    }

    private void fire(BomDO b, DocAction action, String reason) {
        DocStatus old = b.getStatus();
        b.setStatus(DocStateMachines.STANDARD.fire(old, action));
        bomMapper.updateByIdOrFail(b);
        docLogApi.record(BIZ_TYPE, b.getId(), b.getDocNo(), action.name(), action == DocAction.CLOSE ? "停用" : action.label(),
                old.name(), b.getStatus().name(), reason);
    }

    // ==================== 打印 ====================

    /** 打印数据（与 PrintBizDefinition ENG_BOM 声明的变量一致） */
    public Map<String, Object> printData(Long id) {
        var d = queryService.detail(id);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("docNo", d.docNo());
        data.put("version", "V" + d.version());
        data.put("status", DocStatus.valueOf(d.status()) == DocStatus.CLOSED ? "停用" : DocStatus.valueOf(d.status()).label());
        data.put("isDefault", d.isDefault());
        data.put("effectiveDate", d.effectiveDate());
        data.put("baseQty", d.baseQty());
        data.put("description", d.description());
        data.put("remark", d.remark());
        data.put("material", Map.of("code", d.materialCode(), "name", d.materialName(), "spec", nz(d.materialSpec()), "uom", nz(d.uom())));
        List<Map<String, Object>> lines = new ArrayList<>();
        for (var l : d.lines()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("lineNo", l.lineNo());
            m.put("code", l.componentCode());
            m.put("name", l.componentName());
            m.put("spec", nz(l.componentSpec()));
            m.put("uom", l.uom());
            m.put("qtyPer", l.qtyPer());
            m.put("scrapRatePct", l.scrapRate() == null ? BigDecimal.ZERO : l.scrapRate().multiply(BigDecimal.valueOf(100)).stripTrailingZeros());
            m.put("positionNo", nz(l.positionNo()));
            m.put("issueMethod", l.issueMethod() == null ? "" : l.issueMethod().label());
            m.put("isKey", l.isKey());
            m.put("substitutes", l.substitutes().stream().map(s -> s.code() + " " + s.name()).collect(Collectors.joining("；")));
            m.put("remark", nz(l.remark()));
            lines.add(m);
        }
        data.put("lines", lines);
        data.put("lineCount", lines.size());
        return data;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
