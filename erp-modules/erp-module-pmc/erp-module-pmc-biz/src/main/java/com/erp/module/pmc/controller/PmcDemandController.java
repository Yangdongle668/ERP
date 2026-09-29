package com.erp.module.pmc.controller;

import com.erp.common.result.CommonResult;
import com.erp.common.result.PageResult;
import com.erp.framework.excel.ExcelColumn;
import com.erp.module.pmc.controller.vo.DemandVOs.DemandQuery;
import com.erp.module.pmc.controller.vo.DemandVOs.DemandRow;
import com.erp.module.pmc.controller.vo.DemandVOs.DemandSave;
import com.erp.module.pmc.controller.vo.DemandVOs.KitReq;
import com.erp.module.pmc.controller.vo.DemandVOs.KitResult;
import com.erp.module.pmc.controller.vo.DemandVOs.ReplyReq;
import com.erp.module.pmc.controller.vo.DemandVOs.ReplyRow;
import com.erp.module.pmc.service.PmcExportSupport;
import com.erp.module.pmc.service.demand.DeliveryReplyService;
import com.erp.module.pmc.service.demand.DemandService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;

@Tag(name = "PMC - 需求池与交期回复")
@RestController
@RequestMapping("/api/pmc")
public class PmcDemandController {

    static final List<ExcelColumn<DemandRow>> EXPORT_COLUMNS = List.of(
            ExcelColumn.text("demandType", "需求类型", DemandRow::demandType),
            ExcelColumn.text("sourceNo", "来源单号", DemandRow::sourceNo),
            ExcelColumn.text("customerName", "客户", DemandRow::customerName),
            ExcelColumn.text("materialCode", "物料编码", DemandRow::materialCode),
            ExcelColumn.text("materialName", "名称", DemandRow::materialName),
            ExcelColumn.text("materialSpec", "规格", DemandRow::materialSpec),
            ExcelColumn.number("qty", "需求数量", DemandRow::qty),
            ExcelColumn.number("fulfilledQty", "已满足", DemandRow::fulfilledQty),
            ExcelColumn.number("openQty", "未满足", DemandRow::openQty),
            ExcelColumn.date("requiredDate", "需求日期", DemandRow::requiredDate),
            ExcelColumn.date("customerDate", "要求交期", DemandRow::customerDate),
            ExcelColumn.date("promisedDate", "承诺交期", DemandRow::promisedDate),
            ExcelColumn.number("availableQty", "可用库存", DemandRow::availableQty),
            ExcelColumn.number("wipQty", "在制", DemandRow::wipQty),
            ExcelColumn.number("inTransitQty", "在途", DemandRow::inTransitQty),
            ExcelColumn.number("priority", "优先级", DemandRow::priority),
            ExcelColumn.text("demandStatus", "状态", DemandRow::demandStatus),
            ExcelColumn.text("remark", "说明", DemandRow::remark));

    private final DemandService service;
    private final DeliveryReplyService replyService;
    private final PmcExportSupport exportSupport;

    public PmcDemandController(DemandService service, DeliveryReplyService replyService, PmcExportSupport exportSupport) {
        this.service = service;
        this.replyService = replyService;
        this.exportSupport = exportSupport;
    }

    @GetMapping("/demands")
    @PreAuthorize("@ss.has('pmc:demand:query')")
    public CommonResult<PageResult<DemandRow>> page(@Valid DemandQuery q) {
        return CommonResult.success(service.page(q));
    }

    @GetMapping("/demands/export")
    @PreAuthorize("@ss.has('pmc:demand:query')")
    public void export(DemandQuery q, HttpServletResponse response) throws IOException {
        exportSupport.export(response, "需求池", EXPORT_COLUMNS, null, limit -> {
            q.setPageNo(1);
            q.setPageSize(limit);
            return service.page(q).list();
        });
    }

    @PostMapping("/demands")
    @PreAuthorize("@ss.has('pmc:demand:create')")
    public CommonResult<Long> create(@Valid @RequestBody DemandSave req) {
        return CommonResult.success(service.create(req));
    }

    @PutMapping("/demands/{id}")
    @PreAuthorize("@ss.has('pmc:demand:create')")
    public CommonResult<Void> update(@PathVariable Long id, @Valid @RequestBody DemandSave req) {
        service.update(id, req);
        return CommonResult.success();
    }

    @PostMapping("/demands/{id}/close")
    @PreAuthorize("@ss.has('pmc:demand:create')")
    public CommonResult<Void> close(@PathVariable Long id) {
        service.close(id);
        return CommonResult.success();
    }

    @PostMapping("/demands/reconcile")
    @PreAuthorize("@ss.has('pmc:demand:create')")
    public CommonResult<String> reconcile() {
        return CommonResult.success(service.reconcile());
    }

    // ==================== 交期回复 ====================

    @GetMapping("/delivery-replies/pending")
    @PreAuthorize("@ss.hasAny('pmc:delivery:reply', 'pmc:demand:query')")
    public CommonResult<List<ReplyRow>> pending(@RequestParam(required = false) Long customerId, @RequestParam(required = false) Long materialId) {
        return CommonResult.success(replyService.pending(customerId, materialId));
    }

    @PostMapping("/delivery-replies")
    @PreAuthorize("@ss.has('pmc:delivery:reply')")
    public CommonResult<Integer> reply(@Valid @RequestBody ReplyReq req) {
        return CommonResult.success(replyService.reply(req.lines()));
    }

    @PostMapping("/delivery-replies/kit-analysis")
    @PreAuthorize("@ss.hasAny('pmc:delivery:reply', 'pmc:demand:query')")
    public CommonResult<KitResult> kit(@Valid @RequestBody KitReq req) {
        return CommonResult.success(replyService.kit(req.materialId(), req.qty()));
    }
}
