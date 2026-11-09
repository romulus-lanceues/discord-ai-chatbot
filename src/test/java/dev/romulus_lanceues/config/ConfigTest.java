package dev.romulus_lanceues.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigTest {

    private static final String VALID_BOT_TOKEN = "valid-discord-bot-token-123";
    private static final String VALID_GEMINI_KEY = "valid-gemini-api-key-456";

    @Test
    @DisplayName("Loads botToken and geminiConfig when both are valid in environment")
    void loadsValidConfigWithGeminiApiKey() {
        Map<String, String> env = Map.of(
                "BOT_TOKEN", "  " + VALID_BOT_TOKEN + "  ",
                "GEMINI_API_KEY", "  " + VALID_GEMINI_KEY + "  "
        );

        Config config = Config.from(env::get);

        assertEquals(VALID_BOT_TOKEN, config.botToken());
        assertNotNull(config.geminiConfig());
        assertEquals(VALID_GEMINI_KEY, config.geminiConfig().apiKey());
        assertEquals(GeminiConfig.DEFAULT_MODEL, config.geminiConfig().model());
    }

    @Test
    @DisplayName("Loads botToken and falls back to GOOGLE_API_KEY for geminiConfig")
    void loadsValidConfigWithGoogleApiKeyFallback() {
        Map<String, String> env = Map.of(
                "BOT_TOKEN", VALID_BOT_TOKEN,
                "GOOGLE_API_KEY", "google-fallback-key-789"
        );

        Config config = Config.from(env::get);

        assertEquals(VALID_BOT_TOKEN, config.botToken());
        assertEquals("google-fallback-key-789", config.geminiConfig().apiKey());
    }

    @Test
    @DisplayName("Throws NullPointerException when lookup function is null")
    void throwsWhenLookupIsNull() {
        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> Config.from(null)
        );

        assertEquals("lookup must not be null", exception.getMessage());
    }

    @Test
    @DisplayName("Throws IllegalStateException when BOT_TOKEN is missing")
    void throwsWhenBotTokenMissing() {
        Map<String, String> env = Map.of(
                "GEMINI_API_KEY", VALID_GEMINI_KEY
        );

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> Config.from(env::get)
        );

        assertEquals(
                "Missing required environment variable: 'BOT_TOKEN'. Set it in your environment or .env file.",
                exception.getMessage()
        );
    }

    @ParameterizedTest(name = "Rejects invalid BOT_TOKEN: ''{0}''")
    @ValueSource(strings = {"", "   ", "\t", "\n", "\r\n  \t", "\u2000\u2003\u3000"})
    @DisplayName("Throws IllegalStateException when BOT_TOKEN is empty or blank")
    void throwsWhenBotTokenEmptyOrBlank(String invalidToken) {
        Map<String, String> env = Map.of(
                "BOT_TOKEN", invalidToken,
                "GEMINI_API_KEY", VALID_GEMINI_KEY
        );

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> Config.from(env::get)
        );

        assertEquals(
                "Missing required environment variable: 'BOT_TOKEN'. Set it in your environment or .env file.",
                exception.getMessage()
        );
    }

    @Test
    @DisplayName("Throws IllegalStateException when Gemini API key is missing")
    void throwsWhenGeminiKeyMissing() {
        Map<String, String> env = Map.of(
                "BOT_TOKEN", VALID_BOT_TOKEN
        );

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> Config.from(env::get)
        );

        assertEquals(
                "Missing required environment variable: 'GEMINI_API_KEY' (or 'GOOGLE_API_KEY'). Set it in your environment or .env file.",
                exception.getMessage()
        );
    }

    @ParameterizedTest(name = "Rejects invalid Gemini API key: ''{0}''")
    @ValueSource(strings = {"", "   ", "\t", "\n", "\r\n  \t", "\u2000\u2003\u3000"})
    @DisplayName("Throws IllegalStateException when Gemini API key is blank")
    void throwsWhenGeminiKeyBlank(String blankKey) {
        Map<String, String> env = Map.of(
                "BOT_TOKEN", VALID_BOT_TOKEN,
                "GEMINI_API_KEY", blankKey
        );

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> Config.from(env::get)
        );

        assertEquals(
                "Missing required environment variable: 'GEMINI_API_KEY' (or 'GOOGLE_API_KEY'). Set it in your environment or .env file.",
                exception.getMessage()
        );
    }

    @Test
    @DisplayName("Preserves internal whitespace when stripping leading and trailing whitespace from BOT_TOKEN")
    void preservesInternalWhitespaceInBotToken() {
        Map<String, String> env = Map.of(
                "BOT_TOKEN", "  token-part1  token-part2  ",
                "GEMINI_API_KEY", VALID_GEMINI_KEY
        );

        Config config = Config.from(env::get);

        assertEquals("token-part1  token-part2", config.botToken());
    }

    @Test
    @DisplayName("Masks secrets in toString() and never leaks botToken or apiKey")
    void masksSecretsInToString() {
        GeminiConfig gemini = new GeminiConfig(
                "super-secret-gemini-key",
                GeminiConfig.DEFAULT_MODEL,
                GeminiConfig.DEFAULT_TEMPERATURE,
                GeminiConfig.DEFAULT_TOP_P,
                GeminiConfig.DEFAULT_TOP_K,
                GeminiConfig.DEFAULT_MAX_OUTPUT_TOKENS,
                GeminiConfig.DEFAULT_SYSTEM_INSTRUCTION
        );
        Config config = new Config("super-secret-discord-token", gemini);

        String str = config.toString();

        assertTrue(str.contains("botToken=***"));
        assertTrue(str.contains("apiKey=***"));
        assertFalse(str.contains("super-secret-discord-token"));
        assertFalse(str.contains("super-secret-gemini-key"));
    }

    @Test
    @DisplayName("Record equality and hashCode adhere to contract")
    void recordEqualityAndHashCode() {
        GeminiConfig g1 = new GeminiConfig("k1", "m1", 0.7f, 0.95f, 40.0f, 2048, "persona");
        GeminiConfig g2 = new GeminiConfig("k1", "m1", 0.7f, 0.95f, 40.0f, 2048, "persona");
        GeminiConfig g3 = new GeminiConfig("k2", "m1", 0.7f, 0.95f, 40.0f, 2048, "persona");

        Config c1 = new Config("t1", g1);
        Config c2 = new Config("t1", g2);
        Config c3 = new Config("t2", g1);
        Config c4 = new Config("t1", g3);

        assertEquals(c1, c2);
        assertEquals(c1.hashCode(), c2.hashCode());
        assertNotEquals(c1, c3);
        assertNotEquals(c1, c4);
    }
}
