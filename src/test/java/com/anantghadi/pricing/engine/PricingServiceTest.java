package com.anantghadi.pricing.engine;

import com.anantghadi.pricing.api.dto.PriceAdjustment;
import com.anantghadi.pricing.api.dto.QuoteResponse;
import com.anantghadi.pricing.config.PricingProperties;
import com.anantghadi.pricing.discount.Discount;
import com.anantghadi.pricing.discount.DiscountRepository;
import com.anantghadi.pricing.discount.DiscountType;
import com.anantghadi.pricing.domain.Marketplace;
import com.anantghadi.pricing.domain.Zone;
import com.anantghadi.pricing.engine.rules.BaseCostMarginRule;
import com.anantghadi.pricing.engine.rules.DiscountRule;
import com.anantghadi.pricing.engine.rules.FreightRule;
import com.anantghadi.pricing.engine.rules.GstRule;
import com.anantghadi.pricing.engine.rules.MarketplaceFeeRule;
import com.anantghadi.pricing.engine.rules.MinimumPriceFloorRule;
import com.anantghadi.pricing.engine.rules.QuantityTierRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.anantghadi.pricing.support.TestFixtures.PRICING_DATE;
import static com.anantghadi.pricing.support.TestFixtures.bd;
import static com.anantghadi.pricing.support.TestFixtures.properties;
import static com.anantghadi.pricing.support.TestFixtures.request;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** End-to-end run of the real rule chain (no Spring) with discounts mocked. */
class PricingServiceTest {

    private DiscountRepository repository;
    private PricingService service;

    @BeforeEach
    void setUp() {
        PricingProperties properties = properties();
        repository = mock(DiscountRepository.class);
        when(repository.findApplicable(anyString(), any(), any())).thenReturn(List.of());
        service = new PricingService(List.of(
                new BaseCostMarginRule(properties),
                new DiscountRule(repository),
                new QuantityTierRule(properties),
                new FreightRule(properties),
                new MarketplaceFeeRule(properties),
                new MinimumPriceFloorRule(properties),
                new GstRule(properties)), properties);
    }

    @Test
    void producesAFullBreakdownForAnAmazonQuote() {
        QuoteResponse quote = service.quote(request("100"), PRICING_DATE);

        assertThat(quote.breakdown())
                .extracting(PriceAdjustment::rule, PriceAdjustment::amount, PriceAdjustment::runningUnitPrice)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("BASE_COST_MARGIN", bd("100.00"), bd("100.00")),
                        org.assertj.core.groups.Tuple.tuple("BASE_COST_MARGIN", bd("25.00"), bd("125.00")),
                        org.assertj.core.groups.Tuple.tuple("FREIGHT", bd("40.00"), bd("165.00")),
                        org.assertj.core.groups.Tuple.tuple("MARKETPLACE_FEE", bd("32.65"), bd("197.65")),
                        org.assertj.core.groups.Tuple.tuple("MARKETPLACE_FEE", bd("20.00"), bd("217.65")),
                        org.assertj.core.groups.Tuple.tuple("GST", bd("39.18"), bd("256.83")));
        assertThat(quote.unitPrice()).isEqualByComparingTo("256.83");
        assertThat(quote.totalPrice()).isEqualByComparingTo("256.83");
        assertThat(quote.floorApplied()).isFalse();
        assertThat(quote.currency()).isEqualTo("INR");
        assertThat(quote.pricingDate()).isEqualTo(PRICING_DATE);
    }

    @Test
    void deepDiscountIsCaughtByTheFloorGuard() {
        when(repository.findApplicable(anyString(), any(), any())).thenReturn(List.of(
                new Discount("Clearance", DiscountType.PERCENTAGE, bd("60"), null, null, null, null, true)));

        QuoteResponse quote = service.quote(request("100"), PRICING_DATE);

        assertThat(quote.floorApplied()).isTrue();
        assertThat(quote.breakdown()).extracting(PriceAdjustment::rule).contains("DISCOUNT", "PRICE_FLOOR");
        // floor = 100 + 8% + freight 40 + fees 39.41 = 187.41; plus 18% GST = 221.14
        assertThat(quote.unitPrice()).isEqualByComparingTo("221.14");
    }

    @Test
    void totalIsUnitPriceTimesQuantityAfterTierDiscount() {
        QuoteResponse quote = service.quote(
                request("SKU-9", "100", 10, "0.5", Zone.LOCAL, Marketplace.OWN_STORE), PRICING_DATE);

        // 125 -2% = 122.50, +40 freight = 162.50, /0.98 = 165.82, +18% GST = 195.67
        assertThat(quote.unitPrice()).isEqualByComparingTo("195.67");
        assertThat(quote.totalPrice()).isEqualByComparingTo("1956.70");
        assertThat(quote.unitPrice().scale()).isEqualTo(2);
        assertThat(quote.totalPrice().scale()).isEqualTo(2);
    }

    @Test
    void exposesRuleNamesInExecutionOrder() {
        assertThat(service.ruleNames()).containsExactly(
                "BASE_COST_MARGIN", "DISCOUNT", "QUANTITY_TIER", "FREIGHT", "MARKETPLACE_FEE", "PRICE_FLOOR", "GST");
    }
}
