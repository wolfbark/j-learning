package dev.vlearning.banking.traps;

/** A perfectly ordinary checked exception — which is exactly the problem. */
public class PaperworkMissingException extends Exception {

    public PaperworkMissingException(String message) {
        super(message);
    }
}
