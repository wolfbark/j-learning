package dev.vlearning.production.gateway;

/** The one dependency that can ruin your day. */
public interface PaymentGateway {

    Authorization authorize(String orderId, long amountCents);

    record Authorization(String authorizationCode, String status) {}

    /** Thrown for anything the gateway could not answer successfully. */
    class GatewayException extends RuntimeException {
        public GatewayException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** The circuit breaker's answer: we did not even try. */
    class GatewayUnavailableException extends RuntimeException {
        public GatewayUnavailableException(String message) {
            super(message);
        }
    }
}
