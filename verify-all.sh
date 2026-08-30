#!/usr/bin/env bash
# Build and test every project in the training repo.
#
#   ./verify-all.sh            # all projects
#   ./verify-all.sh 07 08 14   # only these
#
# A project is "PASS" when `mvn test` succeeds with the checkpoint tests still
# @Disabled — i.e. the scaffold is in its delivered state. As you work through a
# lesson and enable checkpoints, the Skipped count drops; that is progress, not
# breakage. Docker must be running for projects 07 onward.
set -uo pipefail
cd "$(dirname "$0")"

ALL=(01-modern-java 02-tdd 03-vertical-slices 04-hexagonal-architecture 05-ddd 06-modular-monolith
     07-events-and-outbox 08-cqrs 09-event-sourcing 10-sagas
     11-microservices/order-service 11-microservices/shipping-service
     12-testing-strategy/order-service 12-testing-strategy/payment-service
     13-bdd 14-virtual-threads 15-production-readiness 16-ai-backend
     17-api-security 18-messaging-mechanics 19-reliability-slo
     20-transactions 21-locking 22-distributed-locking)

if [ $# -gt 0 ]; then
    TARGETS=()
    for prefix in "$@"; do
        for project in "${ALL[@]}"; do
            [[ "$project" == "$prefix"* ]] && TARGETS+=("$project")
        done
    done
else
    TARGETS=("${ALL[@]}")
fi

failed=0
for project in "${TARGETS[@]}"; do
    [ -f "$project/pom.xml" ] || { printf '%-38s SKIP (not present)\n' "$project"; continue; }
    log=$(cd "$project" && mvn -B test 2>&1)
    summary=$(printf '%s\n' "$log" | grep -E '^\[INFO\] Tests run:.*Skipped:' | tail -1 | sed 's/^\[INFO\] //')
    if printf '%s\n' "$log" | grep -q 'BUILD SUCCESS'; then
        printf '%-38s PASS  %s\n' "$project" "$summary"
    else
        printf '%-38s FAIL\n' "$project"
        printf '%s\n' "$log" | grep -E '^\[ERROR\]' | head -5 | sed 's/^/    /'
        failed=1
    fi
done
exit $failed
