package dev.romulus_lanceues.util;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

public class RateLimiter {

    private static final int MAX_ENTRIES = 10_000;

    private final Duration cooldownDuration;
    private final Clock clock;
    private final ConcurrentHashMap<Long, Instant> userTimestamps = new ConcurrentHashMap<>();

    public RateLimiter(Duration cooldownDuration) {
        this(cooldownDuration, Clock.systemUTC());
    }

    public RateLimiter(Duration cooldownDuration, Clock clock) {
        this.cooldownDuration = Objects.requireNonNull(cooldownDuration, "cooldownDuration must not be null");
        if (cooldownDuration.isNegative() || cooldownDuration.isZero()) {
            throw new IllegalArgumentException("cooldownDuration must be positive");
        }
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public Optional<Duration> checkCooldown(long userId) {
        Instant now = clock.instant();

        if (userTimestamps.size() > MAX_ENTRIES) {
            cleanExpired(now);
        }

        AtomicReference<Duration> remainingCooldown = new AtomicReference<>();
        userTimestamps.compute(userId, (key, lastAction) -> {
            if (lastAction != null) {
                Duration elapsed = Duration.between(lastAction, now);
                if (elapsed.compareTo(cooldownDuration) < 0) {
                    remainingCooldown.set(cooldownDuration.minus(elapsed));
                    return lastAction;
                }
            }
            return now;
        });

        return Optional.ofNullable(remainingCooldown.get());
    }

    public void cleanExpired(Instant now) {
        userTimestamps.entrySet().removeIf(entry -> {
            Duration elapsed = Duration.between(entry.getValue(), now);
            return elapsed.compareTo(cooldownDuration) >= 0;
        });
    }

    public int size() {
        return userTimestamps.size();
    }
}
