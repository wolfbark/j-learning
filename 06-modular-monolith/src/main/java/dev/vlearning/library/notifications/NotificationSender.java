package dev.vlearning.library.notifications;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import dev.vlearning.library.lending.domain.Loan;
import dev.vlearning.library.lending.repo.LoanRepository;

/**
 * Stand-in for a real mail/SMS gateway: logs every message and keeps it in
 * memory so tests and the /notifications endpoint can inspect what went out.
 */
@Component
public class NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(NotificationSender.class);

    private final List<String> sent = new CopyOnWriteArrayList<>();
    private final LoanRepository loans;

    NotificationSender(LoanRepository loans) {
        this.loans = loans;
    }

    public void sendLoanConfirmation(Long loanId) {
        Loan loan = loans.findById(loanId).orElseThrow();
        send("To %s: you borrowed '%s' (copy %s), due %s".formatted(
                loan.getMemberEmail(), loan.getBookTitle(), loan.getCopyBarcode(), loan.getDueDate()));
    }

    public void send(String message) {
        log.info("NOTIFY {}", message);
        sent.add(message);
    }

    public List<String> sent() {
        return List.copyOf(sent);
    }
}
