package com.anantghadi.pricing.engine.rules;

import com.anantghadi.pricing.api.dto.QuoteRequest;
import com.anantghadi.pricing.domain.Marketplace;
import com.anantghadi.pricing.domain.Zone;
import com.anantghadi.pricing.engine.PricingContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static com.anantghadi.pricing.support.TestFixtures.PRICING_DATE;
import static com.anantghadi.pricing.support.TestFixtures.bd;
import static com.anantghadi.pricing.support.TestFixtures.properties;
import static org.assertj.core.api.Assertions.assertThat;

class BaseCostMarginRuleTest {

    private final BaseCostMarginRule rule = new BaseCostMarginRule(properties());

    @ParameterizedTest(name = "cost {0} with margin {1}% -> {2}")
    @CsvSource({
            "100,    ,   125.00",   // falls back to the default 25%
            "100,    10, 110.00",
            "99.99,  25, 124.99",   // 24.9975 margin rounds to 25.00
            "250.50, 0,  250.50",
            "80,     150, 200.00"
    })
    void addsMarginToBaseCost(String cost, String margin, String expected) {
        PricingContext context = contextFor(cost, margin);

        rule.apply(context);

        assertThat(context.unitPrice()).isEqualByComparingTo(expected);
    }

    @Test
    void recordsCostAndMarginAsSeparateBreakdownLines() {
        PricingContext context = contextFor("100", "20");

        rule.apply(context);

        assertThat(context.adjustments())
                .extracting(a -> a.description(), a -> a.amount())
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("Base cost", bd("100.00")),
                        org.assertj.core.groups.Tuple.tuple("Margin 20%", bd("20.00")));
    }

    @Test
    void zeroMarginProducesOnlyTheCostLine() {
        PricingContext context = contextFor("100", "0");

        rule.apply(context);

        assertThat(context.adjustments()).hasSize(1);
    }

    private static PricingContext contextFor(String cost, String margin) {
        BigDecimal marginPercent = margin == null ? null : bd(margin);
        QuoteRequest request = new QuoteRequest("SKU-1", bd(cost), marginPercent, 1, bd("1"),
                Zone.LOCAL, Marketplace.OWN_STORE);
        return new PricingContext(request, PRICING_DATE);
    }
}
