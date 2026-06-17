package com.bank.reconciliation.entity;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "reconciliation_batch", indexes = {
    @Index(name = "idx_rb_batch_no", columnList = "batchNo", unique = true),
    @Index(name = "idx_rb_recon_date", columnList = "reconciliationDate"),
    @Index(name = "idx_rb_status", columnList = "status")
})
public class ReconciliationBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64, unique = true)
    private String batchNo;

    @Column(nullable = false)
    private LocalDate reconciliationDate;

    @Column(length = 16)
    private String channel;

    @Column(length = 32)
    private String status;

    @Column(name = "file_name", length = 256)
    private String fileName;

    @Column(name = "total_records")
    private Long totalRecords;

    @Column(name = "parsed_records")
    private Long parsedRecords;

    @Column(name = "matched_count")
    private Long matchedCount;

    @Column(name = "local_only_count")
    private Long localOnlyCount;

    @Column(name = "unionpay_only_count")
    private Long unionpayOnlyCount;

    @Column(name = "amount_mismatch_count")
    private Long amountMismatchCount;

    @Column(name = "auto_adjusted_count")
    private Long autoAdjustedCount;

    @Column(name = "auto_adjusted_amount", precision = 32, scale = 2)
    private java.math.BigDecimal autoAdjustedAmount;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public ReconciliationBatch() {
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public static class Status {
        public static final String PENDING = "PENDING";
        public static final String PARSING = "PARSING";
        public static final String PARSED = "PARSED";
        public static final String RECONCILING = "RECONCILING";
        public static final String COMPLETED = "COMPLETED";
        public static final String FAILED = "FAILED";
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getBatchNo() { return batchNo; }
    public void setBatchNo(String batchNo) { this.batchNo = batchNo; }

    public LocalDate getReconciliationDate() { return reconciliationDate; }
    public void setReconciliationDate(LocalDate reconciliationDate) { this.reconciliationDate = reconciliationDate; }

    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public Long getTotalRecords() { return totalRecords; }
    public void setTotalRecords(Long totalRecords) { this.totalRecords = totalRecords; }

    public Long getParsedRecords() { return parsedRecords; }
    public void setParsedRecords(Long parsedRecords) { this.parsedRecords = parsedRecords; }

    public Long getMatchedCount() { return matchedCount; }
    public void setMatchedCount(Long matchedCount) { this.matchedCount = matchedCount; }

    public Long getLocalOnlyCount() { return localOnlyCount; }
    public void setLocalOnlyCount(Long localOnlyCount) { this.localOnlyCount = localOnlyCount; }

    public Long getUnionpayOnlyCount() { return unionpayOnlyCount; }
    public void setUnionpayOnlyCount(Long unionpayOnlyCount) { this.unionpayOnlyCount = unionpayOnlyCount; }

    public Long getAmountMismatchCount() { return amountMismatchCount; }
    public void setAmountMismatchCount(Long amountMismatchCount) { this.amountMismatchCount = amountMismatchCount; }

    public Long getAutoAdjustedCount() { return autoAdjustedCount; }
    public void setAutoAdjustedCount(Long autoAdjustedCount) { this.autoAdjustedCount = autoAdjustedCount; }

    public java.math.BigDecimal getAutoAdjustedAmount() { return autoAdjustedAmount; }
    public void setAutoAdjustedAmount(java.math.BigDecimal autoAdjustedAmount) { this.autoAdjustedAmount = autoAdjustedAmount; }

    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }

    public LocalDateTime getFinishedAt() { return finishedAt; }
    public void setFinishedAt(LocalDateTime finishedAt) { this.finishedAt = finishedAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
