package com.erp.module.engineering.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.common.exception.BizException;
import com.erp.common.result.PageResult;
import com.erp.common.statemachine.StateMachine;
import com.erp.framework.event.DomainEventPublisher;
import com.erp.framework.excel.ImportResult;
import com.erp.framework.excel.ImportRow;
import com.erp.module.engineering.api.EngineeringErrorCodes;
import com.erp.module.engineering.api.tooling.ToolingApi;
import com.erp.module.engineering.api.tooling.ToolingDTO;
import com.erp.module.engineering.api.tooling.ToolingLifeWarningEvent;
import com.erp.module.engineering.controller.vo.CertVOs.MaterialRef;
import com.erp.module.engineering.controller.vo.ToolingVOs.RecordReq;
import com.erp.module.engineering.controller.vo.ToolingVOs.RecordRow;
import com.erp.module.engineering.controller.vo.ToolingVOs.ToolingQuery;
import com.erp.module.engineering.controller.vo.ToolingVOs.ToolingRow;
import com.erp.module.engineering.controller.vo.ToolingVOs.ToolingSave;
import com.erp.module.engineering.dal.dataobject.MaterialDO;
import com.erp.module.engineering.dal.dataobject.ToolingDO;
import com.erp.module.engineering.dal.dataobject.ToolingMaterialDO;
import com.erp.module.engineering.dal.dataobject.ToolingRecordDO;
import com.erp.module.engineering.dal.mapper.ToolingMapper;
import com.erp.module.engineering.dal.mapper.ToolingMaterialMapper;
import com.erp.module.engineering.dal.mapper.ToolingRecordMapper;
import com.erp.module.system.api.file.FileApi;
import com.erp.module.system.api.notify.AlertRaisedEvent;
import com.erp.module.system.api.notify.NotifyApi;
import com.erp.module.system.api.param.ParamApi;
import com.erp.module.system.api.user.UserDTO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 工装台账（需求 05-08）：借还、保养、维修、报废、次数调整；报工累加使用次数、寿命预警与保养提醒。实现 ToolingApi。
 */
@Service
public class ToolingService implements ToolingApi {

    public static final String BIZ_TYPE = "ENG_TOOLING";
    static final String CODE_RULE = "ENG_TOOLING";
    static final String PARAM_WARN_PCT = "eng.tooling.life-warn-pct";

    /** 工装状态 */
    public enum State implements StateMachine.Labeled {
        IN_STOCK("在库"), IN_USE("使用中"), LENT("借出"), REPAIRING("维修中"), SCRAPPED("报废");

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
        LEND("借出"), RETURN("归还"), REPAIR_START("送修"), REPAIR_END("修复"), SCRAP("报废");

        private final String label;

        Op(String label) {
            this.label = label;
        }

        @Override
        public String label() {
            return label;
        }
    }

    /** R05：报废后不可恢复；维修中不能借出 */
    static final StateMachine<State, Op> MACHINE = StateMachine.builder(State.class, Op.class)
            .transition(State.IN_STOCK, Op.LEND, State.LENT)
            .transition(State.LENT, Op.RETURN, State.IN_STOCK)
            .transition(State.IN_USE, Op.RETURN, State.IN_STOCK)
            .transition(State.IN_STOCK, Op.REPAIR_START, State.REPAIRING)
            .transition(State.LENT, Op.REPAIR_START, State.REPAIRING)
            .transition(State.REPAIRING, Op.REPAIR_END, State.IN_STOCK)
            .transition(State.IN_STOCK, Op.SCRAP, State.SCRAPPED)
            .transition(State.IN_USE, Op.SCRAP, State.SCRAPPED)
            .transition(State.LENT, Op.SCRAP, State.SCRAPPED)
            .transition(State.REPAIRING, Op.SCRAP, State.SCRAPPED)
            .build();

