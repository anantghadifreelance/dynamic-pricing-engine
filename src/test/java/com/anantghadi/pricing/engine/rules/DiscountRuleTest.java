package com.anantghadi.pricing.engine.rules;

import com.anantghadi.pricing.api.dto.PriceAdjustment;
import com.anantghadi.pricing.api.dto.QuoteRequest;
import com.anantghadi.pricing.discount.Discount;
import com.anantghadi.pricing.discount.DiscountRepository;
import com.anantghadi.pricing.discount.DiscountType;
import com.anantghadi.pricing.domain.Marketplace;
import com.anantghadi.pricing.engine.PricingContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static com.anantghadi.pricing.support.TestFixtures.PRICING_DATE;
import static com.anantghadi.pricing.support.TestFixtures.bd;
import static com.anantghadi.pricing.support.TestFixtures.contextAt;
import static com.anantghadi.pricing.support.TestFixtures.request;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DiscountRuleTest {

    private DiscountRepository repository;
    private DiscountRule rule;
    private final QuoteRequest request = request("100");

    @BeforeEach
    void setUp() {
        repository = mock(DiscountRepository.class);
        rule = new DiscountRule(repository);
    }

    @ParameterizedTest(name = "{0} {1} on 200.00 -> {2}")
    @CsvSource({
            "PERCENTAGE, 10,   180.00",
            "PERCENTAGE, 12.5, 175.00",
            "PERCENTAGE, 100,  0.00",
            "FLAT,       15,   185.00",
            "FLAT,       199.99, 0.01",
            "FLAT,       500,  0.00"     // capped: a discount never makes the price negative
    })
    void appliesASingleDiscount(DiscountType type, String value, String expected) {
        givenDiscounts(discount("Promo", type, value));
        PricingContext context = contextAt(request, "200");

        rule.apply(context);

        assertThat(context.unitPrice()).isEqualByComparingTo(expected);
    }

    @Test
    void appliesPercentageDiscountsBeforeFlatOnes() {
        givenDiscounts(discount("Coupon", DiscountType.FLAT, "15"), discount("Festive", DiscountType.PERCENTAGE, "10"));
        PricingContext context = contextAt(request, "200");

        rule.apply(context);

        assertThat(context.adjustments().subList(1, context.adjustments().size()))
                .extracting(PriceAdjustment::description, PriceAdjustment::amount)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("Discount 'Festive' (10%)", bd("-20.00")),
                        org.assertj.core.groups.Tuple.tuple("Discount 'Coupon' (flat 15)", bd("-15.00")));
        assertThat(context.unitPrice()).isEqualByComparingTo("165.00");
    }

    @Test
    void leavesPriceUntouchedWhenNothingApplies() {
        givenDiscounts();
        PricingContext context = contextAt(request, "200");

        rule.apply(context);

        assertThat(context.unitPrice()).isEqualByComparingTo("200.00");
        assertThat(context.adjustments()).hasSize(1);
    }

    @Test
    void looksUpDiscountsForTheRequestScopeAndPricingDate() {
        givenDiscounts();

        rule.apply(contextAt(request, "200"));

        verify(repository).findApplicable("SKU-1", Marketplace.AMAZON, PRICING_DATE);
    }

    private void givenDiscounts(Discount... discounts) {
        when(repository.findApplicable(anyString(), any(), any())).thenReturn(List.of(discounts));
    }

    private static Discount discount(String name, DiscountType type, String value) {
        return new Discount(name, type, bd(value), null, null, null, null, true);
    }
}
