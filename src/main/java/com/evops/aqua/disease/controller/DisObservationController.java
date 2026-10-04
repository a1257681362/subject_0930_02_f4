package com.evops.aqua.disease.controller;

import com.evops.aqua.disease.dto.DispatchRequest;
import com.evops.aqua.disease.dto.ObservationCreateRequest;
import com.evops.aqua.disease.entity.DisIsolation;
import com.evops.aqua.disease.entity.DisObservation;
import com.evops.aqua.disease.service.DisIsolationService;
import com.evops.aqua.disease.service.DisObservationService;
import com.evops.common.ApiResponse;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 疫病观察单：发现异常症状开单 → 按风险等级推荐隔离池 → 调度调入隔离池。
 */
@RestController
@RequestMapping("/api/aqua/disease/observations")
public class DisObservationController {

    private final DisObservationService observationService;
    private final DisIsolationService isolationService;

    public DisObservationController(DisObservationService observationService,
                                    DisIsolationService isolationService) {
        this.observationService = observationService;
        this.isolationService = isolationService;
    }

    @PostMapping
    public ApiResponse<DisObservation> create(@Validated @RequestBody ObservationCreateRequest request) {
        return ApiResponse.ok(observationService.create(request));
    }

    @GetMapping
    public ApiResponse<List<DisObservation>> list(@RequestParam(required = false) Long pondId,
                                                  @RequestParam(required = false) Long batchId,
                                                  @RequestParam(required = false) String status) {
        return ApiResponse.ok(observationService.list(pondId, batchId, status));
    }

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable Long id) {
        DisObservation observation = observationService.getOrThrow(id);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("observation", observation);
        List<DisIsolation> isolations = isolationService.list(id, null, null);
        detail.put("isolations", isolations);
        if (!isolations.isEmpty()) {
            detail.put("movements", isolationService.listMovements(isolations.get(0).getId(), null));
        }
        return ApiResponse.ok(detail);
    }

    /** 按风险等级推荐隔离池：各池给出 eligible 标记与冲突原因预览。 */
    @GetMapping("/{id}/recommend-pools")
    public ApiResponse<Map<String, Object>> recommendPools(@PathVariable Long id,
                                                           @RequestParam(required = false) Integer quantity) {
        return ApiResponse.ok(observationService.recommendPools(id, quantity));
    }

    /** 调度：调入隔离池。失败返回具体冲突原因。 */
    @PostMapping("/{id}/dispatch")
    public ApiResponse<DisIsolation> dispatch(@PathVariable Long id,
                                              @Validated @RequestBody DispatchRequest request) {
        return ApiResponse.ok(isolationService.dispatch(id, request));
    }
}
