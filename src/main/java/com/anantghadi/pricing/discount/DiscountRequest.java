package com.anantghadi.pricing.discount;

import com.anantghadi.pricing.domain.Marketplace;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Create/replace payload for a discount. {@code active} defaults to true. */
public record DiscountRequest(
        @NotBlank @Size(max = 100) String name,
        @NotNull DiscountType type,
        @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 12, fraction = 2) BigDecimal value,
        @Size(max = 64) String sku,
        Marketplace marketplace,
        LocalDate validFrom,
        LocalDate validTo,
        Boolean active) {

    @JsonIgnore
    @AssertTrue(message = "validTo must not be before validFrom")
    public boolean isValidWindow() {
        return validFrom == null || validTo == null || !validTo.isBefore(validFrom);
    }

    @JsonIgnore
    @AssertTrue(message = "percentage discount must not exceed 100")
    public boolean isValidPercentage() {
        return type != DiscountType.PERCENTAGE || value == null || value.compareTo(BigDecimal.valueOf(100)) <= 0;
    }

    public boolean activeOrDefault() {
        return active == null || active;
    }
}
