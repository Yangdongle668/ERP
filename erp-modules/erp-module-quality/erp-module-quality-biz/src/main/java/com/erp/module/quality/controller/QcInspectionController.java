package com.erp.module.quality.controller;

import com.erp.common.exception.BizException;
import com.erp.common.exception.GlobalErrorCodes;
import com.erp.common.result.CommonResult;
import com.erp.common.result.PageResult;
import com.erp.framework.excel.ExcelColumn;
import com.erp.module.quality.api.inspection.InspectType;
import com.erp.module.quality.controller.vo.CommonVOs.IdsReq;
import com.erp.module.quality.controller.vo.CommonVOs.ReasonReq;
import com.erp.module.quality.controller.vo.InspectionVOs.BatchJudgeResult;
import com.erp.module.quality.controller.vo.InspectionVOs.InspectionDetail;
import com.erp.module.quality.controller.vo.InspectionVOs.InspectionQuery;
import com.erp.module.quality.controller.vo.InspectionVOs.InspectionRow;
import com.erp.module.quality.controller.vo.InspectionVOs.JudgeReq;
import com.erp.module.quality.controller.vo.InspectionVOs.ManualCreate;
import com.erp.module.quality.controller.vo.InspectionVOs.ProdOrderOption;
import com.erp.module.quality.controller.vo.InspectionVOs.QuickCounts;
import com.erp.module.quality.controller.vo.InspectionVOs.ResultsSave;
import com.erp.module.quality.service.QcExportSupport;
import com.erp.module.quality.service.QcSupport;
import com.erp.module.quality.service.inspection.InspectionManualService;
import com.erp.module.quality.service.inspection.InspectionQueryService;
import com.erp.module.quality.service.inspection.InspectionService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** 检验单（各类型同一接口，按 types 过滤；类型级权限在接口内校验） */
@Tag(name = "品质 - 检验单")
@RestController
@RequestMapping("/api/quality/inspections")
public class QcInspectionController {

    static final String ANY_QUERY = "@ss.hasAny('qc:iqc:query','qc:ipqc:query','qc:fqc:query','qc:oqc:query','qc:return:query')";
    static final String ANY_INSPECT = "@ss.hasAny('qc:iqc:inspect','qc:ipqc:inspect','qc:fqc:inspect','qc:oqc:inspect','qc:return:inspect')";
    static final String ANY_JUDGE = "@ss.hasAny('qc:iqc:judge','qc:ipqc:judge','qc:fqc:judge','qc:oqc:judge','qc:return:judge')";

    static final List<ExcelColumn<InspectionRow>> EXPORT_COLUMNS = List.of(
            ExcelColumn.text("docNo", "单号", InspectionRow::docNo),
            ExcelColumn.text("inspectType", "类型", r -> InspectType.valueOf(r.inspectType()).label()),
            ExcelColumn.text("materialCode", "物料编码", InspectionRow::materialCode),
            ExcelColumn.text("materialName", "物料名称", InspectionRow::materialName),
            ExcelColumn.text("batchNo", "批次", InspectionRow::batchNo),
            ExcelColumn.text("partner", "供应商/客户", r -> r.supplierName() != null ? r.supplierName() : r.customerName()),
            ExcelColumn.number("lotQty", "批量", InspectionRow::lotQty),
            ExcelColumn.number("sampleQty", "样本量", InspectionRow::sampleQty),
            ExcelColumn.text("upstreamNo", "上游单号", InspectionRow::upstreamNo),
            ExcelColumn.text("result", "结果", r -> InspectionService.resultLabel(r.result())),
            ExcelColumn.number("qualifiedQty", "合格数", InspectionRow::qualifiedQty),
            ExcelColumn.number("concessionQty", "特采数", InspectionRow::concessionQty),
            ExcelColumn.number("rejectedQty", "不合格数", InspectionRow::rejectedQty),
            ExcelColumn.text("inspectorName", "检验员", InspectionRow::inspectorName),
            ExcelColumn.text("status", "状态", InspectionRow::status),
            ExcelColumn.dateTime("createdAt", "创建时间", InspectionRow::createdAt));

    private final InspectionService service;
    private final InspectionQueryService queryService;
    private final InspectionManualService manualService;
    private final QcExportSupport exportSupport;
    private final QcSupport support;

