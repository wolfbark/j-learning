package vlearning.payments;

public class PaymentException extends RuntimeException {

    private final String code;

    public PaymentException(String code, String message) {
        super("[" + code + "] " + message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
