package vlearning.payments;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class PaymentProcessor {

    private static final BigDecimal FRAUD_THRESHOLD = new BigDecimal("10000");

    private final LocalDate today;

    public PaymentProcessor() {
        this(LocalDate.now());
    }

    public PaymentProcessor(LocalDate today) {
        this.today = today;
    }

    public PaymentResult process(PaymentRequest request) {
        validate(request);

        CardDetails card = request.getCard();

        if (isExpired(card)) {
            return new Declined(request.getId(), "card expired");
        }
        if (request.getAmount().compareTo(FRAUD_THRESHOLD) > 0) {
            return new Fraud(request.getId(), 0.95, "AMOUNT_THRESHOLD");
        }
        if (card.getNumber().endsWith("0019")) {
            return new Fraud(request.getId(), 0.92, "ISSUER_FLAGGED");
        }
        if (RequestContext.isSet()) {
            String region = RequestContext.current().getRegion();
            if (request.getAmount().compareTo(regionalLimit(region)) > 0) {
                return new Declined(request.getId(), "over regional limit for " + region);
            }
        }
        if (card.getNumber().endsWith("0119")) {
            return new Retryable(request.getId(), "gateway processing error", 30);
        }
        if (card.getNumber().endsWith("9995")) {
            return new Declined(request.getId(), "insufficient funds");
        }
        return new Approved(request.getId(), "AUTH-" + request.getId(),
                feeFor(card.getBrand(), request.getAmount()));
    }

    public String summarize(PaymentResult result) {
        if (result instanceof Approved) {
            Approved approved = (Approved) result;
            if (approved.getFee().compareTo(BigDecimal.ZERO) == 0) {
                return "APPROVED " + approved.getPaymentId() + " auth=" + approved.getAuthCode()
                        + " (fee waived)";
            }
            return "APPROVED " + approved.getPaymentId() + " auth=" + approved.getAuthCode()
                    + " fee=" + approved.getFee();
        } else if (result instanceof Declined) {
            Declined declined = (Declined) result;
            return "DECLINED " + declined.getPaymentId() + " reason=" + declined.getReason();
        } else if (result instanceof Fraud) {
            Fraud fraud = (Fraud) result;
            return "FRAUD " + fraud.getPaymentId() + " risk=" + fraud.getRiskScore()
                    + " rule=" + fraud.getRule();
        } else if (result instanceof Retryable) {
            Retryable retryable = (Retryable) result;
            return "RETRY " + retryable.getPaymentId() + " in " + retryable.getRetryAfterSeconds()
                    + "s: " + retryable.getReason();
        }
        throw new IllegalStateException("Unknown result type: " + result);
    }

    public List<String> summarizeAll(List<PaymentRequest> batch) {
        List<PaymentRequest> sorted = new ArrayList<PaymentRequest>(batch);
        Collections.sort(sorted, new Comparator<PaymentRequest>() {
            @Override
            public int compare(PaymentRequest left, PaymentRequest right) {
                int byAmount = right.getAmount().compareTo(left.getAmount());
                if (byAmount != 0) {
                    return byAmount;
                }
                return left.getId().compareTo(right.getId());
            }
        });
        final List<String> lines = new ArrayList<String>();
        processAll(sorted, new ResultCallback() {
            @Override
            public void onResult(PaymentRequest request, PaymentResult result) {
                lines.add(summarize(result));
            }
        });
        return lines;
    }

    public void processAll(List<PaymentRequest> batch, ResultCallback callback) {
        for (PaymentRequest request : batch) {
            callback.onResult(request, process(request));
        }
    }

    private BigDecimal feeFor(CardBrand brand, BigDecimal amount) {
        BigDecimal rate;
        switch (brand) {
            case VISA:
            case MASTERCARD:
                rate = new BigDecimal("0.015");
                break;
            case AMEX:
                rate = new BigDecimal("0.025");
                break;
            default:
                rate = new BigDecimal("0.020");
                break;
        }
        return amount.multiply(rate).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal regionalLimit(String region) {
        if ("EU".equals(region)) {
            return new BigDecimal("5000");
        } else if ("US".equals(region)) {
            return new BigDecimal("8000");
        } else {
            return new BigDecimal("2500");
        }
    }

    private boolean isExpired(CardDetails card) {
        if (card.getExpiryYear() < today.getYear()) {
            return true;
        }
        return card.getExpiryYear() == today.getYear() && card.getExpiryMonth() < today.getMonthValue();
    }

    private void validate(PaymentRequest request) {
        if (request == null) {
            throw new PaymentException("VALIDATION", "request must not be null");
        }
        if (isBlank(request.getId())) {
            throw new PaymentException("VALIDATION", "payment id is required");
        }
        if (isBlank(request.getCustomerId())) {
            throw new PaymentException("VALIDATION", "customer id is required");
        }
        if (request.getAmount() == null || request.getAmount().signum() <= 0) {
            throw new PaymentException("VALIDATION", "amount must be positive");
        }
        if (request.getCurrency() == null || request.getCurrency().length() != 3) {
            throw new PaymentException("VALIDATION", "currency must be a 3-letter code");
        }
        if (request.getCard() == null) {
            throw new PaymentException("VALIDATION", "card details are required");
        }
        if (isBlank(request.getCard().getNumber()) || request.getCard().getNumber().length() < 12) {
            throw new PaymentException("VALIDATION", "card number looks invalid");
        }
        if (request.getCard().getBrand() == null) {
            throw new PaymentException("VALIDATION", "card brand is required");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
