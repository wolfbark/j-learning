package dev.vlearning.payments.web;

import dev.vlearning.payments.domain.IdempotencyConflictException;
import dev.vlearning.payments.domain.PaymentNotFoundException;
import dev.vlearning.payments.domain.PaymentValidationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(PaymentValidationException.class)
    ResponseEntity<ErrorResponse> invalid(PaymentValidationException e) {
        return ResponseEntity.badRequest().body(ErrorResponse.of("invalid_request", e.getMessage()));
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    ResponseEntity<ErrorResponse> conflict(IdempotencyConflictException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of("idempotency_conflict", e.getMessage()));
    }

    @ExceptionHandler(PaymentNotFoundException.class)
    ResponseEntity<ErrorResponse> notFound(PaymentNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of("not_found", e.getMessage()));
    }
}
