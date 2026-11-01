package com.anantghadi.pricing.error;

/** The batch did not finish within {@code pricing.batch.timeout} (mapped to 503). */
public class BatchTimeoutException extends RuntimeException {

    public BatchTimeoutException(String message) {
        super(message);
    }
}
