package com.erp.module.quality.service.inspection;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.erp.common.enums.DocStatus;
import com.erp.common.exception.BizException;
import com.erp.common.result.PageResult;
import com.erp.framework.datascope.DataScopes;
import com.erp.module.crm.api.customer.CustomerDTO;
import com.erp.module.engineering.api.material.MaterialDTO;
import com.erp.module.production.api.order.OpenOrderDTO;
import com.erp.module.production.api.order.ProductionQueryApi;
import com.erp.module.purchase.api.supplier.SupplierDTO;
import com.erp.module.quality.api.QualityErrorCodes;
import com.erp.module.quality.api.inspection.InspectType;
import com.erp.module.quality.api.inspection.InspectionDTO;
import com.erp.module.quality.api.inspection.IpqcRejectDTO;
import com.erp.module.quality.config.QualityModuleConfig;
import com.erp.module.quality.controller.vo.BasicVOs.SamplingResult;
import com.erp.module.quality.controller.vo.InspectionVOs.DefectRow;
import com.erp.module.quality.controller.vo.InspectionVOs.InspectionDetail;
import com.erp.module.quality.controller.vo.InspectionVOs.InspectionQuery;
import com.erp.module.quality.controller.vo.InspectionVOs.InspectionRow;
import com.erp.module.quality.controller.vo.InspectionVOs.ItemResult;
import com.erp.module.quality.controller.vo.InspectionVOs.OperationOption;
import com.erp.module.quality.controller.vo.InspectionVOs.ProdOrderOption;
import com.erp.module.quality.controller.vo.InspectionVOs.QuickCounts;
import com.erp.module.quality.dal.dataobject.QcInspectionDO;
import com.erp.module.quality.dal.dataobject.QcInspectionDefectDO;
import com.erp.module.quality.dal.dataobject.QcInspectionItemDO;
import com.erp.module.quality.dal.dataobject.QcNcrDO;
import com.erp.module.quality.dal.dataobject.QcStandardDO;
import com.erp.module.quality.dal.mapper.QcInspectionDefectMapper;
import com.erp.module.quality.dal.mapper.QcInspectionItemMapper;
import com.erp.module.quality.dal.mapper.QcInspectionMapper;
import com.erp.module.quality.dal.mapper.QcNcrMapper;
import com.erp.module.quality.dal.mapper.QcStandardMapper;
import com.erp.module.quality.service.InspStatus;
import com.erp.module.quality.service.QcSupport;
import com.erp.module.system.api.user.UserDTO;
import com.erp.module.system.api.workflow.WorkflowApi;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 检验单查询、打印数据、对外查询 */
@Service
public class InspectionQueryService {

    static final List<String> UNJUDGED = List.of(InspStatus.PENDING.name(), InspStatus.INSPECTING.name(), InspStatus.WAIT_MRB.name());

    private final QcInspectionMapper mapper;
    private final QcInspectionItemMapper itemMapper;
    private final QcInspectionDefectMapper defectMapper;
    private final QcStandardMapper standardMapper;
    private final QcNcrMapper ncrMapper;
    private final InspectionService inspectionService;
    private final ProductionQueryApi productionQueryApi;
    private final WorkflowApi workflowApi;
    private final QcSupport support;

    public InspectionQueryService(QcInspectionMapper mapper, QcInspectionItemMapper itemMapper, QcInspectionDefectMapper defectMapper,
                                  QcStandardMapper standardMapper, QcNcrMapper ncrMapper, InspectionService inspectionService,
                                  ProductionQueryApi productionQueryApi, WorkflowApi workflowApi, QcSupport support) {
        this.mapper = mapper;
        this.itemMapper = itemMapper;
        this.defectMapper = defectMapper;
        this.standardMapper = standardMapper;
        this.ncrMapper = ncrMapper;
        this.inspectionService = inspectionService;
        this.productionQueryApi = productionQueryApi;
        this.workflowApi = workflowApi;
        this.support = support;
    }

    // ==================== 列表 ====================

    public PageResult<InspectionRow> page(InspectionQuery q) {
        PageResult<QcInspectionDO> p = mapper.selectPage(q, query(q));
        return new PageResult<>(rows(p.list()), p.total());
    }

    public List<InspectionRow> list(InspectionQuery q) {
        return rows(mapper.selectList(query(q)));
    }

    private int overdueHours() {
        return Math.max(1, support.params().getInt(QualityModuleConfig.P_OVERDUE_HOURS));
    }

