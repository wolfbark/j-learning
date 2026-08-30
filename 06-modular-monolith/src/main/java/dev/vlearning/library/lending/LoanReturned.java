package dev.vlearning.library.lending;

/**
 * Domain event: a loan ended because the copy came back. Part of the lending
 * module's published API.
 *
 * Given up front because the checkpoint tests are compiled against it.
 * Nothing publishes it yet; that is your job in step 4.
 */
public record LoanReturned(Long loanId, String copyBarcode) {
}
