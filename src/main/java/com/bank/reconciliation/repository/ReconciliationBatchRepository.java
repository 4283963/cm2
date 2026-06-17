package com.bank.reconciliation.repository;

import com.bank.reconciliation.entity.ReconciliationBatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface ReconciliationBatchRepository extends JpaRepository<ReconciliationBatch, Long> {

    Optional<ReconciliationBatch> findByBatchNo(String batchNo);

    List<ReconciliationBatch> findByReconciliationDate(LocalDate reconciliationDate);

    List<ReconciliationBatch> findByReconciliationDateAndChannel(LocalDate reconciliationDate, String channel);

    List<ReconciliationBatch> findByStatus(String status);

    boolean existsByBatchNo(String batchNo);
}
