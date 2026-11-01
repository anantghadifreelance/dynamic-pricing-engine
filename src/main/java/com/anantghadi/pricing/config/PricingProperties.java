package com.anantghadi.pricing.config;

import com.anantghadi.pricing.domain.Marketplace;
import com.anantghadi.pricing.domain.Zone;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Externalised pricing configuration bound from the {@code pricing.*} namespace.
 * Changing margins, GST, freight slabs or marketplace fees is a config change, not a code change.
 */
@Validated
@ConfigurationProperties(prefix = "pricing")
public record PricingProperties(
        @NotBlank String currency,
        @NotNull @DecimalMin("0") BigDecimal defaultMarginPercent,
        @NotNull @DecimalMin("0") BigDecimal minMarginPercent,
        @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal gstPercent,
        @NotNull List<@Valid Tier> tiers,
        @NotNull @Valid Freight freight,
        @NotEmpty Map<Marketplace, @Valid MarketplaceFee> marketplaces,
        @NotNull @Valid Batch batch) {

    /** Quantity break: orders of at least {@code minQuantity} units get {@code discountPercent} off. */
    public record Tier(@Min(1) int minQuantity,
                       @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal discountPercent) {
    }

    /** Per-unit freight: slab charge by weight, beyond the last slab {@code extraPerKg} per started kg. */
    public record Freight(@NotEmpty List<@Valid Slab> slabs,
                          @NotNull @DecimalMin("0") BigDecimal extraPerKg,
                          @NotEmpty Map<Zone, @NotNull BigDecimal> zoneMultipliers) {
    }

    public record Slab(@NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal maxWeightKg,
                       @NotNull @DecimalMin("0") BigDecimal charge) {
    }

    /** Marketplace commission (% of selling price) plus a fixed closing fee per unit. */
    public record MarketplaceFee(@NotNull @DecimalMin("0") @DecimalMax(value = "100", inclusive = false) BigDecimal commissionPercent,
                                 @NotNull @DecimalMin("0") BigDecimal closingFee) {
    }

    public record Batch(@Min(1) int threads,
                        @Min(1) int queueCapacity,
                        @Min(1) int maxItems,
                        @NotNull Duration timeout) {
    }
}
