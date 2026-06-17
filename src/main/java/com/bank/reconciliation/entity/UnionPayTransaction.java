package com.bank.reconciliation.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "unionpay_transaction", indexes = {
    @Index(name = "idx_uft_trace_no", columnList = "traceNo", unique = true),
    @Index(name = "idx_uft_batch_id", columnList = "batchId"),
    @Index(name = "idx_uft_trans_date", columnList = "transDate")
})
public class UnionPayTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long batchId;

    @Column(length = 64, unique = true)
    private String traceNo;

    @Column(length = 64)
    private String orderNo;

    @Column(nullable = false, precision = 32, scale = 2)
    private BigDecimal amount;

    @Column(length = 32)
    private String currency;

    @Column(length = 32)
    private String transType;

    @Column(length = 128)
    private String payerAccount;

    @Column(length = 128)
    private String payeeAccount;

    @Column(length = 256)
    private String remark;

    private LocalDateTime transDate;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public UnionPayTransaction() {
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getBatchId() { return batchId; }
    public void setBatchId(Long batchId) { this.batchId = batchId; }

    public String getTraceNo() { return traceNo; }
    public void setTraceNo(String traceNo) { this.traceNo = traceNo; }

    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getTransType() { return transType; }
    public void setTransType(String transType) { this.transType = transType; }

    public String getPayerAccount() { return payerAccount; }
    public void setPayerAccount(String payerAccount) { this.payerAccount = payerAccount; }

    public String getPayeeAccount() { return payeeAccount; }
    public void setPayeeAccount(String payeeAccount) { this.payeeAccount = payeeAccount; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }

    public LocalDateTime getTransDate() { return transDate; }
    public void setTransDate(LocalDateTime transDate) { this.transDate = transDate; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
