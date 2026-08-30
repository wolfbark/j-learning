package dev.vlearning.reliability.settlement;

/**
 * What the caller sends. Note what is in here: an email address and a card
 * number. Both are legitimate inputs and neither belongs in a log line —
 * step 1's real subject is that the data you must handle is not the data you
 * may retain.
 */
public record SettlementCommand(
        String orderId,
        String userId,
        String customerEmail,
        String cardNumber,
        long amountCents) {
}
