package com.parthopratim.spendcheck.input;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.parthopratim.spendcheck.common.ServiceUnavailableException;
import com.parthopratim.spendcheck.config.AppProperties;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.openai.api.ResponseFormat;
import org.springframework.core.io.ClassPathResource;

/**
 * {@link Extractor} on OpenAI chat completions with a strict JSON-schema response. The prompt lives in
 * {@code prompts/extract.st}, the schema in {@code prompts/extract-schema.json}. Never logs the user's text.
 */
public class OpenAiExtractor implements Extractor {

    private static final int CALENDAR_DAYS = 8;

    private final String model;
    private final String template;
    private final OpenAiChatOptions options;
    private final OpenAiChatModel chatModel;   // null when no API key is configured
    private final ObjectMapper mapper = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL)
            .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
            .build();

    public OpenAiExtractor(String apiKey, String baseUrl, AppProperties.Llm llm) {
        this.model = llm.model();
        this.template = resource("prompts/extract.st");
        OpenAiChatOptions.Builder b = OpenAiChatOptions.builder()
                .model(model)
                .responseFormat(ResponseFormat.builder()
                        .type(ResponseFormat.Type.JSON_SCHEMA)
                        .jsonSchema(ResponseFormat.JsonSchema.builder()
                                .name("extraction")
                                .schema(resource("prompts/extract-schema.json"))
                                .strict(true)
                                .build())
                        .build());
        if (isReasoningModel(model)) {
            // gpt-5* / o* reject any temperature but the default; steer with reasoning effort instead.
            if (llm.reasoningEffort() != null && !llm.reasoningEffort().isBlank()) {
                b.reasoningEffort(llm.reasoningEffort());
            }
        } else {
            b.temperature(0.0);
        }
        this.options = b.build();
        this.chatModel = AiErrors.configured(apiKey)
                ? OpenAiChatModel.builder()
                .openAiApi(OpenAiApi.builder()
                        .apiKey(apiKey)
                        .baseUrl(OpenAiHttp.baseUrl(baseUrl))
                        .restClientBuilder(OpenAiHttp.restClient(llm.timeout()))
                        .build())
                .defaultOptions(options)
                .retryTemplate(OpenAiHttp.retry())
                .build()
                : null;
    }

    /** Models that only accept the default temperature (OpenAI reasoning models). */
    static boolean isReasoningModel(String model) {
        String m = model == null ? "" : model.toLowerCase(Locale.ROOT);
        return (m.startsWith("gpt-5") && !m.contains("-chat")) || m.matches("^o\\d.*");
    }

    @Override
    public Extraction extract(String text, LocalDate today, List<InputCategory> categories) {
        if (chatModel == null) {
            throw new ServiceUnavailableException(AiErrors.NOT_CONFIGURED);
        }
        Prompt prompt = new Prompt(List.of(new SystemMessage(systemPrompt(today, categories)), new UserMessage(text)),
                options);
        ChatResponse response = chatModel.call(prompt);
        String json = response == null || response.getResult() == null ? null
                : response.getResult().getOutput().getText();
        if (json == null || json.isBlank()) {
            throw new IllegalStateException("LLM returned no content");
        }
        try {
            return mapper.readValue(json, Extraction.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("LLM returned malformed JSON", e);
        }
    }

    @Override
    public String model() {
        return model;
    }

    /** The system prompt for {@code today}: dates, the user's categories and the rules/examples. */
    String systemPrompt(LocalDate today, List<InputCategory> categories) {
        StringBuilder calendar = new StringBuilder();
        for (int i = 0; i < CALENDAR_DAYS; i++) {
            LocalDate d = today.minusDays(i);
            calendar.append("- ").append(d).append(' ').append(weekday(d.getDayOfWeek()))
                    .append(i == 0 ? " (today, aaj)" : i == 1 ? " (yesterday, kal)"
                            : i == 2 ? " (day before yesterday, parso)" : "")
                    .append('\n');
        }
        String cats = categories.stream()
                .map(c -> "- " + c.name() + " (" + c.kind() + ")" + (c.aliases().isEmpty() ? ""
                        : ": " + String.join(", ", c.aliases())))
                .collect(Collectors.joining("\n"));
        return template
                .replace("{today}", today.toString())
                .replace("{weekday}", weekday(today.getDayOfWeek()))
                .replace("{yesterday}", today.minusDays(1).toString())
                .replace("{dayBeforeYesterday}", today.minusDays(2).toString())
                .replace("{calendar}", calendar.toString().stripTrailing())
                .replace("{categories}", cats);
    }

    private static String weekday(DayOfWeek d) {
        return d.getDisplayName(TextStyle.FULL, Locale.ENGLISH);
    }

    private static String resource(String path) {
        try (var in = new ClassPathResource(path).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Missing classpath resource " + path, e);
        }
    }
}
