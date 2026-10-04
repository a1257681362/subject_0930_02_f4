package com.evops.aqua.disease.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 隔离池档案：挂在养殖池上，登记可承接的最高风险等级、容量、消毒状态与调度权限。
 * 调度前校验：池等级不低于观察单风险等级、剩余容量足够、消毒在有效期内、调度权限开启。
 */
@TableName("t_dis_isolation_pool")
public class DisIsolationPool extends BaseEntity {

    private Long pondId;
    /** 可承接的最高风险等级：LOW / MEDIUM / HIGH */
    private String riskGrade;
    /** 可容纳尾数 */
    private Integer capacityQty;
    private Boolean disinfectedFlag;
    private LocalDateTime disinfectedTime;
    private LocalDate disinfectValidUntil;
    private Boolean dispatchEnabled;
    private String dispatchDisabledReason;
    private String remark;

    public Long getPondId() { return pondId; }
    public void setPondId(Long pondId) { this.pondId = pondId; }
    public String getRiskGrade() { return riskGrade; }
    public void setRiskGrade(String riskGrade) { this.riskGrade = riskGrade; }
    public Integer getCapacityQty() { return capacityQty; }
    public void setCapacityQty(Integer capacityQty) { this.capacityQty = capacityQty; }
    public Boolean getDisinfectedFlag() { return disinfectedFlag; }
    public void setDisinfectedFlag(Boolean disinfectedFlag) { this.disinfectedFlag = disinfectedFlag; }
    public LocalDateTime getDisinfectedTime() { return disinfectedTime; }
    public void setDisinfectedTime(LocalDateTime disinfectedTime) { this.disinfectedTime = disinfectedTime; }
    public LocalDate getDisinfectValidUntil() { return disinfectValidUntil; }
    public void setDisinfectValidUntil(LocalDate disinfectValidUntil) { this.disinfectValidUntil = disinfectValidUntil; }
    public Boolean getDispatchEnabled() { return dispatchEnabled; }
    public void setDispatchEnabled(Boolean dispatchEnabled) { this.dispatchEnabled = dispatchEnabled; }
    public String getDispatchDisabledReason() { return dispatchDisabledReason; }
    public void setDispatchDisabledReason(String dispatchDisabledReason) { this.dispatchDisabledReason = dispatchDisabledReason; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
