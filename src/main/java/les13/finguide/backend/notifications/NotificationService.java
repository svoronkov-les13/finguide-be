package les13.finguide.backend.notifications;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class NotificationService {
    public List<NotificationView> listForPlan(UUID planId) {
        return List.of();
    }

    public record NotificationView(
            UUID id,
            String type,
            String title,
            String description,
            boolean read
    ) {
    }
}
