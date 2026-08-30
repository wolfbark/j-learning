package dev.vlearning.parcels.notify;

/**
 * A command, not an event: somebody must actually send this message, exactly one worker should
 * do it, and it can fail and be retried. Nothing about it is a fact about the past — which is
 * why it belongs on a work queue rather than on a replayable feed.
 */
public record NotifyCustomer(String taskId,
                             String parcelId,
                             String customerId,
                             String channel,
                             String message) {

    public static NotifyCustomer sms(String parcelId, String customerId, String message) {
        return new NotifyCustomer(parcelId + "-notify", parcelId, customerId, "sms", message);
    }
}
