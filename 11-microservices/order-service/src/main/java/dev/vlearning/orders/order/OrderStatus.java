package dev.vlearning.orders.order;

public enum OrderStatus {

    /** Persisted, shipping not arranged yet. */
    PLACED,

    /** Accepted, but shipping could not be arranged right now — someone owes the customer a shipment. */
    SHIPPING_PENDING,

    /** Shipping arranged; the order is a promise we can keep. */
    CONFIRMED
}
