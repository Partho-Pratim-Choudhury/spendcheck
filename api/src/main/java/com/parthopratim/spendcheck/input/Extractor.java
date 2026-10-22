package com.parthopratim.spendcheck.input;

import java.time.LocalDate;
import java.util.List;

/** Turns one free-text input into a structured {@link Extraction} (an LLM call in production). */
public interface Extractor {

    /**
     * @param categories the user's active (non-archived) categories, offered to the model as choices
     * @throws com.parthopratim.spendcheck.common.ServiceUnavailableException not configured / no credits
     * @throws RuntimeException any other failure; callers map it to 502
     */
    Extraction extract(String text, LocalDate today, List<InputCategory> categories);

    /** The model id recorded with each raw input. */
    String model();
}
