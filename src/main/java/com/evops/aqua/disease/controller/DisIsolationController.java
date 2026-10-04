package com.evops.aqua.disease.controller;

import com.evops.aqua.disease.dto.LiftRequest;
import com.evops.aqua.disease.entity.DisIsolation;
import com.evops.aqua.disease.entity.DisPoolMovement;
import com.evops.aqua.disease.service.DisIsolationService;
import com.evops.common.ApiResponse;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 隔离调度单：兽医复核解除、数量变化流水查询。
 */
@RestController
@RequestMapping("/api/aqua/disease/isolations")
public class DisIsolationController {

    private final DisIsolationService isolationService;

    public DisIsolationController(DisIsolationService isolationService) {
        this.isolationService = isolationService;
    }

    /** 解除隔离：必须由兽医复核（兽医姓名与意见必填），数量按流水返回原池。 */
    @PostMapping("/{id}/lift")
    public ApiResponse<DisIsolation> lift(@PathVariable Long id,
                                          @Validated @RequestBody LiftRequest request) {
        return ApiResponse.ok(isolationService.lift(id, request));
    }

    @GetMapping("/{id}")
    public ApiResponse<DisIsolation> detail(@PathVariable Long id) {
        return ApiResponse.ok(isolationService.getOrThrow(id));
    }

    @GetMapping
    public ApiResponse<List<DisIsolation>> list(@RequestParam(required = false) Long observationId,
                                                @RequestParam(required = false) Long batchId,
                                                @RequestParam(required = false) String status) {
        return ApiResponse.ok(isolationService.list(observationId, batchId, status));
    }

    /** 原池与隔离池之间的数量变化：逐笔记录。 */
    @GetMapping("/{id}/movements")
    public ApiResponse<List<DisPoolMovement>> movements(@PathVariable Long id) {
        return ApiResponse.ok(isolationService.listMovements(id, null));
    }
}
