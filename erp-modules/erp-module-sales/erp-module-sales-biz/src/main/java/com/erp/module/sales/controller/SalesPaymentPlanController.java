package com.erp.module.sales.controller;

import com.erp.common.result.CommonResult;
import com.erp.common.result.PageResult;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.erp.framework.excel.ExcelColumn;
import com.erp.module.sales.controller.vo.OrderVOs.FollowUpReq;
import com.erp.module.sales.controller.vo.OrderVOs.PaymentPlanQuery;
import com.erp.module.sales.controller.vo.OrderVOs.PaymentPlanRow;
import com.erp.module.sales.controller.vo.OrderVOs.PaymentSummary;
import com.erp.module.sales.service.SalExportSupport;
import com.erp.module.sales.service.order.PaymentPlanService;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.List;

/** 回款跟踪（需求 04-07） */
@Tag(name = "销售 - 回款计划")
@RestController
@RequestMapping("/api/sales")
public class SalesPaymentPlanController {

    static final List<ExcelColumn<PaymentPlanRow>> EXPORT_COLUMNS = List.of(
            ExcelColumn.text("orderNo", "订单号", PaymentPlanRow::orderNo),
            ExcelColumn.text("customerName", "客户", PaymentPlanRow::customerName),
            ExcelColumn.text("ownerName", "业务员", PaymentPlanRow::ownerName),
            ExcelColumn.text("nodeName", "节点", PaymentPlanRow::nodeName),
            ExcelColumn.number("percent", "比例", PaymentPlanRow::percent),
            ExcelColumn.text("currency", "币别", PaymentPlanRow::currency),
            ExcelColumn.number("planAmount", "计划金额", PaymentPlanRow::planAmount),
            ExcelColumn.date("dueDate", "到期日", PaymentPlanRow::dueDate),
            ExcelColumn.number("overdueDays", "逾期天数", PaymentPlanRow::overdueDays),
            ExcelColumn.number("receivedAmount", "已收", PaymentPlanRow::receivedAmount),
            ExcelColumn.number("unreceivedAmount", "未收", PaymentPlanRow::unreceivedAmount),
            ExcelColumn.text("planStatus", "状态", PaymentPlanRow::planStatus),
            ExcelColumn.text("remark", "催收备注", PaymentPlanRow::remark));

    private final PaymentPlanService service;
    private final SalExportSupport exportSupport;

    public SalesPaymentPlanController(PaymentPlanService service, SalExportSupport exportSupport) {
        this.service = service;
        this.exportSupport = exportSupport;
    }

    @GetMapping("/payment-plans")
    @PreAuthorize("@ss.has('sales:order:query')")
    public CommonResult<PageResult<PaymentPlanRow>> page(@Valid PaymentPlanQuery q) {
        return CommonResult.success(service.page(q));
    }

    @GetMapping("/payment-plans/summary")
    @PreAuthorize("@ss.has('sales:order:query')")
    public CommonResult<PaymentSummary> summary() {
        return CommonResult.success(service.summary());
    }

    /** 记录催收：备注 + 客户承诺付款日期 */
    @PostMapping("/payment-plans/{id}/follow-up")
    @PreAuthorize("@ss.has('sales:order:query')")
    public CommonResult<Void> followUp(@PathVariable Long id, @Valid @RequestBody FollowUpReq req) {
        service.followUp(id, req);
        return CommonResult.success();
    }

    @GetMapping("/payment-plans/export")
    @PreAuthorize("@ss.has('sales:order:export')")
    public void export(@Valid PaymentPlanQuery q, HttpServletResponse response) throws IOException {
        exportSupport.export(response, "回款计划", EXPORT_COLUMNS, q.getColumns(), limit -> {
            q.setPageNo(1);
            q.setPageSize(Math.min(limit, 50000));
            return service.page(q).list();
        });
    }
}
