package com.bank.reconciliation.service;

import com.bank.reconciliation.common.AmountUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
public class InternalFinanceApiService {

    private static final Logger log = LoggerFactory.getLogger(InternalFinanceApiService.class);

    @Value("${reconciliation.small-profit-loss-account:660201}")
    private String smallProfitLossAccount;

    @Value("${reconciliation.finance-operator:SYSTEM_AUTO}")
    private String financeOperator;

    @Value("${reconciliation.auto-adjust-enabled:true}")
    private boolean autoAdjustEnabled;

    public static class AdjustmentResult {
        private final boolean success;
        private final String refNo;
        private final String message;

        public AdjustmentResult(boolean success, String refNo, String message) {
            this.success = success;
            this.refNo = refNo;
            this.message = message;
        }

        public boolean isSuccess() { return success; }
        public String getRefNo() { return refNo; }
        public String getMessage() { return message; }

        public static AdjustmentResult success(String refNo) {
            return new AdjustmentResult(true, refNo, "自动平账成功");
        }

        public static AdjustmentResult fail(String message) {
            return new AdjustmentResult(false, null, message);
        }
    }

    public boolean isAutoAdjustEnabled() {
        return autoAdjustEnabled;
    }

    public AdjustmentResult postSmallDifferenceToProfitLoss(String orderNo, BigDecimal diffAmount, String remark) {
        if (!autoAdjustEnabled) {
            return AdjustmentResult.fail("自动平账功能已关闭");
        }

        BigDecimal absDiff = diffAmount.abs();
        if (!AmountUtils.isAmountValid(absDiff)) {
            return AdjustmentResult.fail("金额不合法: " + diffAmount);
        }

        try {
            String refNo = generateRefNo();
            String direction = diffAmount.compareTo(BigDecimal.ZERO) >= 0 ? "DEBIT" : "CREDIT";
            log.info("[模拟调用内部小额损益科目API] 订单号={}, 科目={}, 差额={}({}), 备注={}, 操作号={}",
                    orderNo, smallProfitLossAccount, diffAmount, direction, remark, refNo);

            log.info("  -> 财务记账：借/贷: " + smallProfitLossAccount + " 金额: " + absDiff + " 操作人: " + financeOperator);

            return AdjustmentResult.success(refNo);
        } catch (Exception e) {
            log.error("调用小额损益科目记账失败: orderNo={}, error={}", orderNo, e.getMessage());
            return AdjustmentResult.fail("财务系统调用异常: " + e.getMessage());
        }
    }

    public String getFinanceOperator() {
        return financeOperator;
    }

    private String generateRefNo() {
        String datePart = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String randomPart = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        return "ADJ" + datePart + randomPart;
    }
}
