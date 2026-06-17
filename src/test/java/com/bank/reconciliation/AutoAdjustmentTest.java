package com.bank.reconciliation;

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
import com.bank.reconciliation.service.AutoAdjustmentService;
import com.bank.reconciliation.service.ReconciliationTxExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class AutoAdjustmentTest {

    @Autowired
    private AutoAdjustmentService autoAdjustmentService;

    @Autowired
    private ReconciliationTxExecutor txExecutor;

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
    private String batchNo;
    private String suffix;

    @BeforeEach
    public void setup() {
        reconDate = LocalDate.now().minusDays(1);
        suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();

        batchNo = "TEST-AUTO-ADJ-" + suffix;
        ReconciliationBatch batch = new ReconciliationBatch();
        batch.setBatchNo(batchNo);
        batch.setChannel("UNIONPAY");
        batch.setReconciliationDate(reconDate);
        batch.setStatus(ReconciliationBatch.Status.PARSED);
        batch = batchRepository.save(batch);
        batchId = batch.getId();

        createTestData();
    }

    private String orderId(String base) {
        return base + "-" + suffix;
    }

    private String traceId(String base) {
        return base + "-" + suffix;
    }

    private void createTestData() {
        LocalDateTime baseTime = reconDate.atTime(10, 0, 0);

        String[][] data = {
            {"ORD-ADJ-001", "TRACE-ADJ-001", "100.00", "100.00", "完全匹配，不应调整"},
            {"ORD-ADJ-002", "TRACE-ADJ-002", "100.50", "100.49", "差0.01，应自动平账"},
            {"ORD-ADJ-003", "TRACE-ADJ-003", "200.00", "199.50", "差0.50，应自动平账"},
            {"ORD-ADJ-004", "TRACE-ADJ-004", "500.00", "499.00", "差1.00，边界值，应自动平账"},
            {"ORD-ADJ-005", "TRACE-ADJ-005", "1000.00", "998.00", "差2.00，超过阈值，不应平账"},
            {"ORD-ADJ-006", "TRACE-ADJ-006", "300.12", "300.00", "差0.12，应自动平账"},
            {"ORD-ADJ-007", null, "500.00", null, "本地独有，不应平账"},
        };

        for (int i = 0; i < data.length; i++) {
            String[] row = data[i];
            BankOrder order = new BankOrder();
            order.setOrderNo(orderId(row[0]));
            order.setUnionpayTraceNo(row[1] != null ? traceId(row[1]) : null);
            order.setAmount(AmountUtils.of(row[2]));
            order.setCurrency("CNY");
            order.setStatus("SUCCESS");
            order.setPayerAccount("622202******0001");
            order.setPayeeAccount("622202******0002");
            order.setRemark(row[4]);
            order.setTransDate(baseTime.plusMinutes(i * 5L));
            bankOrderRepository.save(order);

            if (row[1] != null && row[3] != null) {
                UnionPayTransaction tx = new UnionPayTransaction();
                tx.setBatchId(batchId);
                tx.setTraceNo(traceId(row[1]));
                tx.setOrderNo(orderId(row[0]));
                tx.setAmount(AmountUtils.of(row[3]));
                tx.setCurrency("CNY");
                tx.setTransType("TRANSFER");
                tx.setTransDate(baseTime.plusMinutes(i * 5L));
                tx.setPayerAccount("622202******0001");
                tx.setPayeeAccount("622202******0002");
                tx.setRemark(row[4]);
                unionPayTransactionRepository.save(tx);
            }
        }

        UnionPayTransaction unionOnly = new UnionPayTransaction();
        unionOnly.setBatchId(batchId);
        unionOnly.setTraceNo(traceId("TRACE-ADJ-999"));
        unionOnly.setOrderNo(orderId("ORD-ADJ-999"));
        unionOnly.setAmount(AmountUtils.of("888.88"));
        unionOnly.setCurrency("CNY");
        unionOnly.setTransType("TRANSFER");
        unionOnly.setTransDate(baseTime.plusMinutes(60));
        unionOnly.setPayerAccount("622202******0001");
        unionOnly.setPayeeAccount("622202******0002");
        unionOnly.setRemark("银联独有，不应平账");
        unionPayTransactionRepository.save(unionOnly);
    }

    @Test
    public void testAutoAdjustmentAfterReconciliation() {
        txExecutor.executeReconciliation(batchNo);

        List<CheckErrorLedger> allErrors = checkErrorLedgerRepository.findByBatchId(batchId);
        System.out.println();
        System.out.println("===== 对账后差错账列表 =====");
        for (CheckErrorLedger e : allErrors) {
            System.out.println("  " + e.getOrderNo() + " type=" + e.getErrorType()
                    + " diff=" + e.getDiffAmount() + " status=" + e.getStatus()
                    + " remark=" + e.getRemark());
        }

        long amountMismatchCount = allErrors.stream()
                .filter(e -> e.getErrorType() == ErrorType.AMOUNT_MISMATCH)
                .filter(e -> e.getOrderNo() != null && e.getOrderNo().startsWith("ORD-ADJ-"))
                .count();
        assertEquals(5, amountMismatchCount, "测试数据中的金额不符差错应为5条（0.01/0.50/1.00/2.00/0.12）");

        AutoAdjustmentService.AutoAdjustmentSummary summary = autoAdjustmentService.executeAutoAdjustForBatch(batchId);

        System.out.println();
        System.out.println("===== 自动平账执行结果 =====");
        System.out.println("  处理总数: " + summary.getProcessed());
        System.out.println("  成功: " + summary.getSuccessCount());
        System.out.println("  失败: " + summary.getFailCount());
        System.out.println("  总调整金额: " + summary.getTotalAdjustedAmount());

        assertEquals(4, summary.getProcessed(), "应处理4条符合阈值（<=1.00元）的金额不符差错：0.01/0.50/1.00/0.12");
        assertEquals(4, summary.getSuccessCount(), "4条都应平账成功");
        assertEquals(0, summary.getFailCount(), "不应有失败");
        assertTrue(summary.getTotalAdjustedAmount().compareTo(BigDecimal.ZERO) > 0);

        List<CheckErrorLedger> adjustedList = checkErrorLedgerRepository
                .findByBatchIdAndStatus(batchId, CheckErrorLedger.Status.AUTO_ADJUSTED).stream()
                .filter(e -> e.getOrderNo() != null && e.getOrderNo().startsWith("ORD-ADJ-"))
                .toList();
        System.out.println();
        System.out.println("===== 已自动平账记录 =====");
        for (CheckErrorLedger e : adjustedList) {
            System.out.println("  " + e.getOrderNo() + " diff=" + e.getDiffAmount()
                    + " refNo=" + e.getAdjustmentRefNo()
                    + " adjustedBy=" + e.getAdjustedBy()
                    + " adjustedAt=" + e.getAdjustedAt()
                    + " adjustmentAmount=" + e.getAdjustmentAmount()
                    + " remark=" + e.getRemark());
            assertNotNull(e.getAdjustmentRefNo(), "平账流水号不应为空");
            assertNotNull(e.getAdjustedAt(), "平账时间不应为空");
            assertNotNull(e.getAdjustedBy(), "操作人不应为空");
            assertTrue(e.getRemark().contains("自动平账成功"), "备注应包含平账成功标记");
            assertTrue(e.getAdjustmentAmount().compareTo(BigDecimal.ZERO) > 0, "调整金额应大于0");
        }
        assertEquals(4, adjustedList.size(), "测试数据中应有4条自动平账记录");

        List<CheckErrorLedger> stillPending = checkErrorLedgerRepository
                .findByBatchIdAndStatus(batchId, CheckErrorLedger.Status.PENDING).stream()
                .filter(e -> e.getOrderNo() != null && e.getOrderNo().startsWith("ORD-ADJ-"))
                .toList();
        System.out.println();
        System.out.println("===== 仍为待处理的差错账（不满足自动平账条件） =====");
        for (CheckErrorLedger e : stillPending) {
            System.out.println("  " + e.getOrderNo() + " type=" + e.getErrorType()
                    + " diff=" + (e.getDiffAmount() != null ? e.getDiffAmount() : "N/A")
                    + " status=" + e.getStatus());
            if (e.getErrorType() == ErrorType.AMOUNT_MISMATCH && e.getDiffAmount() != null) {
                assertTrue(e.getDiffAmount().abs().compareTo(new BigDecimal("1.00")) > 0,
                        "仍待处理的金额不符差错，差额应超过1元阈值: " + e.getOrderNo() + " diff=" + e.getDiffAmount());
            }
        }

        ReconciliationBatch updatedBatch = batchRepository.findById(batchId).orElseThrow();
        System.out.println();
        System.out.println("===== 批次自动平账统计 =====");
        System.out.println("  autoAdjustedCount=" + updatedBatch.getAutoAdjustedCount());
        System.out.println("  autoAdjustedAmount=" + updatedBatch.getAutoAdjustedAmount());
        assertEquals(Long.valueOf(4), updatedBatch.getAutoAdjustedCount());
        assertNotNull(updatedBatch.getAutoAdjustedAmount());
        assertTrue(updatedBatch.getAutoAdjustedAmount().compareTo(BigDecimal.ZERO) > 0);

        System.out.println();
        System.out.println("===== 小额差错自动平账功能测试通过 ✓ =====");
    }

    @Test
    public void testAutoAdjustmentThresholdConfig() {
        BigDecimal threshold = autoAdjustmentService.getThreshold();
        System.out.println("自动平账阈值配置: " + threshold + " 元");
        assertEquals(0, threshold.compareTo(new BigDecimal("1.00")),
                "默认阈值应为1.00元");
    }
}
