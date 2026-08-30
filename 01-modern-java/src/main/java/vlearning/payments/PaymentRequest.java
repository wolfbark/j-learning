package vlearning.payments;

import java.math.BigDecimal;
import java.util.Objects;

public class PaymentRequest {

    private String id;
    private String customerId;
    private BigDecimal amount;
    private String currency;
    private CardDetails card;

    public PaymentRequest() {
    }

    public PaymentRequest(String id, String customerId, BigDecimal amount, String currency, CardDetails card) {
        this.id = id;
        this.customerId = customerId;
        this.amount = amount;
        this.currency = currency;
        this.card = card;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public CardDetails getCard() {
        return card;
    }

    public void setCard(CardDetails card) {
        this.card = card;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        PaymentRequest that = (PaymentRequest) o;
        return Objects.equals(id, that.id)
                && Objects.equals(customerId, that.customerId)
                && Objects.equals(amount, that.amount)
                && Objects.equals(currency, that.currency)
                && Objects.equals(card, that.card);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, customerId, amount, currency, card);
    }

    @Override
    public String toString() {
        return "PaymentRequest{id='" + id + "', customerId='" + customerId + "', amount=" + amount
                + ", currency='" + currency + "', card=" + card + "}";
    }
}
