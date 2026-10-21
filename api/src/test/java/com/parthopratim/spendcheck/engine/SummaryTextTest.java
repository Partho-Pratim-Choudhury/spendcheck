package com.parthopratim.spendcheck.engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class SummaryTextTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 22);

    @Test
    void overTodaysSafeSpend() {
        assertThat(SummaryText.daily(TODAY, TODAY, 182_000, 3, 161_300, 1_840_000, 5_000_000, 5_210_000, 0))
                .isEqualTo("Today ₹1,820 across 3 spends — ₹207 over today's safe spend. "
                        + "Month: ₹18,400 of ₹50,000, projected ₹52,100.");
    }

    @Test
    void underSingleSpend() {
        assertThat(SummaryText.daily(TODAY, TODAY, 50_000, 1, 161_350, 1_840_000, 5_000_000, 5_210_000, 0))
                .isEqualTo("Today ₹500 across 1 spend — ₹1,113.50 under today's safe spend. "
                        + "Month: ₹18,400 of ₹50,000, projected ₹52,100.");
    }

    @Test
    void exactlyAtBudget() {
        assertThat(SummaryText.daily(TODAY, TODAY, 100_000, 2, 100_000, 100_000, 5_000_000, 3_100_000, 0))
                .startsWith("Today ₹1,000 across 2 spends — exactly today's safe spend.");
    }

    @Test
    void noSpendsWithPauses() {
        assertThat(SummaryText.daily(TODAY, TODAY, 0, 0, 161_300, 1_840_000, 5_000_000, 5_210_000, 350_000))
                .isEqualTo("No spends today — ₹1,613 of today's safe spend unused. "
                        + "Month: ₹18,400 of ₹50,000, projected ₹52,100. Skipping spends saved ₹3,500.");
    }

    @Test
    void pastDate() {
        assertThat(SummaryText.daily(LocalDate.of(2026, 10, 9), TODAY, 30_000, 2, 20_000, 30_000, 5_000_000,
                3_100_000, 0))
                .isEqualTo("On 9 Oct 2026 ₹300 across 2 spends — ₹100 over the day's safe spend. "
                        + "Month: ₹300 of ₹50,000, projected ₹31,000.");
    }
}
