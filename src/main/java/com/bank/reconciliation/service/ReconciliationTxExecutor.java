package com.bank.reconciliation.service;

import com.bank.reconciliation.common.AmountUtils;
import com.bank.reconciliation.entity.BankOrder;
import com.bank.reconciliation.entity.CheckErrorLedger;
import com.bank.reconciliation.entity.ErrorType;
import com.bank.reconciliation.entity.ReconciliationBatch;
import com.bank.reconciliation.entity.UnionPayTransaction;
import com.bank.reconciliation.repository.BankOrderRepository;
import com.bank.reconciliation.repository.CheckErrorLedgerRepository;
import com.bank.reconciliation.repository.ReconciliationBatchRepository;
import com.bank.reconciliation.repository.UnionPayTransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class ReconciliationTxExecutor {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationTxExecutor.class);

    private final ReconciliationBatchRepository batchRepository;
    private final BankOrderRepository bankOrderRepository;
    private final UnionPayTransactionRepository unionPayTransactionRepository;
    private final CheckErrorLedgerRepository checkErrorLedgerRepository;

    @Value("${reconciliation.batch-size:1000}")
    private int batchSize;

    @Autowired
    public ReconciliationTxExecutor(ReconciliationBatchRepository batchRepository,
                                     BankOrderRepository bankOrderRepository,
                                     UnionPayTransactionRepository unionPayTransactionRepository,
                                     CheckErrorLedgerRepository checkErrorLedgerRepository) {
        this.batchRepository = batchRepository;
        this.bankOrderRepository = bankOrderRepository;
        this.unionPayTransactionRepository = unionPayTransactionRepository;
        this.checkErrorLedgerRepository = checkErrorLedgerRepository;
    }

    @Transactional(rollbackFor = Exception.class)
    public void executeReconciliation(String batchNo) {
        ReconciliationBatch batch = batchRepository.findByBatchNo(batchNo)
                .orElseThrow(() -> new RuntimeException("批次不存在: " + batchNo));

        batch.setStatus(ReconciliationBatch.Status.RECONCILING);
        batch.setStartedAt(LocalDateTime.now());
        batchRepository.save(batch);
        batchRepository.flush();

        checkErrorLedgerRepository.deleteByBatchId(batch.getId());

        ReconciliationStats stats = performReconciliation(batch);

        batch.setStatus(ReconciliationBatch.Status.COMPLETED);
        batch.setFinishedAt(LocalDateTime.now());
        batch.setMatchedCount(stats.matchedCount);
        batch.setLocalOnlyCount(stats.localOnlyCount);
        batch.setUnionpayOnlyCount(stats.unionpayOnlyCount);
        batch.setAmountMismatchCount(stats.amountMismatchCount);
        batchRepository.save(batch);

        log.info("对账完成: batchNo={}, matched={}, localOnly={}, unionpayOnly={}, amountMismatch={}",
                batchNo, stats.matchedCount, stats.localOnlyCount, stats.unionpayOnlyCount, stats.amountMismatchCount);
    }

    private ReconciliationStats performReconciliation(ReconciliationBatch batch) {
        ReconciliationStats stats = new ReconciliationStats();

        LocalDate reconDate = batch.getReconciliationDate();
        LocalDateTime startOfDay = reconDate.atStartOfDay();
        LocalDateTime endOfDay = reconDate.atTime(LocalTime.MAX);

        Map<String, UnionPayTransaction> unionpayMap = loadUnionPayTransactions(batch.getId());

        Set<String> matchedTraceNos = new HashSet<>();
        List<CheckErrorLedger> errorBuffer = new ArrayList<>(batchSize);

        long totalBankOrders = bankOrderRepository.countByTransDateBetween(startOfDay, endOfDay);
        log.info("本地订单总数: {}", totalBankOrders);

        int pageNumber = 0;
        Pageable pageable = PageRequest.of(pageNumber, batchSize);
        Page<BankOrder> bankOrderPage = bankOrderRepository.findByTransDateBetween(startOfDay, endOfDay, pageable);

        while (!bankOrderPage.isEmpty()) {
            for (BankOrder bankOrder : bankOrderPage.getContent()) {
                try {
                    processBankOrder(batch.getId(), bankOrder, unionpayMap, matchedTraceNos, errorBuffer, stats);
                } catch (Exception e) {
                    log.warn("处理本地订单异常: orderNo={}, error={}", bankOrder.getOrderNo(), e.getMessage());
                    stats.processErrorCount++;
                }

                if (errorBuffer.size() >= batchSize) {
                    checkErrorLedgerRepository.saveAll(errorBuffer);
                    errorBuffer.clear();
                }
            }

            pageNumber++;
            pageable = PageRequest.of(pageNumber, batchSize);
            bankOrderPage = bankOrderRepository.findByTransDateBetween(startOfDay, endOfDay, pageable);
        }

        for (Map.Entry<String, UnionPayTransaction> entry : unionpayMap.entrySet()) {
            if (!matchedTraceNos.contains(entry.getKey())) {
                try {
                    errorBuffer.add(createUnionpayOnlyError(batch.getId(), entry.getValue()));
                    stats.unionpayOnlyCount++;
                } catch (Exception e) {
                    log.warn("处理银联流水异常: traceNo={}, error={}", entry.getKey(), e.getMessage());
                    stats.processErrorCount++;
                }

                if (errorBuffer.size() >= batchSize) {
                    checkErrorLedgerRepository.saveAll(errorBuffer);
                    errorBuffer.clear();
                }
            }
        }

        if (!errorBuffer.isEmpty()) {
            checkErrorLedgerRepository.saveAll(errorBuffer);
        }

        if (stats.processErrorCount > 0) {
            log.warn("本次对账存在 {} 条处理异常的记录", stats.processErrorCount);
        }

        return stats;
    }

    private void processBankOrder(Long batchId, BankOrder bankOrder, Map<String, UnionPayTransaction> unionpayMap,
                                   Set<String> matchedTraceNos, List<CheckErrorLedger> errorBuffer,
                                   ReconciliationStats stats) {

        if (bankOrder.getAmount() == null) {
            log.warn("本地订单金额为空: orderNo={}", bankOrder.getOrderNo());
            stats.processErrorCount++;
            return;
        }

        if (!AmountUtils.isAmountValid(bankOrder.getAmount())) {
            log.warn("本地订单金额不合法: orderNo={}, amount={}", bankOrder.getOrderNo(), bankOrder.getAmount());
            stats.processErrorCount++;
            return;
        }

        String traceNo = bankOrder.getUnionpayTraceNo();
        if (traceNo != null && !traceNo.isEmpty()) {
            UnionPayTransaction unionpayTx = unionpayMap.get(traceNo);
            if (unionpayTx != null) {
                matchedTraceNos.add(traceNo);
                if (AmountUtils.compare(bankOrder.getAmount(), unionpayTx.getAmount()) != 0) {
                    errorBuffer.add(createAmountMismatchError(batchId, bankOrder, unionpayTx));
                    stats.amountMismatchCount++;
                } else {
                    stats.matchedCount++;
                }
            } else {
                errorBuffer.add(createLocalOnlyError(batchId, bankOrder));
                stats.localOnlyCount++;
            }
        } else {
            String orderNo = bankOrder.getOrderNo();
            UnionPayTransaction unionpayTx = findByOrderNo(unionpayMap, orderNo);
            if (unionpayTx != null) {
                matchedTraceNos.add(unionpayTx.getTraceNo());
                if (AmountUtils.compare(bankOrder.getAmount(), unionpayTx.getAmount()) != 0) {
                    errorBuffer.add(createAmountMismatchError(batchId, bankOrder, unionpayTx));
                    stats.amountMismatchCount++;
                } else {
                    stats.matchedCount++;
                }
            } else {
                errorBuffer.add(createLocalOnlyError(batchId, bankOrder));
                stats.localOnlyCount++;
            }
        }
    }

    private Map<String, UnionPayTransaction> loadUnionPayTransactions(Long batchId) {
        Map<String, UnionPayTransaction> map = new HashMap<>();

        int pageNumber = 0;
        Pageable pageable = PageRequest.of(pageNumber, batchSize);
        Page<UnionPayTransaction> page = unionPayTransactionRepository.findByBatchId(batchId, pageable);

        while (!page.isEmpty()) {
            for (UnionPayTransaction tx : page.getContent()) {
                if (tx.getAmount() != null && AmountUtils.isAmountValid(tx.getAmount())) {
                    map.put(tx.getTraceNo(), tx);
                } else {
                    log.warn("银联流水金额不合法，跳过: traceNo={}, amount={}", tx.getTraceNo(), tx.getAmount());
                }
            }
            pageNumber++;
            pageable = PageRequest.of(pageNumber, batchSize);
            page = unionPayTransactionRepository.findByBatchId(batchId, pageable);
        }

        log.info("加载银联流水记录数: {}", map.size());
        return map;
    }

    private UnionPayTransaction findByOrderNo(Map<String, UnionPayTransaction> map, String orderNo) {
        if (orderNo == null) {
            return null;
        }
        for (UnionPayTransaction tx : map.values()) {
            if (orderNo.equals(tx.getOrderNo())) {
                return tx;
            }
        }
        return null;
    }

    private CheckErrorLedger createLocalOnlyError(Long batchId, BankOrder bankOrder) {
        BigDecimal localAmount = AmountUtils.normalize(bankOrder.getAmount());
        CheckErrorLedger ledger = new CheckErrorLedger();
        ledger.setBatchId(batchId);
        ledger.setOrderNo(bankOrder.getOrderNo());
        ledger.setUnionpayTraceNo(bankOrder.getUnionpayTraceNo());
        ledger.setErrorType(ErrorType.LOCAL_ONLY);
        ledger.setLocalAmount(localAmount);
        ledger.setUnionpayAmount(BigDecimal.ZERO);
        ledger.setDiffAmount(localAmount);
        ledger.setRemark("本地有银联无");
        ledger.setStatus(CheckErrorLedger.Status.PENDING);
        return ledger;
    }

    private CheckErrorLedger createUnionpayOnlyError(Long batchId, UnionPayTransaction unionpayTx) {
        BigDecimal unionpayAmount = AmountUtils.normalize(unionpayTx.getAmount());
        CheckErrorLedger ledger = new CheckErrorLedger();
        ledger.setBatchId(batchId);
        ledger.setOrderNo(unionpayTx.getOrderNo());
        ledger.setUnionpayTraceNo(unionpayTx.getTraceNo());
        ledger.setErrorType(ErrorType.UNIONPAY_ONLY);
        ledger.setLocalAmount(BigDecimal.ZERO);
        ledger.setUnionpayAmount(unionpayAmount);
        ledger.setDiffAmount(AmountUtils.negate(unionpayAmount));
        ledger.setRemark("本地无银联有");
        ledger.setStatus(CheckErrorLedger.Status.PENDING);
        return ledger;
    }

    private CheckErrorLedger createAmountMismatchError(Long batchId, BankOrder bankOrder, UnionPayTransaction unionpayTx) {
        BigDecimal localAmount = AmountUtils.normalize(bankOrder.getAmount());
        BigDecimal unionpayAmount = AmountUtils.normalize(unionpayTx.getAmount());
        BigDecimal diff = AmountUtils.subtract(localAmount, unionpayAmount);

        CheckErrorLedger ledger = new CheckErrorLedger();
        ledger.setBatchId(batchId);
        ledger.setOrderNo(bankOrder.getOrderNo());
        ledger.setUnionpayTraceNo(bankOrder.getUnionpayTraceNo());
        ledger.setErrorType(ErrorType.AMOUNT_MISMATCH);
        ledger.setLocalAmount(localAmount);
        ledger.setUnionpayAmount(unionpayAmount);
        ledger.setDiffAmount(diff);
        ledger.setRemark("金额不符");
        ledger.setStatus(CheckErrorLedger.Status.PENDING);
        return ledger;
    }

    private static class ReconciliationStats {
        long matchedCount = 0;
        long localOnlyCount = 0;
        long unionpayOnlyCount = 0;
        long amountMismatchCount = 0;
        long processErrorCount = 0;
    }
}
