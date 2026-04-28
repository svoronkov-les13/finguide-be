package les13.finguide.backend.analytics;

import java.util.List;

public record HealthScore(
        int score,
        List<Item> items
) {
    public record Item(
            String key,
            String label,
            Number value,
            Status status,
            String hint
    ) {
    }

    public enum Status {
        GOOD,
        WARNING,
        BAD
    }
}
