package dev.vlearning.library.notifications;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class NotificationsController {

    private final NotificationSender sender;
    private final OverdueNotifier overdueNotifier;

    NotificationsController(NotificationSender sender, OverdueNotifier overdueNotifier) {
        this.sender = sender;
        this.overdueNotifier = overdueNotifier;
    }

    @GetMapping("/notifications")
    List<String> sent() {
        return sender.sent();
    }

    @PostMapping("/notifications/overdue-run")
    Map<String, Integer> overdueRun() {
        return Map.of("notified", overdueNotifier.run());
    }
}
