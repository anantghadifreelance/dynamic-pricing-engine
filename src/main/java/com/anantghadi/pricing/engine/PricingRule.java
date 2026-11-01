package com.anantghadi.pricing.engine;

/**
 * One step of the pricing pipeline. Implementations are Spring beans ordered with
 * {@link org.springframework.core.annotation.Order}; the engine runs them in that order, each one
 * reading and adjusting the shared {@link PricingContext}. Adding a rule means adding a bean.
 */
public interface PricingRule {

    /** Stable identifier shown in the breakdown, e.g. {@code GST}. */
    String name();

    void apply(PricingContext context);
}
