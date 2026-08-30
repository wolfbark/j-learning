package dev.vlearning.quotes.application;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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

/**
 * Checkpoint 3. The application service is tested through its constructor —
 * no Spring context, no mock framework, just hand-written stand-ins for the
 * two driven ports. RateProviderPort has a single method, so a lambda does.
 */
@Disabled("Checkpoint 3 — enable when you start step 3")
class QuoteApplicationServiceTest {

    private final InMemoryQuoteRepository quoteRepository = new InMemoryQuoteRepository();

    private final QuoteApplicationService service = new QuoteApplicationService(
            quoteRepository,
            productCode -> switch (productCode) {
                case "AUTO" -> Money.euros("90.00");
                case "LIFE" -> Money.euros("120.00");
                default -> throw new IllegalStateException("no rate for " + productCode);
            },
            new QuoteCalculator());

    @Test
    void createQuotePricesThroughTheCalculatorAndPersistsThroughThePort() {
        Quote quote = service.createQuote(new CreateQuoteCommand("AUTO", 30, Set.of()));

        assertThat(quote.monthlyPremium()).isEqualTo(Money.euros("90.00"));
        assertThat(quoteRepository.stored).containsEntry(quote.id(), quote);
    }

    @Test
    void looksUpTheRateForTheRequestedProduct() {
        List<String> requestedCodes = new ArrayList<>();
        RateProviderPort recordingProvider = code -> {
            requestedCodes.add(code);
            return Money.euros("10.00");
        };
        QuoteApplicationService recordingService =
                new QuoteApplicationService(new InMemoryQuoteRepository(), recordingProvider, new QuoteCalculator());

        recordingService.createQuote(new CreateQuoteCommand("HOME", 40, Set.of()));

        assertThat(requestedCodes).containsExactly("HOME");
    }

    @Test
    void surchargesApplyToTheProvidedBaseRate() {
        Quote quote = service.createQuote(
                new CreateQuoteCommand("LIFE", 45, Set.of(RiskFactor.SMOKER)));

        assertThat(quote.monthlyPremium()).isEqualTo(Money.euros("144.00"));
    }

    @Test
    void quoteByIdReadsThroughThePort() {
        Quote created = service.createQuote(new CreateQuoteCommand("AUTO", 30, Set.of()));

        assertThat(service.quoteById(created.id())).contains(created);
        assertThat(service.quoteById(UUID.randomUUID())).isEmpty();
    }

    // --- structural guardrails: green from the start, they document the constraint ---

    @Test
    void implementsBothDrivingPorts() {
        assertThat(CreateQuoteUseCase.class).isAssignableFrom(QuoteApplicationService.class);
        assertThat(GetQuoteQuery.class).isAssignableFrom(QuoteApplicationService.class);
    }

    @Test
    void constructorDependsOnlyOnPortsAndDomain() {
        Constructor<?>[] constructors = QuoteApplicationService.class.getDeclaredConstructors();
        assertThat(constructors).hasSize(1);

        for (Class<?> parameterType : constructors[0].getParameterTypes()) {
            assertThat(parameterType.getPackageName())
                    .as("application service may only depend on ports and the domain, but takes %s",
                            parameterType.getName())
                    .matches(pkg -> pkg.startsWith("dev.vlearning.quotes.application.port")
                            || pkg.startsWith("dev.vlearning.quotes.domain"));
        }
    }

    static class InMemoryQuoteRepository implements QuoteRepository {

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
}
