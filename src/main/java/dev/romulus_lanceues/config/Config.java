package dev.romulus_lanceues.config;

import java.util.Objects;
import java.util.function.Function;

public record Config(String botToken) {

    public static Config from(Function<String, String> lookup) {
        Objects.requireNonNull(lookup, "lookup must not be null");
        String token = lookup.apply("BOT_TOKEN");
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("Missing required environment variable: 'BOT_TOKEN'. Set it in your environment or .env file.");
        }
        return new Config(token.strip());
    }

    public static Config fromEnv() {
        return from(System.getenv()::get);
    }

    @Override
    public String toString() {
        return "Config[botToken=***]";
    }
}
