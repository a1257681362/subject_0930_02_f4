package com.evops.aqua.disease.controller;

import com.evops.aqua.disease.entity.DisChangeEvidence;
import com.evops.aqua.disease.service.DisEvidenceService;
import com.evops.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 疫病模块变更证据查询：按对象或 requestId 追溯。
 */
@RestController
@RequestMapping("/api/aqua/disease/evidence")
public class DisEvidenceController {

    private final DisEvidenceService evidenceService;

    public DisEvidenceController(DisEvidenceService evidenceService) {
        this.evidenceService = evidenceService;
    }

    @GetMapping
    public ApiResponse<List<DisChangeEvidence>> list(@RequestParam(required = false) String entityType,
                                                     @RequestParam(required = false) Long entityId,
                                                     @RequestParam(required = false) String requestId) {
        return ApiResponse.ok(evidenceService.list(entityType, entityId, requestId));
    }
}
