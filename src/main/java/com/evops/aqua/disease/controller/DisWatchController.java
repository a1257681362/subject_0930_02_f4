package com.evops.aqua.disease.controller;

import com.evops.aqua.disease.entity.DisPoolMovement;
import com.evops.aqua.disease.entity.DisWatchCheck;
import com.evops.aqua.disease.service.DisIsolationService;
import com.evops.aqua.disease.service.DisWatchService;
import com.evops.common.ApiResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 疫病巡查检查：定时任务每日对未完结观察单生成检查记录（可手动触发，幂等）。
 */
@RestController
@RequestMapping("/api/aqua/disease")
public class DisWatchController {

    private final DisWatchService watchService;
    private final DisIsolationService isolationService;

    public DisWatchController(DisWatchService watchService, DisIsolationService isolationService) {
        this.watchService = watchService;
        this.isolationService = isolationService;
    }

    /** 手动触发一次巡查检查（与定时任务同一逻辑）：同日重跑不多写、不丢操作者。 */
    @PostMapping("/watch/run-check")
    public ApiResponse<Map<String, Object>> runCheck(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkDate) {
        return ApiResponse.ok(watchService.runDailyCheck(checkDate));
    }

    @GetMapping("/watch/checks")
    public ApiResponse<List<DisWatchCheck>> listChecks(
            @RequestParam(required = false) Long observationId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkDate) {
        return ApiResponse.ok(watchService.listChecks(observationId, checkDate));
    }

    /** 数量变化流水：按批次逐笔查询原池与隔离池之间的移动。 */
    @GetMapping("/movements")
    public ApiResponse<List<DisPoolMovement>> movements(@RequestParam(required = false) Long batchId,
                                                        @RequestParam(required = false) Long isolationId) {
        return ApiResponse.ok(isolationService.listMovements(isolationId, batchId));
    }
}
