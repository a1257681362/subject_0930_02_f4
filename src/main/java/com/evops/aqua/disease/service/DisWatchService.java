package com.evops.aqua.disease.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.disease.entity.DisIsolation;
import com.evops.aqua.disease.entity.DisIsolationPool;
import com.evops.aqua.disease.entity.DisObservation;
import com.evops.aqua.disease.entity.DisWatchCheck;
import com.evops.aqua.disease.mapper.DisWatchCheckMapper;
import com.evops.aqua.entity.Pond;
import com.evops.aqua.service.PondService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 疫病巡查定时检查：对每张未完结观察单（观察中/已隔离）按日生成一行检查记录。
 * 幂等：观察单+日期唯一，重启定时任务后重跑同日检查只追加缺失行，绝不重复写入；
 * 已存在的行一律不更新——操作者（沿用观察单开单人）不会因重跑丢失。
 */
@Service
public class DisWatchService {

    private final DisWatchCheckMapper checkMapper;
    private final DisObservationService observationService;
    private final DisIsolationService isolationService;
    private final DisIsolationPoolService poolService;
    private final PondService pondService;

    public DisWatchService(DisWatchCheckMapper checkMapper,
                           DisObservationService observationService,
                           DisIsolationService isolationService,
                           DisIsolationPoolService poolService,
                           PondService pondService) {
        this.checkMapper = checkMapper;
        this.observationService = observationService;
        this.isolationService = isolationService;
        this.poolService = poolService;
        this.pondService = pondService;
    }

    /**
     * 按日巡查：返回扫描/新增/跳过数量。同日重跑（含定时任务重启后重跑）created 为 0。
     */
    public Map<String, Object> runDailyCheck(LocalDate checkDate) {
        int scanned = 0;
        int created = 0;
        for (DisObservation observation : observationService.listUnfinished()) {
            scanned++;
            Long existed = checkMapper.selectCount(new QueryWrapper<DisWatchCheck>()
                    .eq("observation_id", observation.getId())
                    .eq("check_date", checkDate));
            if (existed != null && existed > 0) {
                continue; // 幂等：同日已有检查记录，不重写、不更新，操作者保持原样
            }
            DisWatchCheck check = new DisWatchCheck();
            check.setObservationId(observation.getId());
            check.setCheckDate(checkDate);
            check.setObservationStatus(observation.getStatus());
            check.setRiskLevel(observation.getRiskLevel());
            check.setOperatorName(observation.getReportedBy());
            check.setDetail(buildDetail(observation, checkDate));
            DisIsolation active = isolationService.activeIsolationOfBatch(observation.getBatchId());
            if (active != null) {
                check.setIsolationId(active.getId());
                check.setIsolationPondId(active.getIsolationPoolId());
            }
            try {
                checkMapper.insert(check);
                created++;
            } catch (DuplicateKeyException ex) {
                // 并发或重启重跑撞唯一键：视为已存在，不多写
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("checkDate", checkDate);
        result.put("scanned", scanned);
        result.put("created", created);
        result.put("skipped", scanned - created);
        return result;
    }

    public List<DisWatchCheck> listChecks(Long observationId, LocalDate checkDate) {
        QueryWrapper<DisWatchCheck> wrapper = new QueryWrapper<>();
        wrapper.eq(observationId != null, "observation_id", observationId);
        wrapper.eq(checkDate != null, "check_date", checkDate);
        wrapper.orderByAsc("id");
        return checkMapper.selectList(wrapper);
    }

    private String buildDetail(DisObservation observation, LocalDate checkDate) {
        if (DisObservation.STATUS_OPEN.equals(observation.getStatus())) {
            return "观察中，待调度隔离；症状=" + observation.getSymptom();
        }
        DisIsolation active = isolationService.activeIsolationOfBatch(observation.getBatchId());
        if (active == null) {
            return "已隔离，待兽医复核解除";
        }
        DisIsolationPool pool = poolService.getOrThrow(active.getIsolationPoolId());
        Pond pond = pondService.getOrThrow(pool.getPondId());
        StringBuilder detail = new StringBuilder();
        detail.append("隔离中：隔离池").append(pond.getPondCode())
                .append("，在隔数量").append(active.getQuantity());
        if (!poolService.disinfectionValidOn(pool, checkDate)) {
            detail.append("；消毒已过有效期，需尽快复消");
        }
        return detail.toString();
    }
}
