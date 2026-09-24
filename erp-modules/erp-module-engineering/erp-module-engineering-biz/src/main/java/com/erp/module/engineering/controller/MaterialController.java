package com.erp.module.engineering.controller;

import com.erp.common.result.CommonResult;
import com.erp.common.result.PageResult;
import com.erp.module.engineering.api.material.MaterialStatus;
import com.erp.module.engineering.controller.vo.MaterialPageReqVO;
import com.erp.module.engineering.controller.vo.MaterialRespVO;
import com.erp.module.engineering.controller.vo.MaterialSaveReqVO;
import com.erp.module.engineering.service.MaterialService;
import io.swagger.v3.oas.annotations.Operation;
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

import java.util.Arrays;
import java.util.List;

/** 物料管理。作为各模块 CRUD 接口的参考实现。 */
@Tag(name = "研发工程 - 物料")
@RestController
@RequestMapping("/api/engineering/materials")
public class MaterialController {

    private final MaterialService materialService;

    public MaterialController(MaterialService materialService) {
        this.materialService = materialService;
    }

    @Operation(summary = "分页查询")
    @GetMapping
    @PreAuthorize("@ss.has('eng:material:query')")
    public CommonResult<PageResult<MaterialRespVO>> page(@Valid MaterialPageReqVO req) {
        return CommonResult.success(materialService.page(req));
    }

    @Operation(summary = "选择器远程搜索（登录即可）：编码前缀或名称/规格模糊；ids 用于回显")
    @GetMapping("/search")
    public CommonResult<List<MaterialRespVO>> search(@RequestParam(required = false) String keyword,
                                                     @RequestParam(required = false) String types,
                                                     @RequestParam(required = false, defaultValue = "ENABLED") String status,
                                                     @RequestParam(required = false) String ids,
                                                     @RequestParam(defaultValue = "20") int limit) {
        List<Long> idList = ids == null || ids.isBlank() ? null
                : Arrays.stream(ids.split(",")).map(String::trim).filter(s -> s.matches("\\d+")).map(Long::valueOf).toList();
        MaterialStatus st = status == null || status.isBlank() ? null : MaterialStatus.valueOf(status);
        return CommonResult.success(materialService.search(keyword, types, st, idList, limit));
    }

    @Operation(summary = "按编码精确查询启用物料（登录即可）")
    @GetMapping("/by-code/{code}")
    public CommonResult<MaterialRespVO> byCode(@PathVariable String code) {
        return CommonResult.success(materialService.getEnabledByCode(code));
    }

    @Operation(summary = "详情")
    @GetMapping("/{id}")
    @PreAuthorize("@ss.has('eng:material:query')")
    public CommonResult<MaterialRespVO> get(@PathVariable Long id) {
        return CommonResult.success(materialService.get(id));
    }

    @Operation(summary = "新建（编码为空时自动生成）")
    @PostMapping
    @PreAuthorize("@ss.has('eng:material:create')")
    public CommonResult<Long> create(@Valid @RequestBody MaterialSaveReqVO req) {
        return CommonResult.success(materialService.create(req));
    }

    @Operation(summary = "修改")
    @PutMapping("/{id}")
    @PreAuthorize("@ss.has('eng:material:update')")
    public CommonResult<Void> update(@PathVariable Long id, @Valid @RequestBody MaterialSaveReqVO req) {
        materialService.update(id, req);
        return CommonResult.success();
    }

    @Operation(summary = "启用")
    @PostMapping("/{id}/enable")
    @PreAuthorize("@ss.has('eng:material:enable')")
    public CommonResult<Void> enable(@PathVariable Long id) {
        materialService.enable(id);
        return CommonResult.success();
    }

    @Operation(summary = "停用")
    @PostMapping("/{id}/disable")
    @PreAuthorize("@ss.has('eng:material:disable')")
    public CommonResult<Void> disable(@PathVariable Long id) {
        materialService.disable(id);
        return CommonResult.success();
    }

    @Operation(summary = "删除（仅草稿）")
    @DeleteMapping("/{id}")
    @PreAuthorize("@ss.has('eng:material:delete')")
    public CommonResult<Void> delete(@PathVariable Long id) {
        materialService.delete(id);
        return CommonResult.success();
    }
}
