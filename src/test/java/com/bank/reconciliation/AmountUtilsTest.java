package com.bank.reconciliation;

import com.bank.reconciliation.common.AmountUtils;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

public class AmountUtilsTest {

    @Test
    public void testParseLargeAmounts() {
        String[] amounts = {
            "123456789.12",
            "987654321.99",
            "1000000000.00",
            "5000000000.50",
            "12345678901.23",
            "99999999999.99",
            "500000000000.00",
            "1000000000000.01",
            "99999999999999.99"
        };

        for (String amountStr : amounts) {
            BigDecimal amount = AmountUtils.of(amountStr);
            assertNotNull(amount);
            assertTrue(AmountUtils.isAmountValid(amount));
            assertEquals(2, amount.scale());
            assertEquals(amountStr, amount.toPlainString());
        }
    }

    @Test
    public void testSubtractLargeAmounts() {
        BigDecimal a = AmountUtils.of("1000000000000.01");
        BigDecimal b = AmountUtils.of("500000000000.00");
        BigDecimal diff = AmountUtils.subtract(a, b);
        BigDecimal expected = AmountUtils.of("500000000000.01");
        assertEquals(0, diff.compareTo(expected));
    }

    @Test
    public void testAddLargeAmounts() {
        BigDecimal a = AmountUtils.of("500000000000.00");
        BigDecimal b = AmountUtils.of("500000000000.01");
        BigDecimal sum = AmountUtils.add(a, b);
        BigDecimal expected = AmountUtils.of("1000000000000.01");
        assertEquals(0, sum.compareTo(expected));
    }

    @Test
    public void testNormalizeScale() {
        BigDecimal raw1 = new BigDecimal("100");
        BigDecimal raw2 = new BigDecimal("100.0");
        BigDecimal raw3 = new BigDecimal("100.000");

        BigDecimal norm1 = AmountUtils.normalize(raw1);
        BigDecimal norm2 = AmountUtils.normalize(raw2);
        BigDecimal norm3 = AmountUtils.normalize(raw3);

        assertEquals(2, norm1.scale());
        assertEquals(2, norm2.scale());
        assertEquals(2, norm3.scale());
        assertTrue(AmountUtils.equals(norm1, norm2));
        assertTrue(AmountUtils.equals(norm2, norm3));
    }

    @Test
    public void testBoundaryCheck() {
        BigDecimal validMax = new BigDecimal("99999999999999.99");
        assertTrue(AmountUtils.isAmountValid(validMax));

        BigDecimal overLimit = new BigDecimal("100000000000000.00");
        assertFalse(AmountUtils.isAmountValid(overLimit));

        BigDecimal negative = new BigDecimal("-0.01");
        assertFalse(AmountUtils.isAmountValid(negative));

        BigDecimal zero = BigDecimal.ZERO;
        assertTrue(AmountUtils.isAmountValid(zero));
    }

    @Test
    public void testCompareDifferentScales() {
        BigDecimal a = new BigDecimal("100");
        BigDecimal b = new BigDecimal("100.00");
        assertEquals(0, AmountUtils.compare(a, b));
        assertTrue(AmountUtils.equals(a, b));
    }

    @Test
    public void testNegate() {
        BigDecimal amount = AmountUtils.of("5000000000.50");
        BigDecimal negated = AmountUtils.negate(amount);
        assertEquals("-5000000000.50", negated.toPlainString());
        assertEquals(2, negated.scale());
    }

    @Test
    public void testOfLong() {
        BigDecimal amount = AmountUtils.of(1234567890L);
        assertEquals("1234567890.00", amount.toPlainString());
        assertEquals(2, amount.scale());
    }

    @Test
    public void testOfDouble() {
        BigDecimal amount = AmountUtils.of(12345.67);
        assertEquals(2, amount.scale());
    }
}
