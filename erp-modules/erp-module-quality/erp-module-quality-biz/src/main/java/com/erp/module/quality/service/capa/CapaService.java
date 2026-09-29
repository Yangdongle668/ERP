package com.erp.module.quality.service.capa;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.common.exception.BizException;
import com.erp.common.result.PageResult;
import com.erp.module.crm.api.customer.CustomerDTO;
import com.erp.module.engineering.api.material.MaterialDTO;
import com.erp.module.purchase.api.supplier.SupplierDTO;
import com.erp.module.quality.api.QualityErrorCodes;
import com.erp.module.quality.config.QualityModuleConfig;
import com.erp.module.quality.controller.vo.CapaVOs.CapaDetail;
import com.erp.module.quality.controller.vo.CapaVOs.CapaQuery;
import com.erp.module.quality.controller.vo.CapaVOs.CapaRow;
import com.erp.module.quality.controller.vo.CapaVOs.CapaSave;
import com.erp.module.quality.controller.vo.CapaVOs.Member;
import com.erp.module.quality.controller.vo.CapaVOs.StepSave;
import com.erp.module.quality.controller.vo.CapaVOs.VerifyReq;
import com.erp.module.quality.dal.dataobject.QcCapaDO;
import com.erp.module.quality.dal.dataobject.QcComplaintDO;
import com.erp.module.quality.dal.dataobject.QcNcrDO;
import com.erp.module.quality.dal.mapper.QcCapaMapper;
import com.erp.module.quality.service.CapaStatus;
import com.erp.module.quality.service.QcAction;
import com.erp.module.quality.service.QcStateMachines;
import com.erp.module.quality.service.QcSupport;
import com.erp.module.system.api.user.UserDTO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** CAPA / 8D（10-04） */
@Service
public class CapaService {

    public static final String BIZ_TYPE = QualityModuleConfig.CAPA;
    public static final List<String> SOURCES = List.of("NCR", "COMPLAINT", "AUDIT", "OTHER");

    private final QcCapaMapper mapper;
    private final QcSupport support;

    public CapaService(QcCapaMapper mapper, QcSupport support) {
        this.mapper = mapper;
        this.support = support;
    }

    // ==================== 查询 ====================

    public PageResult<CapaRow> page(CapaQuery q) {
        PageResult<QcCapaDO> p = mapper.selectPage(q, query(q));
        return new PageResult<>(rows(p.list()), p.total());
    }

    public List<CapaRow> list(CapaQuery q) {
        return rows(mapper.selectList(query(q)));
    }

    private LambdaQueryWrapper<QcCapaDO> query(CapaQuery q) {
        List<String> statuses = StringUtils.hasText(q.getStatuses()) ? Arrays.asList(q.getStatuses().split(",")) : List.of();
        LocalDate today = LocalDate.now();
        return new LambdaQueryWrapper<QcCapaDO>()
                .like(StringUtils.hasText(q.getDocNo()), QcCapaDO::getDocNo, q.getDocNo())
                .eq(StringUtils.hasText(q.getSource()), QcCapaDO::getCapaSource, q.getSource())
                .eq(q.getLeaderId() != null, QcCapaDO::getLeaderId, q.getLeaderId())
                .in(!statuses.isEmpty(), QcCapaDO::getCapaStatus, statuses)
                .ge(q.getDueFrom() != null, QcCapaDO::getDueDate, q.getDueFrom())
                .le(q.getDueTo() != null, QcCapaDO::getDueDate, q.getDueTo())
                .lt(Boolean.TRUE.equals(q.getOverdue()), QcCapaDO::getDueDate, today)
                .in(Boolean.TRUE.equals(q.getOverdue()), QcCapaDO::getCapaStatus, List.of(CapaStatus.OPEN.name(), CapaStatus.VERIFYING.name()))
                .orderByDesc(QcCapaDO::getCreatedAt).orderByDesc(QcCapaDO::getId);
    }

    private List<CapaRow> rows(List<QcCapaDO> list) {
        Map<Long, UserDTO> users = support.users(list.stream().map(QcCapaDO::getLeaderId).toList());
        Map<Long, MaterialDTO> ms = support.materials(list.stream().map(QcCapaDO::getMaterialId).toList());
        return list.stream().map(c -> new CapaRow(c.getId(), c.getDocNo(), c.getTitle(), c.getCapaSource(), c.getSourceId(), c.getSourceNo(), c.getMaterialId(),
                ms.containsKey(c.getMaterialId()) ? ms.get(c.getMaterialId()).code() : null, c.getLeaderId(), QcSupport.name(users, c.getLeaderId()),
                c.getCurrentStep(), c.getDueDate(), overdue(c), c.getD3Due(), c.getCapaStatus(), nz(c.getInvalidCount()), c.getCreatedAt())).toList();
    }

