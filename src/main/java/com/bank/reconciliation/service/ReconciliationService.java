package com.bank.reconciliation.service;

import com.bank.reconciliation.dto.ReconciliationResult;
import com.bank.reconciliation.entity.CheckErrorLedger;
import com.bank.reconciliation.entity.ErrorType;
import com.bank.reconciliation.entity.ReconciliationBatch;
import com.bank.reconciliation.common.BusinessException;
import com.bank.reconciliation.repository.CheckErrorLedgerRepository;
import com.bank.reconciliation.repository.ReconciliationBatchRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationService.class);

    private final ReconciliationBatchRepository batchRepository;
    private final CheckErrorLedgerRepository checkErrorLedgerRepository;
    private final ReconciliationTxExecutor txExecutor;

    @Autowired
    public ReconciliationService(ReconciliationBatchRepository batchRepository,
                                 CheckErrorLedgerRepository checkErrorLedgerRepository,
                                 ReconciliationTxExecutor txExecutor) {
        this.batchRepository = batchRepository;
        this.checkErrorLedgerRepository = checkErrorLedgerRepository;
        this.txExecutor = txExecutor;
    }

    public ReconciliationResult startReconciliation(String batchNo) {
        ReconciliationBatch batch = batchRepository.findByBatchNo(batchNo)
                .orElseThrow(() -> new BusinessException("批次不存在: " + batchNo));

        if (!ReconciliationBatch.Status.PARSED.equals(batch.getStatus())
                && !ReconciliationBatch.Status.FAILED.equals(batch.getStatus())) {
            throw new BusinessException("批次状态不正确，当前状态: " + batch.getStatus());
        }

        asyncReconcile(batchNo);

        ReconciliationResult result = new ReconciliationResult();
        result.setBatchNo(batchNo);
        result.setReconciliationDate(batch.getReconciliationDate());
        result.setStatus(ReconciliationBatch.Status.RECONCILING);
        result.setTotalRecords(batch.getParsedRecords());
        return result;
    }

    @Async("reconciliationExecutor")
    public void asyncReconcile(String batchNo) {
        log.info("开始异步对账: batchNo={}", batchNo);
        ReconciliationBatch batch = batchRepository.findByBatchNo(batchNo).orElse(null);
        if (batch == null) {
            log.error("批次不存在: {}", batchNo);
            return;
        }

        try {
            txExecutor.executeReconciliation(batchNo);
        } catch (Exception e) {
            log.error("对账执行失败: batchNo={}", batchNo, e);
            batch.setStatus(ReconciliationBatch.Status.FAILED);
            batch.setFinishedAt(LocalDateTime.now());
            batchRepository.save(batch);
        }
    }

    public ReconciliationResult getReconciliationResult(String batchNo) {
        ReconciliationBatch batch = batchRepository.findByBatchNo(batchNo)
                .orElseThrow(() -> new BusinessException("批次不存在: " + batchNo));

        ReconciliationResult result = new ReconciliationResult();
        result.setBatchNo(batch.getBatchNo());
        result.setReconciliationDate(batch.getReconciliationDate());
        result.setStatus(batch.getStatus());
        result.setTotalRecords(batch.getParsedRecords());
        result.setMatchedCount(batch.getMatchedCount());
        result.setLocalOnlyCount(batch.getLocalOnlyCount());
        result.setUnionpayOnlyCount(batch.getUnionpayOnlyCount());
        result.setAmountMismatchCount(batch.getAmountMismatchCount());
        return result;
    }

    public List<CheckErrorLedger> getErrorLedgers(String batchNo, ErrorType errorType, int page, int size) {
        ReconciliationBatch batch = batchRepository.findByBatchNo(batchNo)
                .orElseThrow(() -> new BusinessException("批次不存在: " + batchNo));

        if (errorType != null) {
            return checkErrorLedgerRepository.findByBatchIdAndErrorType(batch.getId(), errorType);
        }
        Pageable pageable = PageRequest.of(page, size);
        return checkErrorLedgerRepository.findByBatchId(batch.getId(), pageable).getContent();
    }
}
