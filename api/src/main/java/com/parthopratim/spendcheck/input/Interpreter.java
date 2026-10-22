package com.parthopratim.spendcheck.input;

import com.parthopratim.spendcheck.engine.Inr;
import com.parthopratim.spendcheck.input.InputDtos.FollowUp;
import com.parthopratim.spendcheck.ledger.CategoryKind;
import com.parthopratim.spendcheck.ledger.NeedOrWant;
import com.parthopratim.spendcheck.ledger.TransactionDtos;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Deterministic post-processing of an {@link Extraction}: the LLM proposes, this decides. Pure (no Spring, no I/O)
 * so the eval harness runs exactly the production rules.
 */
public final class Interpreter {

    /** Spends may be logged up to this many days back. */
    public static final int MAX_PAST_DAYS = 31;

    public static final String MISSING_AMOUNT = "amount";
    public static final String MISSING_CATEGORY = "category";
    public static final String MISSING_DATE = "date";
    public static final String MISSING_CONFIRMATION = "confirmation";

    private static final int MAX_MERCHANT = 200;
    private static final int MAX_NOTE = 1000;
    private static final int CATEGORY_CHOICES = 4;
    private static final int FUZZY_MIN_LENGTH = 5;

    /** Brands whose casing title-case gets wrong. Keys: lower case, no spaces/punctuation. */
    private static final Map<String, String> BRAND_CASING = Map.ofEntries(
            Map.entry("dmart", "DMart"), Map.entry("bigbasket", "BigBasket"), Map.entry("pvr", "PVR"),
            Map.entry("inox", "INOX"), Map.entry("kfc", "KFC"), Map.entry("ikea", "IKEA"),
            Map.entry("mcdonalds", "McDonald's"), Map.entry("phonepe", "PhonePe"), Map.entry("jiomart", "JioMart"),
            Map.entry("makemytrip", "MakeMyTrip"), Map.entry("bookmyshow", "BookMyShow"),
            Map.entry("irctc", "IRCTC"), Map.entry("ajio", "AJIO"), Map.entry("oyo", "OYO"),
            Map.entry("hdfc", "HDFC"), Map.entry("sbi", "SBI"), Map.entry("lic", "LIC"),
            Map.entry("bpcl", "BPCL"), Map.entry("hpcl", "HPCL"), Map.entry("iocl", "IOCL"),
            Map.entry("youtube", "YouTube"), Map.entry("tatacliq", "Tata CLiQ"), Map.entry("firstcry", "FirstCry"),
            Map.entry("pharmeasy", "PharmEasy"), Map.entry("netmeds", "Netmeds"), Map.entry("1mg", "1mg"));

    private Interpreter() {
    }

    /** The validated reading of one input; a non-null {@code followUp} means "ask, don't act". */
    public record Interpretation(
            InputIntent intent, Long amountPaise, InputCategory category, String merchant, String note,
            LocalDate date, NeedOrWant needOrWant, QueryType queryType, FollowUp followUp) {}

    public static Interpretation interpret(Extraction e, String text, List<InputCategory> categories,
                                           LocalDate today, double confidenceThreshold) {
        InputIntent intent = e.intent() != null ? e.intent() : InputIntent.OTHER;
        List<InputCategory> active = categories.stream().filter(c -> !c.archived()).toList();
        double confidence = e.confidence() != null ? e.confidence() : 1.0;
        boolean unsure = confidence < confidenceThreshold;
        return switch (intent) {
            case LOG_EXPENSE, PRE_CHECK -> spend(intent, e, text, active, today, unsure);
            case QUERY -> query(e, text, active);
            case UNDO -> new Interpretation(intent, null, null, null, null, null, null, null, unsure
                    ? new FollowUp("Should I remove your last entry? Say “undo” to confirm.",
                    List.of(MISSING_CONFIRMATION))
                    : null);
            case OTHER -> new Interpretation(intent, null, null, null, null, null, null, null, null);
        };
    }

    // ---------- spends ----------

    private enum DateProblem { NONE, UNREADABLE, FUTURE, TOO_OLD }

