package vlearning.payments;

public class Fraud extends PaymentResult {

    private final double riskScore;
    private final String rule;

    public Fraud(String paymentId, double riskScore, String rule) {
        super(paymentId);
        this.riskScore = riskScore;
        this.rule = rule;
    }

    public double getRiskScore() {
        return riskScore;
    }

    public String getRule() {
        return rule;
    }
}
