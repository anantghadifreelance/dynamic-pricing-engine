package com.anantghadi.pricing.engine;

import com.anantghadi.pricing.api.dto.QuoteRequest;
import com.anantghadi.pricing.api.dto.QuoteResponse;
import com.anantghadi.pricing.config.AppConfig;
import com.anantghadi.pricing.config.PricingProperties;
import com.anantghadi.pricing.domain.Money;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Runs the ordered {@link PricingRule} chain for a request. Results are cached per
 * (request, pricing date): the date is part of the key so a discount window that opens or closes at
 * midnight is never hidden behind a stale entry. Discount writes evict the cache.
 */
@Service
public class PricingService {

    private final List<PricingRule> rules;
    private final PricingProperties properties;

    public PricingService(List<PricingRule> rules, PricingProperties properties) {
        this.rules = List.copyOf(rules);
        this.properties = properties;
    }

    @Cacheable(cacheNames = AppConfig.QUOTES_CACHE)
    public QuoteResponse quote(QuoteRequest request, LocalDate pricingDate) {
        PricingContext context = new PricingContext(request, pricingDate);
        rules.forEach(rule -> rule.apply(context));

        BigDecimal unitPrice = context.unitPrice();
        BigDecimal total = Money.round(unitPrice.multiply(BigDecimal.valueOf(request.quantity())));
        return new QuoteResponse(
                request.sku(),
                request.marketplace(),
                request.zone(),
                request.quantity(),
                properties.currency(),
                pricingDate,
                context.adjustments(),
                context.floorApplied(),
                unitPrice,
                total);
    }

    /** Rule names in execution order; handy for diagnostics and tests. */
    public List<String> ruleNames() {
        return rules.stream().map(PricingRule::name).toList();
    }
}
