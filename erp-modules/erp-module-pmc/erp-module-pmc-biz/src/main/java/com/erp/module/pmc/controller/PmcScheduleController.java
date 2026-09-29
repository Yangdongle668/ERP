package com.erp.module.pmc.controller;

import com.erp.common.result.CommonResult;
import com.erp.module.pmc.controller.vo.ScheduleVOs.AdjustReq;
import com.erp.module.pmc.controller.vo.ScheduleVOs.ApplyReq;
import com.erp.module.pmc.controller.vo.ScheduleVOs.ApplyRow;
import com.erp.module.pmc.controller.vo.ScheduleVOs.CalendarBatch;
import com.erp.module.pmc.controller.vo.ScheduleVOs.CalendarMonth;
import com.erp.module.pmc.controller.vo.ScheduleVOs.CalendarSave;
import com.erp.module.pmc.controller.vo.ScheduleVOs.LoadDetail;
import com.erp.module.pmc.controller.vo.ScheduleVOs.LoadRow;
import com.erp.module.pmc.controller.vo.ScheduleVOs.RunReq;
import com.erp.module.pmc.controller.vo.ScheduleVOs.RunResult;
import com.erp.module.pmc.controller.vo.ScheduleVOs.ScheduleRow;
import com.erp.module.pmc.controller.vo.ScheduleVOs.SimulateReq;
import com.erp.module.pmc.controller.vo.ScheduleVOs.SimulateResult;
import com.erp.module.pmc.service.schedule.CapacityService;
import com.erp.module.pmc.service.schedule.ScheduleService;
import io.swagger.v3.oas.annotations.tags.Tag;
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

import java.time.LocalDate;
import java.util.List;

@Tag(name = "PMC - 产能与排产")
@RestController
@RequestMapping("/api/pmc")
public class PmcScheduleController {

    private final CapacityService capacityService;
    private final ScheduleService scheduleService;

    public PmcScheduleController(CapacityService capacityService, ScheduleService scheduleService) {
        this.capacityService = capacityService;
        this.scheduleService = scheduleService;
    }

    // ==================== 产能日历、负荷 ====================

    @GetMapping("/capacity-calendars")
    @PreAuthorize("@ss.hasAny('pmc:capacity:query', 'pmc:schedule:query')")
    public CommonResult<CalendarMonth> calendar(@RequestParam(required = false) Long workCenterId, @RequestParam(required = false) String month) {
        return CommonResult.success(capacityService.month(workCenterId, month));
    }

    @PutMapping("/capacity-calendars")
    @PreAuthorize("@ss.has('pmc:capacity:update')")
    public CommonResult<Void> saveCalendar(@Valid @RequestBody CalendarSave req) {
        capacityService.save(req.workCenterId(), req.date(), req.hours(), req.reason());
        return CommonResult.success();
    }

    @PostMapping("/capacity-calendars/batch")
    @PreAuthorize("@ss.has('pmc:capacity:update')")
    public CommonResult<Integer> batchCalendar(@Valid @RequestBody CalendarBatch req) {
        return CommonResult.success(capacityService.batch(req.workCenterId(), req.from(), req.to(), req.hours(), req.reason()));
    }

    @GetMapping("/capacity/load")
    @PreAuthorize("@ss.hasAny('pmc:capacity:query', 'pmc:schedule:query')")
    public CommonResult<List<LoadRow>> load(@RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to,
                                            @RequestParam(required = false) Long deptId) {
        return CommonResult.success(capacityService.load(from, to, deptId));
    }

    @GetMapping("/capacity/load-detail")
    @PreAuthorize("@ss.hasAny('pmc:capacity:query', 'pmc:schedule:query')")
    public CommonResult<List<LoadDetail>> loadDetail(@RequestParam Long workCenterId, @RequestParam LocalDate date) {
        return CommonResult.success(capacityService.loadDetail(workCenterId, date));
    }

    // ==================== 排产 ====================

    @PostMapping("/schedules/run")
    @PreAuthorize("@ss.has('pmc:schedule:run')")
    public CommonResult<RunResult> run(@RequestBody(required = false) RunReq req) {
        return CommonResult.success(scheduleService.run(req == null ? null : req.deptId(), req == null ? null : req.mode()));
    }

    @GetMapping("/schedules")
    @PreAuthorize("@ss.has('pmc:schedule:query')")
    public CommonResult<List<ScheduleRow>> list(@RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to,
                                                @RequestParam(required = false) Long deptId) {
        return CommonResult.success(scheduleService.list(from, to, deptId));
    }

    @PutMapping("/schedules/{id}")
    @PreAuthorize("@ss.has('pmc:schedule:adjust')")
    public CommonResult<Void> adjust(@PathVariable Long id, @Valid @RequestBody AdjustReq req) {
        scheduleService.adjust(id, req.workCenterId(), req.startDate());
        return CommonResult.success();
    }

    @PostMapping("/schedules/{id}/lock")
    @PreAuthorize("@ss.has('pmc:schedule:adjust')")
    public CommonResult<Void> lock(@PathVariable Long id, @RequestParam(defaultValue = "true") boolean locked) {
        scheduleService.lock(id, locked);
        return CommonResult.success();
    }

    @PostMapping("/schedules/simulate")
    @PreAuthorize("@ss.has('pmc:schedule:run')")
    public CommonResult<SimulateResult> simulate(@Valid @RequestBody SimulateReq req) {
        return CommonResult.success(scheduleService.simulate(req.prodOrderId(), req.priority()));
    }

    @PostMapping("/schedules/simulate/confirm")
    @PreAuthorize("@ss.has('pmc:schedule:run')")
    public CommonResult<RunResult> confirmInsert(@Valid @RequestBody SimulateReq req) {
        return CommonResult.success(scheduleService.confirmInsert(req.prodOrderId(), req.priority()));
    }

    @GetMapping("/schedules/apply-preview")
    @PreAuthorize("@ss.hasAny('pmc:schedule:apply', 'pmc:schedule:query')")
    public CommonResult<List<ApplyRow>> applyPreview() {
        return CommonResult.success(scheduleService.applyPreview());
    }

    @PostMapping("/schedules/apply")
    @PreAuthorize("@ss.has('pmc:schedule:apply')")
    public CommonResult<Integer> apply(@RequestBody(required = false) ApplyReq req) {
        return CommonResult.success(scheduleService.apply(req == null ? null : req.prodOrderIds()));
    }
}
