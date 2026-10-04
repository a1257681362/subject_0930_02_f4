package com.evops.aqua.disease.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.disease.dto.DispatchRequest;
import com.evops.aqua.disease.dto.LiftRequest;
import com.evops.aqua.disease.entity.DisChangeEvidence;
import com.evops.aqua.disease.entity.DisIsolation;
import com.evops.aqua.disease.entity.DisIsolationPool;
import com.evops.aqua.disease.entity.DisObservation;
import com.evops.aqua.disease.entity.DisPoolMovement;
import com.evops.aqua.disease.mapper.DisIsolationMapper;
import com.evops.aqua.disease.mapper.DisPoolMovementMapper;
import com.evops.aqua.entity.Pond;
import com.evops.aqua.entity.SeedlingBatch;
import com.evops.aqua.service.PondService;
import com.evops.aqua.service.SeedlingBatchService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 隔离调度：观察单 → 调入隔离池（校验池等级/容量/消毒/权限，失败说明冲突原因）
 * → 兽医复核解除 → 数量返回原池。原池与隔离池之间的数量变化逐笔落流水。
 * 隔离中的批次禁止混养（专池专用）和出池/换池（台账侧统一拦截）。
 */
@Service
public class DisIsolationService {

    private final DisIsolationMapper isolationMapper;
    private final DisPoolMovementMapper movementMapper;
    private final DisObservationService observationService;
    private final DisIsolationPoolService poolService;
    private final SeedlingBatchService batchService;
    private final PondService pondService;
    private final DisEvidenceService evidenceService;

    public DisIsolationService(DisIsolationMapper isolationMapper,
                               DisPoolMovementMapper movementMapper,
                               DisObservationService observationService,
                               DisIsolationPoolService poolService,
                               SeedlingBatchService batchService,
                               PondService pondService,
                               DisEvidenceService evidenceService) {
        this.isolationMapper = isolationMapper;
        this.movementMapper = movementMapper;
        this.observationService = observationService;
        this.poolService = poolService;
        this.batchService = batchService;
        this.pondService = pondService;
        this.evidenceService = evidenceService;
    }

    /**
     * 调度：把观察单批次指定数量调入隔离池。
     * 幂等：requestId 已存在时返回首次调度单。调度失败抛出带冲突原因的异常，不落任何记录。
     */
    @Transactional(rollbackFor = Exception.class)
    public DisIsolation dispatch(Long observationId, DispatchRequest request) {
        if (request.getRequestId() != null && !request.getRequestId().trim().isEmpty()) {
            List<DisIsolation> existed = isolationMapper.selectList(new QueryWrapper<DisIsolation>()
                    .eq("request_id", request.getRequestId()));
            if (!existed.isEmpty()) {
                return existed.get(0);
            }
        }
        DisObservation observation = observationService.getOrThrow(observationId);
        if (!DisObservation.STATUS_OPEN.equals(observation.getStatus())) {
            throw new IllegalArgumentException("观察单" + observation.getObserveNo()
                    + "当前状态" + observation.getStatus() + "，不能调度（仅观察中可调度）");
        }
        SeedlingBatch batch = batchService.getOrThrow(observation.getBatchId());
        batchService.requireStocked(batch, "隔离调度");
        if (request.getQuantity() > batch.getQuantity()) {
            throw new IllegalArgumentException("转移数量" + request.getQuantity()
                    + "超过批次" + batch.getBatchNo() + "在养数量" + batch.getQuantity());
        }
        DisIsolation activeOfBatch = activeIsolationOfBatch(batch.getId());
        if (activeOfBatch != null) {
            throw new IllegalArgumentException("批次" + batch.getBatchNo() + "已有隔离中的隔离单"
                    + activeOfBatch.getIsolationNo() + "，禁止重复调度");
        }

        DisIsolationPool pool = poolService.getOrThrow(request.getIsolationPoolId());
        Pond targetPond = pondService.getOrThrow(pool.getPondId());
        pondService.requireActive(targetPond);
        List<String> conflicts = observationService.conflictReasons(
                observation, pool, request.getQuantity(), request.getMoveDate());
        if (!conflicts.isEmpty()) {
            throw new IllegalArgumentException("隔离调度失败，冲突原因：" + String.join("；", conflicts));
        }

        DisIsolation isolation = new DisIsolation();
        isolation.setIsolationNo("DI-" + observation.getId());
        isolation.setObservationId(observation.getId());
        isolation.setBatchId(batch.getId());
        isolation.setFromPondId(observation.getPondId());
        isolation.setIsolationPoolId(pool.getId());
        isolation.setToPondId(targetPond.getId());
        isolation.setQuantity(request.getQuantity());
        isolation.setMoveDate(request.getMoveDate());
        isolation.setStatus(DisIsolation.STATUS_ISOLATING);
        isolation.setOperatorName(request.getOperator());
        isolation.setRequestId(request.getRequestId());
        isolation.setRemark(request.getRemark());
        isolationMapper.insert(isolation);

        // 数量变化逐笔记录：原池 → 隔离池
        recordMovement(isolation, DisPoolMovement.TYPE_ISOLATE_IN,
                observation.getPondId(), targetPond.getId(),
                request.getQuantity(), request.getMoveDate(),
                request.getOperator(), request.getRequestId());

        DisObservation obsBefore = copyOf(observation);
        observation.setStatus(DisObservation.STATUS_ISOLATED);
        observationService.update(observation);
        evidenceService.record(DisChangeEvidence.ENTITY_ISOLATION, isolation.getId(),
                DisChangeEvidence.ACTION_DISPATCH, request.getRequestId(), obsBefore, isolation,
                "隔离调度：批次" + batch.getBatchNo() + "从池塘id=" + observation.getPondId()
                        + "调入隔离池" + targetPond.getPondCode() + "，数量" + request.getQuantity()
                        + "，操作者=" + request.getOperator());
        return isolation;
    }

