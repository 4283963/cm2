package com.bank.reconciliation.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public class ReconciliationResult {

    private String batchNo;
    private LocalDate reconciliationDate;
    private String status;
    private Long totalRecords;
    private Long matchedCount;
    private Long localOnlyCount;
    private Long unionpayOnlyCount;
    private Long amountMismatchCount;
    private Long autoAdjustedCount;
    private BigDecimal autoAdjustedAmount;

    public ReconciliationResult() {
    }

    public ReconciliationResult(String batchNo, LocalDate reconciliationDate, String status, Long totalRecords,
                                Long matchedCount, Long localOnlyCount, Long unionpayOnlyCount, Long amountMismatchCount) {
        this.batchNo = batchNo;
        this.reconciliationDate = reconciliationDate;
        this.status = status;
        this.totalRecords = totalRecords;
        this.matchedCount = matchedCount;
        this.localOnlyCount = localOnlyCount;
        this.unionpayOnlyCount = unionpayOnlyCount;
        this.amountMismatchCount = amountMismatchCount;
    }

    public String getBatchNo() { return batchNo; }
    public void setBatchNo(String batchNo) { this.batchNo = batchNo; }

    public LocalDate getReconciliationDate() { return reconciliationDate; }
    public void setReconciliationDate(LocalDate reconciliationDate) { this.reconciliationDate = reconciliationDate; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Long getTotalRecords() { return totalRecords; }
    public void setTotalRecords(Long totalRecords) { this.totalRecords = totalRecords; }

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

    public BigDecimal getAutoAdjustedAmount() { return autoAdjustedAmount; }
    public void setAutoAdjustedAmount(BigDecimal autoAdjustedAmount) { this.autoAdjustedAmount = autoAdjustedAmount; }
}
