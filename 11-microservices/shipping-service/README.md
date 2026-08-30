# shipping-service

One half of lesson **[11 — Microservices: The Real Cost of Distribution](../README.md)** — start there.
Arranges shipments on `POST /shipments`; `POST /chaos {"mode":"OK|SLOW_5S|DOWN"}` makes it slow or dead on demand.
In step 4 it grows a Kafka consumer for `orders.placed` and announces on `shipments.arranged`.
Build/test standalone: `mvn test` (Docker only needed once the Kafka checkpoints are enabled).
Runs on port 8081; see the top-level `docker-compose.yml` for the whole system.
