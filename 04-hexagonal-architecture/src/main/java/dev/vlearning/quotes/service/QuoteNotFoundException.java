package dev.vlearning.quotes.service;

import java.util.UUID;

public class QuoteNotFoundException extends RuntimeException {

    public QuoteNotFoundException(UUID id) {
        super("No quote with id " + id);
    }
}