    static boolean overdue(QcCapaDO c) {
        boolean open = CapaStatus.OPEN.name().equals(c.getCapaStatus()) || CapaStatus.VERIFYING.name().equals(c.getCapaStatus());
        return open && c.getDueDate() != null && c.getDueDate().isBefore(LocalDate.now());
    }

    private static int nz(Integer v) {
        return v == null ? 0 : v;
    }

    public CapaDetail detail(Long id) {
        QcCapaDO c = get(id);
        List<Long> team = QcSupport.ids(c.getTeamMembers());
        List<Long> uids = new ArrayList<>(team);
        uids.add(c.getLeaderId());
        uids.add(c.getVerifyBy());
        Map<Long, UserDTO> users = support.users(uids);
        MaterialDTO m = c.getMaterialId() == null ? null : support.materials(List.of(c.getMaterialId())).get(c.getMaterialId());
        CustomerDTO cus = c.getCustomerId() == null ? null : support.customers(List.of(c.getCustomerId())).get(c.getCustomerId());
        SupplierDTO sup = c.getSupplierId() == null ? null : support.suppliers(List.of(c.getSupplierId())).get(c.getSupplierId());
        return new CapaDetail(c.getId(), c.getDocNo(), c.getTitle(), c.getCapaSource(), c.getSourceId(), c.getSourceNo(), c.getMaterialId(),
                m == null ? null : m.code(), m == null ? null : m.name(), c.getCustomerId(), QcSupport.customerName(cus), c.getSupplierId(),
                sup == null ? null : sup.name(), c.getLeaderId(), QcSupport.name(users, c.getLeaderId()),
                team.stream().map(u -> new Member(u, QcSupport.name(users, u))).toList(), c.getD1Team(), c.getD2Problem(), c.getD3Containment(),
                c.getD3Due(), c.getD3DoneAt(), c.getD4RootCause(), c.getD4Method(), c.getD5Actions(), c.getD6Implementation(), c.getD7Prevention(),
                c.getD8Summary(), c.getCurrentStep(), c.getDueDate(), overdue(c), c.getVerifyResult(), c.getVerifyBy(), QcSupport.name(users, c.getVerifyBy()),
                c.getVerifyAt(), nz(c.getInvalidCount()), c.getVerifyHistory(), c.getCapaStatus(), canEdit(c), c.getClosedAt(), c.getCancelReason(),
                c.getVersion());
    }

    public QcCapaDO get(Long id) {
        QcCapaDO c = id == null ? null : mapper.selectById(id);
        if (c == null) throw BizException.of(QualityErrorCodes.NOT_EXISTS, "CAPA");
        return c;
    }

    /** QC-CAPA-R03：负责人和小组成员可以编辑步骤内容 */
    boolean canEdit(QcCapaDO c) {
        Long me = support.currentUser();
        return me == null || me.equals(c.getLeaderId()) || QcSupport.ids(c.getTeamMembers()).contains(me);
    }

    // ==================== 新建 ====================

    @Transactional(rollbackFor = Exception.class)
    public Long create(CapaSave req) {
        String source = StringUtils.hasText(req.source()) ? req.source() : "OTHER";
        if (!SOURCES.contains(source)) throw BizException.of(QualityErrorCodes.NOT_EXISTS, "来源 " + source);
        QcCapaDO c = base(req.title(), source, req.sourceId(), req.sourceNo(), req.materialId(), req.customerId(), req.supplierId(), req.leaderId(),
                req.dueDate(), LocalDate.now().plusDays(1), null);
        c.setTeamMembers(QcSupport.idText(req.teamMembers()));
        mapper.insert(c);
        created(c);
        return c.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, CapaSave req) {
        QcCapaDO c = get(id);
        requireOpen(c, "修改");
        if (!canEdit(c) && !support.hasPermission("qc:capa:close")) throw new BizException(QualityErrorCodes.CAPA_NOT_MEMBER);
        if (req.version() != null) c.setVersion(req.version());
        c.setTitle(req.title().trim());
        c.setLeaderId(req.leaderId());
        c.setTeamMembers(QcSupport.idText(req.teamMembers()));
        c.setDueDate(req.dueDate());
        if (req.materialId() != null) c.setMaterialId(req.materialId());
        mapper.updateByIdOrFail(c);
    }

