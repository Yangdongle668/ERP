package com.erp.module.quality.service.ncr;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.common.enums.DocStatus;
import com.erp.common.exception.BizException;
import com.erp.common.result.PageResult;
import com.erp.common.util.Decimals;
import com.erp.framework.event.DomainEventPublisher;
import com.erp.module.crm.api.customer.CustomerDTO;
import com.erp.module.engineering.api.material.Tracking;
import com.erp.module.engineering.api.material.MaterialDTO;
import com.erp.module.inventory.api.batch.BatchApi;
import com.erp.module.inventory.api.doc.StockInType;
import com.erp.module.inventory.api.doc.StockInRequest;
import com.erp.module.inventory.api.doc.InventoryDocApi;
import com.erp.module.inventory.api.doc.SourceRef;
import com.erp.module.inventory.api.doc.StockOutRequest;
import com.erp.module.inventory.api.doc.StockOutType;
import com.erp.module.inventory.api.warehouse.WarehouseApi;
import com.erp.module.inventory.api.warehouse.WarehouseDTO;
import com.erp.module.inventory.api.warehouse.WarehouseType;
import com.erp.module.production.api.order.MrpSuggestion;
import com.erp.module.production.api.order.ProductionOrderApi;
import com.erp.module.purchase.api.receipt.PurchaseReturnApi;
import com.erp.module.purchase.api.supplier.SupplierDTO;
import com.erp.module.quality.api.QualityErrorCodes;
import com.erp.module.quality.api.inspection.InspectType;
import com.erp.module.quality.api.ncr.NcrApprovedEvent;
import com.erp.module.quality.config.QualityModuleConfig;
import com.erp.module.quality.controller.vo.NcrVOs.CapaSuggestReq;
import com.erp.module.quality.controller.vo.NcrVOs.DispositionRow;
import com.erp.module.quality.controller.vo.NcrVOs.DispositionSave;
import com.erp.module.quality.controller.vo.NcrVOs.NcrDetail;
import com.erp.module.quality.controller.vo.NcrVOs.NcrQuery;
import com.erp.module.quality.controller.vo.NcrVOs.NcrRow;
import com.erp.module.quality.controller.vo.NcrVOs.NcrSave;
import com.erp.module.quality.dal.dataobject.QcCapaDO;
import com.erp.module.quality.dal.dataobject.QcComplaintDO;
import com.erp.module.quality.dal.dataobject.QcInspectionDO;
import com.erp.module.quality.dal.dataobject.QcInspectionDefectDO;
import com.erp.module.quality.dal.dataobject.QcNcrDO;
import com.erp.module.quality.dal.dataobject.QcNcrDispositionDO;
import com.erp.module.quality.dal.dataobject.QcScarDO;
import com.erp.module.quality.dal.mapper.QcCapaMapper;
import com.erp.module.quality.dal.mapper.QcComplaintMapper;
import com.erp.module.quality.dal.mapper.QcInspectionDefectMapper;
import com.erp.module.quality.dal.mapper.QcInspectionMapper;
import com.erp.module.quality.dal.mapper.QcNcrDispositionMapper;
import com.erp.module.quality.dal.mapper.QcNcrMapper;
import com.erp.module.quality.dal.mapper.QcScarMapper;
import com.erp.module.quality.service.QcAction;
import com.erp.module.quality.service.QcStateMachines;
import com.erp.module.quality.service.QcSupport;
import com.erp.module.quality.service.capa.CapaService;
import com.erp.module.quality.service.inspection.InspectionService;
import com.erp.module.quality.service.scar.ScarService;
import com.erp.module.system.api.notify.AlertRaisedEvent;
import com.erp.module.system.api.user.UserDTO;
import com.erp.module.system.api.workflow.ApprovalCompletedEvent;
import com.erp.module.system.api.workflow.StartResult;
import com.erp.module.system.api.workflow.WorkflowApi;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** NCR 与 MRB 处置（10-03） */
@Service
public class NcrService {

    public static final String BIZ_TYPE = QualityModuleConfig.NCR;
    public static final List<String> SOURCES = List.of("IQC", "IPQC", "FQC", "OQC", "RETURN", "RECHECK", "PRODUCTION", "INVENTORY", "COMPLAINT");
    /** 降级转换生成的仓库单据来源类型 */
    public static final String DOWNGRADE_SOURCE = "QC_NCR_DOWNGRADE";
    public static final List<String> DISPOSITIONS = List.of("RETURN", "CONCESSION", "SORT", "REWORK", "SCRAP", "DOWNGRADE");
    public static final List<String> SEVERITIES = List.of("CRITICAL", "MAJOR", "MINOR");
    static final Map<String, String> DISP_NAMES = Map.of("RETURN", "退货", "CONCESSION", "特采", "SORT", "挑选", "REWORK", "返工", "SCRAP", "报废",
            "DOWNGRADE", "降级使用");

    private final QcNcrMapper mapper;
    private final QcNcrDispositionMapper dispMapper;
    private final QcInspectionMapper inspectionMapper;
    private final QcInspectionDefectMapper inspectionDefectMapper;
    private final QcCapaMapper capaMapper;
    private final QcScarMapper scarMapper;
    private final QcComplaintMapper complaintMapper;
    private final InspectionService inspectionService;
    private final WorkflowApi workflowApi;
    private final BatchApi batchApi;
    private final InventoryDocApi inventoryDocApi;
    private final WarehouseApi warehouseApi;
    private final ProductionOrderApi productionOrderApi;
    private final DomainEventPublisher eventPublisher;
    private final QcSupport support;
    private final CapaService capaService;
    private final ScarService scarService;
    private final ObjectProvider<PurchaseReturnApi> purchaseReturnApi;
    private final TransactionTemplate newTx;

    public NcrService(QcNcrMapper mapper, QcNcrDispositionMapper dispMapper, QcInspectionMapper inspectionMapper, QcInspectionDefectMapper inspectionDefectMapper,
                      QcCapaMapper capaMapper, QcScarMapper scarMapper, QcComplaintMapper complaintMapper, InspectionService inspectionService,
                      WorkflowApi workflowApi, BatchApi batchApi, InventoryDocApi inventoryDocApi, WarehouseApi warehouseApi,
                      ProductionOrderApi productionOrderApi, DomainEventPublisher eventPublisher, QcSupport support,
                      CapaService capaService, ScarService scarService, ObjectProvider<PurchaseReturnApi> purchaseReturnApi,
                      PlatformTransactionManager transactionManager) {
        this.purchaseReturnApi = purchaseReturnApi;
        this.newTx = new TransactionTemplate(transactionManager);
        this.newTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.mapper = mapper;
        this.dispMapper = dispMapper;
        this.inspectionMapper = inspectionMapper;
        this.inspectionDefectMapper = inspectionDefectMapper;
        this.capaMapper = capaMapper;
        this.scarMapper = scarMapper;
        this.complaintMapper = complaintMapper;
        this.inspectionService = inspectionService;
        this.workflowApi = workflowApi;
        this.batchApi = batchApi;
        this.inventoryDocApi = inventoryDocApi;
        this.warehouseApi = warehouseApi;
        this.productionOrderApi = productionOrderApi;
        this.eventPublisher = eventPublisher;
        this.support = support;
        this.capaService = capaService;
        this.scarService = scarService;
    }

