package dev.vlearning.lending;

import java.time.LocalDate;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import dev.vlearning.lending.events.BookBorrowed;
import dev.vlearning.lending.events.BookReturned;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Checkpoint 2 — the write side announces facts. Each command handler
 * publishes its domain event via ApplicationEventPublisher *inside* the
 * command transaction. Nothing consumes them yet — that is step 3. Spring's
 * test support records every event published during a test method.
 */
@Disabled("Checkpoint 2 — enable when you start step 2")
@SpringBootTest
@AutoConfigureMockMvc
@RecordApplicationEvents
class Checkpoint2DomainEventsTest extends AbstractIntegrationTest {

    @Autowired
    ApplicationEvents events;

    @Test
    void borrowingPublishesBookBorrowed() throws Exception {
        long loanId = borrow(8, 5);

        assertThat(events.stream(BookBorrowed.class))
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.loanId()).isEqualTo(loanId);
                    assertThat(event.memberId()).isEqualTo(8);
                    assertThat(event.bookId()).isEqualTo(5);
                    assertThat(event.borrowedOn()).isEqualTo(LocalDate.of(2026, 9, 1));
                    assertThat(event.dueOn()).isEqualTo(LocalDate.of(2026, 9, 22));
                });

        // clean up so other tests see book 5 available again
        mvc.perform(post("/api/loans/{id}/return", loanId)).andExpect(status().isOk());
    }

    @Test
    void returningPublishesBookReturned() throws Exception {
        long loanId = borrow(8, 5);
        mvc.perform(post("/api/loans/{id}/return", loanId)).andExpect(status().isOk());

        assertThat(events.stream(BookReturned.class))
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.loanId()).isEqualTo(loanId);
                    assertThat(event.memberId()).isEqualTo(8);
                    assertThat(event.bookId()).isEqualTo(5);
                    assertThat(event.returnedOn()).isEqualTo(LocalDate.of(2026, 9, 1));
                    assertThat(event.late()).isFalse();
                });
    }
}
