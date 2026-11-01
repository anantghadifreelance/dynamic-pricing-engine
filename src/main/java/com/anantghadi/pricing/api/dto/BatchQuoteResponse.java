package com.anantghadi.pricing.api.dto;

import java.util.List;

/** Quotes in the same order as the request items. */
public record BatchQuoteResponse(int count, long elapsedMillis, List<QuoteResponse> quotes) {
}
