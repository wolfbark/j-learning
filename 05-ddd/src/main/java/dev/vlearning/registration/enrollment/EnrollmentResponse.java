package dev.vlearning.registration.enrollment;

/**
 * What the web tier serves. The JSON shape is pinned by the always-on behavior
 * test — it must not change while the domain model underneath it does. That is
 * the whole point of keeping a translation at the boundary.
 */
public record EnrollmentResponse(Long id, String courseCode, String attendeeEmail, int seats, String status) {

    static EnrollmentResponse from(Enrollment enrollment) {
        return new EnrollmentResponse(
                enrollment.getId(),
                enrollment.getCourse().getCode(),
                enrollment.getAttendeeEmail(),
                enrollment.getSeats(),
                enrollment.getStatus().name());
    }
}
