package com.anantghadi.pricing.engine.rules;

import com.anantghadi.pricing.domain.Marketplace;
import com.anantghadi.pricing.domain.Zone;
import com.anantghadi.pricing.engine.PricingContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static com.anantghadi.pricing.support.TestFixtures.contextAt;
import static com.anantghadi.pricing.support.TestFixtures.properties;
import static com.anantghadi.pricing.support.TestFixtures.request;
import static org.assertj.core.api.Assertions.assertThat;

class FreightRuleTest {

    private final FreightRule rule = new FreightRule(properties());

    @ParameterizedTest(name = "{0} kg {1} -> freight {2}")
    @CsvSource({
            "0.2,  LOCAL,    40.00",
            "0.5,  LOCAL,    40.00",   // slab upper bound is inclusive
            "0.51, LOCAL,    65.00",
            "1,    REGIONAL, 84.50",   // 65 x 1.3
            "2,    NATIONAL, 144.00",  // 90 x 1.6
            "5,    LOCAL,    150.00",
            "5.2,  LOCAL,    175.00",  // 150 + 1 started extra kg x 25
            "7,    NATIONAL, 320.00",  // (150 + 2 x 25) x 1.6
            "7.5,  REGIONAL, 292.50"   // (150 + 3 x 25) x 1.3
    })
    void chargesBySlabAndZone(String weight, Zone zone, String expectedFreight) {
        PricingContext context = contextAt(request("SKU-1", "100", 1, weight, zone, Marketplace.OWN_STORE), "100");

        rule.apply(context);

        assertThat(context.unitPrice().subtract(new java.math.BigDecimal("100")))
                .isEqualByComparingTo(expectedFreight);
        assertThat(context.passThroughCharges()).isEqualByComparingTo(expectedFreight);
    }
}