    // ==================== 查询 ====================

    public PageResult<NcrRow> page(NcrQuery q) {
        List<QcNcrDO> list;
        long total;
        LambdaQueryWrapper<QcNcrDO> w = query(q);
        PageResult<QcNcrDO> p = mapper.selectPage(q, w);
        list = p.list();
        total = p.total();
        return new PageResult<>(rows(list), total);
    }

    public List<NcrRow> list(NcrQuery q) {
        return rows(mapper.selectList(query(q)));
    }

    private LambdaQueryWrapper<QcNcrDO> query(NcrQuery q) {
        List<DocStatus> statuses = new ArrayList<>();
        if (StringUtils.hasText(q.getStatuses())) {
            for (String s : q.getStatuses().split(",")) {
                if ("OPEN".equals(s)) statuses.addAll(List.of(DocStatus.DRAFT, DocStatus.PENDING_APPROVAL, DocStatus.APPROVED));
                else statuses.add(DocStatus.valueOf(s));
            }
        }
        return new LambdaQueryWrapper<QcNcrDO>()
                .like(StringUtils.hasText(q.getDocNo()), QcNcrDO::getDocNo, q.getDocNo())
                .eq(StringUtils.hasText(q.getSource()), QcNcrDO::getNcrSource, q.getSource())
                .eq(q.getMaterialId() != null, QcNcrDO::getMaterialId, q.getMaterialId())
                .eq(q.getSupplierId() != null, QcNcrDO::getSupplierId, q.getSupplierId())
                .eq(q.getCustomerId() != null, QcNcrDO::getCustomerId, q.getCustomerId())
                .eq(q.getInspectionId() != null, QcNcrDO::getInspectionId, q.getInspectionId())
                .eq(StringUtils.hasText(q.getResponsibility()), QcNcrDO::getResponsibility, q.getResponsibility())
                .eq(StringUtils.hasText(q.getSeverity()), QcNcrDO::getSeverity, q.getSeverity())
                .in(!statuses.isEmpty(), QcNcrDO::getStatus, statuses)
                .ge(q.getDateFrom() != null, QcNcrDO::getDocDate, q.getDateFrom())
                .le(q.getDateTo() != null, QcNcrDO::getDocDate, q.getDateTo())
                .orderByDesc(QcNcrDO::getCreatedAt).orderByDesc(QcNcrDO::getId);
    }

    private List<NcrRow> rows(List<QcNcrDO> list) {
        Map<Long, MaterialDTO> ms = support.materials(list.stream().map(QcNcrDO::getMaterialId).toList());
        Map<Long, SupplierDTO> sups = support.suppliers(list.stream().map(QcNcrDO::getSupplierId).toList());
        Map<Long, CustomerDTO> cus = support.customers(list.stream().map(QcNcrDO::getCustomerId).toList());
        Map<Long, UserDTO> users = support.users(list.stream().map(QcNcrDO::getOwnerId).toList());
        Map<Long, List<QcNcrDispositionDO>> disps = dispMapper.selectByParents(list.stream().map(QcNcrDO::getId).toList()).stream()
                .collect(Collectors.groupingBy(QcNcrDispositionDO::getNcrId));
        return list.stream().map(n -> {
            MaterialDTO m = ms.get(n.getMaterialId());
            return new NcrRow(n.getId(), n.getDocNo(), n.getNcrSource(), n.getSourceNo(), n.getMaterialId(), m == null ? null : m.code(),
                    m == null ? null : m.name(), n.getBatchNo(), n.getNcrQty(), n.getSeverity(), n.getResponsibility(), summary(disps.getOrDefault(n.getId(), List.of())),
                    n.getSupplierId(), sups.containsKey(n.getSupplierId()) ? sups.get(n.getSupplierId()).name() : null, n.getCustomerId(),
                    QcSupport.customerName(cus.get(n.getCustomerId())), Boolean.TRUE.equals(n.getCapaRequired()), Boolean.TRUE.equals(n.getScarRequired()),
                    n.getStatus().name(), QcSupport.name(users, n.getOwnerId()), n.getDocDate(), n.getCreatedAt());
        }).toList();
    }

    /** 处置摘要：“特采 80 / 退货 20” */
    static String summary(List<QcNcrDispositionDO> ds) {
        return ds.stream().map(d -> DISP_NAMES.getOrDefault(d.getDisposition(), d.getDisposition()) + " " + QcSupport.plain(d.getQty()))
                .collect(Collectors.joining(" / "));
    }

