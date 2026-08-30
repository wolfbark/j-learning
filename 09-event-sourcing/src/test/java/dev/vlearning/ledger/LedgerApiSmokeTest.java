package dev.vlearning.ledger;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * ENABLED end-to-end behavior over HTTP. Pristine it runs against the in-memory store;
 * after you flip {@code ledger.event-store=postgres} in step 3 it runs — unchanged —
 * against your Postgres store. Behavior tests against the CONTRACT survive the swap;
 * that is why they are written at this level.
 */
@SpringBootTest
@AutoConfigureMockMvc
class LedgerApiSmokeTest extends PostgresTestBase {

    @Autowired
    MockMvc mvc;

    @Test
    void fullAccountLifecycleOverHttp() throws Exception {
        var id = "smoke-" + UUID.randomUUID();

        mvc.perform(post("/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"accountId": "%s", "owner": "Ada Lovelace"}
                                """.formatted(id)))
                .andExpect(status().isCreated());

        mvc.perform(post("/accounts/{id}/deposits", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amountCents": 10000, "description": "salary"}
                                """))
                .andExpect(status().isNoContent());

        mvc.perform(post("/accounts/{id}/withdrawals", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amountCents": 3000, "description": "rent"}
                                """))
                .andExpect(status().isNoContent());

        mvc.perform(get("/accounts/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.owner").value("Ada Lovelace"))
                .andExpect(jsonPath("$.balanceCents").value(7000))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.version").value(3));
    }

    @Test
    void businessRulesSurfaceAs422() throws Exception {
        var id = "smoke-" + UUID.randomUUID();

        mvc.perform(post("/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"accountId": "%s", "owner": "Grace"}
                                """.formatted(id)))
                .andExpect(status().isCreated());

        // overdraft
        mvc.perform(post("/accounts/{id}/withdrawals", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amountCents": 1, "description": "overdraft attempt"}
                                """))
                .andExpect(status().isUnprocessableEntity());

        // deposit, then closing with a balance is rejected
        mvc.perform(post("/accounts/{id}/deposits", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amountCents": 500, "description": "pocket money"}
                                """))
                .andExpect(status().isNoContent());
        mvc.perform(post("/accounts/{id}/close", id))
                .andExpect(status().isUnprocessableEntity());

        // empty it, close it, and the account refuses further business
        mvc.perform(post("/accounts/{id}/withdrawals", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amountCents": 500, "description": "empty it"}
                                """))
                .andExpect(status().isNoContent());
        mvc.perform(post("/accounts/{id}/close", id))
                .andExpect(status().isNoContent());
        mvc.perform(post("/accounts/{id}/deposits", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amountCents": 100, "description": "too late"}
                                """))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void unknownAccountIs404() throws Exception {
        mvc.perform(get("/accounts/{id}", "smoke-never-opened"))
                .andExpect(status().isNotFound());
    }
}
