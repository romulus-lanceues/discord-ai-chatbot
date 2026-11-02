package dev.romulus_lanceues.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class RateLimiterTest {

    private static class MutableClock extends Clock {
        private final AtomicReference<Instant> now;

        public MutableClock(Instant initial) {
            this.now = new AtomicReference<>(initial);
        }

        public void advance(Duration duration) {
            now.updateAndGet(i -> i.plus(duration));
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now.get();
        }
    }

    @Test
    @DisplayName("Validates constructor parameters")
    void validatesConstructorParameters() {
        assertThrows(NullPointerException.class, () -> new RateLimiter(null));
        assertThrows(IllegalArgumentException.class, () -> new RateLimiter(Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> new RateLimiter(Duration.ofSeconds(-1)));
    }

    @Test
    @DisplayName("Allows first call for a user and blocks second call within cooldown")
    void enforcesCooldownPerUser() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        RateLimiter rateLimiter = new RateLimiter(Duration.ofSeconds(5), clock);

        long userId = 12345L;

        // First call should succeed (no cooldown)
        Optional<Duration> first = rateLimiter.checkCooldown(userId);
        assertTrue(first.isEmpty());

        // Advance 2 seconds - still in 5s cooldown
        clock.advance(Duration.ofSeconds(2));
        Optional<Duration> second = rateLimiter.checkCooldown(userId);
        assertTrue(second.isPresent());
        assertEquals(Duration.ofSeconds(3), second.get());

        // Advance another 3 seconds - cooldown elapsed
        clock.advance(Duration.ofSeconds(3));
        Optional<Duration> third = rateLimiter.checkCooldown(userId);
        assertTrue(third.isEmpty());
    }

    @Test
    @DisplayName("Different users have independent cooldown trackers")
    void tracksUsersIndependently() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        RateLimiter rateLimiter = new RateLimiter(Duration.ofSeconds(5), clock);

        long user1 = 111L;
        long user2 = 222L;

        assertTrue(rateLimiter.checkCooldown(user1).isEmpty());
        // User 2 should still be allowed immediately
        assertTrue(rateLimiter.checkCooldown(user2).isEmpty());

        // Both are now on cooldown
        assertTrue(rateLimiter.checkCooldown(user1).isPresent());
        assertTrue(rateLimiter.checkCooldown(user2).isPresent());
    }

    @Test
    @DisplayName("Prunes expired user entries during cleanup")
    void prunesExpiredEntries() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        RateLimiter rateLimiter = new RateLimiter(Duration.ofSeconds(5), clock);

        rateLimiter.checkCooldown(1L);
        rateLimiter.checkCooldown(2L);
        assertEquals(2, rateLimiter.size());

        // Advance 10 seconds (well past the 5s cooldown)
        clock.advance(Duration.ofSeconds(10));
        rateLimiter.cleanExpired(clock.instant());

        assertEquals(0, rateLimiter.size());
    }

    @Test
    @DisplayName("Immediate second request triggers cooldown with correct remaining seconds")
    void immediateSecondRequestTriggersCooldownWithAccurateRemainingSeconds() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        RateLimiter rateLimiter = new RateLimiter(Duration.ofSeconds(5), clock);

        long userId = 9999L;
        // First request is allowed
        assertTrue(rateLimiter.checkCooldown(userId).isEmpty());

        // Advance only 500 milliseconds
        clock.advance(Duration.ofMillis(500));
        Optional<Duration> cooldown = rateLimiter.checkCooldown(userId);
        assertTrue(cooldown.isPresent());
        assertEquals(Duration.ofMillis(4500), cooldown.get());

        long remainingSeconds = (long) Math.ceil(cooldown.get().toMillis() / 1000.0);
        assertEquals(5, remainingSeconds);
    }

    @Test
    @DisplayName("Cleans expired entries while preserving active user entries")
    void cleansExpiredWhilePreservingActiveEntries() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        RateLimiter rateLimiter = new RateLimiter(Duration.ofSeconds(5), clock);

        // User 1 requests at t=0
        rateLimiter.checkCooldown(1L);

        // Advance to t=4s, User 2 requests
        clock.advance(Duration.ofSeconds(4));
        rateLimiter.checkCooldown(2L);
        assertEquals(2, rateLimiter.size());

        // Advance to t=6s (User 1 is 6s old > 5s; User 2 is 2s old < 5s)
        clock.advance(Duration.ofSeconds(2));
        rateLimiter.cleanExpired(clock.instant());

        assertEquals(1, rateLimiter.size());
        // User 2 should still be in cooldown
        Optional<Duration> user2Cooldown = rateLimiter.checkCooldown(2L);
        assertTrue(user2Cooldown.isPresent());
        assertEquals(Duration.ofSeconds(3), user2Cooldown.get());
    }

    @Test
    @DisplayName("Thread safety: only one thread succeeds among concurrent requests for same user")
    void threadSafetyConcurrentSingleUser() throws InterruptedException {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        RateLimiter rateLimiter = new RateLimiter(Duration.ofSeconds(5), clock);

        int threadCount = 20;
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(threadCount);
        java.util.concurrent.CountDownLatch readyLatch = new java.util.concurrent.CountDownLatch(threadCount);
        java.util.concurrent.CountDownLatch startLatch = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.atomic.AtomicInteger allowedCount = new java.util.concurrent.atomic.AtomicInteger(0);
        java.util.concurrent.atomic.AtomicInteger blockedCount = new java.util.concurrent.atomic.AtomicInteger(0);

        long userId = 777L;

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    Optional<Duration> result = rateLimiter.checkCooldown(userId);
                    if (result.isEmpty()) {
                        allowedCount.incrementAndGet();
                    } else {
                        blockedCount.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        readyLatch.await();
        startLatch.countDown();
        executor.shutdown();
        assertTrue(executor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS));

        assertEquals(1, allowedCount.get(), "Exactly one concurrent thread should be allowed");
        assertEquals(threadCount - 1, blockedCount.get(), "All other concurrent threads should be blocked");
    }

    @Test
    @DisplayName("Thread safety: concurrent distinct users are all allowed independently")
    void threadSafetyConcurrentDistinctUsers() throws InterruptedException {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        RateLimiter rateLimiter = new RateLimiter(Duration.ofSeconds(5), clock);

        int threadCount = 30;
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(threadCount);
        java.util.concurrent.CountDownLatch readyLatch = new java.util.concurrent.CountDownLatch(threadCount);
        java.util.concurrent.CountDownLatch startLatch = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.atomic.AtomicInteger allowedCount = new java.util.concurrent.atomic.AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final long userId = 1000L + i;
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    Optional<Duration> result = rateLimiter.checkCooldown(userId);
                    if (result.isEmpty()) {
                        allowedCount.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        readyLatch.await();
        startLatch.countDown();
        executor.shutdown();
        assertTrue(executor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS));

        assertEquals(threadCount, allowedCount.get(), "All distinct users should be allowed on first request");
    }
}
