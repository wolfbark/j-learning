package dev.vlearning.library;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;

/**
 * Pins the current HTTP behavior of the app. These tests are enabled and must
 * stay green through every refactoring step. They know nothing about packages,
 * modules, or events — only about the HTTP contract. That is what makes them
 * the safety net for the tangle-to-modulith refactoring.
 *
 * Notification assertions poll (Awaitility): the tangled code sends
 * synchronously, but once you switch to @ApplicationModuleListener in step 3,
 * messages go out asynchronously after the transaction commits. The polling
 * makes the tests correct in both worlds. Deliberately NOT @Transactional —
 * a rolled-back test transaction would never trigger after-commit listeners.
 */
@SpringBootTest
@AutoConfigureMockMvc
class LibraryApiBehaviorTest {

    @Autowired
    MockMvc mvc;

    @Test
    void addingABookCreatesAvailableCopies() throws Exception {
        List<String> barcodes = addBook("978-1-0001", "Clean Architecture", "Robert C. Martin", 2);

        assertThat(barcodes).containsExactly("978-1-0001-1", "978-1-0001-2");
        assertThat(copyStatus("978-1-0001-1")).isEqualTo("AVAILABLE");
        assertThat(copyStatus("978-1-0001-2")).isEqualTo("AVAILABLE");
    }

    @Test
    void borrowingMarksTheCopyOnLoan() throws Exception {
        List<String> barcodes = addBook("978-1-0002", "Refactoring", "Martin Fowler", 1);

        mvc.perform(post("/loans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"barcode": "%s", "memberEmail": "anna@example.com", "dueDate": "%s"}
                                """.formatted(barcodes.getFirst(), LocalDate.now().plusDays(14))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.bookTitle").value("Refactoring"))
                .andExpect(jsonPath("$.returnedAt", nullValue()));

        assertThat(copyStatus(barcodes.getFirst())).isEqualTo("ON_LOAN");
    }

    @Test
    void borrowingSendsAConfirmationNotification() throws Exception {
        List<String> barcodes = addBook("978-1-0003", "Domain-Driven Design", "Eric Evans", 1);
        borrow(barcodes.getFirst(), "bela@example.com", LocalDate.now().plusDays(7));

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(notifications()).anyMatch(message ->
                        message.contains("bela@example.com")
                                && message.contains("Domain-Driven Design")
                                && message.contains(barcodes.getFirst())));
    }

    @Test
    void borrowingAnUnavailableCopyIsRejected() throws Exception {
        List<String> barcodes = addBook("978-1-0004", "A Philosophy of Software Design", "John Ousterhout", 1);
        borrow(barcodes.getFirst(), "cleo@example.com", LocalDate.now().plusDays(14));

        mvc.perform(post("/loans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"barcode": "%s", "memberEmail": "dora@example.com", "dueDate": "%s"}
                                """.formatted(barcodes.getFirst(), LocalDate.now().plusDays(14))))
                .andExpect(status().isConflict());
    }

    @Test
    void borrowingAnUnknownBarcodeIs404() throws Exception {
        mvc.perform(post("/loans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"barcode": "no-such-copy", "memberEmail": "emil@example.com", "dueDate": "%s"}
                                """.formatted(LocalDate.now().plusDays(14))))
                .andExpect(status().isNotFound());
    }

    @Test
    void returningMakesTheCopyBorrowableAgain() throws Exception {
        List<String> barcodes = addBook("978-1-0005", "Working Effectively with Legacy Code", "Michael Feathers", 1);
        String loanBody = borrow(barcodes.getFirst(), "filip@example.com", LocalDate.now().plusDays(14));
        Number loanId = JsonPath.read(loanBody, "$.id");

        mvc.perform(post("/loans/{id}/return", loanId.longValue()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.returnedAt", notNullValue()));

        assertThat(copyStatus(barcodes.getFirst())).isEqualTo("AVAILABLE");
        borrow(barcodes.getFirst(), "greta@example.com", LocalDate.now().plusDays(14));
    }

    @Test
    void overdueRunFlagsLateLoansOnly() throws Exception {
        List<String> barcodes = addBook("978-1-0006", "Release It!", "Michael Nygard", 2);
        String lateBarcode = barcodes.get(0);
        String promptBarcode = barcodes.get(1);
        borrow(lateBarcode, "late@example.com", LocalDate.now().minusDays(3));
        borrow(promptBarcode, "prompt@example.com", LocalDate.now().plusDays(14));

        // Poll: after step 3 the notifications module learns about loans
        // asynchronously, so a single immediate overdue run could miss them.
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            mvc.perform(post("/notifications/overdue-run")).andExpect(status().isOk());
            assertThat(notifications()).anyMatch(message ->
                    message.contains("OVERDUE") && message.contains(lateBarcode));
        });

        assertThat(notifications()).noneMatch(message ->
                message.contains("OVERDUE") && message.contains(promptBarcode));
    }

    // --- helpers ---------------------------------------------------------

    private List<String> addBook(String isbn, String title, String author, int copies) throws Exception {
        String body = mvc.perform(post("/catalog/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"isbn": "%s", "title": "%s", "author": "%s", "copies": %d}
                                """.formatted(isbn, title, author, copies)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.copyBarcodes");
    }

    private String borrow(String barcode, String memberEmail, LocalDate dueDate) throws Exception {
        return mvc.perform(post("/loans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"barcode": "%s", "memberEmail": "%s", "dueDate": "%s"}
                                """.formatted(barcode, memberEmail, dueDate)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    private String copyStatus(String barcode) throws Exception {
        String body = mvc.perform(get("/catalog/books"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<String> statuses = JsonPath.read(body,
                "$[*].copies[?(@.barcode == '%s')].status".formatted(barcode));
        return statuses.getFirst();
    }

    private List<String> notifications() throws Exception {
        String body = mvc.perform(get("/notifications"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$");
    }
}
