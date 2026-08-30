package dev.vlearning.registration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.jmolecules.ddd.annotation.ValueObject;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import dev.vlearning.registration.enrollment.Email;
import dev.vlearning.registration.enrollment.Enrollment;
import dev.vlearning.registration.enrollment.SeatCount;
import dev.vlearning.registration.shared.CourseId;

/**
 * Checkpoint 2 — value objects. Make {@link Email}, {@link SeatCount} and
 * {@link CourseId} impossible to construct in an invalid state (compact
 * constructors), give them the {@code @ValueObject} stereotype, and switch the
 * {@code Enrollment} entity's email and seats fields over to them. Plain
 * JUnit — a value object needs no framework.
 */
@Disabled("Checkpoint 2 — enable when you start step 2")
class Checkpoint2ValueObjectsTest {

    @Test
    void emailRejectsWhatAPlainStringHappilyAccepts() {
        assertThatThrownBy(() -> new Email(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Email("")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Email("kriss")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Email("kriss@")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Email("kriss@vaadin")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Email("kri ss@vaadin.com")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void emailNormalizesSoThatEqualityMeansTheSameMailbox() {
        assertThat(new Email("  Kriss@Vaadin.COM ").value()).isEqualTo("kriss@vaadin.com");
        assertThat(new Email("Kriss@Vaadin.COM")).isEqualTo(new Email("kriss@vaadin.com"));
    }

    @Test
    void seatCountEnforcesTheGroupBookingPolicy() {
        assertThat(new SeatCount(1).value()).isEqualTo(1);
        assertThat(new SeatCount(20).value()).isEqualTo(20);

        assertThatThrownBy(() -> new SeatCount(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SeatCount(-3)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SeatCount(21)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void courseIdValidatesAndNormalizesTheCode() {
        assertThat(new CourseId("ddd-101").value()).isEqualTo("DDD-101");
        assertThat(new CourseId(" DDD-101 ").value()).isEqualTo("DDD-101");

        assertThatThrownBy(() -> new CourseId(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CourseId("")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CourseId("101")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CourseId("DDD101")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void valueObjectsDeclareTheirStereotype() {
        assertThat(Email.class.isAnnotationPresent(ValueObject.class))
                .as("@ValueObject on Email").isTrue();
        assertThat(SeatCount.class.isAnnotationPresent(ValueObject.class))
                .as("@ValueObject on SeatCount").isTrue();
        assertThat(CourseId.class.isAnnotationPresent(ValueObject.class))
                .as("@ValueObject on CourseId").isTrue();
    }

    @Test
    void theEnrollmentEntitySpeaksInValueObjectsNotPrimitives() {
        Set<Class<?>> fieldTypes = Arrays.stream(Enrollment.class.getDeclaredFields())
                .map(Field::getType)
                .collect(Collectors.toSet());

        assertThat(fieldTypes)
                .as("Enrollment's own fields should use the value objects")
                .contains(Email.class, SeatCount.class);
        assertThat(fieldTypes)
                .as("no more raw String / int business data on the aggregate")
                .doesNotContain(String.class, int.class);
    }
}
