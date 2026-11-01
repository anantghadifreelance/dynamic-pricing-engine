package com.anantghadi.pricing.engine;

import com.anantghadi.pricing.api.dto.PriceAdjustment;
import com.anantghadi.pricing.api.dto.QuoteRequest;
import com.anantghadi.pricing.domain.Money;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Mutable state for one quote as it flows through the rules. Not thread-safe by design: each quote
 * gets its own context, so parallel batch quoting never shares one.
 */
public final class PricingContext {

    private final QuoteRequest request;
    private final LocalDate pricingDate;
    private final List<PriceAdjustment> adjustments = new ArrayList<>();
    private BigDecimal unitPrice = Money.ZERO;
    private BigDecimal passThroughCharges = Money.ZERO;
    private boolean floorApplied;

    public PricingContext(QuoteRequest request, LocalDate pricingDate) {
        this.request = Objects.requireNonNull(request, "request");
        this.pricingDate = Objects.requireNonNull(pricingDate, "pricingDate");
    }

    /**
     * Adds {@code amount} (negative for a reduction) to the unit price and records a breakdown line.
     * Zero adjustments are ignored so the breakdown only shows rules that did something.
     */
    public void adjust(String rule, String description, BigDecimal amount) {
        BigDecimal rounded = Money.round(amount);
        if (rounded.signum() == 0) {
            return;
        }
        unitPrice = Money.round(unitPrice.add(rounded));
        adjustments.add(new PriceAdjustment(rule, description, rounded, unitPrice));
    }

    /** Records costs the seller only passes on (freight, marketplace fees); the floor guard adds them back. */
    public void addPassThroughCharge(BigDecimal amount) {
        passThroughCharges = Money.round(passThroughCharges.add(amount));
    }

    public void markFloorApplied() {
        this.floorApplied = true;
    }

    public QuoteRequest request() {
        return request;
    }

    public LocalDate pricingDate() {
        return pricingDate;
    }

    public BigDecimal unitPrice() {
        return unitPrice;
    }

    public BigDecimal passThroughCharges() {
        return passThroughCharges;
    }

    public boolean floorApplied() {
        return floorApplied;
    }

    public List<PriceAdjustment> adjustments() {
        return List.copyOf(adjustments);
    }
}
