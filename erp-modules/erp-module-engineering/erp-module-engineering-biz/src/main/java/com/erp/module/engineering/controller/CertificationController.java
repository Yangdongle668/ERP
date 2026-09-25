package com.erp.module.engineering.controller;

import com.erp.common.result.CommonResult;
import com.erp.common.result.PageResult;
import com.erp.framework.excel.ExcelColumn;
import com.erp.module.engineering.controller.vo.CertVOs.CertQuery;
import com.erp.module.engineering.controller.vo.CertVOs.CertRow;
import com.erp.module.engineering.controller.vo.CertVOs.CertSave;
import com.erp.module.engineering.service.CertificationService;
import com.erp.module.engineering.service.ExportSupport;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
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

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 认证证书（需求 05-09 第 5 节） */
@Tag(name = "研发工程 - 认证")
@RestController
@RequestMapping("/api/engineering/certifications")
public class CertificationController {

    static final Map<String, String> VALIDITY = Map.of("VALID", "有效", "LONG_TERM", "长期有效", "EXPIRING", "即将到期", "EXPIRED", "已过期", "REVOKED", "已撤销");

    static final List<ExcelColumn<CertRow>> EXPORT_COLUMNS = List.of(
            ExcelColumn.text("certType", "类型", CertRow::certType),
            ExcelColumn.text("certNo", "证书编号", CertRow::certNo),
            ExcelColumn.text("name", "名称", CertRow::name),
            ExcelColumn.text("issuingBody", "发证机构", CertRow::issuingBody),
            ExcelColumn.text("materials", "适用物料", r -> r.materials().stream().map(m -> m.code()).collect(Collectors.joining(","))),
            ExcelColumn.text("countries", "适用国家", r -> String.join(",", r.countries())),
            ExcelColumn.date("issueDate", "发证日期", CertRow::issueDate),
            ExcelColumn.date("expireDate", "到期日期", CertRow::expireDate),
            ExcelColumn.text("validity", "有效性", r -> VALIDITY.get(r.validity())));

    private final CertificationService service;
    private final ExportSupport exportSupport;

    public CertificationController(CertificationService service, ExportSupport exportSupport) {
        this.service = service;
        this.exportSupport = exportSupport;
    }

    @GetMapping
    @PreAuthorize("@ss.has('eng:cert:query')")
    public CommonResult<PageResult<CertRow>> page(@Valid CertQuery q) {
        return CommonResult.success(service.page(q));
    }

    @Operation(summary = "物料适用的证书（登录即可，物料详情“认证”页签）")
    @GetMapping("/by-material/{materialId}")
    public CommonResult<List<CertRow>> byMaterial(@PathVariable Long materialId) {
        return CommonResult.success(service.byMaterial(materialId));
    }

    @PostMapping
    @PreAuthorize("@ss.has('eng:cert:create')")
    public CommonResult<Long> create(@Valid @RequestBody CertSave req) {
        return CommonResult.success(service.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@ss.has('eng:cert:update')")
    public CommonResult<Void> update(@PathVariable Long id, @Valid @RequestBody CertSave req) {
        service.update(id, req);
        return CommonResult.success();
    }

    @PostMapping("/{id}/revoke")
    @PreAuthorize("@ss.has('eng:cert:update')")
    public CommonResult<Void> revoke(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        service.revoke(id, body == null ? null : body.get("reason"));
        return CommonResult.success();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@ss.has('eng:cert:delete')")
    public CommonResult<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return CommonResult.success();
    }

    @GetMapping("/export")
    @PreAuthorize("@ss.has('eng:cert:export')")
    public void export(@Valid CertQuery q, @RequestParam(required = false) String columns, HttpServletResponse response) throws IOException {
        exportSupport.export(response, "认证证书", EXPORT_COLUMNS, columns, limit -> service.listForExport(q, limit));
    }
}
