package com.evops.aqua.disease.dto;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * 隔离调度请求：把观察单批次部分或全部数量调入隔离池。
 * 调度前校验池等级/容量/消毒状态/调度权限，失败说明冲突原因。
 */
public class DispatchRequest {

    @NotNull(message = "目标隔离池不能为空")
    private Long isolationPoolId;
    @NotNull(message = "转移数量不能为空")
    @Min(value = 1, message = "转移数量必须大于0")
    private Integer quantity;
    @NotNull(message = "转移日期不能为空")
    private LocalDate moveDate;
    @NotBlank(message = "调度操作者不能为空")
    private String operator;
    private String requestId;
    private String remark;

    public Long getIsolationPoolId() { return isolationPoolId; }
    public void setIsolationPoolId(Long isolationPoolId) { this.isolationPoolId = isolationPoolId; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public LocalDate getMoveDate() { return moveDate; }
    public void setMoveDate(LocalDate moveDate) { this.moveDate = moveDate; }
    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
