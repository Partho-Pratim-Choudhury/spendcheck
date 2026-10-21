package com.parthopratim.spendcheck.engine;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class InrTest {

    @ParameterizedTest(name = "{0} paise -> {1}")
    @CsvSource(delimiter = '|', value = {
            "0             | ₹0",
            "5             | ₹0.05",
            "50            | ₹0.50",
            "100           | ₹1",
            "99999         | ₹999.99",
            "100000        | ₹1,000",
            "123456        | ₹1,234.56",
            "1000000       | ₹10,000",
            "5000000       | ₹50,000",
            "12000000      | ₹1,20,000",
            "100000000     | ₹10,00,000",
            "1234567890    | ₹1,23,45,678.90",
            "1000000000000 | ₹10,00,00,00,000",
            "-50000        | -₹500",
            "-1            | -₹0.01",
    })
    void formatsWithIndianGrouping(long paise, String expected) {
        assertThat(Inr.format(paise)).isEqualTo(expected);
    }
}
