package com.anantghadi.pricing.engine.rules;

import com.anantghadi.pricing.config.PricingProperties;
import com.anantghadi.pricing.domain.Money;
import com.anantghadi.pricing.engine.PricingContext;
import com.anantghadi.pricing.engine.PricingRule;
import com.anantghadi.pricing.engine.RuleOrder;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Adds GST on the final pre-tax price. Always last so tax is computed on what the customer pays. */
@Component
@Order(RuleOrder.GST)
public class GstRule implements PricingRule {

    private final PricingProperties properties;

    public GstRule(PricingProperties properties) {
        this.properties = properties;
    }

    @Override
    public String name() {
        return "GST";
    }

    @Override
    public void apply(PricingContext context) {
        context.adjust(name(), "GST " + properties.gstPercent().stripTrailingZeros().toPlainString() + "%",
                Money.percentOf(context.unitPrice(), properties.gstPercent()));
    }
}
