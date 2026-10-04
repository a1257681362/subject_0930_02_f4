package com.evops.aqua.disease.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.disease.dto.DisinfectRequest;
import com.evops.aqua.disease.dto.DispatchPermissionRequest;
import com.evops.aqua.disease.dto.IsolationPoolCreateRequest;
import com.evops.aqua.disease.entity.DisChangeEvidence;
import com.evops.aqua.disease.entity.DisIsolation;
import com.evops.aqua.disease.entity.DisIsolationPool;
import com.evops.aqua.disease.entity.DisObservation;
import com.evops.aqua.disease.mapper.DisIsolationMapper;
import com.evops.aqua.disease.mapper.DisIsolationPoolMapper;
import com.evops.aqua.entity.Pond;
import com.evops.aqua.service.PondService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 隔离池档案：登记可承接的最高风险等级、容量、消毒状态与调度权限。
 * 池负载 = 该池全部隔离中（ISOLATING）调度单的数量合计；剩余容量 = 容量 - 负载。
 */
@Service
public class DisIsolationPoolService {

    private final DisIsolationPoolMapper poolMapper;
    private final DisIsolationMapper isolationMapper;
    private final PondService pondService;
    private final DisEvidenceService evidenceService;

    public DisIsolationPoolService(DisIsolationPoolMapper poolMapper,
                                   DisIsolationMapper isolationMapper,
                                   PondService pondService,
                                   DisEvidenceService evidenceService) {
        this.poolMapper = poolMapper;
        this.isolationMapper = isolationMapper;
        this.pondService = pondService;
        this.evidenceService = evidenceService;
    }

    /**
     * 登记隔离池：一个养殖池只能登记一个隔离池档案。
     */
    @Transactional(rollbackFor = Exception.class)
    public DisIsolationPool create(IsolationPoolCreateRequest request) {
        Pond pond = pondService.getOrThrow(request.getPondId());
        pondService.requireActive(pond);
        if (!DisObservation.isValidRisk(request.getRiskGrade())) {
            throw new IllegalArgumentException("隔离池等级不合法：" + request.getRiskGrade() + "（应为LOW/MEDIUM/HIGH）");
        }
        Long duplicated = poolMapper.selectCount(
                new QueryWrapper<DisIsolationPool>().eq("pond_id", request.getPondId()));
        if (duplicated != null && duplicated > 0) {
            throw new IllegalArgumentException("养殖池" + pond.getPondCode() + "已登记隔离池档案，不能重复登记");
        }
        DisIsolationPool pool = new DisIsolationPool();
        pool.setPondId(pond.getId());
        pool.setRiskGrade(request.getRiskGrade());
        pool.setCapacityQty(request.getCapacityQty());
        pool.setDisinfectedFlag(Boolean.FALSE);
        pool.setDispatchEnabled(Boolean.TRUE);
        pool.setRemark(request.getRemark());
        poolMapper.insert(pool);
        evidenceService.record(DisChangeEvidence.ENTITY_ISOLATION_POOL, pool.getId(),
                DisChangeEvidence.ACTION_CREATE, request.getRequestId(), null, pool,
                "登记隔离池：养殖池" + pond.getPondCode() + "，等级" + pool.getRiskGrade()
                        + "，容量" + pool.getCapacityQty());
        return pool;
    }

    /**
     * 消毒登记：刷新消毒状态与有效期。调度前校验消毒在有效期内。
     */
    @Transactional(rollbackFor = Exception.class)
    public DisIsolationPool disinfect(Long poolId, DisinfectRequest request) {
        DisIsolationPool pool = getOrThrow(poolId);
        if (request.getValidUntil().isBefore(request.getDisinfectedDate())) {
            throw new IllegalArgumentException("消毒有效期不能早于消毒日期");
        }
        DisIsolationPool before = copyOf(pool);
        pool.setDisinfectedFlag(Boolean.TRUE);
        pool.setDisinfectedTime(LocalDateTime.now());
        pool.setDisinfectValidUntil(request.getValidUntil());
        poolMapper.updateById(pool);
        evidenceService.record(DisChangeEvidence.ENTITY_ISOLATION_POOL, pool.getId(),
                DisChangeEvidence.ACTION_DISINFECT, request.getRequestId(), before, pool,
                "隔离池消毒：有效期至" + request.getValidUntil() + "，操作者=" + request.getOperator());
        return pool;
    }

