package dev.vlearning.shipping.chaos;

public enum ChaosMode {

    /** Business as usual. */
    OK,

    /** Every /shipments call takes five seconds. The network is not reliable — sometimes it's worse: slow. */
    SLOW_5S,

    /** Every /shipments call is refused with a 503. */
    DOWN
}
