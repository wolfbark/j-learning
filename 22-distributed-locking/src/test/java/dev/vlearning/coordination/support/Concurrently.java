package dev.vlearning.coordination.support;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntConsumer;

/**
 * Run the same piece of work from N threads that all start at once, and report
 * what happened — including the failures, which in a concurrency lesson are the
 * result rather than the problem.
 */
public final class Concurrently {

    private Concurrently() {
    }

    public record Outcome(int successes, List<Throwable> failures) {

        public long failuresOfType(Class<? extends Throwable> type) {
            return failures.stream().filter(type::isInstance).count();
        }

        public int attempted() {
            return successes + failures.size();
        }
    }

    public static Outcome run(int threads, IntConsumer work) {
        var readyToGo = new CountDownLatch(1);
        var done = new CountDownLatch(threads);
        var successes = new AtomicInteger();
        List<Throwable> failures = new CopyOnWriteArrayList<>();

        for (int i = 0; i < threads; i++) {
            int index = i;
            Thread.ofVirtual().name("worker-" + i).start(() -> {
                try {
                    readyToGo.await();
                    work.accept(index);
                    successes.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (Throwable failure) {
                    failures.add(failure);
                } finally {
                    done.countDown();
                }
            });
        }

        readyToGo.countDown();
        try {
            if (!done.await(60, TimeUnit.SECONDS)) {
                throw new IllegalStateException("workers did not finish within 60s");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
        return new Outcome(successes.get(), List.copyOf(failures));
    }
}
