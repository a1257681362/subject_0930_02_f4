package com.evops.aqua.disease.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.time.LocalDate;

/**
 * 疫病观察单：发现异常症状即开单。
 * 状态 OPEN（观察中）→ ISOLATED（已隔离）→ LIFTED（已解除）。
 * reportedBy 为开单操作者，后续巡查检查记录必须沿用，不得丢失。
 */
@TableName("t_dis_observation")
public class DisObservation extends BaseEntity {

    public static final String STATUS_OPEN = "OPEN";
    public static final String STATUS_ISOLATED = "ISOLATED";
    public static final String STATUS_LIFTED = "LIFTED";

    public static final String RISK_LOW = "LOW";
    public static final String RISK_MEDIUM = "MEDIUM";
    public static final String RISK_HIGH = "HIGH";

    private String observeNo;
    private Long pondId;
    private Long batchId;
    private String symptom;
    private String riskLevel;
    private LocalDate observeDate;
    private String status;
    private String reportedBy;
    private String requestId;
    private String remark;

    /** 风险等级排序值：等级越高越须转入高级别隔离池。 */
    public static int riskRank(String riskLevel) {
        if (RISK_HIGH.equals(riskLevel)) {
            return 3;
        }
        if (RISK_MEDIUM.equals(riskLevel)) {
            return 2;
        }
        if (RISK_LOW.equals(riskLevel)) {
            return 1;
        }
        return 0;
    }

    public static boolean isValidRisk(String riskLevel) {
        return RISK_LOW.equals(riskLevel) || RISK_MEDIUM.equals(riskLevel) || RISK_HIGH.equals(riskLevel);
    }

    public String getObserveNo() { return observeNo; }
    public void setObserveNo(String observeNo) { this.observeNo = observeNo; }
    public Long getPondId() { return pondId; }
    public void setPondId(Long pondId) { this.pondId = pondId; }
    public Long getBatchId() { return batchId; }
    public void setBatchId(Long batchId) { this.batchId = batchId; }
    public String getSymptom() { return symptom; }
    public void setSymptom(String symptom) { this.symptom = symptom; }
    public String getRiskLevel() { return riskLevel; }
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }
    public LocalDate getObserveDate() { return observeDate; }
    public void setObserveDate(LocalDate observeDate) { this.observeDate = observeDate; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getReportedBy() { return reportedBy; }
    public void setReportedBy(String reportedBy) { this.reportedBy = reportedBy; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