    public NcrDetail detail(Long id) {
        QcNcrDO n = get(id);
        MaterialDTO m = support.materials(List.of(n.getMaterialId())).get(n.getMaterialId());
        QcInspectionDO ins = n.getInspectionId() == null ? null : inspectionMapper.selectById(n.getInspectionId());
        QcCapaDO capa = n.getCapaId() == null ? null : capaMapper.selectById(n.getCapaId());
        QcScarDO scar = n.getScarId() == null ? null : scarMapper.selectById(n.getScarId());
        QcComplaintDO cpl = n.getComplaintId() == null ? null : complaintMapper.selectById(n.getComplaintId());
        SupplierDTO sup = support.suppliers(java.util.Collections.singletonList(n.getSupplierId())).get(n.getSupplierId());
        CustomerDTO cus = support.customers(java.util.Collections.singletonList(n.getCustomerId())).get(n.getCustomerId());
        List<QcNcrDispositionDO> dlist = dispMapper.selectByParent(id);
        Map<Long, MaterialDTO> targets = support.materials(dlist.stream().map(QcNcrDispositionDO::getTargetMaterialId).filter(java.util.Objects::nonNull).toList());
        List<DispositionRow> ds = dlist.stream().map(d -> {
            MaterialDTO t = d.getTargetMaterialId() == null ? null : targets.get(d.getTargetMaterialId());
            return new DispositionRow(d.getId(), d.getSeq(), d.getDisposition(), d.getQty(), d.getRemark(), d.getFollowDocNo(), Boolean.TRUE.equals(d.getDone()),
                    d.getDoneAt(), d.getTargetMaterialId(), t == null ? null : t.code(), t == null ? null : t.name());
        }).toList();
        return new NcrDetail(n.getId(), n.getDocNo(), n.getDocDate(), n.getNcrSource(), n.getSourceNo(), n.getInspectionId(), ins == null ? null : ins.getDocNo(),
                ins == null ? null : ins.getResult(), n.getMaterialId(), m == null ? null : m.code(), m == null ? null : m.name(), m == null ? null : m.spec(),
                m == null ? null : m.baseUom(), n.getBatchNo(), n.getNcrQty(), n.getSupplierId(), sup == null ? null : sup.name(), n.getCustomerId(),
                QcSupport.customerName(cus), n.getDefectDescription(), codes(n.getDefectCodes()), n.getSeverity(), n.getResponsibility(), n.getContainment(),
                Boolean.TRUE.equals(n.getCapaRequired()), Boolean.TRUE.equals(n.getScarRequired()),
                suggestCapa(n.getMaterialId(), codes(n.getDefectCodes()), n.getSeverity(), n.getNcrSource(), n.getId()), n.getAmountBase(),
                n.getCapaId(), capa == null ? null : capa.getDocNo(), capa == null ? null : capa.getCapaStatus(), n.getScarId(),
                scar == null ? null : scar.getDocNo(), scar == null ? null : scar.getScarStatus(), n.getComplaintId(), cpl == null ? null : cpl.getDocNo(),
                Boolean.TRUE.equals(n.getBatchFrozen()), n.getStatus().name(), workflowApi.isRunning(BIZ_TYPE, id), n.getOwnerId(),
                support.userName(n.getOwnerId()), n.getApprovedAt(), n.getClosedAt(), n.getRemark(), n.getVersion(), ds);
    }

    static List<String> codes(String text) {
        if (!StringUtils.hasText(text)) return List.of();
        return Arrays.stream(text.split(",")).map(String::trim).filter(StringUtils::hasText).toList();
    }

    public QcNcrDO get(Long id) {
        QcNcrDO n = id == null ? null : mapper.selectById(id);
        if (n == null) throw BizException.of(QualityErrorCodes.NOT_EXISTS, "NCR");
        return n;
    }

    // ==================== 新建 / 编辑 ====================

    @Transactional(rollbackFor = Exception.class)
    public Long create(NcrSave req) {
        QcNcrDO n = new QcNcrDO();
        n.setDocNo(support.nextNo(BIZ_TYPE));
        n.setDocDate(LocalDate.now());
        String source = StringUtils.hasText(req.source()) ? req.source() : "INVENTORY";
        if (!List.of("PRODUCTION", "INVENTORY", "IPQC").contains(source)) throw BizException.of(QualityErrorCodes.NCR_DISP_NOT_ALLOWED, source);
        n.setNcrSource(source);
        n.setSourceNo(QcSupport.trim(req.sourceNo()));
        support.fillOwner(n, null);
        fill(n, req, true);
        n.setStatus(DocStatus.DRAFT);
        mapper.insert(n);
        saveDispositions(n, req.dispositions());
        support.bindFiles(req.fileIds(), BIZ_TYPE, n.getId());
        support.log(BIZ_TYPE, n.getId(), n.getDocNo(), QcAction.CREATE.name(), QcAction.CREATE.label(), null, n.getStatus().name(), null);
        return n.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, NcrSave req) {
        QcNcrDO n = get(id);
        requireDraft(n);
        if (req.version() != null) n.setVersion(req.version());
        fill(n, req, n.getInspectionId() == null && !"COMPLAINT".equals(n.getNcrSource()));
        if (n.getInspectionId() == null && req.sourceNo() != null) n.setSourceNo(QcSupport.trim(req.sourceNo()));
        mapper.updateByIdOrFail(n);
        saveDispositions(n, req.dispositions());
        support.bindFiles(req.fileIds(), BIZ_TYPE, n.getId());
    }

    private void fill(QcNcrDO n, NcrSave req, boolean headEditable) {
        if (headEditable) {
            if (req.materialId() == null) throw BizException.of(QualityErrorCodes.REASON_REQUIRED, "物料");
            support.material(req.materialId());
            if (req.ncrQty() == null || req.ncrQty().signum() <= 0) throw new BizException(QualityErrorCodes.NCR_QTY_INVALID);
            n.setMaterialId(req.materialId());
            n.setBatchNo(QcSupport.trim(req.batchNo()));
            n.setNcrQty(Decimals.qty(req.ncrQty()));
            n.setSupplierId(req.supplierId());
            n.setCustomerId(req.customerId());
        } else if (req.ncrQty() != null && req.ncrQty().signum() > 0 && n.getInspectionId() != null) {
            QcInspectionDO ins = inspectionMapper.selectById(n.getInspectionId());
            if (ins != null && req.ncrQty().compareTo(ins.getLotQty()) <= 0) n.setNcrQty(Decimals.qty(req.ncrQty()));
        }
        if (req.severity() == null || !SEVERITIES.contains(req.severity())) throw BizException.of(QualityErrorCodes.NOT_EXISTS, "严重度 " + req.severity());
        support.dictApi().validate("qc_ncr_responsibility", req.responsibility(), "责任归属");
        n.setDefectDescription(req.defectDescription().trim());
        n.setDefectCodes(req.defectCodes() == null || req.defectCodes().isEmpty() ? null : QcSupport.limit(String.join(",", req.defectCodes()), 256));
        n.setSeverity(req.severity());
        n.setResponsibility(req.responsibility());
        n.setContainment(QcSupport.limit(req.containment(), 1000));
        n.setCapaRequired(req.capaRequired() != null ? req.capaRequired()
                : suggestCapa(n.getMaterialId(), req.defectCodes(), req.severity(), n.getNcrSource(), n.getId()));
        n.setScarRequired(req.scarRequired() != null ? req.scarRequired() : "SUPPLIER".equals(req.responsibility()));
    }