    private static Interpretation spend(InputIntent intent, Extraction e, String text, List<InputCategory> active,
                                        LocalDate today, boolean unsure) {
        Optional<Long> amount = Optional.ofNullable(e.amountRupees()).map(Interpreter::toPaise);
        boolean amountInvalid = amount.isPresent() && !validAmount(amount.get());
        String merchant = cleanMerchant(e.merchant(), active);
        InputCategory category = resolveCategory(text, merchant, e.category(), active);

        LocalDate date = today;
        DateProblem dateProblem = DateProblem.NONE;
        if (intent == InputIntent.LOG_EXPENSE && e.date() != null && !e.date().isBlank()) {
            try {
                date = LocalDate.parse(e.date().trim());
                if (date.isAfter(today)) {
                    dateProblem = DateProblem.FUTURE;
                } else if (date.isBefore(today.minusDays(MAX_PAST_DAYS))) {
                    dateProblem = DateProblem.TOO_OLD;
                }
            } catch (DateTimeParseException ex) {
                dateProblem = DateProblem.UNREADABLE;
            }
        }

        List<String> missing = new ArrayList<>();
        List<String> questions = new ArrayList<>();
        boolean noAmount = amount.isEmpty() || amountInvalid;
        if (noAmount) {
            missing.add(MISSING_AMOUNT);
        }
        if (category == null) {
            missing.add(MISSING_CATEGORY);
        }
        if (noAmount && category == null) {
            questions.add((intent == InputIntent.PRE_CHECK ? "How much is it" : "How much was it")
                    + ", and which category — " + choices(active) + "?");
        } else if (noAmount) {
            questions.add(amountInvalid ? "That amount doesn't look right — how much was it?"
                    : intent == InputIntent.PRE_CHECK ? "How much does it cost?" : "How much was it?");
        } else if (category == null) {
            questions.add("Which category — " + choices(active) + "?");
        }
        if (dateProblem != DateProblem.NONE) {
            missing.add(MISSING_DATE);
            questions.add(switch (dateProblem) {
                case FUTURE -> "That date is in the future — which day was it?";
                case TOO_OLD -> "I can only log spends from the last " + MAX_PAST_DAYS + " days — which day was it?";
                default -> "Which day was it?";
            });
        }
        FollowUp followUp = null;
        if (!missing.isEmpty()) {
            followUp = new FollowUp(String.join(" ", questions), missing);
        } else if (unsure) {
            followUp = new FollowUp("Not sure I got that — I read it as " + Inr.format(amount.get()) + " · " + category.name()
                    + ". Could you say it again with a bit more detail?", List.of(MISSING_CONFIRMATION));
        }
        return new Interpretation(intent, amount.orElse(null), category, merchant, truncate(blankToNull(e.note()),
                MAX_NOTE), date, e.needOrWant(), null, followUp);
    }

    /** Rupees to paise, half-up. */
    public static long toPaise(BigDecimal rupees) {
        BigDecimal paise = rupees.movePointRight(2).setScale(0, RoundingMode.HALF_UP);
        return paise.compareTo(BigDecimal.valueOf(Long.MAX_VALUE)) > 0 ? Long.MAX_VALUE
                : paise.compareTo(BigDecimal.valueOf(Long.MIN_VALUE)) < 0 ? Long.MIN_VALUE : paise.longValue();
    }

    /** Positive and confirmable: the same cap {@code POST /transactions} enforces. */
    public static boolean validAmount(long paise) {
        return paise > 0 && paise <= TransactionDtos.MAX_AMOUNT_PAISE;
    }

    private static String choices(List<InputCategory> active) {
        List<String> names = active.stream()
                .sorted(Comparator.comparing((InputCategory c) -> c.kind() == CategoryKind.FIXED)
                        .thenComparingLong(InputCategory::id))
                .map(InputCategory::name).toList();
        if (names.isEmpty()) {
            return "which one";
        }
        String head = String.join(", ", names.subList(0, Math.min(CATEGORY_CHOICES, names.size())));
        return names.size() > CATEGORY_CHOICES ? head + "…" : head;
    }

    // ---------- queries ----------

    private static Interpretation query(Extraction e, String text, List<InputCategory> active) {
        String asked = e.queryCategory() != null && !e.queryCategory().isBlank() ? e.queryCategory() : e.category();
        QueryType type = e.queryType();
        InputCategory category = null;
        if (type == null || type == QueryType.CATEGORY_LEFT) {
            category = resolveCategory(text, null, asked, active);
            if (type == null) {
                type = category != null ? QueryType.CATEGORY_LEFT : QueryType.TOTAL_SPENT;
            }
        }
        FollowUp followUp = type == QueryType.CATEGORY_LEFT && category == null
                ? new FollowUp("Which category — " + choices(active) + "?", List.of(MISSING_CATEGORY))
                : null;
        return new Interpretation(InputIntent.QUERY, null, category, null, null, null, null, type, followUp);
    }

    // ---------- category ----------

    /**
     * Category names said in the text, then the user's aliases found in the text/merchant, then the LLM's pick,
     * then a one-typo alias match. Among several hits the LLM's pick wins if it is one of them, else the earliest.
     * Archived categories are never chosen.
     */
    public static InputCategory resolveCategory(String text, String merchant, String llmCategory,
                                                List<InputCategory> categories) {
        List<InputCategory> active = categories.stream().filter(c -> !c.archived()).toList();
        String hay = (nullToEmpty(text) + " " + nullToEmpty(merchant)).toLowerCase(Locale.ROOT);
        InputCategory llm = byName(llmCategory, active);

        InputCategory byNameInText = pick(hits(hay, active, c -> List.of(c.name())), llm);
        if (byNameInText != null) {
            return byNameInText;
        }
        InputCategory byAlias = pick(hits(hay, active, InputCategory::aliases), llm);
        if (byAlias != null) {
            return byAlias;
        }
        if (llm != null) {
            return llm;
        }
        return fuzzyAlias(hay, active);
    }

