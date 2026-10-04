package com.evops.aqua.disease.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

/**
 * 疫病模块变更证据：开单/调度/解除/消毒/权限调整均留痕（对象、动作、操作前后 JSON 快照、幂等请求号）。
 * 只追加不修改不删除。
 */
@TableName("t_dis_change_evidence")
public class DisChangeEvidence extends BaseEntity {

    public static final String ENTITY_OBSERVATION = "DIS_OBSERVATION";
    public static final String ENTITY_ISOLATION_POOL = "DIS_ISOLATION_POOL";
    public static final String ENTITY_ISOLATION = "DIS_ISOLATION";

    public static final String ACTION_CREATE = "CREATE";
    public static final String ACTION_DISPATCH = "DISPATCH";
    public static final String ACTION_LIFT = "LIFT";
    public static final String ACTION_DISINFECT = "DISINFECT";
    public static final String ACTION_PERMISSION = "PERMISSION";

    private String entityType;
    private Long entityId;
    private String action;
    private String requestId;
    private String beforeJson;
    private String afterJson;
    private String detail;

    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }
    public Long getEntityId() { return entityId; }
    public void setEntityId(Long entityId) { this.entityId = entityId; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getBeforeJson() { return beforeJson; }
    public void setBeforeJson(String beforeJson) { this.beforeJson = beforeJson; }
    public String getAfterJson() { return afterJson; }
    public void setAfterJson(String afterJson) { this.afterJson = afterJson; }
    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }
}
