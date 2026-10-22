package com.parthopratim.spendcheck.input;

import com.parthopratim.spendcheck.common.ServiceUnavailableException;
import com.parthopratim.spendcheck.config.AppProperties;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.ai.audio.transcription.AudioTranscriptionResponse;
import org.springframework.ai.openai.OpenAiAudioTranscriptionModel;
import org.springframework.ai.openai.OpenAiAudioTranscriptionOptions;
import org.springframework.ai.openai.api.OpenAiAudioApi;
import org.springframework.core.io.ByteArrayResource;

/** {@link SpeechToText} on OpenAI's transcription endpoint, English hint plus the user's merchant names. */
public class OpenAiSpeechToText implements SpeechToText {

    private static final int MAX_HINTS = 60;

    private final String model;
    private final OpenAiAudioTranscriptionModel transcriber;   // null when no API key is configured

    public OpenAiSpeechToText(String apiKey, String baseUrl, AppProperties.Stt stt) {
        this.model = stt.model();
        this.transcriber = AiErrors.configured(apiKey)
                ? new OpenAiAudioTranscriptionModel(
                OpenAiAudioApi.builder()
                        .apiKey(apiKey)
                        .baseUrl(OpenAiHttp.baseUrl(baseUrl))
                        .restClientBuilder(OpenAiHttp.restClient(stt.timeout()))
                        .build(),
                OpenAiAudioTranscriptionOptions.builder()
                        .model(model)
                        .language("en")
                        .responseFormat(OpenAiAudioApi.TranscriptResponseFormat.JSON)
                        .temperature(0f)
                        .build(),
                OpenAiHttp.retry())
                : null;
    }

    @Override
    public String transcribe(byte[] audio, String filename, List<String> hints) {
        if (transcriber == null) {
            throw new ServiceUnavailableException(AiErrors.NOT_CONFIGURED);
        }
        ByteArrayResource resource = new ByteArrayResource(audio) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
        OpenAiAudioTranscriptionOptions options = OpenAiAudioTranscriptionOptions.builder()
                .model(model)
                .language("en")
                .responseFormat(OpenAiAudioApi.TranscriptResponseFormat.JSON)
                .temperature(0f)
                .prompt(prompt(hints))
                .build();
        AudioTranscriptionResponse response = transcriber.call(new AudioTranscriptionPrompt(resource, options));
        if (response == null || response.getResult() == null) {
            throw new IllegalStateException("Transcription returned no result");
        }
        String text = response.getResult().getOutput();
        return text == null ? "" : text.trim();
    }

    @Override
    public String model() {
        return model;
    }

    static String prompt(List<String> hints) {
        Set<String> names = new LinkedHashSet<>();
        for (String h : hints) {
            if (h != null && !h.isBlank() && names.size() < MAX_HINTS) {
                names.add(h.trim());
            }
        }
        return "A short Indian English or Hinglish voice note about money, like \"Swiggy 450\", "
                + "\"kal DMart pe 2340\" or \"can I buy shoes for 3500\". Amounts are in rupees."
                + (names.isEmpty() ? "" : " Names that may come up: " + String.join(", ", names) + ".");
    }
}
