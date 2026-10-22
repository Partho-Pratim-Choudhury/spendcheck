package com.parthopratim.spendcheck.input;

import java.util.List;

/** Transcribes a short voice note (an OpenAI call in production). */
public interface SpeechToText {

    /**
     * @param filename a name whose extension matches the audio format (the provider sniffs by extension)
     * @param hints    vocabulary that helps recognition, e.g. the user's merchant aliases
     * @throws com.parthopratim.spendcheck.common.ServiceUnavailableException not configured / no credits
     * @throws RuntimeException any other failure; callers map it to 502
     */
    String transcribe(byte[] audio, String filename, List<String> hints);

    String model();
}
