package com.parthopratim.spendcheck.input;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.parthopratim.spendcheck.auth.UserTime;
import com.parthopratim.spendcheck.check.CheckDtos.VerdictResponse;
import com.parthopratim.spendcheck.check.CheckService;
import com.parthopratim.spendcheck.common.NoActivePlanException;
import com.parthopratim.spendcheck.config.AppProperties;
import com.parthopratim.spendcheck.engine.Inr;
import com.parthopratim.spendcheck.input.InputDtos.Answer;
import com.parthopratim.spendcheck.input.InputDtos.Draft;
import com.parthopratim.spendcheck.input.InputDtos.InputResult;
import com.parthopratim.spendcheck.input.InputDtos.UndoCandidate;
import com.parthopratim.spendcheck.input.Interpreter.Interpretation;
import com.parthopratim.spendcheck.ledger.CategoryRepository;
import com.parthopratim.spendcheck.ledger.TransactionDtos.TransactionResponse;
import com.parthopratim.spendcheck.ledger.TransactionService;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * The text/voice pipeline: (speech-to-text →) LLM extraction → deterministic {@link Interpreter} → intent
 * handling. Every input is stored in {@code raw_inputs}. Provider calls run outside any DB transaction. Nothing is
 * written here but the raw input: drafts are confirmed, and UNDO candidates removed, by the app.
 */
