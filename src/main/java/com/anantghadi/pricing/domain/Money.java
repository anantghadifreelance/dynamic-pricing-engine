package com.anantghadi.pricing.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Central place for monetary arithmetic. Every amount the engine produces goes through
 * {@link #round(BigDecimal)} so rounding is explicit and consistent (HALF_UP, 2 decimal places).
 */
public final class Money {

    public static final int SCALE = 2;
    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;
    public static final BigDecimal ZERO = BigDecimal.ZERO.setScale(SCALE, ROUNDING);

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final int INTERMEDIATE_SCALE = 10;

    private Money() {
    }

    public static BigDecimal round(BigDecimal amount) {
        return amount.setScale(SCALE, ROUNDING);
    }

    public static BigDecimal of(String amount) {
        return round(new BigDecimal(amount));
    }

    /** {@code amount * percent / 100}, rounded. */
    public static BigDecimal percentOf(BigDecimal amount, BigDecimal percent) {
        return round(amount.multiply(percent).divide(HUNDRED, INTERMEDIATE_SCALE, ROUNDING));
    }

    /** Converts a percentage such as {@code 15} into the fraction {@code 0.15}. */
    public static BigDecimal fraction(BigDecimal percent) {
        return percent.divide(HUNDRED, INTERMEDIATE_SCALE, ROUNDING);
    }

    public static BigDecimal divide(BigDecimal dividend, BigDecimal divisor) {
        return round(dividend.divide(divisor, INTERMEDIATE_SCALE, ROUNDING));
    }
}
