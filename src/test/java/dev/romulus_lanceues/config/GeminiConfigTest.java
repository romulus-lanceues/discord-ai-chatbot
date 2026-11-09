package dev.romulus_lanceues.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeminiConfigTest {

    @Test
    @DisplayName("Loads GEMINI_API_KEY when present")
    void loadsGeminiApiKeyWhenPresent() {
        Map<String, String> env = Map.of("GEMINI_API_KEY", "gemini-key-123");
        GeminiConfig config = GeminiConfig.from(env::get);

        assertEquals("gemini-key-123", config.apiKey());
    }

    @Test
    @DisplayName("Falls back to GOOGLE_API_KEY when GEMINI_API_KEY is missing")
    void fallsBackToGoogleApiKeyWhenGeminiApiKeyMissing() {
        Map<String, String> env = Map.of("GOOGLE_API_KEY", "google-key-456");
        GeminiConfig config = GeminiConfig.from(env::get);

        assertEquals("google-key-456", config.apiKey());
    }

    @Test
    @DisplayName("Prefers GEMINI_API_KEY over GOOGLE_API_KEY when both are present")
    void prefersGeminiApiKeyOverGoogleApiKeyWhenBothPresent() {
        Map<String, String> env = Map.of(
                "GEMINI_API_KEY", "gemini-key-primary",
                "GOOGLE_API_KEY", "google-key-fallback"
        );
        GeminiConfig config = GeminiConfig.from(env::get);

        assertEquals("gemini-key-primary", config.apiKey());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\t\n  "})
    @DisplayName("Falls back to GOOGLE_API_KEY when GEMINI_API_KEY is empty or blank")
    void fallsBackToGoogleApiKeyWhenGeminiApiKeyIsBlank(String blankGeminiKey) {
        Map<String, String> env = Map.of(
                "GEMINI_API_KEY", blankGeminiKey,
                "GOOGLE_API_KEY", "google-key-fallback"
        );
        GeminiConfig config = GeminiConfig.from(env::get);

        assertEquals("google-key-fallback", config.apiKey());
    }

    @Test
    @DisplayName("Strips whitespace around GEMINI_API_KEY and GOOGLE_API_KEY")
    void stripsWhitespaceAroundApiKeys() {
        GeminiConfig config1 = GeminiConfig.from(Map.of("GEMINI_API_KEY", "  gemini-stripped-key  ")::get);
        assertEquals("gemini-stripped-key", config1.apiKey());

        GeminiConfig config2 = GeminiConfig.from(Map.of("GOOGLE_API_KEY", "  google-stripped-key  ")::get);
        assertEquals("google-stripped-key", config2.apiKey());
    }

    @Test
    @DisplayName("Throws IllegalStateException when both API keys are missing")
    void throwsWhenBothApiKeysMissing() {
        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> GeminiConfig.from(key -> null)
        );

        assertEquals(
                "Missing required environment variable: 'GEMINI_API_KEY' (or 'GOOGLE_API_KEY'). Set it in your environment or .env file.",
                ex.getMessage()
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\t\n  "})
    @DisplayName("Throws IllegalStateException when API key is empty or blank")
    void throwsWhenApiKeyEmptyOrBlank(String blankKey) {
        Map<String, String> env = Map.of("GEMINI_API_KEY", blankKey);
        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> GeminiConfig.from(env::get)
        );

        assertEquals(
                "Missing required environment variable: 'GEMINI_API_KEY' (or 'GOOGLE_API_KEY'). Set it in your environment or .env file.",
                ex.getMessage()
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\t\n  "})
    @DisplayName("Throws IllegalStateException when GOOGLE_API_KEY is empty or blank and GEMINI_API_KEY is missing")
    void throwsWhenGoogleApiKeyEmptyOrBlankAndGeminiMissing(String blankKey) {
        Map<String, String> env = Map.of("GOOGLE_API_KEY", blankKey);
        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> GeminiConfig.from(env::get)
        );

        assertEquals(
                "Missing required environment variable: 'GEMINI_API_KEY' (or 'GOOGLE_API_KEY'). Set it in your environment or .env file.",
                ex.getMessage()
        );
    }

    @Test
    @DisplayName("Throws IllegalStateException when both GEMINI_API_KEY and GOOGLE_API_KEY are blank")
    void throwsWhenBothKeysAreBlank() {
        Map<String, String> env = Map.of(
                "GEMINI_API_KEY", "   ",
                "GOOGLE_API_KEY", " \t\n "
        );
        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> GeminiConfig.from(env::get)
        );

        assertEquals(
                "Missing required environment variable: 'GEMINI_API_KEY' (or 'GOOGLE_API_KEY'). Set it in your environment or .env file.",
                ex.getMessage()
        );
    }

    @Test
    @DisplayName("Throws NullPointerException when lookup is null")
    void throwsWhenLookupIsNull() {
        NullPointerException ex = assertThrows(
                NullPointerException.class,
                () -> GeminiConfig.from(null)
        );

        assertEquals("lookup must not be null", ex.getMessage());
    }

    @Test
    @DisplayName("Applies default model, hyperparameters, and Cloud persona")
    void appliesDefaultHyperparametersAndCloudPersona() {
        Map<String, String> env = Map.of("GEMINI_API_KEY", "test-key");
        GeminiConfig config = GeminiConfig.from(env::get);

        assertEquals(GeminiConfig.DEFAULT_MODEL, config.model());
        assertEquals(GeminiConfig.DEFAULT_TEMPERATURE, config.temperature());
        assertEquals(GeminiConfig.DEFAULT_TOP_P, config.topP());
        assertEquals(GeminiConfig.DEFAULT_TOP_K, config.topK());
        assertEquals(GeminiConfig.DEFAULT_MAX_OUTPUT_TOKENS, config.maxOutputTokens());
        assertEquals(GeminiConfig.DEFAULT_SYSTEM_INSTRUCTION, config.systemInstruction());
    }

    @Test
    @DisplayName("Loads custom model and strips whitespace")
    void loadsCustomModelWhenSpecified() {
        Map<String, String> env = Map.of(
                "GEMINI_API_KEY", "test-key",
                "GEMINI_MODEL", "  gemini-1.5-pro  "
        );
        GeminiConfig config = GeminiConfig.from(env::get);

        assertEquals("gemini-1.5-pro", config.model());
    }

    @Test
    @DisplayName("Parses valid custom hyperparameters and custom system instruction")
    void parsesValidCustomHyperparameters() {
        Map<String, String> env = Map.of(
                "GEMINI_API_KEY", "test-key",
                "GEMINI_TEMPERATURE", "1.2",
                "GEMINI_TOP_P", "0.8",
                "GEMINI_TOP_K", "20.5",
                "GEMINI_MAX_OUTPUT_TOKENS", "4096",
                "GEMINI_SYSTEM_INSTRUCTION", "Custom system instruction"
        );
        GeminiConfig config = GeminiConfig.from(env::get);

        assertEquals(1.2f, config.temperature());
        assertEquals(0.8f, config.topP());
        assertEquals(20.5f, config.topK());
        assertEquals(4096, config.maxOutputTokens());
        assertEquals("Custom system instruction", config.systemInstruction());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\t\n  "})
    @DisplayName("Blank or whitespace-only custom settings fall back to default values")
    void blankCustomSettingsFallBackToDefaults(String blankValue) {
        Map<String, String> env = Map.of(
                "GEMINI_API_KEY", "test-key",
                "GEMINI_MODEL", blankValue,
                "GEMINI_TEMPERATURE", blankValue,
                "GEMINI_TOP_P", blankValue,
                "GEMINI_TOP_K", blankValue,
                "GEMINI_MAX_OUTPUT_TOKENS", blankValue,
                "GEMINI_SYSTEM_INSTRUCTION", blankValue
        );
        GeminiConfig config = GeminiConfig.from(env::get);

        assertEquals(GeminiConfig.DEFAULT_MODEL, config.model());
        assertEquals(GeminiConfig.DEFAULT_TEMPERATURE, config.temperature());
        assertEquals(GeminiConfig.DEFAULT_TOP_P, config.topP());
        assertEquals(GeminiConfig.DEFAULT_TOP_K, config.topK());
        assertEquals(GeminiConfig.DEFAULT_MAX_OUTPUT_TOKENS, config.maxOutputTokens());
        assertEquals(GeminiConfig.DEFAULT_SYSTEM_INSTRUCTION, config.systemInstruction());
    }

    @Test
    @DisplayName("Trims whitespace around custom parameters")
    void trimsWhitespaceAroundCustomParameters() {
        Map<String, String> env = Map.of(
                "GEMINI_API_KEY", "test-key",
                "GEMINI_MODEL", "  custom-model  ",
                "GEMINI_TEMPERATURE", "  1.5  ",
                "GEMINI_TOP_P", "  0.7  ",
                "GEMINI_TOP_K", "  35.0  ",
                "GEMINI_MAX_OUTPUT_TOKENS", "  1000  ",
                "GEMINI_SYSTEM_INSTRUCTION", "  Custom Persona  "
        );
        GeminiConfig config = GeminiConfig.from(env::get);

        assertEquals("custom-model", config.model());
        assertEquals(1.5f, config.temperature());
        assertEquals(0.7f, config.topP());
        assertEquals(35.0f, config.topK());
        assertEquals(1000, config.maxOutputTokens());
        assertEquals("Custom Persona", config.systemInstruction());
    }

    @Test
    @DisplayName("Throws IllegalStateException when GEMINI_TEMPERATURE is not a valid float")
    void throwsWhenTemperatureNotNumeric() {
        Map<String, String> env = Map.of(
                "GEMINI_API_KEY", "test-key",
                "GEMINI_TEMPERATURE", "invalid-float"
        );
        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> GeminiConfig.from(env::get)
        );

        assertEquals(
                "Invalid format for environment variable 'GEMINI_TEMPERATURE': expected Float, got 'invalid-float'.",
                ex.getMessage()
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"-0.1", "-1.0"})
    @DisplayName("Throws IllegalStateException when GEMINI_TEMPERATURE is less than 0.0")
    void throwsWhenTemperatureOutOfRangeLow(String invalidTemp) {
        Map<String, String> env = Map.of(
                "GEMINI_API_KEY", "test-key",
                "GEMINI_TEMPERATURE", invalidTemp
        );
        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> GeminiConfig.from(env::get)
        );

        assertTrue(ex.getMessage().startsWith("Invalid value for 'GEMINI_TEMPERATURE': expected value between 0.0 and 2.0"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"2.01", "3.0"})
    @DisplayName("Throws IllegalStateException when GEMINI_TEMPERATURE is greater than 2.0")
    void throwsWhenTemperatureOutOfRangeHigh(String invalidTemp) {
        Map<String, String> env = Map.of(
                "GEMINI_API_KEY", "test-key",
                "GEMINI_TEMPERATURE", invalidTemp
        );
        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> GeminiConfig.from(env::get)
        );

        assertTrue(ex.getMessage().startsWith("Invalid value for 'GEMINI_TEMPERATURE': expected value between 0.0 and 2.0"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.0", "2.0"})
    @DisplayName("Accepts exact boundary values for GEMINI_TEMPERATURE (0.0 and 2.0)")
    void acceptsBoundaryValuesForTemperature(String boundaryTemp) {
        Map<String, String> env = Map.of(
                "GEMINI_API_KEY", "test-key",
                "GEMINI_TEMPERATURE", boundaryTemp
        );
        GeminiConfig config = GeminiConfig.from(env::get);

        assertEquals(Float.parseFloat(boundaryTemp), config.temperature());
    }

    @Test
    @DisplayName("Throws IllegalStateException when GEMINI_TOP_P is not a valid float")
    void throwsWhenTopPNotNumeric() {
        Map<String, String> env = Map.of(
                "GEMINI_API_KEY", "test-key",
                "GEMINI_TOP_P", "not-a-number"
        );
        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> GeminiConfig.from(env::get)
        );

        assertEquals(
                "Invalid format for environment variable 'GEMINI_TOP_P': expected Float, got 'not-a-number'.",
                ex.getMessage()
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"-0.01", "1.01"})
    @DisplayName("Throws IllegalStateException when GEMINI_TOP_P is outside [0.0, 1.0]")
    void throwsWhenTopPOutOfRange(String invalidTopP) {
        Map<String, String> env = Map.of(
                "GEMINI_API_KEY", "test-key",
                "GEMINI_TOP_P", invalidTopP
        );
        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> GeminiConfig.from(env::get)
        );

        assertTrue(ex.getMessage().startsWith("Invalid value for 'GEMINI_TOP_P': expected value between 0.0 and 1.0"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.0", "1.0"})
    @DisplayName("Accepts boundary values for GEMINI_TOP_P (0.0 and 1.0)")
    void acceptsBoundaryValuesForTopP(String boundaryTopP) {
        Map<String, String> env = Map.of(
                "GEMINI_API_KEY", "test-key",
                "GEMINI_TOP_P", boundaryTopP
        );
        GeminiConfig config = GeminiConfig.from(env::get);

        assertEquals(Float.parseFloat(boundaryTopP), config.topP());
    }

    @Test
    @DisplayName("Throws IllegalStateException when GEMINI_TOP_K is not a valid float")
    void throwsWhenTopKNotNumeric() {
        Map<String, String> env = Map.of(
                "GEMINI_API_KEY", "test-key",
                "GEMINI_TOP_K", "not-a-float"
        );
        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> GeminiConfig.from(env::get)
        );

        assertEquals(
                "Invalid format for environment variable 'GEMINI_TOP_K': expected Float, got 'not-a-float'.",
                ex.getMessage()
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.0", "-5.0"})
    @DisplayName("Throws IllegalStateException when GEMINI_TOP_K is non-positive")
    void throwsWhenTopKNonPositive(String invalidTopK) {
        Map<String, String> env = Map.of(
                "GEMINI_API_KEY", "test-key",
                "GEMINI_TOP_K", invalidTopK
        );
        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> GeminiConfig.from(env::get)
        );

        assertTrue(ex.getMessage().startsWith("Invalid value for 'GEMINI_TOP_K': expected positive float"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0.001", "1.0", "50.0"})
    @DisplayName("Accepts positive float values for GEMINI_TOP_K (> 0.0)")
    void acceptsPositiveValuesForTopK(String validVal) {
        Map<String, String> env = Map.of(
                "GEMINI_API_KEY", "test-key",
                "GEMINI_TOP_K", validVal
        );
        GeminiConfig config = GeminiConfig.from(env::get);

        assertEquals(Float.parseFloat(validVal), config.topK());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-100"})
    @DisplayName("Throws IllegalStateException when GEMINI_MAX_OUTPUT_TOKENS is non-positive")
    void throwsWhenMaxOutputTokensNonPositive(String invalidMaxTokens) {
        Map<String, String> env = Map.of(
                "GEMINI_API_KEY", "test-key",
                "GEMINI_MAX_OUTPUT_TOKENS", invalidMaxTokens
        );
        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> GeminiConfig.from(env::get)
        );

        assertTrue(ex.getMessage().startsWith("Invalid value for 'GEMINI_MAX_OUTPUT_TOKENS': expected positive integer"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1", "2048", "8192"})
    @DisplayName("Accepts positive integer values for GEMINI_MAX_OUTPUT_TOKENS (> 0)")
    void acceptsPositiveValuesForMaxOutputTokens(String validVal) {
        Map<String, String> env = Map.of(
                "GEMINI_API_KEY", "test-key",
                "GEMINI_MAX_OUTPUT_TOKENS", validVal
        );
        GeminiConfig config = GeminiConfig.from(env::get);

        assertEquals(Integer.parseInt(validVal), config.maxOutputTokens());
    }

    @Test
    @DisplayName("Throws IllegalStateException when GEMINI_MAX_OUTPUT_TOKENS is not an integer")
    void throwsWhenMaxOutputTokensNotNumeric() {
        Map<String, String> env = Map.of(
                "GEMINI_API_KEY", "test-key",
                "GEMINI_MAX_OUTPUT_TOKENS", "abc"
        );
        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> GeminiConfig.from(env::get)
        );

        assertEquals(
                "Invalid format for environment variable 'GEMINI_MAX_OUTPUT_TOKENS': expected Integer, got 'abc'.",
                ex.getMessage()
        );
    }

    @Test
    @DisplayName("Masks apiKey in toString() and does not leak secret")
    void masksApiKeyInToStringAndDoesNotLeakSecret() {
        Map<String, String> env = Map.of("GEMINI_API_KEY", "very-secret-gemini-key");
        GeminiConfig config = GeminiConfig.from(env::get);

        String str = config.toString();
        assertTrue(str.contains("apiKey=***"));
        assertFalse(str.contains("very-secret-gemini-key"));
        assertTrue(str.contains("persona length=" + GeminiConfig.DEFAULT_SYSTEM_INSTRUCTION.length()));
    }

    @Test
    @DisplayName("Secrets do not leak into exception error messages when parameter validation fails")
    void secretsDoNotLeakIntoExceptionMessages() {
        String secret = "super-confidential-api-key-99999";
        Map<String, String> env = Map.of(
                "GEMINI_API_KEY", secret,
                "GEMINI_TEMPERATURE", "invalid-temp"
        );
        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> GeminiConfig.from(env::get)
        );
        assertFalse(ex.getMessage().contains(secret));
    }

    @Test
    @DisplayName("Record equality and hashCode adhere to contract")
    void recordEqualityAndHashCode() {
        GeminiConfig g1 = new GeminiConfig("k1", "m1", 0.7f, 0.95f, 40.0f, 2048, "persona");
        GeminiConfig g2 = new GeminiConfig("k1", "m1", 0.7f, 0.95f, 40.0f, 2048, "persona");
        GeminiConfig g3 = new GeminiConfig("k2", "m1", 0.7f, 0.95f, 40.0f, 2048, "persona");

        assertEquals(g1, g2);
        assertEquals(g1.hashCode(), g2.hashCode());
        assertNotEquals(g1, g3);
    }
}
