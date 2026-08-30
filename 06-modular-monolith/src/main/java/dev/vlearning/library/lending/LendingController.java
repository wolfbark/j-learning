package dev.vlearning.library.lending;

import java.time.Instant;
import java.time.LocalDate;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import dev.vlearning.library.lending.domain.Loan;

@RestController
public class LendingController {

    private final LendingService lending;

    LendingController(LendingService lending) {
        this.lending = lending;
    }

    record BorrowRequest(String barcode, String memberEmail, LocalDate dueDate) {
    }

    record LoanResponse(Long id, String copyBarcode, String bookTitle, String memberEmail,
                        LocalDate dueDate, Instant returnedAt) {

        static LoanResponse of(Loan loan) {
            return new LoanResponse(loan.getId(), loan.getCopyBarcode(), loan.getBookTitle(),
                    loan.getMemberEmail(), loan.getDueDate(), loan.getReturnedAt());
        }
    }

    @PostMapping("/loans")
    @ResponseStatus(HttpStatus.CREATED)
    LoanResponse borrow(@RequestBody BorrowRequest request) {
        if (request.barcode() == null || request.barcode().isBlank()) {
            throw new IllegalArgumentException("barcode must not be blank");
        }
        if (request.memberEmail() == null || request.memberEmail().isBlank()) {
            throw new IllegalArgumentException("memberEmail must not be blank");
        }
        if (request.dueDate() == null) {
            throw new IllegalArgumentException("dueDate must not be null");
        }
        return LoanResponse.of(lending.borrow(request.barcode(), request.memberEmail(), request.dueDate()));
    }

    @PostMapping("/loans/{id}/return")
    LoanResponse returnLoan(@PathVariable Long id) {
        return LoanResponse.of(lending.returnLoan(id));
    }
}
