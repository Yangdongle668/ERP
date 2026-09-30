package com.erp.module.finance.controller;

import com.erp.common.result.CommonResult;
import com.erp.framework.excel.ExcelColumn;
import com.erp.module.finance.controller.vo.CostVOs.CheckResult;
import com.erp.module.finance.controller.vo.CostVOs.ExceptionVO;
import com.erp.module.finance.controller.vo.CostVOs.ExpenseRow;
import com.erp.module.finance.controller.vo.CostVOs.ExpenseSave;
import com.erp.module.finance.controller.vo.CostVOs.MaterialCostRow;
import com.erp.module.finance.controller.vo.CostVOs.OrderCostVO;
import com.erp.module.finance.controller.vo.CostVOs.ProductCostRow;
import com.erp.module.finance.controller.vo.CostVOs.RunVO;
import com.erp.module.finance.service.FinExportSupport;
import com.erp.module.finance.service.cost.CostService;
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

@Tag(name = "财务 - 成本核算")
@RestController
@RequestMapping("/api/finance/cost")
public class FinCostController {

    static final List<ExcelColumn<MaterialCostRow>> MATERIAL_COLUMNS = List.of(
            ExcelColumn.text("materialCode", "物料编码", MaterialCostRow::materialCode),
            ExcelColumn.text("materialName", "物料名称", MaterialCostRow::materialName),
            ExcelColumn.text("spec", "规格", MaterialCostRow::spec),
            ExcelColumn.number("openingQty", "期初数量", MaterialCostRow::openingQty),
            ExcelColumn.number("openingAmount", "期初金额", MaterialCostRow::openingAmount),
            ExcelColumn.number("inQty", "入库数量", MaterialCostRow::inQty),
            ExcelColumn.number("inAmount", "入库金额", MaterialCostRow::inAmount),
            ExcelColumn.number("unitCost", "加权单价", MaterialCostRow::unitCost),
            ExcelColumn.number("outQty", "出库数量", MaterialCostRow::outQty),
            ExcelColumn.number("outAmount", "出库金额", MaterialCostRow::outAmount),
            ExcelColumn.number("closingQty", "期末数量", MaterialCostRow::closingQty),
            ExcelColumn.number("closingAmount", "期末金额", MaterialCostRow::closingAmount));

    static final List<ExcelColumn<ProductCostRow>> PRODUCT_COLUMNS = List.of(
            ExcelColumn.text("materialCode", "产品编码", ProductCostRow::materialCode),
            ExcelColumn.text("materialName", "产品名称", ProductCostRow::materialName),
            ExcelColumn.number("finishedQty", "完工数量", ProductCostRow::finishedQty),
            ExcelColumn.number("unitCost", "单位成本", ProductCostRow::unitCost),
            ExcelColumn.number("materialCost", "材料", ProductCostRow::materialCost),
            ExcelColumn.number("laborCost", "人工", ProductCostRow::laborCost),
            ExcelColumn.number("overheadCost", "制费", ProductCostRow::overheadCost),
            ExcelColumn.number("totalCost", "完工成本", ProductCostRow::totalCost));

    private final CostService service;
    private final FinExportSupport exportSupport;

    public FinCostController(CostService service, FinExportSupport exportSupport) {
        this.service = service;
        this.exportSupport = exportSupport;
    }

    @GetMapping("/check")
    @PreAuthorize("@ss.has('fin:cost:query')")
    public CommonResult<CheckResult> check(@RequestParam String period) {
        return CommonResult.success(service.check(period));
    }

    @GetMapping("/expenses")
    @PreAuthorize("@ss.has('fin:cost:query')")
    public CommonResult<List<ExpenseRow>> expenses(@RequestParam String period) {
        return CommonResult.success(service.expenseRows(period));
    }

    @PutMapping("/expenses")
    @PreAuthorize("@ss.has('fin:cost:calculate')")
    public CommonResult<Void> saveExpenses(@Valid @RequestBody ExpenseSave req) {
        service.saveExpenses(req);
        return CommonResult.success();
    }

    @PostMapping("/runs")
    @PreAuthorize("@ss.has('fin:cost:calculate')")
    public CommonResult<RunVO> calculate(@RequestParam String period) {
        return CommonResult.success(service.calculate(period));
    }

    @GetMapping("/runs")
    @PreAuthorize("@ss.has('fin:cost:query')")
    public CommonResult<List<RunVO>> runs(@RequestParam String period) {
        return CommonResult.success(service.runs(period));
    }

    @GetMapping("/runs/{id}")
    @PreAuthorize("@ss.has('fin:cost:query')")
    public CommonResult<RunVO> run(@PathVariable Long id) {
        return CommonResult.success(service.run(id));
    }

    @GetMapping("/exceptions")
    @PreAuthorize("@ss.has('fin:cost:query')")
    public CommonResult<List<ExceptionVO>> exceptions(@RequestParam String period) {
        return CommonResult.success(service.exceptions(period));
    }

    @PostMapping("/lock")
    @PreAuthorize("@ss.has('fin:cost:lock')")
    public CommonResult<Void> lock(@RequestParam String period) {
        service.lock(period);
        return CommonResult.success();
    }

    @PostMapping("/unlock")
    @PreAuthorize("@ss.has('fin:cost:lock')")
    public CommonResult<Void> unlock(@RequestParam String period) {
        service.unlock(period);
        return CommonResult.success();
    }

    @GetMapping("/products")
    @PreAuthorize("@ss.has('fin:cost:query')")
    public CommonResult<List<ProductCostRow>> products(@RequestParam String period, @RequestParam(required = false) Long materialId) {
        return CommonResult.success(service.products(period, materialId));
    }

    @GetMapping("/products/export")
    @PreAuthorize("@ss.has('fin:cost:query')")
    public void productsExport(@RequestParam String period, @RequestParam(required = false) Long materialId, @RequestParam(required = false) String columns,
                               HttpServletResponse response) throws IOException {
        exportSupport.export(response, "产品成本表-" + period, PRODUCT_COLUMNS, columns, limit -> service.products(period, materialId).stream().limit(limit).toList());
    }

    @GetMapping("/materials")
    @PreAuthorize("@ss.has('fin:cost:query')")
    public CommonResult<List<MaterialCostRow>> materials(@RequestParam String period, @RequestParam(required = false) String keyword) {
        return CommonResult.success(service.materials(period, keyword));
    }

    @GetMapping("/materials/export")
    @PreAuthorize("@ss.has('fin:cost:query')")
    public void materialsExport(@RequestParam String period, @RequestParam(required = false) String keyword, @RequestParam(required = false) String columns,
                                HttpServletResponse response) throws IOException {
        exportSupport.export(response, "物料单价表-" + period, MATERIAL_COLUMNS, columns, limit -> service.materials(period, keyword).stream().limit(limit).toList());
    }

    @GetMapping("/orders")
    @PreAuthorize("@ss.has('fin:cost:query')")
    public CommonResult<List<OrderCostVO>> orders(@RequestParam String period, @RequestParam(required = false) Long materialId) {
        return CommonResult.success(service.orders(period, materialId));
    }

    @GetMapping("/orders/{prodOrderId}")
    @PreAuthorize("@ss.has('fin:cost:query')")
    public CommonResult<OrderCostVO> order(@PathVariable Long prodOrderId, @RequestParam(required = false) String period) {
        return CommonResult.success(service.order(prodOrderId, period));
    }
}