    /**
     * 解除隔离：必须由兽医复核（兽医姓名与意见必填）。数量按流水返回原池。
     * 幂等：重复解除（同 requestId 或已解除）返回原调度单，不重复写流水。
     */
    @Transactional(rollbackFor = Exception.class)
    public DisIsolation lift(Long isolationId, LiftRequest request) {
        DisIsolation isolation = getOrThrow(isolationId);
        if (DisIsolation.STATUS_LIFTED.equals(isolation.getStatus())) {
            return isolation; // 幂等：已解除不重复写流水
        }
        DisIsolation before = copyOf(isolation);
        isolation.setStatus(DisIsolation.STATUS_LIFTED);
        isolation.setVetName(request.getVetName());
        isolation.setVetOpinion(request.getVetOpinion());
        isolation.setLiftedTime(LocalDateTime.now());
        isolation.setLiftRequestId(request.getRequestId());
        isolationMapper.updateById(isolation);

        // 数量变化逐笔记录：隔离池 → 原池
        recordMovement(isolation, DisPoolMovement.TYPE_RETURN_BACK,
                isolation.getToPondId(), isolation.getFromPondId(),
                isolation.getQuantity(), request.getMoveDate(),
                request.getOperator(), request.getRequestId());

        DisObservation observation = observationService.getOrThrow(isolation.getObservationId());
        DisObservation obsBefore = copyOf(observation);
        observation.setStatus(DisObservation.STATUS_LIFTED);
        observationService.update(observation);
        evidenceService.record(DisChangeEvidence.ENTITY_ISOLATION, isolation.getId(),
                DisChangeEvidence.ACTION_LIFT, request.getRequestId(), before, isolation,
                "解除隔离：隔离单" + isolation.getIsolationNo() + "，数量" + isolation.getQuantity()
                        + "返回原池id=" + isolation.getFromPondId() + "，兽医=" + request.getVetName()
                        + "，复核意见=" + request.getVetOpinion() + "，操作者=" + request.getOperator());
        return isolation;
    }

    /**
     * 台账闸门：批次有隔离中的调度单时，禁止出池/换池（隔离中的批次禁止混养和出池）。
     */
    public void requireNotIsolated(Long batchId, String action) {
        DisIsolation active = activeIsolationOfBatch(batchId);
        if (active != null) {
            throw new IllegalArgumentException("批次id=" + batchId + "隔离中（隔离单"
                    + active.getIsolationNo() + "），禁止" + action + "，须先由兽医复核解除隔离");
        }
    }

