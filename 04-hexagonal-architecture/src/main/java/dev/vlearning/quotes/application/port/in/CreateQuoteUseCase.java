package dev.vlearning.quotes.application.port.in;

import java.util.Set;

import dev.vlearning.quotes.domain.Quote;
import dev.vlearning.quotes.domain.RiskFactor;

/**
 * Driving port: what the outside world may ask the application to DO.
 * Driving adapters (the REST controller, but equally a CLI, a scheduler,
 * or a test) call this; they never touch the application service directly
 * by any other name.
 */
public interface CreateQuoteUseCase {

    Quote createQuote(CreateQuoteCommand command);

    record CreateQuoteCommand(String productCode, int age, Set<RiskFactor> riskFactors) {
    }
}
