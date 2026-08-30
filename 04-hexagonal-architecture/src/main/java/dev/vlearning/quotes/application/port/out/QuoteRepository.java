package dev.vlearning.quotes.application.port.out;

import java.util.Optional;
import java.util.UUID;

import dev.vlearning.quotes.domain.Quote;

/**
 * Driven port: what the application NEEDS from persistence, in the domain's
 * language. Compare with QuoteJpaRepository: this interface speaks Quote, not
 * QuoteEntity; it has the two methods the use case needs, not the twenty that
 * JpaRepository inherits; and nothing here mentions JPA, SQL, or Spring Data.
 */
public interface QuoteRepository {

    Quote save(Quote quote);

    Optional<Quote> findById(UUID id);
}
