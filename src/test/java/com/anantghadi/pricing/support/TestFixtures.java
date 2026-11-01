package com.anantghadi.pricing.support;

import com.anantghadi.pricing.api.dto.QuoteRequest;
import com.anantghadi.pricing.config.PricingProperties;
import com.anantghadi.pricing.config.PricingProperties.Batch;
import com.anantghadi.pricing.config.PricingProperties.Freight;
import com.anantghadi.pricing.config.PricingProperties.MarketplaceFee;
import com.anantghadi.pricing.config.PricingProperties.Slab;
import com.anantghadi.pricing.config.PricingProperties.Tier;
import com.anantghadi.pricing.domain.Marketplace;
import com.anantghadi.pricing.domain.Zone;
import com.anantghadi.pricing.engine.PricingContext;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Same values as src/main/resources/application.yml, built by hand so unit tests need no Spring context. */
public final class TestFixtures {

    public static final LocalDate PRICING_DATE = LocalDate.of(2026, 11, 1);

    private TestFixtures() {
    }

    public static PricingProperties properties() {
        return properties(Map.of(
                Marketplace.AMAZON, new MarketplaceFee(bd("15"), bd("20")),
                Marketplace.FLIPKART, new MarketplaceFee(bd("12"), bd("25")),
                Marketplace.OWN_STORE, new MarketplaceFee(bd("2"), bd("0"))),
                new Batch(4, 50, 100, Duration.ofSeconds(5)));
    }

    public static PricingProperties properties(Map<Marketplace, MarketplaceFee> marketplaces, Batch batch) {
        return new PricingProperties(
                "INR",
                bd("25"),
                bd("8"),
                bd("18"),
                List.of(new Tier(10, bd("2")), new Tier(50, bd("5")), new Tier(100, bd("8"))),
                new Freight(
                        List.of(new Slab(bd("0.5"), bd("40")), new Slab(bd("1"), bd("65")),
                                new Slab(bd("2"), bd("90")), new Slab(bd("5"), bd("150"))),
                        bd("25"),
                        Map.of(Zone.LOCAL, bd("1.0"), Zone.REGIONAL, bd("1.3"), Zone.NATIONAL, bd("1.6"))),
                marketplaces,
                batch);
    }

    public static QuoteRequest request(String baseCost) {
        return new QuoteRequest("SKU-1", bd(baseCost), null, 1, bd("0.5"), Zone.LOCAL, Marketplace.AMAZON);
    }

    public static QuoteRequest request(String sku, String baseCost, int quantity, String weightKg, Zone zone,
                                       Marketplace marketplace) {
        return new QuoteRequest(sku, bd(baseCost), null, quantity, bd(weightKg), zone, marketplace);
    }

    /** A context whose running unit price has already been set to {@code startingPrice}. */
    public static PricingContext contextAt(QuoteRequest request, String startingPrice) {
        PricingContext context = new PricingContext(request, PRICING_DATE);
        context.adjust("SEED", "starting price", bd(startingPrice));
        return context;
    }

    public static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}
