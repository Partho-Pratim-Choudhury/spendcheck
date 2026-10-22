package com.parthopratim.spendcheck.input;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.parthopratim.spendcheck.common.ServiceUnavailableException;
import com.parthopratim.spendcheck.config.AppProperties;
import com.parthopratim.spendcheck.config.SeedRunner;
import com.parthopratim.spendcheck.input.Interpreter.Interpretation;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Runs {@code nlu-golden.jsonl} through the real {@link OpenAiExtractor} and the production {@link Interpreter}
 * against the seeded default categories, and writes {@code target/nlu-eval-report.md}. Costs real money: excluded
 * from the default build, run with {@code ./mvnw -Peval test}. The key comes from {@code OPENAI_API_KEY} or the
 * repo-root {@code .env}, like the app.
 */
@Tag("eval")
class NluEvalTest {

    private static final Path REPORT = Path.of("target", "nlu-eval-report.md");
    private static final int THREADS = 4;
    private static final double THRESHOLD = 0.7;

    private record Case(int line, LocalDate today, String text, JsonNode expected) {}

    private record Outcome(Case c, Interpretation in, long latencyMs, String error) {}

    @Test
    void goldenSet() throws Exception {
        Map<String, String> env = env();
        String key = env.get("OPENAI_API_KEY");
        Assumptions.assumeTrue(AiErrors.configured(key), "OPENAI_API_KEY not set (env or ../.env)");
        String model = env.getOrDefault("LLM_MODEL", "gpt-5.4-mini");
        AppProperties.Llm llm = new AppProperties.Llm(model, THRESHOLD, Duration.ofSeconds(30),
                env.getOrDefault("LLM_REASONING_EFFORT", "low"));
        Extractor extractor = new OpenAiExtractor(key, env.get("OPENAI_BASE_URL"), llm);
        List<InputCategory> cats = IntStream.range(0, SeedRunner.DEFAULT_CATEGORIES.size())
                .mapToObj(i -> {
                    SeedRunner.Seed s = SeedRunner.DEFAULT_CATEGORIES.get(i);
                    return new InputCategory(i + 1, s.name(), s.kind(), s.aliases(), false);
                }).toList();
        List<Case> cases = load();

        // One probe first: no credits / bad key should stop the run, not fail 147 times.
        Outcome probe = run(extractor, cats, cases.get(0));
        if (probe.error() != null && probe.error().startsWith("503")) {
            Files.createDirectories(REPORT.getParent());
            Files.writeString(REPORT, "# NLU eval — NOT RUN\n\nModel `" + model + "`: " + probe.error() + "\n");
            throw new AssertionError("Eval not run: " + probe.error());
        }

        List<Outcome> outcomes = new ArrayList<>();
        outcomes.add(probe);
        AtomicBoolean stop = new AtomicBoolean();
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        try {
            List<Future<Outcome>> futures = cases.subList(1, cases.size()).stream()
                    .map(c -> pool.submit(() -> stop.get() ? new Outcome(c, null, 0, "skipped") : run(extractor,
                            cats, c, stop)))
                    .toList();
            for (Future<Outcome> f : futures) {
                outcomes.add(f.get());
            }
        } finally {
            pool.shutdownNow();
        }
        outcomes.sort(Comparator.comparingInt(o -> o.c().line()));
        String report = report(model, outcomes);
        Files.createDirectories(REPORT.getParent());
        Files.writeString(REPORT, report);
        System.out.println(report.lines().limit(20).reduce("", (a, b) -> a + b + "\n"));
        assertThat(outcomes).hasSize(cases.size());
    }

    private static Outcome run(Extractor extractor, List<InputCategory> cats, Case c) {
        return run(extractor, cats, c, new AtomicBoolean());
    }

    private static Outcome run(Extractor extractor, List<InputCategory> cats, Case c, AtomicBoolean stop) {
        long start = System.nanoTime();
        try {
            Extraction e = extractor.extract(c.text(), c.today(), cats);
            long ms = (System.nanoTime() - start) / 1_000_000;
            return new Outcome(c, Interpreter.interpret(e, c.text(), cats, c.today(), THRESHOLD), ms, null);
        } catch (RuntimeException ex) {
            RuntimeException t = AiErrors.translate(ex, "LLM call failed: " + ex);
            if (t instanceof ServiceUnavailableException) {
                stop.set(true);
                return new Outcome(c, null, 0, "503 " + t.getMessage());
            }
            return new Outcome(c, null, 0, "502 " + t.getMessage());
        }
    }

