package dev.vlearning.parcels.notify;

/**
 * Non-retryable: the work item itself is wrong. Retrying it a hundred times only means failing
 * a hundred times and blocking everything behind it.
 */
public class UnknownChannelException extends RuntimeException {

    public UnknownChannelException(String channel) {
        super("Unknown notification channel: " + channel);
    }
}
