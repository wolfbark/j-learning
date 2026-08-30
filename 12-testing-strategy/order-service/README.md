# order-service

The **consumer** half of lesson **[12 — Testing Strategy: Two Services, No E2E Allowed](../README.md)** — start there.
Prices orders (`OrderPricer` — tiered discounts, currency-aware rounding) and authorizes payment through the payment-service over a declarative `@HttpExchange` client.
Given complete and working, with deliberately *incomplete* unit tests: this is the mutation-testing and Pact-consumer side of the lesson.
Build/test standalone: `mvn test` (no Docker needed). Mutation gate: `mvn -Pmutation verify`.
Runs on port 8080; expects the payment-service on 8081.