    private LambdaQueryWrapper<QcInspectionDO> query(InspectionQuery q) {
        List<String> types = types(q.getTypes());
        List<String> statuses = StringUtils.hasText(q.getStatuses()) ? Arrays.asList(q.getStatuses().split(",")) : List.of();
        LambdaQueryWrapper<QcInspectionDO> w = new LambdaQueryWrapper<QcInspectionDO>()
                .in(!types.isEmpty(), QcInspectionDO::getInspectType, types)
                .like(StringUtils.hasText(q.getDocNo()), QcInspectionDO::getDocNo, q.getDocNo())
                .eq(q.getMaterialId() != null, QcInspectionDO::getMaterialId, q.getMaterialId())
                .like(StringUtils.hasText(q.getBatchNo()), QcInspectionDO::getBatchNo, q.getBatchNo())
                .eq(q.getSupplierId() != null, QcInspectionDO::getSupplierId, q.getSupplierId())
                .eq(q.getCustomerId() != null, QcInspectionDO::getCustomerId, q.getCustomerId())
                .like(StringUtils.hasText(q.getUpstreamNo()), QcInspectionDO::getUpstreamNo, q.getUpstreamNo())
                .eq(StringUtils.hasText(q.getResult()), QcInspectionDO::getResult, q.getResult())
                .eq(q.getInspectorId() != null, QcInspectionDO::getInspectorId, q.getInspectorId())
                .ge(q.getDateFrom() != null, QcInspectionDO::getCreatedAt, q.getDateFrom() == null ? null : q.getDateFrom().atStartOfDay())
                .lt(q.getDateTo() != null, QcInspectionDO::getCreatedAt, q.getDateTo() == null ? null : q.getDateTo().plusDays(1).atStartOfDay());
        if ("PENDING".equals(q.getQuick())) {
            w.in(QcInspectionDO::getInspStatus, List.of(InspStatus.PENDING.name(), InspStatus.INSPECTING.name()));
        } else if ("OVERDUE".equals(q.getQuick())) {
            w.in(QcInspectionDO::getInspStatus, UNJUDGED).lt(QcInspectionDO::getCreatedAt, LocalDateTime.now().minusHours(overdueHours()));
        } else if ("TODAY".equals(q.getQuick())) {
            w.ge(QcInspectionDO::getJudgeAt, LocalDate.now().atStartOfDay());
        } else {
            w.in(!statuses.isEmpty(), QcInspectionDO::getInspStatus, statuses);
        }
        return w.orderByDesc(QcInspectionDO::getCreatedAt).orderByDesc(QcInspectionDO::getId);
    }

    private static List<String> types(String text) {
        if (!StringUtils.hasText(text)) return List.of();
        return Arrays.stream(text.split(",")).map(String::trim).filter(StringUtils::hasText).map(t -> InspectType.valueOf(t).name()).toList();
    }

    public QuickCounts counts(String typesText) {
        List<String> types = types(typesText);
        long pending = mapper.selectCount(new LambdaQueryWrapper<QcInspectionDO>().in(!types.isEmpty(), QcInspectionDO::getInspectType, types)
                .in(QcInspectionDO::getInspStatus, List.of(InspStatus.PENDING.name(), InspStatus.INSPECTING.name())));
        long overdue = mapper.selectCount(new LambdaQueryWrapper<QcInspectionDO>().in(!types.isEmpty(), QcInspectionDO::getInspectType, types)
                .in(QcInspectionDO::getInspStatus, UNJUDGED).lt(QcInspectionDO::getCreatedAt, LocalDateTime.now().minusHours(overdueHours())));
        long today = mapper.selectCount(new LambdaQueryWrapper<QcInspectionDO>().in(!types.isEmpty(), QcInspectionDO::getInspectType, types)
                .ge(QcInspectionDO::getJudgeAt, LocalDate.now().atStartOfDay()));
        return new QuickCounts(pending, overdue, today);
    }