    public QcInspectionController(InspectionService service, InspectionQueryService queryService, InspectionManualService manualService,
                                  QcExportSupport exportSupport, QcSupport support) {
        this.service = service;
        this.queryService = queryService;
        this.manualService = manualService;
        this.exportSupport = exportSupport;
        this.support = support;
    }

    /** 请求的每个类型都需要对应的查看权限 */
    private void requireQuery(String types) {
        List<String> list = StringUtils.hasText(types) ? Arrays.asList(types.split(",")) : Arrays.stream(InspectType.values()).map(Enum::name).toList();
        for (String t : list) {
            if (!support.hasPermission("qc:" + InspectionService.permSegment(t.trim()) + ":query")) throw new BizException(GlobalErrorCodes.FORBIDDEN);
        }
    }

    private void requireQuery(Long id) {
        requireQuery(service.get(id).getInspectType());
    }

    @GetMapping
    @PreAuthorize(ANY_QUERY)
    public CommonResult<PageResult<InspectionRow>> page(@Valid InspectionQuery q) {
        requireQuery(q.getTypes());
        return CommonResult.success(queryService.page(q));
    }

    @GetMapping("/counts")
    @PreAuthorize(ANY_QUERY)
    public CommonResult<QuickCounts> counts(@RequestParam(required = false) String types) {
        requireQuery(types);
        return CommonResult.success(queryService.counts(types));
    }

    @GetMapping("/export")
    @PreAuthorize(ANY_QUERY)
    public void export(@Valid InspectionQuery q, @RequestParam(required = false) String columns, HttpServletResponse response) throws IOException {
        requireQuery(q.getTypes());
        exportSupport.export(response, "检验单", EXPORT_COLUMNS, columns, limit -> queryService.list(q).stream().limit(limit).toList());
    }

    @GetMapping("/{id}")
    @PreAuthorize(ANY_QUERY)
    public CommonResult<InspectionDetail> detail(@PathVariable Long id) {
        requireQuery(id);
        return CommonResult.success(queryService.detail(id));
    }

    @GetMapping("/{id}/print-data")
    @PreAuthorize(ANY_QUERY)
    public CommonResult<Map<String, Object>> printData(@PathVariable Long id, @RequestParam(required = false) String lang) {
        requireQuery(id);
        return CommonResult.success(queryService.printData(id));
    }

    /** 手工新建：IPQC（首件 / 巡检 / 末件）或复检 */
    @PostMapping
    @PreAuthorize("@ss.has('qc:inspection:create')")
    public CommonResult<Long> create(@Valid @RequestBody ManualCreate req) {
        return CommonResult.success(manualService.create(req));
    }

    @GetMapping("/prod-orders")
    @PreAuthorize("@ss.has('qc:inspection:create')")
    public CommonResult<List<ProdOrderOption>> prodOrders(@RequestParam(required = false) String keyword) {
        return CommonResult.success(queryService.prodOrders(keyword));
    }

    @PutMapping("/{id}/results")
    @PreAuthorize(ANY_INSPECT)
    public CommonResult<Void> saveResults(@PathVariable Long id, @Valid @RequestBody ResultsSave req) {
        service.saveResults(id, req);
        return CommonResult.success();
    }

    @PostMapping("/{id}/judge")
    @PreAuthorize(ANY_JUDGE)
    public CommonResult<Void> judge(@PathVariable Long id, @Valid @RequestBody JudgeReq req) {
        service.judge(id, req);
        return CommonResult.success();
    }

    @PostMapping("/batch-judge-pass")
    @PreAuthorize(ANY_JUDGE)
    public CommonResult<BatchJudgeResult> batchJudgePass(@RequestBody IdsReq req) {
        return CommonResult.success(service.batchJudgePass(req.ids()));
    }

    @PostMapping("/{id}/to-mrb")
    @PreAuthorize(ANY_JUDGE)
    public CommonResult<Long> toMrb(@PathVariable Long id) {
        return CommonResult.success(service.toMrb(id));
    }

    @PostMapping("/{id}/rejudge")
    @PreAuthorize("@ss.has('qc:inspection:rejudge')")
    public CommonResult<Void> rejudge(@PathVariable Long id, @RequestBody ReasonReq req) {
        service.rejudge(id, req.reason());
        return CommonResult.success();
    }
}
