package dev.vlearning.ticketing;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Step 8: the same conflict, one layer out, where no database lock can reach it.
 *
 * <p>Two administrators open the price form at 09:00 and save at 09:05 and
 * 09:06. Their transactions never overlap — the "concurrent" edit is a minute
 * wide, and the thing being raced is a human being's think-time. A row lock is
 * useless across that; a version number carried to the client and back is not.
 *
 * <p>That is exactly what {@code ETag} and {@code If-Match} are: optimistic
 * locking, standardised in HTTP, with a status code reserved for losing.
 */
@Disabled("Checkpoint 8 — enable when you start step 8")
@AutoConfigureMockMvc
class Checkpoint8HttpConcurrencyTest extends AbstractIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Test
    void aGetCarriesTheCurrentVersionAsAnEtag() throws Exception {
        mvc.perform(get("/ticket-types/{id}", CONFERENCE))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"0\""))
                .andExpect(jsonPath("$.price").value(50000));
    }

    @Test
    void aStaleIfMatchIsRejectedWithPreconditionFailed() throws Exception {
        String etagBothAdminsSaw = etagOf(CONFERENCE);

        mvc.perform(put("/ticket-types/{id}", CONFERENCE)
                        .header("If-Match", etagBothAdminsSaw)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"price\": 45000}"))
                .andExpect(status().isNoContent());

        mvc.perform(put("/ticket-types/{id}", CONFERENCE)
                        .header("If-Match", etagBothAdminsSaw)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"price\": 60000}"))
                .andExpect(status().isPreconditionFailed());

        assertThat(priceOf(CONFERENCE))
                .as("the first admin's edit stands; the second was told, not ignored")
                .isEqualTo(45000);
    }

    @Test
    void aCurrentIfMatchSucceedsAndHandsBackTheNextEtag() throws Exception {
        mvc.perform(put("/ticket-types/{id}", CONFERENCE)
                        .header("If-Match", etagOf(CONFERENCE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"price\": 45000}"))
                .andExpect(status().isNoContent())
                .andExpect(header().string("ETag", "\"1\""));

        assertThat(priceOf(CONFERENCE)).isEqualTo(45000);
    }

    @Test
    void theRejectedAdminRefetchesAndSucceeds() throws Exception {
        String stale = etagOf(CONFERENCE);
        mvc.perform(put("/ticket-types/{id}", CONFERENCE)
                        .header("If-Match", stale)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"price\": 45000}"))
                .andExpect(status().isNoContent());

        mvc.perform(put("/ticket-types/{id}", CONFERENCE)
                        .header("If-Match", stale)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"price\": 60000}"))
                .andExpect(status().isPreconditionFailed());

        // The recovery a human can actually understand: look again, decide again.
        mvc.perform(put("/ticket-types/{id}", CONFERENCE)
                        .header("If-Match", etagOf(CONFERENCE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"price\": 60000}"))
                .andExpect(status().isNoContent());

        assertThat(priceOf(CONFERENCE)).isEqualTo(60000);
    }

    private String etagOf(long ticketTypeId) throws Exception {
        return mvc.perform(get("/ticket-types/{id}", ticketTypeId))
                .andReturn().getResponse().getHeader("ETag");
    }

    private long priceOf(long ticketTypeId) {
        return jdbc.sql("SELECT price FROM ticket_type WHERE id = :id")
                .param("id", ticketTypeId).query(Long.class).single();
    }
}
