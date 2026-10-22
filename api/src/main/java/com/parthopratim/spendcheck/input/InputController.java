package com.parthopratim.spendcheck.input;

import com.parthopratim.spendcheck.auth.CurrentUser;
import com.parthopratim.spendcheck.common.FieldValidationException;
import com.parthopratim.spendcheck.input.InputDtos.InputResult;
import com.parthopratim.spendcheck.input.InputDtos.TextInputRequest;
import jakarta.validation.Valid;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/inputs")
public class InputController {

    public static final long MAX_AUDIO_BYTES = 2L * 1024 * 1024;

    /** Accepted audio content types → the file extension the speech-to-text provider expects. */
    private static final Map<String, String> AUDIO_TYPES = Map.ofEntries(
            Map.entry("audio/mp4", "m4a"), Map.entry("audio/m4a", "m4a"), Map.entry("audio/x-m4a", "m4a"),
            Map.entry("audio/aac", "aac"), Map.entry("audio/mpeg", "mp3"), Map.entry("audio/mp3", "mp3"),
            Map.entry("audio/x-mp3", "mp3"), Map.entry("audio/mpeg3", "mp3"), Map.entry("audio/x-mpeg-3", "mp3"),
            Map.entry("audio/webm", "webm"), Map.entry("audio/ogg", "ogg"), Map.entry("audio/wav", "wav"),
            Map.entry("audio/x-wav", "wav"), Map.entry("audio/wave", "wav"), Map.entry("audio/vnd.wave", "wav"),
            Map.entry("audio/flac", "flac"), Map.entry("audio/x-flac", "flac"));
    private static final Set<String> AUDIO_EXTENSIONS = Set.of("m4a", "mp4", "aac", "mp3", "mpeg", "mpga", "webm",
            "ogg", "oga", "wav", "flac");

    private final InputOrchestrator orchestrator;
    private final CurrentUser currentUser;

    public InputController(InputOrchestrator orchestrator, CurrentUser currentUser) {
        this.orchestrator = orchestrator;
        this.currentUser = currentUser;
    }

    @PostMapping("/text")
    public InputResult text(@Valid @RequestBody TextInputRequest req) {
        return orchestrator.text(currentUser.id(), req.text());
    }

    @PostMapping(path = "/voice", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public InputResult voice(@RequestPart("audio") MultipartFile audio) {
        if (audio.isEmpty()) {
            throw new FieldValidationException("audio", "must not be empty");
        }
        if (audio.getSize() > MAX_AUDIO_BYTES) {
            throw new FieldValidationException("audio", "must be at most 2 MB");
        }
        String filename = providerFilename(audio.getContentType(), audio.getOriginalFilename());
        try {
            return orchestrator.voice(currentUser.id(), audio.getBytes(), filename);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * {@code voice.<ext>} for a supported type. A type we don't know — none, {@code application/octet-stream} or an
     * unlisted {@code audio/*} — is accepted when the original filename has a known audio extension.
     *
     * @throws FieldValidationException unsupported type
     */
    static String providerFilename(String contentType, String originalFilename) {
        String type = contentType == null ? "" : contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        String ext = extension(originalFilename);
        String mapped = AUDIO_TYPES.get(type);
        if (mapped != null) {
            return "voice." + (ext != null && AUDIO_EXTENSIONS.contains(ext) ? ext : mapped);
        }
        boolean unknownType = type.isEmpty() || type.equals(MediaType.APPLICATION_OCTET_STREAM_VALUE)
                || type.startsWith("audio/");
        if (unknownType && ext != null && AUDIO_EXTENSIONS.contains(ext)) {
            return "voice." + ext;
        }
        throw new FieldValidationException("audio",
                "unsupported audio type; send m4a, aac, mp4, mp3, webm, ogg, wav or flac");
    }

    private static String extension(String filename) {
        if (filename == null) {
            return null;
        }
        int dot = filename.lastIndexOf('.');
        return dot < 0 || dot == filename.length() - 1 ? null
                : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
