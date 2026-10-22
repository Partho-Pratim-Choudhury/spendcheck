package com.parthopratim.spendcheck.input;

import static org.assertj.core.api.Assertions.assertThat;

import com.parthopratim.spendcheck.input.Interpreter.Interpretation;
import com.parthopratim.spendcheck.ledger.CategoryKind;
import com.parthopratim.spendcheck.ledger.NeedOrWant;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class InterpreterTest {

    static final LocalDate TODAY = LocalDate.of(2026, 10, 22);
    static final double THRESHOLD = 0.7;

    static final InputCategory FOOD = cat(1, "Food", CategoryKind.WANT, "swiggy", "zomato", "dinner", "chai");
    static final InputCategory GROCERIES = cat(2, "Groceries", CategoryKind.NEED, "dmart", "blinkit", "grocery");
    static final InputCategory SHOPPING = cat(3, "Shopping", CategoryKind.WANT, "amazon", "shoes");
    static final InputCategory TRAVEL = cat(4, "Travel", CategoryKind.NEED, "uber", "metro", "auto", "petrol");
    static final InputCategory BILLS = cat(5, "Bills", CategoryKind.NEED, "electricity", "recharge", "gas");
    static final InputCategory HEALTH = cat(6, "Health", CategoryKind.NEED, "medicine");
    static final InputCategory RENT = cat(7, "Rent", CategoryKind.FIXED, "rent");
    static final InputCategory EMI = cat(8, "EMI", CategoryKind.FIXED, "emi");
    static final InputCategory OLD = new InputCategory(9, "Old", CategoryKind.WANT, List.of("legacy"), true);
    static final List<InputCategory> CATS = List.of(FOOD, GROCERIES, SHOPPING, TRAVEL, BILLS, HEALTH, RENT, EMI, OLD);

    static InputCategory cat(long id, String name, CategoryKind kind, String... aliases) {
        return new InputCategory(id, name, kind, List.of(aliases), false);
    }

    static Extraction log(String amount, String category, String merchant, String date) {
        return new Extraction(InputIntent.LOG_EXPENSE, amount == null ? null : new BigDecimal(amount), category,
                merchant, null, date, null, null, null, 0.95);
    }

    static Interpretation run(Extraction e, String text) {
        return Interpreter.interpret(e, text, CATS, TODAY, THRESHOLD);
    }

    @Nested
    class Categories {

        @Test
        void nameInTextUsedWithoutValidLlmPick() {
            assertThat(Interpreter.resolveCategory("groceries from blinkit 860", null, null, CATS))
                    .isEqualTo(GROCERIES);
            assertThat(Interpreter.resolveCategory("paid rent 25000", null, null, CATS)).isEqualTo(RENT);
            assertThat(Interpreter.resolveCategory("electricity bill 900", null, "Nonsense", CATS)).isEqualTo(BILLS);
        }

        @Test
        void validLlmPickBeatsWordsInTheText() {
            // category names/aliases used in another sense
            assertThat(Interpreter.resolveCategory("paid the dinner bill 1200", null, "Food", CATS)).isEqualTo(FOOD);
            assertThat(Interpreter.resolveCategory("dog food 400", null, "Shopping", CATS)).isEqualTo(SHOPPING);
            assertThat(Interpreter.resolveCategory("amazon prime 1499", "Amazon Prime", "Bills", CATS))
                    .isEqualTo(BILLS);
            assertThat(Interpreter.resolveCategory("groceries 860", null, "Food", CATS)).isEqualTo(FOOD);
        }

        @Test
        void merchantThatIsAnAliasBeatsTheLlm() {
            assertThat(Interpreter.resolveCategory("swiggy 450", "Swiggy", "Groceries", CATS)).isEqualTo(FOOD);
            assertThat(Interpreter.resolveCategory("groceries from blinkit 860", "Blinkit", "Food", CATS))
                    .isEqualTo(GROCERIES);
            // an alias only inside the merchant's name is not the merchant
            assertThat(Interpreter.resolveCategory("amazon prime 1499", "Amazon Prime", "Bills", CATS))
                    .isEqualTo(BILLS);
        }

        @Test
        void spendUsesCleanedMerchantForTheMapping() {
            Interpretation in = run(log("450", "Groceries", "swigy", null), "swigy 450");
            assertThat(in.merchant()).isEqualTo("Swiggy");
            assertThat(in.category()).isEqualTo(FOOD);
            assertThat(run(log("1200", "Food", null, null), "paid the dinner bill 1200").category()).isEqualTo(FOOD);
        }

        @Test
        void aliasMatchesWholeWordsAndPlurals() {
            assertThat(Interpreter.resolveCategory("swiggy 450", null, null, CATS)).isEqualTo(FOOD);
            assertThat(Interpreter.resolveCategory("SWIGGY 450", null, null, CATS)).isEqualTo(FOOD);
            assertThat(Interpreter.resolveCategory("medicines 340", null, null, CATS)).isEqualTo(HEALTH);
            assertThat(Interpreter.resolveCategory("450", "Zomato", null, CATS)).isEqualTo(FOOD);
            // "emi" must not fire inside "emirates", nor "gas" inside "vegas"
            assertThat(Interpreter.resolveCategory("emirates lounge vegas 900", null, null, CATS)).isNull();
        }

        @Test
        void withoutLlmPickEarliestHitWins() {
            assertThat(Interpreter.resolveCategory("metro card recharge 500", null, "Bills", CATS)).isEqualTo(BILLS);
            assertThat(Interpreter.resolveCategory("metro card recharge 500", null, "Food", CATS)).isEqualTo(FOOD);
            assertThat(Interpreter.resolveCategory("metro card recharge 500", null, null, CATS)).isEqualTo(TRAVEL);
            assertThat(Interpreter.resolveCategory("recharge after metro 500", null, null, CATS)).isEqualTo(BILLS);
        }

        @Test
        void llmCategoryUsedWhenTextHasNoHint() {
            assertThat(Interpreter.resolveCategory("kiraya 25000 diya", null, "rent", CATS)).isEqualTo(RENT);
            assertThat(Interpreter.resolveCategory("headphones 1299", null, "Shopping", CATS)).isEqualTo(SHOPPING);
            assertThat(Interpreter.resolveCategory("thing 100", null, "Nonsense", CATS)).isNull();
        }

        @Test
        void archivedNeverChosen() {
            assertThat(Interpreter.resolveCategory("legacy 100", null, "Old", CATS)).isNull();
            assertThat(Interpreter.resolveCategory("old stuff 100", null, null, CATS)).isNull();
        }

        @Test
        void oneTypoAliasAsLastResort() {
            assertThat(Interpreter.resolveCategory("petrl 1000", null, null, CATS)).isEqualTo(TRAVEL);
            assertThat(Interpreter.resolveCategory("electricty 900", null, null, CATS)).isEqualTo(BILLS);
            assertThat(Interpreter.resolveCategory("petrl 1000", null, "Misc-not-there", CATS)).isEqualTo(TRAVEL);
        }
    }

    @Nested
    class Merchants {

        @Test
        void casingAndTypos() {
            assertThat(Interpreter.cleanMerchant("swigy", CATS)).isEqualTo("Swiggy");
            assertThat(Interpreter.cleanMerchant("zommato", CATS)).isEqualTo("Zomato");
            assertThat(Interpreter.cleanMerchant("swiggy", CATS)).isEqualTo("Swiggy");
            assertThat(Interpreter.cleanMerchant("dmart", CATS)).isEqualTo("DMart");
            assertThat(Interpreter.cleanMerchant("chai point", CATS)).isEqualTo("Chai Point");
            assertThat(Interpreter.cleanMerchant("BigBasket", CATS)).isEqualTo("BigBasket");
            assertThat(Interpreter.cleanMerchant("  uber.. ", CATS)).isEqualTo("Uber");
        }

        @Test
        void capitalsAsTypedAreKept() {
            assertThat(Interpreter.cleanMerchant("H&M", CATS)).isEqualTo("H&M");
            assertThat(Interpreter.cleanMerchant("BMW", CATS)).isEqualTo("BMW");
            assertThat(Interpreter.cleanMerchant("ITC", CATS)).isEqualTo("ITC");
            assertThat(Interpreter.cleanMerchant("SWIGGY", CATS)).isEqualTo("SWIGGY");
            assertThat(Interpreter.cleanMerchant("DMART", CATS)).isEqualTo("DMart");   // known casing still wins
            assertThat(Interpreter.cleanMerchant("third wave coffee", CATS)).isEqualTo("Third Wave Coffee");
        }

        @Test
        void onlyBrandAliasesAreSnapTargets() {
            // "diner" is one edit from the generic alias "dinner", "shoez" from "shoes": not snapped
            assertThat(Interpreter.cleanMerchant("diner", CATS)).isEqualTo("Diner");
            assertThat(Interpreter.cleanMerchant("shoez", CATS)).isEqualTo("Shoez");
            // four letters or fewer never snap ("ubar" is not Uber)
            assertThat(Interpreter.cleanMerchant("ubar", CATS)).isEqualTo("Ubar");
            assertThat(Interpreter.cleanMerchant("amazn", CATS)).isEqualTo("Amazon");
        }

        @Test
        void exactAliasIsNeverSnappedAway() {
            // the user's own alias "zomatos" is one edit from brand "zomato": the exact match stays
            List<InputCategory> cats = List.of(cat(1, "Food", CategoryKind.WANT, "zomato", "zomatos"));
            assertThat(Interpreter.cleanMerchant("zomatos", cats)).isEqualTo("Zomatos");
            assertThat(Interpreter.cleanMerchant("zomatoo", cats)).isEqualTo("Zomato");
        }

        @Test
        void blankOrCategoryNameIsNoMerchant() {
            assertThat(Interpreter.cleanMerchant(" ", CATS)).isNull();
            assertThat(Interpreter.cleanMerchant(null, CATS)).isNull();
            assertThat(Interpreter.cleanMerchant("groceries", CATS)).isNull();
        }
    }

    @Nested
    class Amounts {

        @ParameterizedTest
        @CsvSource({"450, 45000", "189.50, 18950", "0.005, 1", "0.004, 0", "1200, 120000", "10.125, 1013"})
        void paiseRoundHalfUp(String rupees, long paise) {
            assertThat(Interpreter.toPaise(new BigDecimal(rupees))).isEqualTo(paise);
        }

        @Test
        void zeroNegativeOrHugeAmountAsks() {
            for (String bad : List.of("0", "-5", "1000001", "99999999999999999999")) {
                Interpretation in = run(log(bad, "Food", null, null), "food " + bad);
                assertThat(in.followUp()).as(bad).isNotNull();
                assertThat(in.followUp().missing()).containsExactly("amount");
                assertThat(in.followUp().question()).contains("doesn't look right");
            }
            assertThat(run(log("1000000", "Food", null, null), "food").followUp()).isNull();
        }

        @Test
        void absurdMagnitudesAreRejectedCheaply() {
            assertThat(Interpreter.toPaise(new BigDecimal("1e999999999"))).isEqualTo(Long.MAX_VALUE);
            assertThat(Interpreter.toPaise(new BigDecimal("-1e999999999"))).isEqualTo(Long.MIN_VALUE);
            assertThat(Interpreter.toPaise(new BigDecimal("1e-999999999"))).isZero();
            assertThat(Interpreter.toPaise(new BigDecimal("100000001"))).isEqualTo(Long.MAX_VALUE);
            assertThat(Interpreter.toPaise(new BigDecimal("100000000"))).isEqualTo(10_000_000_000L);
            for (String bad : List.of("1e999999999", "1e-999999999")) {
                Interpretation in = run(log(bad, "Food", null, null), "food");
                assertThat(in.followUp().missing()).as(bad).containsExactly("amount");
            }
        }
    }

    @Nested
    class Dates {

        @Test
        void nullIsTodayAndRecentDatesPass() {
            assertThat(run(log("450", "Food", null, null), "swiggy 450").date()).isEqualTo(TODAY);
            assertThat(run(log("450", "Food", null, "2026-10-21"), "kal swiggy 450").date())
                    .isEqualTo(TODAY.minusDays(1));
            Interpretation edge = run(log("450", "Food", null, TODAY.minusDays(31).toString()), "x");
            assertThat(edge.followUp()).isNull();
        }

        @Test
        void futureTooOldOrUnreadableAsks() {
            Interpretation future = run(log("450", "Food", null, "2026-10-23"), "x");
            assertThat(future.followUp().missing()).containsExactly("date");
            assertThat(future.followUp().question()).contains("future");
            Interpretation old = run(log("450", "Food", null, TODAY.minusDays(32).toString()), "x");
            assertThat(old.followUp().question()).contains("last 31 days");
            assertThat(run(log("450", "Food", null, "last week"), "x").followUp().missing()).containsExactly("date");
        }

        @Test
        void preCheckIgnoresDate() {
            Extraction e = new Extraction(InputIntent.PRE_CHECK, new BigDecimal("3500"), "Shopping", null, null,
                    "2026-11-30", null, null, null, 0.9);
            Interpretation in = run(e, "can I buy shoes next month");
            assertThat(in.followUp()).isNull();
            assertThat(in.date()).isEqualTo(TODAY);
        }
    }

    @Nested
    class FollowUps {

        @Test
        void missingAmount() {
            Interpretation in = run(log(null, "Shopping", null, null), "bought shoes");
            assertThat(in.followUp().question()).isEqualTo("How much was it?");
            assertThat(in.followUp().missing()).containsExactly("amount");
            Extraction pre = new Extraction(InputIntent.PRE_CHECK, null, "Shopping", null, null, null, null, null,
                    null, 0.9);
            assertThat(run(pre, "can I buy shoes").followUp().question()).isEqualTo("How much does it cost?");
        }

        @Test
        void missingCategoryListsVariableCategoriesFirst() {
            Interpretation in = run(log("450", null, null, null), "450");
            assertThat(in.amountPaise()).isEqualTo(45_000);
            assertThat(in.followUp().question()).isEqualTo("Which category — Food, Groceries, Shopping, Travel…?");
            assertThat(in.followUp().missing()).containsExactly("category");
        }

        @Test
        void missingBoth() {
            Interpretation in = run(log(null, null, null, null), "spent");
            assertThat(in.followUp().missing()).containsExactly("amount", "category");
        }

        @Test
        void lowConfidenceAsksForConfirmation() {
            Extraction e = new Extraction(InputIntent.LOG_EXPENSE, new BigDecimal("450"), "Food", null, null, null,
                    null, null, null, 0.5);
            Interpretation in = run(e, "sw 450?");
            assertThat(in.followUp().missing()).containsExactly("confirmation");
            assertThat(in.followUp().question()).contains("₹450 · Food");
            Extraction sure = new Extraction(InputIntent.LOG_EXPENSE, new BigDecimal("450"), "Food", null, null, null,
                    NeedOrWant.WANT, null, null, 0.7);
            Interpretation ok = run(sure, "food 450 want");
            assertThat(ok.followUp()).isNull();
            assertThat(ok.needOrWant()).isEqualTo(NeedOrWant.WANT);
        }

        @Test
        void completeSpendHasNoFollowUp() {
            Interpretation in = run(log("450", null, "swigy", null), "swigy 450");
            assertThat(in.followUp()).isNull();
            assertThat(in.category()).isEqualTo(FOOD);
            assertThat(in.merchant()).isEqualTo("Swiggy");
            assertThat(in.amountPaise()).isEqualTo(45_000);
        }
    }

    @Nested
    class OtherIntents {

        @Test
        void queryCategoryResolvedOrAsked() {
            Extraction q = new Extraction(InputIntent.QUERY, null, null, null, null, null, null,
                    QueryType.CATEGORY_LEFT, "food", 0.9);
            assertThat(run(q, "how much left for food?").category()).isEqualTo(FOOD);
            Extraction noCat = new Extraction(InputIntent.QUERY, null, null, null, null, null, null,
                    QueryType.CATEGORY_LEFT, null, 0.9);
            assertThat(run(noCat, "how much left").followUp().missing()).containsExactly("category");
            Extraction total = new Extraction(InputIntent.QUERY, null, null, null, null, null, null,
                    QueryType.TOTAL_SPENT, null, 0.9);
            Interpretation t = run(total, "food total spent");
            assertThat(t.category()).isNull();
            assertThat(t.followUp()).isNull();
        }

        @Test
        void missingQueryTypeIsCategoryLeftForACategoryElseAsked() {
            Extraction e = new Extraction(InputIntent.QUERY, null, null, null, null, null, null, null, null, 0.9);
            assertThat(run(e, "travel?").queryType()).isEqualTo(QueryType.CATEGORY_LEFT);
            assertThat(run(e, "travel?").followUp()).isNull();
            Interpretation vague = run(e, "how much?");
            assertThat(vague.queryType()).isNull();
            assertThat(vague.followUp().question()).isEqualTo("What would you like to know — what's left in a "
                    + "category, total spent, or safe to spend today?");
            assertThat(vague.followUp().missing()).containsExactly("query");
        }

        @Test
        void unsureQueryAsks() {
            Extraction e = new Extraction(InputIntent.QUERY, null, null, null, null, null, null,
                    QueryType.TOTAL_SPENT, null, 0.4);
            Interpretation in = run(e, "spent?");
            assertThat(in.followUp().missing()).containsExactly("confirmation");
            assertThat(in.followUp().question()).contains("total spent");
            Extraction sure = new Extraction(InputIntent.QUERY, null, null, null, null, null, null,
                    QueryType.TOTAL_SPENT, null, 0.7);
            assertThat(run(sure, "spent?").followUp()).isNull();
        }

        @Test
        void undoNeedsConfidenceOtherNever() {
            Extraction sure = new Extraction(InputIntent.UNDO, null, null, null, null, null, null, null, null, 0.9);
            assertThat(run(sure, "undo").followUp()).isNull();
            Extraction unsure = new Extraction(InputIntent.UNDO, null, null, null, null, null, null, null, null, 0.3);
            assertThat(run(unsure, "remove?").followUp().missing()).containsExactly("confirmation");
            Extraction other = new Extraction(InputIntent.OTHER, null, null, null, null, null, null, null, null, 0.1);
            assertThat(run(other, "asdf").followUp()).isNull();
            Extraction none = new Extraction(null, null, null, null, null, null, null, null, null, null);
            assertThat(run(none, "?").intent()).isEqualTo(InputIntent.OTHER);
        }
    }

    @Test
    void oneEdit() {
        assertThat(Interpreter.withinOneEdit("swigy", "swiggy")).isTrue();
        assertThat(Interpreter.withinOneEdit("swiggy", "swigy")).isTrue();
        assertThat(Interpreter.withinOneEdit("zomatp", "zomato")).isTrue();
        assertThat(Interpreter.withinOneEdit("swgy", "swiggy")).isFalse();
        assertThat(Interpreter.withinOneEdit("ab", "ba")).isFalse();
    }
}