    private void saveDispositions(QcNcrDO n, List<DispositionSave> list) {
        if (list == null) return;
        dispMapper.deleteByParent(n.getId());
        int seq = 0;
        for (DispositionSave s : list) {
            if (s.qty() == null || s.qty().signum() <= 0) continue;
            if (s.disposition() == null || !DISPOSITIONS.contains(s.disposition())) throw BizException.of(QualityErrorCodes.NCR_DISP_NOT_ALLOWED, s.disposition());
            QcNcrDispositionDO d = new QcNcrDispositionDO();
            d.setNcrId(n.getId());
            d.setSeq(++seq);
            d.setDisposition(s.disposition());
            d.setQty(Decimals.qty(s.qty()));
            if ("DOWNGRADE".equals(s.disposition())) {
                if (s.targetMaterialId() == null || s.targetMaterialId().equals(n.getMaterialId())) throw new BizException(QualityErrorCodes.NCR_DOWNGRADE_TARGET);
                d.setTargetMaterialId(support.materialApi().validateUsable(s.targetMaterialId()).id());
            }
            d.setRemark(QcSupport.limit(s.remark(), 512));
            d.setDone(false);
            dispMapper.insert(d);
        }
    }

    /** QC-NCR-R05：同物料同缺陷代码 30 天内 NCR 次数（含本次）≥ 参数，或致命，或来源为客诉 */
    public boolean suggestCapa(Long materialId, List<String> defectCodes, String severity, String source, Long excludeId) {
        if ("CRITICAL".equals(severity) || "COMPLAINT".equals(source)) return true;
        if (materialId == null || defectCodes == null || defectCodes.isEmpty()) return false;
        int threshold = support.params().getInt(QualityModuleConfig.P_CAPA_REPEAT);
        List<QcNcrDO> recent = mapper.selectList(new LambdaQueryWrapper<QcNcrDO>().eq(QcNcrDO::getMaterialId, materialId)
                .ne(QcNcrDO::getStatus, DocStatus.VOIDED).ne(excludeId != null, QcNcrDO::getId, excludeId)
                .ge(QcNcrDO::getCreatedAt, LocalDateTime.now().minusDays(30)).isNotNull(QcNcrDO::getDefectCodes));
        long same = recent.stream().filter(r -> codes(r.getDefectCodes()).stream().anyMatch(defectCodes::contains)).count();
        return same + 1 >= threshold;
    }

    public boolean suggestCapa(CapaSuggestReq req) {
        return suggestCapa(req.materialId(), req.defectCodes(), req.severity(), req.source(), null);
    }

    /** 由检验单生成 NCR（提交 MRB 或拒收自动生成），带出检验结果与缺陷 */
    @Transactional(rollbackFor = Exception.class)
    public Long createFromInspection(QcInspectionDO ins, BigDecimal qty, boolean mrb) {
        List<QcInspectionDefectDO> defects = inspectionDefectMapper.selectByParent(ins.getId());
        QcNcrDO n = new QcNcrDO();
        n.setDocNo(support.nextNo(BIZ_TYPE));
        n.setDocDate(LocalDate.now());
        n.setNcrSource(ins.getInspectType());
        n.setInspectionId(ins.getId());
        n.setSourceType(QualityModuleConfig.INSPECTION);
        n.setSourceId(ins.getId());
        n.setSourceNo(ins.getDocNo());
        n.setMaterialId(ins.getMaterialId());
        n.setBatchNo(ins.getBatchNo());
        n.setSupplierId(ins.getSupplierId());
        n.setCustomerId(ins.getCustomerId());
        n.setNcrQty(Decimals.qty(qty));
        StringBuilder desc = new StringBuilder(InspectType.valueOf(ins.getInspectType()).label()).append(" ").append(ins.getDocNo())
                .append("：批量 ").append(QcSupport.plain(ins.getLotQty())).append("，样本 ").append(ins.getSampleQty())
                .append("，CR ").append(ins.getCrCount()).append(" / MA ").append(ins.getMaCount()).append(" / MI ").append(ins.getMiCount());
        for (QcInspectionDefectDO d : defects) {
            desc.append("；").append(d.getDefectName() == null ? d.getDefectCode() : d.getDefectName()).append(" ").append(d.getQty());
            if (StringUtils.hasText(d.getDescription())) desc.append("（").append(d.getDescription()).append("）");
        }
        if (!mrb) desc.append("；检验判定拒收");
        n.setDefectDescription(QcSupport.limit(desc.toString(), 2000));
        List<String> codes = defects.stream().map(QcInspectionDefectDO::getDefectCode).distinct().toList();
        n.setDefectCodes(codes.isEmpty() ? null : QcSupport.limit(String.join(",", codes), 256));
        n.setSeverity(ins.getCrCount() != null && ins.getCrCount() > 0 ? "CRITICAL" : ins.getMaCount() != null && ins.getMaCount() > 0 ? "MAJOR" : "MINOR");
        String type = ins.getInspectType();
        n.setResponsibility("IQC".equals(type) || "RECHECK".equals(type) ? "SUPPLIER" : "RETURN".equals(type) ? "UNKNOWN" : "PROCESS");
        n.setCapaRequired(suggestCapa(n.getMaterialId(), codes, n.getSeverity(), n.getNcrSource(), null));
        n.setScarRequired("SUPPLIER".equals(n.getResponsibility()) && n.getSupplierId() != null);
        n.setBatchFrozen(false);
        n.setStatus(DocStatus.DRAFT);
        support.fillOwner(n, null);
        mapper.insert(n);
        List<Long> images = defects.stream().flatMap(d -> QcSupport.ids(d.getImageFileIds()).stream()).toList();
        if (!images.isEmpty()) {
            try {
                support.bindFiles(images, BIZ_TYPE, n.getId());
            } catch (BizException ignored) {
                // 照片已绑定到检验单，NCR 详情通过来源检验查看
            }
        }
        support.log(BIZ_TYPE, n.getId(), n.getDocNo(), QcAction.CREATE.name(), mrb ? "提交 MRB 生成" : "拒收自动生成", null, n.getStatus().name(),
                "来源 " + ins.getDocNo());
        return n.getId();
    }

