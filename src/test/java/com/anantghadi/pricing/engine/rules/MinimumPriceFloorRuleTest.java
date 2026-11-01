package com.anantghadi.pricing.engine.rules;

import com.anantghadi.pricing.engine.PricingContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static com.anantghadi.pricing.support.TestFixtures.bd;
import static com.anantghadi.pricing.support.TestFixtures.contextAt;
import static com.anantghadi.pricing.support.TestFixtures.properties;
import static com.anantghadi.pricing.support.TestFixtures.request;
import static org.assertj.core.api.Assertions.assertThat;

/** Cost is 100 and the minimum margin 8%, so the floor is 108 plus any pass-through charges. */
class MinimumPriceFloorRuleTest {

    private final MinimumPriceFloorRule rule = new MinimumPriceFloorRule(properties());

    @ParameterizedTest(name = "price {0} with pass-through {1} -> {2} (floor applied: {3})")
    @CsvSource({
            "150,    0,  150.00, false",
            "108,    0,  108.00, false",   // exactly at the floor is allowed
            "107.99, 0,  108.00, true",
            "50,     0,  108.00, true",
            "0.01,   0,  108.00, true",
            "150,    60, 168.00, true",    // freight and fees are added on top of cost + margin
            "200,    60, 200.00, false"
    })
    void neverLetsThePriceDropBelowCostPlusMinimumMargin(String price, String passThrough, String expected,
                                                         boolean floorApplied) {
        PricingContext context = contextAt(request("100"), price);
        context.addPassThroughCharge(bd(passThrough));

        rule.apply(context);

        assertThat(context.unitPrice()).isEqualByComparingTo(expected);
        assertThat(context.floorApplied()).isEqualTo(floorApplied);
    }

    @Test
    void recordsTheTopUpInTheBreakdown() {
        PricingContext context = contextAt(request("100"), "90");

        rule.apply(context);

        assertThat(context.adjustments().get(1).rule()).isEqualTo("PRICE_FLOOR");
        assertThat(context.adjustments().get(1).amount()).isEqualByComparingTo("18.00");
    }
}
