package dev.vlearning.ticketing.booking;

import dev.vlearning.ticketing.catalog.SoldOutException;
import dev.vlearning.ticketing.catalog.TicketType;
import dev.vlearning.ticketing.catalog.TicketTypeRepository;
import dev.vlearning.ticketing.support.Interleaving;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Selling tickets, written the ordinary way: load the entity, ask it to reserve,
 * let the persistence context write it back on flush.
 *
 * <p>Every method here is inside a transaction, and the transaction is doing its
 * job. What it does not do — and what nobody remembers until the conference is
 * oversold — is stop a second request from making the same decision from the
 * same starting point.
 */
@Service
public class BookingService {

    private final TicketTypeRepository ticketTypes;
    private final BookingRepository bookings;
    private final Interleaving interleaving;

    BookingService(TicketTypeRepository ticketTypes, BookingRepository bookings, Interleaving interleaving) {
        this.ticketTypes = ticketTypes;
        this.bookings = bookings;
        this.interleaving = interleaving;
    }

    @Transactional
    public void book(long ticketTypeId, String attendee, int quantity) {
        TicketType type = ticketTypes.findById(ticketTypeId).orElseThrow();
        interleaving.afterRead();
        type.reserve(quantity);
        bookings.save(new Booking(ticketTypeId, attendee, quantity));
    }

    /**
     * Checkpoint 4: the same sale, but nobody else may hold this ticket type
     * while we decide.
     */
    @Transactional
    public void bookWithRowLock(long ticketTypeId, String attendee, int quantity) {
        TicketType type = ticketTypes.findByIdForUpdate(ticketTypeId).orElseThrow();
        interleaving.afterRead();
        type.reserve(quantity);
        bookings.save(new Booking(ticketTypeId, attendee, quantity));
    }

    /**
     * Checkpoint 4b: the same sale again, but a caller who cannot have the row
     * immediately is told so rather than parked.
     */
    @Transactional
    public void bookOrGiveUp(long ticketTypeId, String attendee, int quantity) {
        TicketType type = ticketTypes.findByIdForUpdateNoWait(ticketTypeId).orElseThrow();
        type.reserve(quantity);
        bookings.save(new Booking(ticketTypeId, attendee, quantity));
    }

    /**
     * A bundle: one ticket of each type, locked in the order the caller happened
     * to pass them. Two customers buying the same two ticket types in opposite
     * orders is all it takes — see step 5.
     */
    @Transactional
    public void bookBundle(long firstTypeId, long secondTypeId, String attendee) {
        TicketType first = ticketTypes.findByIdForUpdate(firstTypeId).orElseThrow();
        first.reserve(1);
        interleaving.afterRead();
        TicketType second = ticketTypes.findByIdForUpdate(secondTypeId).orElseThrow();
        second.reserve(1);
        bookings.save(new Booking(firstTypeId, attendee, 1));
        bookings.save(new Booking(secondTypeId, attendee, 1));
    }

    /**
     * Checkpoint 5: the same bundle, immune to the order the caller asked for.
     */
    @Transactional
    public void bookBundleSafely(long firstTypeId, long secondTypeId, String attendee) {
        throw new UnsupportedOperationException(
                "Checkpoint 5: take the two locks in an order that does not depend on the caller");
    }

    /**
     * Checkpoint 7: no read, no lock, no version — one conditional statement.
     */
    @Transactional
    public void bookAtomically(long ticketTypeId, String attendee, int quantity) {
        int reserved = ticketTypes.reserveIfAvailable(ticketTypeId, quantity);
        if (reserved == 0) {
            throw new SoldOutException("ticket type " + ticketTypeId, 0, quantity);
        }
        bookings.save(new Booking(ticketTypeId, attendee, quantity));
    }
}
