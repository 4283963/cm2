package com.bank.reconciliation.repository;

import com.bank.reconciliation.entity.BankOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface BankOrderRepository extends JpaRepository<BankOrder, Long> {

    Optional<BankOrder> findByOrderNo(String orderNo);

    Optional<BankOrder> findByUnionpayTraceNo(String unionpayTraceNo);

    List<BankOrder> findByTransDateBetween(LocalDateTime start, LocalDateTime end);

    Page<BankOrder> findByTransDateBetween(LocalDateTime start, LocalDateTime end, Pageable pageable);

    @Query("SELECT COUNT(b) FROM BankOrder b WHERE b.transDate BETWEEN :start AND :end")
    long countByTransDateBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT b FROM BankOrder b WHERE b.transDate BETWEEN :start AND :end AND b.status = :status")
    List<BankOrder> findByTransDateBetweenAndStatus(@Param("start") LocalDateTime start,
                                                     @Param("end") LocalDateTime end,
                                                     @Param("status") String status);

    boolean existsByOrderNo(String orderNo);
}
