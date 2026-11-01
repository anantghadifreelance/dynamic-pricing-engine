package com.anantghadi.pricing.engine.rules;

import com.anantghadi.pricing.config.PricingProperties;
import com.anantghadi.pricing.domain.Marketplace;
import com.anantghadi.pricing.domain.Money;
import com.anantghadi.pricing.engine.PricingContext;
import com.anantghadi.pricing.engine.PricingRule;
import com.anantghadi.pricing.engine.RuleOrder;
import com.anantghadi.pricing.error.PricingException;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Marketplace commission and closing fee. Commission is charged on the selling price, so the price is
 * grossed up: {@code selling = (net + closingFee) / (1 - commission%)}. That way the seller still
 * nets the pre-fee amount after the marketplace takes its cut.
 */
@Component
@Order(RuleOrder.MARKETPLACE_FEES)
public class MarketplaceFeeRule implements PricingRule {

    private final PricingProperties properties;

    public MarketplaceFeeRule(PricingProperties properties) {
        this.properties = properties;
    }

    @Override
    public String name() {
        return "MARKETPLACE_FEE";
    }

    @Override
    public void apply(PricingContext context) {
        Marketplace marketplace = context.request().marketplace();
        PricingProperties.MarketplaceFee fee = properties.marketplaces().get(marketplace);
        if (fee == null) {
            throw new PricingException("No fee configuration for marketplace " + marketplace);
        }

        BigDecimal net = context.unitPrice();
        BigDecimal closingFee = Money.round(fee.closingFee());
        BigDecimal selling = Money.divide(net.add(closingFee),
                BigDecimal.ONE.subtract(Money.fraction(fee.commissionPercent())));
        BigDecimal commission = selling.subtract(net).subtract(closingFee);

        context.adjust(name(), marketplace + " commission "
                + fee.commissionPercent().stripTrailingZeros().toPlainString() + "%", commission);
        context.adjust(name(), marketplace + " closing fee", closingFee);
        context.addPassThroughCharge(commission.add(closingFee));
    }
}
