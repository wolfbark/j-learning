package dev.vlearning.loom.pool;

import java.time.Duration;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import dev.vlearning.loom.support.ConcurrencyMeter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * A stand-in for the thing virtual threads cannot conjure more of: a connection
 * pool. Ten permits, one per "connection"; acquiring blocks when they are gone.
 *
 * <p>HikariCP behaves exactly like this, and its default pool size is 10. Once
 * threads stop being the scarce resource, this becomes your ceiling — and no
 * amount of Loom changes that. Step 5 is about finding the ceiling and then
 * raising it the only way that works: by holding permits for less time.
 */
@Component
public class ScarcePool {

    private final int size;
    private final Semaphore permits;
    private final ConcurrencyMeter meter = new ConcurrencyMeter();
    private final AtomicLong totalHoldNanos = new AtomicLong();
    private final AtomicInteger acquisitions = new AtomicInteger();
    private final long queryMillis;

    public ScarcePool(@Value("${loom.pool-size:10}") int size,
                      @Value("${loom.query-ms:50}") long queryMillis) {
        this.size = size;
        this.queryMillis = queryMillis;
        this.permits = new Semaphore(size, true);
    }

    /**
     * Borrow a "connection", run {@code work}, return it. The hold time is
     * recorded, because hold time × requests ÷ pool size is your throughput
     * ceiling, and it is the number you can actually influence.
     */
    public <T> T withConnection(ConnectionWork<T> work) throws InterruptedException {
        permits.acquire();
        long start = System.nanoTime();
        try {
            return meter.measure(() -> work.run(new Connection()));
        } catch (InterruptedException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        } finally {
            totalHoldNanos.addAndGet(System.nanoTime() - start);
            acquisitions.incrementAndGet();
            permits.release();
        }
    }

    public int size() {
        return size;
    }

    /** Peak number of permits held simultaneously — can never exceed {@link #size()}. */
    public int peakConcurrentUse() {
        return meter.peak();
    }

    public Duration averageHoldTime() {
        int count = acquisitions.get();
        return count == 0 ? Duration.ZERO : Duration.ofNanos(totalHoldNanos.get() / count);
    }

    public void reset() {
        meter.reset();
        totalHoldNanos.set(0);
        acquisitions.set(0);
    }

    /** The "connection" itself: one slow query, nothing more. */
    public final class Connection {
        public int runQuery() throws InterruptedException {
            Thread.sleep(queryMillis);
            return 1;
        }
    }

    @FunctionalInterface
    public interface ConnectionWork<T> {
        T run(Connection connection) throws Exception;
    }
}
