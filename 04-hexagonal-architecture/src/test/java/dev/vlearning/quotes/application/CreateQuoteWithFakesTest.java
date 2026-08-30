package dev.vlearning.quotes.application;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import dev.vlearning.quotes.application.port.in.CreateQuoteUseCase;
import dev.vlearning.quotes.application.port.in.CreateQuoteUseCase.CreateQuoteCommand;
import dev.vlearning.quotes.application.port.in.GetQuoteQuery;
import dev.vlearning.quotes.application.port.out.QuoteRepository;
import dev.vlearning.quotes.application.port.out.RateProviderPort;
import dev.vlearning.quotes.domain.Money;
import dev.vlearning.quotes.domain.Quote;
import dev.vlearning.quotes.domain.QuoteCalculator;
import dev.vlearning.quotes.domain.RiskFactor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Checkpoint 5 — the payoff. This is the ENTIRE use case, end to end: driving
 * port in, domain in the middle, driven ports out. Both driven adapters have
 * been swapped for in-memory fakes, so there is no Spring context, no Tomcat,
 * no H2, no HTTP — and no 50 ms stub-provider latency. Run this class and the
 * integration test class back to back and compare the clock.
 *
 * Note that the test talks to the hexagon strictly through the driving port
 * interfaces — exactly like the REST controller does.
 */
@Disabled("Checkpoint 5 — enable when you start step 5")
class CreateQuoteWithFakesTest {

    private final InMemoryQuotes quotes = new InMemoryQuotes();
    private final FixedRates rates = new FixedRates(Map.of(
            "AUTO", Money.euros("90.00"),
            "LIFE", Money.euros("120.00")));

    private final QuoteApplicationService applicationService =
            new QuoteApplicationService(quotes, rates, new QuoteCalculator());

    private final CreateQuoteUseCase createQuote = applicationService;
    private final GetQuoteQuery getQuote = applicationService;

    @Test
    void fullUseCaseWithoutAFrameworkInSight() {
        Quote created = createQuote.createQuote(
                new CreateQuoteCommand("AUTO", 22, Set.of(RiskFactor.PREVIOUS_CLAIMS)));

        assertThat(created.monthlyPremium()).isEqualTo(Money.euros("153.00"));
        assertThat(getQuote.quoteById(created.id())).contains(created);
    }

    @Test
    void quotesAccumulateInTheFakeJustLikeInTheRealAdapter() {
        Quote first = createQuote.createQuote(new CreateQuoteCommand("AUTO", 30, Set.of()));
        Quote second = createQuote.createQuote(new CreateQuoteCommand("LIFE", 45, Set.of(RiskFactor.SMOKER)));

        assertThat(getQuote.quoteById(first.id())).contains(first);
        assertThat(getQuote.quoteById(second.id())).contains(second);
        assertThat(quotes.stored).hasSize(2);
    }

    @Test
    void domainRulesStillHoldBehindThePorts() {
        assertThatThrownBy(() -> createQuote.createQuote(new CreateQuoteCommand("AUTO", 17, Set.of())))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(quotes.stored).isEmpty();
    }

    @Test
    void aFailingRateProviderIsOneLambdaAway() {
        RateProviderPort brokenProvider = productCode -> {
            throw new IllegalStateException("rate provider is down");
        };
        CreateQuoteUseCase useCase =
                new QuoteApplicationService(new InMemoryQuotes(), brokenProvider, new QuoteCalculator());

        assertThatThrownBy(() -> useCase.createQuote(new CreateQuoteCommand("AUTO", 30, Set.of())))
                .isInstanceOf(IllegalStateException.class);
    }

    static class InMemoryQuotes implements QuoteRepository {

        final Map<UUID, Quote> stored = new HashMap<>();

        @Override
        public Quote save(Quote quote) {
            stored.put(quote.id(), quote);
            return quote;
        }

        @Override
        public Optional<Quote> findById(UUID id) {
            return Optional.ofNullable(stored.get(id));
        }
    }

    record FixedRates(Map<String, Money> rates) implements RateProviderPort {

        @Override
        public Money baseRateFor(String productCode) {
            Money rate = rates.get(productCode);
            if (rate == null) {
                throw new IllegalStateException("No rate for " + productCode);
            }
            return rate;
        }
    }
}
