package com.anantghadi.pricing.engine.rules;

import com.anantghadi.pricing.engine.PricingContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static com.anantghadi.pricing.support.TestFixtures.contextAt;
import static com.anantghadi.pricing.support.TestFixtures.properties;
import static com.anantghadi.pricing.support.TestFixtures.request;
import static org.assertj.core.api.Assertions.assertThat;

class GstRuleTest {

    private final GstRule rule = new GstRule(properties());

    @ParameterizedTest(name = "{0} + 18% GST = {1}")
    @CsvSource({
            "100,     118.00",
            "99.99,   117.99",   // 17.9982 -> 18.00
            "1234.56, 1456.78",  // 222.2208 -> 222.22
            "0.01,    0.01"      // GST rounds to zero and adds no line
    })
    void addsGstOnThePreTaxPrice(String price, String expected) {
        PricingContext context = contextAt(request("100"), price);

        rule.apply(context);

        assertThat(context.unitPrice()).isEqualByComparingTo(expected);
    }
}
