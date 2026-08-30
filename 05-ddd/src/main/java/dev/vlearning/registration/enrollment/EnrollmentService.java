package dev.vlearning.registration.enrollment;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.vlearning.registration.catalog.internal.Course;
import dev.vlearning.registration.catalog.internal.CourseRepository;

/**
 * The fat service: every rule of the enrollment lifecycle lives here, applied
 * to a passive data bag through setters. It also injects the catalog context's
 * internal repository, because it needs the {@link Course} entity to wire the
 * foreign object reference. Both smells are deliberate — they are what this
 * lesson dismantles.
 */
@Service
@Transactional
public class EnrollmentService {

    private final EnrollmentRepository enrollments;
    private final CourseRepository courses;

    EnrollmentService(EnrollmentRepository enrollments, CourseRepository courses) {
        this.enrollments = enrollments;
        this.courses = courses;
    }

    public Enrollment create(String courseCode, String attendeeEmail, int seats) {
        Course course = courses.findByCode(courseCode)
                .orElseThrow(() -> new CourseNotFoundException(courseCode));
        if (attendeeEmail == null || !attendeeEmail.contains("@")) {
            throw new IllegalArgumentException("not a valid email address: " + attendeeEmail);
        }
        String normalizedEmail = attendeeEmail.trim().toLowerCase();
        if (seats < 1) {
            throw new IllegalArgumentException("an enrollment needs at least one seat");
        }
        if (seats > course.getCapacity()) {
            throw new IllegalArgumentException(
                    "course " + courseCode + " only has " + course.getCapacity() + " seats in total");
        }
        Enrollment enrollment = new Enrollment();
        enrollment.setCourse(course);
        enrollment.setAttendeeEmail(normalizedEmail);
        enrollment.setSeats(seats);
        enrollment.setStatus(EnrollmentStatus.REQUESTED);
        return enrollments.save(enrollment);
    }

    public Enrollment reserveSeat(long enrollmentId) {
        Enrollment enrollment = load(enrollmentId);
        if (enrollment.getStatus() != EnrollmentStatus.REQUESTED) {
            throw new IllegalStateException(
                    "a seat can only be reserved for a REQUESTED enrollment, this one is "
                            + enrollment.getStatus());
        }
        enrollment.setStatus(EnrollmentStatus.SEAT_RESERVED);
        return enrollments.save(enrollment);
    }

    public Enrollment confirm(long enrollmentId) {
        Enrollment enrollment = load(enrollmentId);
        if (enrollment.getStatus() != EnrollmentStatus.SEAT_RESERVED) {
            throw new IllegalStateException(
                    "cannot confirm without a reserved seat, this enrollment is "
                            + enrollment.getStatus());
        }
        enrollment.setStatus(EnrollmentStatus.CONFIRMED);
        return enrollments.save(enrollment);
    }

    public Enrollment cancel(long enrollmentId) {
        Enrollment enrollment = load(enrollmentId);
        if (enrollment.getStatus() == EnrollmentStatus.CONFIRMED) {
            throw new IllegalStateException(
                    "a confirmed enrollment cannot be cancelled here — that is a refund, and refunds belong to Billing");
        }
        if (enrollment.getStatus() == EnrollmentStatus.CANCELLED) {
            throw new IllegalStateException("this enrollment is already cancelled");
        }
        enrollment.setStatus(EnrollmentStatus.CANCELLED);
        return enrollments.save(enrollment);
    }

    @Transactional(readOnly = true)
    public Enrollment get(long enrollmentId) {
        return load(enrollmentId);
    }

    private Enrollment load(long enrollmentId) {
        return enrollments.findById(enrollmentId)
                .orElseThrow(() -> new EnrollmentNotFoundException(enrollmentId));
    }
}