    public DisIsolation getOrThrow(Long id) {
        DisIsolation isolation = isolationMapper.selectById(id);
        if (isolation == null) {
            throw new IllegalArgumentException("隔离调度单不存在，id=" + id);
        }
        return isolation;
    }

    public DisIsolation activeIsolationOfBatch(Long batchId) {
        List<DisIsolation> actives = isolationMapper.selectList(new QueryWrapper<DisIsolation>()
                .eq("batch_id", batchId)
                .eq("status", DisIsolation.STATUS_ISOLATING)
                .orderByAsc("id"));
        return actives.isEmpty() ? null : actives.get(0);
    }

    public List<DisIsolation> list(Long observationId, Long batchId, String status) {
        QueryWrapper<DisIsolation> wrapper = new QueryWrapper<>();
        wrapper.eq(observationId != null, "observation_id", observationId);
        wrapper.eq(batchId != null, "batch_id", batchId);
        wrapper.eq(status != null && !status.trim().isEmpty(), "status", status);
        wrapper.orderByAsc("id");
        return isolationMapper.selectList(wrapper);
    }

    /** 数量变化流水：按隔离单或批次逐笔查询。 */
    public List<DisPoolMovement> listMovements(Long isolationId, Long batchId) {
        QueryWrapper<DisPoolMovement> wrapper = new QueryWrapper<>();
        wrapper.eq(isolationId != null, "isolation_id", isolationId);
        wrapper.eq(batchId != null, "batch_id", batchId);
        wrapper.orderByAsc("id");
        return movementMapper.selectList(wrapper);
    }

    private void recordMovement(DisIsolation isolation, String moveType,
                                Long fromPondId, Long toPondId, int quantity,
                                java.time.LocalDate moveDate, String operator, String requestId) {
        DisPoolMovement movement = new DisPoolMovement();
        movement.setIsolationId(isolation.getId());
        movement.setBatchId(isolation.getBatchId());
        movement.setMoveType(moveType);
        movement.setFromPondId(fromPondId);
        movement.setToPondId(toPondId);
        movement.setQuantity(quantity);
        movement.setMoveDate(moveDate);
        movement.setOperatorName(operator);
        movement.setRequestId(requestId);
        movementMapper.insert(movement);
    }

    private DisObservation copyOf(DisObservation source) {
        DisObservation copy = new DisObservation();
        copy.setId(source.getId());
        copy.setObserveNo(source.getObserveNo());
        copy.setPondId(source.getPondId());
        copy.setBatchId(source.getBatchId());
        copy.setSymptom(source.getSymptom());
        copy.setRiskLevel(source.getRiskLevel());
        copy.setObserveDate(source.getObserveDate());
        copy.setStatus(source.getStatus());
        copy.setReportedBy(source.getReportedBy());
        copy.setRequestId(source.getRequestId());
        copy.setRemark(source.getRemark());
        return copy;
    }

    private DisIsolation copyOf(DisIsolation source) {
        DisIsolation copy = new DisIsolation();
        copy.setId(source.getId());
        copy.setIsolationNo(source.getIsolationNo());
        copy.setObservationId(source.getObservationId());
        copy.setBatchId(source.getBatchId());
        copy.setFromPondId(source.getFromPondId());
        copy.setIsolationPoolId(source.getIsolationPoolId());
        copy.setToPondId(source.getToPondId());
        copy.setQuantity(source.getQuantity());
        copy.setMoveDate(source.getMoveDate());
        copy.setStatus(source.getStatus());
        copy.setOperatorName(source.getOperatorName());
        copy.setVetName(source.getVetName());
        copy.setVetOpinion(source.getVetOpinion());
        copy.setLiftedTime(source.getLiftedTime());
        copy.setRequestId(source.getRequestId());
        copy.setLiftRequestId(source.getLiftRequestId());
        copy.setRemark(source.getRemark());
        return copy;
    }
}
