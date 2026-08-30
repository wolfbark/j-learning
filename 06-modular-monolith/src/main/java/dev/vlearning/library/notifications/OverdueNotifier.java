package dev.vlearning.library.notifications;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Component;

import dev.vlearning.library.lending.domain.Loan;
import dev.vlearning.library.lending.repo.LoanRepository;

@Component
public class OverdueNotifier {

    private final LoanRepository loans;
    private final NotificationSender sender;

    OverdueNotifier(LoanRepository loans, NotificationSender sender) {
        this.loans = loans;
        this.sender = sender;
    }

    public int run() {
        List<Loan> overdue = loans.findByReturnedAtIsNullAndDueDateBefore(LocalDate.now());
        overdue.forEach(loan -> sender.send("OVERDUE: %s should have returned '%s' (copy %s) by %s".formatted(
                loan.getMemberEmail(), loan.getBookTitle(), loan.getCopyBarcode(), loan.getDueDate())));
        return overdue.size();
    }
}
