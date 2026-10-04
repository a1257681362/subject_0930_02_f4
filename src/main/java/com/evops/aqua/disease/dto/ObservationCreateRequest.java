package com.evops.aqua.disease.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * 疫病观察单开立请求：发现异常症状即开单，操作者必填。
 * 幂等：requestId 唯一，重复请求返回首次观察单。
 */
public class ObservationCreateRequest {

    @NotNull(message = "池塘不能为空")
    private Long pondId;
    @NotNull(message = "批次不能为空")
    private Long batchId;
    @NotBlank(message = "异常症状不能为空")
    private String symptom;
    @NotBlank(message = "风险等级不能为空（LOW/MEDIUM/HIGH）")
    private String riskLevel;
    @NotNull(message = "观察日期不能为空")
    private LocalDate observeDate;
    @NotBlank(message = "开单操作者不能为空")
    private String reportedBy;
    private String requestId;
    private String remark;

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
    public String getReportedBy() { return reportedBy; }
    public void setReportedBy(String reportedBy) { this.reportedBy = reportedBy; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
