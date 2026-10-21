package com.parthopratim.spendcheck.engine;

import java.time.LocalDate;

public record GoalInput(long id, String name, long monthlyContributionPaise, LocalDate targetDate) {}
