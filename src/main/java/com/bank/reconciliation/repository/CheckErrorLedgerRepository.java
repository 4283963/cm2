package com.bank.reconciliation.repository;

import com.bank.reconciliation.entity.CheckErrorLedger;
import com.bank.reconciliation.entity.ErrorType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface CheckErrorLedgerRepository extends JpaRepository<CheckErrorLedger, Long> {

    List<CheckErrorLedger> findByBatchId(Long batchId);

    Page<CheckErrorLedger> findByBatchId(Long batchId, Pageable pageable);

    List<CheckErrorLedger> findByBatchIdAndErrorType(Long batchId, ErrorType errorType);

    long countByBatchId(Long batchId);

    long countByBatchIdAndErrorType(Long batchId, ErrorType errorType);

    @Modifying
    @Query("DELETE FROM CheckErrorLedger c WHERE c.batchId = :batchId")
    void deleteByBatchId(@Param("batchId") Long batchId);

    List<CheckErrorLedger> findByStatus(String status);

    List<CheckErrorLedger> findByBatchIdAndStatus(Long batchId, String status);

    long countByBatchIdAndStatus(Long batchId, String status);

    @Query("SELECT COALESCE(SUM(ABS(c.diffAmount)), 0) FROM CheckErrorLedger c WHERE c.batchId = :batchId AND c.status = :status")
    BigDecimal sumAbsDiffAmountByBatchIdAndStatus(@Param("batchId") Long batchId, @Param("status") String status);

    @Query("SELECT c FROM CheckErrorLedger c WHERE c.batchId = :batchId AND c.errorType = 'AMOUNT_MISMATCH' AND c.status = 'PENDING' AND ABS(c.diffAmount) <= :threshold")
    List<CheckErrorLedger> findSmallAmountMismatchForAutoAdjust(@Param("batchId") Long batchId, @Param("threshold") BigDecimal threshold);
}
