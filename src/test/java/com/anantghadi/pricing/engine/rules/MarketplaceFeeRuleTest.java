package com.anantghadi.pricing.engine.rules;

import com.anantghadi.pricing.api.dto.PriceAdjustment;
import com.anantghadi.pricing.config.PricingProperties.MarketplaceFee;
import com.anantghadi.pricing.domain.Marketplace;
import com.anantghadi.pricing.domain.Money;
import com.anantghadi.pricing.domain.Zone;
import com.anantghadi.pricing.engine.PricingContext;
import com.anantghadi.pricing.error.PricingException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static com.anantghadi.pricing.support.TestFixtures.bd;
import static com.anantghadi.pricing.support.TestFixtures.contextAt;
import static com.anantghadi.pricing.support.TestFixtures.properties;
import static com.anantghadi.pricing.support.TestFixtures.request;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class MarketplaceFeeRuleTest {

    private final MarketplaceFeeRule rule = new MarketplaceFeeRule(properties());

    @ParameterizedTest(name = "{0}: net {1} -> commission {2}, selling {3}")
    @CsvSource({
            "AMAZON,    100, 21.18, 141.18",  // (100 + 20) / 0.85
            "FLIPKART,  100, 17.05, 142.05",  // (100 + 25) / 0.88
            "OWN_STORE, 100, 2.04,  102.04",  // 100 / 0.98, no closing fee
            "AMAZON,    500, 91.76, 611.76"
    })
    void grossesUpForCommissionAndClosingFee(Marketplace marketplace, String net, String commission, String selling) {
        PricingContext context = contextAt(request("SKU-1", "50", 1, "1", Zone.LOCAL, marketplace), net);

        rule.apply(context);

        assertThat(context.unitPrice()).isEqualByComparingTo(selling);
        assertThat(feeLines(context).get(0).amount()).isEqualByComparingTo(commission);
    }

    @ParameterizedTest(name = "{0} at net {1}: seller still nets the pre-fee price")
    @CsvSource({"AMAZON, 100", "AMAZON, 999.99", "FLIPKART, 333.33", "OWN_STORE, 12.34"})
    void sellerNetsThePreFeeAmount(Marketplace marketplace, String net) {
        MarketplaceFee fee = properties().marketplaces().get(marketplace);
        PricingContext context = contextAt(request("SKU-1", "50", 1, "1", Zone.LOCAL, marketplace), net);

        rule.apply(context);

        BigDecimal selling = context.unitPrice();
        BigDecimal sellerNet = selling.subtract(Money.percentOf(selling, fee.commissionPercent())).subtract(fee.closingFee());
        assertThat(sellerNet).isCloseTo(bd(net), within(bd("0.01")));
    }

    @Test
    void recordsFeesAsPassThroughCharges() {
        PricingContext context = contextAt(request("SKU-1", "50", 1, "1", Zone.LOCAL, Marketplace.AMAZON), "100");

        rule.apply(context);

        assertThat(context.passThroughCharges()).isEqualByComparingTo("41.18");
    }

    @Test
    void omitsTheClosingFeeLineWhenItIsZero() {
        PricingContext context = contextAt(request("SKU-1", "50", 1, "1", Zone.LOCAL, Marketplace.OWN_STORE), "100");

        rule.apply(context);

        assertThat(feeLines(context)).hasSize(1);
    }

    @Test
    void rejectsMarketplacesWithoutConfiguration() {
        MarketplaceFeeRule amazonOnly = new MarketplaceFeeRule(properties(
                Map.of(Marketplace.AMAZON, new MarketplaceFee(bd("15"), bd("20"))), properties().batch()));
        PricingContext context = contextAt(request("SKU-1", "50", 1, "1", Zone.LOCAL, Marketplace.FLIPKART), "100");

        assertThatThrownBy(() -> amazonOnly.apply(context))
                .isInstanceOf(PricingException.class)
                .hasMessageContaining("FLIPKART");
    }

    private static List<PriceAdjustment> feeLines(PricingContext context) {
        return context.adjustments().stream().filter(a -> a.rule().equals("MARKETPLACE_FEE")).toList();
    }
}
