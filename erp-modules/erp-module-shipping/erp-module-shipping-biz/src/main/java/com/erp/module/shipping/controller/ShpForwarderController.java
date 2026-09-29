package com.erp.module.shipping.controller;

import com.erp.common.result.CommonResult;
import com.erp.common.result.PageResult;
import com.erp.module.shipping.controller.vo.ForwarderVOs.ForwarderOption;
import com.erp.module.shipping.controller.vo.ForwarderVOs.ForwarderQuery;
import com.erp.module.shipping.controller.vo.ForwarderVOs.ForwarderRow;
import com.erp.module.shipping.controller.vo.ForwarderVOs.ForwarderSave;
import com.erp.module.shipping.service.logistics.ForwarderService;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "出货 - 货代")
@RestController
@RequestMapping("/api/shipping/forwarders")
public class ShpForwarderController {

    private final ForwarderService service;

    public ShpForwarderController(ForwarderService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("@ss.has('shp:forwarder:manage')")
    public CommonResult<PageResult<ForwarderRow>> page(@Valid ForwarderQuery q) {
        return CommonResult.success(service.page(q));
    }

    /** 单据中的货代下拉 */
    @GetMapping("/options")
    @PreAuthorize("@ss.hasAny('shp:notice:query','shp:shipment:query','shp:logistics:query','shp:forwarder:manage')")
    public CommonResult<List<ForwarderOption>> options() {
        return CommonResult.success(service.options());
    }

    @GetMapping("/{id}")
    @PreAuthorize("@ss.has('shp:forwarder:manage')")
    public CommonResult<ForwarderRow> get(@PathVariable Long id) {
        return CommonResult.success(service.get(id));
    }

    @PostMapping
    @PreAuthorize("@ss.has('shp:forwarder:manage')")
    public CommonResult<Long> create(@Valid @RequestBody ForwarderSave req) {
        return CommonResult.success(service.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@ss.has('shp:forwarder:manage')")
    public CommonResult<Void> update(@PathVariable Long id, @Valid @RequestBody ForwarderSave req) {
        service.update(id, req);
        return CommonResult.success();
    }

    /** 已被单据引用时改为停用 */
    @DeleteMapping("/{id}")
    @PreAuthorize("@ss.has('shp:forwarder:manage')")
    public CommonResult<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return CommonResult.success();
    }
}
