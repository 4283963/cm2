package com.bank.reconciliation.service;

import com.bank.reconciliation.common.AmountUtils;
import com.bank.reconciliation.entity.CheckErrorLedger;
import com.bank.reconciliation.entity.ErrorType;
import com.bank.reconciliation.entity.ReconciliationBatch;
import com.bank.reconciliation.repository.CheckErrorLedgerRepository;
import com.bank.reconciliation.repository.ReconciliationBatchRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class AutoAdjustmentService {

    private static final Logger log = LoggerFactory.getLogger(AutoAdjustmentService.class);

    public static final BigDecimal DEFAULT_THRESHOLD = new BigDecimal("1.00");

    private final CheckErrorLedgerRepository errorLedgerRepository;
    private final ReconciliationBatchRepository batchRepository;
    private final InternalFinanceApiService financeApiService;

    @Value("${reconciliation.auto-adjust-threshold:1.00}")
    private BigDecimal threshold;

    @Autowired
    public AutoAdjustmentService(CheckErrorLedgerRepository errorLedgerRepository,
                                ReconciliationBatchRepository batchRepository,
                                InternalFinanceApiService financeApiService) {
        this.errorLedgerRepository = errorLedgerRepository;
        this.batchRepository = batchRepository;
        this.financeApiService = financeApiService;
    }

    public static class AutoAdjustmentSummary {
        private long processed;
        private long successCount;
        private long failCount;
        private BigDecimal totalAdjustedAmount;

        public AutoAdjustmentSummary() {
            this.processed = 0;
            this.successCount = 0;
            this.failCount = 0;
            this.totalAdjustedAmount = AmountUtils.ZERO;
        }

        public long getProcessed() { return processed; }
        public long getSuccessCount() { return successCount; }
        public long getFailCount() { return failCount; }
        public BigDecimal getTotalAdjustedAmount() { return totalAdjustedAmount; }

        public void incrementProcessed() { this.processed++; }
        public void incrementSuccess(BigDecimal amount) {
            this.successCount++;
            this.totalAdjustedAmount = AmountUtils.add(this.totalAdjustedAmount, amount.abs());
        }
        public void incrementFail() { this.failCount++; }
    }

    @Transactional(rollbackFor = Exception.class)
    public AutoAdjustmentSummary executeAutoAdjustForBatch(Long batchId) {
        AutoAdjustmentSummary summary = new AutoAdjustmentSummary();

        if (!financeApiService.isAutoAdjustEnabled()) {
            log.info("自动平账功能已关闭，跳过批次: {}", batchId);
            return summary;
        }

        List<CheckErrorLedger> candidates = errorLedgerRepository
                .findSmallAmountMismatchForAutoAdjust(batchId, threshold != null ? threshold : DEFAULT_THRESHOLD);

        if (candidates == null || candidates.isEmpty()) {
            log.info("批次 {} 无符合自动平账候选记录", batchId);
            return summary;
        }

        log.info("批次 {} 发现 {} 条小额差错候选，开始自动平账处理（阈值: {}元）",
                batchId, candidates.size(), threshold != null ? threshold : DEFAULT_THRESHOLD);

        for (CheckErrorLedger ledger : candidates) {
            summary.incrementProcessed();
            try {
                processSingleLedger(ledger, summary);
            } catch (Exception e) {
                log.warn("自动平账异常: ledgerId={}, orderNo={}, error={}",
                        ledger.getId(), ledger.getOrderNo(), e.getMessage());
                summary.incrementFail();
            }
        }

        updateBatchStats(batchId, summary);

        log.info("批次 {} 自动平账完成: 处理={}, 成功={}, 失败={}, 总调整金额={}",
                batchId, summary.getProcessed(), summary.getSuccessCount(),
                summary.getFailCount(), summary.getTotalAdjustedAmount());

        return summary;
    }

    private void processSingleLedger(CheckErrorLedger ledger, AutoAdjustmentSummary summary) {
        if (ledger.getErrorType() != ErrorType.AMOUNT_MISMATCH) {
            return;
        }

        BigDecimal diffAmount = ledger.getDiffAmount();
        if (diffAmount == null) {
            return;
        }

        BigDecimal absDiff = diffAmount.abs();
        BigDecimal effectiveThreshold = threshold != null ? threshold : DEFAULT_THRESHOLD;
        if (AmountUtils.compare(absDiff, effectiveThreshold) > 0) {
            return;
        }

        String remarkSuffix = ledger.getRemark() != null ? ledger.getRemark() : "";
        InternalFinanceApiService.AdjustmentResult result = financeApiService
                .postSmallDifferenceToProfitLoss(
                        ledger.getOrderNo(),
                        diffAmount,
                        "小额差异自动平账-" + remarkSuffix);

        if (result.isSuccess()) {
            ledger.setStatus(CheckErrorLedger.Status.AUTO_ADJUSTED);
            ledger.setAdjustedAt(LocalDateTime.now());
            ledger.setAdjustedBy(financeApiService.getFinanceOperator());
            ledger.setAdjustmentRefNo(result.getRefNo());
            ledger.setAdjustmentAmount(absDiff);

            String originalRemark = ledger.getRemark() != null ? ledger.getRemark() : "";
            ledger.setRemark(originalRemark + " [系统自动平账成功，流水号:" + result.getRefNo() + "]");

            errorLedgerRepository.save(ledger);
            summary.incrementSuccess(absDiff);

            log.info("自动平账成功: orderNo={}, diff={}, refNo={}",
                    ledger.getOrderNo(), diffAmount, result.getRefNo());
        } else {
            summary.incrementFail();
            log.warn("自动平账失败: orderNo={}, diff={}, reason={}",
                    ledger.getOrderNo(), diffAmount, result.getMessage());
        }
    }

    private void updateBatchStats(Long batchId, AutoAdjustmentSummary summary) {
        ReconciliationBatch batch = batchRepository.findById(batchId).orElse(null);
        if (batch == null) {
            return;
        }
        batch.setAutoAdjustedCount(summary.getSuccessCount());
        batch.setAutoAdjustedAmount(summary.getTotalAdjustedAmount());
        batchRepository.save(batch);
    }

    public BigDecimal getThreshold() {
        return threshold != null ? threshold : DEFAULT_THRESHOLD;
    }
}
