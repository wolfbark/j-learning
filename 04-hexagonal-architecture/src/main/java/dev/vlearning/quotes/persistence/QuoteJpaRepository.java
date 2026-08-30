package dev.vlearning.quotes.persistence;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface QuoteJpaRepository extends JpaRepository<QuoteEntity, UUID> {
}
