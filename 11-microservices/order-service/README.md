# order-service

One half of lesson **[11 — Microservices: The Real Cost of Distribution](../README.md)** — start there.
Takes orders on `POST /orders` and arranges shipping through the shipping-service:
synchronously over HTTP in phase 1 (deliberately without timeouts), via Kafka events after step 4.
Build/test standalone: `mvn test` (needs Docker for Testcontainers).
Runs on port 8080; see the top-level `docker-compose.yml` for the whole system.
