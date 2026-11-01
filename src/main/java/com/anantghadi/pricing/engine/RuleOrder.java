package com.anantghadi.pricing.engine;

/** Pipeline positions, spaced out so new rules can be slotted in between. */
public final class RuleOrder {

    public static final int BASE_COST_AND_MARGIN = 100;
    public static final int DISCOUNTS = 200;
    public static final int QUANTITY_TIERS = 300;
    public static final int FREIGHT = 400;
    public static final int MARKETPLACE_FEES = 500;
    public static final int PRICE_FLOOR = 600;
    public static final int GST = 700;

    private RuleOrder() {
    }
}
