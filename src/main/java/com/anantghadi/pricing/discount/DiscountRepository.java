package com.anantghadi.pricing.discount;

import com.anantghadi.pricing.domain.Marketplace;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface DiscountRepository extends JpaRepository<Discount, Long> {

    @Query("""
            select d from Discount d
            where d.active = true
              and (d.sku is null or d.sku = :sku)
              and (d.marketplace is null or d.marketplace = :marketplace)
              and (d.validFrom is null or d.validFrom <= :date)
              and (d.validTo is null or d.validTo >= :date)
            """)
    List<Discount> findApplicable(@Param("sku") String sku,
                                  @Param("marketplace") Marketplace marketplace,
                                  @Param("date") LocalDate date);
}