    /**
     * 调度权限调整：关闭后该池不能调入（须登记原因），开启恢复。
     */
    @Transactional(rollbackFor = Exception.class)
    public DisIsolationPool changePermission(Long poolId, DispatchPermissionRequest request) {
        DisIsolationPool pool = getOrThrow(poolId);
        DisIsolationPool before = copyOf(pool);
        pool.setDispatchEnabled(request.getEnabled());
        pool.setDispatchDisabledReason(Boolean.TRUE.equals(request.getEnabled()) ? null : request.getReason());
        poolMapper.updateById(pool);
        evidenceService.record(DisChangeEvidence.ENTITY_ISOLATION_POOL, pool.getId(),
                DisChangeEvidence.ACTION_PERMISSION, request.getRequestId(), before, pool,
                (Boolean.TRUE.equals(request.getEnabled()) ? "开启" : "关闭") + "隔离池调度权限，原因="
                        + request.getReason() + "，操作者=" + request.getOperator());
        return pool;
    }

    public DisIsolationPool getOrThrow(Long id) {
        DisIsolationPool pool = poolMapper.selectById(id);
        if (pool == null) {
            throw new IllegalArgumentException("隔离池不存在，id=" + id);
        }
        return pool;
    }

    public List<DisIsolationPool> list() {
        return poolMapper.selectList(new QueryWrapper<DisIsolationPool>().orderByAsc("id"));
    }

    /** 该池当前隔离中的调度单（专池专用：有则禁止再调入，防止混养）。 */
    public DisIsolation activeIsolation(Long poolId) {
        List<DisIsolation> actives = isolationMapper.selectList(new QueryWrapper<DisIsolation>()
                .eq("isolation_pool_id", poolId)
                .eq("status", DisIsolation.STATUS_ISOLATING)
                .orderByAsc("id"));
        return actives.isEmpty() ? null : actives.get(0);
    }

    /** 池负载：隔离中调度单数量合计。 */
    public int currentLoad(Long poolId) {
        List<DisIsolation> actives = isolationMapper.selectList(new QueryWrapper<DisIsolation>()
                .eq("isolation_pool_id", poolId)
                .eq("status", DisIsolation.STATUS_ISOLATING));
        int load = 0;
        for (DisIsolation isolation : actives) {
            load += isolation.getQuantity();
        }
        return load;
    }

    public int remainingCapacity(DisIsolationPool pool) {
        return pool.getCapacityQty() - currentLoad(pool.getId());
    }

    /** 消毒状态在指定日期是否有效。 */
    public boolean disinfectionValidOn(DisIsolationPool pool, LocalDate date) {
        return Boolean.TRUE.equals(pool.getDisinfectedFlag())
                && pool.getDisinfectValidUntil() != null
                && !pool.getDisinfectValidUntil().isBefore(date);
    }

    private DisIsolationPool copyOf(DisIsolationPool source) {
        DisIsolationPool copy = new DisIsolationPool();
        copy.setId(source.getId());
        copy.setPondId(source.getPondId());
        copy.setRiskGrade(source.getRiskGrade());
        copy.setCapacityQty(source.getCapacityQty());
        copy.setDisinfectedFlag(source.getDisinfectedFlag());
        copy.setDisinfectedTime(source.getDisinfectedTime());
        copy.setDisinfectValidUntil(source.getDisinfectValidUntil());
        copy.setDispatchEnabled(source.getDispatchEnabled());
        copy.setDispatchDisabledReason(source.getDispatchDisabledReason());
        copy.setRemark(source.getRemark());
        return copy;
    }
}
