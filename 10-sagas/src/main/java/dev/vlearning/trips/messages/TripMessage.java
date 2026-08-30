package dev.vlearning.trips.messages;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * The complete message contract of the trip saga, on one page. In a real system
 * each service would own a copy of this (schema registry, shared contract jar);
 * here one sealed interface plays that role — and gives you exhaustive
 * pattern-matching switches in every listener.
 *
 * <p>Naming discipline matters more than tooling: <b>events</b> are past-tense
 * facts anyone may react to ({@code FlightReserved}); <b>commands</b> are
 * imperatives addressed to exactly one service ({@code CancelFlight}). Events
 * travel on the shared {@code trips.events} topic, commands on the target
 * service's own command topic (see {@link TripTopics}).
 *
 * <p>Note what the reservation events drag along: {@code destination} and
 * {@code price} are payment's and hotel's data, piggybacking on flight's event
 * because the <i>next</i> participant needs them. That is a choreography smell
 * you will meet head-on in step 1.
 */
public sealed interface TripMessage {

    UUID tripId();

    // ── Events — facts, published to the shared trips.events topic ──────────

    record TripRequested(UUID tripId, String traveller, String destination, BigDecimal price) implements TripMessage {}

    record FlightReserved(UUID tripId, String flightNumber, String destination, BigDecimal price) implements TripMessage {}

    record FlightRejected(UUID tripId, String reason) implements TripMessage {}

    record FlightCancelled(UUID tripId) implements TripMessage {}

    record HotelReserved(UUID tripId, String hotelName, BigDecimal price) implements TripMessage {}

    record HotelRejected(UUID tripId, String reason) implements TripMessage {}

    record HotelCancelled(UUID tripId) implements TripMessage {}

    record PaymentCaptured(UUID tripId, BigDecimal amount) implements TripMessage {}

    record PaymentFailed(UUID tripId, String reason) implements TripMessage {}

    record PaymentRefunded(UUID tripId) implements TripMessage {}

    // ── Commands — imperatives, each on its target service's command topic ──

    record ReserveFlight(UUID tripId, String destination, BigDecimal price) implements TripMessage {}

    record CancelFlight(UUID tripId) implements TripMessage {}

    record ReserveHotel(UUID tripId, String destination, BigDecimal price) implements TripMessage {}

    record CancelHotel(UUID tripId) implements TripMessage {}

    record CapturePayment(UUID tripId, BigDecimal amount) implements TripMessage {}

    record RefundPayment(UUID tripId) implements TripMessage {}
}
