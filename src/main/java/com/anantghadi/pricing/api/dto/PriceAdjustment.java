package com.anantghadi.pricing.api.dto;

import java.math.BigDecimal;

/** One line of the price breakdown: what a rule changed and the running unit price after it. */
public record PriceAdjustment(String rule, String description, BigDecimal amount, BigDecimal runningUnitPrice) {
}
