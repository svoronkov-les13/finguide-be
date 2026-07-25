package les13.finguide.backend.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class PasswordResetRateLimiter {
    private final int maxAttempts;
    private final Duration window;
    private final Clock clock;
    private final ConcurrentMap<String, Deque<Instant>> attempts = new ConcurrentHashMap<>();

    @Autowired
    public PasswordResetRateLimiter(
            @Value("${finguide.keycloak.password-reset-max-attempts:3}") int maxAttempts,
            @Value("${finguide.keycloak.password-reset-window:PT1H}") Duration window
    ) {
        this(maxAttempts, window, Clock.systemUTC());
    }

    PasswordResetRateLimiter(int maxAttempts, Duration window, Clock clock) {
        this.maxAttempts = maxAttempts;
        this.window = window;
        this.clock = clock;
    }

    public boolean tryAcquire(String email) {
        if (maxAttempts <= 0) {
            return true;
        }

        String key = email.trim().toLowerCase(Locale.ROOT);
        Instant now = clock.instant();
        Instant cutoff = now.minus(window);
        Deque<Instant> emailAttempts = attempts.computeIfAbsent(key, ignored -> new ArrayDeque<>());

        synchronized (emailAttempts) {
            while (!emailAttempts.isEmpty() && emailAttempts.peekFirst().isBefore(cutoff)) {
                emailAttempts.removeFirst();
            }
            if (emailAttempts.size() >= maxAttempts) {
                return false;
            }
            emailAttempts.addLast(now);
            return true;
        }
    }
}
