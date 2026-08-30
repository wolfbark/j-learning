package dev.vlearning.quotes.application;

import java.util.Optional;
import java.util.UUID;

import dev.vlearning.quotes.application.port.in.CreateQuoteUseCase;
import dev.vlearning.quotes.application.port.in.GetQuoteQuery;
import dev.vlearning.quotes.application.port.out.QuoteRepository;
import dev.vlearning.quotes.application.port.out.RateProviderPort;
import dev.vlearning.quotes.domain.Quote;
import dev.vlearning.quotes.domain.QuoteCalculator;

/**
 * The application service: implements the driving ports by orchestrating the
 * domain and the driven ports. Nothing more. If you find business rules
 * creeping in here, they belong in the domain; if you find HTTP or SQL
 * creeping in, they belong in an adapter.
 *
 * The constructor is provided so the checkpoint tests compile — notice that
 * every parameter is either a port or a pure domain object.
 */
public class QuoteApplicationService implements CreateQuoteUseCase, GetQuoteQuery {

    private final QuoteRepository quoteRepository;
    private final RateProviderPort rateProvider;
    private final QuoteCalculator quoteCalculator;

    public QuoteApplicationService(QuoteRepository quoteRepository,
                                   RateProviderPort rateProvider,
                                   QuoteCalculator quoteCalculator) {
        this.quoteRepository = quoteRepository;
        this.rateProvider = rateProvider;
        this.quoteCalculator = quoteCalculator;
    }

    @Override
    public Quote createQuote(CreateQuoteCommand command) {
        // TODO Step 3: build the RiskProfile, fetch the base rate through the
        //  RateProviderPort, price with the QuoteCalculator, persist through
        //  the QuoteRepository port, return the saved Quote.
        throw new UnsupportedOperationException("Checkpoint 3: orchestrate ports and domain");
    }

    @Override
    public Optional<Quote> quoteById(UUID id) {
        // TODO Step 3: read through the QuoteRepository port.
        throw new UnsupportedOperationException("Checkpoint 3: read through the port");
    }
}
