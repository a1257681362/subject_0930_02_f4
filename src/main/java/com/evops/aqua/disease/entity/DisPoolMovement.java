package com.evops.aqua.disease.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.time.LocalDate;

/**
 * 原池与隔离池之间的数量变化流水：转入隔离 ISOLATE_IN / 解除返回 RETURN_BACK，逐笔记录。
 */
@TableName("t_dis_pool_movement")
public class DisPoolMovement extends BaseEntity {

    public static final String TYPE_ISOLATE_IN = "ISOLATE_IN";
    public static final String TYPE_RETURN_BACK = "RETURN_BACK";

    private Long isolationId;
    private Long batchId;
    private String moveType;
    private Long fromPondId;
    private Long toPondId;
    private Integer quantity;
    private LocalDate moveDate;
    private String operatorName;
    private String requestId;

    public Long getIsolationId() { return isolationId; }
    public void setIsolationId(Long isolationId) { this.isolationId = isolationId; }
    public Long getBatchId() { return batchId; }
    public void setBatchId(Long batchId) { this.batchId = batchId; }
    public String getMoveType() { return moveType; }
    public void setMoveType(String moveType) { this.moveType = moveType; }
    public Long getFromPondId() { return fromPondId; }
    public void setFromPondId(Long fromPondId) { this.fromPondId = fromPondId; }
    public Long getToPondId() { return toPondId; }
    public void setToPondId(Long toPondId) { this.toPondId = toPondId; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public LocalDate getMoveDate() { return moveDate; }
    public void setMoveDate(LocalDate moveDate) { this.moveDate = moveDate; }
    public String getOperatorName() { return operatorName; }
    public void setOperatorName(String operatorName) { this.operatorName = operatorName; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
}
