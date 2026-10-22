package com.parthopratim.spendcheck;

import com.parthopratim.spendcheck.input.Extraction;
import com.parthopratim.spendcheck.input.Extractor;
import com.parthopratim.spendcheck.input.InputCategory;
import com.parthopratim.spendcheck.input.SpeechToText;
import java.time.LocalDate;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Programmable stand-ins for the OpenAI beans: a test scripts the next response or failure. Unscripted calls go to
 * the real beans, which (with the test profile's placeholder key) answer 503 — no network call is ever made.
 */
@TestConfiguration(proxyBeanMethods = false)
public class ScriptedAi {

    @Bean
    @Primary
    ScriptedExtractor scriptedExtractor(@Qualifier("extractor") Extractor real) {
        return new ScriptedExtractor(real);
    }

    @Bean
    @Primary
    ScriptedSpeechToText scriptedSpeechToText(@Qualifier("speechToText") SpeechToText real) {
        return new ScriptedSpeechToText(real);
    }

    public static class ScriptedExtractor implements Extractor {
        private final Extractor real;
        private Extraction next;
        private RuntimeException failure;
        public volatile String lastText;
        public volatile LocalDate lastToday;
        public volatile List<InputCategory> lastCategories;

        ScriptedExtractor(Extractor real) {
            this.real = real;
        }

        public void respond(Extraction e) {
            this.next = e;
            this.failure = null;
        }

        public void fail(RuntimeException e) {
            this.failure = e;
            this.next = null;
        }

        public void reset() {
            next = null;
            failure = null;
            lastText = null;
            lastToday = null;
            lastCategories = null;
        }

        @Override
        public Extraction extract(String text, LocalDate today, List<InputCategory> categories) {
            lastText = text;
            lastToday = today;
            lastCategories = categories;
            if (failure != null) {
                throw failure;
            }
            return next != null ? next : real.extract(text, today, categories);
        }

        @Override
        public String model() {
            return "scripted-model";
        }
    }

    public static class ScriptedSpeechToText implements SpeechToText {
        private final SpeechToText real;
        private String next;
        private RuntimeException failure;
        public volatile String lastFilename;
        public volatile List<String> lastHints;
        public volatile int lastSize;

        ScriptedSpeechToText(SpeechToText real) {
            this.real = real;
        }

        public void respond(String transcript) {
            this.next = transcript;
            this.failure = null;
        }

        public void fail(RuntimeException e) {
            this.failure = e;
            this.next = null;
        }

        public void reset() {
            next = null;
            failure = null;
            lastFilename = null;
            lastHints = null;
            lastSize = 0;
        }

        @Override
        public String transcribe(byte[] audio, String filename, List<String> hints) {
            lastFilename = filename;
            lastHints = hints;
            lastSize = audio.length;
            if (failure != null) {
                throw failure;
            }
            return next != null ? next : real.transcribe(audio, filename, hints);
        }

        @Override
        public String model() {
            return "scripted-stt";
        }
    }
}
