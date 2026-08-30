package dev.vlearning.library;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.LocalDate;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.modulith.events.CompletedEventPublications;
import org.springframework.test.web.servlet.MockMvc;

import dev.vlearning.library.lending.LoanCreated;

/**
 * Proves the Event Publication Registry at work: publishing LoanCreated
 * inside the borrow transaction writes a row to the event_publication table
 * in the SAME transaction; once the notifications listener succeeds, the
 * publication is marked completed. Kill the JVM between the two and the
 * publication survives as "incomplete" — that is the crash-redelivery story.
 *
 * Not @Transactional: the registry entry is written on commit, and a
 * rolled-back test transaction would never produce one.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Disabled("Checkpoint 6 — enable when you start step 6")
class Checkpoint6EventPublicationRegistryTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    CompletedEventPublications completed;

    @Test
    void loanCreatedPublicationIsPersistedAndCompleted() throws Exception {
        mvc.perform(post("/catalog/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"isbn": "978-CP6", "title": "Enterprise Integration Patterns", "author": "Hohpe/Woolf", "copies": 1}
                                """))
                .andExpect(status().isCreated());

        mvc.perform(post("/loans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"barcode": "978-CP6-1", "memberEmail": "cp6@example.com", "dueDate": "%s"}
                                """.formatted(LocalDate.now().plusDays(14))))
                .andExpect(status().isCreated());

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(completed.findAll())
                        .anyMatch(publication -> publication.getEvent() instanceof LoanCreated event
                                && event.copyBarcode().equals("978-CP6-1")));
    }
}
