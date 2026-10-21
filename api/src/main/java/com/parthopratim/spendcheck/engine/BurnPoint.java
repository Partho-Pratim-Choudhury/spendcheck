package com.parthopratim.spendcheck.engine;

import java.time.LocalDate;

public record BurnPoint(LocalDate date, long cumulativePaise) {}
