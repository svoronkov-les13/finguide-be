package les13.finguide.backend.auth;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordResetRateLimiterTests {
    @Test
    void limitsPasswordResetAttemptsPerEmailWithinWindow() {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-22T10:00:00Z"));
        PasswordResetRateLimiter limiter = new PasswordResetRateLimiter(2, Duration.ofHours(1), clock);

        assertThat(limiter.tryAcquire(" Stas@Example.com ")).isTrue();
        assertThat(limiter.tryAcquire("stas@example.com")).isTrue();
        assertThat(limiter.tryAcquire("STAS@example.com")).isFalse();

        clock.advance(Duration.ofHours(1).plusSeconds(1));

        assertThat(limiter.tryAcquire("stas@example.com")).isTrue();
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
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
            return instant;
        }
    }
}
