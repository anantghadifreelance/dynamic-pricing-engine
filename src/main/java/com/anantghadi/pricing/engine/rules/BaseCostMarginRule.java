package com.anantghadi.pricing.engine.rules;

import com.anantghadi.pricing.config.PricingProperties;
import com.anantghadi.pricing.domain.Money;
import com.anantghadi.pricing.engine.PricingContext;
import com.anantghadi.pricing.engine.PricingRule;
import com.anantghadi.pricing.engine.RuleOrder;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/** Starts the price at landed cost and adds the requested (or default) margin. */
@Component
@Order(RuleOrder.BASE_COST_AND_MARGIN)
public class BaseCostMarginRule implements PricingRule {

    private final PricingProperties properties;

    public BaseCostMarginRule(PricingProperties properties) {
        this.properties = properties;
    }

    @Override
    public String name() {
        return "BASE_COST_MARGIN";
    }

    @Override
    public void apply(PricingContext context) {
        BigDecimal cost = context.request().baseCost();
        BigDecimal marginPercent = context.request().marginPercent() != null
                ? context.request().marginPercent()
                : properties.defaultMarginPercent();

        context.adjust(name(), "Base cost", cost);
        context.adjust(name(), "Margin " + marginPercent.stripTrailingZeros().toPlainString() + "%",
                Money.percentOf(cost, marginPercent));
    }
}
