package com.anantghadi.pricing.api.dto;

import com.anantghadi.pricing.domain.Marketplace;
import com.anantghadi.pricing.domain.Zone;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Full quote: every rule's adjustment plus the final per-unit and order totals (GST inclusive). */
public record QuoteResponse(
        String sku,
        Marketplace marketplace,
        Zone zone,
        int quantity,
        String currency,
        LocalDate pricingDate,
        List<PriceAdjustment> breakdown,
        boolean floorApplied,
        BigDecimal unitPrice,
        BigDecimal totalPrice) {
}
