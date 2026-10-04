package com.evops.aqua.disease.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * 解除隔离请求：必须由兽医复核（兽医姓名与复核意见必填），数量按流水返回原池。
 */
public class LiftRequest {

    @NotBlank(message = "解除隔离必须由兽医复核，兽医姓名不能为空")
    private String vetName;
    @NotBlank(message = "解除隔离必须登记兽医复核意见")
    private String vetOpinion;
    @NotNull(message = "返回日期不能为空")
    private LocalDate moveDate;
    @NotBlank(message = "解除操作者不能为空")
    private String operator;
    private String requestId;

    public String getVetName() { return vetName; }
    public void setVetName(String vetName) { this.vetName = vetName; }
    public String getVetOpinion() { return vetOpinion; }
    public void setVetOpinion(String vetOpinion) { this.vetOpinion = vetOpinion; }
    public LocalDate getMoveDate() { return moveDate; }
    public void setMoveDate(LocalDate moveDate) { this.moveDate = moveDate; }
    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
}
