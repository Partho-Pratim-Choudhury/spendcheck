package com.parthopratim.spendcheck.input;

import com.parthopratim.spendcheck.ledger.NeedOrWant;
import java.math.BigDecimal;

/**
 * What the LLM read out of one input, before any validation. Stored verbatim in {@code raw_inputs.llm_output}.
 *
 * @param amountRupees  rupees as said ("1.2k" = 1200); null when not mentioned
 * @param category      one of the user's category names, or null
 * @param date          {@code yyyy-MM-dd} in the user's tz; null means today
 * @param needOrWant    only when the user said so or it is obvious
 * @param queryCategory the category a {@code CATEGORY_LEFT} question is about
 * @param confidence    0..1, the model's own estimate
 */
public record Extraction(
        InputIntent intent,
        BigDecimal amountRupees,
        String category,
        String merchant,
        String note,
        String date,
        NeedOrWant needOrWant,
        QueryType queryType,
        String queryCategory,
        Double confidence) {}