    /** 其他模块发起（生产不良、库存问题、客诉） */
    @Transactional(rollbackFor = Exception.class)
    public QcNcrDO createExternal(String source, String sourceNo, Long materialId, String batchNo, Long supplierId, Long customerId, BigDecimal qty,
                                  String description, List<String> defectCodes, String severity, String responsibility, List<Long> fileIds) {
        support.material(materialId);
        if (qty == null || qty.signum() <= 0) throw new BizException(QualityErrorCodes.NCR_QTY_INVALID);
        QcNcrDO n = new QcNcrDO();
        n.setDocNo(support.nextNo(BIZ_TYPE));
        n.setDocDate(LocalDate.now());
        n.setNcrSource(source != null && SOURCES.contains(source) ? source : "INVENTORY");
        n.setSourceNo(QcSupport.limit(sourceNo, 64));
        n.setMaterialId(materialId);
        n.setBatchNo(QcSupport.trim(batchNo));
        n.setSupplierId(supplierId);
        n.setCustomerId(customerId);
        n.setNcrQty(Decimals.qty(qty));
        n.setDefectDescription(QcSupport.limit(StringUtils.hasText(description) ? description : "（待补充）", 2000));
        List<String> codes = defectCodes == null ? List.of() : defectCodes.stream().filter(StringUtils::hasText).toList();
        n.setDefectCodes(codes.isEmpty() ? null : QcSupport.limit(String.join(",", codes), 256));
        n.setSeverity(severity != null && SEVERITIES.contains(severity) ? severity : "MAJOR");
        n.setResponsibility(responsibility == null ? "UNKNOWN" : responsibility);
        n.setCapaRequired(suggestCapa(materialId, codes, n.getSeverity(), n.getNcrSource(), null));
        n.setScarRequired("SUPPLIER".equals(n.getResponsibility()) && supplierId != null);
        n.setBatchFrozen(false);
        n.setStatus(DocStatus.DRAFT);
        support.fillOwner(n, null);
        mapper.insert(n);
        if (fileIds != null && !fileIds.isEmpty()) {
            try {
                support.bindFiles(fileIds, BIZ_TYPE, n.getId());
            } catch (BizException ignored) {
                // 照片属于来源单据
            }
        }
        support.log(BIZ_TYPE, n.getId(), n.getDocNo(), QcAction.CREATE.name(), QcAction.CREATE.label(), null, n.getStatus().name(),
                sourceNo == null ? null : "来源 " + sourceNo);
        return n;
    }

    // ==================== 提交 / 审批 ====================

    /** 提交 MRB 会签（QC-NCR-R01、R02） */
    @Transactional(rollbackFor = Exception.class)
    public void submit(Long id) {
        QcNcrDO n = get(id);
        requireDraft(n);
        List<QcNcrDispositionDO> ds = dispMapper.selectByParent(id);
        if (ds.isEmpty()) throw new BizException(QualityErrorCodes.NCR_NO_DISP);
        BigDecimal sum = ds.stream().map(QcNcrDispositionDO::getQty).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (sum.compareTo(n.getNcrQty()) != 0) throw BizException.of(QualityErrorCodes.NCR_DISP_SUM, QcSupport.plain(sum), QcSupport.plain(n.getNcrQty()));
        List<String> allowed = allowedDispositions(n.getNcrSource());
        for (QcNcrDispositionDO d : ds) {
            if (!allowed.contains(d.getDisposition())) throw BizException.of(QualityErrorCodes.NCR_DISP_NOT_ALLOWED, DISP_NAMES.get(d.getDisposition()));
        }
        Map<String, Object> vars = new HashMap<>();
        boolean concession = ds.stream().anyMatch(d -> "CONCESSION".equals(d.getDisposition()));
        vars.put("disposition", concession ? "CONCESSION" : ds.get(0).getDisposition());
        vars.put("qty", n.getNcrQty());
        vars.put("amountBase", n.getAmountBase() == null ? BigDecimal.ZERO : n.getAmountBase());
        vars.put("source", n.getNcrSource());
        Map<String, Long> users = new HashMap<>();
        if (n.getOwnerId() != null) users.put("ownerId", n.getOwnerId());
        fire(n, QcAction.SUBMIT, null);
        StartResult r = workflowApi.start(BIZ_TYPE, id, n.getDocNo(), "NCR " + n.getDocNo() + "（" + summary(ds) + "）", vars, users, support.currentUser());
        if (!r.isStarted()) approve(get(id));
    }

    /** QC-NCR-R02：检验来源的处置范围 */
    static List<String> allowedDispositions(String source) {
        return switch (source) {
            case "IQC", "RECHECK" -> List.of("RETURN", "CONCESSION", "SORT", "SCRAP", "DOWNGRADE");
            case "FQC", "RETURN" -> List.of("CONCESSION", "REWORK", "SCRAP", "DOWNGRADE");
            case "OQC" -> List.of("REWORK", "SORT", "CONCESSION");
            default -> DISPOSITIONS;
        };
    }

    @EventListener
    public void onApproval(ApprovalCompletedEvent e) {
        if (!BIZ_TYPE.equals(e.getBizType())) return;
        QcNcrDO n = mapper.selectById(e.getBizId());
        if (n == null || n.getStatus() != DocStatus.PENDING_APPROVAL) return;
        switch (e.getResult()) {
            case APPROVED -> approve(n);
            case WITHDRAWN -> fire(n, QcAction.WITHDRAW, null);
            default -> fire(n, QcAction.REJECT, e.getComment());
        }
    }

    /** QC-NCR-R03、R04：按处置完成检验判定；致命缺陷冻结同批次库存；发布 NcrApprovedEvent */
    private void approve(QcNcrDO n) {
        n.setApprovedAt(LocalDateTime.now());
        fire(n, QcAction.APPROVE, null);
        List<QcNcrDispositionDO> ds = dispMapper.selectByParent(n.getId());
        Map<String, BigDecimal> byDisp = new LinkedHashMap<>();
        for (QcNcrDispositionDO d : ds) byDisp.merge(d.getDisposition(), d.getQty(), BigDecimal::add);
        if (n.getInspectionId() != null) {
            BigDecimal rejected = sum(byDisp, "RETURN", "REWORK", "SCRAP", "DOWNGRADE");
            inspectionService.applyMrb(n.getInspectionId(), n.getDocNo(), n.getNcrQty(), sum(byDisp, "CONCESSION"), rejected, sum(byDisp, "SORT"),
                    sum(byDisp, "REWORK"));
            // 检验单挑选处置由检验员按挑选判定后完成；其余处置随检验判定自动完成库存动作
            for (QcNcrDispositionDO d : ds) {
                if ("CONCESSION".equals(d.getDisposition()) || "SORT".equals(d.getDisposition())) {
                    d.setDone(true);
                    d.setDoneAt(LocalDateTime.now());
                    d.setFollowDocNo(n.getSourceNo());
                    dispMapper.updateByIdOrFail(d);
                }
            }
        }
        QcNcrDO fresh = get(n.getId());
        if ("CRITICAL".equals(fresh.getSeverity()) && StringUtils.hasText(fresh.getBatchNo())) {
            batchApi.freeze(fresh.getMaterialId(), fresh.getBatchNo(), "NCR " + fresh.getDocNo() + " 致命缺陷", QualityModuleConfig.MODULE, fresh.getDocNo());
            fresh.setBatchFrozen(true);
            mapper.updateByIdOrFail(fresh);
        }
        eventPublisher.publish(new NcrApprovedEvent(fresh.getId(), fresh.getDocNo(), fresh.getNcrSource(), fresh.getInspectionId(), fresh.getMaterialId(),
                fresh.getBatchNo(), fresh.getSupplierId(), fresh.getNcrQty(), fresh.getSeverity(),
                byDisp.entrySet().stream().map(x -> x.getKey() + "=" + QcSupport.plain(x.getValue())).collect(Collectors.joining(";"))));
        if (byDisp.containsKey("RETURN") && fresh.getSupplierId() != null) {
            SupplierDTO s = support.suppliers(List.of(fresh.getSupplierId())).get(fresh.getSupplierId());
            if (s != null && s.buyerId() != null) {
                support.message(List.of(s.buyerId()), "NCR 退供应商：" + fresh.getDocNo(), "NCR " + fresh.getDocNo() + " 处置退货 "
                        + QcSupport.plain(byDisp.get("RETURN")) + "，请创建采购退货", "/quality/ncr/" + fresh.getId());
            }
        }
        if ("CRITICAL".equals(fresh.getSeverity())) {
            support.alert("QC_NCR_CRITICAL_" + fresh.getId(), AlertRaisedEvent.Level.CRITICAL, support.managers(), BIZ_TYPE, fresh.getId(),
                    "致命不合格：" + fresh.getDocNo(), fresh.getDefectDescription(), "/quality/ncr/" + fresh.getId());
        }
    }

