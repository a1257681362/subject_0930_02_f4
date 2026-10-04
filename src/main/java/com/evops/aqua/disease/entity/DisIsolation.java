package com.evops.aqua.disease.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 隔离调度单：一次观察单对应一次隔离调度。
 * 状态 ISOLATING（隔离中）→ LIFTED（已解除）。解除隔离必须登记复核兽医与意见。
 */
@TableName("t_dis_isolation")
public class DisIsolation extends BaseEntity {

    public static final String STATUS_ISOLATING = "ISOLATING";
    public static final String STATUS_LIFTED = "LIFTED";

    private String isolationNo;
    private Long observationId;
    private Long batchId;
    private Long fromPondId;
    private Long isolationPoolId;
    private Long toPondId;
    private Integer quantity;
    private LocalDate moveDate;
    private String status;
    /** 调度操作者 */
    private String operatorName;
    /** 解除隔离复核兽医 */
    private String vetName;
    private String vetOpinion;
    private LocalDateTime liftedTime;
    private String requestId;
    private String liftRequestId;
    private String remark;

    public String getIsolationNo() { return isolationNo; }
    public void setIsolationNo(String isolationNo) { this.isolationNo = isolationNo; }
    public Long getObservationId() { return observationId; }
    public void setObservationId(Long observationId) { this.observationId = observationId; }
    public Long getBatchId() { return batchId; }
    public void setBatchId(Long batchId) { this.batchId = batchId; }
    public Long getFromPondId() { return fromPondId; }
    public void setFromPondId(Long fromPondId) { this.fromPondId = fromPondId; }
    public Long getIsolationPoolId() { return isolationPoolId; }
    public void setIsolationPoolId(Long isolationPoolId) { this.isolationPoolId = isolationPoolId; }
    public Long getToPondId() { return toPondId; }
    public void setToPondId(Long toPondId) { this.toPondId = toPondId; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public LocalDate getMoveDate() { return moveDate; }
    public void setMoveDate(LocalDate moveDate) { this.moveDate = moveDate; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getOperatorName() { return operatorName; }
    public void setOperatorName(String operatorName) { this.operatorName = operatorName; }
    public String getVetName() { return vetName; }
    public void setVetName(String vetName) { this.vetName = vetName; }
    public String getVetOpinion() { return vetOpinion; }
    public void setVetOpinion(String vetOpinion) { this.vetOpinion = vetOpinion; }
    public LocalDateTime getLiftedTime() { return liftedTime; }
    public void setLiftedTime(LocalDateTime liftedTime) { this.liftedTime = liftedTime; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getLiftRequestId() { return liftRequestId; }
    public void setLiftRequestId(String liftRequestId) { this.liftRequestId = liftRequestId; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
