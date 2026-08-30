#!/usr/bin/env bash
# Exercise the running system (docker compose up first). Watch the timings.
set -u

ORDERS=${ORDERS:-http://localhost:8080}
SHIPPING=${SHIPPING:-http://localhost:8081}

place_order() {
  curl -s -X POST "$ORDERS/orders" \
    -H 'Content-Type: application/json' \
    -H "X-Correlation-Id: demo-$RANDOM" \
    -d '{"customerId":"ada","item":"mechanical keyboard","quantity":1}' \
    -w '\n   -> HTTP %{http_code} in %{time_total}s\n'
  echo
}

set_chaos() {
  echo "chaos mode -> $1"
  curl -s -X POST "$SHIPPING/chaos" -H 'Content-Type: application/json' -d "{\"mode\":\"$1\"}"
  echo; echo
}

echo "== 1. happy path — shipping answers instantly =="
place_order

echo "== 2. chaos SLOW_5S — the caller inherits the callee's latency =="
set_chaos SLOW_5S
echo "placing an order (this is what the customer feels)..."
place_order

echo "== 3. chaos DOWN — watch the failure cascade =="
set_chaos DOWN
place_order

echo "== 4. calm again =="
set_chaos OK
place_order
