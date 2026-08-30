package dev.vlearning.coordination.billing;

import java.util.List;

import dev.vlearning.coordination.gateway.PaymentGateway;
import dev.vlearning.coordination.locking.AdvisoryLock;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The work itself: invoice every customer for a period and charge them.
 *
 * <p>Coordination lives next door in {@link BillingWorker}. Keeping them apart
 * is the point — the work is the same however you decide who is allowed to run
 * it, and the interesting failures are all in the deciding.
 */
@Service
public class Billing {

    /** Any application-chosen constant; advisory locks are just numbers to Postgres. */
    public static final long BILLING_LOCK_KEY = 90_210L;

    private final JdbcClient jdbc;
    private final PaymentGateway gateway;
    private final AdvisoryLock advisoryLock;

    Billing(JdbcClient jdbc, PaymentGateway gateway, AdvisoryLock advisoryLock) {
        this.jdbc = jdbc;
        this.gateway = gateway;
        this.advisoryLock = advisoryLock;
    }

    @Transactional
    public void bill(String period, String workerId) {
        for (Customer customer : customers()) {
            invoice(customer, period, workerId);
            gateway.charge(customer.name(), customer.amount(), workerId);
        }
    }

    /**
     * Step 5: the same billing run, but every charge presents the token this
     * worker was given when it took the lease.
     */
    @Transactional
    public void billFenced(String period, String workerId, long fencingToken) {
        for (Customer customer : customers()) {
            invoice(customer, period, workerId);
            gateway.chargeFenced(fencingToken, customer.name(), customer.amount(), workerId);
        }
    }

    /**
     * Step 7: the same billing run, safe to execute twice — which is the only
     * property that has held up so far under every failure in this project.
     */
    @Transactional
    public void billIdempotent(String period, String workerId) {
        throw new UnsupportedOperationException(
                "Checkpoint 7: give each charge a key that identifies the work rather than the attempt");
    }

    /**
     * Step 2: hold a lock for exactly as long as this transaction, then let the
     * commit release it. Returns false if somebody else is already running.
     *
     * <p>{@code whileHoldingTheLock} is a test seam — it lets a test decide how
     * long this worker stays in the critical section, so "the other worker tried
     * while we were inside it" is a fact rather than a hope.
     */
    @Transactional
    public boolean billIfLockAcquired(String period, String workerId, Runnable whileHoldingTheLock) {
        if (!advisoryLock.tryLockForThisTransaction(BILLING_LOCK_KEY)) {
            return false;
        }
        whileHoldingTheLock.run();
        bill(period, workerId);
        return true;
    }

    private void invoice(Customer customer, String period, String workerId) {
        jdbc.sql("""
                        INSERT INTO invoice (customer, period, amount, issued_by)
                        VALUES (:customer, :period, :amount, :by)
                        """)
                .param("customer", customer.name()).param("period", period)
                .param("amount", customer.amount()).param("by", workerId)
                .update();
    }

    @Transactional(readOnly = true)
    public List<Customer> customers() {
        return jdbc.sql("SELECT id, name, amount FROM customer ORDER BY id")
                .query((rs, n) -> new Customer(rs.getLong("id"), rs.getString("name"), rs.getLong("amount")))
                .list();
    }

    public record Customer(long id, String name, long amount) {
    }
}
