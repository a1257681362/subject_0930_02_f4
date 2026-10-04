package com.evops.aqua.disease.controller;

import com.evops.aqua.disease.dto.DisinfectRequest;
import com.evops.aqua.disease.dto.DispatchPermissionRequest;
import com.evops.aqua.disease.dto.IsolationPoolCreateRequest;
import com.evops.aqua.disease.entity.DisIsolationPool;
import com.evops.aqua.disease.service.DisIsolationPoolService;
import com.evops.common.ApiResponse;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 隔离池档案：登记等级与容量、消毒登记、调度权限开关。
 */
@RestController
@RequestMapping("/api/aqua/disease/isolation-pools")
public class DisIsolationPoolController {

    private final DisIsolationPoolService poolService;

    public DisIsolationPoolController(DisIsolationPoolService poolService) {
        this.poolService = poolService;
    }

    @PostMapping
    public ApiResponse<DisIsolationPool> create(@Validated @RequestBody IsolationPoolCreateRequest request) {
        return ApiResponse.ok(poolService.create(request));
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (DisIsolationPool pool : poolService.list()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("pool", pool);
            item.put("currentLoad", poolService.currentLoad(pool.getId()));
            item.put("remainingCapacity", poolService.remainingCapacity(pool));
            result.add(item);
        }
        return ApiResponse.ok(result);
    }

    @PostMapping("/{id}/disinfect")
    public ApiResponse<DisIsolationPool> disinfect(@PathVariable Long id,
                                                   @Validated @RequestBody DisinfectRequest request) {
        return ApiResponse.ok(poolService.disinfect(id, request));
    }

    @PostMapping("/{id}/dispatch-permission")
    public ApiResponse<DisIsolationPool> changePermission(@PathVariable Long id,
                                                          @Validated @RequestBody DispatchPermissionRequest request) {
        return ApiResponse.ok(poolService.changePermission(id, request));
    }
}
