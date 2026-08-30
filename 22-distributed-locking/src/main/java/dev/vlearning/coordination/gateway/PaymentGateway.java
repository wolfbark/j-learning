package dev.vlearning.coordination.gateway;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.stereotype.Component;

/**
 * Somebody else's system, and the reason this project exists.
 *
 * <p>Everything in projects 20 and 21 could be undone by rolling back a
 * transaction. A charge cannot. Once this call returns, money has moved in a
 * system your transaction has no authority over, and no lock you hold in your
 * own database can reach in and take it back.
 *
 * <p>Three doors, deliberately: the one that trusts you, the one that checks a
 * fencing token, and the one that de-duplicates on an idempotency key. Steps 5
 * and 7 are about earning the right to use the second and third.
 */
@Component
public class PaymentGateway {

    private final List<Charge> charges = new CopyOnWriteArrayList<>();
    private final Set<String> idempotencyKeys = ConcurrentHashMap.newKeySet();

    private volatile long highestTokenSeen = 0;

    public record Charge(String customer, long amount, String by) {
    }

    /** The naive door. Whoever calls it, whenever they call it, is believed. */
    public void charge(String customer, long amount, String by) {
        charges.add(new Charge(customer, amount, by));
    }

    /**
     * The fenced door. A caller must present a token from the lock service; the
     * gateway remembers the highest it has ever seen and refuses anything older.
     * Equal is fine: one lease legitimately makes many calls.
     *
     * <p>This is the piece that makes distributed locking actually safe, and the
     * piece almost nobody implements — largely because it requires cooperation
     * from the resource being protected, which is often not yours to change.
     */
    public void chargeFenced(long fencingToken, String customer, long amount, String by) {
        synchronized (this) {
            if (fencingToken < highestTokenSeen) {
                throw new StaleTokenException(fencingToken, highestTokenSeen, by);
            }
            highestTokenSeen = fencingToken;
        }
        charges.add(new Charge(customer, amount, by));
    }

    /**
     * The idempotent door. A repeated key is not an error and not a second
     * charge — it is the same charge, acknowledged again. This is what every
     * real payment API offers, and what {@code 07-events-and-outbox} calls an
     * idempotent consumer.
     */
    public void chargeIdempotent(String idempotencyKey, String customer, long amount, String by) {
        if (!idempotencyKeys.add(idempotencyKey)) {
            return;
        }
        charges.add(new Charge(customer, amount, by));
    }

    public List<Charge> charges() {
        return List.copyOf(charges);
    }

    public long chargesFor(String customer) {
        return charges.stream().filter(c -> c.customer().equals(customer)).count();
    }

    public long totalCharged() {
        return charges.stream().mapToLong(Charge::amount).sum();
    }

    public void reset() {
        charges.clear();
        idempotencyKeys.clear();
        highestTokenSeen = 0;
    }

    /** What a fenced resource says to a process that no longer holds the lock. */
    public static class StaleTokenException extends RuntimeException {

        public StaleTokenException(long presented, long highestSeen, String by) {
            super("%s presented fencing token %d; %d has already been seen".formatted(by, presented, highestSeen));
        }
    }
}
