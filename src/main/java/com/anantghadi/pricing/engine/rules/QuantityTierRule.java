package com.anantghadi.pricing.engine.rules;

import com.anantghadi.pricing.config.PricingProperties;
import com.anantghadi.pricing.domain.Money;
import com.anantghadi.pricing.engine.PricingContext;
import com.anantghadi.pricing.engine.PricingRule;
import com.anantghadi.pricing.engine.RuleOrder;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Comparator;

/** Volume discount: the highest tier whose minimum quantity the order meets wins (tiers do not stack). */
@Component
@Order(RuleOrder.QUANTITY_TIERS)
public class QuantityTierRule implements PricingRule {

    private final PricingProperties properties;

    public QuantityTierRule(PricingProperties properties) {
        this.properties = properties;
    }

    @Override
    public String name() {
        return "QUANTITY_TIER";
    }

    @Override
    public void apply(PricingContext context) {
        int quantity = context.request().quantity();
        properties.tiers().stream()
                .filter(tier -> quantity >= tier.minQuantity())
                .max(Comparator.comparingInt(PricingProperties.Tier::minQuantity))
                .ifPresent(tier -> context.adjust(name(),
                        "Tier " + tier.minQuantity() + "+ units ("
                                + tier.discountPercent().stripTrailingZeros().toPlainString() + "%)",
                        Money.percentOf(context.unitPrice(), tier.discountPercent()).negate()));
    }
}