    // ---------- scoring ----------

    private static final class Metric {
        int ok;
        int total;

        void add(boolean pass) {
            total++;
            if (pass) {
                ok++;
            }
        }

        String fmt() {
            return total == 0 ? "n/a" : String.format("%.1f%% (%d/%d)", ok * 100.0 / total, ok, total);
        }
    }

    private static String report(String model, List<Outcome> outcomes) {
        Metric intent = new Metric();
        Metric amount = new Metric();
        Metric category = new Metric();
        Metric date = new Metric();
        Metric merchant = new Metric();
        Metric needOrWant = new Metric();
        Metric queryType = new Metric();
        int fuPredicted = 0;
        int fuCorrect = 0;
        int fuExpected = 0;
        int fuFound = 0;
        int errors = 0;
        List<Long> latencies = new ArrayList<>();
        List<String> failures = new ArrayList<>();

        for (Outcome o : outcomes) {
            JsonNode x = o.c().expected();
            boolean expectFollowUp = x.path("followUp").asBoolean(false);
            if (expectFollowUp) {
                fuExpected++;
            }
            if (o.in() == null) {
                errors++;
                intent.add(false);
                failures.add(row(o, "error", "", o.error()));
                continue;
            }
            latencies.add(o.latencyMs());
            Interpretation in = o.in();
            String expIntent = x.path("intent").asText();
            boolean intentOk = expIntent.equals(in.intent().name());
            intent.add(intentOk);
            if (!intentOk) {
                failures.add(row(o, "intent", expIntent, in.intent().name()));
            }
            if (x.has("amountPaise")) {
                Long exp = x.get("amountPaise").isNull() ? null : x.get("amountPaise").asLong();
                boolean ok = Objects.equals(exp, in.amountPaise());
                amount.add(ok);
                if (!ok) {
                    failures.add(row(o, "amountPaise", String.valueOf(exp), String.valueOf(in.amountPaise())));
                }
            }
            if (x.has("category")) {
                String act = in.category() == null ? null : in.category().name();
                boolean ok = x.get("category").asText().equalsIgnoreCase(act);
                category.add(ok);
                if (!ok) {
                    failures.add(row(o, "category", x.get("category").asText(), String.valueOf(act)));
                }
            }
            if (x.has("date")) {
                String act = in.date() == null ? null : in.date().toString();
                boolean ok = x.get("date").asText().equals(act);
                date.add(ok);
                if (!ok) {
                    failures.add(row(o, "date", x.get("date").asText(), String.valueOf(act)));
                }
            }
            if (x.has("merchant")) {
                String exp = x.get("merchant").asText();
                boolean ok = in.merchant() != null && in.merchant().toLowerCase().contains(exp.toLowerCase());
                merchant.add(ok);
                if (!ok) {
                    failures.add(row(o, "merchant", exp, String.valueOf(in.merchant())));
                }
            }
            if (x.has("needOrWant")) {
                String act = in.needOrWant() == null ? null : in.needOrWant().name();
                boolean ok = x.get("needOrWant").asText().equals(act);
                needOrWant.add(ok);
                if (!ok) {
                    failures.add(row(o, "needOrWant", x.get("needOrWant").asText(), String.valueOf(act)));
                }
            }
            if (x.has("queryType")) {
                String act = in.queryType() == null ? null : in.queryType().name();
                boolean ok = x.get("queryType").asText().equals(act);
                queryType.add(ok);
                if (!ok) {
                    failures.add(row(o, "queryType", x.get("queryType").asText(), String.valueOf(act)));
                }
            }
            boolean gotFollowUp = in.followUp() != null;
            if (gotFollowUp) {
                fuPredicted++;
                if (expectFollowUp) {
                    fuCorrect++;
                } else {
                    failures.add(row(o, "unexpected followUp", "", in.followUp().question()));
                }
            }
            if (expectFollowUp) {
                if (gotFollowUp) {
                    fuFound++;
                } else {
                    failures.add(row(o, "missing followUp", "followUp", "none"));
                }
            }
        }

        latencies.sort(Comparator.naturalOrder());
        StringBuilder sb = new StringBuilder();
        sb.append("# NLU eval\n\n")
                .append("Model `").append(model).append("`, ").append(outcomes.size()).append(" cases, ")
                .append(errors).append(" errors. Confidence threshold ").append(THRESHOLD).append(".\n\n")
                .append("| Metric | Score |\n|---|---|\n")
                .append("| Intent accuracy | ").append(intent.fmt()).append(" |\n")
                .append("| Amount exact match | ").append(amount.fmt()).append(" |\n")
                .append("| Category accuracy | ").append(category.fmt()).append(" |\n")
                .append("| Date accuracy | ").append(date.fmt()).append(" |\n")
                .append("| Merchant match (contains, case-insensitive) | ").append(merchant.fmt()).append(" |\n")
                .append("| Need/want | ").append(needOrWant.fmt()).append(" |\n")
                .append("| Query type | ").append(queryType.fmt()).append(" |\n")
                .append("| Follow-up precision | ").append(ratio(fuCorrect, fuPredicted)).append(" |\n")
                .append("| Follow-up recall | ").append(ratio(fuFound, fuExpected)).append(" |\n")
                .append("| Latency p50 / p95 | ").append(pct(latencies, 50)).append(" ms / ")
                .append(pct(latencies, 95)).append(" ms |\n\n")
                .append("## Failures (").append(failures.size()).append(")\n\n");
        if (failures.isEmpty()) {
            sb.append("None.\n");
        } else {
            sb.append("| Line | Text | Field | Expected | Actual |\n|---|---|---|---|---|\n");
            failures.forEach(sb::append);
        }
        return sb.toString();
    }

