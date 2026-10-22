package com.parthopratim.spendcheck.input;

import com.parthopratim.spendcheck.check.CheckDtos.VerdictResponse;
import com.parthopratim.spendcheck.ledger.NeedOrWant;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class InputDtos {

    public static final int MAX_TEXT_LENGTH = 500;

    private InputDtos() {
    }

    public record TextInputRequest(
            @NotNull @Size(min = 1, max = MAX_TEXT_LENGTH)
            @Pattern(regexp = "(?s).*\\S.*", message = "must not be blank") String text) {}

    /** API.md "Text / voice input". Exactly the fields the app reads; absent parts are null. */
    /** @param undoneTransactionId deprecated, always null: UNDO only proposes an {@code undoCandidate} */
    public record InputResult(
            long rawInputId, InputIntent intent, String transcript, Draft draft, VerdictResponse verdict,
            Answer answer, UndoCandidate undoCandidate, UUID undoneTransactionId, FollowUp followUp) {

        static InputResult followUp(long rawInputId, InputIntent intent, String transcript, FollowUp followUp) {
            return new InputResult(rawInputId, intent, transcript, null, null, null, null, null, followUp);
        }

        static InputResult answer(long rawInputId, InputIntent intent, String transcript, String text) {
            return new InputResult(rawInputId, intent, transcript, null, null, new Answer(text), null, null, null);
        }
    }

    /** The transaction UNDO proposes to remove; the app confirms with {@code DELETE /transactions/{id}}. */
    public record UndoCandidate(UUID transactionId, long amountPaise, String categoryName, String merchant,
                                Instant spentAt) {}

    /** A spend the app shows for confirmation; never saved by {@code /inputs/*}. */
    public record Draft(
            long amountPaise, long categoryId, String categoryName, String merchant, String note, Instant spentAt,
            NeedOrWant needOrWant, After after) {}

    /** The draft's category as it would stand after this spend (spent includes the draft's amount). */
    public record After(long categorySpentPaise, long categoryLimitPaise) {}

    public record Answer(String text) {}

    /** @param missing the fields still needed: amount, category, date, or confirmation when unsure */
    public record FollowUp(String question, List<String> missing) {
        public FollowUp {
            missing = List.copyOf(missing);
        }
    }
}
