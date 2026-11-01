package com.anantghadi.pricing.batch;

import com.anantghadi.pricing.api.dto.QuoteRequest;
import com.anantghadi.pricing.api.dto.QuoteResponse;
import com.anantghadi.pricing.config.PricingProperties;
import com.anantghadi.pricing.engine.PricingService;
import com.anantghadi.pricing.error.BatchTimeoutException;
import com.anantghadi.pricing.error.PricingException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Fans a batch out over the bounded pricing pool with {@link CompletableFuture}s and gathers the
 * results in request order. All items share one pricing date so a batch that straddles midnight is
 * still internally consistent. Each item goes through the cached {@link PricingService} proxy, so
 * repeated SKUs within or across batches hit the cache.
 */
@Service
public class BatchQuoteService {

    private final PricingService pricingService;
    private final ExecutorService executor;
    private final Clock clock;
    private final int maxItems;
    private final Duration timeout;

    public BatchQuoteService(PricingService pricingService, ExecutorService pricingExecutor, Clock clock,
                             PricingProperties properties) {
        this.pricingService = pricingService;
        this.executor = pricingExecutor;
        this.clock = clock;
        this.maxItems = properties.batch().maxItems();
        this.timeout = properties.batch().timeout();
    }

    public List<QuoteResponse> quoteAll(List<QuoteRequest> requests) {
        if (requests.size() > maxItems) {
            throw new PricingException("Batch size " + requests.size() + " exceeds the limit of " + maxItems);
        }
        LocalDate pricingDate = LocalDate.now(clock);

        List<CompletableFuture<QuoteResponse>> futures = requests.stream()
                .map(request -> CompletableFuture.supplyAsync(() -> pricingService.quote(request, pricingDate), executor))
                .toList();

        try {
            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                    .get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            futures.forEach(future -> future.cancel(true));
            throw new BatchTimeoutException("Batch of " + requests.size() + " quotes did not finish within " + timeout);
        } catch (ExecutionException e) {
            throw unwrap(e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BatchTimeoutException("Batch quoting was interrupted");
        }
        return futures.stream().map(CompletableFuture::join).toList();
    }

    private static RuntimeException unwrap(Throwable throwable) {
        Throwable cause = throwable;
        while (cause instanceof CompletionException && cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause instanceof RuntimeException runtime ? runtime : new IllegalStateException(cause);
    }
}
