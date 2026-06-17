package com.bank.reconciliation;

import com.bank.reconciliation.entity.BankOrder;
import com.bank.reconciliation.entity.CheckErrorLedger;
import com.bank.reconciliation.entity.ErrorType;
import com.bank.reconciliation.entity.ReconciliationBatch;
import com.bank.reconciliation.entity.UnionPayTransaction;
import com.bank.reconciliation.repository.BankOrderRepository;
import com.bank.reconciliation.repository.CheckErrorLedgerRepository;
import com.bank.reconciliation.repository.ReconciliationBatchRepository;
import com.bank.reconciliation.repository.UnionPayTransactionRepository;
import com.bank.reconciliation.service.ReconciliationTxExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class SuperLargeAmountReconciliationTest {

    @Autowired
    private ReconciliationTxExecutor reconciliationTxExecutor;

    @Autowired
    private BankOrderRepository bankOrderRepository;

    @Autowired
    private UnionPayTransactionRepository unionPayTransactionRepository;

    @Autowired
    private CheckErrorLedgerRepository checkErrorLedgerRepository;

    @Autowired
    private ReconciliationBatchRepository batchRepository;

    private LocalDate reconDate;
    private Long batchId;

    @BeforeEach
    public void setup() {
        reconDate = LocalDate.now().minusDays(1);

        ReconciliationBatch batch = new ReconciliationBatch();
        batch.setBatchNo("TEST-SUPER-LARGE-" + System.currentTimeMillis());
        batch.setChannel("UNIONPAY");
        batch.setReconciliationDate(reconDate);
        batch.setStatus(ReconciliationBatch.Status.PARSED);
        batch = batchRepository.save(batch);
        batchId = batch.getId();

        createTestBankOrders();
        createTestUnionPayTransactions();
    }

    private void createTestBankOrders() {
        LocalDateTime baseTime = reconDate.atTime(10, 0, 0);

        String[][] largeAmounts = {
            {"ORD-LG-001", "TRACE-LG-001", "123456789.12"},
            {"ORD-LG-002", "TRACE-LG-002", "987654321.99"},
            {"ORD-LG-003", "TRACE-LG-003", "1000000000.00"},
            {"ORD-LG-004", "TRACE-LG-004", "5000000000.50"},
            {"ORD-LG-005", "TRACE-LG-005", "12345678901.23"},
            {"ORD-LG-006", "TRACE-LG-006", "99999999999.99"},
            {"ORD-LG-007", "TRACE-LG-007", "500000000000.00"},
            {"ORD-LG-008", "TRACE-LG-008", "1000000000000.01"},
            {"ORD-LOCAL-ONLY", null, "99999999.99"}
        };

        for (int i = 0; i < largeAmounts.length; i++) {
            String[] data = largeAmounts[i];
            BankOrder order = new BankOrder();
            order.setOrderNo(data[0]);
            order.setUnionpayTraceNo(data[1]);
            order.setAmount(new BigDecimal(data[2]));
            order.setCurrency("CNY");
            order.setStatus("SUCCESS");
            order.setPayerAccount("622202******8888");
            order.setPayeeAccount("622202******9999");
            order.setRemark("超级大额测试-" + data[0]);
            order.setTransDate(baseTime.plusMinutes(i * 5L));
            bankOrderRepository.save(order);
        }
    }

    private void createTestUnionPayTransactions() {
        LocalDateTime baseTime = reconDate.atTime(10, 0, 0);

        String[][] txns = {
            {"TRACE-LG-001", "ORD-LG-001", "123456789.12"},
            {"TRACE-LG-002", "ORD-LG-002", "987654321.00"},
            {"TRACE-LG-003", "ORD-LG-003", "1000000000.00"},
            {"TRACE-LG-004", "ORD-LG-004", "5000000000.50"},
            {"TRACE-LG-005", "ORD-LG-005", "12345678901.00"},
            {"TRACE-LG-006", "ORD-LG-006", "99999999999.99"},
            {"TRACE-LG-007", "ORD-LG-007", "500000000000.00"},
            {"TRACE-LG-008", "ORD-LG-008", "1000000000000.01"},
            {"TRACE-UP-ONLY", "ORD-UP-ONLY", "888888888.88"}
        };

        for (int i = 0; i < txns.length; i++) {
            String[] data = txns[i];
            UnionPayTransaction tx = new UnionPayTransaction();
            tx.setBatchId(batchId);
            tx.setTraceNo(data[0]);
            tx.setOrderNo(data[1]);
            tx.setAmount(new BigDecimal(data[2]));
            tx.setCurrency("CNY");
            tx.setTransType("LARGE_TRANSFER");
            tx.setTransDate(baseTime.plusMinutes(i * 5L));
            tx.setPayerAccount("622202******8888");
            tx.setPayeeAccount("622202******9999");
            tx.setRemark("银联大额测试-" + data[0]);
            unionPayTransactionRepository.save(tx);
        }
    }

    @Test
    public void testSuperLargeAmountReconciliation() {
        assertDoesNotThrow(() -> {
            ReconciliationBatch batch = batchRepository.findById(batchId).orElseThrow();
            reconciliationTxExecutor.executeReconciliation(batch.getBatchNo());
        });

        ReconciliationBatch batch = batchRepository.findById(batchId).orElseThrow();
        assertEquals(ReconciliationBatch.Status.COMPLETED, batch.getStatus());

        System.out.println("对账结果:");
        System.out.println("  匹配: " + batch.getMatchedCount());
        System.out.println("  本地独有: " + batch.getLocalOnlyCount());
        System.out.println("  银联独有: " + batch.getUnionpayOnlyCount());
        System.out.println("  金额不符: " + batch.getAmountMismatchCount());

        assertTrue(batch.getMatchedCount() >= 5, "匹配数应至少为5条超级大额匹配");
        assertTrue(batch.getLocalOnlyCount() >= 1, "本地独有应至少为1条");
        assertTrue(batch.getUnionpayOnlyCount() >= 1, "银联独有应至少为1条");
        assertTrue(batch.getAmountMismatchCount() >= 2, "金额不符应至少为2条");

        CheckErrorLedger amountMismatch = checkErrorLedgerRepository
                .findByBatchIdAndErrorType(batchId, ErrorType.AMOUNT_MISMATCH)
                .stream()
                .filter(e -> "ORD-LG-002".equals(e.getOrderNo()))
                .findFirst()
                .orElseThrow();

        BigDecimal expectedDiff = new BigDecimal("0.99");
        assertEquals(0, amountMismatch.getDiffAmount().compareTo(expectedDiff),
                "超级大额差值计算错误: 987654321.99 - 987654321.00 应该等于 0.99");

        System.out.println();
        System.out.println("超级大额差值计算验证通过!");
        System.out.println("  本地金额: " + amountMismatch.getLocalAmount());
        System.out.println("  银联金额: " + amountMismatch.getUnionpayAmount());
        System.out.println("  差额: " + amountMismatch.getDiffAmount());
    }
}