    /** NCR 生成 CAPA：带出物料、供应商 / 客户，NCR 描述带入 D2（QC-CAPA-T01） */
    @Transactional(rollbackFor = Exception.class)
    public QcCapaDO createFromNcr(QcNcrDO n) {
        MaterialDTO m = support.material(n.getMaterialId());
        QcCapaDO c = base(QcSupport.limit(m.code() + " " + m.name() + "：" + firstLine(n.getDefectDescription()), 128), "NCR", n.getId(), n.getDocNo(),
                n.getMaterialId(), n.getCustomerId(), n.getSupplierId(), n.getOwnerId() != null ? n.getOwnerId() : support.currentUser(),
                LocalDate.now().plusDays(30), LocalDate.now().plusDays(1), n.getDefectDescription());
        if (StringUtils.hasText(n.getContainment())) c.setD3Containment(n.getContainment());
        mapper.insert(c);
        created(c);
        return c;
    }

    /** 客诉生成 CAPA：D3 期限为收到 + 客诉回复期限（QC-CAPA-R02） */
    @Transactional(rollbackFor = Exception.class)
    public QcCapaDO createFromComplaint(QcComplaintDO p) {
        int days = support.params().getInt(QualityModuleConfig.P_COMPLAINT_REPLY_DAYS);
        LocalDate received = p.getReceivedAt() == null ? LocalDate.now() : p.getReceivedAt().toLocalDate();
        QcCapaDO c = base(QcSupport.limit("客诉 " + p.getDocNo() + "：" + firstLine(p.getDescription()), 128), "COMPLAINT", p.getId(), p.getDocNo(),
                p.getMaterialId(), p.getCustomerId(), null, p.getQeId(), LocalDate.now().plusDays(30), received.plusDays(days), p.getDescription());
        mapper.insert(c);
        created(c);
        return c;
    }

    private static String firstLine(String s) {
        if (s == null) return "";
        String t = s.strip();
        int i = t.indexOf('\n');
        return i > 0 ? t.substring(0, i) : t;
    }

    private QcCapaDO base(String title, String source, Long sourceId, String sourceNo, Long materialId, Long customerId, Long supplierId, Long leaderId,
                          LocalDate due, LocalDate d3Due, String d2) {
        QcCapaDO c = new QcCapaDO();
        c.setDocNo(support.nextNo(BIZ_TYPE));
        c.setDocDate(LocalDate.now());
        c.setTitle(title.trim());
        c.setCapaSource(source);
        c.setSourceType(source);
        c.setSourceId(sourceId);
        c.setSourceNo(sourceNo);
        c.setMaterialId(materialId);
        c.setCustomerId(customerId);
        c.setSupplierId(supplierId);
        c.setLeaderId(leaderId);
        c.setDueDate(due);
        c.setD3Due(d3Due);
        c.setD2Problem(d2);
        c.setCurrentStep(1);
        c.setInvalidCount(0);
        c.setCapaStatus(CapaStatus.OPEN.name());
        c.setStatus(CapaStatus.OPEN.docStatus());
        support.fillOwner(c, leaderId);
        return c;
    }

    private void created(QcCapaDO c) {
        support.log(BIZ_TYPE, c.getId(), c.getDocNo(), QcAction.CREATE.name(), QcAction.CREATE.label(), null, c.getCapaStatus(),
                c.getSourceNo() == null ? null : "来源 " + c.getSourceNo());
        if (c.getLeaderId() != null && !c.getLeaderId().equals(support.currentUser())) {
            support.message(List.of(c.getLeaderId()), "新 CAPA：" + c.getDocNo(), c.getTitle() + "，期限 " + c.getDueDate(), "/quality/capa/" + c.getId());
        }
    }

    // ==================== 步骤 ====================

    @Transactional(rollbackFor = Exception.class)
    public void saveStep(Long id, int step, StepSave req) {
        QcCapaDO c = get(id);
        requireOpen(c, "编辑");
        if (step < 1 || step > 8) throw BizException.of(QualityErrorCodes.CAPA_STEP_ORDER, c.getCurrentStep());
        if (step == 6) {
            if (!support.hasPermission("qc:capa:verify") && !canEdit(c)) throw new BizException(QualityErrorCodes.CAPA_NOT_MEMBER);
        } else if (!canEdit(c)) {
            throw new BizException(QualityErrorCodes.CAPA_NOT_MEMBER);
        }
        if (step > c.getCurrentStep()) throw BizException.of(QualityErrorCodes.CAPA_STEP_ORDER, c.getCurrentStep());
        setContent(c, step, req.content());
        if (step == 1 && req.teamMembers() != null) c.setTeamMembers(QcSupport.idText(req.teamMembers()));
        if (step == 3 && req.d3Due() != null) c.setD3Due(req.d3Due());
        if (step == 4 && req.method() != null) c.setD4Method(QcSupport.limit(req.method(), 64));
        mapper.updateByIdOrFail(c);
    }

