package dev.vlearning.parcels.notify;

/** Retryable: the item is fine, the world was briefly not. */
public class ChannelUnavailableException extends RuntimeException {

    public ChannelUnavailableException(String message) {
        super(message);
    }
}