    private List<InspectionRow> rows(List<QcInspectionDO> list) {
        Map<Long, MaterialDTO> ms = support.materials(list.stream().map(QcInspectionDO::getMaterialId).toList());
        Map<Long, SupplierDTO> sups = support.suppliers(list.stream().map(QcInspectionDO::getSupplierId).toList());
        Map<Long, CustomerDTO> cus = support.customers(list.stream().map(QcInspectionDO::getCustomerId).toList());
        Map<Long, UserDTO> users = support.users(list.stream().map(QcInspectionDO::getInspectorId).toList());
        int hours = overdueHours();
        LocalDateTime now = LocalDateTime.now();
        return list.stream().map(d -> {
            MaterialDTO m = ms.get(d.getMaterialId());
            boolean unjudged = UNJUDGED.contains(d.getInspStatus());
            long wait = unjudged && d.getCreatedAt() != null ? Duration.between(d.getCreatedAt(), now).toMinutes() : 0;
            return new InspectionRow(d.getId(), d.getDocNo(), d.getInspectType(), d.getIpqcKind(), d.getMaterialId(), m == null ? null : m.code(),
                    m == null ? null : m.name(), m == null ? null : m.spec(), d.getBatchNo(), d.getSupplierId(),
                    sups.containsKey(d.getSupplierId()) ? sups.get(d.getSupplierId()).name() : null, d.getCustomerId(), QcSupport.customerName(cus.get(d.getCustomerId())),
                    d.getLotQty(), d.getSampleQty(), d.getUpstreamType(), d.getUpstreamId(), d.getUpstreamNo(),
                    InspectionService.SRC_REPORT.equals(d.getSourceType()) || "IPQC".equals(d.getInspectType()) ? d.getUpstreamNo() : null,
                    d.getOperationSeq(), wait, unjudged && wait >= hours * 60L, d.getSuggestedResult(), d.getResult(), d.getQualifiedQty(), d.getConcessionQty(),
                    d.getRejectedQty(), d.getInspectorId(), QcSupport.name(users, d.getInspectorId()), d.getInspStatus(), d.getCreatedAt(), d.getJudgeAt());
        }).toList();
    }

    // ==================== 详情 ====================

    public InspectionDetail detail(Long id) {
        QcInspectionDO d = inspectionService.get(id);
        MaterialDTO m = support.material(d.getMaterialId());
        SupplierDTO sup = d.getSupplierId() == null ? null : support.suppliers(List.of(d.getSupplierId())).get(d.getSupplierId());
        CustomerDTO cus = d.getCustomerId() == null ? null : support.customers(List.of(d.getCustomerId())).get(d.getCustomerId());
        QcStandardDO std = d.getStandardId() == null ? null : standardMapper.selectById(d.getStandardId());
        QcNcrDO ncr = d.getNcrId() == null ? null : ncrMapper.selectById(d.getNcrId());
        Map<Long, UserDTO> users = support.users(Arrays.asList(d.getInspectorId(), d.getJudgeBy()));
        List<ItemResult> items = itemMapper.selectByParent(id).stream().map(this::item).toList();
        List<DefectRow> defects = defectMapper.selectByParent(id).stream().map(x -> new DefectRow(x.getId(), x.getDefectCode(), x.getDefectName(),
                x.getDefectLevel(), x.getQty(), x.getDescription(), QcSupport.ids(x.getImageFileIds()))).toList();
        return new InspectionDetail(d.getId(), d.getDocNo(), d.getInspectType(), d.getIpqcKind(), d.getMaterialId(), m.code(), m.name(), m.spec(), m.baseUom(),
                d.getBatchNo(), d.getLotQty(), d.getSupplierId(), sup == null ? null : sup.name(), d.getCustomerId(), QcSupport.customerName(cus),
                d.getSourceType(), d.getSourceId(), d.getSourceNo(), d.getUpstreamType(), d.getUpstreamId(), d.getUpstreamLineId(), d.getUpstreamNo(),
                d.getProdOrderId(), d.getOperationSeq(), d.getWarehouseId(), d.getStandardId(), d.getStandardCode(), d.getStandardVersion(),
                std == null ? null : std.getName(), std == null ? null : std.getFileId(), inspectionService.sampling(d), d.getSampleQty(), d.getInspectorId(),
                QcSupport.name(users, d.getInspectorId()), d.getStartedAt(), d.getInspectedAt(), d.getSuggestedResult(), d.getResult(), d.getQualifiedQty(),
                d.getConcessionQty(), d.getRejectedQty(), d.getCrCount(), d.getMaCount(), d.getMiCount(), d.getJudgeBy(), QcSupport.name(users, d.getJudgeBy()),
                d.getJudgeAt(), d.getJudgeReason(), d.getNcrId(), ncr == null ? null : ncr.getDocNo(), d.getInspStatus(), Boolean.TRUE.equals(d.getMrbSort()),
                d.getPresetConcessionQty(), d.getPresetRejectedQty(), QcSupport.ids(d.getTransferIds()), d.getRejudgeCount() == null ? 0 : d.getRejudgeCount(),
                workflowApi.isRunning(QualityModuleConfig.REJUDGE, id), d.getRemark(), d.getCreatedAt(), d.getVersion(), items, defects);
    }

