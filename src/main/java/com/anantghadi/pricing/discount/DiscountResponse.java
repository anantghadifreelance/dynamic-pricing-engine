package com.anantghadi.pricing.discount;

import com.anantghadi.pricing.domain.Marketplace;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DiscountResponse(Long id, String name, DiscountType type, BigDecimal value, String sku,
                               Marketplace marketplace, LocalDate validFrom, LocalDate validTo, boolean active) {

    static DiscountResponse from(Discount d) {
        return new DiscountResponse(d.getId(), d.getName(), d.getType(), d.getValue(), d.getSku(),
                d.getMarketplace(), d.getValidFrom(), d.getValidTo(), d.isActive());
    }
}
