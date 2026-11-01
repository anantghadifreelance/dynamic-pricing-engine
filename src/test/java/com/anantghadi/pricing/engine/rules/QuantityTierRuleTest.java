package com.anantghadi.pricing.engine.rules;

import com.anantghadi.pricing.domain.Marketplace;
import com.anantghadi.pricing.domain.Zone;
import com.anantghadi.pricing.engine.PricingContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static com.anantghadi.pricing.support.TestFixtures.contextAt;
import static com.anantghadi.pricing.support.TestFixtures.properties;
import static com.anantghadi.pricing.support.TestFixtures.request;
import static org.assertj.core.api.Assertions.assertThat;

class QuantityTierRuleTest {

    private final QuantityTierRule rule = new QuantityTierRule(properties());

    @ParameterizedTest(name = "{0} units -> {1}")
    @CsvSource({
            "1,    100.00",
            "9,    100.00",
            "10,   98.00",   // 2% tier starts exactly at the minimum
            "49,   98.00",
            "50,   95.00",
            "99,   95.00",
            "100,  92.00",
            "5000, 92.00"    // highest tier wins, tiers do not stack
    })
    void appliesTheBestMatchingTier(int quantity, String expected) {
        PricingContext context = contextAt(
                request("SKU-1", "80", quantity, "1", Zone.LOCAL, Marketplace.OWN_STORE), "100");

        rule.apply(context);

        assertThat(context.unitPrice()).isEqualByComparingTo(expected);
    }

    @Test
    void describesTheTierInTheBreakdown() {
        PricingContext context = contextAt(
                request("SKU-1", "80", 60, "1", Zone.LOCAL, Marketplace.OWN_STORE), "100");

        rule.apply(context);

        assertThat(context.adjustments().get(1).description()).isEqualTo("Tier 50+ units (5%)");
    }
}