    private record Hit(InputCategory category, int position) {}

    private static List<Hit> hits(String hay, List<InputCategory> active,
                                  Function<InputCategory, List<String>> terms) {
        List<Hit> hits = new ArrayList<>();
        for (InputCategory c : active) {
            int best = Integer.MAX_VALUE;
            for (String term : terms.apply(c)) {
                if (term == null || term.isBlank()) {
                    continue;
                }
                Matcher m = termPattern(term).matcher(hay);
                if (m.find()) {
                    best = Math.min(best, m.start());
                }
            }
            if (best != Integer.MAX_VALUE) {
                hits.add(new Hit(c, best));
            }
        }
        return hits;
    }

    /** Whole word/phrase, case-insensitive, tolerating a plural "s"/"es" (and its absence for "Bills"). */
    private static Pattern termPattern(String term) {
        String t = term.trim().toLowerCase(Locale.ROOT);
        String stem = t.length() > 3 && t.endsWith("s") ? t.substring(0, t.length() - 1) : t;
        return Pattern.compile("(?<![\\p{L}\\p{N}])" + Pattern.quote(stem) + "(?:s|es)?(?![\\p{L}\\p{N}])");
    }

    private static InputCategory pick(List<Hit> hits, InputCategory llm) {
        if (hits.isEmpty()) {
            return null;
        }
        if (llm != null && hits.stream().anyMatch(h -> h.category().id() == llm.id())) {
            return llm;
        }
        return hits.stream().min(Comparator.comparingInt(Hit::position)).orElseThrow().category();
    }

    private static InputCategory byName(String name, List<InputCategory> active) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String n = name.trim();
        return active.stream().filter(c -> c.name().equalsIgnoreCase(n)).findFirst().orElse(null);
    }

    private static InputCategory fuzzyAlias(String hay, List<InputCategory> active) {
        List<String> tokens = List.of(hay.split("[^\\p{L}\\p{N}]+"));
        for (String token : tokens) {
            if (token.length() < FUZZY_MIN_LENGTH - 1) {
                continue;
            }
            for (InputCategory c : active) {
                for (String alias : c.aliases()) {
                    String a = alias.toLowerCase(Locale.ROOT);
                    if (a.length() >= FUZZY_MIN_LENGTH && !a.contains(" ") && withinOneEdit(token, a)) {
                        return c;
                    }
                }
            }
        }
        return null;
    }

    // ---------- merchant ----------

    /**
     * Trims, drops a "merchant" that is just a category name, snaps a one-typo match onto the user's alias
     * ("zommato" → "Zomato") and fixes casing.
     */
    public static String cleanMerchant(String raw, List<InputCategory> categories) {
        String m = blankToNull(raw);
        if (m == null) {
            return null;
        }
        m = m.replaceAll("^[\\p{Punct}\\s]+|[\\p{Punct}\\s]+$", "").replaceAll("\\s+", " ").trim();
        if (m.isEmpty()) {
            return null;
        }
        String finalM = m;
        if (categories.stream().anyMatch(c -> c.name().equalsIgnoreCase(finalM))) {
            return null;
        }
        String key = key(m);
        if (key.length() >= FUZZY_MIN_LENGTH) {
            for (InputCategory c : categories) {
                for (String alias : c.aliases()) {
                    String ak = key(alias);
                    if (!ak.equals(key) && ak.length() >= FUZZY_MIN_LENGTH && withinOneEdit(key, ak)) {
                        return truncate(casing(alias), MAX_MERCHANT);
                    }
                }
            }
        }
        return truncate(casing(m), MAX_MERCHANT);
    }

    private static String casing(String m) {
        String known = BRAND_CASING.get(key(m));
        if (known != null) {
            return known;
        }
        boolean mixed = !m.equals(m.toLowerCase(Locale.ROOT)) && !m.equals(m.toUpperCase(Locale.ROOT));
        if (mixed) {
            return m;
        }
        return List.of(m.toLowerCase(Locale.ROOT).split(" ")).stream()
                .map(w -> w.isEmpty() ? w : Character.toUpperCase(w.charAt(0)) + w.substring(1))
                .collect(Collectors.joining(" "));
    }

    private static String key(String s) {
        return s.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
    }

    /** Levenshtein distance ≤ 1. */
    static boolean withinOneEdit(String a, String b) {
        if (a.equals(b)) {
            return true;
        }
        int la = a.length();
        int lb = b.length();
        if (Math.abs(la - lb) > 1) {
            return false;
        }
        int i = 0;
        int j = 0;
        boolean edited = false;
        while (i < la && j < lb) {
            if (a.charAt(i) == b.charAt(j)) {
                i++;
                j++;
                continue;
            }
            if (edited) {
                return false;
            }
            edited = true;
            if (la > lb) {
                i++;
            } else if (lb > la) {
                j++;
            } else {
                i++;
                j++;
            }
        }
        return !(edited && (i < la || j < lb));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max).trim();
    }
}
