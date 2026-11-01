package com.anantghadi.pricing.engine;

import com.anantghadi.pricing.domain.Money;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class MoneyTest {

    @ParameterizedTest(name = "{0} rounds to {1}")
    @CsvSource({
            "1.005, 1.01",
            "1.004, 1.00",
            "2.345, 2.35",
            "-1.005, -1.01",
            "10, 10.00"
    })
    void roundsHalfUpToTwoDecimals(String input, String expected) {
        BigDecimal rounded = Money.round(new BigDecimal(input));

        assertThat(rounded).isEqualByComparingTo(expected);
        assertThat(rounded.scale()).isEqualTo(2);
    }

    @ParameterizedTest(name = "{1}% of {0} = {2}")
    @CsvSource({
            "200, 18, 36.00",
            "99.99, 15, 15.00",
            "33.33, 12.5, 4.17",
            "0.01, 18, 0.00"
    })
    void computesRoundedPercentages(String amount, String percent, String expected) {
        assertThat(Money.percentOf(new BigDecimal(amount), new BigDecimal(percent))).isEqualByComparingTo(expected);
    }
}
