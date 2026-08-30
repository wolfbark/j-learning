package dev.vlearning.quotes.application.port.out;

import dev.vlearning.quotes.domain.Money;

/**
 * Driven port: the market base rate for a product. Round 1 hardwires an HTTP
 * call for this; behind a port it could be HTTP, a cache, a file, or a
 * one-line lambda in a test. Adapters translate "provider says 404" into the
 * application's own unknown-product failure — decide where that exception
 * should live when you refactor in step 4.
 */
public interface RateProviderPort {

    Money baseRateFor(String productCode);
}
