package dev.vlearning.quotes.application.port.in;

import java.util.Optional;
import java.util.UUID;

import dev.vlearning.quotes.domain.Quote;

/**
 * Driving port for the read side. Splitting queries from commands at the port
 * level is a judgment call, not a law — see the debrief in step 7.
 */
public interface GetQuoteQuery {

    Optional<Quote> quoteById(UUID id);
}
