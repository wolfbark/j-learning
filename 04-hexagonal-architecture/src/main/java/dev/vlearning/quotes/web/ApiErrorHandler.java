package dev.vlearning.quotes.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import dev.vlearning.quotes.service.QuoteNotFoundException;
import dev.vlearning.quotes.service.UnknownProductException;

@RestControllerAdvice
public class ApiErrorHandler {

    public record ApiError(String error) {
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError badRequest(IllegalArgumentException e) {
        return new ApiError(e.getMessage());
    }

    @ExceptionHandler({UnknownProductException.class, QuoteNotFoundException.class})
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiError notFound(RuntimeException e) {
        return new ApiError(e.getMessage());
    }
}
