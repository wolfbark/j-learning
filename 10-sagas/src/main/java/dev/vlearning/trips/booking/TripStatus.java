package dev.vlearning.trips.booking;

/**
 * What the customer sees. {@code PENDING} is the eventual-consistency window —
 * in the pristine scaffold it is also, embarrassingly, the final state of every
 * trip, because nothing coordinates the saga yet.
 */
public enum TripStatus {
    PENDING, CONFIRMED, REJECTED
}
