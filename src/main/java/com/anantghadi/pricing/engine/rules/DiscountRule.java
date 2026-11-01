package com.anantghadi.pricing.engine.rules;

import com.anantghadi.pricing.discount.Discount;
import com.anantghadi.pricing.discount.DiscountRepository;
import com.anantghadi.pricing.discount.DiscountType;
import com.anantghadi.pricing.domain.Money;
import com.anantghadi.pricing.engine.PricingContext;
import com.anantghadi.pricing.engine.PricingRule;
import com.anantghadi.pricing.engine.RuleOrder;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

/**
 * Applies every active discount whose validity window contains the pricing date and whose scope
 * (SKU / marketplace, null meaning "all") matches the request. Percentage discounts are applied
 * before flat ones so a flat coupon is never inflated by a later percentage. A discount can take the
 * price to zero at most; the floor guard later restores a viable price.
 */
@Component
@Order(RuleOrder.DISCOUNTS)
public class DiscountRule implements PricingRule {

    private static final Comparator<Discount> PERCENTAGE_FIRST =
            Comparator.comparing((Discount d) -> d.getType() == DiscountType.PERCENTAGE ? 0 : 1)
                    .thenComparing(Discount::getId, Comparator.nullsLast(Comparator.naturalOrder()));

    private final DiscountRepository repository;

    public DiscountRule(DiscountRepository repository) {
        this.repository = repository;
    }

    @Override
    public String name() {
        return "DISCOUNT";
    }

    @Override
    public void apply(PricingContext context) {
        List<Discount> discounts = repository.findApplicable(
                context.request().sku(), context.request().marketplace(), context.pricingDate());

        discounts.stream().sorted(PERCENTAGE_FIRST).forEach(discount -> {
            BigDecimal reduction = switch (discount.getType()) {
                case PERCENTAGE -> Money.percentOf(context.unitPrice(), discount.getValue());
                case FLAT -> Money.round(discount.getValue());
            };
            BigDecimal capped = reduction.min(context.unitPrice());
            context.adjust(name(), describe(discount), capped.negate());
        });
    }

    private static String describe(Discount discount) {
        String value = discount.getValue().stripTrailingZeros().toPlainString();
        String amount = discount.getType() == DiscountType.PERCENTAGE ? value + "%" : "flat " + value;
        return "Discount '" + discount.getName() + "' (" + amount + ")";
    }
}
