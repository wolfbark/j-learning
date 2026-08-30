package dev.vlearning.ticketing.catalog;

/**
 * The honest answer to a customer. Note that in step 1 nobody ever sees it:
 * the tickets run out without any transaction noticing.
 */
public class SoldOutException extends RuntimeException {

    public SoldOutException(String ticketType, int available, int wanted) {
        super("%s: %d left, %d wanted".formatted(ticketType, available, wanted));
    }
}
