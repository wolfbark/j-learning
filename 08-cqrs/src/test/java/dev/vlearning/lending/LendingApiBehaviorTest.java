package dev.vlearning.lending;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pins the HTTP behavior of the pristine app. These tests are ENABLED and must
 * stay green through every step of the lesson: they know nothing about
 * packages, events or read models — only about the HTTP contract. They are
 * your safety net while you take LibraryService apart.
 *
 * The clock is frozen at 2026-09-01 (see src/test/resources), so "today" is
 * always 2026-09-01 and every seeded date below is stable.
 */
@SpringBootTest
@AutoConfigureMockMvc
class LendingApiBehaviorTest extends AbstractIntegrationTest {

    @Test
    void borrowingCreatesALoanDueInThreeWeeks() throws Exception {
        String body = mvc.perform(post("/api/loans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"memberId": 7, "bookId": 11}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.loanId", notNullValue()))
                .andExpect(jsonPath("$.dueOn").value("2026-09-22"))
                .andReturn().getResponse().getContentAsString();

        long loanId = ((Number) com.jayway.jsonpath.JsonPath.read(body, "$.loanId")).longValue();
        LocalDate borrowedOn = jdbc.sql("SELECT borrowed_on FROM loans WHERE id = :id")
                .param("id", loanId).query(LocalDate.class).single();
        assertThat(borrowedOn).isEqualTo(LocalDate.of(2026, 9, 1));
    }

    @Test
    void borrowingIsRejectedWhenEveryCopyIsOut() throws Exception {
        // Book 13 (Accelerate) has a single copy, held open by member 6.
        mvc.perform(post("/api/loans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"memberId": 8, "bookId": 13}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error", containsString("copies")));
    }

    @Test
    void borrowingIsRejectedAtTheOpenLoanLimit() throws Exception {
        // Member 6 holds five open loans — the limit.
        mvc.perform(post("/api/loans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"memberId": 6, "bookId": 5}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error", containsString("limit")));
    }

    @Test
    void borrowingForUnknownMemberOrBookIs404() throws Exception {
        mvc.perform(post("/api/loans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"memberId": 999, "bookId": 1}
                                """))
                .andExpect(status().isNotFound());

        mvc.perform(post("/api/loans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"memberId": 7, "bookId": 999}
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    void returningClosesTheLoanExactlyOnce() throws Exception {
        long loanId = borrow(8, 9);

        mvc.perform(post("/api/loans/{id}/return", loanId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.returnedOn").value("2026-09-01"))
                .andExpect(jsonPath("$.late").value(false));

        mvc.perform(post("/api/loans/{id}/return", loanId))
                .andExpect(status().isConflict());

        mvc.perform(post("/api/loans/{id}/return", 99999))
                .andExpect(status().isNotFound());
    }

    @Test
    void memberActivityAggregatesLoanHistoryAcrossTables() throws Exception {
        mvc.perform(get("/api/members/1/activity"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberId").value(1))
                .andExpect(jsonPath("$.name").value("Maya Chen"))
                .andExpect(jsonPath("$.email").value("maya.chen@example.com"))
                .andExpect(jsonPath("$.totalLoans").value(12))
                .andExpect(jsonPath("$.openLoans").value(4))
                .andExpect(jsonPath("$.returnedLoans").value(8))
                .andExpect(jsonPath("$.lateReturns").value(2))
                .andExpect(jsonPath("$.distinctBooks").value(11))
                .andExpect(jsonPath("$.distinctAuthors").value(10))
                .andExpect(jsonPath("$.favoriteAuthor").value("Martin Fowler"))
                .andExpect(jsonPath("$.lastActivityOn").value("2026-08-25"))
                .andExpect(jsonPath("$.currentLoans", hasSize(4)))
                // sorted by due date; the first one is overdue on 2026-09-01
                .andExpect(jsonPath("$.currentLoans[0].loanId").value(9))
                .andExpect(jsonPath("$.currentLoans[0].title").value("Building Microservices"))
                .andExpect(jsonPath("$.currentLoans[0].dueOn").value("2026-08-15"))
                .andExpect(jsonPath("$.currentLoans[0].overdue").value(true))
                .andExpect(jsonPath("$.currentLoans[1].loanId").value(10))
                .andExpect(jsonPath("$.currentLoans[1].overdue").value(false))
                .andExpect(jsonPath("$.currentLoans[3].loanId").value(12))
                .andExpect(jsonPath("$.currentLoans[3].dueOn").value("2026-09-15"));
    }

    @Test
    void favoriteAuthorPrefersMostBorrowedThenAlphabetical() throws Exception {
        // Jonas: Eric Evans 2x vs Vaughn Vernon 2x -> alphabetical winner
        mvc.perform(get("/api/members/2/activity"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.favoriteAuthor").value("Eric Evans"))
                .andExpect(jsonPath("$.totalLoans").value(6))
                .andExpect(jsonPath("$.lateReturns").value(1));

        // Tomasz: five authors with one loan each -> pure alphabetical
        mvc.perform(get("/api/members/4/activity"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.favoriteAuthor").value("Andrew Hunt"))
                .andExpect(jsonPath("$.openLoans").value(0))
                .andExpect(jsonPath("$.lateReturns").value(2))
                .andExpect(jsonPath("$.currentLoans", hasSize(0)));
    }

    @Test
    void returnOnTheDueDateItselfIsNotLate() throws Exception {
        // Priya's loan 20 was returned exactly on its due date.
        mvc.perform(get("/api/members/3/activity"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalLoans").value(10))
                .andExpect(jsonPath("$.lateReturns").value(2));
    }

    @Test
    void memberWithNoLoansGetsAnEmptyDashboard() throws Exception {
        mvc.perform(get("/api/members/5/activity"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Aino Virtanen"))
                .andExpect(jsonPath("$.totalLoans").value(0))
                .andExpect(jsonPath("$.openLoans").value(0))
                .andExpect(jsonPath("$.favoriteAuthor", nullValue()))
                .andExpect(jsonPath("$.lastActivityOn", nullValue()))
                .andExpect(jsonPath("$.currentLoans", hasSize(0)));
    }

    @Test
    void activityForUnknownMemberIs404() throws Exception {
        mvc.perform(get("/api/members/999/activity"))
                .andExpect(status().isNotFound());
    }

    /**
     * Read-your-writes: in the pristine single-model app this is trivially
     * true. It must STAY true after step 3 flips the dashboard to the view —
     * the after-commit projector runs synchronously, so the write is projected
     * before the HTTP response returns. Step 4 shows what happens when it
     * isn't (that is what the ProjectionToggle is for).
     */
    @Test
    void borrowingShowsUpInTheDashboardImmediately() throws Exception {
        Map<String, Object> before = activity(7);
        int openBefore = (int) before.get("openLoans");

        long loanId = borrow(7, 14);

        Map<String, Object> after = activity(7);
        assertThat((int) after.get("openLoans")).isEqualTo(openBefore + 1);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> currentLoans = (List<Map<String, Object>>) after.get("currentLoans");
        assertThat(currentLoans)
                .anyMatch(loan -> ((Number) loan.get("loanId")).longValue() == loanId);
    }
}
