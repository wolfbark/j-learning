package dev.vlearning.registration.enrollment;

/**
 * Shell of a value object — see {@link Email}. Business policy for checkpoint 2:
 * an enrollment books between 1 and 20 seats (group bookings beyond that go
 * through sales, not the website).
 */
public record SeatCount(int value) {
}
