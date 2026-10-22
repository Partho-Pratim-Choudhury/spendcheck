package com.parthopratim.spendcheck.engine;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class InrTest {

    @ParameterizedTest(name = "{0} paise -> {1}")
    @CsvSource(delimiter = '|', value = {
            "0             | ₹0",
            "5             | ₹0",
            "49            | ₹0",
            "50            | ₹1",
            "100           | ₹1",
            "149           | ₹1",
            "150           | ₹2",
            "99999         | ₹1,000",
            "100000        | ₹1,000",
            "123456        | ₹1,235",
            "1000000       | ₹10,000",
            "5000000       | ₹50,000",
            "12000000      | ₹1,20,000",
            "100000000     | ₹10,00,000",
            "1234567890    | ₹1,23,45,679",
            "1210937       | ₹12,109",
            "1000000000000 | ₹10,00,00,00,000",
            "-50000        | -₹500",
            "-150          | -₹2",
            "-1            | ₹0",
    })
    void formatsWholeRupeesHalfUpWithIndianGrouping(long paise, String expected) {
        assertThat(Inr.format(paise)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0} paise up -> {1}")
    @CsvSource(delimiter = '|', value = {"0 | ₹0", "1 | ₹1", "100 | ₹1", "101 | ₹2", "-1 | -₹1"})
    void formatUpNeverUnderstates(long paise, String expected) {
        assertThat(Inr.formatUp(paise)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0} paise down -> {1}")
    @CsvSource(delimiter = '|', value = {"0 | ₹0", "1 | ₹0", "99 | ₹0", "100 | ₹1", "199 | ₹1", "123456 | ₹1,234",
            "-1 | -₹1"})
    void formatDownNeverOverstates(long paise, String expected) {
        assertThat(Inr.formatDown(paise)).isEqualTo(expected);
    }
}
