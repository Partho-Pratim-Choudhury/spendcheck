package com.parthopratim.spendcheck.engine;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

/**
 * Formats paise as whole rupees for message text, rounding half-up (away from zero): {@code ₹1,20,000},
 * {@code ₹1,235}, {@code -₹500}. JSON fields keep exact paise; only text is rounded.
 */
public final class Inr {

    private Inr() {
    }

    public static String format(long paise) {
        return rupees(paise, RoundingMode.HALF_UP);
    }

    /** Rounded up (away from zero) to a whole rupee, for amounts that must never be understated ("₹1 over"). */
    public static String formatUp(long paise) {
        return rupees(paise, RoundingMode.UP);
    }

    private static String rupees(long paise, RoundingMode mode) {
        // BigDecimal: rounds the magnitude, and Long.MIN_VALUE has no positive long counterpart
        BigInteger rupees = BigDecimal.valueOf(paise).movePointLeft(2).setScale(0, mode).toBigInteger();
        StringBuilder sb = new StringBuilder();
        if (rupees.signum() < 0) {
            sb.append('-');
        }
        return sb.append('₹').append(group(rupees.abs().toString())).toString();
    }

    /** Indian grouping: the last three digits, then groups of two (1,23,45,678). */
    static String group(String digits) {
        if (digits.length() <= 3) {
            return digits;
        }
        String head = digits.substring(0, digits.length() - 3);
        String tail = digits.substring(digits.length() - 3);
        StringBuilder sb = new StringBuilder();
        int first = head.length() % 2;
        if (first > 0) {
            sb.append(head, 0, first);
        }
        for (int i = first; i < head.length(); i += 2) {
            if (!sb.isEmpty()) {
                sb.append(',');
            }
            sb.append(head, i, i + 2);
        }
        return sb.append(',').append(tail).toString();
    }
}