    private static BigDecimal sum(Map<String, BigDecimal> m, String... keys) {
        BigDecimal s = BigDecimal.ZERO;
        for (String k : keys) s = s.add(m.getOrDefault(k, BigDecimal.ZERO));
        return s;
    }

    // ==================== 处置执行 ====================

    @Transactional(rollbackFor = Exception.class)
    public void markDone(Long id, Long dispId, String followDocNo) {
        QcNcrDO n = get(id);
        if (n.getStatus() != DocStatus.APPROVED) throw BizException.of(QualityErrorCodes.STATUS_NOT_ALLOWED, n.getStatus().label(), "标记处置完成");
        QcNcrDispositionDO d = disposition(id, dispId);
        d.setDone(true);
        d.setDoneAt(LocalDateTime.now());
        if (StringUtils.hasText(followDocNo)) d.setFollowDocNo(followDocNo.trim());
        dispMapper.updateByIdOrFail(d);
        support.log(BIZ_TYPE, id, n.getDocNo(), QcAction.DONE.name(), QcAction.DONE.label(), n.getStatus().name(), n.getStatus().name(),
                DISP_NAMES.get(d.getDisposition()) + " " + QcSupport.plain(d.getQty()) + (d.getFollowDocNo() == null ? "" : " → " + d.getFollowDocNo()));
    }

    private QcNcrDispositionDO disposition(Long ncrId, Long dispId) {
        QcNcrDispositionDO d = dispMapper.selectById(dispId);
        if (d == null || !d.getNcrId().equals(ncrId)) throw BizException.of(QualityErrorCodes.NOT_EXISTS, "处置明细");
        return d;
    }

    private QcNcrDispositionDO firstOpen(QcNcrDO n, String disposition) {
        if (n.getStatus() != DocStatus.APPROVED) throw BizException.of(QualityErrorCodes.STATUS_NOT_ALLOWED, n.getStatus().label(), "生成后续单据");
        List<QcNcrDispositionDO> ds = dispMapper.selectByParent(n.getId()).stream().filter(d -> disposition.equals(d.getDisposition())).toList();
        if (ds.isEmpty()) throw BizException.of(QualityErrorCodes.NCR_NO_DISP_OF, DISP_NAMES.get(disposition));
        for (QcNcrDispositionDO d : ds) {
            if (StringUtils.hasText(d.getFollowDocNo())) throw BizException.of(QualityErrorCodes.NCR_FOLLOW_EXISTS, DISP_NAMES.get(disposition) + "单据", d.getFollowDocNo());
        }
        return ds.get(0);
    }

    /** 报废：从不良品仓生成其他出库草稿（来源 = 本 NCR） */
    @Transactional(rollbackFor = Exception.class)
    public String createScrapOut(Long id) {
        QcNcrDO n = get(id);
        QcNcrDispositionDO d = firstOpen(n, "SCRAP");
        WarehouseDTO ng = warehouseApi.listByType(WarehouseType.NG).stream().filter(WarehouseDTO::isDefault).findFirst()
                .orElseGet(() -> warehouseApi.getDefaultWarehouse(n.getMaterialId(), WarehouseType.NG));
        MaterialDTO m = support.material(n.getMaterialId());
        inventoryDocApi.createStockOut(new StockOutRequest(StockOutType.OTHER_OUT, new SourceRef(BIZ_TYPE, n.getId(), n.getDocNo()), ng.id(), LocalDate.now(),
                null, null, n.getSupplierId(), n.getCustomerId(), List.of(new StockOutRequest.Line(d.getId(), m.id(), m.baseUom(), d.getQty(), n.getBatchNo(), null)),
                "SCRAP", QcSupport.limit("NCR " + n.getDocNo() + " 处置报废：" + n.getDefectDescription(), 500)));
        String follow = "报废出库（来源 " + n.getDocNo() + "）";
        d.setFollowDocNo(QcSupport.limit(follow, 64));
        dispMapper.updateByIdOrFail(d);
        support.log(BIZ_TYPE, id, n.getDocNo(), "SCRAP_OUT", "生成报废出库", n.getStatus().name(), n.getStatus().name(), QcSupport.plain(d.getQty()));
        return follow;
    }

