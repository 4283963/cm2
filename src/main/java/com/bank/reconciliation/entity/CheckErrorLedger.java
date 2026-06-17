package com.bank.reconciliation.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "check_error_ledger", indexes = {
    @Index(name = "idx_cel_batch_id", columnList = "batchId"),
    @Index(name = "idx_cel_error_type", columnList = "errorType"),
    @Index(name = "idx_cel_order_no", columnList = "orderNo"),
    @Index(name = "idx_cel_status", columnList = "status")
})
public class CheckErrorLedger {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long batchId;

    @Column(length = 64)
    private String orderNo;

    @Column(length = 64)
    private String unionpayTraceNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ErrorType errorType;

    @Column(precision = 32, scale = 2)
    private BigDecimal localAmount;

    @Column(precision = 32, scale = 2)
    private BigDecimal unionpayAmount;

    @Column(precision = 32, scale = 2)
    private BigDecimal diffAmount;

    @Column(length = 32)
    private String status;

    @Column(length = 512)
    private String remark;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public CheckErrorLedger() {
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) {
            status = "PENDING";
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public static class Status {
        public static final String PENDING = "PENDING";
        public static final String CONFIRMED = "CONFIRMED";
        public static final String RESOLVED = "RESOLVED";
        public static final String IGNORED = "IGNORED";
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getBatchId() { return batchId; }
    public void setBatchId(Long batchId) { this.batchId = batchId; }

    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }

    public String getUnionpayTraceNo() { return unionpayTraceNo; }
    public void setUnionpayTraceNo(String unionpayTraceNo) { this.unionpayTraceNo = unionpayTraceNo; }

    public ErrorType getErrorType() { return errorType; }
    public void setErrorType(ErrorType errorType) { this.errorType = errorType; }

    public BigDecimal getLocalAmount() { return localAmount; }
    public void setLocalAmount(BigDecimal localAmount) { this.localAmount = localAmount; }

    public BigDecimal getUnionpayAmount() { return unionpayAmount; }
    public void setUnionpayAmount(BigDecimal unionpayAmount) { this.unionpayAmount = unionpayAmount; }

    public BigDecimal getDiffAmount() { return diffAmount; }
    public void setDiffAmount(BigDecimal diffAmount) { this.diffAmount = diffAmount; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
