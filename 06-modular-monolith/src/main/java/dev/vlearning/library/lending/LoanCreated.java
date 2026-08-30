package dev.vlearning.library.lending;

import java.time.LocalDate;

/**
 * Domain event: a member borrowed a copy. Part of the lending module's
 * published API — this record IS the contract other modules may rely on.
 *
 * Given up front because the checkpoint tests are compiled against it.
 * Nothing publishes it yet; that is your job in step 3.
 */
public record LoanCreated(Long loanId, String memberEmail, String bookTitle, String copyBarcode, LocalDate dueDate) {
}
