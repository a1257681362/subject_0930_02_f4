package com.evops.aqua.disease.dto;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * 隔离池登记请求：挂在养殖池上，登记可承接的最高风险等级与容量。
 */
public class IsolationPoolCreateRequest {

    @NotNull(message = "养殖池不能为空")
    private Long pondId;
    @NotBlank(message = "隔离池等级不能为空（LOW/MEDIUM/HIGH）")
    private String riskGrade;
    @NotNull(message = "容量不能为空")
    @Min(value = 1, message = "容量必须大于0")
    private Integer capacityQty;
    private String remark;
    private String requestId;

    public Long getPondId() { return pondId; }
    public void setPondId(Long pondId) { this.pondId = pondId; }
    public String getRiskGrade() { return riskGrade; }
    public void setRiskGrade(String riskGrade) { this.riskGrade = riskGrade; }
    public Integer getCapacityQty() { return capacityQty; }
    public void setCapacityQty(Integer capacityQty) { this.capacityQty = capacityQty; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
}
