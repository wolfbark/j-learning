package dev.vlearning.apisecurity.expense;

public record ExpenseReport(
        long id,
        String ownerUsername,
        String team,
        String merchant,
        long amountCents,
        String currency,
        String category,
        String status,
        String cardLast4,
        String receiptUrl,
        Integer receiptBytes) {

    public static final String DRAFT = "DRAFT";
    public static final String SUBMITTED = "SUBMITTED";
    public static final String APPROVED = "APPROVED";
}
