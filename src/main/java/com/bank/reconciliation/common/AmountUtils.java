package com.bank.reconciliation.common;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class AmountUtils {

    public static final int SCALE = 2;
    public static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;

    public static final BigDecimal MAX_AMOUNT = new BigDecimal("99999999999999.99");
    public static final BigDecimal MIN_AMOUNT = BigDecimal.ZERO;
    public static final BigDecimal ZERO = BigDecimal.ZERO;

    private AmountUtils() {
    }

    public static BigDecimal of(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("金额不能为空");
        }
        String trimmed = value.trim();
        try {
            BigDecimal amount = new BigDecimal(trimmed);
            return normalize(amount);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("非法金额格式: " + value, e);
        }
    }

    public static BigDecimal of(long value) {
        return normalize(BigDecimal.valueOf(value));
    }

    public static BigDecimal of(double value) {
        return normalize(BigDecimal.valueOf(value));
    }

    public static BigDecimal normalize(BigDecimal amount) {
        if (amount == null) {
            throw new IllegalArgumentException("金额不能为null");
        }
        BigDecimal normalized = amount.setScale(SCALE, ROUNDING_MODE);
        validateRange(normalized);
        return normalized;
    }

    public static void validateRange(BigDecimal amount) {
        if (amount.compareTo(MIN_AMOUNT) < 0) {
            throw new IllegalArgumentException("金额不能为负数: " + amount);
        }
        if (amount.compareTo(MAX_AMOUNT) > 0) {
            throw new IllegalArgumentException("金额超过上限: " + amount + "，最大允许: " + MAX_AMOUNT);
        }
    }

    public static boolean isAmountValid(BigDecimal amount) {
        if (amount == null) {
            return false;
        }
        try {
            validateRange(amount);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public static boolean equals(BigDecimal a, BigDecimal b) {
        if (a == null || b == null) {
            return false;
        }
        return normalize(a).compareTo(normalize(b)) == 0;
    }

    public static int compare(BigDecimal a, BigDecimal b) {
        if (a == null || b == null) {
            throw new IllegalArgumentException("金额不能为null");
        }
        return normalize(a).compareTo(normalize(b));
    }

    public static BigDecimal subtract(BigDecimal a, BigDecimal b) {
        if (a == null || b == null) {
            throw new IllegalArgumentException("金额不能为null");
        }
        BigDecimal result = normalize(a).subtract(normalize(b));
        return result.setScale(SCALE, ROUNDING_MODE);
    }

    public static BigDecimal add(BigDecimal a, BigDecimal b) {
        if (a == null || b == null) {
            throw new IllegalArgumentException("金额不能为null");
        }
        BigDecimal result = normalize(a).add(normalize(b));
        return normalize(result);
    }

    public static BigDecimal negate(BigDecimal amount) {
        if (amount == null) {
            throw new IllegalArgumentException("金额不能为null");
        }
        return normalize(amount).negate().setScale(SCALE, ROUNDING_MODE);
    }

    public static boolean isZero(BigDecimal amount) {
        if (amount == null) {
            return false;
        }
        return normalize(amount).compareTo(BigDecimal.ZERO) == 0;
    }
}
