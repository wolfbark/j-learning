package dev.vlearning.registration.enrollment;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class ApiExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    Map<String, String> badRequest(IllegalArgumentException e) {
        return Map.of("error", String.valueOf(e.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    Map<String, String> conflict(IllegalStateException e) {
        return Map.of("error", String.valueOf(e.getMessage()));
    }

    @ExceptionHandler({CourseNotFoundException.class, EnrollmentNotFoundException.class})
    @ResponseStatus(HttpStatus.NOT_FOUND)
    Map<String, String> notFound(RuntimeException e) {
        return Map.of("error", String.valueOf(e.getMessage()));
    }
}
