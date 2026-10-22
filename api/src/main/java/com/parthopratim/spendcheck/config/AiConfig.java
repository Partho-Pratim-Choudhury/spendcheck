package com.parthopratim.spendcheck.config;

import com.parthopratim.spendcheck.input.Extractor;
import com.parthopratim.spendcheck.input.OpenAiExtractor;
import com.parthopratim.spendcheck.input.OpenAiSpeechToText;
import com.parthopratim.spendcheck.input.SpeechToText;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The NLU pipeline's OpenAI clients. Built here (not from Spring AI's auto-configured models) so they get our own
 * timeouts, a short retry, and no default temperature (gpt-5* models reject one). Without a real
 * {@code OPENAI_API_KEY} they still exist and answer every call with 503.
 */
@Configuration
public class AiConfig {

    @Bean
    Extractor extractor(AppProperties props, @Value("${spring.ai.openai.api-key:}") String apiKey,
                        @Value("${spring.ai.openai.base-url:}") String baseUrl) {
        return new OpenAiExtractor(apiKey, baseUrl, props.llm());
    }

    @Bean
    SpeechToText speechToText(AppProperties props, @Value("${spring.ai.openai.api-key:}") String apiKey,
                              @Value("${spring.ai.openai.base-url:}") String baseUrl) {
        return new OpenAiSpeechToText(apiKey, baseUrl, props.stt());
    }
}