    private ItemResult item(QcInspectionItemDO i) {
        List<BigDecimal> values = support.fromJson(i.getMeasuredValues(), new TypeReference<List<BigDecimal>>() {
        });
        return new ItemResult(i.getId(), i.getSeq(), i.getItemName(), i.getItemType(), i.getMethod(), i.getUnit(), i.getSpec(), i.getTarget(), i.getUpperLimit(),
                i.getLowerLimit(), i.getDefectLevel(), Boolean.TRUE.equals(i.getIsKey()), i.getSampleQty(), values == null ? List.of() : values,
                i.getNgCount() == null ? 0 : i.getNgCount(), i.getItemResult(), i.getRemark());
    }

    /** 检验报告打印数据 */
    public Map<String, Object> printData(Long id) {
        InspectionDetail d = detail(id);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("docNo", d.docNo());
        m.put("inspectTypeName", InspectType.valueOf(d.inspectType()).label());
        m.put("status", InspStatus.PENDING.name().equals(d.status()) || InspStatus.INSPECTING.name().equals(d.status()) ? DocStatus.DRAFT.name()
                : InspStatus.CANCELED.name().equals(d.status()) ? "CANCELED" : d.status());
        m.put("materialCode", d.materialCode());
        m.put("materialName", d.materialName());
        m.put("materialSpec", d.materialSpec());
        m.put("batchNo", d.batchNo());
        m.put("lotQty", d.lotQty());
        m.put("sampleQty", d.sampleQty());
        SamplingResult s = d.sampling();
        m.put("samplingText", s == null ? "" : s.text());
        m.put("partnerName", d.supplierName() != null ? d.supplierName() : d.customerName());
        m.put("upstreamNo", d.upstreamNo());
        m.put("standardText", d.standardCode() == null ? "" : d.standardCode() + " V" + d.standardVersion() + " " + (d.standardName() == null ? "" : d.standardName()));
        m.put("crCount", d.crCount());
        m.put("maCount", d.maCount());
        m.put("miCount", d.miCount());
        m.put("resultName", InspectionService.resultLabel(d.result()));
        m.put("qualifiedQty", d.qualifiedQty());
        m.put("concessionQty", d.concessionQty());
        m.put("rejectedQty", d.rejectedQty());
        m.put("inspectorName", d.inspectorName());
        m.put("judgeName", d.judgeName());
        m.put("judgeAt", d.judgeAt());
        m.put("items", d.items().stream().map(i -> {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("seq", i.seq());
            r.put("itemName", i.itemName());
            r.put("spec", specText(i));
            r.put("levelName", i.defectLevel());
            r.put("sampleQty", i.sampleQty());
            r.put("valuesText", i.measuredValues().stream().map(BigDecimal::toPlainString).collect(Collectors.joining(", ")));
            r.put("ngCount", i.ngCount());
            r.put("itemResult", i.itemResult());
            return r;
        }).toList());
        m.put("defects", d.defects().stream().map(x -> {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("defectCode", x.defectCode());
            r.put("defectName", x.defectName());
            r.put("defectLevel", x.defectLevel());
            r.put("qty", x.qty());
            r.put("description", x.description());
            return r;
        }).toList());
        return m;
    }

    static String specText(ItemResult i) {
        StringBuilder sb = new StringBuilder(i.spec() == null ? "" : i.spec());
        if (i.lowerLimit() != null || i.upperLimit() != null) {
            if (!sb.isEmpty()) sb.append(" ");
            sb.append(i.lowerLimit() == null ? "" : i.lowerLimit().stripTrailingZeros().toPlainString()).append(" ~ ")
                    .append(i.upperLimit() == null ? "" : i.upperLimit().stripTrailingZeros().toPlainString());
            if (i.unit() != null) sb.append(" ").append(i.unit());
        }
        return sb.toString();
    }

    // ==================== 对外查询 ====================

    public List<InspectionDTO> getByBiz(String upstreamType, Long upstreamId) {
        return mapper.selectList(new LambdaQueryWrapper<QcInspectionDO>().eq(QcInspectionDO::getUpstreamType, upstreamType).eq(QcInspectionDO::getUpstreamId, upstreamId)
                .ne(QcInspectionDO::getInspStatus, InspStatus.CANCELED.name()).orderByAsc(QcInspectionDO::getId)).stream().map(InspectionQueryService::dto).toList();
    }

