package com.bank.reconciliation.schedule;

import com.bank.reconciliation.entity.ReconciliationBatch;
import com.bank.reconciliation.repository.ReconciliationBatchRepository;
import com.bank.reconciliation.service.ReconciliationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Component
public class ReconciliationScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationScheduler.class);

    private final ReconciliationBatchRepository batchRepository;
    private final ReconciliationService reconciliationService;

    @Value("${reconciliation.file-storage-path:./reconciliation-files}")
    private String fileStoragePath;

    @Autowired
    public ReconciliationScheduler(ReconciliationBatchRepository batchRepository,
                                   ReconciliationService reconciliationService) {
        this.batchRepository = batchRepository;
        this.reconciliationService = reconciliationService;
    }

    @Scheduled(cron = "0 0 2 * * ?")
    public void autoReconcile() {
        log.info("开始执行自动对账任务");
        LocalDate yesterday = LocalDate.now().minusDays(1);
        autoReconcileForDate(yesterday, "UNIONPAY");
        autoReconcileForDate(yesterday, "PBOC");
        log.info("自动对账任务执行完成");
    }

    private void autoReconcileForDate(LocalDate date, String channel) {
        try {
            List<ReconciliationBatch> batches = batchRepository.findByReconciliationDateAndChannel(date, channel);

            for (ReconciliationBatch batch : batches) {
                if (ReconciliationBatch.Status.PARSED.equals(batch.getStatus()) ||
                    ReconciliationBatch.Status.FAILED.equals(batch.getStatus())) {
                    log.info("自动触发对账: batchNo={}, channel={}, date={}", batch.getBatchNo(), channel, date);
                    reconciliationService.asyncReconcile(batch.getBatchNo());
                }
            }

            if (batches.isEmpty()) {
                log.warn("未找到待对账批次: date={}, channel={}", date, channel);
            }
        } catch (Exception e) {
            log.error("自动对账失败: date={}, channel={}", date, channel, e);
        }
    }
}
