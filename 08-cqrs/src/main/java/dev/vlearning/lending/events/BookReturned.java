package dev.vlearning.lending.events;

import java.time.LocalDate;

/**
 * Domain event: the counterpart to {@link BookBorrowed}. Published by the
 * return command once step 2 wires it up.
 */
public record BookReturned(long loanId, long memberId, long bookId, LocalDate returnedOn, boolean late) {
}