    static InspectionDTO dto(QcInspectionDO d) {
        return new InspectionDTO(d.getId(), d.getDocNo(), InspectType.valueOf(d.getInspectType()), d.getIpqcKind(), d.getMaterialId(), d.getBatchNo(), d.getLotQty(),
                d.getUpstreamType(), d.getUpstreamId(), d.getUpstreamLineId(), d.getUpstreamNo(), d.getInspStatus(), d.getResult(), d.getQualifiedQty(),
                d.getConcessionQty(), d.getRejectedQty(), d.getJudgeAt(), d.getNcrId());
    }

    public boolean isOqcPassed(Long noticeId) {
        List<QcInspectionDO> list = mapper.selectList(new LambdaQueryWrapper<QcInspectionDO>().eq(QcInspectionDO::getInspectType, InspectType.OQC.name())
                .eq(QcInspectionDO::getUpstreamType, InspectionService.SRC_NOTICE).eq(QcInspectionDO::getUpstreamId, noticeId)
                .ne(QcInspectionDO::getInspStatus, InspStatus.CANCELED.name()));
        if (list.isEmpty()) return false;
        return list.stream().allMatch(d -> (InspStatus.JUDGED.name().equals(d.getInspStatus()) || InspStatus.HANDLED.name().equals(d.getInspStatus()))
                && !InspectionService.REJECTED.equals(d.getResult()));
    }

    /** QC-INS-R09：参数打开时，首件检验判定合格（或特采 / 挑选）前阻止报工 */
    public void checkFirstArticle(Long prodOrderId) {
        if (!support.params().getBool(QualityModuleConfig.P_FIRST_ARTICLE)) return;
        boolean passed = mapper.selectCount(new LambdaQueryWrapper<QcInspectionDO>().eq(QcInspectionDO::getInspectType, InspectType.IPQC.name())
                .eq(QcInspectionDO::getIpqcKind, "FIRST").eq(QcInspectionDO::getProdOrderId, prodOrderId)
                .in(QcInspectionDO::getInspStatus, List.of(InspStatus.JUDGED.name(), InspStatus.HANDLED.name()))
                .ne(QcInspectionDO::getResult, InspectionService.REJECTED)) > 0;
        if (!passed) throw new BizException(QualityErrorCodes.INS_FIRST_ARTICLE);
    }

    /** 生产订单各工序最近一次已判定的 IPQC 为拒收时返回（跳过数据权限：供生产订单详情显示警示） */
    public List<IpqcRejectDTO> ipqcRejected(Long prodOrderId) {
        if (prodOrderId == null) return List.of();
        List<QcInspectionDO> list = DataScopes.ignore(() -> mapper.selectList(new LambdaQueryWrapper<QcInspectionDO>()
                .eq(QcInspectionDO::getInspectType, InspectType.IPQC.name()).eq(QcInspectionDO::getProdOrderId, prodOrderId)
                .isNotNull(QcInspectionDO::getOperationSeq)
                .in(QcInspectionDO::getInspStatus, List.of(InspStatus.JUDGED.name(), InspStatus.HANDLED.name()))
                .orderByDesc(QcInspectionDO::getJudgeAt).orderByDesc(QcInspectionDO::getId)));
        Map<Integer, QcInspectionDO> latest = new LinkedHashMap<>();
        for (QcInspectionDO d : list) latest.putIfAbsent(d.getOperationSeq(), d);
        return latest.values().stream().filter(d -> InspectionService.REJECTED.equals(d.getResult()))
                .map(d -> new IpqcRejectDTO(d.getOperationSeq(), d.getId(), d.getDocNo(), d.getJudgeAt(), d.getNcrId())).toList();
    }

    /** 新建 IPQC 时的生产订单选项 */
    public List<ProdOrderOption> prodOrders(String keyword) {
        List<OpenOrderDTO> orders = productionQueryApi.getOpenOrders(null).stream()
                .filter(o -> List.of("RELEASED", "IN_PROGRESS", "SUSPENDED").contains(o.prodStatus()))
                .filter(o -> !StringUtils.hasText(keyword) || o.docNo().contains(keyword.trim())).limit(50).toList();
        Map<Long, MaterialDTO> ms = support.materials(orders.stream().map(OpenOrderDTO::materialId).toList());
        List<ProdOrderOption> out = new ArrayList<>();
        for (OpenOrderDTO o : orders) {
            MaterialDTO m = ms.get(o.materialId());
            out.add(new ProdOrderOption(o.id(), o.docNo(), o.materialId(), m == null ? null : m.code(), m == null ? null : m.name(), o.qty(), o.prodStatus(),
                    o.operations() == null ? List.of() : o.operations().stream().map(op -> new OperationOption(op.seq(), op.operation())).toList()));
        }
        return out;
    }
}
