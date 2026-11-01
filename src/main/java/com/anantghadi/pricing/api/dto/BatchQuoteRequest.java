package com.anantghadi.pricing.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/** A list of quotes to compute in parallel. The upper bound is {@code pricing.batch.max-items}. */
public record BatchQuoteRequest(@NotEmpty List<@Valid QuoteRequest> items) {
}
