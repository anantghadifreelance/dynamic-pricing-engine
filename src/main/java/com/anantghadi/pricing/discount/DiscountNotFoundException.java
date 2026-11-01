package com.anantghadi.pricing.discount;

public class DiscountNotFoundException extends RuntimeException {

    public DiscountNotFoundException(Long id) {
        super("Discount " + id + " not found");
    }
}
