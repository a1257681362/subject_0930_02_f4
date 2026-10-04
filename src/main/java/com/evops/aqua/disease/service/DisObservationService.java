package com.evops.aqua.disease.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.disease.dto.ObservationCreateRequest;
import com.evops.aqua.disease.entity.DisChangeEvidence;
import com.evops.aqua.disease.entity.DisIsolationPool;
import com.evops.aqua.disease.entity.DisObservation;
import com.evops.aqua.disease.mapper.DisObservationMapper;
import com.evops.aqua.entity.Pond;
import com.evops.aqua.entity.SeedlingBatch;
import com.evops.aqua.service.PondService;
import com.evops.aqua.service.SeedlingBatchService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 疫病观察单：发现异常症状即开单（操作者必填，requestId 幂等）。
 * 按风险等级推荐隔离池：池等级不低于观察单风险等级，并给出各池冲突原因预览。
 */
@Service
public class DisObservationService {

    private final DisObservationMapper observationMapper;
    private final DisIsolationPoolService poolService;
    private final SeedlingBatchService batchService;
    private final PondService pondService;
    private final DisEvidenceService evidenceService;

    public DisObservationService(DisObservationMapper observationMapper,
                                 DisIsolationPoolService poolService,
                                 SeedlingBatchService batchService,
                                 PondService pondService,
                                 DisEvidenceService evidenceService) {
        this.observationMapper = observationMapper;
        this.poolService = poolService;
        this.batchService = batchService;
        this.pondService = pondService;
        this.evidenceService = evidenceService;
    }

    /**
     * 开立观察单：批次须在养且当前在该池；风险等级须合法；操作者必填。
     * 幂等：requestId 已存在时返回首次观察单，不产生第二条。
     */
    @Transactional(rollbackFor = Exception.class)
    public DisObservation create(ObservationCreateRequest request) {
        if (request.getRequestId() != null && !request.getRequestId().trim().isEmpty()) {
            List<DisObservation> existed = observationMapper.selectList(new QueryWrapper<DisObservation>()
                    .eq("request_id", request.getRequestId()));
            if (!existed.isEmpty()) {
                return existed.get(0);
            }
        }
        if (!DisObservation.isValidRisk(request.getRiskLevel())) {
            throw new IllegalArgumentException("风险等级不合法：" + request.getRiskLevel() + "（应为LOW/MEDIUM/HIGH）");
        }
        SeedlingBatch batch = batchService.getOrThrow(request.getBatchId());
        batchService.requireStocked(batch, "开立疫病观察单");
        if (!batch.getPondId().equals(request.getPondId())) {
            throw new IllegalArgumentException(
                    "批次" + batch.getBatchNo() + "当前不在池塘id=" + request.getPondId() + "，请核对后再开观察单");
        }
        Pond pond = pondService.getOrThrow(request.getPondId());
        pondService.requireActive(pond);

        DisObservation observation = new DisObservation();
        observation.setObserveNo(nextObserveNo(batch.getId(), request.getObserveDate().toString().replace("-", "")));
        observation.setPondId(request.getPondId());
        observation.setBatchId(batch.getId());
        observation.setSymptom(request.getSymptom());
        observation.setRiskLevel(request.getRiskLevel());
        observation.setObserveDate(request.getObserveDate());
        observation.setStatus(DisObservation.STATUS_OPEN);
        observation.setReportedBy(request.getReportedBy());
        observation.setRequestId(request.getRequestId());
        observation.setRemark(request.getRemark());
        observationMapper.insert(observation);
        evidenceService.record(DisChangeEvidence.ENTITY_OBSERVATION, observation.getId(),
                DisChangeEvidence.ACTION_CREATE, request.getRequestId(), null, observation,
                "开立疫病观察单：批次" + batch.getBatchNo() + "，症状=" + request.getSymptom()
                        + "，风险等级" + request.getRiskLevel() + "，操作者=" + request.getReportedBy());
        return observation;
    }

