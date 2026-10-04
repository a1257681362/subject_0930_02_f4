package com.evops.aqua.disease.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.time.LocalDate;

/**
 * 定时巡查检查记录：每张未完结观察单每日一行（观察单+日期唯一）。
 * 重启定时任务后重跑同日检查只追加缺失行，绝不重复写入；
 * operatorName 取自观察单开单操作者，重跑不得覆盖丢失。
 */
@TableName("t_dis_watch_check")
public class DisWatchCheck extends BaseEntity {

    private Long observationId;
    private LocalDate checkDate;
    /** 检查时观察单状态快照 */
    private String observationStatus;
    private String riskLevel;
    private Long isolationId;
    private Long isolationPondId;
    /** 操作者（沿用观察单开单人） */
    private String operatorName;
    private String detail;

    public Long getObservationId() { return observationId; }
    public void setObservationId(Long observationId) { this.observationId = observationId; }
    public LocalDate getCheckDate() { return checkDate; }
    public void setCheckDate(LocalDate checkDate) { this.checkDate = checkDate; }
    public String getObservationStatus() { return observationStatus; }
    public void setObservationStatus(String observationStatus) { this.observationStatus = observationStatus; }
    public String getRiskLevel() { return riskLevel; }
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }
    public Long getIsolationId() { return isolationId; }
    public void setIsolationId(Long isolationId) { this.isolationId = isolationId; }
    public Long getIsolationPondId() { return isolationPondId; }
    public void setIsolationPondId(Long isolationPondId) { this.isolationPondId = isolationPondId; }
    public String getOperatorName() { return operatorName; }
    public void setOperatorName(String operatorName) { this.operatorName = operatorName; }
    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }
}
