package com.evops.aqua.disease.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * 隔离池消毒登记：记录消毒时间与有效期，调度前校验消毒状态。
 */
public class DisinfectRequest {

    @NotNull(message = "消毒日期不能为空")
    private LocalDate disinfectedDate;
    @NotNull(message = "消毒有效期不能为空")
    private LocalDate validUntil;
    @NotBlank(message = "消毒操作者不能为空")
    private String operator;
    private String requestId;

    public LocalDate getDisinfectedDate() { return disinfectedDate; }
    public void setDisinfectedDate(LocalDate disinfectedDate) { this.disinfectedDate = disinfectedDate; }
    public LocalDate getValidUntil() { return validUntil; }
    public void setValidUntil(LocalDate validUntil) { this.validUntil = validUntil; }
    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
}