@Service
public class InputOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(InputOrchestrator.class);

    static final Duration UNDO_WINDOW = Duration.ofHours(24);
    static final String NOTHING_TO_UNDO = "Nothing recent to undo.";
    static final String NO_PLAN_CHECK = "You don't have a budget plan yet — set one up and I can check spends "
            + "against it. You can still log this one.";
    static final String NOTHING_HEARD = "I couldn't hear anything in that voice note — try again?";
    static final String HELP = "I can log a spend (“Swiggy 450”, “kal DMart 2340”), check one before you buy "
            + "(“Can I buy shoes for 3500?”), answer questions (“How much is left for food?”, “How much can I "
            + "spend today?”) and undo your last entry (“undo”).";

    private final Extractor extractor;
    private final SpeechToText speechToText;
    private final RawInputRepository rawInputs;
    private final CategoryRepository categories;
    private final CheckService checks;
    private final TransactionService transactions;
    private final InputAnswers answers;
    private final UserTime userTime;
    private final AppProperties props;
    private final ObjectMapper json;

    public InputOrchestrator(Extractor extractor, SpeechToText speechToText, RawInputRepository rawInputs,
                             CategoryRepository categories, CheckService checks, TransactionService transactions,
                             InputAnswers answers, UserTime userTime, AppProperties props, ObjectMapper json) {
        this.extractor = extractor;
        this.speechToText = speechToText;
        this.rawInputs = rawInputs;
        this.categories = categories;
        this.checks = checks;
        this.transactions = transactions;
        this.answers = answers;
        this.userTime = userTime;
        this.props = props;
        this.json = json;
    }

    public InputResult text(long userId, String text) {
        return understand(userId, RawInputType.TEXT, text.trim(), null, 0);
    }

    /** @param filename carries the audio format to the provider (validated by the controller) */
    public InputResult voice(long userId, byte[] audio, String filename) {
        List<InputCategory> cats = categories(userId);
        List<String> hints = new ArrayList<>();
        cats.stream().filter(c -> !c.archived()).forEach(c -> hints.addAll(c.aliases()));
        long start = System.nanoTime();
        String transcript;
        try {
            transcript = speechToText.transcribe(audio, filename, hints);
        } catch (RuntimeException e) {
            rawInputs.save(new RawInput(userId, RawInputType.VOICE, null, null));
            throw AiErrors.translate(e, AiErrors.TRANSCRIBE_FAILED);
        }
        long sttMillis = millisSince(start);
        if (transcript == null || transcript.isBlank()) {
            RawInput raw = new RawInput(userId, RawInputType.VOICE, null, "");
            raw.recordLlm(null, speechToText.model(), (int) sttMillis);
            raw = rawInputs.save(raw);
            return InputResult.answer(raw.getId(), InputIntent.OTHER, "", NOTHING_HEARD);
        }
        return understand(userId, RawInputType.VOICE, transcript.trim(), cats, sttMillis);
    }

    private InputResult understand(long userId, RawInputType type, String text, List<InputCategory> cached,
                                   long sttMillis) {
        List<InputCategory> cats = cached != null ? cached : categories(userId);
        List<InputCategory> active = cats.stream().filter(c -> !c.archived()).toList();
        ZoneId zone = userTime.zone(userId);
        LocalDate today = userTime.today(zone);
        String transcript = type == RawInputType.VOICE ? text : null;
        RawInput raw = new RawInput(userId, type, type == RawInputType.TEXT ? text : null, transcript);

        long start = System.nanoTime();
        Extraction extraction;
        try {
            extraction = extractor.extract(text, today, active);
        } catch (RuntimeException e) {
            rawInputs.save(raw);
            throw AiErrors.translate(e, AiErrors.EXTRACT_FAILED);
        }
        long latency = sttMillis + millisSince(start);
        raw.recordLlm(toJson(extraction), extractor.model(), (int) Math.min(Integer.MAX_VALUE, latency));
        raw = rawInputs.save(raw);

        Interpretation in = Interpreter.interpret(extraction, text, cats, today, props.llm().confidenceThreshold());
        log.info("Input id={} user={} type={} intent={} followUp={} latencyMs={}", raw.getId(), userId, type,
                in.intent(), in.followUp() != null, latency);
        return handle(userId, raw.getId(), transcript, in, zone, today);
    }

    private InputResult handle(long userId, long rawId, String transcript, Interpretation in, ZoneId zone,
                               LocalDate today) {
        if (in.followUp() != null) {
            return InputResult.followUp(rawId, in.intent(), transcript, in.followUp());
        }
        return switch (in.intent()) {
            case LOG_EXPENSE -> new InputResult(rawId, in.intent(), transcript, draft(userId, in, zone, today), null,
                    null, null, null, null);
            case PRE_CHECK -> preCheck(userId, rawId, transcript, in, zone, today);
            case QUERY -> InputResult.answer(rawId, in.intent(), transcript,
                    answers.answer(userId, in.queryType(), in.category()));
            case UNDO -> undo(userId, rawId, transcript);
            case OTHER -> InputResult.answer(rawId, in.intent(), transcript, HELP);
        };
    }

    private InputResult preCheck(long userId, long rawId, String transcript, Interpretation in, ZoneId zone,
                                 LocalDate today) {
        Draft draft = draft(userId, in, zone, today);
        try {
            VerdictResponse verdict = checks.check(userId, in.amountPaise(), in.category().id());
            return new InputResult(rawId, in.intent(), transcript, draft, verdict, null, null, null, null);
        } catch (NoActivePlanException noPlan) {
            return new InputResult(rawId, in.intent(), transcript, draft, null, new Answer(NO_PLAN_CHECK), null,
                    null, null);
        }
    }

    /** Proposes, never cancels: the most recent chat-created spend of the last 24 h, for the app to confirm. */
    private InputResult undo(long userId, long rawId, String transcript) {
        Optional<TransactionResponse> latest = transactions.latestUndoable(userId, userTime.now().minus(UNDO_WINDOW));
        if (latest.isEmpty()) {
            return InputResult.answer(rawId, InputIntent.UNDO, transcript, NOTHING_TO_UNDO);
        }
        TransactionResponse t = latest.get();
        String text = "Remove " + Inr.format(t.amountPaise()) + " · " + t.categoryName()
                + (t.merchant() != null ? " · " + t.merchant() : "") + "?";
        UndoCandidate candidate = new UndoCandidate(t.id(), t.amountPaise(), t.categoryName(), t.merchant(),
                t.spentAt());
        return new InputResult(rawId, InputIntent.UNDO, transcript, null, null, new Answer(text), candidate, null,
                null);
    }

    /** Today's spends are stamped "now"; earlier days at noon in the user's tz. */
    private Draft draft(long userId, Interpretation in, ZoneId zone, LocalDate today) {
        LocalDate date = in.date() != null ? in.date() : today;
        Instant spentAt = date.equals(today) ? userTime.now() : date.atTime(LocalTime.NOON).atZone(zone).toInstant();
        InputCategory c = in.category();
        return new Draft(in.amountPaise(), c.id(), c.name(), in.merchant(), in.note(), spentAt,
                in.needOrWant(), answers.after(userId, c, in.amountPaise(), date));
    }

    private List<InputCategory> categories(long userId) {
        return categories.findByUserIdOrderByIdAsc(userId).stream().map(InputCategory::from).toList();
    }

    private String toJson(Extraction e) {
        try {
            return json.writeValueAsString(e);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Extraction not serialisable", ex);
        }
    }

    private static long millisSince(long startNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
    }
}
