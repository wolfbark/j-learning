package dev.vlearning.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

/**
 * CHECKPOINT 6 — time travel. Note what this test does NOT import: no FeeCharged record, no
 * fees projector. It writes raw event rows the way a year-old deployment would have left
 * them, then talks to the system purely over HTTP and SQL. The history below was seeded
 * long before anyone imagined a monthly fees report — and the report gets built anyway,
 * because the events, not any table, are the system of record.
 *
 * The payload shapes below are the contract your step-6 records must deserialize:
 *   FeeCharged  {"accountId": …, "amountCents": …, "month": "2026-01"}
 *   FeeRefunded {"accountId": …, "amountCents": …, "month": "2026-01"}
 */
@SpringBootTest(properties = "ledger.event-store=postgres")
@AutoConfigureMockMvc
@Disabled("Checkpoint 6 — enable when you start step 6")
class Checkpoint6TimeTravelTest extends PostgresTestBase {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcClient jdbc;

    String ada;
    String bob;

    @BeforeEach
    void seedHistoryTheWayOldCodeLeftIt() {
        ada = "cp6-" + UUID.randomUUID();
        bob = "cp6-" + UUID.randomUUID();

        // An "old" stream: opened months ago, deposits only — it never heard of fees…
        insert(ada, 1, "AccountOpened", """
                {"accountId": "%s", "owner": "Ada"}""".formatted(ada));
        insert(ada, 2, "MoneyDeposited", """
                {"accountId": "%s", "amountCents": 10000, "description": "January salary"}""".formatted(ada));
        insert(ada, 3, "MoneyDeposited", """
                {"accountId": "%s", "amountCents": 5000, "description": "February bonus"}""".formatted(ada));

        // …until the new fee-billing feature appended new event types on top of it:
        insert(ada, 4, "FeeCharged", """
                {"accountId": "%s", "amountCents": 500, "month": "2026-01"}""".formatted(ada));
        insert(ada, 5, "FeeCharged", """
                {"accountId": "%s", "amountCents": 500, "month": "2026-02"}""".formatted(ada));
        insert(ada, 6, "FeeRefunded", """
                {"accountId": "%s", "amountCents": 500, "month": "2026-01"}""".formatted(ada));

        // A second old stream with no fee events at all — replay must simply pass it by.
        insert(bob, 1, "AccountOpened", """
                {"accountId": "%s", "owner": "Bob"}""".formatted(bob));
        insert(bob, 2, "MoneyDeposited", """
                {"accountId": "%s", "amountCents": 700, "description": "pocket money"}""".formatted(bob));
    }

    @Test
    void aReadModelNobodyAnticipatedIsBuiltFromFullHistory() throws Exception {
        mvc.perform(post("/admin/projections/monthly-fees/rebuild"))
                .andExpect(status().isAccepted());

        var rows = jdbc.sql("""
                        SELECT month, fees_charged_cents, fees_refunded_cents
                        FROM monthly_fees_report WHERE account_id = :id ORDER BY month
                        """)
                .param("id", ada)
                .query()
                .listOfRows();

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).get("month")).isEqualTo("2026-01");
        assertThat(rows.get(0).get("fees_charged_cents")).isEqualTo(500L);
        assertThat(rows.get(0).get("fees_refunded_cents")).isEqualTo(500L);
        assertThat(rows.get(1).get("month")).isEqualTo("2026-02");
        assertThat(rows.get(1).get("fees_charged_cents")).isEqualTo(500L);
        assertThat(rows.get(1).get("fees_refunded_cents")).isEqualTo(0L);

        assertThat(feeRowsFor(bob)).as("no fees, no rows — but replay must not choke on his stream").isZero();
    }

    @Test
    void rehydrationUnderstandsTheNewHistoryToo() throws Exception {
        // 10000 + 5000 - 500 - 500 + 500: evolve() now speaks fees, so the fold does.
        mvc.perform(get("/accounts/{id}", ada))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balanceCents").value(14500))
                .andExpect(jsonPath("$.version").value(6));
    }

    private void insert(String streamId, long version, String type, String payload) {
        jdbc.sql("""
                        INSERT INTO events (stream_id, version, type, payload)
                        VALUES (:streamId, :version, :type, CAST(:payload AS jsonb))
                        """)
                .param("streamId", streamId)
                .param("version", version)
                .param("type", type)
                .param("payload", payload)
                .update();
    }

    private long feeRowsFor(String accountId) {
        return jdbc.sql("SELECT count(*) FROM monthly_fees_report WHERE account_id = :id")
                .param("id", accountId)
                .query(Long.class)
                .single();
    }
}
