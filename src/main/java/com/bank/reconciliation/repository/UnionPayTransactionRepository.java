package com.bank.reconciliation.repository;

import com.bank.reconciliation.entity.UnionPayTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UnionPayTransactionRepository extends JpaRepository<UnionPayTransaction, Long> {

    Optional<UnionPayTransaction> findByTraceNo(String traceNo);

    List<UnionPayTransaction> findByBatchId(Long batchId);

    Page<UnionPayTransaction> findByBatchId(Long batchId, Pageable pageable);

    long countByBatchId(Long batchId);

    @Query("SELECT u.traceNo FROM UnionPayTransaction u WHERE u.batchId = :batchId")
    List<String> findTraceNosByBatchId(@Param("batchId") Long batchId);

    @Query("SELECT u FROM UnionPayTransaction u WHERE u.batchId = :batchId AND u.traceNo = :traceNo")
    Optional<UnionPayTransaction> findByBatchIdAndTraceNo(@Param("batchId") Long batchId,
                                                          @Param("traceNo") String traceNo);

    @Modifying
    @Query("DELETE FROM UnionPayTransaction u WHERE u.batchId = :batchId")
    void deleteByBatchId(@Param("batchId") Long batchId);

    @Query(value = "SELECT trace_no, order_no, amount FROM unionpay_transaction WHERE batch_id = :batchId ORDER BY id LIMIT :offset, :limit", nativeQuery = true)
    List<Object[]> findSimpleFieldsByBatchIdWithOffset(@Param("batchId") Long batchId,
                                                       @Param("offset") long offset,
                                                       @Param("limit") int limit);
}
