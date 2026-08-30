package dev.vlearning.lending;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * One controller for both sides — for now. Step 1 splits it along with the
 * service: commands and queries each get their own controller.
 */
@RestController
@RequestMapping("/api")
public class LibraryController {

    private final LibraryService service;

    LibraryController(LibraryService service) {
        this.service = service;
    }

    record BorrowRequest(long memberId, long bookId) {
    }

    @PostMapping("/loans")
    @ResponseStatus(HttpStatus.CREATED)
    LibraryService.LoanReceipt borrow(@RequestBody BorrowRequest request) {
        return service.borrowBook(request.memberId(), request.bookId());
    }

    @PostMapping("/loans/{loanId}/return")
    LibraryService.ReturnReceipt returnBook(@PathVariable long loanId) {
        return service.returnBook(loanId);
    }

    @GetMapping("/members/{memberId}/activity")
    MemberActivity memberActivity(@PathVariable long memberId) {
        return service.getMemberActivity(memberId);
    }
}
