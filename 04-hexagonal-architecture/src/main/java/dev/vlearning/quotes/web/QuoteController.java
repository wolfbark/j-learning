package dev.vlearning.quotes.web;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.vlearning.quotes.persistence.QuoteEntity;
import dev.vlearning.quotes.service.QuoteService;

@RestController
@RequestMapping("/quotes")
public class QuoteController {

    private final QuoteService quoteService;

    public QuoteController(QuoteService quoteService) {
        this.quoteService = quoteService;
    }

    public record CreateQuoteRequest(String productCode, int age, List<String> riskFactors) {
    }

    @PostMapping
    public ResponseEntity<QuoteEntity> createQuote(@RequestBody CreateQuoteRequest request) {
        QuoteEntity quote = quoteService.createQuote(request.productCode(), request.age(), request.riskFactors());
        return ResponseEntity.created(URI.create("/quotes/" + quote.getId())).body(quote);
    }

    @GetMapping("/{id}")
    public QuoteEntity getQuote(@PathVariable UUID id) {
        return quoteService.getQuote(id);
    }
}
