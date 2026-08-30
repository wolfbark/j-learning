package dev.vlearning.quotes.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import dev.vlearning.quotes.persistence.QuoteEntity;
import dev.vlearning.quotes.persistence.QuoteJpaRepository;

@Service
public class QuoteService {

    private final QuoteJpaRepository quoteJpaRepository;
    private final Environment environment;

    public QuoteService(QuoteJpaRepository quoteJpaRepository, Environment environment) {
        this.quoteJpaRepository = quoteJpaRepository;
        this.environment = environment;
    }

    @Transactional
    public QuoteEntity createQuote(String productCode, int age, List<String> riskFactors) {
        if (age < 18) {
            throw new IllegalArgumentException("Applicants must be at least 18 years old");
        }

        String baseUrl = environment.getProperty("rate-provider.base-url", "http://localhost:8080");
        RestClient restClient = RestClient.create(baseUrl);
        RateResponse rate;
        try {
            rate = restClient.get()
                    .uri("/external/rates/{productCode}", productCode)
                    .retrieve()
                    .body(RateResponse.class);
        } catch (HttpClientErrorException.NotFound notFound) {
            throw new UnknownProductException(productCode);
        }

        BigDecimal load = BigDecimal.ZERO;
        if (age < 25) {
            load = load.add(new BigDecimal("0.30"));
        }
        if (age >= 70) {
            load = load.add(new BigDecimal("0.20"));
        }
        if (riskFactors != null) {
            if (riskFactors.contains("SMOKER")) {
                load = load.add(new BigDecimal("0.20"));
            }
            if (riskFactors.contains("HAZARDOUS_OCCUPATION")) {
                load = load.add(new BigDecimal("0.15"));
            }
            if (riskFactors.contains("PREVIOUS_CLAIMS")) {
                load = load.add(new BigDecimal("0.40"));
            }
        }

        BigDecimal premium = rate.baseRate()
                .multiply(BigDecimal.ONE.add(load))
                .setScale(2, RoundingMode.HALF_UP);

        QuoteEntity entity = new QuoteEntity();
        entity.setId(UUID.randomUUID());
        entity.setProductCode(productCode);
        entity.setAge(age);
        entity.setRiskFactors(riskFactors == null ? "" : String.join(",", riskFactors));
        entity.setMonthlyPremium(premium);
        entity.setCurrency("EUR");
        entity.setCreatedAt(Instant.now());
        return quoteJpaRepository.save(entity);
    }

    @Transactional(readOnly = true)
    public QuoteEntity getQuote(UUID id) {
        return quoteJpaRepository.findById(id)
                .orElseThrow(() -> new QuoteNotFoundException(id));
    }

    public record RateResponse(String productCode, BigDecimal baseRate) {
    }
}
