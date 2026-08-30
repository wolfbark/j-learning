package dev.vlearning.registration.enrollment;

import dev.vlearning.registration.catalog.internal.Course;
import dev.vlearning.registration.shared.CourseId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * The Enrollment "aggregate" as the previous team left it: a bag of getters
 * and setters whose actual rules live in {@link EnrollmentService}. It even
 * holds a live reference into another bounded context's aggregate
 * ({@link Course}). This class is the refactoring subject of checkpoints 2-6.
 *
 * <p>The rich-model API below ({@link #request}, {@link #reserveSeat},
 * {@link #confirm}, {@link #cancel}) is stubbed, not missing — the checkpoint
 * tests must compile against something, so the target API is fixed here. Your
 * job in step 3 is to move the behavior in, and then delete the setters.
 */
@Entity
@Table(name = "enrollment")
public class Enrollment {

    @Id
    @GeneratedValue
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "course_code")
    private Course course;

    @Column(name = "attendee_email")
    private String attendeeEmail;

    private int seats;

    @Enumerated(EnumType.STRING)
    private EnrollmentStatus status;

    protected Enrollment() {
        // JPA
    }

    // ——— the rich aggregate API, stubbed until step 3 ———

    /** A new enrollment request for a course. Starts life as {@code REQUESTED}. */
    public static Enrollment request(CourseId courseId, Email attendeeEmail, SeatCount seats) {
        throw new UnsupportedOperationException(
                "Checkpoint 3 — move the enrollment rules from EnrollmentService into the aggregate");
    }

    /** A seat is reserved for this enrollment. Only a {@code REQUESTED} enrollment can reserve. */
    public void reserveSeat() {
        throw new UnsupportedOperationException(
                "Checkpoint 3 — move the enrollment rules from EnrollmentService into the aggregate");
    }

    /** Confirms the enrollment. The invariant: no confirmation without a reserved seat. */
    public void confirm() {
        throw new UnsupportedOperationException(
                "Checkpoint 3 — move the enrollment rules from EnrollmentService into the aggregate");
    }

    /** Cancels the enrollment. Confirmed enrollments are Billing's problem, not ours. */
    public void cancel() {
        throw new UnsupportedOperationException(
                "Checkpoint 3 — move the enrollment rules from EnrollmentService into the aggregate");
    }

    // ——— the anemic surface ———

    public Long getId() {
        return id;
    }

    public Course getCourse() {
        return course;
    }

    public void setCourse(Course course) {
        this.course = course;
    }

    public String getAttendeeEmail() {
        return attendeeEmail;
    }

    public void setAttendeeEmail(String attendeeEmail) {
        this.attendeeEmail = attendeeEmail;
    }

    public int getSeats() {
        return seats;
    }

    public void setSeats(int seats) {
        this.seats = seats;
    }

    public EnrollmentStatus getStatus() {
        return status;
    }

    public void setStatus(EnrollmentStatus status) {
        this.status = status;
    }
}
