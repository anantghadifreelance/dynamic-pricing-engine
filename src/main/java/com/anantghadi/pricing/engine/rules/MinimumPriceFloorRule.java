package com.anantghadi.pricing.engine.rules;

import com.anantghadi.pricing.config.PricingProperties;
import com.anantghadi.pricing.domain.Money;
import com.anantghadi.pricing.engine.PricingContext;
import com.anantghadi.pricing.engine.PricingRule;
import com.anantghadi.pricing.engine.RuleOrder;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Minimum selling price (MSP) guard. However discounts stack up, the pre-tax price may not drop below
 * {@code cost + minMargin% of cost + pass-through charges (freight, marketplace fees)}, i.e. the seller
 * always keeps at least the minimum margin on the product.
 */
@Component
@Order(RuleOrder.PRICE_FLOOR)
public class MinimumPriceFloorRule implements PricingRule {

    private final PricingProperties properties;

    public MinimumPriceFloorRule(PricingProperties properties) {
        this.properties = properties;
    }

    @Override
    public String name() {
        return "PRICE_FLOOR";
    }

    @Override
    public void apply(PricingContext context) {
        BigDecimal floor = floorFor(context);
        if (context.unitPrice().compareTo(floor) < 0) {
            context.adjust(name(), "Raised to minimum selling price " + floor.toPlainString(),
                    floor.subtract(context.unitPrice()));
            context.markFloorApplied();
        }
    }

    BigDecimal floorFor(PricingContext context) {
        BigDecimal cost = Money.round(context.request().baseCost());
        return cost
                .add(Money.percentOf(cost, properties.minMarginPercent()))
                .add(context.passThroughCharges());
    }
}
