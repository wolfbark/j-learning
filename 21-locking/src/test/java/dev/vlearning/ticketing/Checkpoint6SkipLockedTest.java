package dev.vlearning.ticketing;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;

import dev.vlearning.ticketing.seating.SeatAllocator;
import dev.vlearning.ticketing.support.Concurrently;
import dev.vlearning.ticketing.support.DbSession.Isolation;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step 6: claiming a row nobody else has — the pattern behind every
 * database-backed work queue, wearing a seat's clothing.
 *
 * <p>Plain {@code FOR UPDATE} is correct here and still wrong: Postgres will
 * eventually give the waiting worker the next free seat, but only after the
 * transaction holding row 1 finishes. With jobs that take seconds, a pool of
 * workers degenerates into one worker and a queue of spectators.
 */
@Disabled("Checkpoint 6 — enable when you start step 6")
class Checkpoint6SkipLockedTest extends AbstractIntegrationTest {

    @Autowired
    SeatAllocator allocator;

    @Test
    void plainForUpdate_waitsForWhoeverIsHoldingTheFirstSeat() throws Exception {
        try (var holder = session("holder")) {
            holder.begin(Isolation.READ_COMMITTED);
            holder.update("UPDATE seat SET status = 'HELD', held_by = 'other' WHERE id = 1");

            var worker = CompletableFuture.supplyAsync(() -> allocator.allocateBlocking("worker"));
            Thread.sleep(600);

            assertThat(worker.isDone())
                    .as("nineteen seats are free and this worker is waiting for the one that is not")
                    .isFalse();

            holder.commit();

            assertThat(worker.get(10, TimeUnit.SECONDS))
                    .as("it does get the next seat — eventually")
                    .contains(2L);
        }
    }

    @Test
    void skipLocked_takesTheNextFreeSeatWithoutWaiting() {
        try (var holder = session("holder")) {
            holder.begin(Isolation.READ_COMMITTED);
            holder.update("UPDATE seat SET status = 'HELD', held_by = 'other' WHERE id = 1");

            // Returns while the holder is still holding. No wait, no timeout, no queue.
            assertThat(allocator.allocateSkippingLocked("worker")).contains(2L);
        }
    }

    @Test
    void fourWorkersTakeFourDifferentSeatsAtTheSameTime() {
        var allHaveClaimed = new CyclicBarrier(4);
        interleaving.armAfterRead(() -> {
            try {
                allHaveClaimed.await();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });

        List<Long> claimed = new CopyOnWriteArrayList<>();
        var outcome = Concurrently.run(4, worker ->
                allocator.allocateSkippingLocked("worker" + worker).ifPresent(claimed::add));

        assertThat(outcome.failures()).isEmpty();
        assertThat(claimed).as("four workers, four seats, nobody blocked").hasSize(4).doesNotHaveDuplicates();
        assertThat(heldSeatCount()).isEqualTo(4);
    }
}
