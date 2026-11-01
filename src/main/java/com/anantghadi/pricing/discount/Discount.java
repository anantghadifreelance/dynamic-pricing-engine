package com.anantghadi.pricing.discount;

import com.anantghadi.pricing.domain.Marketplace;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A promotional discount. {@code sku} and {@code marketplace} narrow its scope (null = applies to
 * all); {@code validFrom}/{@code validTo} are inclusive and may be open-ended.
 */
@Entity
@Table(name = "discounts", indexes = @Index(name = "idx_discount_sku", columnList = "sku"))
public class Discount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DiscountType type;

    @Column(name = "discount_value", nullable = false, precision = 14, scale = 2)
    private BigDecimal value;

    @Column(length = 64)
    private String sku;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Marketplace marketplace;

    private LocalDate validFrom;

    private LocalDate validTo;

    @Column(nullable = false)
    private boolean active;

    protected Discount() {
        // for JPA
    }

    public Discount(String name, DiscountType type, BigDecimal value, String sku, Marketplace marketplace,
                    LocalDate validFrom, LocalDate validTo, boolean active) {
        update(name, type, value, sku, marketplace, validFrom, validTo, active);
    }

    public final void update(String name, DiscountType type, BigDecimal value, String sku, Marketplace marketplace,
                             LocalDate validFrom, LocalDate validTo, boolean active) {
        this.name = name;
        this.type = type;
        this.value = value;
        this.sku = sku;
        this.marketplace = marketplace;
        this.validFrom = validFrom;
        this.validTo = validTo;
        this.active = active;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public DiscountType getType() {
        return type;
    }

    public BigDecimal getValue() {
        return value;
    }

    public String getSku() {
        return sku;
    }

    public Marketplace getMarketplace() {
        return marketplace;
    }

    public LocalDate getValidFrom() {
        return validFrom;
    }

    public LocalDate getValidTo() {
        return validTo;
    }

    public boolean isActive() {
        return active;
    }
}
