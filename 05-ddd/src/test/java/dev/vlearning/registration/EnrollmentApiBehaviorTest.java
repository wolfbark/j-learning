package dev.vlearning.registration;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;

/**
 * Pins the HTTP contract of the enrollment context, and stays enabled for the
 * whole lesson. It knows nothing about aggregates, value objects, or events —
 * which is exactly what makes it a refactoring safety net. If it goes red
 * during checkpoints 2-6, you changed behavior, not just the model.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class EnrollmentApiBehaviorTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void enrollingCreatesARequestedEnrollment() throws Exception {
        mockMvc.perform(post("/enrollments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"courseCode": "DDD-101", "attendeeEmail": "kriss@vaadin.com", "seats": 2}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.courseCode").value("DDD-101"))
                .andExpect(jsonPath("$.attendeeEmail").value("kriss@vaadin.com"))
                .andExpect(jsonPath("$.seats").value(2))
                .andExpect(jsonPath("$.status").value("REQUESTED"));
    }

    @Test
    void attendeeEmailIsNormalizedOnTheWayIn() throws Exception {
        mockMvc.perform(post("/enrollments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"courseCode": "DDD-101", "attendeeEmail": "  Kriss@Vaadin.COM ", "seats": 1}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.attendeeEmail").value("kriss@vaadin.com"));
    }

    @Test
    void enrollingInAnUnknownCourseIs404() throws Exception {
        mockMvc.perform(post("/enrollments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"courseCode": "XX-999", "attendeeEmail": "kriss@vaadin.com", "seats": 1}
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    void malformedEmailIs400() throws Exception {
        mockMvc.perform(post("/enrollments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"courseCode": "DDD-101", "attendeeEmail": "not-an-email", "seats": 1}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void zeroSeatsIs400() throws Exception {
        mockMvc.perform(post("/enrollments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"courseCode": "DDD-101", "attendeeEmail": "kriss@vaadin.com", "seats": 0}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void moreSeatsThanTheCourseHasIs400() throws Exception {
        mockMvc.perform(post("/enrollments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"courseCode": "MOD-401", "attendeeEmail": "kriss@vaadin.com", "seats": 5}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void reserveThenConfirmIsTheHappyPath() throws Exception {
        long id = enroll("DDD-101", "happy@vaadin.com", 1);

        mockMvc.perform(post("/enrollments/{id}/reserve-seat", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SEAT_RESERVED"));

        mockMvc.perform(post("/enrollments/{id}/confirm", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void confirmingWithoutAReservedSeatIs409() throws Exception {
        long id = enroll("DDD-101", "eager@vaadin.com", 1);

        mockMvc.perform(post("/enrollments/{id}/confirm", id))
                .andExpect(status().isConflict());
    }

    @Test
    void reservingTwiceIs409() throws Exception {
        long id = enroll("TDD-201", "twice@vaadin.com", 1);

        mockMvc.perform(post("/enrollments/{id}/reserve-seat", id))
                .andExpect(status().isOk());
        mockMvc.perform(post("/enrollments/{id}/reserve-seat", id))
                .andExpect(status().isConflict());
    }

    @Test
    void cancellingARequestedEnrollmentWorks() throws Exception {
        long id = enroll("TDD-201", "leaver@vaadin.com", 1);

        mockMvc.perform(post("/enrollments/{id}/cancel", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void cancellingAConfirmedEnrollmentIs409BecauseRefundsAreBillings() throws Exception {
        long id = enroll("DDD-101", "committed@vaadin.com", 1);
        mockMvc.perform(post("/enrollments/{id}/reserve-seat", id)).andExpect(status().isOk());
        mockMvc.perform(post("/enrollments/{id}/confirm", id)).andExpect(status().isOk());

        mockMvc.perform(post("/enrollments/{id}/cancel", id))
                .andExpect(status().isConflict());
    }

    @Test
    void anEnrollmentCanBeFetched() throws Exception {
        long id = enroll("DDD-101", "reader@vaadin.com", 3);

        mockMvc.perform(get("/enrollments/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.courseCode").value("DDD-101"))
                .andExpect(jsonPath("$.seats").value(3));
    }

    private long enroll(String courseCode, String email, int seats) throws Exception {
        String body = mockMvc.perform(post("/enrollments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"courseCode": "%s", "attendeeEmail": "%s", "seats": %d}
                                """.formatted(courseCode, email, seats)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }
}
