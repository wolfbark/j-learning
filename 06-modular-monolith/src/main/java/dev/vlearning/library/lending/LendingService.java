package dev.vlearning.library.lending;

import java.time.Instant;
import java.time.LocalDate;
import java.util.NoSuchElementException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.vlearning.library.catalog.domain.BookCopy;
import dev.vlearning.library.catalog.repo.BookCopyRepository;
import dev.vlearning.library.lending.domain.Loan;
import dev.vlearning.library.lending.repo.LoanRepository;
import dev.vlearning.library.notifications.NotificationSender;

@Service
public class LendingService {

    private final LoanRepository loans;
    private final BookCopyRepository copies;
    private final NotificationSender notifications;

    LendingService(LoanRepository loans, BookCopyRepository copies, NotificationSender notifications) {
        this.loans = loans;
        this.copies = copies;
        this.notifications = notifications;
    }

    @Transactional
    public Loan borrow(String barcode, String memberEmail, LocalDate dueDate) {
        BookCopy copy = copies.findByBarcode(barcode)
                .orElseThrow(() -> new NoSuchElementException("No copy with barcode " + barcode));
        if (copy.getStatus() != BookCopy.Status.AVAILABLE) {
            throw new IllegalStateException("Copy " + barcode + " is already on loan");
        }

        copy.setStatus(BookCopy.Status.ON_LOAN);
        Loan loan = loans.save(new Loan(barcode, copy.getBook().getTitle(), memberEmail, dueDate));

        notifications.sendLoanConfirmation(loan.getId());
        return loan;
    }

    @Transactional
    public Loan returnLoan(Long loanId) {
        Loan loan = loans.findById(loanId)
                .orElseThrow(() -> new NoSuchElementException("No loan with id " + loanId));
        if (loan.getReturnedAt() != null) {
            throw new IllegalStateException("Loan " + loanId + " was already returned");
        }

        loan.setReturnedAt(Instant.now());
        BookCopy copy = copies.findByBarcode(loan.getCopyBarcode()).orElseThrow();
        copy.setStatus(BookCopy.Status.AVAILABLE);
        return loan;
    }
}
