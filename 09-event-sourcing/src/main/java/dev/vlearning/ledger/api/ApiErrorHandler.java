package dev.vlearning.ledger.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import dev.vlearning.ledger.application.AccountNotFoundException;
import dev.vlearning.ledger.domain.CommandRejectedException;
import dev.vlearning.ledger.eventstore.ConcurrencyException;

/**
 * Given. The status codes carry meaning:
 * 422 — the domain said no (retrying the same request will fail again);
 * 409 — a concurrent writer won the race (retrying against fresh state may well succeed).
 * Collapsing both into one code robs the client of that distinction.
 */
@RestControllerAdvice
class ApiErrorHandler {

    @ExceptionHandler(CommandRejectedException.class)
    ProblemDetail commandRejected(CommandRejectedException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
    }

    @ExceptionHandler(ConcurrencyException.class)
    ProblemDetail concurrentAppend(ConcurrencyException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(AccountNotFoundException.class)
    ProblemDetail notFound(AccountNotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }
}
