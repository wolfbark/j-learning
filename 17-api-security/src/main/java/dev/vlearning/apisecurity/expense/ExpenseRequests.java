package dev.vlearning.apisecurity.expense;

public final class ExpenseRequests {

    private ExpenseRequests() {
    }

    /**
     * What the mobile app posts. Yes, it sends the card number and the employee's email address —
     * the payment integration wants them. Where those end up is step 7's problem.
     */
    public record CreateExpense(
            String ownerUsername,
            String team,
            String merchant,
            long amountCents,
            String currency,
            String category,
            String cardNumber,
            String employeeEmail) {
    }

    public record UpdateExpense(
            String merchant,
            long amountCents,
            String category) {
    }

    public record ReceiptFromUrl(String url) {
    }
}