    private static String row(Outcome o, String field, String expected, String actual) {
        return "| " + o.c().line() + " | " + cell(o.c().text()) + " | " + field + " | " + cell(expected) + " | "
                + cell(actual) + " |\n";
    }

    private static String cell(String s) {
        return s == null ? "" : s.replace("|", "\\|").replace("\n", " ");
    }

    private static String ratio(int ok, int total) {
        return total == 0 ? "n/a" : String.format("%.1f%% (%d/%d)", ok * 100.0 / total, ok, total);
    }

    private static long pct(List<Long> sorted, int p) {
        if (sorted.isEmpty()) {
            return 0;
        }
        int idx = (int) Math.ceil(p / 100.0 * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(sorted.size() - 1, idx)));
    }

    // ---------- inputs ----------

    private static List<Case> load() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        List<Case> cases = new ArrayList<>();
        try (InputStream in = NluEvalTest.class.getResourceAsStream("/nlu-golden.jsonl")) {
            Objects.requireNonNull(in, "nlu-golden.jsonl missing");
            List<String> lines = new String(in.readAllBytes(), StandardCharsets.UTF_8).lines().toList();
            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i).isBlank()) {
                    continue;
                }
                JsonNode n = mapper.readTree(lines.get(i));
                cases.add(new Case(i + 1, LocalDate.parse(n.get("today").asText()), n.get("text").asText(),
                        n.get("expected")));
            }
        }
        return cases;
    }

    /** Process env wins over {@code ../.env} then {@code .env} (the app's own lookup order, roughly). */
    private static Map<String, String> env() throws IOException {
        Map<String, String> out = new HashMap<>();
        for (Path p : List.of(Path.of(".env"), Path.of("..", ".env"))) {
            if (Files.isRegularFile(p)) {
                for (String line : Files.readAllLines(p)) {
                    String t = line.trim();
                    int eq = t.indexOf('=');
                    if (t.isEmpty() || t.startsWith("#") || eq <= 0) {
                        continue;
                    }
                    String v = t.substring(eq + 1).trim();
                    if (v.length() >= 2 && (v.startsWith("\"") && v.endsWith("\"")
                            || v.startsWith("'") && v.endsWith("'"))) {
                        v = v.substring(1, v.length() - 1);
                    }
                    out.put(t.substring(0, eq).trim(), v);
                }
            }
        }
        System.getenv().forEach((k, v) -> {
            if (v != null && !v.isBlank()) {
                out.put(k, v);
            }
        });
        return out;
    }
}
