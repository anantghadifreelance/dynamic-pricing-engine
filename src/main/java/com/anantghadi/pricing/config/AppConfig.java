package com.anantghadi.pricing.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Configuration
@EnableCaching
public class AppConfig {

    public static final String QUOTES_CACHE = "quotes";

    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }

    /**
     * Bounded pool for batch quoting. A fixed number of threads and a bounded queue keep a large
     * batch from exhausting the JVM; when the queue is full the caller thread does the work itself
     * (back-pressure) instead of rejecting the request.
     */
    @Bean(destroyMethod = "shutdown")
    public ExecutorService pricingExecutor(PricingProperties properties) {
        PricingProperties.Batch batch = properties.batch();
        return new ThreadPoolExecutor(
                batch.threads(), batch.threads(),
                0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(batch.queueCapacity()),
                namedThreads("pricing-worker-"),
                new ThreadPoolExecutor.CallerRunsPolicy());
    }

    private static ThreadFactory namedThreads(String prefix) {
        AtomicInteger counter = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, prefix + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }
}
