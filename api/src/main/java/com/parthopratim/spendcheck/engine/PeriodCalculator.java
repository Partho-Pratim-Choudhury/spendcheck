package com.parthopratim.spendcheck.engine;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;

/** Salary-period arithmetic (ENGINE.md "Period"). Pure and stateless. */
public final class PeriodCalculator {

    private PeriodCalculator() {
    }

    /** {@code min(salaryDay, lengthOfMonth)} of the given month. */
    public static LocalDate scheduledSalaryDate(int year, int month, int salaryDay) {
        return scheduledSalaryDate(YearMonth.of(year, month), salaryDay);
    }

    public static LocalDate scheduledSalaryDate(YearMonth month, int salaryDay) {
        checkSalaryDay(salaryDay);
        return month.atDay(Math.min(salaryDay, month.lengthOfMonth()));
    }

    /** Start = latest scheduled salary date on or before {@code date}; end = next scheduled date − 1 day. */
    public static Period periodContaining(LocalDate date, int salaryDay) {
        YearMonth month = YearMonth.from(date);
        YearMonth startMonth = scheduledSalaryDate(month, salaryDay).isAfter(date) ? month.minusMonths(1) : month;
        LocalDate start = scheduledSalaryDate(startMonth, salaryDay);
        LocalDate end = scheduledSalaryDate(startMonth.plusMonths(1), salaryDay).minusDays(1);
        return new Period(start, end);
    }

    /**
     * "Salary came on {@code today}": the period starts today and ends the day before the scheduled salary date
     * one month after the scheduled date nearest to today (previous/current/next month; ties go earlier).
     */
    public static Period rolloverPeriod(LocalDate today, int salaryDay) {
        LocalDate nearest = nearestScheduledSalaryDate(today, salaryDay);
        LocalDate end = scheduledSalaryDate(YearMonth.from(nearest).plusMonths(1), salaryDay).minusDays(1);
        return new Period(today, end);
    }

    public static LocalDate nearestScheduledSalaryDate(LocalDate date, int salaryDay) {
        YearMonth month = YearMonth.from(date);
        LocalDate best = null;
        long bestDistance = Long.MAX_VALUE;
        // chronological order + strict '<' means ties keep the earlier date
        for (YearMonth m : new YearMonth[] {month.minusMonths(1), month, month.plusMonths(1)}) {
            LocalDate candidate = scheduledSalaryDate(m, salaryDay);
            long distance = Math.abs(ChronoUnit.DAYS.between(candidate, date));
            if (distance < bestDistance) {
                best = candidate;
                bestDistance = distance;
            }
        }
        return best;
    }

    /**
     * The period that follows one ending on {@code previousEnd} (automatic rollover): it starts the next day and
     * ends by the salary-day rule. If that would make a single-day period (possible only after the salary day was
     * changed), it runs to the end of the following period instead, since a plan must span at least two days.
     */
    public static Period nextPeriodAfter(LocalDate previousEnd, int salaryDay) {
        LocalDate start = previousEnd.plusDays(1);
        LocalDate end = periodContaining(start, salaryDay).end();
        if (!end.isAfter(start)) {
            end = periodContaining(end.plusDays(1), salaryDay).end();
        }
        return new Period(start, end);
    }

    public static int daysTotal(Period p) {
        return (int) ChronoUnit.DAYS.between(p.start(), p.end()) + 1;
    }

    /** {@code today − start + 1}, clamped to {@code 1..daysTotal}; includes today. */
    public static int daysElapsed(Period p, LocalDate today) {
        long elapsed = ChronoUnit.DAYS.between(p.start(), today) + 1;
        return (int) Math.max(1, Math.min(daysTotal(p), elapsed));
    }

    /** {@code end − today + 1}, at least 1; includes today. */
    public static int daysLeft(Period p, LocalDate today) {
        long left = ChronoUnit.DAYS.between(today, p.end()) + 1;
        return (int) Math.max(1, left);
    }

    private static void checkSalaryDay(int salaryDay) {
        if (salaryDay < 1 || salaryDay > 31) {
            throw new IllegalArgumentException("salaryDay must be 1..31, was " + salaryDay);
        }
    }
}
