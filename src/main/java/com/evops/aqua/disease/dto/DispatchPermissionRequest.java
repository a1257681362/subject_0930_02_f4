package com.evops.aqua.disease.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * 隔离池调度权限调整：关闭/开启均须登记原因与操作者。
 */
public class DispatchPermissionRequest {

    @NotNull(message = "权限开关不能为空")
    private Boolean enabled;
    @NotBlank(message = "调整调度权限必须登记原因")
    private String reason;
    @NotBlank(message = "操作者不能为空")
    private String operator;
    private String requestId;

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
}