    /** QC-CAPA-R01：按顺序完成；D5 完成后进入待验证；D8 通过结案完成 */
    @Transactional(rollbackFor = Exception.class)
    public void completeStep(Long id, int step, StepSave req) {
        QcCapaDO c = get(id);
        if (!CapaStatus.OPEN.name().equals(c.getCapaStatus())) throw BizException.of(QualityErrorCodes.STATUS_NOT_ALLOWED, label(c), "完成步骤");
        if (!canEdit(c)) throw new BizException(QualityErrorCodes.CAPA_NOT_MEMBER);
        if (step != c.getCurrentStep() || step == 6 || step == 8) throw BizException.of(QualityErrorCodes.CAPA_STEP_ORDER, c.getCurrentStep());
        if (req != null && req.content() != null) {
            setContent(c, step, req.content());
            if (step == 1 && req.teamMembers() != null) c.setTeamMembers(QcSupport.idText(req.teamMembers()));
            if (step == 4 && req.method() != null) c.setD4Method(QcSupport.limit(req.method(), 64));
        }
        if (!StringUtils.hasText(content(c, step))) throw BizException.of(QualityErrorCodes.CAPA_STEP_EMPTY, step);
        if (step == 3) c.setD3DoneAt(LocalDateTime.now());
        c.setCurrentStep(step + 1);
        fire(c, step == 5 ? QcAction.TO_VERIFY : QcAction.COMPLETE_STEP, "D" + step);
    }

    /** 效果验证（D6）：有效 → 继续 D7；无效 → 退回 D4 并记录（QC-CAPA-T03） */
    @Transactional(rollbackFor = Exception.class)
    public void verify(Long id, VerifyReq req) {
        QcCapaDO c = get(id);
        if (!CapaStatus.VERIFYING.name().equals(c.getCapaStatus())) throw new BizException(QualityErrorCodes.CAPA_VERIFY_STEP);
        boolean ok = "EFFECTIVE".equals(req.result());
        if (!ok && !"INEFFECTIVE".equals(req.result())) throw BizException.of(QualityErrorCodes.NOT_EXISTS, "验证结果 " + req.result());
        c.setD6Implementation(req.content().trim());
        c.setVerifyResult(req.result());
        c.setVerifyBy(support.currentUser());
        c.setVerifyAt(LocalDateTime.now());
        String line = LocalDate.now() + " " + (ok ? "有效" : "无效") + "（" + support.userName(support.currentUser()) + "）";
        c.setVerifyHistory(QcSupport.limit(c.getVerifyHistory() == null ? line : c.getVerifyHistory() + "\n" + line, 2000));
        if (ok) {
            c.setCurrentStep(7);
            fire(c, QcAction.VERIFY_OK, null);
        } else {
            c.setInvalidCount(nz(c.getInvalidCount()) + 1);
            c.setCurrentStep(4);
            fire(c, QcAction.VERIFY_FAIL, "退回 D4 重新分析");
        }
    }

