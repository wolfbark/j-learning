package dev.vlearning.payments.domain;

/**
 * The only two outcomes an authorization can have. Capture/refund are out of
 * scope — but note that this enum is part of the published contract: adding a
 * value is a consumer-visible change, which is exactly what step 5 is about.
 */
public enum PaymentStatus {
    AUTHORIZED,
    DECLINED
}