    private final ToolingMapper mapper;
    private final ToolingMaterialMapper materialMapper;
    private final ToolingRecordMapper recordMapper;
    private final EngSupport support;
    private final ParamApi paramApi;
    private final NotifyApi notifyApi;
    private final FileApi fileApi;
    private final DomainEventPublisher eventPublisher;
    private final TransactionTemplate tx;

    public ToolingService(ToolingMapper mapper, ToolingMaterialMapper materialMapper, ToolingRecordMapper recordMapper, EngSupport support,
                          ParamApi paramApi, NotifyApi notifyApi, FileApi fileApi, DomainEventPublisher eventPublisher,
                          PlatformTransactionManager transactionManager) {
        this.mapper = mapper;
        this.materialMapper = materialMapper;
        this.recordMapper = recordMapper;
        this.support = support;
        this.paramApi = paramApi;
        this.notifyApi = notifyApi;
        this.fileApi = fileApi;
        this.eventPublisher = eventPublisher;
        this.tx = new TransactionTemplate(transactionManager);
    }

    // ==================== 台账 ====================

    @Transactional(rollbackFor = Exception.class)
    public Long create(ToolingSave req) {
        ToolingDO t = new ToolingDO();
        t.setToolingStatus(State.IN_STOCK.name());
        int initial = req.initialUsedCount() == null ? 0 : req.initialUsedCount();
        t.setUsedCount(initial);
        t.setLastMaintainCount(initial);
        t.setLifeWarned(false);
        fill(t, req, null);
        mapper.insert(t);
        saveMaterials(t.getId(), req.materialIds());
        if (initial > 0) record(t, "ADJUST", initial, null, null, "建档初始使用次数", null);
        return t.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, ToolingSave req) {
        ToolingDO t = getOrThrow(id);
        if (req.version() != null) t.setVersion(req.version());
        fill(t, req, id);
        mapper.updateByIdOrFail(t);
        saveMaterials(id, req.materialIds());
    }

    /** R01：编号唯一（为空按编码规则生成）；客户资产必须选择客户 */
    private void fill(ToolingDO t, ToolingSave req, Long id) {
        String code = StringUtils.hasText(req.code()) ? req.code().trim().toUpperCase() : (id == null ? support.nextNo(CODE_RULE) : t.getCode());
        if (mapper.selectCount(new LambdaQueryWrapper<ToolingDO>().eq(ToolingDO::getCode, code).ne(id != null, ToolingDO::getId, id)) > 0) {
            throw BizException.of(EngineeringErrorCodes.TOOLING_CODE_DUPLICATE, code);
        }
        String ownership = StringUtils.hasText(req.ownership()) ? req.ownership() : "OWN";
        if ("CUSTOMER".equals(ownership) && req.customerId() == null) throw new BizException(EngineeringErrorCodes.TOOLING_CUSTOMER_REQUIRED);
        t.setCode(code);
        t.setName(req.name().trim());
        t.setToolingType(req.toolingType());
        t.setSpec(EngSupport.trim(req.spec()));
        t.setOwnership(ownership);
        t.setCustomerId("CUSTOMER".equals(ownership) ? req.customerId() : null);
        t.setCavity(req.cavity() == null ? 1 : req.cavity());
        t.setDesignLife(req.designLife());
        t.setMaintainCycle(req.maintainCycle());
        t.setLocation(EngSupport.trim(req.location()));
        t.setSupplierName(EngSupport.trim(req.supplierName()));
        t.setPurchaseDate(req.purchaseDate());
        t.setPurchaseAmount(req.purchaseAmount());
        t.setAllowOverLife(Boolean.TRUE.equals(req.allowOverLife()));
        t.setOverLifeReason(Boolean.TRUE.equals(req.allowOverLife()) ? EngSupport.trim(req.overLifeReason()) : null);
        if (Boolean.TRUE.equals(req.allowOverLife()) && t.getOverLifeReason() == null) {
            throw BizException.of(EngineeringErrorCodes.TOOLING_FIELD_REQUIRED, "超寿命继续使用的原因");
        }
        t.setRemark(EngSupport.trim(req.remark()));
    }

