package dev.vlearning.registration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Field;
import java.util.Arrays;

import org.jmolecules.ddd.annotation.AggregateRoot;
import org.jmolecules.ddd.annotation.Identity;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import dev.vlearning.registration.enrollment.Email;
import dev.vlearning.registration.enrollment.Enrollment;
import dev.vlearning.registration.enrollment.EnrollmentStatus;
import dev.vlearning.registration.enrollment.SeatCount;
import dev.vlearning.registration.shared.CourseId;

/**
 * Checkpoint 3 — the rich aggregate. Move the lifecycle rules out of
 * {@code EnrollmentService} into {@code Enrollment}: implement the stubbed
 * {@code request/reserveSeat/confirm/cancel} API, swap the foreign
 * {@code Course} reference for a {@link CourseId}, and delete every setter.
 * The service shrinks to load-call-save orchestration.
 *
 * <p>Note what is missing here: Spring. A domain model you can only test with
 * a container is not a domain model.
 */
@Disabled("Checkpoint 3 — enable when you start step 3")
class Checkpoint3RichAggregateTest {

    private Enrollment requested() {
        return Enrollment.request(
                new CourseId("DDD-101"), new Email("kriss@vaadin.com"), new SeatCount(2));
    }

    @Test
    void aFreshEnrollmentStartsItsLifeRequested() {
        assertThat(requested().getStatus()).isEqualTo(EnrollmentStatus.REQUESTED);
    }

    @Test
    void theCoreInvariant_noConfirmationWithoutAReservedSeat() {
        Enrollment enrollment = requested();

        assertThatThrownBy(enrollment::confirm)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("reserved");
    }

    @Test
    void reserveThenConfirmIsTheOnlyPathToConfirmed() {
        Enrollment enrollment = requested();

        enrollment.reserveSeat();
        assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.SEAT_RESERVED);

        enrollment.confirm();
        assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.CONFIRMED);
    }

    @Test
    void aSeatCanOnlyBeReservedOnce() {
        Enrollment enrollment = requested();
        enrollment.reserveSeat();

        assertThatThrownBy(enrollment::reserveSeat).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void cancellingBeforeConfirmationIsAllowed() {
        Enrollment enrollment = requested();
        enrollment.reserveSeat();
        enrollment.cancel();

        assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.CANCELLED);
    }

    @Test
    void aConfirmedEnrollmentCannotBeCancelled_thatIsARefundAndRefundsAreBillings() {
        Enrollment enrollment = requested();
        enrollment.reserveSeat();
        enrollment.confirm();

        assertThatThrownBy(enrollment::cancel).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void theAggregateExposesNoSetters() {
        assertThat(Arrays.stream(Enrollment.class.getMethods()))
                .as("public setters put the rules back in everyone else's hands")
                .noneMatch(method -> method.getName().startsWith("set"));
    }

    @Test
    void theAggregateDeclaresItsStereotypeAndItsIdentity() {
        assertThat(Enrollment.class.isAnnotationPresent(AggregateRoot.class))
                .as("@AggregateRoot on Enrollment").isTrue();
        assertThat(Arrays.stream(Enrollment.class.getDeclaredFields()))
                .as("exactly one field annotated @Identity")
                .filteredOn(field -> field.isAnnotationPresent(Identity.class))
                .hasSize(1);
    }

    @Test
    void theForeignCourseReferenceIsGone() {
        assertThat(Arrays.stream(Enrollment.class.getDeclaredFields()).map(Field::getType))
                .as("Enrollment references the course by CourseId, not by entity")
                .contains(CourseId.class)
                .noneMatch(type -> type.getSimpleName().equals("Course"));
    }
}
