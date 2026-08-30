package dev.vlearning.library.lending;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.modulith.test.ApplicationModuleTest.BootstrapMode;
import org.springframework.modulith.test.Scenario;
import org.springframework.test.context.jdbc.Sql;

/**
 * Module-scoped integration test: bootstraps the lending module only — plus
 * its direct dependency, catalog, because lending calls the CatalogService
 * facade synchronously (BootstrapMode.DIRECT_DEPENDENCIES). notifications is
 * NOT part of this application context; the LoanCreated event is observed on
 * its way out instead of asserting on another module's behavior.
 *
 * The catalog rows are seeded with plain SQL on purpose: this test must not
 * touch catalog's Java internals — they are another module's private parts.
 */
@ApplicationModuleTest(mode = BootstrapMode.DIRECT_DEPENDENCIES)
@Sql(statements = {
        "INSERT INTO book (id, isbn, title, author) VALUES (905, '978-CP5', 'Refactoring', 'Martin Fowler')",
        "INSERT INTO book_copy (id, book_id, barcode, status) VALUES (905, 905, '978-CP5-1', 'AVAILABLE')"
})
@Disabled("Checkpoint 5 — enable when you start step 5")
class Checkpoint5LendingScenarioTest {

    @Autowired
    LendingService lending;

    @Test
    void borrowingPublishesLoanCreatedWithTheFullEventPayload(Scenario scenario) {
        scenario.stimulate(() -> lending.borrow("978-CP5-1", "cp5@example.com", LocalDate.now().plusDays(21)))
                .andWaitForEventOfType(LoanCreated.class)
                .matching(event -> event.copyBarcode().equals("978-CP5-1"))
                .toArriveAndVerify(event -> {
                    assertThat(event.loanId()).isNotNull();
                    assertThat(event.memberEmail()).isEqualTo("cp5@example.com");
                    assertThat(event.bookTitle()).isEqualTo("Refactoring");
                    assertThat(event.dueDate()).isEqualTo(LocalDate.now().plusDays(21));
                });
    }
}
