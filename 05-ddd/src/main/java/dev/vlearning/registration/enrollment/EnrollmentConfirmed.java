package dev.vlearning.registration.enrollment;

import dev.vlearning.registration.shared.CourseId;

/**
 * The pivotal event of the enrollment context — the fact other contexts wait
 * for. Currently a plain record that nothing ever publishes; checkpoint 4
 * makes it a real domain event, registered by the aggregate itself when
 * {@code confirm()} succeeds.
 */
public record EnrollmentConfirmed(Long enrollmentId, CourseId courseId, Email attendeeEmail) {
}
