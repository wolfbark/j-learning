package dev.vlearning.registration.notifications;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import dev.vlearning.registration.enrollment.EnrollmentConfirmed;

/**
 * The notifications context — a generic subdomain, and a <em>conformist</em>
 * consumer: it listens to the enrollment context's published event and takes
 * it exactly as it comes, no negotiation, no translation layer. For a generic
 * subdomain that is the right amount of effort.
 *
 * <p>"Sending" means an in-memory outbox plus a log line; a real mail gateway
 * would change nothing about the relationship. This listener never fires
 * today, because nothing publishes {@link EnrollmentConfirmed} yet — that is
 * checkpoint 4's payoff.
 */
@Component
public class EnrollmentNotifier {

    private static final Logger log = LoggerFactory.getLogger(EnrollmentNotifier.class);

    private final List<String> sentMessages = new CopyOnWriteArrayList<>();

    @EventListener
    public void on(EnrollmentConfirmed event) {
        String message = "To %s: your seat for %s is confirmed (enrollment #%d)".formatted(
                event.attendeeEmail().value(), event.courseId().value(), event.enrollmentId());
        sentMessages.add(message);
        log.info("[notifications] {}", message);
    }

    public List<String> sentMessages() {
        return List.copyOf(sentMessages);
    }
}
