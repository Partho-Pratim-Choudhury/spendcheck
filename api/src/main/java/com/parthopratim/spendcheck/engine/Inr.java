package com.parthopratim.spendcheck.engine;

/** Formats paise as rupees for message text: {@code ₹1,20,000}, {@code ₹1,234.50}, {@code -₹500}. */
public final class Inr {

    private Inr() {
    }

    public static String format(long paise) {
        boolean negative = paise < 0;
        // via unsigned-safe magnitude: Long.MIN_VALUE has no positive counterpart
        String digits = Long.toUnsignedString(negative ? -paise : paise);
        String rupees = digits.length() > 2 ? digits.substring(0, digits.length() - 2) : "0";
        String fraction = digits.length() >= 2 ? digits.substring(digits.length() - 2) : "0" + digits;
        StringBuilder sb = new StringBuilder();
        if (negative) {
            sb.append('-');
        }
        sb.append('₹').append(group(rupees));
        if (!fraction.equals("00")) {
            sb.append('.').append(fraction);
        }
        return sb.toString();
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
