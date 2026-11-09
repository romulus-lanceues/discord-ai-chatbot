package dev.romulus_lanceues.ai;

import com.google.genai.AsyncModels;
import com.google.genai.Client;
import com.google.genai.errors.ApiException;
import com.google.genai.types.FinishReason;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.GenerateContentResponsePromptFeedback;
import dev.romulus_lanceues.config.GeminiConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class GeminiChatServiceTest {

    private Client client;
    private AsyncModels asyncModels;
    private GeminiConfig config;
    private GeminiChatService chatService;

    @BeforeEach
    void setUp() {
        client = mock(Client.class);
        Client.Async async = mock(Client.Async.class);
        asyncModels = mock(AsyncModels.class);

        // Client fields are public final, so Mockito reflection or direct assignment works
        try {
            var asyncField = Client.class.getField("async");
            asyncField.setAccessible(true);
            // On a mock, public fields might be null unless stubbed or set
            var fieldSetter = Client.class.getDeclaredField("async");
            fieldSetter.setAccessible(true);
            fieldSetter.set(client, async);

            var modelsField = Client.Async.class.getDeclaredField("models");
            modelsField.setAccessible(true);
            modelsField.set(async, asyncModels);
        } catch (Exception e) {
            throw new RuntimeException("Failed to inject mocked async fields", e);
        }

        config = new GeminiConfig(
                "fake-api-key",
                "gemini-2.5-flash",
                0.7f,
                0.95f,
                40.0f,
                2048,
                "Test instruction"
        );
        chatService = new GeminiChatService(client, config, Duration.ofSeconds(5));
    }

    @Test
    @DisplayName("Returns generated text on successful response")
    void returnsGeneratedTextOnSuccess() {
        GenerateContentResponse response = mock(GenerateContentResponse.class);
        when(response.finishReason()).thenReturn(new FinishReason(FinishReason.Known.STOP));
        when(response.promptFeedback()).thenReturn(Optional.empty());
        when(response.text()).thenReturn("Hello! This is Cloud.");

        when(asyncModels.generateContent(eq("gemini-2.5-flash"), eq("Hi"), any(GenerateContentConfig.class)))
                .thenReturn(CompletableFuture.completedFuture(response));

        String result = chatService.ask("Hi").join();
        assertEquals("Hello! This is Cloud.", result);
    }

    @Test
    @DisplayName("Returns friendly message when response is flagged by safety guidelines")
    void handlesSafetyBlock() {
        GenerateContentResponse response = mock(GenerateContentResponse.class);
        when(response.finishReason()).thenReturn(new FinishReason(FinishReason.Known.SAFETY));
        when(response.promptFeedback()).thenReturn(Optional.empty());

        when(asyncModels.generateContent(anyString(), anyString(), any(GenerateContentConfig.class)))
                .thenReturn(CompletableFuture.completedFuture(response));

        String result = chatService.ask("Unsafe prompt").join();
        assertEquals("I'm unable to answer that because the response was flagged by safety guidelines.", result);
    }

    @Test
    @DisplayName("Returns friendly message when response text throws safety IllegalArgumentException")
    void handlesSafetyExceptionFromTextExtraction() {
        GenerateContentResponse response = mock(GenerateContentResponse.class);
        when(response.finishReason()).thenReturn(null);
        when(response.promptFeedback()).thenReturn(Optional.empty());
        when(response.text()).thenThrow(new IllegalArgumentException("The response finished unexpectedly with reason SAFETY."));

        when(asyncModels.generateContent(anyString(), anyString(), any(GenerateContentConfig.class)))
                .thenReturn(CompletableFuture.completedFuture(response));

        String result = chatService.ask("Unsafe prompt").join();
        assertEquals("I'm unable to answer that because the response was flagged by safety guidelines.", result);
    }

    @Test
    @DisplayName("Returns rate limit friendly message when 429 ApiException occurs")
    void handlesRateLimitException() {
        ApiException apiException = new ApiException(429, "RESOURCE_EXHAUSTED", "Quota exceeded");
        CompletableFuture<GenerateContentResponse> failedFuture = CompletableFuture.failedFuture(apiException);

        when(asyncModels.generateContent(anyString(), anyString(), any(GenerateContentConfig.class)))
                .thenReturn(failedFuture);

        String result = chatService.ask("Any prompt").join();
        assertEquals("I'm receiving too many requests right now. Please wait a moment and try again.", result);
    }

    @Test
    @DisplayName("Returns timeout friendly message when request times out")
    void handlesTimeoutException() {
        CompletableFuture<GenerateContentResponse> failedFuture = CompletableFuture.failedFuture(new TimeoutException("Timed out"));

        when(asyncModels.generateContent(anyString(), anyString(), any(GenerateContentConfig.class)))
                .thenReturn(failedFuture);

        String result = chatService.ask("Slow prompt").join();
        assertEquals("Request timed out while waiting for Gemini to respond. Please try again.", result);
    }

    @Test
    @DisplayName("Returns generic error friendly message on unexpected exception")
    void handlesUnexpectedException() {
        CompletableFuture<GenerateContentResponse> failedFuture = CompletableFuture.failedFuture(new RuntimeException("Network down"));

        when(asyncModels.generateContent(anyString(), anyString(), any(GenerateContentConfig.class)))
                .thenReturn(failedFuture);

        String result = chatService.ask("Fail prompt").join();
        assertEquals("I encountered an unexpected issue while generating a response. Please try again later.", result);
    }

    @Test
    @DisplayName("Returns friendly safety message when response text is empty or blank")
    void handlesEmptyOrBlankResponseText() {
        GenerateContentResponse response = mock(GenerateContentResponse.class);
        when(response.finishReason()).thenReturn(null);
        when(response.promptFeedback()).thenReturn(Optional.empty());
        when(response.text()).thenReturn("   ");

        when(asyncModels.generateContent(anyString(), anyString(), any(GenerateContentConfig.class)))
                .thenReturn(CompletableFuture.completedFuture(response));

        String result = chatService.ask("Prompt that gives blank").join();
        assertEquals("I'm unable to answer that because the response was flagged by safety guidelines.", result);
    }

    @Test
    @DisplayName("Returns friendly safety message when response text is null")
    void handlesNullResponseText() {
        GenerateContentResponse response = mock(GenerateContentResponse.class);
        when(response.finishReason()).thenReturn(null);
        when(response.promptFeedback()).thenReturn(Optional.empty());
        when(response.text()).thenReturn(null);

        when(asyncModels.generateContent(anyString(), anyString(), any(GenerateContentConfig.class)))
                .thenReturn(CompletableFuture.completedFuture(response));

        String result = chatService.ask("Prompt that gives null text").join();
        assertEquals("I'm unable to answer that because the response was flagged by safety guidelines.", result);
    }

    @Test
    @DisplayName("Returns generic error when response object itself is null")
    void handlesNullResponseObject() {
        when(asyncModels.generateContent(anyString(), anyString(), any(GenerateContentConfig.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        String result = chatService.ask("Null response").join();
        assertEquals("I encountered an unexpected issue while generating a response. Please try again later.", result);
    }

    @Test
    @DisplayName("Returns rate limit friendly message when ApiException message mentions quota")
    void handlesQuotaMentionInApiExceptionMessage() {
        ApiException apiException = new ApiException(403, "RESOURCE_EXHAUSTED", "quota exceeded for quota metric");
        CompletableFuture<GenerateContentResponse> failedFuture = CompletableFuture.failedFuture(apiException);

        when(asyncModels.generateContent(anyString(), anyString(), any(GenerateContentConfig.class)))
                .thenReturn(failedFuture);

        String result = chatService.ask("Quota prompt").join();
        assertEquals("I'm receiving too many requests right now. Please wait a moment and try again.", result);
    }

    @Test
    @DisplayName("Returns friendly safety message for various safety finish reasons like BLOCKLIST")
    void handlesBlocklistFinishReason() {
        GenerateContentResponse response = mock(GenerateContentResponse.class);
        when(response.finishReason()).thenReturn(new FinishReason(FinishReason.Known.BLOCKLIST));
        when(response.promptFeedback()).thenReturn(Optional.empty());

        when(asyncModels.generateContent(anyString(), anyString(), any(GenerateContentConfig.class)))
                .thenReturn(CompletableFuture.completedFuture(response));

        String result = chatService.ask("Blocklisted").join();
        assertEquals("I'm unable to answer that because the response was flagged by safety guidelines.", result);
    }

    @Test
    @DisplayName("Supports 2-arg constructor with default 30-second timeout")
    void supportsDefaultConstructor() {
        GeminiChatService defaultService = new GeminiChatService(client, config);
        GenerateContentResponse response = mock(GenerateContentResponse.class);
        when(response.finishReason()).thenReturn(new FinishReason(FinishReason.Known.STOP));
        when(response.promptFeedback()).thenReturn(Optional.empty());
        when(response.text()).thenReturn("Default timeout response");

        when(asyncModels.generateContent(anyString(), anyString(), any(GenerateContentConfig.class)))
                .thenReturn(CompletableFuture.completedFuture(response));

        String result = defaultService.ask("Hi").join();
        assertEquals("Default timeout response", result);
    }
}