    /** 结案：D1～D7 完成且 D8 已填写 */
    @Transactional(rollbackFor = Exception.class)
    public void close(Long id, String summary) {
        QcCapaDO c = get(id);
        if (!CapaStatus.OPEN.name().equals(c.getCapaStatus()) || c.getCurrentStep() < 8) throw new BizException(QualityErrorCodes.CAPA_CLOSE_STEP);
        if (StringUtils.hasText(summary)) c.setD8Summary(summary.trim());
        if (!StringUtils.hasText(c.getD8Summary())) throw BizException.of(QualityErrorCodes.CAPA_STEP_EMPTY, 8);
        c.setCurrentStep(9);
        c.setClosedAt(LocalDateTime.now());
        fire(c, QcAction.CLOSE, null);
        support.resolve("QC_CAPA_OVERDUE_" + id);
    }

    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id, String reason) {
        QcCapaDO c = get(id);
        c.setCancelReason(QcSupport.limit(QcSupport.requireText(reason, "取消原因"), 256));
        fire(c, QcAction.CANCEL, c.getCancelReason());
        support.resolve("QC_CAPA_OVERDUE_" + id);
    }

    private static String content(QcCapaDO c, int step) {
        return switch (step) {
            case 1 -> StringUtils.hasText(c.getD1Team()) ? c.getD1Team() : (StringUtils.hasText(c.getTeamMembers()) ? c.getTeamMembers() : null);
            case 2 -> c.getD2Problem();
            case 3 -> c.getD3Containment();
            case 4 -> c.getD4RootCause();
            case 5 -> c.getD5Actions();
            case 6 -> c.getD6Implementation();
            case 7 -> c.getD7Prevention();
            default -> c.getD8Summary();
        };
    }

    private static void setContent(QcCapaDO c, int step, String text) {
        String v = text == null ? null : text.strip();
        switch (step) {
            case 1 -> c.setD1Team(QcSupport.limit(v, 1000));
            case 2 -> c.setD2Problem(v);
            case 3 -> c.setD3Containment(v);
            case 4 -> c.setD4RootCause(v);
            case 5 -> c.setD5Actions(v);
            case 6 -> c.setD6Implementation(v);
            case 7 -> c.setD7Prevention(v);
            default -> c.setD8Summary(v);
        }
    }

    private static String label(QcCapaDO c) {
        return CapaStatus.valueOf(c.getCapaStatus()).label();
    }

    private void requireOpen(QcCapaDO c, String action) {
        CapaStatus s = CapaStatus.valueOf(c.getCapaStatus());
        if (s != CapaStatus.OPEN && s != CapaStatus.VERIFYING) throw BizException.of(QualityErrorCodes.STATUS_NOT_ALLOWED, s.label(), action);
    }

    private void fire(QcCapaDO c, QcAction action, String reason) {
        CapaStatus from = CapaStatus.valueOf(c.getCapaStatus());
        CapaStatus to = QcStateMachines.CAPA.fire(from, action);
        c.setCapaStatus(to.name());
        c.setStatus(to.docStatus());
        mapper.updateByIdOrFail(c);
        support.log(BIZ_TYPE, c.getId(), c.getDocNo(), action.name(), action.label(), from.name(), to.name(), reason);
    }

    // ==================== 提醒（QC-CAPA-R04） ====================

    /** 到期前 3 天、到期当天提醒负责人；超期每周提醒负责人并抄送品质主管 */
    @Transactional(rollbackFor = Exception.class)
    public int remind(LocalDate today) {
        int n = 0;
        List<QcCapaDO> open = mapper.selectList(new LambdaQueryWrapper<QcCapaDO>().in(QcCapaDO::getCapaStatus, List.of(CapaStatus.OPEN.name(), CapaStatus.VERIFYING.name())));
        for (QcCapaDO c : open) {
            if (c.getDueDate() == null || today.equals(c.getLastRemindDate())) continue;
            long days = ChronoUnit.DAYS.between(today, c.getDueDate());
            String route = "/quality/capa/" + c.getId();
            if (days == 3 || days == 0) {
                support.message(List.of(c.getLeaderId()), "CAPA 即将到期：" + c.getDocNo(), c.getTitle() + "，期限 " + c.getDueDate() + "，当前 D" + c.getCurrentStep(), route);
            } else if (days < 0 && (c.getLastRemindDate() == null || ChronoUnit.DAYS.between(c.getLastRemindDate(), today) >= 7)) {
                List<Long> to = new ArrayList<>(support.managers());
                to.add(c.getLeaderId());
                support.message(to, "CAPA 已超期：" + c.getDocNo(), c.getTitle() + "，期限 " + c.getDueDate() + "，已超期 " + (-days) + " 天", route);
            } else {
                continue;
            }
            c.setLastRemindDate(today);
            mapper.updateByIdOrFail(c);
            n++;
        }
        return n;
    }

    // ==================== 打印 ====================

    public Map<String, Object> printData(Long id) {
        CapaDetail d = detail(id);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("docNo", d.docNo());
        m.put("title", d.title());
        m.put("sourceNo", d.sourceNo());
        m.put("customerName", d.customerName());
        m.put("supplierName", d.supplierName());
        m.put("materialCode", d.materialCode());
        m.put("leaderName", d.leaderName());
        m.put("teamText", String.join("、", d.teamMembers().stream().map(Member::name).filter(StringUtils::hasText).toList()));
        m.put("dueDate", d.dueDate());
        m.put("d1Team", d.d1Team());
        m.put("d2Problem", d.d2Problem());
        m.put("d3Containment", d.d3Containment());
        m.put("d4RootCause", d.d4RootCause());
        m.put("d5Actions", d.d5Actions());
        m.put("d6Implementation", d.d6Implementation());
        m.put("d7Prevention", d.d7Prevention());
        m.put("d8Summary", d.d8Summary());
        m.put("verifyResultName", d.verifyResult() == null ? "" : "EFFECTIVE".equals(d.verifyResult()) ? "有效" : "无效");
        m.put("status", d.status());
        return m;
    }
}
