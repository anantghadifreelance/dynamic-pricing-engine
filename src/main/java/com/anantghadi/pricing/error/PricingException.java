package com.anantghadi.pricing.error;

/** A quote that cannot be computed with the current configuration (mapped to 422). */
public class PricingException extends RuntimeException {

    public PricingException(String message) {
        super(message);
    }
}