    /**
     * 降级使用：不合格品从不良品仓出库（其他出库），同时以降级后的物料入物料默认仓（其他入库），均为草稿由仓库确认；
     * 来源类型为 QC_NCR_DOWNGRADE、来源行 = 处置明细。
     */
    @Transactional(rollbackFor = Exception.class)
    public String createDowngrade(Long id) {
        QcNcrDO n = get(id);
        QcNcrDispositionDO d = firstOpen(n, "DOWNGRADE");
        if (d.getTargetMaterialId() == null) throw new BizException(QualityErrorCodes.NCR_DOWNGRADE_TARGET);
        WarehouseDTO ng = warehouseApi.listByType(WarehouseType.NG).stream().filter(WarehouseDTO::isDefault).findFirst()
                .orElseGet(() -> warehouseApi.getDefaultWarehouse(n.getMaterialId(), WarehouseType.NG));
        MaterialDTO m = support.material(n.getMaterialId());
        MaterialDTO t = support.material(d.getTargetMaterialId());
        SourceRef src = new SourceRef(DOWNGRADE_SOURCE, d.getId(), n.getDocNo());
        inventoryDocApi.createStockOut(new StockOutRequest(StockOutType.OTHER_OUT, src, ng.id(), LocalDate.now(), null, null, n.getSupplierId(), n.getCustomerId(),
                List.of(new StockOutRequest.Line(d.getId(), m.id(), m.baseUom(), d.getQty(), n.getBatchNo(), null)), "DOWNGRADE",
                QcSupport.limit("NCR " + n.getDocNo() + " 降级为 " + t.code(), 500)));
        String batch = t.tracking() == Tracking.BATCH ? (StringUtils.hasText(n.getBatchNo()) ? n.getBatchNo() : n.getDocNo()) : null;
        inventoryDocApi.createStockIn(new StockInRequest(StockInType.OTHER_IN, src, warehouseApi.getDefaultWarehouse(t.id(), null).id(), LocalDate.now(),
                n.getSupplierId(), n.getCustomerId(), List.of(new StockInRequest.Line(d.getId(), t.id(), t.baseUom(), d.getQty(), batch, null, null, null, null))));
        String follow = "降级 " + m.code() + " → " + t.code() + "（来源 " + n.getDocNo() + "）";
        d.setFollowDocNo(QcSupport.limit(follow, 64));
        dispMapper.updateByIdOrFail(d);
        support.log(BIZ_TYPE, id, n.getDocNo(), "DOWNGRADE", "生成降级转换", n.getStatus().name(), n.getStatus().name(),
                QcSupport.plain(d.getQty()) + " " + m.code() + " → " + t.code());
        return follow;
    }

    /** 返工：生成“已计划”的返工生产订单（来源 = 本 NCR） */
    @Transactional(rollbackFor = Exception.class)
    public String createReworkOrder(Long id) {
        QcNcrDO n = get(id);
        QcNcrDispositionDO d = firstOpen(n, "REWORK");
        List<Long> ids = productionOrderApi.createFromMrp(List.of(new MrpSuggestion(n.getId(), n.getDocNo(), n.getMaterialId(), d.getQty(), LocalDate.now(),
                LocalDate.now().plusDays(7), null, null, null, "NCR " + n.getDocNo() + " 返工")));
        String follow = "返工生产订单（来源 " + n.getDocNo() + "）";
        d.setFollowDocNo(QcSupport.limit(follow, 64));
        dispMapper.updateByIdOrFail(d);
        support.log(BIZ_TYPE, id, n.getDocNo(), "REWORK_ORDER", "生成返工订单", n.getStatus().name(), n.getStatus().name(), ids.toString());
        return follow;
    }

    /**
     * 退供应商：通过资材 {@link PurchaseReturnApi} 生成草稿退货单（IQC 来源的 NCR 直接对应到货行，其余按供应商 + 物料 + 批次查找），
     * 处置记录登记退货单号，并给采购员发待办确认提交；找不到可退的到货记录时只发待办，由采购员手工创建。返回提示文字。
     */
    @Transactional(rollbackFor = Exception.class)
    public String notifyPurchaseReturn(Long id) {
        QcNcrDO n = get(id);
        QcNcrDispositionDO d = firstOpen(n, "RETURN");
        PurchaseReturnApi api = purchaseReturnApi.getIfAvailable();
        PurchaseReturnApi.DraftResult draft = null;
        String failure = null;
        if (api != null && n.getSupplierId() != null) {
            Long receiptLineId = null;
            QcInspectionDO ins = n.getInspectionId() == null ? null : inspectionMapper.selectById(n.getInspectionId());
            if (ins != null && "PUR_RECEIPT".equals(ins.getUpstreamType())) receiptLineId = ins.getUpstreamLineId();
            PurchaseReturnApi.DraftRequest req = new PurchaseReturnApi.DraftRequest(n.getSupplierId(), receiptLineId, n.getMaterialId(), n.getBatchNo(),
                    d.getQty(), ins != null && InspectType.IQC.name().equals(ins.getInspectType()) ? "IQC_REJECT" : "STOCK_DEFECT", n.getDocNo(),
                    "NCR " + n.getDocNo() + " 处置退供应商");
            try {
                draft = newTx.execute(s -> api.createDraft(req));
            } catch (BizException e) {
                failure = e.getMessage();
            }
        }
        if (draft != null) {
            d.setFollowDocNo(QcSupport.limit(draft.docNo(), 64));
            dispMapper.updateByIdOrFail(d);
        }
        List<Long> to = new ArrayList<>();
        if (n.getSupplierId() != null) {
            SupplierDTO s = support.suppliers(List.of(n.getSupplierId())).get(n.getSupplierId());
            if (s != null && s.buyerId() != null) to.add(s.buyerId());
        }
        if (to.isEmpty()) to.addAll(support.usersWithPermission("pur:return:create"));
        MaterialDTO m = support.material(n.getMaterialId());
        String what = m.code() + " " + QcSupport.plain(draft != null ? draft.qty() : d.getQty()) + (n.getBatchNo() == null ? "" : " 批次 " + n.getBatchNo());
        if (draft != null) {
            support.todo("QC_NCR_RETURN_" + d.getId(), to, BIZ_TYPE, n.getId(), n.getDocNo(),
                    "确认提交采购退货 " + draft.docNo() + "：" + what + "（NCR " + n.getDocNo() + "）", "/purchase/return/" + draft.returnId());
        } else {
            support.todo("QC_NCR_RETURN_" + d.getId(), to, BIZ_TYPE, n.getId(), n.getDocNo(),
                    "采购退货：" + what + "（NCR " + n.getDocNo() + "）", "/quality/ncr/" + n.getId());
        }
        support.log(BIZ_TYPE, id, n.getDocNo(), "PURCHASE_RETURN", "通知采购退货", n.getStatus().name(), n.getStatus().name(),
                draft != null ? "退货单 " + draft.docNo() : failure);
        return draft != null ? "已生成退货单 " + draft.docNo() + "（草稿），并通知采购员确认提交"
                : "已通知采购员创建采购退货" + (failure == null ? "" : "（" + failure + "）");
    }

    /** 生成 CAPA（带出物料、描述到 D2） */
    @Transactional(rollbackFor = Exception.class)
    public Long createCapa(Long id) {
        QcNcrDO n = get(id);
        if (n.getStatus() == DocStatus.VOIDED) throw BizException.of(QualityErrorCodes.STATUS_NOT_ALLOWED, n.getStatus().label(), "生成 CAPA");
        if (n.getCapaId() != null) throw BizException.of(QualityErrorCodes.NCR_FOLLOW_EXISTS, "CAPA", capaMapper.selectById(n.getCapaId()).getDocNo());
        QcCapaDO c = capaService.createFromNcr(n);
        linkCapa(id, c.getId());
        return c.getId();
    }