    /**
     * 按风险等级推荐隔离池：池等级不低于观察单风险等级、剩余容量足够、
     * 消毒在有效期内、调度权限开启、池内无隔离中批次（禁止混养）。
     * 每个池给出 eligible 标记与冲突原因列表，调度失败原因可预览。
     */
    public Map<String, Object> recommendPools(Long observationId, Integer quantity) {
        DisObservation observation = getOrThrow(observationId);
        SeedlingBatch batch = batchService.getOrThrow(observation.getBatchId());
        int need = quantity != null ? quantity : batch.getQuantity();

        List<Map<String, Object>> pools = new ArrayList<>();
        for (DisIsolationPool pool : poolService.list()) {
            Pond pond = pondService.getOrThrow(pool.getPondId());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("isolationPoolId", pool.getId());
            item.put("pondId", pond.getId());
            item.put("pondCode", pond.getPondCode());
            item.put("pondName", pond.getPondName());
            item.put("riskGrade", pool.getRiskGrade());
            item.put("capacityQty", pool.getCapacityQty());
            item.put("remainingCapacity", poolService.remainingCapacity(pool));
            item.put("disinfectValidUntil", pool.getDisinfectValidUntil());
            item.put("dispatchEnabled", pool.getDispatchEnabled());
            List<String> conflicts = conflictReasons(observation, pool, need, observation.getObserveDate());
            item.put("eligible", conflicts.isEmpty());
            item.put("conflictReasons", conflicts);
            pools.add(item);
        }
        pools.sort((a, b) -> Boolean.compare((Boolean) b.get("eligible"), (Boolean) a.get("eligible")));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("observationId", observation.getId());
        result.put("observeNo", observation.getObserveNo());
        result.put("riskLevel", observation.getRiskLevel());
        result.put("requiredQuantity", need);
        result.put("pools", pools);
        return result;
    }

    /**
     * 调度前逐条校验目标隔离池，返回全部冲突原因（空列表表示可调入）。
     * 校验顺序：池等级 → 调度权限 → 消毒状态 → 禁止混养 → 容量。
     */
    public List<String> conflictReasons(DisObservation observation, DisIsolationPool pool,
                                        int quantity, java.time.LocalDate moveDate) {
        List<String> conflicts = new ArrayList<>();
        Pond pond = pondService.getOrThrow(pool.getPondId());
        String pondCode = pond.getPondCode();
        if (DisObservation.riskRank(pool.getRiskGrade()) < DisObservation.riskRank(observation.getRiskLevel())) {
            conflicts.add("隔离池" + pondCode + "等级" + pool.getRiskGrade()
                    + "低于观察单风险等级" + observation.getRiskLevel() + "，不能承接");
        }
        if (!Boolean.TRUE.equals(pool.getDispatchEnabled())) {
            conflicts.add("隔离池" + pondCode + "已关闭调度权限"
                    + (pool.getDispatchDisabledReason() == null ? "" : "（" + pool.getDispatchDisabledReason() + "）")
                    + "，不能调入");
        }
        if (!poolService.disinfectionValidOn(pool, moveDate)) {
            conflicts.add("隔离池" + pondCode + "未消毒或消毒已过有效期"
                    + (pool.getDisinfectValidUntil() == null ? "" : "（有效期至" + pool.getDisinfectValidUntil() + "）")
                    + "，不能调入");
        }
        com.evops.aqua.disease.entity.DisIsolation active = poolService.activeIsolation(pool.getId());
        if (active != null) {
            conflicts.add("隔离池" + pondCode + "已有隔离中的批次（隔离单" + active.getIsolationNo() + "），禁止混养");
        }
        int remaining = poolService.remainingCapacity(pool);
        if (remaining < quantity) {
            conflicts.add("隔离池" + pondCode + "容量不足：剩余容量" + remaining + "，需要" + quantity);
        }
        return conflicts;
    }

    public DisObservation getOrThrow(Long id) {
        DisObservation observation = observationMapper.selectById(id);
        if (observation == null) {
            throw new IllegalArgumentException("疫病观察单不存在，id=" + id);
        }
        return observation;
    }

    public List<DisObservation> list(Long pondId, Long batchId, String status) {
        QueryWrapper<DisObservation> wrapper = new QueryWrapper<>();
        wrapper.eq(pondId != null, "pond_id", pondId);
        wrapper.eq(batchId != null, "batch_id", batchId);
        wrapper.eq(status != null && !status.trim().isEmpty(), "status", status);
        wrapper.orderByAsc("id");
        return observationMapper.selectList(wrapper);
    }

    /** 未完结观察单（定时巡查检查对象）：观察中或已隔离未解除。 */
    public List<DisObservation> listUnfinished() {
        return observationMapper.selectList(new QueryWrapper<DisObservation>()
                .in("status", DisObservation.STATUS_OPEN, DisObservation.STATUS_ISOLATED)
                .orderByAsc("id"));
    }

    public void update(DisObservation observation) {
        observationMapper.updateById(observation);
    }

    private String nextObserveNo(Long batchId, String dateCompact) {
        Long count = observationMapper.selectCount(new QueryWrapper<DisObservation>()
                .eq("batch_id", batchId)
                .likeRight("observe_no", "DO-" + batchId + "-" + dateCompact));
        return "DO-" + batchId + "-" + dateCompact + "-" + ((count == null ? 0 : count) + 1);
    }
}
