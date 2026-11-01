package dev.romulus_lanceues.config;

import java.util.Objects;
import java.util.function.Function;

public record GeminiConfig(
        String apiKey,
        String model,
        float temperature,
        float topP,
        float topK,
        int maxOutputTokens,
        String systemInstruction
) {

    public static final String DEFAULT_MODEL = "gemini-2.5-flash";
    public static final float DEFAULT_TEMPERATURE = 0.7f;
    public static final float DEFAULT_TOP_P = 0.95f;
    public static final float DEFAULT_TOP_K = 40.0f;
    public static final int DEFAULT_MAX_OUTPUT_TOKENS = 2048;
    public static final String DEFAULT_SYSTEM_INSTRUCTION =
            "You are Cloud, a helpful, friendly, and knowledgeable Discord assistant. " +
            "Provide clear, concise, and well-structured responses formatted nicely using Discord markdown.";

    public static GeminiConfig from(Function<String, String> lookup) {
        Objects.requireNonNull(lookup, "lookup must not be null");

        String rawApiKey = lookup.apply("GEMINI_API_KEY");
        if (rawApiKey == null || rawApiKey.isBlank()) {
            rawApiKey = lookup.apply("GOOGLE_API_KEY");
        }
        if (rawApiKey == null || rawApiKey.isBlank()) {
            throw new IllegalStateException(
                    "Missing required environment variable: 'GEMINI_API_KEY' (or 'GOOGLE_API_KEY'). Set it in your environment or .env file."
            );
        }

        String rawModel = lookup.apply("GEMINI_MODEL");
        String model = (rawModel == null || rawModel.isBlank()) ? DEFAULT_MODEL : rawModel.strip();

        float temperature = parseFloat(lookup, "GEMINI_TEMPERATURE", DEFAULT_TEMPERATURE);
        if (temperature < 0.0f || temperature > 2.0f) {
            throw new IllegalStateException("Invalid value for 'GEMINI_TEMPERATURE': expected value between 0.0 and 2.0, got " + temperature);
        }

        float topP = parseFloat(lookup, "GEMINI_TOP_P", DEFAULT_TOP_P);
        if (topP < 0.0f || topP > 1.0f) {
            throw new IllegalStateException("Invalid value for 'GEMINI_TOP_P': expected value between 0.0 and 1.0, got " + topP);
        }

        float topK = parseFloat(lookup, "GEMINI_TOP_K", DEFAULT_TOP_K);
        if (topK <= 0.0f) {
            throw new IllegalStateException("Invalid value for 'GEMINI_TOP_K': expected positive float, got " + topK);
        }

        int maxOutputTokens = parseInteger(lookup, "GEMINI_MAX_OUTPUT_TOKENS", DEFAULT_MAX_OUTPUT_TOKENS);
        if (maxOutputTokens <= 0) {
            throw new IllegalStateException("Invalid value for 'GEMINI_MAX_OUTPUT_TOKENS': expected positive integer, got " + maxOutputTokens);
        }

        String rawInstruction = lookup.apply("GEMINI_SYSTEM_INSTRUCTION");
        String systemInstruction = (rawInstruction == null || rawInstruction.isBlank()) ? DEFAULT_SYSTEM_INSTRUCTION : rawInstruction.strip();

        return new GeminiConfig(rawApiKey.strip(), model, temperature, topP, topK, maxOutputTokens, systemInstruction);
    }

    private static float parseFloat(Function<String, String> lookup, String varName, float defaultValue) {
        String raw = lookup.apply(varName);
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        try {
            return Float.parseFloat(raw.strip());
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Invalid format for environment variable '" + varName + "': expected Float, got '" + raw + "'.", e);
        }
    }

    private static int parseInteger(Function<String, String> lookup, String varName, int defaultValue) {
        String raw = lookup.apply(varName);
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(raw.strip());
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Invalid format for environment variable '" + varName + "': expected Integer, got '" + raw + "'.", e);
        }
    }

    @Override
    public String toString() {
        return "GeminiConfig[apiKey=***, model=" + model + ", temperature=" + temperature
                + ", topP=" + topP + ", topK=" + topK + ", maxOutputTokens=" + maxOutputTokens
                + ", systemInstruction=[persona length=" + systemInstruction.length() + "]]";
    }
}
