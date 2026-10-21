package com.parthopratim.spendcheck.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class PeriodCalculatorTest {

    @ParameterizedTest(name = "{0}-{1} salary day {2} -> {3}")
    @CsvSource({
            "2026, 10,  7, 2026-10-07",
            "2026,  1, 31, 2026-01-31",
            "2026,  2, 31, 2026-02-28",   // clamp to Feb
            "2026,  2, 29, 2026-02-28",
            "2026,  2, 28, 2026-02-28",
            "2028,  2, 31, 2028-02-29",   // leap year
            "2028,  2, 30, 2028-02-29",
            "2028,  2, 29, 2028-02-29",
            "2026,  4, 31, 2026-04-30",   // 30-day month
            "2026,  4, 30, 2026-04-30",
            "2026,  6, 31, 2026-06-30",
            "2026, 12, 31, 2026-12-31",
            "2026,  3,  1, 2026-03-01",
    })
    void scheduledSalaryDate(int year, int month, int salaryDay, LocalDate expected) {
        assertThat(PeriodCalculator.scheduledSalaryDate(year, month, salaryDay)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 32, -1})
    void salaryDayOutOfRangeIsRejected(int salaryDay) {
        assertThatThrownBy(() -> PeriodCalculator.scheduledSalaryDate(2026, 1, salaryDay))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest(name = "{0} salary day {1} -> {2}..{3}")
    @CsvSource({
            "2026-10-22,  7, 2026-10-07, 2026-11-06",
            "2026-10-07,  7, 2026-10-07, 2026-11-06",   // on salary day
            "2026-10-06,  7, 2026-09-07, 2026-10-06",   // day before salary day
            "2026-11-06,  7, 2026-10-07, 2026-11-06",   // last day
            "2027-01-05,  7, 2026-12-07, 2027-01-06",   // across the year end
            "2026-12-31, 31, 2026-12-31, 2027-01-30",
            "2026-01-15,  1, 2026-01-01, 2026-01-31",
            "2026-02-27, 31, 2026-01-31, 2026-02-27",   // salary day 31 in Feb: Feb 28 is payday
            "2026-02-28, 31, 2026-02-28, 2026-03-30",
            "2026-03-15, 31, 2026-02-28, 2026-03-30",
            "2026-03-31, 31, 2026-03-31, 2026-04-29",   // into a 30-day month
            "2026-04-30, 31, 2026-04-30, 2026-05-30",
            "2026-06-30, 31, 2026-06-30, 2026-07-30",
            "2028-02-29, 31, 2028-02-29, 2028-03-30",   // leap year
            "2028-02-28, 30, 2028-01-30, 2028-02-28",
            "2028-02-29, 30, 2028-02-29, 2028-03-29",
            "2026-02-28, 30, 2026-02-28, 2026-03-29",
    })
    void periodContaining(LocalDate date, int salaryDay, LocalDate start, LocalDate end) {
        assertThat(PeriodCalculator.periodContaining(date, salaryDay)).isEqualTo(new Period(start, end));
    }

    @ParameterizedTest(name = "salary on {0} (day {1}) -> {2}..{3}")
    @CsvSource({
            "2026-10-07,  7, 2026-10-07, 2026-11-06",   // on the day
            "2026-10-05,  7, 2026-10-05, 2026-11-06",   // early
            "2026-10-09,  7, 2026-10-09, 2026-11-06",   // late
            "2026-11-03,  7, 2026-11-03, 2026-12-06",   // early, for next month's salary
            "2026-10-22,  7, 2026-10-22, 2026-11-06",   // 15 days after Oct 7, 16 before Nov 7
            "2026-10-23,  7, 2026-10-23, 2026-12-06",   // 16 after, 15 before -> Nov 7 is nearest
            "2026-09-16,  1, 2026-09-16, 2026-09-30",   // tie (Sep 1 vs Oct 1): earlier wins
            "2026-03-02, 31, 2026-03-02, 2026-03-30",   // nearest is Feb 28 (clamped)
            "2026-02-26, 31, 2026-02-26, 2026-03-30",
            "2028-02-27, 30, 2028-02-27, 2028-03-29",   // leap: Feb 29 is nearest
            "2026-12-30,  2, 2026-12-30, 2027-02-01",   // nearest is Jan 2 next year
            "2027-01-01, 31, 2027-01-01, 2027-01-30",   // nearest is Dec 31 previous year
    })
    void rolloverPeriod(LocalDate today, int salaryDay, LocalDate start, LocalDate end) {
        assertThat(PeriodCalculator.rolloverPeriod(today, salaryDay)).isEqualTo(new Period(start, end));
    }

    @ParameterizedTest(name = "after {0} (day {1}) -> {2}..{3}")
    @CsvSource({
            "2026-11-06,  7, 2026-11-07, 2026-12-06",
            "2026-02-27, 31, 2026-02-28, 2026-03-30",
            "2026-11-06, 20, 2026-11-07, 2026-11-19",   // salary day changed to later: short period
            "2026-11-06,  8, 2026-11-07, 2026-12-07",   // would be one day: runs to the following period end
            "2026-12-06,  7, 2026-12-07, 2027-01-06",
    })
    void nextPeriodAfter(LocalDate previousEnd, int salaryDay, LocalDate start, LocalDate end) {
        assertThat(PeriodCalculator.nextPeriodAfter(previousEnd, salaryDay)).isEqualTo(new Period(start, end));
    }

    @ParameterizedTest(name = "{0}..{1} on {2}: total {3}, elapsed {4}, left {5}")
    @CsvSource({
            "2026-10-07, 2026-11-06, 2026-10-07, 31,  1, 31",   // first day
            "2026-10-07, 2026-11-06, 2026-10-22, 31, 16, 16",
            "2026-10-07, 2026-11-06, 2026-11-06, 31, 31,  1",   // last day
            "2026-10-07, 2026-11-06, 2026-11-10, 31, 31,  1",   // after end: elapsed clamped, daysLeft min 1
            "2026-10-07, 2026-11-06, 2026-10-01, 31,  1, 37",   // before start: elapsed clamped to 1
            "2026-02-07, 2026-03-06, 2026-02-20, 28, 14, 15",   // February
            "2028-02-07, 2028-03-06, 2028-02-29, 29, 23,  7",   // leap February
    })
    void dayCounts(LocalDate start, LocalDate end, LocalDate today, int total, int elapsed, int left) {
        Period p = new Period(start, end);
        assertThat(PeriodCalculator.daysTotal(p)).isEqualTo(total);
        assertThat(PeriodCalculator.daysElapsed(p, today)).isEqualTo(elapsed);
        assertThat(PeriodCalculator.daysLeft(p, today)).isEqualTo(left);
    }

    @Test
    void periodRejectsEndBeforeStart() {
        assertThatThrownBy(() -> new Period(LocalDate.of(2026, 1, 2), LocalDate.of(2026, 1, 1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void everyDayOfFourYearsIsInsideItsPeriodAndPeriodsTile() {
        for (int salaryDay = 1; salaryDay <= 31; salaryDay++) {
            Period prev = null;
            for (LocalDate d = LocalDate.of(2026, 1, 1); d.isBefore(LocalDate.of(2030, 1, 1)); d = d.plusDays(1)) {
                Period p = PeriodCalculator.periodContaining(d, salaryDay);
                assertThat(p.contains(d)).isTrue();
                assertThat(p.end()).isAfter(p.start());
                if (prev != null && !prev.equals(p)) {
                    assertThat(p.start()).isEqualTo(prev.end().plusDays(1));
                }
                prev = p;
            }
        }
    }
}
