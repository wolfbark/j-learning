# payment-service

The **provider** half of lesson **[12 — Testing Strategy: Two Services, No E2E Allowed](../README.md)** — start there.
Authorizes payments on `POST /payments` (idempotency key in the `Idempotency-Key` header) and serves `GET /payments/{id}`, persisting to Postgres.
Given complete and working: your job in this lesson is the *tests* — a Testcontainers integration suite, provider-side contract verification, and ArchUnit fitness functions.
Build/test standalone: `mvn test` (needs Docker for Testcontainers; pristine run is green with checkpoints disabled).
Runs on port 8081.
