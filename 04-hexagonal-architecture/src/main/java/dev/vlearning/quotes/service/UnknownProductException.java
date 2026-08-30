package dev.vlearning.quotes.service;

public class UnknownProductException extends RuntimeException {

    public UnknownProductException(String productCode) {
        super("No such product: " + productCode);
    }
}
