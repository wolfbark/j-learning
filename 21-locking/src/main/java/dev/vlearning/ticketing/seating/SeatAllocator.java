package dev.vlearning.ticketing.seating;

import java.util.Optional;

import dev.vlearning.ticketing.support.Interleaving;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The lock lives as long as the transaction, so the claim and the mark have to
 * be in the same one. That is the whole trick of a database-backed queue: the
 * row lock <em>is</em> the lease, and it is released by the commit, or by the
 * crash.
 */
@Service
public class SeatAllocator {

    private final SeatRepository seats;
    private final Interleaving interleaving;

    SeatAllocator(SeatRepository seats, Interleaving interleaving) {
        this.seats = seats;
        this.interleaving = interleaving;
    }

    @Transactional
    public Optional<Long> allocateBlocking(String worker) {
        Optional<Long> seat = seats.nextFreeSeatBlocking();
        interleaving.afterRead();
        seat.ifPresent(id -> seats.hold(id, worker));
        return seat;
    }

    @Transactional
    public Optional<Long> allocateSkippingLocked(String worker) {
        Optional<Long> seat = seats.nextFreeSeatSkippingLocked();
        interleaving.afterRead();
        seat.ifPresent(id -> seats.hold(id, worker));
        return seat;
    }
}
