package com.bank.reconciliation.dto;

import java.time.LocalDate;

public class ReconciliationUploadResponse {

    private String batchNo;
    private LocalDate reconciliationDate;
    private String channel;
    private String status;
    private String fileName;

    public ReconciliationUploadResponse() {
    }

    public ReconciliationUploadResponse(String batchNo, LocalDate reconciliationDate, String channel, String status, String fileName) {
        this.batchNo = batchNo;
        this.reconciliationDate = reconciliationDate;
        this.channel = channel;
        this.status = status;
        this.fileName = fileName;
    }

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
}
