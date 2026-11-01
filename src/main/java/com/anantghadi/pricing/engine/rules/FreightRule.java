package com.anantghadi.pricing.engine.rules;

import com.anantghadi.pricing.config.PricingProperties;
import com.anantghadi.pricing.domain.Money;
import com.anantghadi.pricing.domain.Zone;
import com.anantghadi.pricing.engine.PricingContext;
import com.anantghadi.pricing.engine.PricingRule;
import com.anantghadi.pricing.engine.RuleOrder;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;

/**
 * Per-unit freight. The unit weight picks the first slab whose upper bound covers it; heavier
 * parcels pay the last slab plus {@code extraPerKg} for every started kilogram above it. The result
 * is scaled by the zone multiplier.
 */
@Component
@Order(RuleOrder.FREIGHT)
public class FreightRule implements PricingRule {

    private final PricingProperties.Freight freight;
    private final List<PricingProperties.Slab> slabs;

    public FreightRule(PricingProperties properties) {
        this.freight = properties.freight();
        this.slabs = properties.freight().slabs().stream()
                .sorted(Comparator.comparing(PricingProperties.Slab::maxWeightKg))
                .toList();
    }

    @Override
    public String name() {
        return "FREIGHT";
    }

    @Override
    public void apply(PricingContext context) {
        BigDecimal weight = context.request().weightKg();
        Zone zone = context.request().zone();
        BigDecimal charge = freightFor(weight, zone);
        context.adjust(name(), "Freight " + weight.stripTrailingZeros().toPlainString() + " kg, " + zone, charge);
        context.addPassThroughCharge(charge);
    }

    BigDecimal freightFor(BigDecimal weightKg, Zone zone) {
        BigDecimal slabCharge = slabs.stream()
                .filter(slab -> weightKg.compareTo(slab.maxWeightKg()) <= 0)
                .findFirst()
                .map(PricingProperties.Slab::charge)
                .orElseGet(() -> overweightCharge(weightKg));
        BigDecimal multiplier = freight.zoneMultipliers().getOrDefault(zone, BigDecimal.ONE);
        return Money.round(slabCharge.multiply(multiplier));
    }

    private BigDecimal overweightCharge(BigDecimal weightKg) {
        PricingProperties.Slab last = slabs.get(slabs.size() - 1);
        BigDecimal extraKg = weightKg.subtract(last.maxWeightKg()).setScale(0, RoundingMode.CEILING);
        return last.charge().add(extraKg.multiply(freight.extraPerKg()));
    }
}
