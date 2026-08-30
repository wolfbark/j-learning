package vlearning.payments;

import java.util.Objects;

public class CardDetails {

    private String number;
    private CardBrand brand;
    private int expiryMonth;
    private int expiryYear;

    public CardDetails() {
    }

    public CardDetails(String number, CardBrand brand, int expiryMonth, int expiryYear) {
        this.number = number;
        this.brand = brand;
        this.expiryMonth = expiryMonth;
        this.expiryYear = expiryYear;
    }

    public String getNumber() {
        return number;
    }

    public void setNumber(String number) {
        this.number = number;
    }

    public CardBrand getBrand() {
        return brand;
    }

    public void setBrand(CardBrand brand) {
        this.brand = brand;
    }

    public int getExpiryMonth() {
        return expiryMonth;
    }

    public void setExpiryMonth(int expiryMonth) {
        this.expiryMonth = expiryMonth;
    }

    public int getExpiryYear() {
        return expiryYear;
    }

    public void setExpiryYear(int expiryYear) {
        this.expiryYear = expiryYear;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        CardDetails that = (CardDetails) o;
        return expiryMonth == that.expiryMonth
                && expiryYear == that.expiryYear
                && Objects.equals(number, that.number)
                && brand == that.brand;
    }

    @Override
    public int hashCode() {
        return Objects.hash(number, brand, expiryMonth, expiryYear);
    }

    @Override
    public String toString() {
        return "CardDetails{number='" + number + "', brand=" + brand
                + ", expiryMonth=" + expiryMonth + ", expiryYear=" + expiryYear + "}";
    }
}
