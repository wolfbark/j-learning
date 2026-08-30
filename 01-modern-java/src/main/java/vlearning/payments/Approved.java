package vlearning.payments;

import java.math.BigDecimal;

public class Approved extends PaymentResult {

    private final String authCode;
    private final BigDecimal fee;

    public Approved(String paymentId, String authCode, BigDecimal fee) {
        super(paymentId);
        this.authCode = authCode;
        this.fee = fee;
    }

    public String getAuthCode() {
        return authCode;
    }

    public BigDecimal getFee() {
        return fee;
    }
}
