package com.parthopratim.spendcheck.input;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.parthopratim.spendcheck.common.BadGatewayException;
import com.parthopratim.spendcheck.common.FieldValidationException;
import com.parthopratim.spendcheck.common.ServiceUnavailableException;
import com.parthopratim.spendcheck.config.AppProperties;
import com.parthopratim.spendcheck.ledger.CategoryKind;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.web.client.ResourceAccessException;

/** Error mapping, upload type rules and prompt rendering — no network. */
class AiPlumbingTest {

    static final AppProperties.Llm LLM = new AppProperties.Llm("gpt-5.4-mini", 0.7, Duration.ofSeconds(20), "low");
    static final AppProperties.Stt STT = new AppProperties.Stt("gpt-4o-mini-transcribe", Duration.ofSeconds(20));

    @Test
    void configuredKey() {
        assertThat(AiErrors.configured(null)).isFalse();
        assertThat(AiErrors.configured(" ")).isFalse();
        assertThat(AiErrors.configured("not-set")).isFalse();
        assertThat(AiErrors.configured("sk-abc")).isTrue();
    }

    @Test
    void translateProviderErrors() {
        assertThat(AiErrors.translate(new NonTransientAiException(
                "429 - {\"error\":{\"type\":\"insufficient_quota\",\"code\":\"credit_balance_exhausted\"}}"), "x"))
                .isInstanceOf(ServiceUnavailableException.class).hasMessage(AiErrors.NO_CREDITS);
        assertThat(AiErrors.translate(new NonTransientAiException("429 - {\"code\":\"rate_limit_exceeded\"}"), "x"))
                .isInstanceOf(BadGatewayException.class).hasMessage(AiErrors.BUSY);
        assertThat(AiErrors.translate(new NonTransientAiException("401 - {\"code\":\"invalid_api_key\"}"), "x"))
                .isInstanceOf(ServiceUnavailableException.class).hasMessage(AiErrors.KEY_REJECTED);
        assertThat(AiErrors.translate(new TransientAiException("503 - overloaded"), "boom"))
                .isInstanceOf(BadGatewayException.class).hasMessage("boom");
        assertThat(AiErrors.translate(new ResourceAccessException("Read timed out"), "boom"))
                .isInstanceOf(BadGatewayException.class).hasMessage("boom");
        ServiceUnavailableException same = new ServiceUnavailableException(AiErrors.NOT_CONFIGURED);
        assertThat(AiErrors.translate(same, "x")).isSameAs(same);
        // wrapped somewhere down the cause chain
        assertThat(AiErrors.translate(new RuntimeException("wrap", new NonTransientAiException(
                "429 - insufficient_quota")), "x")).isInstanceOf(ServiceUnavailableException.class);
    }

    @Test
    void unconfiguredClientsAnswer503WithoutCallingOut() {
        assertThatThrownBy(() -> new OpenAiExtractor("not-set", null, LLM).extract("hi", LocalDate.now(), List.of()))
                .isInstanceOf(ServiceUnavailableException.class).hasMessage(AiErrors.NOT_CONFIGURED);
        assertThatThrownBy(() -> new OpenAiSpeechToText("", null, STT).transcribe(new byte[1], "v.m4a", List.of()))
                .isInstanceOf(ServiceUnavailableException.class).hasMessage(AiErrors.NOT_CONFIGURED);
    }

    @Test
    void reasoningModelsGetNoTemperature() {
        assertThat(OpenAiExtractor.isReasoningModel("gpt-5.4-mini")).isTrue();
        assertThat(OpenAiExtractor.isReasoningModel("o4-mini")).isTrue();
        assertThat(OpenAiExtractor.isReasoningModel("gpt-5-chat-latest")).isFalse();
        assertThat(OpenAiExtractor.isReasoningModel("gpt-4.1-mini")).isFalse();
    }

    @Test
    void systemPromptHasDatesCategoriesAndNoPlaceholders() {
        OpenAiExtractor x = new OpenAiExtractor("not-set", null, LLM);
        String p = x.systemPrompt(LocalDate.of(2026, 10, 22), List.of(
                new InputCategory(1, "Food", CategoryKind.WANT, List.of("swiggy", "zomato"), false),
                new InputCategory(2, "Rent", CategoryKind.FIXED, List.of(), false)));
        assertThat(p).contains("Today is Thursday, 2026-10-22")
                .contains("- 2026-10-21 Wednesday (yesterday, kal)")
                .contains("- 2026-10-19 Monday")
                .contains("- Food (WANT): swiggy, zomato")
                .contains("- Rent (FIXED)")
                .contains("\"date\":\"2026-10-21\"");
        assertThat(Pattern.compile("\\{[a-zA-Z]+\\}").matcher(p).find()).as("unreplaced placeholder").isFalse();
    }

    @Test
    void sttPromptCarriesHints() {
        assertThat(OpenAiSpeechToText.prompt(List.of("swiggy", " dmart ", "swiggy")))
                .endsWith("Names that may come up: swiggy, dmart.");
    }

    @Test
    void audioTypes() {
        assertThat(InputController.providerFilename("audio/mp4", "voice.m4a")).isEqualTo("voice.m4a");
        assertThat(InputController.providerFilename("audio/mp4", null)).isEqualTo("voice.m4a");
        assertThat(InputController.providerFilename("audio/mpeg", "x")).isEqualTo("voice.mp3");
        assertThat(InputController.providerFilename("AUDIO/WEBM; codecs=opus", null)).isEqualTo("voice.webm");
        assertThat(InputController.providerFilename("application/octet-stream", "a.OGG")).isEqualTo("voice.ogg");
        assertThat(InputController.providerFilename(null, "a.wav")).isEqualTo("voice.wav");
        assertThatThrownBy(() -> InputController.providerFilename("application/octet-stream", "a.exe"))
                .isInstanceOf(FieldValidationException.class);
        assertThatThrownBy(() -> InputController.providerFilename("video/mp4", "a.mp4"))
                .isInstanceOf(FieldValidationException.class);
    }
}
