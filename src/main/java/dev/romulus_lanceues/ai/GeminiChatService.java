package dev.romulus_lanceues.ai;

import com.google.genai.Client;
import com.google.genai.errors.ApiException;
import com.google.genai.types.Content;
import com.google.genai.types.FinishReason;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.GenerateContentResponsePromptFeedback;
import com.google.genai.types.Part;
import dev.romulus_lanceues.config.GeminiConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class GeminiChatService implements ChatService {

    private static final Logger LOG = LoggerFactory.getLogger(GeminiChatService.class);

    static final String RATE_LIMIT_MESSAGE = "I'm receiving too many requests right now. Please wait a moment and try again.";
    static final String SAFETY_BLOCK_MESSAGE = "I'm unable to answer that because the response was flagged by safety guidelines.";
    static final String TIMEOUT_MESSAGE = "Request timed out while waiting for Gemini to respond. Please try again.";
    static final String GENERIC_ERROR_MESSAGE = "I encountered an unexpected issue while generating a response. Please try again later.";

    private final Client client;
    private final GeminiConfig config;
    private final Duration timeout;

    public GeminiChatService(Client client, GeminiConfig config, Duration timeout) {
        this.client = Objects.requireNonNull(client, "client must not be null");
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.timeout = Objects.requireNonNull(timeout, "timeout must not be null");
    }

    public GeminiChatService(Client client, GeminiConfig config) {
        this(client, config, Duration.ofSeconds(30));
    }

    @Override
    public CompletableFuture<String> ask(String prompt) {
        GenerateContentConfig genConfig = GenerateContentConfig.builder()
                .temperature(config.temperature())
                .topP(config.topP())
                .topK(config.topK())
                .maxOutputTokens(config.maxOutputTokens())
                .systemInstruction(Content.fromParts(Part.fromText(config.systemInstruction())))
                .build();

        return client.async.models.generateContent(config.model(), prompt, genConfig)
                .orTimeout(timeout.toSeconds(), TimeUnit.SECONDS)
                .handle((response, throwable) -> {
                    if (throwable != null) {
                        return handleException(throwable);
                    }
                    return handleResponse(response);
                });
    }

    private String handleResponse(GenerateContentResponse response) {
        if (response == null) {
            LOG.warn("Received null response from Gemini API");
            return GENERIC_ERROR_MESSAGE;
        }

        // Check if response contains safety flags in candidate finish reasons
        FinishReason finishReason = null;
        try {
            finishReason = response.finishReason();
        } catch (Exception e) {
            LOG.debug("Could not inspect response finishReason: {}", e.getMessage());
        }

        if (finishReason != null && isSafetyReason(finishReason.knownEnum())) {
            LOG.info("Gemini response finishReason flagged safety: {}", finishReason.knownEnum());
            return SAFETY_BLOCK_MESSAGE;
        }

        // Check if prompt feedback indicates a block
        if (response.promptFeedback().flatMap(GenerateContentResponsePromptFeedback::blockReason).isPresent()) {
            LOG.info("Gemini prompt feedback flagged block: {}", response.promptFeedback().get().blockReason().get());
            return SAFETY_BLOCK_MESSAGE;
        }

        try {
            String text = response.text();
            if (text == null || text.isBlank()) {
                LOG.warn("Gemini returned empty or blank text, flagging safety");
                return SAFETY_BLOCK_MESSAGE;
            }
            return text.strip();
        } catch (IllegalArgumentException e) {
            if (isSafetyMessage(e.getMessage())) {
                LOG.info("Gemini response extraction threw safety finish reason exception: {}", e.getMessage());
                return SAFETY_BLOCK_MESSAGE;
            }
            LOG.error("Failed to extract text from Gemini response", e);
            return GENERIC_ERROR_MESSAGE;
        } catch (Exception e) {
            LOG.error("Unexpected error extracting text from Gemini response", e);
            return GENERIC_ERROR_MESSAGE;
        }
    }

    private String handleException(Throwable throwable) {
        Throwable cause = unwrap(throwable);

        if (cause instanceof TimeoutException) {
            LOG.warn("Gemini request timed out after {} seconds", timeout.toSeconds());
            return TIMEOUT_MESSAGE;
        }

        if (cause instanceof ApiException apiEx) {
            if (apiEx.code() == 429) {
                LOG.warn("Gemini rate limit / quota exceeded (HTTP 429)");
                return RATE_LIMIT_MESSAGE;
            }
            String message = apiEx.message();
            if (message != null && isRateLimitMessage(message)) {
                LOG.warn("Gemini quota/rate limit error: {}", message);
                return RATE_LIMIT_MESSAGE;
            }
        }

        if (cause instanceof IllegalArgumentException && isSafetyMessage(cause.getMessage())) {
            LOG.info("Gemini response was flagged by safety guidelines: {}", cause.getMessage());
            return SAFETY_BLOCK_MESSAGE;
        }

        LOG.error("Unexpected error communicating with Gemini API", cause);
        return GENERIC_ERROR_MESSAGE;
    }

    private Throwable unwrap(Throwable throwable) {
        Throwable current = throwable;
        while ((current instanceof CompletionException || current instanceof ExecutionException) && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private boolean isSafetyReason(FinishReason.Known known) {
        if (known == null) {
            return false;
        }
        return known == FinishReason.Known.SAFETY
                || known == FinishReason.Known.BLOCKLIST
                || known == FinishReason.Known.PROHIBITED_CONTENT
                || known == FinishReason.Known.SPII
                || known == FinishReason.Known.IMAGE_SAFETY
                || known == FinishReason.Known.IMAGE_PROHIBITED_CONTENT;
    }

    private boolean isSafetyMessage(String message) {
        if (message == null) {
            return false;
        }
        String upper = message.toUpperCase();
        return upper.contains("SAFETY")
                || upper.contains("BLOCKLIST")
                || upper.contains("PROHIBITED_CONTENT")
                || upper.contains("SPII");
    }

    private boolean isRateLimitMessage(String message) {
        String lower = message.toLowerCase();
        return lower.contains("quota")
                || lower.contains("rate limit")
                || lower.contains("resource_exhausted")
                || lower.contains("too many requests");
    }
}