    private void saveMaterials(Long toolingId, List<Long> materialIds) {
        materialMapper.deleteByParent(toolingId);
        if (materialIds == null) return;
        for (Long mid : new LinkedHashSet<>(materialIds)) {
            support.material(mid);
            ToolingMaterialDO x = new ToolingMaterialDO();
            x.setToolingId(toolingId);
            x.setMaterialId(mid);
            materialMapper.insert(x);
        }
    }

    /** R06：有使用记录的工装不能删除 */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        getOrThrow(id);
        if (recordMapper.selectCount(new LambdaQueryWrapper<ToolingRecordDO>().eq(ToolingRecordDO::getToolingId, id).eq(ToolingRecordDO::getRecordType, "USAGE")) > 0) {
            throw new BizException(EngineeringErrorCodes.TOOLING_HAS_USAGE);
        }
        materialMapper.deleteByParent(id);
        recordMapper.deleteByParent(id);
        mapper.deleteById(id);
        fileApi.deleteByBiz(BIZ_TYPE, id);
    }

    // ==================== 登记 ====================

    @Transactional(rollbackFor = Exception.class)
    public void lend(Long id, RecordReq req) {
        ToolingDO t = getOrThrow(id);
        if (req.userId() == null) throw BizException.of(EngineeringErrorCodes.TOOLING_FIELD_REQUIRED, "借用人");
        transit(t, Op.LEND);
        t.setHolderId(req.userId());
        mapper.updateByIdOrFail(t);
        String content = EngSupport.trim(req.content());
        if (req.expectedReturn() != null) content = (content == null ? "" : content + "；") + "预计归还 " + req.expectedReturn();
        record(t, "LEND", null, req.userId(), null, content, null);
    }

    @Transactional(rollbackFor = Exception.class)
    public void giveBack(Long id, RecordReq req) {
        ToolingDO t = getOrThrow(id);
        transit(t, Op.RETURN);
        t.setHolderId(null);
        mapper.updateByIdOrFail(t);
        record(t, "RETURN", null, req.userId(), null, EngSupport.trim(req.content()), null);
    }

    /** R04：登记保养后 last_maintain_count = used_count，保养提醒消失 */
    @Transactional(rollbackFor = Exception.class)
    public void maintain(Long id, RecordReq req) {
        ToolingDO t = getOrThrow(id);
        if (State.SCRAPPED.name().equals(t.getToolingStatus())) throw statusError(t, "保养");
        if (!StringUtils.hasText(req.content())) throw BizException.of(EngineeringErrorCodes.TOOLING_FIELD_REQUIRED, "保养内容");
        t.setLastMaintainCount(t.getUsedCount());
        mapper.updateByIdOrFail(t);
        record(t, "MAINTAIN", null, req.userId(), null, req.content().trim(), req.cost(), req.date() == null ? null : req.date().atStartOfDay());
        notifyApi.resolve(maintainKey(t));
    }

    @Transactional(rollbackFor = Exception.class)
    public void repairStart(Long id, RecordReq req) {
        ToolingDO t = getOrThrow(id);
        if (!StringUtils.hasText(req.content())) throw BizException.of(EngineeringErrorCodes.TOOLING_FIELD_REQUIRED, "维修内容");
        transit(t, Op.REPAIR_START);
        t.setHolderId(null);
        mapper.updateByIdOrFail(t);
        record(t, "REPAIR_START", null, req.userId(), null, withVendor(req), req.cost());
    }

    @Transactional(rollbackFor = Exception.class)
    public void repairEnd(Long id, RecordReq req) {
        ToolingDO t = getOrThrow(id);
        if (!StringUtils.hasText(req.content())) throw BizException.of(EngineeringErrorCodes.TOOLING_FIELD_REQUIRED, "维修内容");
        transit(t, Op.REPAIR_END);
        mapper.updateByIdOrFail(t);
        record(t, "REPAIR_END", null, req.userId(), null, withVendor(req), req.cost());
    }

    @Transactional(rollbackFor = Exception.class)
    public void scrap(Long id, RecordReq req) {
        ToolingDO t = getOrThrow(id);
        if (!StringUtils.hasText(req.content())) throw BizException.of(EngineeringErrorCodes.TOOLING_FIELD_REQUIRED, "报废原因");
        transit(t, Op.SCRAP);
        t.setHolderId(null);
        mapper.updateByIdOrFail(t);
        record(t, "SCRAP", null, req.userId(), null, req.content().trim(), null, req.date() == null ? null : req.date().atStartOfDay());
        notifyApi.resolve(maintainKey(t));
        notifyApi.resolve(lifeKey(t));
    }

    /** 次数调整（需要 eng:tooling:update）：低于预警线时重新允许寿命预警 */
    @Transactional(rollbackFor = Exception.class)
    public void adjust(Long id, RecordReq req) {
        ToolingDO t = getOrThrow(id);
        if (req.count() == null) throw BizException.of(EngineeringErrorCodes.TOOLING_FIELD_REQUIRED, "调整后次数");
        if (!StringUtils.hasText(req.content())) throw BizException.of(EngineeringErrorCodes.TOOLING_FIELD_REQUIRED, "调整原因");
        t.setUsedCount(req.count());
        if (t.getLastMaintainCount() > req.count()) t.setLastMaintainCount(req.count());
        if (!overWarnLine(t)) {
            t.setLifeWarned(false);
            notifyApi.resolve(lifeKey(t));
        }
        mapper.updateByIdOrFail(t);
        record(t, "ADJUST", req.count(), req.userId(), null, req.content().trim(), null);
        checkAlerts(t);
    }

    private static String withVendor(RecordReq req) {
        String c = req.content().trim();
        return StringUtils.hasText(req.vendor()) ? c + "（维修厂商：" + req.vendor().trim() + "）" : c;
    }

    private void transit(ToolingDO t, Op op) {
        State from = State.valueOf(t.getToolingStatus());
        if (!MACHINE.canFire(from, op)) throw statusError(t, op.label());
        t.setToolingStatus(MACHINE.fire(from, op).name());
    }

    private static BizException statusError(ToolingDO t, String action) {
        return BizException.of(EngineeringErrorCodes.TOOLING_STATUS, t.getCode(), State.valueOf(t.getToolingStatus()).label(), action);
    }

    private void record(ToolingDO t, String type, Integer count, Long userId, String sourceDocNo, String content, BigDecimal cost) {
        record(t, type, count, userId, sourceDocNo, content, cost, null);
    }

    private void record(ToolingDO t, String type, Integer count, Long userId, String sourceDocNo, String content, BigDecimal cost, LocalDateTime at) {
        ToolingRecordDO r = new ToolingRecordDO();
        r.setToolingId(t.getId());
        r.setRecordType(type);
        r.setRecordCount(count);
        r.setUserId(userId != null ? userId : support.currentUser());
        r.setSourceDocNo(sourceDocNo);
        r.setContent(content);
        r.setCost(cost);
        r.setOccurredAt(at != null ? at : LocalDateTime.now());
        recordMapper.insert(r);
    }

    public ToolingDO getOrThrow(Long id) {
        ToolingDO t = id == null ? null : mapper.selectById(id);
        if (t == null) throw new BizException(EngineeringErrorCodes.TOOLING_NOT_EXISTS);
        return t;
    }

    // ==================== 使用次数与预警（R02～R04） ====================

    private BigDecimal warnPct() {
        BigDecimal p = paramApi.getDecimal(PARAM_WARN_PCT);
        return (p == null ? new BigDecimal("90") : p).divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
    }

    private boolean overWarnLine(ToolingDO t) {
        return t.getDesignLife() != null && BigDecimal.valueOf(t.getUsedCount()).compareTo(BigDecimal.valueOf(t.getDesignLife()).multiply(warnPct())) >= 0;
    }

    private static String lifeKey(ToolingDO t) {
        return "ENG_TOOLING_LIFE:" + t.getId();
    }

    private static String maintainKey(ToolingDO t) {
        return "ENG_TOOLING_MAINTAIN:" + t.getId();
    }

    /** 寿命预警（每个工装只提醒一次）、保养提醒 */
    private void checkAlerts(ToolingDO t) {
        if (overWarnLine(t) && !Boolean.TRUE.equals(t.getLifeWarned())) {
            t.setLifeWarned(true);
            mapper.updateByIdOrFail(t);
            notifyApi.alert(new AlertRaisedEvent(lifeKey(t), "ENG_TOOLING_LIFE", AlertRaisedEvent.Level.WARNING, List.of(), "eng:tooling:record",
                    BIZ_TYPE, t.getId(), "工装寿命预警：" + t.getCode(), t.getName() + " 已使用 " + t.getUsedCount() + " 次，设计寿命 " + t.getDesignLife() + " 次",
                    "/engineering/tooling/" + t.getId()));
            eventPublisher.publish(new ToolingLifeWarningEvent(t.getId(), t.getCode(), t.getUsedCount(), t.getDesignLife()));
        }
        if (t.getMaintainCycle() != null && t.getUsedCount() - t.getLastMaintainCount() >= t.getMaintainCycle()) {
            notifyApi.alert(new AlertRaisedEvent(maintainKey(t), "ENG_TOOLING_MAINTAIN", AlertRaisedEvent.Level.INFO, List.of(), "eng:tooling:record",
                    BIZ_TYPE, t.getId(), "工装需要保养：" + t.getCode(), t.getName() + " 自上次保养已使用 " + (t.getUsedCount() - t.getLastMaintainCount()) + " 次",
                    "/engineering/tooling/" + t.getId()));
        }
    }

    static boolean usable(ToolingDO t) {
        State s = State.valueOf(t.getToolingStatus());
        if (s == State.SCRAPPED || s == State.REPAIRING) return false;
        return t.getDesignLife() == null || t.getUsedCount() < t.getDesignLife() || Boolean.TRUE.equals(t.getAllowOverLife());
    }

    @Override
    public void validateUsable(Long toolingId) {
        ToolingDO t = getOrThrow(toolingId);
        State s = State.valueOf(t.getToolingStatus());
        if (s == State.SCRAPPED || s == State.REPAIRING) throw statusError(t, "使用");
        if (!usable(t)) throw BizException.of(EngineeringErrorCodes.TOOLING_LIFE_EXCEEDED, t.getCode());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addUsage(Long toolingId, int count, String sourceDocNo) {
        ToolingDO t = getOrThrow(toolingId);
        if (count > 0) validateUsable(toolingId);
        t.setUsedCount(Math.max(0, t.getUsedCount() + count));
        mapper.updateByIdOrFail(t);
        record(t, "USAGE", count, null, sourceDocNo, count < 0 ? "报工反审核扣回" : null, null);
        checkAlerts(t);
    }

    @Override
    public int usageOf(Long toolingId, BigDecimal outputQty) {
        ToolingDO t = getOrThrow(toolingId);
        if (outputQty == null || outputQty.signum() <= 0) return 0;
        return outputQty.divide(BigDecimal.valueOf(t.getCavity()), 0, RoundingMode.CEILING).intValueExact();
    }

    @Override
    public Optional<ToolingDTO> get(Long toolingId) {
        return Optional.ofNullable(toolingId == null ? null : mapper.selectById(toolingId)).map(ToolingService::toDto);
    }

    @Override
    public List<ToolingDTO> listUsable(Long materialId) {
        List<Long> ids = materialMapper.selectList(new LambdaQueryWrapper<ToolingMaterialDO>().eq(ToolingMaterialDO::getMaterialId, materialId))
                .stream().map(ToolingMaterialDO::getToolingId).toList();
        if (ids.isEmpty()) return List.of();
        return mapper.selectBatchIds(ids).stream().filter(ToolingService::usable).map(ToolingService::toDto).toList();
    }

    static ToolingDTO toDto(ToolingDO t) {
        return new ToolingDTO(t.getId(), t.getCode(), t.getName(), t.getToolingType(), t.getCavity(), t.getDesignLife(), t.getUsedCount(),
                t.getToolingStatus(), usable(t));
    }

    // ==================== 查询 ====================

    public PageResult<ToolingRow> page(ToolingQuery q) {
        LambdaQueryWrapper<ToolingDO> w = new LambdaQueryWrapper<ToolingDO>()
                .likeRight(StringUtils.hasText(q.getCode()), ToolingDO::getCode, q.getCode() == null ? null : q.getCode().trim().toUpperCase())
                .like(StringUtils.hasText(q.getName()), ToolingDO::getName, q.getName())
                .eq(StringUtils.hasText(q.getToolingType()), ToolingDO::getToolingType, q.getToolingType())
                .eq(StringUtils.hasText(q.getOwnership()), ToolingDO::getOwnership, q.getOwnership())
                .eq(StringUtils.hasText(q.getToolingStatus()), ToolingDO::getToolingStatus, q.getToolingStatus())
                .orderByAsc(ToolingDO::getCode);
        if (q.getMaterialId() != null) {
            w.inSql(ToolingDO::getId, "SELECT tooling_id FROM eng_tooling_material WHERE deleted = 0 AND material_id = " + q.getMaterialId().longValue());
        }
        if (Boolean.TRUE.equals(q.getLifeWarning())) {
            w.isNotNull(ToolingDO::getDesignLife).apply("used_count >= design_life * {0}", warnPct());
        }
        PageResult<ToolingDO> page = mapper.selectPage(q, w);
        return new PageResult<>(toRows(page.list()), page.total());
    }

    public List<ToolingRow> listForExport(ToolingQuery q, int limit) {
        q.setPageNo(1);
        q.setPageSize(Math.min(limit, 100_000));
        return page(q).list();
    }

    public ToolingRow detail(Long id) {
        return toRows(List.of(getOrThrow(id))).get(0);
    }

    private List<ToolingRow> toRows(List<ToolingDO> list) {
        if (list.isEmpty()) return List.of();
        Map<Long, List<ToolingMaterialDO>> links = materialMapper.selectByParents(list.stream().map(ToolingDO::getId).toList()).stream()
                .collect(Collectors.groupingBy(ToolingMaterialDO::getToolingId));
        Map<Long, MaterialDO> ms = support.materials(links.values().stream().flatMap(List::stream).map(ToolingMaterialDO::getMaterialId).toList());
        Map<Long, UserDTO> users = support.users(list.stream().map(ToolingDO::getHolderId).toList());
        Map<Long, String> customers = support.customers(list.stream().map(ToolingDO::getCustomerId).toList());
        BigDecimal pct = warnPct();
        return list.stream().map(t -> {
            BigDecimal life = t.getDesignLife() == null ? null
                    : BigDecimal.valueOf(t.getUsedCount()).divide(BigDecimal.valueOf(t.getDesignLife()), 4, RoundingMode.HALF_UP);
            Integer toMaintain = t.getMaintainCycle() == null ? null : t.getMaintainCycle() - (t.getUsedCount() - t.getLastMaintainCount());
            List<ToolingMaterialDO> ls = links.getOrDefault(t.getId(), List.of());
            return new ToolingRow(t.getId(), t.getCode(), t.getName(), t.getToolingType(), t.getSpec(), t.getOwnership(), t.getCustomerId(),
                    customers.get(t.getCustomerId()), t.getCavity(), t.getDesignLife(), t.getUsedCount(), life, life != null && life.compareTo(pct) >= 0,
                    t.getMaintainCycle(), t.getLastMaintainCount(), toMaintain, t.getLocation(), t.getToolingStatus(), t.getHolderId(),
                    EngSupport.name(users, t.getHolderId()), t.getSupplierName(), t.getPurchaseDate(), t.getPurchaseAmount(),
                    Boolean.TRUE.equals(t.getAllowOverLife()), t.getOverLifeReason(), ls.stream().map(ToolingMaterialDO::getMaterialId).toList(),
                    ls.stream().map(l -> ms.get(l.getMaterialId())).filter(Objects::nonNull).map(m -> new MaterialRef(m.getId(), m.getCode(), m.getName())).toList(),
                    t.getRemark(), t.getVersion());
        }).toList();
    }

    public List<RecordRow> records(Long id) {
        getOrThrow(id);
        List<ToolingRecordDO> list = recordMapper.selectList(new LambdaQueryWrapper<ToolingRecordDO>().eq(ToolingRecordDO::getToolingId, id)
                .orderByDesc(ToolingRecordDO::getOccurredAt).orderByDesc(ToolingRecordDO::getId));
        Set<Long> uids = new HashSet<>();
        list.forEach(r -> {
            uids.add(r.getUserId());
            uids.add(r.getCreatedBy());
        });
        Map<Long, UserDTO> users = support.users(uids);
        return list.stream().map(r -> new RecordRow(r.getId(), r.getRecordType(), r.getRecordCount(), r.getUserId(), EngSupport.name(users, r.getUserId()),
                r.getSourceDocNo(), r.getContent(), r.getCost(), r.getOccurredAt(), EngSupport.name(users, r.getCreatedBy()))).toList();
    }

    /** 报工选择：只返回可用工装；materialId 非空时只列该物料适用的工装 */
    public List<ToolingRow> simple(Long materialId) {
        List<ToolingDO> list;
        if (materialId == null) {
            list = mapper.selectList(new LambdaQueryWrapper<ToolingDO>().ne(ToolingDO::getToolingStatus, State.SCRAPPED.name()).orderByAsc(ToolingDO::getCode));
        } else {
            List<Long> ids = materialMapper.selectList(new LambdaQueryWrapper<ToolingMaterialDO>().eq(ToolingMaterialDO::getMaterialId, materialId))
                    .stream().map(ToolingMaterialDO::getToolingId).toList();
            list = ids.isEmpty() ? List.of() : mapper.selectBatchIds(ids);
        }
        return toRows(list.stream().filter(ToolingService::usable).toList());
    }

    // ==================== 导入 ====================

    /** 导入：每行独立事务；编号为空时自动生成 */
    public ImportResult doImport(List<ImportRow> rows) {
        int ok = 0;
        List<ImportResult.Error> errors = new ArrayList<>();
        for (ImportRow r : rows) {
            try {
                ToolingSave req = new ToolingSave(r.get("code"), r.get("name"), r.get("toolingType"), r.get("spec"),
                        StringUtils.hasText(r.get("ownership")) ? r.get("ownership") : "OWN", null, intOf(r, "cavity"), intOf(r, "designLife"),
                        intOf(r, "usedCount"), intOf(r, "maintainCycle"), r.get("location"), r.get("supplierName"), null, null, false, null,
                        List.of(), r.get("remark"), null);
                if (!StringUtils.hasText(req.name())) throw BizException.of(EngineeringErrorCodes.TOOLING_FIELD_REQUIRED, "名称");
                if (!StringUtils.hasText(req.toolingType())) throw BizException.of(EngineeringErrorCodes.TOOLING_FIELD_REQUIRED, "工装类型");
                tx.executeWithoutResult(s -> create(req));
                ok++;
            } catch (BizException e) {
                errors.add(new ImportResult.Error(r.rowNo(), e.getMessage()));
            } catch (NumberFormatException e) {
                errors.add(new ImportResult.Error(r.rowNo(), "数字格式不正确"));
            }
        }
        return new ImportResult(ok, errors.size(), errors);
    }

    private static Integer intOf(ImportRow r, String key) {
        String v = r.get(key);
        return StringUtils.hasText(v) ? new BigDecimal(v.trim()).intValueExact() : null;
    }
}
