package com.anantghadi.pricing.batch;

import com.anantghadi.pricing.api.dto.QuoteRequest;
import com.anantghadi.pricing.api.dto.QuoteResponse;
import com.anantghadi.pricing.config.PricingProperties;
import com.anantghadi.pricing.config.PricingProperties.Batch;
import com.anantghadi.pricing.domain.Marketplace;
import com.anantghadi.pricing.domain.Zone;
import com.anantghadi.pricing.engine.PricingService;
import com.anantghadi.pricing.error.BatchTimeoutException;
import com.anantghadi.pricing.error.PricingException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.IntStream;

import static com.anantghadi.pricing.support.TestFixtures.PRICING_DATE;
import static com.anantghadi.pricing.support.TestFixtures.properties;
import static com.anantghadi.pricing.support.TestFixtures.request;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BatchQuoteServiceTest {

    private final Clock clock = Clock.fixed(PRICING_DATE.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
    private final Set<String> workerThreads = ConcurrentHashMap.newKeySet();
    private final Set<LocalDate> pricingDates = ConcurrentHashMap.newKeySet();
    private PricingService pricingService;
    private ExecutorService executor;

    @BeforeEach
    void setUp() {
        pricingService = mock(PricingService.class);
        executor = Executors.newFixedThreadPool(4);
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    void quotesInParallelAndPreservesRequestOrder() {
        when(pricingService.quote(any(), any())).thenAnswer(invocation -> {
            sleep(ThreadLocalRandom.current().nextInt(1, 15));
            workerThreads.add(Thread.currentThread().getName());
            pricingDates.add(invocation.getArgument(1));
            return quoteFor(invocation.getArgument(0));
        });
        List<QuoteRequest> requests = IntStream.range(0, 40)
                .mapToObj(i -> request("SKU-" + i, "100", 1, "1", Zone.LOCAL, Marketplace.AMAZON))
                .toList();

        List<QuoteResponse> quotes = service(Duration.ofSeconds(5)).quoteAll(requests);

        assertThat(quotes).extracting(QuoteResponse::sku)
                .containsExactlyElementsOf(requests.stream().map(QuoteRequest::sku).toList());
        assertThat(workerThreads).hasSizeGreaterThan(1);
        assertThat(pricingDates).containsExactly(PRICING_DATE);
    }

    @Test
    void propagatesTheFirstPricingFailure() {
        QuoteRequest bad = request("BAD", "100", 1, "1", Zone.LOCAL, Marketplace.FLIPKART);
        when(pricingService.quote(any(), any())).thenAnswer(invocation -> quoteFor(invocation.getArgument(0)));
        doThrow(new PricingException("no config for FLIPKART")).when(pricingService).quote(eq(bad), any());

        assertThatThrownBy(() -> service(Duration.ofSeconds(5)).quoteAll(List.of(request("100"), bad)))
                .isInstanceOf(PricingException.class)
                .hasMessageContaining("FLIPKART");
    }

    @Test
    void failsWithTimeoutWhenTheBatchIsTooSlow() {
        when(pricingService.quote(any(), any())).thenAnswer(invocation -> {
            sleep(2_000);
            return quoteFor(invocation.getArgument(0));
        });

        assertThatThrownBy(() -> service(Duration.ofMillis(100)).quoteAll(List.of(request("100"))))
                .isInstanceOf(BatchTimeoutException.class);
    }

    @Test
    void rejectsBatchesAboveTheConfiguredLimit() {
        List<QuoteRequest> tooMany = IntStream.range(0, 101).mapToObj(i -> request("100")).toList();

        assertThatThrownBy(() -> service(Duration.ofSeconds(5)).quoteAll(tooMany))
                .isInstanceOf(PricingException.class)
                .hasMessageContaining("exceeds the limit of 100");
    }

    private BatchQuoteService service(Duration timeout) {
        PricingProperties base = properties();
        PricingProperties properties = properties(base.marketplaces(), new Batch(4, 50, 100, timeout));
        return new BatchQuoteService(pricingService, executor, clock, properties);
    }

    private static QuoteResponse quoteFor(QuoteRequest request) {
        return new QuoteResponse(request.sku(), request.marketplace(), request.zone(), request.quantity(), "INR",
                PRICING_DATE, List.of(), false, BigDecimal.TEN, BigDecimal.TEN);
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