    /** 生成 SCAR（需要供应商） */
    @Transactional(rollbackFor = Exception.class)
    public Long createScar(Long id) {
        QcNcrDO n = get(id);
        if (n.getStatus() == DocStatus.VOIDED) throw BizException.of(QualityErrorCodes.STATUS_NOT_ALLOWED, n.getStatus().label(), "生成 SCAR");
        if (n.getScarId() != null) throw BizException.of(QualityErrorCodes.NCR_FOLLOW_EXISTS, "SCAR", scarMapper.selectById(n.getScarId()).getDocNo());
        QcScarDO s = scarService.createFromNcr(n);
        linkScar(id, s.getId());
        return s.getId();
    }

    /** 关联 CAPA / SCAR 后回写 */
    @Transactional(rollbackFor = Exception.class)
    public void linkCapa(Long ncrId, Long capaId) {
        QcNcrDO n = get(ncrId);
        n.setCapaId(capaId);
        mapper.updateByIdOrFail(n);
    }

    @Transactional(rollbackFor = Exception.class)
    public void linkScar(Long ncrId, Long scarId) {
        QcNcrDO n = get(ncrId);
        n.setScarId(scarId);
        mapper.updateByIdOrFail(n);
    }

    @Transactional(rollbackFor = Exception.class)
    public void linkComplaint(Long ncrId, Long complaintId) {
        QcNcrDO n = get(ncrId);
        n.setComplaintId(complaintId);
        mapper.updateByIdOrFail(n);
    }

    // ==================== 关闭 / 作废 ====================

    /** QC-NCR-R06：处置全部完成、需要的 CAPA / SCAR 已生成；可选解冻批次 */
    @Transactional(rollbackFor = Exception.class)
    public void close(Long id, boolean unfreeze) {
        QcNcrDO n = get(id);
        if (n.getStatus() != DocStatus.APPROVED) throw BizException.of(QualityErrorCodes.STATUS_NOT_ALLOWED, n.getStatus().label(), "关闭");
        if (n.getInspectionId() != null) {
            QcInspectionDO ins = inspectionMapper.selectById(n.getInspectionId());
            if (ins != null && Boolean.TRUE.equals(ins.getMrbSort()) && ins.getResult() == null) throw new BizException(QualityErrorCodes.NCR_DISP_UNDONE);
        }
        if (dispMapper.selectByParent(id).stream().anyMatch(d -> !Boolean.TRUE.equals(d.getDone()))) throw new BizException(QualityErrorCodes.NCR_DISP_UNDONE);
        if (Boolean.TRUE.equals(n.getCapaRequired()) && n.getCapaId() == null) throw new BizException(QualityErrorCodes.NCR_CAPA_REQUIRED);
        if (Boolean.TRUE.equals(n.getScarRequired()) && n.getScarId() == null) throw new BizException(QualityErrorCodes.NCR_SCAR_REQUIRED);
        if (unfreeze && Boolean.TRUE.equals(n.getBatchFrozen())) {
            batchApi.unfreeze(n.getMaterialId(), n.getBatchNo(), "NCR " + n.getDocNo() + " 关闭", QualityModuleConfig.MODULE);
            n.setBatchFrozen(false);
        }
        n.setClosedAt(LocalDateTime.now());
        fire(n, QcAction.CLOSE, unfreeze ? "解冻批次" : null);
        support.resolve("QC_NCR_CRITICAL_" + id);
    }

    @Transactional(rollbackFor = Exception.class)
    public void voidNcr(Long id, String reason) {
        QcNcrDO n = get(id);
        requireDraft(n);
        String r = QcSupport.requireText(reason, "作废原因");
        fire(n, QcAction.VOID, r);
        if (n.getInspectionId() != null) inspectionService.mrbBack(n.getInspectionId(), n.getDocNo());
    }

    private static void requireDraft(QcNcrDO n) {
        if (n.getStatus() == DocStatus.PENDING_APPROVAL) throw new BizException(QualityErrorCodes.DOC_PENDING);
        if (n.getStatus() != DocStatus.DRAFT) throw BizException.of(QualityErrorCodes.STATUS_NOT_ALLOWED, n.getStatus().label(), "修改");
    }

    private void fire(QcNcrDO n, QcAction action, String reason) {
        DocStatus from = n.getStatus();
        n.setStatus(QcStateMachines.NCR.fire(from, action));
        mapper.updateByIdOrFail(n);
        support.log(BIZ_TYPE, n.getId(), n.getDocNo(), action.name(), action.label(), from.name(), n.getStatus().name(), reason);
    }

    // ==================== 打印 ====================

    public Map<String, Object> printData(Long id) {
        NcrDetail d = detail(id);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("docNo", d.docNo());
        m.put("docDate", d.docDate());
        m.put("status", d.status());
        m.put("sourceName", d.source());
        m.put("sourceNo", d.sourceNo());
        m.put("materialCode", d.materialCode());
        m.put("materialName", d.materialName());
        m.put("batchNo", d.batchNo());
        m.put("ncrQty", d.ncrQty());
        m.put("partnerName", d.supplierName() != null ? d.supplierName() : d.customerName());
        m.put("severityName", switch (d.severity()) { case "CRITICAL" -> "致命"; case "MAJOR" -> "严重"; default -> "轻微"; });
        m.put("responsibilityName", support.dictLabel("qc_ncr_responsibility", d.responsibility()));
        m.put("defectDescription", d.defectDescription());
        m.put("containment", d.containment());
        m.put("ownerName", d.ownerName());
        m.put("dispositions", d.dispositions().stream().map(x -> {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("dispositionName", DISP_NAMES.getOrDefault(x.disposition(), x.disposition()));
            r.put("qty", x.qty());
            r.put("remark", x.remark());
            r.put("followDocNo", x.followDocNo());
            return r;
        }).toList());
        return m;
    }

    /** 统计用：全部 NCR（过滤在调用方） */
    public List<QcNcrDO> listBetween(LocalDate from, LocalDate to) {
        return mapper.selectList(new LambdaQueryWrapper<QcNcrDO>().ne(QcNcrDO::getStatus, DocStatus.VOIDED)
                .ge(from != null, QcNcrDO::getDocDate, from).le(to != null, QcNcrDO::getDocDate, to));
    }

    static Set<Long> nonNull(List<Long> ids) {
        return ids.stream().filter(Objects::nonNull).collect(Collectors.toSet());
    }
}
