package dev.vlearning.lending;

import java.util.NoSuchElementException;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps domain refusals to HTTP: unknown things are 404, rule violations 409.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    record ApiError(String error) {
    }

    @ExceptionHandler(NoSuchElementException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    ApiError notFound(NoSuchElementException exception) {
        return new ApiError(exception.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    ApiError conflict(IllegalStateException exception) {
        return new ApiError(exception.getMessage());
    }
}
