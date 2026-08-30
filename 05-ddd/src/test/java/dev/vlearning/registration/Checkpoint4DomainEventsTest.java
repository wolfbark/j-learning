package dev.vlearning.registration;

import static org.assertj.core.api.Assertions.assertThat;

import org.jmolecules.event.annotation.DomainEvent;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import dev.vlearning.registration.enrollment.Enrollment;
import dev.vlearning.registration.enrollment.EnrollmentConfirmed;
import dev.vlearning.registration.enrollment.EnrollmentService;
import dev.vlearning.registration.notifications.EnrollmentNotifier;

/**
 * Checkpoint 4 — domain events. {@code Enrollment.confirm()} registers an
 * {@link EnrollmentConfirmed} (annotated {@code @DomainEvent}); saving the
 * aggregate publishes it as a Spring application event; the notifications
 * context reacts without the enrollment context ever knowing it exists.
 *
 * <p>Deliberately NOT {@code @Transactional}: we want real event publication,
 * not a rolled-back one.
 */
@Disabled("Checkpoint 4 — enable when you start step 4")
@SpringBootTest
@RecordApplicationEvents
class Checkpoint4DomainEventsTest {

    @Autowired
    EnrollmentService service;

    @Autowired
    ApplicationEvents events;

    @Autowired
    EnrollmentNotifier notifier;

    @Test
    void confirmingAnEnrollmentPublishesEnrollmentConfirmed() {
        Enrollment enrollment = service.create("DDD-101", "confirm-me@vaadin.com", 2);
        service.reserveSeat(enrollment.getId());
        service.confirm(enrollment.getId());

        assertThat(events.stream(EnrollmentConfirmed.class))
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.enrollmentId()).isEqualTo(enrollment.getId());
                    assertThat(event.courseId().value()).isEqualTo("DDD-101");
                    assertThat(event.attendeeEmail().value()).isEqualTo("confirm-me@vaadin.com");
                });
    }

    @Test
    void nothingIsPublishedBeforeTheConfirmation() {
        Enrollment enrollment = service.create("TDD-201", "not-yet@vaadin.com", 1);
        service.reserveSeat(enrollment.getId());

        assertThat(events.stream(EnrollmentConfirmed.class)).isEmpty();
    }

    @Test
    void theNotificationsContextReactsWithoutEnrollmentKnowingIt() {
        Enrollment enrollment = service.create("DDD-101", "notify-me@vaadin.com", 1);
        service.reserveSeat(enrollment.getId());
        service.confirm(enrollment.getId());

        assertThat(notifier.sentMessages())
                .anySatisfy(message -> assertThat(message)
                        .contains("notify-me@vaadin.com")
                        .contains("DDD-101"));
    }

    @Test
    void theEventDeclaresItsStereotype() {
        assertThat(EnrollmentConfirmed.class.isAnnotationPresent(DomainEvent.class))
                .as("@DomainEvent on EnrollmentConfirmed").isTrue();
    }
}
