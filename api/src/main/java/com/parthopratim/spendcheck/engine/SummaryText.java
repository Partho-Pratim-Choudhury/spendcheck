package com.parthopratim.spendcheck.engine;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Deterministic one-paragraph daily summary. */
public final class SummaryText {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    private SummaryText() {
    }

    /**
     * e.g. "Today ₹1,820 across 3 spends — ₹207 over today's safe spend. Month: ₹18,400 of ₹50,000, projected
     * ₹52,100." For a past date "Today" becomes "On 21 Oct 2026" and "today's" becomes "the day's".
     */
    public static String daily(LocalDate date, LocalDate today, long spentPaise, long count, long budgetPaise,
                               long monthSpentPaise, long discretionaryPaise, long projectedPaise,
                               long pausesSavedPaise) {
        boolean isToday = date.isEqual(today);
        String when = isToday ? "Today" : "On " + DAY.format(date);
        String safe = isToday ? "today's safe spend" : "the day's safe spend";
        StringBuilder sb = new StringBuilder();
        if (count == 0) {
            sb.append(isToday ? "No spends today" : "No spends on " + DAY.format(date))
                    .append(" — ").append(Inr.format(budgetPaise)).append(" of ").append(safe).append(" unused.");
        } else {
            sb.append(when).append(' ').append(Inr.format(spentPaise)).append(" across ").append(count)
                    .append(count == 1 ? " spend" : " spends").append(" — ");
            long diff = spentPaise - budgetPaise;
            if (diff > 0) {
                sb.append(Inr.format(diff)).append(" over ").append(safe).append('.');
            } else if (diff < 0) {
                sb.append(Inr.format(-diff)).append(" under ").append(safe).append('.');
            } else {
                sb.append("exactly ").append(safe).append('.');
            }
        }
        sb.append(" Month: ").append(Inr.format(monthSpentPaise)).append(" of ").append(Inr.format(discretionaryPaise))
                .append(", projected ").append(Inr.format(projectedPaise)).append('.');
        if (pausesSavedPaise > 0) {
            sb.append(" Skipping spends saved ").append(Inr.format(pausesSavedPaise)).append('.');
        }
        return sb.toString();
    }
}
