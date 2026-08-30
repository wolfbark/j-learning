package dev.vlearning.lending.events;

import java.time.LocalDate;

/**
 * Domain event: a fact, named in the past tense, published by the write side
 * once step 2 wires it up. Given here so the checkpoint tests compile; the
 * pristine app publishes nothing yet.
 */
public record BookBorrowed(long loanId, long memberId, long bookId, LocalDate borrowedOn, LocalDate dueOn) {
}
