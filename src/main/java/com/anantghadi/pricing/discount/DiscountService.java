package com.anantghadi.pricing.discount;

import com.anantghadi.pricing.config.AppConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** CRUD for discounts. Every write clears the quote cache because any cached quote may now be wrong. */
@Service
@Transactional(readOnly = true)
public class DiscountService {

    private final DiscountRepository repository;

    public DiscountService(DiscountRepository repository) {
        this.repository = repository;
    }

    public List<DiscountResponse> findAll() {
        return repository.findAll().stream().map(DiscountResponse::from).toList();
    }

    public DiscountResponse findById(Long id) {
        return DiscountResponse.from(load(id));
    }

    @Transactional
    @CacheEvict(cacheNames = AppConfig.QUOTES_CACHE, allEntries = true)
    public DiscountResponse create(DiscountRequest request) {
        Discount discount = new Discount(request.name(), request.type(), request.value(), blankToNull(request.sku()),
                request.marketplace(), request.validFrom(), request.validTo(), request.activeOrDefault());
        return DiscountResponse.from(repository.save(discount));
    }

    @Transactional
    @CacheEvict(cacheNames = AppConfig.QUOTES_CACHE, allEntries = true)
    public DiscountResponse update(Long id, DiscountRequest request) {
        Discount discount = load(id);
        discount.update(request.name(), request.type(), request.value(), blankToNull(request.sku()),
                request.marketplace(), request.validFrom(), request.validTo(), request.activeOrDefault());
        return DiscountResponse.from(discount);
    }

    @Transactional
    @CacheEvict(cacheNames = AppConfig.QUOTES_CACHE, allEntries = true)
    public void delete(Long id) {
        repository.delete(load(id));
    }

    private Discount load(Long id) {
        return repository.findById(id).orElseThrow(() -> new DiscountNotFoundException(id));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
