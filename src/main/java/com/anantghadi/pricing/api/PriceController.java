package com.anantghadi.pricing.api;

import com.anantghadi.pricing.api.dto.BatchQuoteRequest;
import com.anantghadi.pricing.api.dto.BatchQuoteResponse;
import com.anantghadi.pricing.api.dto.QuoteRequest;
import com.anantghadi.pricing.api.dto.QuoteResponse;
import com.anantghadi.pricing.batch.BatchQuoteService;
import com.anantghadi.pricing.engine.PricingService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/prices")
public class PriceController {

    private final PricingService pricingService;
    private final BatchQuoteService batchQuoteService;
    private final Clock clock;

    public PriceController(PricingService pricingService, BatchQuoteService batchQuoteService, Clock clock) {
        this.pricingService = pricingService;
        this.batchQuoteService = batchQuoteService;
        this.clock = clock;
    }

    @PostMapping("/quote")
    public QuoteResponse quote(@Valid @RequestBody QuoteRequest request) {
        return pricingService.quote(request, LocalDate.now(clock));
    }

    @PostMapping("/quote/batch")
    public BatchQuoteResponse batch(@Valid @RequestBody BatchQuoteRequest request) {
        long started = System.nanoTime();
        List<QuoteResponse> quotes = batchQuoteService.quoteAll(request.items());
        long elapsedMillis = (System.nanoTime() - started) / 1_000_000;
        return new BatchQuoteResponse(quotes.size(), elapsedMillis, quotes);
    }
}
