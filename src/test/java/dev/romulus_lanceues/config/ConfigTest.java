package dev.romulus_lanceues.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConfigTest {

    @Test
    @DisplayName("Loads token and strips whitespace")
    void loadsTokenAndStripsWhitespace() {
        Map<String, String> env = Map.of("BOT_TOKEN", "  test-token-123  ");
        Config config = Config.from(env::get);

        assertEquals("test-token-123", config.botToken());
    }

    @Test
    @DisplayName("Throws IllegalStateException when BOT_TOKEN is missing")
    void throwsWhenTokenMissing() {
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> Config.from(key -> null)
        );

        assertEquals(
                "Missing required environment variable: 'BOT_TOKEN'. Set it in your environment or .env file.",
                exception.getMessage()
        );
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
    @DisplayName("Throws IllegalStateException when BOT_TOKEN is empty")
    void throwsWhenTokenEmpty() {
        Map<String, String> env = Map.of("BOT_TOKEN", "");
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
    @DisplayName("Throws IllegalStateException when BOT_TOKEN is blank")
    void throwsWhenTokenBlank() {
        Map<String, String> env = Map.of("BOT_TOKEN", "   \t\n  ");
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
    @DisplayName("Masks botToken in toString()")
    void masksTokenInToString() {
        Config config = new Config("super-secret-token");
        String str = config.toString();

        assertEquals("Config[botToken=***]", str);
        assertFalse(str.contains("super-secret-token"));
    }

    @Test
    @DisplayName("Preserves internal whitespace when stripping leading and trailing whitespace")
    void preservesInternalWhitespace() {
        Map<String, String> env = Map.of("BOT_TOKEN", "  token-part1  token-part2  ");
        Config config = Config.from(env::get);

        assertEquals("token-part1  token-part2", config.botToken());
    }

    @Test
    @DisplayName("Record equality and hash code adhere to contract")
    void recordEqualityAndHashCode() {
        Config c1 = new Config("test-token");
        Config c2 = new Config("test-token");
        Config c3 = new Config("other-token");

        assertEquals(c1, c2);
        assertEquals(c1.hashCode(), c2.hashCode());
        assertEquals("test-token", c1.botToken());
        assertFalse(c1.equals(c3));
    }
}
