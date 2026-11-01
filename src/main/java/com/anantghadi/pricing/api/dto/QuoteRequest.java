package com.anantghadi.pricing.api.dto;

import com.anantghadi.pricing.domain.Marketplace;
import com.anantghadi.pricing.domain.Zone;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Input for a single price quote. Amounts are per unit; {@code marginPercent} is optional and
 * falls back to {@code pricing.default-margin-percent}.
 */
public record QuoteRequest(
        @NotBlank @Size(max = 64) String sku,
        @NotNull @DecimalMin("0.01") @Digits(integer = 12, fraction = 2) BigDecimal baseCost,
        @DecimalMin("0") @DecimalMax("500") BigDecimal marginPercent,
        @NotNull @Min(1) @Max(100_000) Integer quantity,
        @NotNull @DecimalMin("0.001") @DecimalMax("1000") BigDecimal weightKg,
        @NotNull Zone zone,
        @NotNull Marketplace marketplace) {
}
